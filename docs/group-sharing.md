# Per-group location sharing (SCRUM-15 / T-04)

User story: as a member, I want to turn location sharing on or off separately for each group, so that I control who can see my location.

## Endpoints

Both routes need a Bearer JWT. The user is always the token subject.

| Route | Body | Success |
| --- | --- | --- |
| `GET /api/v1/groups/{groupId}/me/sharing` | none | `200 SharingSettings` |
| `PUT /api/v1/groups/{groupId}/me/sharing` | `{"sharingEnabled": bool, "shareLiveLocation": bool}` | `200 SharingSettings` |

```json
{
  "groupId": "10000000-0000-0000-0000-000000000001",
  "userId": "00000000-0000-0000-0000-000000000006",
  "sharingEnabled": true,
  "shareLiveLocation": true,
  "updatedAt": "2026-11-15T10:30:00.123456Z"
}
```

To turn sharing on in a group, send both flags as `true`. To turn it off, send `"sharingEnabled": false`.

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

## Tests

- `SharingRequestTest` (JUnit, `mvn verify`): strict body parsing and JWT subject handling.
- `src/test/python/check_group_sharing.py` (CI, disposable PostgreSQL). It runs over real HTTP and checks: authentication; `404` for non-members and missing groups; validation; the default of not sharing; turning sharing on/off and how that changes visibility and uploads; that each flag hides the member on its own; that repeating the same `PUT` is idempotent; isolation between two groups in both directions; persistence across an API and database restart; and that settings are dropped when a member leaves.

Run locally from `backend/`:

```sh
mvn -q -DskipTests package && python3 src/test/python/check_group_sharing.py
```

## Out of scope

- Listing the groups I share with (SCRUM-18), immediate revocation guarantees (SCRUM-16) and temporary sharing (SCRUM-17).
- Android/React UI for the toggle.
- Whether locations recorded while sharing is off are retained (an open team privacy decision).
