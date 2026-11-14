"""Check the contract against a trusted feature checkout, using only disposable fixtures."""
import argparse
from datetime import datetime, timedelta, timezone
import importlib.util
import json
from pathlib import Path
import urllib.error
import urllib.request
import uuid

from jsonschema import Draft202012Validator, FormatChecker
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
    read_path = '/api/v1/groups/{groupId}/locations/latest'
    upload_path = '/api/v1/groups/{groupId}/locations'

    def matches_schema(schema, payload):
        schema = dict(schema, components=contract['components'])
        if not Draft202012Validator(schema, format_checker=FormatChecker()).is_valid(payload):
            # Never report a ValidationError or payload containing coordinates.
            raise RuntimeError('HTTP payload does not match its declared operation response')

    def matches_response(path, method, status, payload, headers=None):
        response = contract['paths'][path][method]['responses'][str(status)]
        media = 'application/json' if status < 400 else 'application/problem+json'
        matches_schema(response['content'][media]['schema'], payload)
        if headers is not None:
            assert headers.get_content_type() == media
            for name, header in response.get('headers', {}).items():
                value = headers.get(name)
                if header.get('required'):
                    assert value is not None, 'Required response header missing'
                if value is not None:
                    matches_schema(header['schema'], value)

    def verify(sql, latest, restart, base, token, group, other_group, ids):
        sql("UPDATE shares SET updated_at='2026-01-01T00:00:00Z';")
        recorded = (datetime.now(timezone.utc) - timedelta(seconds=1)).isoformat()
        payload = dict(latitude=12.3456, longitude=-45.6789, accuracyM=12.5, recordedAt=recorded)
        assert validator(contract, 'CreateLocationRequest').is_valid(payload)
        key = str(uuid.uuid4())
        headers = {'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token(ids[1]),
                   'Idempotency-Key': key}

        def upload(body):
            req = urllib.request.Request(base + f'/api/v1/groups/{group}/locations',
                                         data=json.dumps(body).encode(), headers=headers, method='POST')
            try:
                response = urllib.request.urlopen(req, timeout=10)
            except urllib.error.HTTPError as error:
                response = error
            with response:
                result = json.load(response)
                matches_response(upload_path, 'post', response.status, result, response.headers)
                return response.status, result

        status, point = upload(payload)
        assert status == 201
        assert upload(payload) == (200, point)
        assert upload(dict(payload, longitude=0))[0] == 409
        status, page = latest(ids[0])
        assert status == 200
        matches_response(read_path, 'get', status, page)
        member = next(item for item in page['items'] if item['userId'] == ids[1])
        assert member['location'] == point
        assert member['freshness']['status'] == 'RECENT'
        assert member['accuracy']['status'] == 'ACCEPTABLE'
        for suffix in ('?cursor=invalid', '?limit=invalid'):
            status, problem = latest(ids[0], suffix)
            assert status == 400
            matches_response(read_path, 'get', status, problem)
        status, problem = latest(ids[0], group_id=other_group)
        assert status == 404
        matches_response(read_path, 'get', status, problem)
        status, problem = latest(bearer=token('invalid-subject'))
        assert status == 401
        matches_response(read_path, 'get', status, problem)
        invalid = dict(payload, latitude=91)
        assert not validator(contract, 'CreateLocationRequest').is_valid(invalid)
        assert upload(invalid)[0] == 400
        sql(f"UPDATE shares SET sharing_enabled=false WHERE group_id='{group}' AND user_id='{ids[1]}';")
        assert upload(payload)[0] == 403
        sql(f"UPDATE shares SET sharing_enabled=true WHERE group_id='{group}' AND user_id='{ids[1]}';")
        sql(f"DELETE FROM locations WHERE location_id='{point['locationId']}';")
        print('PASS: actual upload/replay/read/metadata/error bodies and upload headers match declared operations')

    harness.main(app_args=('--closecircle.location.recent-for-seconds=300',
                           '--closecircle.location.poor-accuracy-threshold-m=50'), verify_upload=verify)


if __name__ == '__main__':
    main()
