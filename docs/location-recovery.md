# Last-known location recovery: SCRUM-21 backend read path

The latest-location endpoint already stores its source of truth in PostgreSQL. A lack of new recordings does not delete or replace the last saved point, alter its timestamps, or make it recent. SCRUM-20's configured freshness metadata lets clients distinguish that last-known point from a recent recording. SCRUM-21 adds repeatable recovery coverage for this backend behaviour; no new schema or connection-presence flag is needed.

PR #4 was approved by Bogdanctx and merged into `dev` after SCRUM-19/20/22 on 15 November 2026. SCRUM-43's upload implementation was then reviewed and merged. Merge status does not establish deployed availability or Android acceptance.

## Verified behaviour

`python3 src/test/python/check_location_recovery.py` (from `backend/`, after `mvn verify`) starts disposable local PostgreSQL and the real API. It checks that:

- Repeated reads without a new point retain the exact last-known point, including recording and receipt times.
- Restarting both the API and PostgreSQL preserves that point; it remains last-known.
- A delayed older recording with a newer receipt time does not replace the latest recording.
- A newly persisted recent recording becomes visible on the next read, with independent freshness and accuracy statuses.
- Restarting does not restore visibility after sharing is revoked; group isolation and current membership are still enforced.

CI runs these checks alongside authorization, freshness and accuracy regressions. All coordinates and accounts are synthetic. No production database is used, and no retention period is introduced.

## Remaining end-to-end work

The dedicated recovery suite seeds accepted points through SQL. The merged SCRUM-43 upload suite separately exercises real HTTP uploads, restart/concurrent retries and the upload/read/freshness flow. Neither establishes that the Android offline queue works.

Backend ingestion and retry/order checks are implemented and verified under SCRUM-43 against current identity, membership and consent. Android must retain an allowed last-known view during network failure, show that updates are stale, retry or resume on reconnection, and handle visibility revocation when it reconnects. Offline storage lifetime and removal rules need team agreement; the server cannot erase data from a disconnected client. Do not equate `LAST_KNOWN` with proven device disconnection.

SCRUM-21 remains In Progress until those integration and client acceptance criteria are demonstrated. SCRUM-42 remains open until the team reviews the shared contract.
