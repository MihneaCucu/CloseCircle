# Last-known location recovery: SCRUM-21 backend read path

The latest-location endpoint already stores its source of truth in PostgreSQL. A lack of new recordings does not delete or replace the last saved point, alter its timestamps, or make it recent. SCRUM-20's configured freshness metadata lets clients distinguish that last-known point from a recent recording. SCRUM-21 adds repeatable recovery coverage for this backend behaviour; no new schema or connection-presence flag is needed.

This branch depends on the unmerged SCRUM-19, SCRUM-20 and SCRUM-22 changes. Review its changes against `feature/SCRUM-22-location-accuracy` and merge only after those dependencies. It does not merge or deploy anything into `dev` or `main`.

## Verified behaviour

`python3 src/test/python/check_location_recovery.py` (from `backend/`, after `mvn verify`) starts disposable local PostgreSQL and the real API. It checks that:

- Repeated reads without a new point retain the exact last-known point, including recording and receipt times.
- Restarting both the API and PostgreSQL preserves that point; it remains last-known.
- A delayed older recording with a newer receipt time does not replace the latest recording.
- A newly persisted recent recording becomes visible on the next read, with independent freshness and accuracy statuses.
- Restarting does not restore visibility after sharing is revoked; group isolation and current membership are still enforced.

CI runs these checks alongside authorization, freshness and accuracy regressions. All coordinates and accounts are synthetic. No production database is used, and no retention period is introduced.

## Remaining end-to-end work

These are backend **read-path** checks. Tests seed accepted recordings through SQL because `POST /groups/{groupId}/locations` is still a proposed route. They do not establish that upload ingestion or the Android offline queue works.

The upload owner must implement and verify ingestion against current identity, membership and consent, including retry/order/idempotency policy. Android must retain an allowed last-known view during network failure, show that updates are stale, retry or resume on reconnection, and handle visibility revocation when it reconnects. Offline storage lifetime and removal rules need team agreement; the server cannot erase data from a disconnected client. Do not equate `LAST_KNOWN` with proven device disconnection.

SCRUM-21 remains In Progress until those integration and client acceptance criteria are demonstrated. SCRUM-42 remains open until the team reviews the shared contract.
