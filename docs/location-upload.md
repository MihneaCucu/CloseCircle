# Location upload backend

SCRUM-43 is a backend subtask of SCRUM-21 supporting the member-location flow in SCRUM-19/20. PR #5 was approved by Bogdanctx and merged into `dev` after the read/freshness/accuracy/recovery dependencies on 15 November 2026. Android and teammate authentication/group/sharing integration remain separate.

## Request and response

`POST /api/v1/groups/{groupId}/locations` requires a Bearer JWT whose subject is the uploading user's UUID. Only these JSON fields are accepted:

```json
{
  "latitude": 12.3456,
  "longitude": -45.6789,
  "accuracyM": 12.5,
  "recordedAt": "2026-11-14T12:00:00Z"
}
```

Coordinates must be finite JSON numbers in WGS 84 ranges. `accuracyM` is optional/nullable; when supplied it must be finite and non-negative. `recordedAt` requires an offset and is normalized to UTC with microsecond precision. Unknown fields, including client-supplied identity, group or receipt time, are rejected. The server generates `locationId`, derives `userId` from JWT, takes `groupId` from the authorized path, and generates `receivedAt`.

New writes return `201` with the existing `Location` representation (`locationId`, `groupId`, `userId`, coordinates, nullable accuracy, recording and receipt times). Responses use `Cache-Control: no-store`. The new point is available through the latest-location read endpoint; an older recording does not replace a newer recorded point.

## Consent and clock handling

The upload transaction locks the current membership and sharing rows before writing. Missing/inaccessible membership returns `404 GROUP_NOT_FOUND`; missing settings or either disabled sharing flag returns `403 LOCATION_SHARING_DISABLED`. Uploads never enable sharing or create consent automatically. A recording older than `shares.updated_at` returns `403 LOCATION_CONSENT_CHANGED`, including retries after sharing has changed.

This is a conservative implementation proposal for team review: a client must acquire a new recording after the latest preference update, rather than replaying a queue from an older consent state. It does not define historical retention or authorize uploads while sharing is off. The sharing endpoint owner must maintain `updated_at` when preferences change.

`closecircle.location.upload-max-future-seconds` accepts integer values from 0 to 300. The default is 0: future recordings are rejected. A demo may explicitly configure a tolerance, for example `--closecircle.location.upload-max-future-seconds=30`. This setting does not change freshness classification, which treats future points as unknown. The production tolerance and consent-boundary behaviour still need team agreement. No retention period or arbitrary past-age limit is introduced here.

## Safe retries

Clients may send `Idempotency-Key: <canonical UUID>`. The key is scoped to the authenticated member and group. Repeating the same normalized payload returns `200` and the exact saved representation, including its original receipt time. Reusing a key with different payload returns `409 IDEMPOTENCY_CONFLICT`. Replays still require current membership and consent. Concurrent retries serialize for that membership, and a database uniqueness constraint protects the key. Without a key, each request is a new recording; clients must use a stable key when retrying a lost response.

Flyway V2 adds nullable internal `locations.client_upload_id` and uniqueness on `(group_id, user_id, client_upload_id)`. Existing rows remain valid, and the internal key is not exposed in JSON. V1 is unchanged.

## Verification and demonstration

Run `mvn -f backend/pom.xml verify`, then from `backend/` run `python3 src/test/python/check_location_upload.py`. The regression uses synthetic users, JWTs and coordinates with disposable local PostgreSQL and the real API. It checks new uploads and read-back, malformed data/identity overrides, inaccessible groups, disabled/current consent, missing accuracy, late recordings, idempotent replay, conflicting/concurrent retries and replay after API restart. Existing access/isolation/revocation checks run in the same harness. CI runs the upload regression after the other location checks.

For the checkpoint demonstration, use the real auth/group/sharing endpoints once their owners integrate them. The automated regression seeds those prerequisites; it proves the upload/read backend, not Android acceptance, real token issuance, invitation flow, deployed integration or completion of a whole User Story. A different Developer must perform the story acceptance review before it is counted Done.

## AI assisted work record

- Tool: Codex, used for implementation, tests, review and documentation.
- Request: implement the backend location upload endpoint on a separate feature branch, track it in Jira, and assess the weeks 7-8 checkpoint.
- Result: upload API, V2 migration, unit and real PostgreSQL HTTP checks, contract and checkpoint notes.
- Test-first evidence: commit `299da91` records the HTTP checks before implementation; its run failed at the absent upload handler. The subsequent implementation makes those checks pass. This is a repository sequence, not evidence that AI use alone satisfies the course's team TDD requirement.
- Validation: Maven tests and real HTTP/database tests were executed by the agent; CI evidence is attached to the PR. Human code understanding, any team changes and independent acceptance remain to be recorded by the team. No human review or Pair Programming session is claimed here.

The upload regression also runs a connected SCRUM-19/SCRUM-20 checkpoint scenario with a five-second test window: real HTTP uploads are read by another member, age from RECENT to LAST_KNOWN without losing their timestamp, and become RECENT again after a resumed upload. Sharing revocation hides the member. See [checkpoint-2-readiness.md](checkpoint-2-readiness.md) for the backend handoff and whole-story acceptance boundary.
