# Location accuracy: SCRUM-22 backend groundwork

SCRUM-22 asks for a visual warning when a location has poor accuracy and a configurable, documented threshold. This branch starts the backend portion independently from `dev`. The Android marker and integration with the latest-location endpoint are still outstanding; this is not a completed story or a new public response contract.

## Assessment

`LocationAccuracy` interprets `accuracyM` as reported uncertainty in metres. Larger uncertainty means poorer accuracy. It returns:

| Input | Status |
| --- | --- |
| Finite, non-negative uncertainty at or below the configured threshold | `ACCEPTABLE` |
| Finite uncertainty above the configured threshold | `POOR` |
| Missing, negative, NaN, or infinite uncertainty | `UNKNOWN` |

The threshold boundary is inclusive. These statuses describe the reported measurement, not a guarantee of the member's actual position. An unknown value must not be treated as good accuracy. Existing upload validation remains responsible for rejecting invalid measurements; this read-side assessment does not make them valid.

## Configuration

Set the Spring property `closecircle.location.poor-accuracy-threshold-m` to a finite positive number of metres. For example, `--closecircle.location.poor-accuracy-threshold-m=100` creates a `LocationAccuracy` bean whose threshold is 100 metres. **100 is an example, not an approved product default.** Spring's environment-variable spelling is `CLOSECIRCLE_LOCATION_POORACCURACYTHRESHOLDM`.

No threshold is supplied by default. Without the property, no assessment bean is created and the existing API behaves as before. When the property is present but invalid, context initialization fails. A property change takes effect on application restart.

## Remaining integration

1. Review and merge SCRUM-19's latest-location endpoint through PR #1 into `dev`.
2. Agree on the numeric threshold, response field names, and unknown-value display with the Android/backend owners. The classification and boundary above are the implementation proposal for that review.
3. Integrate the configured assessment into the permitted-location response and update `docs/api-contract.md` in that same change. Assessment must run only after membership and sharing checks; it never grants access to coordinates. A member with `location: null` still has no point to assess.
4. Verify response values alongside the existing group-isolation and sharing-revocation checks. Android implements the visual warning and verifies it against that response.

Run `mvn -f backend/pom.xml verify`. Tests cover the boundary, changes of threshold, unknown/invalid inputs, absent configuration, configured behaviour, and startup rejection of invalid thresholds. No database change or production data access is needed for this groundwork.
