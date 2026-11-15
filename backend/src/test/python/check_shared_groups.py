"""SCRUM-18 (T-17): list of the groups I share my location with, over real HTTP and PostgreSQL.

From backend/: mvn -q -DskipTests package && python3 src/test/python/check_shared_groups.py
Uses the disposable PostgreSQL harness in check_latest_locations.py; never connects to Neon.

Acceptance criteria checked:
1. All groups with active sharing are listed, and only those: the list matches who can actually see
   the user's location.
2. For temporary sharing, the remaining time is shown.
"""
import json
import time
import urllib.error
import urllib.request

from check_latest_locations import main


def verify(sql, latest, restart, base, token, group, other_group, ids):
    # Fixture (see check_latest_locations.py): `group` is named "Test", `other_group` "Other".
    # ids[1] shares in both groups; ids[2] shares in `group`; ids[3] shares only in `other_group`
    # (disabled in `group`); ids[4] has live location off in `group`; ids[5] has no saved preferences.
    # ids[0] reads `group` and ids[6] reads `other_group`.
    sharer = ids[1]
    readers = {group: ids[0], other_group: ids[6]}
    sql("UPDATE shares SET updated_at='2026-01-01T00:00:00Z';")

    def listed(user=sharer, *, bearer=None):
        credential = bearer if bearer is not None else (token(user) if user else None)
        headers = {'Authorization': 'Bearer ' + credential} if credential else {}
        req = urllib.request.Request(f'{base}/api/v1/users/me/sharing', headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=10) as response:
                assert 'no-store' in response.headers.get('Cache-Control', '')
                return response.status, json.load(response)
        except urllib.error.HTTPError as error:
            if error.code == 401:
                assert error.headers.get('WWW-Authenticate', '').startswith('Bearer')
            content = error.read()
            return error.code, json.loads(content) if content else None

    def items(user=sharer):
        status, body = listed(user)
        assert status == 200 and set(body) == {'items'}
        return body['items']

    def group_ids(user=sharer):
        return [item['groupId'] for item in items(user)]

    def sharing(method, group_id, body=None, user=sharer):
        headers = {'Authorization': 'Bearer ' + token(user)}
        data = json.dumps(body).encode() if body is not None else None
        if data is not None:
            headers['Content-Type'] = 'application/json'
        req = urllib.request.Request(f'{base}/api/v1/groups/{group_id}/me/sharing', data=data,
                                     headers=headers, method=method)
        with urllib.request.urlopen(req, timeout=10) as response:
            return json.load(response)

    def visible_in(group_id, user=sharer):
        status, page = latest(readers[group_id], group_id=group_id)
        assert status == 200
        return any(item['userId'] == user for item in page['items'])

    def assert_matches_access(user=sharer):
        # The list must agree with what the other members actually see.
        listed_groups = set(group_ids(user))
        assert listed_groups == {g for g in readers if visible_in(g, user)}, 'List differs from real access'

    # Authentication: the list is always the caller's own.
    assert listed(None)[0] == 401
    status, problem = listed(None, bearer=token('not-a-uuid'))
    assert status == 401 and problem['code'] == 'INVALID_TOKEN'

    # Criterion 1: every group with active sharing, sorted by name, and nothing else.
    shared = items()
    assert [item['groupId'] for item in shared] == [other_group, group]
    assert [item['groupName'] for item in shared] == ['Other', 'Test']
    for item in shared:
        assert item['expiresAt'] is None and item['remainingSeconds'] is None and item['updatedAt']
    assert_matches_access()
    assert group_ids(ids[2]) == [group]
    assert group_ids(ids[3]) == [other_group], 'A group with sharing disabled was listed'
    assert group_ids(ids[4]) == [], 'A group with live location off was listed'
    assert group_ids(ids[5]) == [], 'Membership alone was listed as sharing'
    assert group_ids(ids[0]) == []
    for user in (ids[2], ids[3], ids[4]):
        assert_matches_access(user)

    # Criterion 2: temporary sharing shows the remaining time, computed by the server.
    temporary = sharing('PUT', group, {'sharingEnabled': True, 'shareLiveLocation': True, 'durationMinutes': 30})
    entry = next(item for item in items() if item['groupId'] == group)
    assert entry['expiresAt'] == temporary['expiresAt'] and entry['updatedAt'] == temporary['updatedAt']
    assert 30 * 60 - 5 <= entry['remainingSeconds'] <= 30 * 60
    unlimited = next(item for item in items() if item['groupId'] == other_group)
    assert unlimited['expiresAt'] is None and unlimited['remainingSeconds'] is None

    # The remaining time counts down, and the group leaves the list when sharing expires.
    sql(f"UPDATE shares SET expires_at = clock_timestamp() + interval '3 seconds' "
        f"WHERE group_id='{group}' AND user_id='{sharer}';")
    first = next(item for item in items() if item['groupId'] == group)['remainingSeconds']
    assert 1 <= first <= 3
    deadline = time.monotonic() + 10
    while group in group_ids():
        remaining = next(item for item in items() if item['groupId'] == group)['remainingSeconds']
        assert 1 <= remaining <= first, 'Remaining time did not count down'
        assert time.monotonic() < deadline, 'Expired sharing stayed in the list'
        time.sleep(0.2)
    assert group_ids() == [other_group]
    assert_matches_access()

    # Manual changes are reflected immediately.
    sharing('DELETE', other_group)
    assert group_ids() == []
    sharing('PUT', group, {'sharingEnabled': True, 'shareLiveLocation': True})
    sharing('PUT', other_group, {'sharingEnabled': True, 'shareLiveLocation': False})
    assert group_ids() == [group]
    assert_matches_access()
    sharing('PUT', other_group, {'sharingEnabled': True, 'shareLiveLocation': True})
    assert group_ids() == [other_group, group]

    # Persisted: the list is the same after an API and database restart.
    before = items()
    restart(restart_database=True)
    assert items() == before

    # Leaving a group removes it from the list (the preferences go with the membership).
    sql("INSERT INTO users(user_id,email,password_hash,display_name) VALUES "
        "('00000000-0000-0000-0000-000000000099','leaver@example.invalid','x','Leaver');")
    leaver = '00000000-0000-0000-0000-000000000099'
    sql(f"INSERT INTO group_members(group_id,user_id) VALUES ('{other_group}','{leaver}');")
    sharing('PUT', other_group, {'sharingEnabled': True, 'shareLiveLocation': True}, user=leaver)
    assert group_ids(leaver) == [other_group]
    sql(f"DELETE FROM group_members WHERE group_id='{other_group}' AND user_id='{leaver}';")
    assert group_ids(leaver) == []
    sql(f"DELETE FROM users WHERE user_id='{leaver}';")

    # Restore the shared fixture for the harness's remaining common checks.
    sql(f"UPDATE shares SET sharing_enabled=true, share_live_location=true, expires_at=NULL, "
        f"updated_at='2026-01-01T00:00:00Z' WHERE user_id='{sharer}';")
    print('PASS: active groups only, matching real access, remaining time and countdown, expiry, '
          'manual changes, persistence and leaving a group')


if __name__ == '__main__':
    main(verify_upload=verify)
