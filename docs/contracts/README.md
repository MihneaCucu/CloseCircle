# Location API integration contract (SCRUM-42)

`location-api.openapi.json` is the machine-readable OpenAPI 3.1 review contract for the backend location slice: GET latest member locations and POST own location. It includes reusable JSON schemas, Bearer authentication, pagination, retry responses, nullability, validation and error bodies. Import the file into an OpenAPI-compatible client tool or use its component schemas when implementing Android models.

This independent branch starts from `dev` and adds no business endpoints. The contract was checked against the pending backend at `a505df189ebe7dec15eb75c92194072df7de3d30` (PR #5, including PRs #1-4). That does not mean the routes are available on `dev`, merged, deployed, or accepted by the team. The wider proposed API remains in [api-contract.md](../api-contract.md); this file covers only the tested location slice, not authentication issuance, group/invitation/sharing changes, zones or simulator operations.

For this slice, the feature contract records implementation details that supersede older generic proposals for integration testing: uploads accept offset-bearing timestamps and normalize them to UTC microseconds; future tolerance is configurable 0-300 seconds with default 0; disabled sharing and recordings before the current consent timestamp are rejected; identical keyed retries return 200, new points 201. These upload policies require team review. Freshness/accuracy metadata is omitted unless configured, and the team must agree on the numeric settings. Neither the spec nor schema validation grants access or selects product policy.

## Validate and use

From the repository root, in an isolated environment:

```sh
python3 -m venv .venv-contract
.venv-contract/bin/python -m pip install -r backend/src/test/python/requirements-contract.txt
.venv-contract/bin/python backend/src/test/python/check_api_contract.py
```

The CI contract workflow validates the OpenAPI document and its schemas, then runs 40 positive/negative examples from `location-examples.json`. Examples are synthetic. The checks cover required/optional/null fields, coordinate bounds, timestamp formats, identity overrides, response extensions, metadata enums and error bodies. They do not prove authorization, freshness calculations or mobile behavior; the backend PostgreSQL regressions cover server behavior separately.

Validate a saved **synthetic** request or response, without sending it anywhere:

```sh
.venv-contract/bin/python backend/src/test/python/check_api_contract.py \
  --schema CreateLocationRequest --payload /path/to/synthetic-upload.json
```

Other component names include `Location`, `LatestLocationPage` and `Problem`. Exit 0 means valid; exit 1 means invalid. Payload contents are never printed, external schema references are rejected, and non-finite JSON numbers are rejected. Keep real locations, tokens and captured private responses out of repository files and CI logs.

Requests reject unknown properties. Responses tolerate extra fields so additive server changes do not break clients. `location` may be null; configured metadata objects may be omitted but are not null. `nextCursor:null` ends pagination. A schema cannot check whether a timestamp is too far in the future, whether consent is current, or whether a user belongs to a group; the server checks those rules. Authentication-filter 401 responses may have no body: clients must use the status and Bearer challenge and must not always expect a Problem object.

## Check against the pending backend

The optional HTTP check uses the real feature API and its existing disposable PostgreSQL harness. It does not connect to Neon or any remote API. PostgreSQL command-line tools and Java 21+ are required. Use a separate trusted checkout containing PR #5 and build its jar first:

```sh
mvn -f /path/to/feature-checkout/backend/pom.xml clean verify
.venv-contract/bin/python backend/src/test/python/check_location_contract_http.py \
  --backend-root /path/to/feature-checkout/backend
```

The runner executes that checkout's test harness, seeds synthetic prerequisite accounts/memberships/consent, uploads a point through HTTP, and validates actual read/upload/error responses against this branch's schemas. It also runs the harness's authorization, group-isolation, pagination and sharing-revocation checks. Only use a trusted checkout. The standard contract CI job checks the document/examples; it does not claim to run this optional feature-dependent HTTP check until those endpoints are in its checkout.

## Checkpoint priority

This work reduces integration mistakes for SCRUM-19/20 while their backend PRs await review. The next product blockers remain teammate-owned SCRUM-41 (auth), SCRUM-11/12/13 (groups/invitations), SCRUM-15 (sharing), and Android integration. Completing this technical contract task is not an additional completed User Story toward the one-third target. SCRUM-42 remains open until the Android, React and backend owners review and agree on the contract.

Standards: [OpenAPI 3.1](https://spec.openapis.org/oas/v3.1.0), [JSON Schema validator documentation](https://python-jsonschema.readthedocs.io/en/stable/validate/).
