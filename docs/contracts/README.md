# Location API integration contract (SCRUM-42)

`location-api.openapi.json` is the machine-readable OpenAPI 3.1 review contract for the backend location slice: GET latest member locations and POST own location. It includes reusable JSON schemas, Bearer authentication, pagination, retry responses, nullability, validation and error bodies. Import the file into an OpenAPI-compatible client tool or use its component schemas when implementing Android models.

The location backend PRs #1, #3, #2, #4 and #5 were reviewed and merged into `dev` on 15 November 2026. This checkout contains their read/upload implementation alongside the contract and test tooling. The wider proposed API remains in [api-contract.md](../api-contract.md); this file covers only the location slice, not authentication issuance, group/invitation/sharing changes, zones or simulator operations. Merge status does not establish deployed availability or Android acceptance.

For this slice, the feature contract records implementation details that supersede older generic proposals for integration testing: uploads accept offset-bearing timestamps and normalize them to UTC microseconds; future tolerance is configurable 0-300 seconds with default 0; disabled sharing and recordings before the current consent timestamp are rejected; identical keyed retries return 200, new points 201. These upload policies require team review. Freshness/accuracy metadata is omitted unless configured, and the team must agree on the numeric settings. Neither the spec nor schema validation grants access or selects product policy.

## Validate and use

From the repository root, in an isolated environment:

```sh
python3 -m venv .venv-contract
.venv-contract/bin/python -m pip install -r backend/src/test/python/requirements-contract.txt
.venv-contract/bin/python backend/src/test/python/check_api_contract.py
```

The CI contract workflow first validates the OpenAPI document and its schemas, then runs 40 positive/negative examples from `location-examples.json`. Its next job builds the backend from the same checkout and runs the real API/PostgreSQL contract check. A third job collects unit and HTTP/PostgreSQL coverage. Examples are synthetic. The checks cover required/optional/null fields, coordinate bounds, timestamp formats, identity overrides, response extensions, metadata enums and error bodies. They do not prove authorization, freshness calculations or mobile behavior; the backend PostgreSQL regressions cover server behavior separately.

Validate a saved **synthetic** request or response, without sending it anywhere:

```sh
.venv-contract/bin/python backend/src/test/python/check_api_contract.py \
  --schema CreateLocationRequest --payload /path/to/synthetic-upload.json
```

Other component names include `Location`, `LatestLocationPage` and `Problem`. Exit 0 means valid; exit 1 means invalid. Payload contents are never printed, external schema references are rejected, and non-finite JSON numbers are rejected. Keep real locations, tokens and captured private responses out of repository files and CI logs.

Requests reject unknown properties. Responses tolerate extra fields so additive server changes do not break clients. `location` may be null; configured metadata objects may be omitted but are not null. `nextCursor:null` ends pagination. A schema cannot check whether a timestamp is too far in the future, whether consent is current, or whether a user belongs to a group; the server checks those rules. Authentication-filter 401 responses may have no body: clients must use the status and Bearer challenge and must not always expect a Problem object.

## Check against the merged backend

The HTTP check uses the real API and its existing disposable PostgreSQL harness, both locally and in CI. It does not connect to Neon or any remote API. PostgreSQL command-line tools and Java 21+ are required. Build this checkout first (or use an isolated trusted checkout of the same revision):

```sh
mvn -f backend/pom.xml clean verify
.venv-contract/bin/python backend/src/test/python/check_location_contract_http.py \
  --backend-root backend
```

The runner executes that checkout's test harness, seeds synthetic prerequisite accounts/memberships/consent, uploads a point through HTTP, and validates actual read/upload/error responses against the same checkout's schemas. It also runs the harness's authorization, group-isolation, pagination and sharing-revocation checks. Only use a trusted checkout. The CI `http-contract` job executes this check after static validation. It validates actual operation-specific status/body schemas and upload headers, including 201 creation, 200 replay, 409 conflict, 400 invalid input, 401 invalid subject, 403 disabled sharing and 404 inaccessible group.

## CI and coverage reports

The workflow `.github/workflows/api-contract.yml` now validates the backend and contract from the **same checkout**. The temporary PR #5 snapshot pin and second checkout have been removed. Pull requests test their merge checkout; pushes to `dev`/`main` test that branch revision. Future backend or schema changes therefore run together automatically, and the manifest records the actual tested commits. The contract workflow does not deploy anything or use cloud/database secrets.

After the real API contract job passes, the coverage job builds the same backend with JaCoCo 0.8.14. It runs the 21 unit/configuration tests, all five existing PostgreSQL suites (access, freshness, accuracy, recovery and upload), and the real API contract check. The instrumented API JVMs retain their existing sanitized environment; a temporary local Java launcher adds the coverage agent, including across restarts. The runner changes neither the backend POM nor its source or harness.

Download **backend-location-coverage** from the workflow run's Artifacts section (retained 14 days). It contains:

- `unit/index.html`, `http-postgres/index.html`, `combined/index.html`: separate human-readable reports, with XML and CSV in each directory.
- `unit.exec`, `http-postgres.exec`: raw execution data for the same compiled classes.
- `manifest.json`: exact backend and contract/tooling commits, dirty-state flags, individual check exit codes, unit test totals and coverage counters.
- `summary.md`: the same coverage table shown in the GitHub Actions job summary.

The denominator includes **all compiled backend application classes**, including uncovered ones; test code and framework dependencies are not included. Combined coverage is the union of unit and HTTP execution, not their average. No arbitrary percentage gate is imposed. The job fails if a suite fails, execution data/reports are missing, or the instrumented HTTP run does not cover the read/upload controllers and services. Available partial reports are preserved on failure and clearly labelled incomplete. Never present those as passing checkpoint evidence.

For a fresh local measurement, use this checkout or an isolated trusted checkout of the same revision:

```sh
.venv-contract/bin/python backend/src/test/python/run_backend_coverage.py \
  --backend-root backend \
  --output /tmp/closecircle-coverage-new-run
```

Maven resolves the pinned JaCoCo tools. Use a fresh output directory whose path has no spaces, commas or equals signs. The runner cleans the backend build directory, so use an isolated test checkout when preserving local build outputs matters. All database activity uses disposable synthetic fixtures; no production/Neon connection is used. Coverage measures exercised code, not Android behavior, real auth/group/sharing integration, independent human acceptance or completion of the one-third story target. Implementation details: [JaCoCo agent](https://www.jacoco.org/jacoco/trunk/doc/agent.html), [report CLI](https://www.jacoco.org/jacoco/trunk/doc/cli.html).

## Checkpoint priority

This work reduces integration mistakes for SCRUM-19/20 after their backend PRs were reviewed and merged. The next product blockers remain teammate-owned SCRUM-41 (auth), SCRUM-11/12/13 (groups/invitations), SCRUM-15 (sharing), and Android integration. Completing this technical contract task is not an additional completed User Story toward the one-third target. SCRUM-42 remains open until the Android, React and backend owners review and agree on the contract.

Standards: [OpenAPI 3.1](https://spec.openapis.org/oas/v3.1.0), [JSON Schema validator documentation](https://python-jsonschema.readthedocs.io/en/stable/validate/).
