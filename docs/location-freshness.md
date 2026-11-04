# Location freshness: SCRUM-20 backend proposal

SCRUM-20 asks the client to distinguish a recent location from a last-known one and show when it was recorded. This branch adds an independent backend classifier that can be integrated with the latest-location response after SCRUM-19 is available on `dev`.

`LocationFreshness.assess(recordedAt, recentFor, clock)` returns the original `recordedAt` and one of these proposed statuses:

- `RECENT`: the timestamp is not in the future and its age is at most the supplied `recentFor` duration (the boundary is inclusive).
- `LAST_KNOWN`: its age is greater than the supplied duration.
- `UNKNOWN`: the location timestamp is missing or is in the future. A future timestamp remains visible in the result so its cause can be handled explicitly.

The caller supplies both the duration and the clock. There is no default duration, environment setting, public endpoint, or change to the response contract in this branch. A negative duration is rejected; zero marks only a timestamp equal to the evaluation time as recent. All timestamp calculations use `Instant`, independent of local time zones.

Before integration, the Android, backend, and product owners need to agree on the duration, whether a small future clock skew should be tolerated, the response field names and states, and how the client displays an unknown value. These behaviors are a proposal for that review, not an approved policy. A freshness classification never grants access to a location: membership, sharing, and retention rules must be enforced separately.

Run the backend tests with `mvn -f backend/pom.xml test`. The unit tests cover the threshold boundary, stale, missing, future and invalid inputs with a fixed clock.
