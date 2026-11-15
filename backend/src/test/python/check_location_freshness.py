"""Exercise SCRUM-20 through the authenticated API and disposable PostgreSQL."""
from datetime import datetime, timedelta, timezone
import time

from check_latest_locations import main


def verify(sql, request, group, other_group, ids):
    def by_user():
        status, page = request(ids[0])
        assert status == 200
        return {item['userId']: item for item in page['items']}

    items = by_user()
    assert items[ids[1]]['freshness']['status'] == 'LAST_KNOWN'
    assert items[ids[2]]['freshness']['status'] == 'UNKNOWN'
    assert items[ids[2]]['location'] is None
    assert items[ids[1]]['freshness']['evaluatedAt'] == items[ids[2]]['freshness']['evaluatedAt']
    assert items[ids[1]]['freshness']['recentForSeconds'] == 300

    # A recent receipt must not disguise a device's old recording time.
    recorded = datetime.now(timezone.utc) - timedelta(days=1)
    sql(f"UPDATE locations SET recorded_at='{recorded.isoformat()}', received_at=now() "
        f"WHERE group_id='{group}' AND user_id='{ids[1]}';")
    assert by_user()[ids[1]]['freshness']['status'] == 'LAST_KNOWN'

    recorded = datetime.now(timezone.utc) - timedelta(seconds=30)
    sql(f"UPDATE locations SET recorded_at='{recorded.isoformat()}' "
        f"WHERE group_id='{group}' AND user_id='{ids[1]}';")
    items = by_user()
    assert items[ids[1]]['freshness']['status'] == 'RECENT'
    assert datetime.fromisoformat(items[ids[1]]['location']['recordedAt'].replace('Z', '+00:00')) == recorded

    future = datetime.now(timezone.utc) + timedelta(days=1)
    sql(f"UPDATE locations SET recorded_at='{future.isoformat()}' "
        f"WHERE group_id='{group}' AND user_id='{ids[1]}';")
    assert by_user()[ids[1]]['freshness']['status'] == 'UNKNOWN'

    # Restore the fixture before the shared update/revocation checks.
    sql(f"UPDATE locations SET recorded_at='2026-01-02T00:00:00Z' "
        f"WHERE group_id='{group}' AND user_id='{ids[1]}';")
    print('PASS: freshness in HTTP responses uses recording time, shared evaluation time and configured window')


def verify_aging(sql, request, group, other_group, ids):
    recorded = datetime.now(timezone.utc)
    sql(f"UPDATE locations SET recorded_at='{recorded.isoformat()}' "
        f"WHERE group_id='{group}' AND user_id='{ids[1]}';")

    def member():
        status, page = request(ids[0])
        assert status == 200
        return next(item for item in page['items'] if item['userId'] == ids[1])

    recent = member()
    assert recent['freshness']['status'] == 'RECENT'
    deadline = time.monotonic() + 12
    while True:
        last_known = member()
        if last_known['freshness']['status'] == 'LAST_KNOWN':
            break
        assert time.monotonic() < deadline, 'Point never aged out of the configured window'
        time.sleep(0.2)
    assert recent['location'] == last_known['location'], 'Aging must preserve the last point and timestamp'
    assert recent['freshness']['evaluatedAt'] < last_known['freshness']['evaluatedAt']
    sql(f"UPDATE locations SET recorded_at='2026-01-02T00:00:00Z' "
        f"WHERE group_id='{group}' AND user_id='{ids[1]}';")
    print('PASS: repeated reads age RECENT into LAST_KNOWN without replacing the point')


def verify_disabled(sql, request, group, other_group, ids):
    assert all('freshness' not in item for item in request(ids[0])[1]['items'])
    print('PASS: unconfigured freshness preserves the existing response')


if __name__ == '__main__':
    main(verify_disabled)
    main(verify, ('--closecircle.location.recent-for-seconds=300',))
    main(verify_aging, ('--closecircle.location.recent-for-seconds=5',))
