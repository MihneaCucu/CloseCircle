"""SCRUM-17 (T-11): temporary location sharing, over real HTTP and PostgreSQL.

From backend/: mvn -q -DskipTests package && python3 src/test/python/check_temporary_sharing.py
Uses the disposable PostgreSQL harness in check_latest_locations.py; never connects to Neon.

Acceptance criteria checked:
1. The user can choose a duration for sharing.
2. When the duration expires, sharing stops automatically (no request from the user).
3. The user can stop sharing manually before it expires.
"""
from datetime import datetime, timedelta, timezone
import json
import time
import urllib.error
import urllib.request
import uuid

from check_latest_locations import main


def verify(sql, latest, restart, base, token, group, other_group, ids):
    # Fixture roles (see check_latest_locations.py): ids[0] reads `group`; ids[1] shares in both
    # groups; ids[6] reads `other_group`.
    viewer, sharer, other_viewer = ids[0], ids[1], ids[6]
    created = []
    sql("UPDATE shares SET updated_at='2026-01-01T00:00:00Z';")

    def call(method, body=None, *, user=sharer, group_id=group):
        headers = {'Authorization': 'Bearer ' + token(user)}
        data = json.dumps(body).encode() if body is not None else None
        if data is not None:
            headers['Content-Type'] = 'application/json'
        req = urllib.request.Request(f'{base}/api/v1/groups/{group_id}/me/sharing', data=data,
                                     headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=10) as response:
                return response.status, json.load(response)
        except urllib.error.HTTPError as error:
            content = error.read()
            return error.code, json.loads(content) if content else None

    def share(minutes=None, **kwargs):
        body = {'sharingEnabled': True, 'shareLiveLocation': True}
        if minutes is not None:
            body['durationMinutes'] = minutes
        return call('PUT', body, **kwargs)

    def now(offset_seconds=0):
        return (datetime.now(timezone.utc) + timedelta(seconds=offset_seconds)).isoformat()

    def instant(value):
        return datetime.fromisoformat(value.replace('Z', '+00:00'))

    def upload(*, group_id=group, recorded_at=None):
        body = {'latitude': 44.43, 'longitude': 26.1, 'recordedAt': recorded_at or now()}
        req = urllib.request.Request(f'{base}/api/v1/groups/{group_id}/locations',
                                     data=json.dumps(body).encode(), method='POST', headers={
                                         'Content-Type': 'application/json',
                                         'Authorization': 'Bearer ' + token(sharer),
                                         'Idempotency-Key': str(uuid.uuid4())})
        try:
            with urllib.request.urlopen(req, timeout=10) as response:
                point = json.load(response)
                created.append(point['locationId'])
                return response.status, point
        except urllib.error.HTTPError as error:
            content = error.read()
            return error.code, json.loads(content) if content else None

    def visible(reader=viewer, group_id=group):
        status, page = latest(reader, group_id=group_id)
        assert status == 200
        return next((item for item in page['items'] if item['userId'] == sharer), None)

    def expire_in(seconds):
        # Only the expiry is shortened, so the test need not wait a whole minute.
        sql(f"UPDATE shares SET expires_at = clock_timestamp() + interval '{seconds} seconds' "
            f"WHERE group_id='{group}' AND user_id='{sharer}';")

    # Criterion 1: choose a duration. The server computes the expiry from its own clock.
    status, temporary = share(60)
    assert status == 200 and temporary['sharingEnabled'] is True
    assert instant(temporary['expiresAt']) - instant(temporary['updatedAt']) == timedelta(minutes=60)
    assert call('GET') == (200, temporary)
    assert visible() is not None
    status, point = upload()
    assert status == 201 and visible()['location']['locationId'] == point['locationId']

    for duration in (0, -5, 10081, 1.5, '60', True, {}):
        status, problem = share(duration)
        assert status == 400 and problem['code'] == 'INVALID_SHARING_REQUEST', duration
    status, problem = call('PUT', {'sharingEnabled': False, 'shareLiveLocation': True, 'durationMinutes': 30})
    assert status == 400 and problem['code'] == 'INVALID_SHARING_REQUEST'
    assert call('GET') == (200, temporary), 'A rejected request changed the settings'

    # Criterion 2: sharing stops by itself at the expiry, without any request from the user.
    expire_in(2)
    assert visible() is not None
    deadline = time.monotonic() + 10
    while visible() is not None:
        assert time.monotonic() < deadline, 'Sharing did not stop at its expiry'
        time.sleep(0.1)
    status, expired = call('GET')
    assert status == 200 and expired['sharingEnabled'] is False and expired['expiresAt'] is not None
    assert instant(expired['expiresAt']) <= datetime.now(timezone.utc)
    status, problem = upload()
    assert status == 403 and problem['code'] == 'LOCATION_SHARING_DISABLED'
    recorded_while_expired = now()
    # Expiry is per group: the other group keeps seeing the user.
    assert visible(other_viewer, other_group) is not None
    assert upload(group_id=other_group)[0] == 201
    # Expired sharing stays stopped across an API and database restart.
    restart(restart_database=True)
    assert call('GET') == (200, expired)
    assert visible() is None

    # Sharing again starts a new period; recordings from the stopped period are not released.
    time.sleep(0.01)
    status, again = share(60)
    assert status == 200 and again['sharingEnabled'] is True
    assert instant(again['updatedAt']) > instant(expired['expiresAt'])
    status, problem = upload(recorded_at=recorded_while_expired)
    assert status == 403 and problem['code'] == 'LOCATION_CONSENT_CHANGED'
    time.sleep(0.01)
    status, point = upload()
    assert status == 201 and visible()['location']['locationId'] == point['locationId']

    # A recording time-stamped after the expiry is refused, even within the clock tolerance.
    expire_in(5)
    status, problem = upload(recorded_at=now(10))
    assert status == 403 and problem['code'] == 'LOCATION_SHARING_DISABLED'
    assert upload()[0] == 201

    # Criterion 3: stop manually before the expiry, with DELETE or PUT.
    status, temporary = share(60)
    assert status == 200 and temporary['sharingEnabled'] is True
    status, stopped = call('DELETE')
    assert status == 200 and stopped['sharingEnabled'] is False and stopped['expiresAt'] is None
    assert visible() is None
    assert upload()[0] == 403
    assert share(30)[0] == 200
    status, stopped = call('PUT', {'sharingEnabled': False, 'shareLiveLocation': True})
    assert status == 200 and stopped['sharingEnabled'] is False and stopped['expiresAt'] is None
    assert visible() is None

    # Changing the period: extend it, or switch to sharing without a time limit.
    status, shorter = share(30)
    status, longer = share(120)
    assert status == 200 and instant(longer['expiresAt']) > instant(shorter['expiresAt'])
    status, unlimited = share()
    assert status == 200 and unlimited['expiresAt'] is None and unlimited['sharingEnabled'] is True
    assert instant(unlimited['updatedAt']) > instant(longer['updatedAt'])
    assert share() == (200, unlimited), 'Repeating unlimited sharing is not idempotent'

    # Restore the shared fixture for the harness's remaining common checks.
    quoted = ','.join("'" + value + "'" for value in created)
    sql(f"DELETE FROM locations WHERE location_id IN ({quoted});")
    sql(f"UPDATE shares SET sharing_enabled=true, share_live_location=true, expires_at=NULL, "
        f"updated_at='2026-01-01T00:00:00Z' WHERE user_id='{sharer}';")
    print('PASS: chosen duration, automatic stop at expiry, per-group scope, persistence, '
          'new period after expiry, manual stop and changing the period')


if __name__ == '__main__':
    main(app_args=('--closecircle.location.upload-max-future-seconds=60',), verify_upload=verify)
