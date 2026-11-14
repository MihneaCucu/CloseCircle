# Location accuracy: SCRUM-22 backend implementation

SCRUM-22 asks for a visual warning when a location has poor accuracy and a configurable, documented threshold. This branch integrates the assessment into the latest-location response, building on the unmerged SCRUM-19 and SCRUM-20 backend changes. Review this PR against `feature/SCRUM-20-location-freshness`, then retarget to `dev` after that dependency is merged. Android's visual marker and team agreement on the numeric threshold remain outstanding; the shared story is not Done.

## Assessment

`LocationAccuracy` interprets `accuracyM` as reported uncertainty in metres. Larger uncertainty means poorer accuracy. It returns:

| Input | Status |
| --- | --- |
| Finite, non-negative uncertainty at or below the configured threshold | `ACCEPTABLE` |
| Finite uncertainty above the configured threshold | `POOR` |
| Missing, negative, NaN, or infinite uncertainty | `UNKNOWN` |

The threshold boundary is inclusive. These statuses describe the reported measurement, not a guarantee of the member's actual position. An unknown value must not be treated as good accuracy. Upload validation must reject invalid measurements when that route is implemented. This read-side assessment does not make them valid; non-finite or negative stored uncertainty is returned as `accuracyM: null`.

## Configuration

Set the Spring property `closecircle.location.poor-accuracy-threshold-m` to a finite positive number of metres. For example, `--closecircle.location.poor-accuracy-threshold-m=100` creates a `LocationAccuracy` bean whose threshold is 100 metres. **100 is an example, not an approved product default.** Spring's environment-variable spelling is `CLOSECIRCLE_LOCATION_POORACCURACYTHRESHOLDM`.

No threshold is supplied by default. Without the property, no assessment bean is created and the new `accuracy` member field is omitted. When the property is present but invalid, context initialization fails. A property change takes effect on application restart.

When configured, each permitted member includes `"accuracy": {"status":"POOR","poorAccuracyThresholdM":100.0}` alongside the existing `location`. A member with `location: null` has status `UNKNOWN`. Freshness and accuracy are independent: an old location may be accurate and a recent location may have poor accuracy. Membership and sharing checks apply before either assessment; hidden members remain absent.

## Remaining review and client work

1. Review and merge SCRUM-19 and SCRUM-20 in dependency order before this PR.
2. Agree on the numeric threshold, response field names, and unknown-value display with the Android/backend owners. The classification and boundary above are the implementation proposal for that review.
3. Android implements the visual warning and verifies it against this proposed response. Assessment never grants access to coordinates or decides retention.

Run `mvn -f backend/pom.xml verify`, then from `backend/` run `python3 src/test/python/check_location_accuracy.py`. Unit tests cover thresholds and configuration. The real API/PostgreSQL checks verify disabled/enabled metadata, boundaries, missing/non-finite accuracy, combined freshness, group isolation and revocation. CI runs all checks. No schema change or production database access is required.
