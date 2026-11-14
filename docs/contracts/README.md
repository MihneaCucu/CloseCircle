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

The CI contract workflow first validates the OpenAPI document and its schemas, then runs 40 positive/negative examples from `location-examples.json`. Its next job builds the pinned backend snapshot and runs the real API/PostgreSQL contract check. A third job collects unit and HTTP/PostgreSQL coverage. Examples are synthetic. The checks cover required/optional/null fields, coordinate bounds, timestamp formats, identity overrides, response extensions, metadata enums and error bodies. They do not prove authorization, freshness calculations or mobile behavior; the backend PostgreSQL regressions cover server behavior separately.

Validate a saved **synthetic** request or response, without sending it anywhere:

```sh
.venv-contract/bin/python backend/src/test/python/check_api_contract.py \
  --schema CreateLocationRequest --payload /path/to/synthetic-upload.json
```

Other component names include `Location`, `LatestLocationPage` and `Problem`. Exit 0 means valid; exit 1 means invalid. Payload contents are never printed, external schema references are rejected, and non-finite JSON numbers are rejected. Keep real locations, tokens and captured private responses out of repository files and CI logs.

Requests reject unknown properties. Responses tolerate extra fields so additive server changes do not break clients. `location` may be null; configured metadata objects may be omitted but are not null. `nextCursor:null` ends pagination. A schema cannot check whether a timestamp is too far in the future, whether consent is current, or whether a user belongs to a group; the server checks those rules. Authentication-filter 401 responses may have no body: clients must use the status and Bearer challenge and must not always expect a Problem object.

## Check against the pending backend

The HTTP check uses the real feature API and its existing disposable PostgreSQL harness, both locally and in CI. It does not connect to Neon or any remote API. PostgreSQL command-line tools and Java 21+ are required. Use a separate trusted checkout containing PR #5 and build its jar first:

```sh
mvn -f /path/to/feature-checkout/backend/pom.xml clean verify
.venv-contract/bin/python backend/src/test/python/check_location_contract_http.py \
  --backend-root /path/to/feature-checkout/backend
```

The runner executes that checkout's test harness, seeds synthetic prerequisite accounts/memberships/consent, uploads a point through HTTP, and validates actual read/upload/error responses against this branch's schemas. It also runs the harness's authorization, group-isolation, pagination and sharing-revocation checks. Only use a trusted checkout. The CI `http-contract` job executes this check after static validation. It validates actual operation-specific status/body schemas and upload headers, including 201 creation, 200 replay, 409 conflict, 400 invalid input, 401 invalid subject, 403 disabled sharing and 404 inaccessible group.

## CI pairing and coverage reports

The workflow `.github/workflows/api-contract.yml` pins `LOCATION_BACKEND_COMMIT` to `a505df189ebe7dec15eb75c92194072df7de3d30`, the PR #5 integration snapshot. Every PR #6 update validates its current contract/tooling against that exact backend. This keeps PR #6 independent from the unmerged business implementation. It is not an automatic test of every later backend branch head: update the reviewed SHA when changing the integration snapshot. Once the endpoint PRs are merged, switch the workflow to the same checkout and remove the temporary pairing. The job does not deploy anything or use cloud/database secrets.

After the real API contract job passes, the coverage job builds the same backend with JaCoCo 0.8.14. It runs the 21 unit/configuration tests, all five existing PostgreSQL suites (access, freshness, accuracy, recovery and upload), and the real API contract check. The instrumented API JVMs retain their existing sanitized environment; a temporary local Java launcher adds the coverage agent, including across restarts. The runner changes neither the backend POM nor its source or harness.

Download **backend-location-coverage** from the workflow run's Artifacts section (retained 14 days). It contains:

- `unit/index.html`, `http-postgres/index.html`, `combined/index.html`: separate human-readable reports, with XML and CSV in each directory.
- `unit.exec`, `http-postgres.exec`: raw execution data for the same compiled classes.
- `manifest.json`: exact backend and contract/tooling commits, dirty-state flags, individual check exit codes, unit test totals and coverage counters.
- `summary.md`: the same coverage table shown in the GitHub Actions job summary.

The denominator includes **all compiled backend application classes**, including uncovered ones; test code and framework dependencies are not included. Combined coverage is the union of unit and HTTP execution, not their average. No arbitrary percentage gate is imposed. The job fails if a suite fails, execution data/reports are missing, or the instrumented HTTP run does not cover the read/upload controllers and services. Available partial reports are preserved on failure and clearly labelled incomplete. Never present those as passing checkpoint evidence.

For a fresh local measurement, use the same trusted backend checkout as above:

```sh
.venv-contract/bin/python backend/src/test/python/run_backend_coverage.py \
  --backend-root /path/to/feature-checkout/backend \
  --output /tmp/closecircle-coverage-new-run
```

Maven resolves the pinned JaCoCo tools. Use a fresh output directory whose path has no spaces, commas or equals signs. The runner cleans the backend build directory, so it must be an isolated test checkout. All database activity uses disposable synthetic fixtures; no production/Neon connection is used. Coverage measures exercised code, not Android behavior, real auth/group/sharing integration, independent human acceptance or completion of the one-third story target. Implementation details: [JaCoCo agent](https://www.jacoco.org/jacoco/trunk/doc/agent.html), [report CLI](https://www.jacoco.org/jacoco/trunk/doc/cli.html).

## Checkpoint priority

This work reduces integration mistakes for SCRUM-19/20 while their backend PRs await review. The next product blockers remain teammate-owned SCRUM-41 (auth), SCRUM-11/12/13 (groups/invitations), SCRUM-15 (sharing), and Android integration. Completing this technical contract task is not an additional completed User Story toward the one-third target. SCRUM-42 remains open until the Android, React and backend owners review and agree on the contract.

Standards: [OpenAPI 3.1](https://spec.openapis.org/oas/v3.1.0), [JSON Schema validator documentation](https://python-jsonschema.readthedocs.io/en/stable/validate/).
