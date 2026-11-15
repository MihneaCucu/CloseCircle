"""SCRUM-16 (T-10): immediate revocation of location sharing, over real HTTP and PostgreSQL.

From backend/: mvn -q -DskipTests package && python3 src/test/python/check_sharing_revocation.py
Uses the disposable PostgreSQL harness in check_latest_locations.py; never connects to Neon.

Acceptance criteria checked:
1. Revocation takes effect immediately: the first read after DELETE returns no longer shows the user,
   also while uploads are running concurrently.
2. After revocation, group members receive no further location updates from the user: new uploads,
   retries of earlier uploads and recordings queued before the revocation are all refused, and no
   point is stored after the revocation.
"""
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import json
import threading
import time
import urllib.error
import urllib.request
import uuid

from check_latest_locations import main


def verify(sql, latest, restart, base, token, group, other_group, ids):
    # Fixture roles (see check_latest_locations.py): ids[0] reads `group`; ids[1] shares in both
    # groups; ids[5] is a member of `group` without saved preferences; ids[6] reads `other_group`.
    viewer, sharer, newcomer, other_viewer = ids[0], ids[1], ids[5], ids[6]
    created = []
    created_lock = threading.Lock()
    sql("UPDATE shares SET updated_at='2026-01-01T00:00:00Z';")

    def call(method, user, *, group_id=group, body=None, bearer=None):
        headers = {}
        credential = bearer if bearer is not None else (token(user) if user else None)
        if credential:
            headers['Authorization'] = 'Bearer ' + credential
        data = json.dumps(body).encode() if body is not None else None
        if data is not None:
            headers['Content-Type'] = 'application/json'
        req = urllib.request.Request(f'{base}/api/v1/groups/{group_id}/me/sharing', data=data,
                                     headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=10) as response:
                assert 'no-store' in response.headers.get('Cache-Control', '')
                return response.status, json.load(response)
        except urllib.error.HTTPError as error:
            content = error.read()
            return error.code, json.loads(content) if content else None

    def revoke(user, **kwargs):
        return call('DELETE', user, **kwargs)

    def now():
        return datetime.now(timezone.utc).isoformat()

    def instant(value):
        return datetime.fromisoformat(value.replace('Z', '+00:00'))

    def upload(user=sharer, *, group_id=group, recorded_at=None, key=None):
        body = {'latitude': 44.43, 'longitude': 26.1, 'recordedAt': recorded_at or now()}
        req = urllib.request.Request(f'{base}/api/v1/groups/{group_id}/locations',
                                     data=json.dumps(body).encode(), method='POST', headers={
                                         'Content-Type': 'application/json',
                                         'Authorization': 'Bearer ' + token(user),
                                         'Idempotency-Key': key or str(uuid.uuid4())})
        try:
            with urllib.request.urlopen(req, timeout=10) as response:
                point = json.load(response)
                with created_lock:
                    created.append(point['locationId'])
                return response.status, point
        except urllib.error.HTTPError as error:
            content = error.read()
            return error.code, json.loads(content) if content else None

    def visible_to(reader, member, group_id=group):
        status, page = latest(reader, group_id=group_id)
        assert status == 200
        return next((item for item in page['items'] if item['userId'] == member), None)

    def assert_nothing_received_after_revocation(user, group_id=group):
        sql(f"DO $$ BEGIN IF EXISTS (SELECT 1 FROM locations l JOIN shares s USING (group_id, user_id) "
            f"WHERE l.group_id='{group_id}' AND l.user_id='{user}' AND NOT s.sharing_enabled "
            "AND l.received_at > s.updated_at) THEN "
            "RAISE EXCEPTION 'A location was stored after sharing was revoked'; END IF; END $$;")

    # Authentication and access, as for the other sharing routes.
    assert revoke(None)[0] == 401
    status, problem = revoke(None, bearer=token('not-a-uuid'))
    assert status == 401 and problem['code'] == 'INVALID_TOKEN'
    for status, problem in (revoke(other_viewer), revoke(viewer, group_id=str(uuid.uuid4()))):
        assert status == 404 and problem['code'] == 'GROUP_NOT_FOUND'
    status, problem = revoke(viewer, group_id='not-a-uuid')
    assert status == 400 and problem['code'] == 'INVALID_PARAMETER'

    # Baseline: the sharer is visible in both groups and their updates arrive.
    retry_key = str(uuid.uuid4())
    status, accepted = upload(key=retry_key)
    assert status == 201
    assert visible_to(viewer, sharer)['location']['locationId'] == accepted['locationId']
    assert visible_to(other_viewer, sharer, other_group) is not None
    # A recording the phone captured before revoking but has not uploaded yet (e.g. offline queue).
    queued_recording = now()
    time.sleep(0.01)

    # Criterion 1: DELETE revokes, and the very next read already hides the user.
    status, revoked = revoke(sharer)
    assert status == 200 and revoked['groupId'] == group and revoked['userId'] == sharer
    assert revoked['sharingEnabled'] is False and revoked['shareLiveLocation'] is False
    assert revoked['updatedAt'] is not None
    for _ in range(5):
        assert visible_to(viewer, sharer) is None
    assert call('GET', sharer) == (200, revoked)

    # Criterion 2: no further updates reach the group.
    status, problem = upload()
    assert status == 403 and problem['code'] == 'LOCATION_SHARING_DISABLED'
    status, problem = upload(key=retry_key)
    assert status == 403 and problem['code'] == 'LOCATION_SHARING_DISABLED', 'Retry bypassed revocation'
    status, problem = upload(recorded_at=queued_recording)
    assert status == 403
    assert visible_to(viewer, sharer) is None
    assert_nothing_received_after_revocation(sharer)

    # Revocation is per group: the other group still sees the user and receives updates.
    status, other_point = upload(group_id=other_group)
    assert status == 201
    assert visible_to(other_viewer, sharer, other_group)['location']['locationId'] == other_point['locationId']
    status, other_settings = call('GET', sharer, group_id=other_group)
    assert status == 200 and other_settings['sharingEnabled'] is True

    # Revoking again changes nothing, including the consent timestamp.
    assert revoke(sharer) == (200, revoked)

    # Revocation survives an API and database restart.
    restart(restart_database=True)
    assert call('GET', sharer) == (200, revoked)
    assert visible_to(viewer, sharer) is None
    assert upload()[0] == 403

    # Re-enabling sharing does not release recordings made before or during the revocation.
    status, reenabled = call('PUT', sharer, body={'sharingEnabled': True, 'shareLiveLocation': True})
    assert status == 200 and instant(reenabled['updatedAt']) > instant(revoked['updatedAt'])
    status, problem = upload(recorded_at=queued_recording)
    assert status == 403 and problem['code'] == 'LOCATION_CONSENT_CHANGED'
    assert visible_to(viewer, sharer)['location']['locationId'] == accepted['locationId']
    time.sleep(0.01)
    status, resumed = upload()
    assert status == 201
    assert visible_to(viewer, sharer)['location']['locationId'] == resumed['locationId']

    # Revoking while uploads are in flight: uploads and the revocation are serialized, nothing is
    # stored after the revocation, and every upload that starts after DELETE returned is refused.
    stop = threading.Event()
    revoked_at = []
    outcomes = []

    def stream():
        while not stop.is_set():
            started = time.monotonic()
            status, _ = upload()
            outcomes.append((started, status))

    with ThreadPoolExecutor(max_workers=4) as pool:
        workers = [pool.submit(stream) for _ in range(4)]
        time.sleep(0.3)
        status, concurrent = revoke(sharer)
        revoked_at.append(time.monotonic())
        assert status == 200 and concurrent['sharingEnabled'] is False
        assert visible_to(viewer, sharer) is None
        time.sleep(0.3)
        stop.set()
        for worker in workers:
            worker.result()
    assert {status for _, status in outcomes} <= {201, 403}, 'Unexpected upload status during revocation'
    assert any(status == 201 for _, status in outcomes), 'No upload completed before the revocation'
    late = [status for started, status in outcomes if started > revoked_at[0]]
    assert late and all(status == 403 for status in late), 'An upload after revocation was accepted'
    assert visible_to(viewer, sharer) is None
    assert_nothing_received_after_revocation(sharer)

    # Revoking without saved preferences stores an explicit "not sharing" state.
    status, never_shared = revoke(newcomer)
    assert status == 200 and never_shared['sharingEnabled'] is False and never_shared['updatedAt'] is not None
    assert visible_to(viewer, newcomer) is None and upload(newcomer)[0] == 403

    # Restore the shared fixture for the harness's remaining common checks.
    quoted = ','.join("'" + value + "'" for value in created)
    sql(f"DELETE FROM locations WHERE location_id IN ({quoted});")
    sql(f"DELETE FROM shares WHERE group_id='{group}' AND user_id='{newcomer}';")
    sql(f"UPDATE shares SET sharing_enabled=true, share_live_location=true, updated_at='2026-01-01T00:00:00Z' "
        f"WHERE user_id='{sharer}';")
    print('PASS: immediate revocation, refused updates/retries/queued recordings, per-group scope, '
          'idempotency, persistence and concurrent uploads')


if __name__ == '__main__':
    main(verify_upload=verify)
