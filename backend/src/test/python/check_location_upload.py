"""SCRUM-43: real authenticated uploads, persistence, retries and read-back."""
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
import json
import time
import urllib.error
import urllib.request
import uuid

from check_latest_locations import main


def verify(sql, latest, restart, base, token, group, other_group, ids):
    sql(f"UPDATE shares SET updated_at='2026-01-01T00:00:00Z';")
    recorded = (datetime.now(timezone.utc) - timedelta(seconds=30)).isoformat()
    payload = {'latitude': 11.25, 'longitude': 22.5, 'accuracyM': 7.5, 'recordedAt': recorded}
    created = set()

    def upload(body=payload, *, user=ids[1], group_id=group, key=None, raw=None, bearer=None):
        headers = {'Content-Type': 'application/json'}
        if user is not None or bearer is not None:
            headers['Authorization'] = 'Bearer ' + (bearer if bearer is not None else token(user))
        if key is not None:
            headers['Idempotency-Key'] = key
        data = raw if raw is not None else json.dumps(body).encode()
        req = urllib.request.Request(f'{base}/api/v1/groups/{group_id}/locations', data=data,
                                     headers=headers, method='POST')
        try:
            with urllib.request.urlopen(req, timeout=10) as response:
                assert 'no-store' in response.headers.get('Cache-Control', '')
                result = json.load(response)
                created.add(result['locationId'])
                return response.status, result
        except urllib.error.HTTPError as error:
            content = error.read()
            return error.code, json.loads(content) if content else None

    def visible():
        return next(item for item in latest(ids[0])[1]['items'] if item['userId'] == ids[1])

    assert upload(user=None)[0] == 401
    assert upload(bearer=token('invalid-subject'))[0] == 401
    assert upload(bearer=token('0-0-0-0-2'))[0] == 401
    assert upload(user=ids[6])[0] == 404
    assert upload(group_id=other_group, user=ids[0])[0] == 404
    for user in (ids[2], ids[3], ids[4], ids[5]):
        # User 3 is a sharer, so temporarily disable it for the disabled checks.
        if user == ids[2]:
            sql(f"UPDATE shares SET sharing_enabled=false WHERE group_id='{group}' AND user_id='{user}';")
        assert upload(user=user)[0] == 403
    sql(f"UPDATE shares SET sharing_enabled=true WHERE group_id='{group}' AND user_id='{ids[2]}';")

    status, point = upload(key=str(uuid.uuid4()))
    assert status == 201
    assert point['userId'] == ids[1] and point['groupId'] == group
    assert point['latitude'] == payload['latitude'] and point['accuracyM'] == 7.5
    assert datetime.fromisoformat(point['recordedAt'].replace('Z', '+00:00')) == datetime.fromisoformat(recorded)
    assert datetime.fromisoformat(point['receivedAt'].replace('Z', '+00:00')) > datetime.fromisoformat(recorded)
    assert visible()['location']['locationId'] == point['locationId']
    assert visible()['freshness']['status'] == 'RECENT'

    # A lost response can be retried without duplicating an accepted recording.
    retry_key = str(uuid.uuid4())
    first_status, first = upload(key=retry_key)
    assert first_status == 201
    assert upload(key=retry_key) == (200, first)
    assert upload(dict(payload, latitude=12), key=retry_key)[0] == 409
    restart()
    assert upload(key=retry_key) == (200, first)
    concurrent_key = str(uuid.uuid4())
    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(lambda _: upload(key=concurrent_key), range(2)))
    assert sorted(status for status, _ in results) == [200, 201]
    assert results[0][1]['locationId'] == results[1][1]['locationId']
    sql(f"DO $$ BEGIN IF (SELECT count(*) FROM locations WHERE group_id='{group}' "
        f"AND user_id='{ids[1]}' AND client_upload_id='{concurrent_key}') <> 1 THEN "
        "RAISE EXCEPTION 'Concurrent retry inserted more than one point'; END IF; END $$;")

    # The same client key is scoped to a group and authenticated member.
    assert upload(group_id=other_group, key=retry_key)[0] == 201
    assert upload(user=ids[2], key=retry_key)[0] == 201

    # Delayed recordings do not displace a newer recorded point.
    last = visible()['location']
    old = dict(payload, latitude=33, recordedAt='2026-01-02T00:00:00Z')
    assert upload(old)[0] == 201
    assert visible()['location'] == last

    for body in [None, [], {}, dict(payload, latitude=None), dict(payload, latitude=91),
                 dict(payload, longitude=-181), dict(payload, latitude='1'),
                 dict(payload, latitude=True), dict(payload, accuracyM=-1),
                 dict(payload, accuracyM='NaN'), dict(payload, recordedAt=None),
                 dict(payload, recordedAt='2026-01-01T12:00:00'),
                 dict(payload, userId=ids[0]), dict(payload, groupId=other_group),
                 dict(payload, receivedAt=recorded),
                 dict(payload, recordedAt=(datetime.now(timezone.utc) + timedelta(days=1)).isoformat())]:
        assert upload(body)[0] == 400, 'Invalid upload was accepted'
    assert upload(raw=b'{bad-json')[0] == 400
    assert upload(raw=b'{"latitude":1e400,"longitude":0,"recordedAt":"2026-01-01T00:00:00Z"}')[0] == 400
    assert upload(key='not-a-uuid')[0] == 400
    assert upload(dict(payload, accuracyM=None))[0] == 201
    without_accuracy = dict(payload)
    del without_accuracy['accuracyM']
    assert upload(without_accuracy)[0] == 201

    # Current consent is checked on retries too. A recording from before the
    # latest preference change cannot be uploaded after re-enabling sharing.
    sql(f"UPDATE shares SET sharing_enabled=false, updated_at=now() WHERE group_id='{group}' AND user_id='{ids[1]}';")
    assert upload(key=retry_key)[0] == 403
    sql(f"UPDATE shares SET sharing_enabled=true WHERE group_id='{group}' AND user_id='{ids[1]}';")
    assert upload()[0] == 403
    sql(f"UPDATE shares SET updated_at='2026-01-01T00:00:00Z' WHERE group_id='{group}' AND user_id='{ids[1]}';")

    # Fixture cleanup preserves the harness's independent common regressions.
    quoted = ','.join("'" + value + "'" for value in created)
    sql(f"DELETE FROM locations WHERE location_id IN ({quoted});")
    sql(f"UPDATE shares SET sharing_enabled=true WHERE group_id='{group}' AND user_id='{ids[2]}';")
    print('PASS: authenticated upload, read-back, restart/concurrent retries, validation, isolation and consent')


def verify_checkpoint(sql, latest, restart, base, token, group, other_group, ids):
    # Only account/membership/consent prerequisites use fixtures. Every point in
    # this scenario enters through the public upload API, as it would on Android.
    sql("UPDATE shares SET updated_at='2026-01-01T00:00:00Z';")
    created = []

    def upload(age):
        recorded = (datetime.now(timezone.utc) - timedelta(seconds=age)).isoformat()
        payload = dict(latitude=44.4, longitude=26.1, accuracyM=8, recordedAt=recorded)
        req = urllib.request.Request(f'{base}/api/v1/groups/{group}/locations',
            data=json.dumps(payload).encode(), method='POST', headers={
                'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token(ids[1]),
                'Idempotency-Key': str(uuid.uuid4())})
        with urllib.request.urlopen(req, timeout=10) as response:
            assert response.status == 201
            point = json.load(response)
            created.append(point['locationId'])
            return point

    def member():
        status, page = latest(ids[0])
        assert status == 200
        return next(item for item in page['items'] if item['userId'] == ids[1])

    old = upload(60)
    last_known = member()
    assert last_known['location'] == old
    assert last_known['freshness']['status'] == 'LAST_KNOWN'
    recent = upload(0)
    current = member()
    assert current['location'] == recent
    assert current['freshness']['status'] == 'RECENT'
    assert current['freshness']['recentForSeconds'] == 5
    deadline = time.monotonic() + 12
    while True:
        aged = member()
        if aged['freshness']['status'] == 'LAST_KNOWN':
            break
        assert time.monotonic() < deadline, 'Uploaded point did not age to last-known'
        time.sleep(0.2)
    assert aged['location'] == recent
    resumed = upload(0)
    assert member()['location'] == resumed
    assert member()['freshness']['status'] == 'RECENT'
    assert latest(ids[0], group_id=other_group)[0] == 404
    sql(f"UPDATE shares SET sharing_enabled=false WHERE group_id='{group}' AND user_id='{ids[1]}';")
    assert all(item['userId'] != ids[1] for item in latest(ids[0])[1]['items'])
    sql(f"UPDATE shares SET sharing_enabled=true WHERE group_id='{group}' AND user_id='{ids[1]}';")
    quoted = ','.join("'" + value + "'" for value in created)
    sql(f"DELETE FROM locations WHERE location_id IN ({quoted});")
    print('PASS: checkpoint HTTP upload/read flow, recent-to-last-known aging, resumed updates and privacy')


if __name__ == '__main__':
    main(app_args=('--closecircle.location.recent-for-seconds=300',), verify_upload=verify)
    main(app_args=('--closecircle.location.recent-for-seconds=5',), verify_upload=verify_checkpoint)
