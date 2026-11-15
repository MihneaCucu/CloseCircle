# Location freshness: SCRUM-20 backend implementation

SCRUM-20 asks the client to distinguish a recent location from a last-known one and show when it was recorded. The classifier is integrated into SCRUM-19's authenticated response. PR #3 was approved by prezcu and merged into `dev` after PR #1 on 15 November 2026. Android presentation and team agreement on the window remain separate; merge status alone does not establish deployment or whole-story acceptance.

`LocationFreshness.assess(recordedAt, recentFor, clock)` returns the original `recordedAt` and one of these proposed statuses:

- `RECENT`: the timestamp is not in the future and its age is at most the supplied `recentFor` duration (the boundary is inclusive).
- `LAST_KNOWN`: its age is greater than the supplied duration.
- `UNKNOWN`: the location timestamp is missing or is in the future. A future timestamp remains visible in the result so its cause can be handled explicitly.

The classifier accepts a duration and clock. A negative duration is rejected; zero marks only a timestamp equal to the evaluation time as recent. All timestamp calculations use `Instant`, independent of local time zones.

## Configuration and response

Set `closecircle.location.recent-for-seconds` to an integer of zero or more seconds, or use `CLOSECIRCLE_LOCATION_RECENTFORSECONDS`. For example, `--closecircle.location.recent-for-seconds=300` uses a five-minute window for testing; **300 is an example, not an approved product default**. Absent configuration omits the new `freshness` field entirely and preserves the previous response. Empty, negative, non-integer, and out-of-range values fail startup. Restart to apply a change.

When configured, each permitted member in `GET /api/v1/groups/{groupId}/locations/latest` includes:

```json
{
  "freshness": {
    "status": "LAST_KNOWN",
    "evaluatedAt": "2026-11-14T12:00:00Z",
    "recentForSeconds": 300
  }
}
```

The existing `location.recordedAt` is the last update time for display. `receivedAt` never makes an old device recording recent. All members in one response use the same server evaluation time; each subsequent request evaluates again. An otherwise visible member with `location: null` gets `UNKNOWN`. Future points also get `UNKNOWN`; this does not change upload clock-skew validation. The backend does not claim to know whether a device is connected.

Membership and sharing are checked before any classification. Hidden members remain absent, and freshness grants no access to coordinates. The original timestamps and points are preserved. Android remains responsible for refresh and visual presentation.

## Review and verification

The Android, backend, and product owners still need to agree on the numeric window, future-clock handling, proposed response fields, and unknown-value display. The backend implementation has passed review and is merged; this does not establish agreement on every product setting or completion of Android work.

Run `mvn -f backend/pom.xml verify`, then from `backend/` run `python3 src/test/python/check_location_freshness.py`. The HTTP regression starts disposable local PostgreSQL and the real API with classification disabled and enabled. It checks old/recent/future/missing points, recording versus receipt time, the configured window and shared evaluation time, automatic RECENT-to-LAST_KNOWN aging across repeated reads without changing the point or timestamp, plus existing authorization, isolation and revocation checks. CI runs it on every PR. No production database is accessed.
