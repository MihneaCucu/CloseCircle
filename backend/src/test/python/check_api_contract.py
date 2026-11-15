"""Validate the location OpenAPI contract, examples, or a saved JSON payload.

No server calls or database access. Never prints submitted payloads.
"""
import argparse
import json
import math
from pathlib import Path
import sys

from jsonschema import Draft202012Validator, FormatChecker
from openapi_spec_validator import validate as validate_openapi

ROOT = Path(__file__).resolve().parents[4]
SPEC = ROOT / 'docs/contracts/location-api.openapi.json'
EXAMPLES = ROOT / 'docs/contracts/location-examples.json'


def load_json(path):
    def invalid_constant(_):
        raise ValueError('Non-finite JSON number')

    def finite_number(value):
        result = float(value)
        if not math.isfinite(result):
            raise ValueError('Unrepresentable JSON number')
        return result

    return json.loads(Path(path).read_text(), parse_constant=invalid_constant, parse_float=finite_number)


def validator(spec, name):
    if name not in spec['components']['schemas']:
        raise ValueError('Unknown schema name')
    schema = {'$ref': '#/components/schemas/' + name, 'components': spec['components']}
    return Draft202012Validator(schema, format_checker=FormatChecker())


def check_contract(spec):
    # Keep all references local, making validation offline and preventing schema
    # edits from silently fetching external resources during CI or payload checks.
    def walk(value):
        if isinstance(value, dict):
            if '$ref' in value and not value['$ref'].startswith('#/components/schemas/'):
                raise ValueError('Only local schema references are permitted')
            for child in value.values():
                walk(child)
        elif isinstance(value, list):
            for child in value:
                walk(child)
    walk(spec)
    validate_openapi(spec)
    for schema in spec['components']['schemas'].values():
        Draft202012Validator.check_schema(schema)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--schema', help='Component schema to apply to a saved payload')
    parser.add_argument('--payload', type=Path, help='Local JSON file; contents are never logged')
    args = parser.parse_args()
    if bool(args.schema) != bool(args.payload):
        parser.error('--schema and --payload must be used together')
    try:
        spec = load_json(SPEC)
        check_contract(spec)
        if args.payload:
            errors = list(validator(spec, args.schema).iter_errors(load_json(args.payload)))
            if errors:
                # Do not print ValidationError: it contains the instance values.
                print('FAIL: payload does not match the selected schema', file=sys.stderr)
                return 1
            print('PASS: payload matches the selected schema')
        else:
            cases = load_json(EXAMPLES)
            for index, case in enumerate(cases, 1):
                valid = validator(spec, case['schema']).is_valid(case['payload'])
                if valid != case['valid']:
                    print(f'FAIL: contract example {index}', file=sys.stderr)
                    return 1
            print(f'PASS: OpenAPI document, schemas and {len(cases)} positive/negative examples')
    except Exception:
        # The libraries can include schema/example values in exception messages.
        print('FAIL: invalid contract, JSON or validator configuration', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
