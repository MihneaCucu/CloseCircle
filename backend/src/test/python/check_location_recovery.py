"""SCRUM-21 backend read-path recovery checks; no mobile network or upload simulation."""
from datetime import datetime, timedelta, timezone

from check_latest_locations import main


def verify(sql, request, restart, group, other_group, ids):
    def by_user():
        status, page = request(ids[0])
        assert status == 200
        return {item['userId']: item for item in page['items']}

    original = by_user()[ids[1]]['location']
    assert by_user()[ids[1]]['location'] == original
    assert by_user()[ids[1]]['freshness']['status'] == 'LAST_KNOWN'

    # Real API and database processes restart while persisted data stays intact.
    restart(restart_database=True)
    item = by_user()[ids[1]]
    assert item['location'] == original
    assert item['freshness']['status'] == 'LAST_KNOWN'

    # A delayed accepted point must not replace the newer known recording.
    delayed_id = '90000000-0000-0000-0000-000000000001'
    resumed_id = '90000000-0000-0000-0000-000000000002'
    sql(f"INSERT INTO locations(location_id,group_id,user_id,latitude,longitude,recorded_at) "
        f"VALUES ('{delayed_id}','{group}','{ids[1]}',10,11,'2026-01-01T12:00:00Z');")
    assert by_user()[ids[1]]['location'] == original

    # Seed a newly accepted recording; this deliberately does not claim to test
    # the still-unimplemented upload endpoint or Android's offline queue.
    recorded = datetime.now(timezone.utc) - timedelta(seconds=30)
    sql(f"INSERT INTO locations(location_id,group_id,user_id,latitude,longitude,accuracy_m,recorded_at) "
        f"VALUES ('{resumed_id}','{group}','{ids[1]}',20,21,250,'{recorded.isoformat()}');")
    item = by_user()[ids[1]]
    assert item['location']['locationId'] == resumed_id and item['location']['latitude'] == 20
    assert item['freshness']['status'] == 'RECENT'
    assert item['accuracy']['status'] == 'POOR'

    # The saved point never bypasses sharing or membership after restart.
    sql(f"UPDATE shares SET sharing_enabled=false WHERE group_id='{group}' AND user_id='{ids[1]}';")
    restart()
    assert ids[1] not in by_user()
    assert ids[1] in {item['userId'] for item in request(ids[6], group_id=other_group)[1]['items']}
    sql(f"DELETE FROM group_members WHERE group_id='{group}' AND user_id='{ids[0]}';")
    assert request(ids[0])[0] == 404

    # Restore fixtures for the harness's common authorization/revocation checks.
    sql(f"INSERT INTO group_members(group_id,user_id,joined_at) VALUES "
        f"('{group}','{ids[0]}','2026-01-01T00:00:00Z');")
    sql(f"UPDATE shares SET sharing_enabled=true WHERE group_id='{group}' AND user_id='{ids[1]}';")
    sql(f"DELETE FROM locations WHERE location_id IN ('{delayed_id}','{resumed_id}');")
    print('PASS: persisted last-known point, API/database restart, delayed/resumed recordings and renewed authorization')


if __name__ == '__main__':
    main(app_args=('--closecircle.location.recent-for-seconds=300',
                   '--closecircle.location.poor-accuracy-threshold-m=100'), verify_recovery=verify)
