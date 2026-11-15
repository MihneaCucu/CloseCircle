"""SCRUM-15 (T-04): per-group sharing settings over real HTTP and PostgreSQL.

From backend/: mvn -q -DskipTests package && python3 src/test/python/check_group_sharing.py
Uses the disposable PostgreSQL harness in check_latest_locations.py; never connects to Neon.

Acceptance criteria checked:
1. Sharing can be turned on/off separately for each group.
2. Only members of groups where sharing is on can see the user's location.
3. The sharing state is persisted (survives an API and database restart).
"""
from datetime import datetime, timezone
import json
import time
import urllib.error
import urllib.request
import uuid

from check_latest_locations import main


def verify(sql, latest, restart, base, token, group, other_group, ids):
    # Fixture roles (see check_latest_locations.py): ids[0] reads; ids[1] is a member of both
    # groups and shares in both; ids[5] is a member of `group` with no saved preferences;
    # ids[6] is a member of `other_group` only.
    viewer, both_groups, newcomer, outsider = ids[0], ids[1], ids[5], ids[6]
    on = {'sharingEnabled': True, 'shareLiveLocation': True}
    off = {'sharingEnabled': False, 'shareLiveLocation': True}
    created = []

    def call(method, user, *, group_id=group, body=None, raw=None, bearer=None):
        headers = {}
        credential = bearer if bearer is not None else (token(user) if user else None)
        if credential:
            headers['Authorization'] = 'Bearer ' + credential
        data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
        if data is not None:
            headers['Content-Type'] = 'application/json'
        req = urllib.request.Request(f'{base}/api/v1/groups/{group_id}/me/sharing', data=data,
                                     headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=10) as response:
                assert 'no-store' in response.headers.get('Cache-Control', '')
                return response.status, json.load(response)
        except urllib.error.HTTPError as error:
            if error.code == 401:
                assert error.headers.get('WWW-Authenticate', '').startswith('Bearer')
            content = error.read()
            return error.code, json.loads(content) if content else None

    def put(user, body, **kwargs):
        return call('PUT', user, body=body, **kwargs)

    def get(user, **kwargs):
        return call('GET', user, **kwargs)

    def visible_to(reader, member, group_id=group):
        status, page = latest(reader, group_id=group_id)
        assert status == 200
        return next((item for item in page['items'] if item['userId'] == member), None)

    def upload(user, group_id=group):
        body = {'latitude': 45.5, 'longitude': 25.5,
                'recordedAt': datetime.now(timezone.utc).isoformat()}
        req = urllib.request.Request(f'{base}/api/v1/groups/{group_id}/locations',
                                     data=json.dumps(body).encode(), method='POST', headers={
                                         'Content-Type': 'application/json',
                                         'Authorization': 'Bearer ' + token(user),
                                         'Idempotency-Key': str(uuid.uuid4())})
        try:
            with urllib.request.urlopen(req, timeout=10) as response:
                point = json.load(response)
                created.append(point['locationId'])
                return response.status, point
        except urllib.error.HTTPError as error:
            return error.code, None

    def instant(value):
        return datetime.fromisoformat(value.replace('Z', '+00:00'))

    # Authentication and access: the caller comes only from a valid JWT.
    assert get(None)[0] == 401
    assert put(None, on)[0] == 401
    status, problem = get(None, bearer=token('not-a-uuid'))
    assert status == 401 and problem['code'] == 'INVALID_TOKEN'
    assert put(None, on, bearer=token('0-0-0-0-6'))[0] == 401
    for status, problem in (get(outsider), put(outsider, on),
                            get(viewer, group_id=str(uuid.uuid4())), put(viewer, on, group_id=str(uuid.uuid4()))):
        assert status == 404 and problem['code'] == 'GROUP_NOT_FOUND'
    status, problem = get(viewer, group_id='not-a-uuid')
    assert status == 400 and problem['code'] == 'INVALID_PARAMETER'
    sql(f"DO $$ BEGIN IF EXISTS (SELECT 1 FROM shares WHERE user_id='{outsider}' AND group_id='{group}') "
        "THEN RAISE EXCEPTION 'Non-member preferences were stored'; END IF; END $$;")

    # Strict body validation; nothing is stored for rejected requests.
    for body in ({}, [], {'sharingEnabled': True}, {'shareLiveLocation': True},
                 dict(on, sharingEnabled='true'), dict(on, sharingEnabled=None), dict(on, shareLiveLocation=1),
                 dict(on, userId=viewer), dict(on, groupId=other_group)):
        status, problem = put(newcomer, body)
        assert status == 400 and problem['code'] == 'INVALID_SHARING_REQUEST', body
    assert put(newcomer, None, raw=b'{bad-json')[0] == 400

    # A member who never saved preferences is not sharing and is not visible.
    status, settings = get(newcomer)
    assert status == 200 and settings == {'groupId': group, 'userId': newcomer, 'sharingEnabled': False,
                                          'shareLiveLocation': False, 'expiresAt': None,
                                          'updatedAt': None}
    assert visible_to(viewer, newcomer) is None
    assert upload(newcomer)[0] == 403

    # Turning sharing on makes the member visible to that group and allows uploads.
    before = datetime.now(timezone.utc)
    status, enabled = put(newcomer, on)
    assert status == 200 and enabled['groupId'] == group and enabled['userId'] == newcomer
    assert enabled['sharingEnabled'] is True and enabled['shareLiveLocation'] is True
    assert instant(enabled['updatedAt']) >= before.replace(microsecond=0)
    assert get(newcomer) == (200, enabled)
    member = visible_to(viewer, newcomer)
    assert member is not None and member['displayName'] == 'User 6' and member['location'] is None
    time.sleep(0.01)
    status, point = upload(newcomer)
    assert status == 201
    assert visible_to(viewer, newcomer)['location']['locationId'] == point['locationId']

    # Repeating the same settings is idempotent and keeps the consent timestamp.
    assert put(newcomer, on) == (200, enabled)

    # Each half of the setting hides the member independently.
    status, live_off = put(newcomer, {'sharingEnabled': True, 'shareLiveLocation': False})
    assert status == 200 and instant(live_off['updatedAt']) >= instant(enabled['updatedAt'])
    assert visible_to(viewer, newcomer) is None
    status, disabled = put(newcomer, off)
    assert status == 200 and disabled['sharingEnabled'] is False
    assert visible_to(viewer, newcomer) is None
    assert upload(newcomer)[0] == 403
    # Re-enabling advances the consent boundary used by uploads.
    status, reenabled = put(newcomer, on)
    assert status == 200 and instant(reenabled['updatedAt']) >= instant(disabled['updatedAt'])
    assert visible_to(viewer, newcomer)['location']['locationId'] == point['locationId']

    # Settings are per group: turning sharing off in one group does not affect the other.
    assert visible_to(viewer, both_groups) is not None
    assert visible_to(outsider, both_groups, other_group) is not None
    status, off_here = put(both_groups, off)
    assert status == 200 and off_here['groupId'] == group
    assert visible_to(viewer, both_groups) is None
    assert visible_to(outsider, both_groups, other_group) is not None
    status, other_settings = get(both_groups, group_id=other_group)
    assert status == 200 and other_settings['groupId'] == other_group and other_settings['sharingEnabled'] is True
    assert upload(both_groups)[0] == 403
    assert upload(both_groups, other_group)[0] == 201
    # ...and the reverse direction.
    assert put(both_groups, off, group_id=other_group)[0] == 200
    assert put(both_groups, on)[0] == 200
    assert visible_to(viewer, both_groups) is not None
    assert visible_to(outsider, both_groups, other_group) is None

    # Persistence: preferences survive an API and database restart.
    saved = {(g, u): get(u, group_id=g)[1] for g, u in ((group, newcomer), (group, both_groups),
                                                       (other_group, both_groups))}
    restart(restart_database=True)
    for (g, u), value in saved.items():
        assert get(u, group_id=g) == (200, value)
    assert visible_to(viewer, both_groups) is not None
    assert visible_to(outsider, both_groups, other_group) is None

    # Removing a member drops their preferences with the membership; rejoining starts unshared.
    sql(f"DELETE FROM group_members WHERE group_id='{group}' AND user_id='{newcomer}';")
    assert get(newcomer)[0] == 404 and put(newcomer, on)[0] == 404
    sql(f"INSERT INTO group_members(group_id,user_id,joined_at) VALUES ('{group}','{newcomer}','2026-01-01T00:00:00Z');")
    assert get(newcomer)[1]['sharingEnabled'] is False and visible_to(viewer, newcomer) is None

    # Restore the shared fixture for the harness's remaining common checks.
    quoted = ','.join("'" + value + "'" for value in created)
    sql(f"DELETE FROM locations WHERE location_id IN ({quoted});")
    sql(f"UPDATE shares SET sharing_enabled=true, share_live_location=true, updated_at='2026-01-01T00:00:00Z' "
        f"WHERE user_id='{both_groups}';")
    print('PASS: per-group sharing toggle, visibility, validation, isolation, idempotency and persistence')


if __name__ == '__main__':
    main(verify_upload=verify)
