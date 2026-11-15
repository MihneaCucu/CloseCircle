# Per-group location sharing (SCRUM-15 / T-04, SCRUM-16 / T-10, SCRUM-17 / T-11)

User stories:

- SCRUM-15: as a member, I want to turn location sharing on or off separately for each group, so that I control who can see my location.
- SCRUM-16: as a member, I want to revoke location sharing immediately, so that the others no longer see my location.
- SCRUM-17: as a member, I want to share my location for a limited time, so that sharing stops automatically.

## Endpoints

All routes need a Bearer JWT. The user is always the token subject.

| Route | Body | Success |
| --- | --- | --- |
| `GET /api/v1/groups/{groupId}/me/sharing` | none | `200 SharingSettings` |
| `PUT /api/v1/groups/{groupId}/me/sharing` | `{"sharingEnabled": bool, "shareLiveLocation": bool, "durationMinutes": int?}` | `200 SharingSettings` |
| `DELETE /api/v1/groups/{groupId}/me/sharing` | none | `200 SharingSettings` (both flags `false`) |

```json
{
  "groupId": "10000000-0000-0000-0000-000000000001",
  "userId": "00000000-0000-0000-0000-000000000006",
  "sharingEnabled": true,
  "shareLiveLocation": true,
  "expiresAt": "2026-11-15T11:30:00.123456Z",
  "updatedAt": "2026-11-15T10:30:00.123456Z"
}
```

To turn sharing on in a group, send both flags as `true`. To revoke it, call `DELETE` (or `PUT` with `"sharingEnabled": false`).

| Error | Code |
| --- | --- |
| `400` missing/extra field, non-boolean value, malformed JSON | `INVALID_SHARING_REQUEST` |
| `400` malformed `groupId` | `INVALID_PARAMETER` |
| `401` missing/invalid token or non-UUID subject | `INVALID_TOKEN` (no body when the token is missing or rejected by Spring Security) |
| `404` group does not exist, or caller is not a member | `GROUP_NOT_FOUND` |

All responses use `Cache-Control: no-store`.

## How each acceptance criterion is met

1. **Per-group toggle:** preferences are stored in `shares`, one row per `(group_id, user_id)` membership. Changing one group never touches another group's row.
2. **Only groups with sharing on can see the location:** `GET /groups/{groupId}/locations/latest` (SCRUM-19) only returns members whose row in that group has both flags `true`; a missing row is hidden. `POST /groups/{groupId}/locations` (SCRUM-43) rejects uploads with `403 LOCATION_SHARING_DISABLED` under the same rule. A member who never saved preferences is not sharing.
3. **Persisted:** the state is in PostgreSQL. `updatedAt` moves forward only when a flag changes, so a repeated `PUT` with the same values changes nothing. Uploads use it as the consent boundary: a recording made before the latest change is rejected with `403 LOCATION_CONSENT_CHANGED`.

Concurrency: `PUT` locks the membership row before writing, in the same order as uploads. A concurrent upload therefore sees either the old or the new settings, and a concurrent removal from the group cannot slip in between the check and the write. When a member leaves a group, their row is removed with the membership, so they start unshared if they rejoin.

## Immediate revocation (SCRUM-16)

`DELETE /api/v1/groups/{groupId}/me/sharing` is a one-call revoke: it sets both flags to `false` for that group only and returns the saved settings. It is idempotent: revoking again returns the same response and keeps `updatedAt`. Revoking without saved preferences stores an explicit "not sharing" row.

1. **Revocation takes effect immediately.** The change is committed before the `200` response. The latest-locations read checks the `shares` row in the same SQL statement on every request, and nothing is cached (`Cache-Control: no-store`, no server cache). The first read that starts after `DELETE` returns no longer contains the user. Members' apps only notice on their next request, because the API has no push channel.
2. **No further updates reach the group.** While sharing is revoked, uploads are refused with `403 LOCATION_SHARING_DISABLED`, including idempotent retries of uploads accepted earlier. Revocation and uploads lock the same membership row, so an upload in flight either completes before the revocation (and is then hidden) or is refused; no point is stored after it. After sharing is re-enabled, recordings made before or during the revocation (for example, from an offline queue) are refused with `403 LOCATION_CONSENT_CHANGED`, so only new recordings reach the group.

`updatedAt` uses `clock_timestamp()`, the moment of the write after the lock is taken, rather than the transaction start. A request that waited for the lock therefore cannot backdate the consent boundary.

Stored points are not deleted on revocation. They stay hidden while sharing is off. After re-enabling, the last point sent before the revocation is visible again until a new one arrives. Deleting history is a separate, open retention decision.

## Temporary sharing (SCRUM-17)

1. **Choose a duration:** add `"durationMinutes"` to `PUT`, a whole number from 1 to 10080 (7 days). It is only allowed when `sharingEnabled` is `true`. Omitting it, or sending `null`, means no time limit. The server computes `expiresAt` from its own clock as exactly `updatedAt` plus the duration, so a wrong phone clock does not matter. Sending a new duration replaces the old one, which extends or shortens the period. A `PUT` without a duration makes sharing unlimited again.
2. **Stops automatically:** sharing is active only while `expiresAt` is `null` or in the future. The latest-locations read, uploads and `GET .../me/sharing` all apply this rule at the database clock, so sharing stops exactly at the expiry without a background job and survives restarts. After expiry, `GET` returns `sharingEnabled: false` and keeps `expiresAt` so the app can show when it ended. Uploads get `403 LOCATION_SHARING_DISABLED`, including recordings time-stamped after the expiry under a future clock tolerance.
3. **Stop manually before expiry:** `DELETE` (SCRUM-16) or `PUT` with `"sharingEnabled": false`. Both clear `expiresAt`.

`updatedAt` also changes when the expiry changes. Sharing again after an expiry therefore starts a new consent period, and recordings made while sharing had stopped are refused with `403 LOCATION_CONSENT_CHANGED`. As a consequence, repeating a `PUT` with a duration is not idempotent: it starts a new period each time.

The database row keeps `sharing_enabled = true` after expiry. Any new code that reads `shares` (for example SCRUM-18) must apply the same `expires_at` rule.

## Tests

- `SharingRequestTest` (JUnit, `mvn verify`): strict body parsing and JWT subject handling.
- `src/test/python/check_temporary_sharing.py` (CI, disposable PostgreSQL, 60 s upload clock tolerance). It checks: the expiry computed from the chosen duration; invalid durations; the automatic stop at expiry (visibility, `GET` and uploads); that the other group is unaffected; persistence across an API and database restart; that recordings from the stopped period are refused after sharing again; refused recordings time-stamped after the expiry; manual stop with `DELETE` and `PUT`; and extending or removing the time limit.
- `src/test/python/check_sharing_revocation.py` (CI, disposable PostgreSQL). It checks: authentication and `404`; that the first read after `DELETE` hides the user; refused new uploads, retries and queued recordings; that no point is stored after revocation; that the other group is unaffected; idempotent `DELETE`; persistence across an API and database restart; refused pre-revocation recordings after re-enabling; and revoking while uploads run concurrently.
- `src/test/python/check_group_sharing.py` (CI, disposable PostgreSQL). It runs over real HTTP and checks: authentication; `404` for non-members and missing groups; validation; the default of not sharing; turning sharing on/off and how that changes visibility and uploads; that each flag hides the member on its own; that repeating the same `PUT` is idempotent; isolation between two groups in both directions; persistence across an API and database restart; and that settings are dropped when a member leaves.

Run locally from `backend/`:

```sh
mvn -q -DskipTests package && python3 src/test/python/check_group_sharing.py
python3 src/test/python/check_sharing_revocation.py
python3 src/test/python/check_temporary_sharing.py
```

## Out of scope

- Listing the groups I share with (SCRUM-18).
- Notifying the user or the group when temporary sharing expires.
- Pushing revocations to other members' apps in real time (there is no push channel; apps see it on their next request).
- Android/React UI for the toggle.
- Whether locations recorded while sharing is off are retained (an open team privacy decision).
