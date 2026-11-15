"""Exercise SCRUM-22 and combined freshness/accuracy through the real API."""
from check_latest_locations import main


def verify(sql, request, group, other_group, ids):
    def by_user():
        status, page = request(ids[0])
        assert status == 200
        return {item['userId']: item for item in page['items']}

    items = by_user()
    assert items[ids[1]]['accuracy'] == {'status': 'ACCEPTABLE', 'poorAccuracyThresholdM': 100.0}
    assert items[ids[2]]['accuracy']['status'] == 'UNKNOWN'
    assert items[ids[2]]['location'] is None
    assert items[ids[1]]['freshness']['status'] == 'LAST_KNOWN'

    for value, expected in [('100', 'ACCEPTABLE'), ('100.001', 'POOR'), ('NULL', 'UNKNOWN'),
                            ("'NaN'", 'UNKNOWN'), ("'Infinity'", 'UNKNOWN')]:
        sql(f"UPDATE locations SET accuracy_m={value} WHERE group_id='{group}' AND user_id='{ids[1]}';")
        item = by_user()[ids[1]]
        assert item['accuracy']['status'] == expected
        if expected == 'UNKNOWN':
            assert item['location']['accuracyM'] is None
        # Poor accuracy does not fabricate freshness or change the stored point.
        assert item['location']['latitude'] == 3 and item['freshness']['status'] == 'LAST_KNOWN'

    sql(f"UPDATE shares SET sharing_enabled=false WHERE group_id='{group}' AND user_id='{ids[1]}';")
    assert ids[1] not in by_user()
    assert {item['userId'] for item in request(ids[6], group_id=other_group)[1]['items']} == {ids[1], ids[3]}
    sql(f"UPDATE shares SET sharing_enabled=true WHERE group_id='{group}' AND user_id='{ids[1]}';")
    print('PASS: accuracy HTTP metadata, threshold, unknown values and privacy work alongside freshness')


def verify_disabled(sql, request, group, other_group, ids):
    assert all('accuracy' not in item and 'freshness' not in item for item in request(ids[0])[1]['items'])
    print('PASS: unconfigured accuracy preserves the existing response')


if __name__ == '__main__':
    main(verify_disabled)
    main(verify, ('--closecircle.location.poor-accuracy-threshold-m=100',
                  '--closecircle.location.recent-for-seconds=300'))
