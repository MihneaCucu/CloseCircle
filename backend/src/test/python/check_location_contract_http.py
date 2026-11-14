"""Check the contract against an existing feature checkout, using only disposable fixtures."""
import argparse
from datetime import datetime, timedelta, timezone
import importlib.util
import json
from pathlib import Path
import urllib.error
import urllib.request

from check_api_contract import check_contract, load_json, SPEC, validator


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--backend-root', required=True, type=Path,
                        help='Trusted feature backend directory with a freshly built jar and PostgreSQL test harness')
    args = parser.parse_args()
    source = args.backend_root.resolve() / 'src/test/python/check_latest_locations.py'
    module_spec = importlib.util.spec_from_file_location('location_harness', source)
    harness = importlib.util.module_from_spec(module_spec)
    module_spec.loader.exec_module(harness)
    contract = load_json(SPEC)
    check_contract(contract)

    def matches(name, payload):
        if not validator(contract, name).is_valid(payload):
            # Never report a ValidationError or payload containing coordinates.
            raise RuntimeError('HTTP payload does not match ' + name)

    def verify(sql, latest, restart, base, token, group, other_group, ids):
        sql("UPDATE shares SET updated_at='2026-01-01T00:00:00Z';")
        recorded = (datetime.now(timezone.utc) - timedelta(seconds=1)).isoformat()
        payload = dict(latitude=12.3456, longitude=-45.6789, accuracyM=12.5, recordedAt=recorded)
        matches('CreateLocationRequest', payload)
        headers = {'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token(ids[1])}
        req = urllib.request.Request(base + f'/api/v1/groups/{group}/locations',
                                     data=json.dumps(payload).encode(), headers=headers, method='POST')
        with urllib.request.urlopen(req, timeout=10) as response:
            assert response.status == 201
            assert 'no-store' in response.headers.get('Cache-Control', '')
            point = json.load(response)
        matches('Location', point)
        status, page = latest(ids[0])
        assert status == 200
        matches('LatestLocationPage', page)
        member = next(item for item in page['items'] if item['userId'] == ids[1])
        assert member['location'] == point
        assert member['freshness']['status'] == 'RECENT'
        assert member['accuracy']['status'] == 'ACCEPTABLE'
        for suffix in ('?cursor=invalid', '?limit=invalid'):
            status, problem = latest(ids[0], suffix)
            assert status == 400
            matches('Problem', problem)
        status, problem = latest(ids[0], group_id=other_group)
        assert status == 404
        matches('Problem', problem)
        invalid = dict(payload, latitude=91)
        assert not validator(contract, 'CreateLocationRequest').is_valid(invalid)
        req = urllib.request.Request(base + f'/api/v1/groups/{group}/locations',
                                     data=json.dumps(invalid).encode(), headers=headers, method='POST')
        try:
            urllib.request.urlopen(req, timeout=10).close()
            raise AssertionError('Invalid upload unexpectedly succeeded')
        except urllib.error.HTTPError as error:
            assert error.code == 400
            matches('Problem', json.load(error))
        sql(f"DELETE FROM locations WHERE location_id='{point['locationId']}';")
        print('PASS: actual upload/read/metadata/error bodies match the location contract')

    harness.main(app_args=('--closecircle.location.recent-for-seconds=300',
                           '--closecircle.location.poor-accuracy-threshold-m=50'), verify_upload=verify)


if __name__ == '__main__':
    main()
