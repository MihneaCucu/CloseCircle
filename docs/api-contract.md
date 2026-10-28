# CloseCircle REST API contract

**Status:** Draft v0.1 — proposed for review under SCRUM-42. The route and payload proposals below are not implemented or team-approved yet. Android, React, simulator, and backend owners must review the open decisions before client implementation. The only currently implemented HTTP endpoints are `GET /actuator/health` and `GET /actuator/info`.

This document defines the client-facing JSON contract. It is independent of PostgreSQL table names and columns. Persisted schema changes are versioned separately with Flyway under `backend/src/main/resources/db/migration/`.

## 1. Contract rules

| Topic | Contract |
| --- | --- |
| Local API base URL | `http://localhost:8080` |
| Demo API base URL | `https://closecircle-api.onrender.com` |
| Business API prefix | `/api/v1` |
| Media type | `application/json; charset=utf-8` for request and response bodies |
| Authentication | `Authorization: Bearer <access-token>` on every `/api/v1/**` request, except registration, login, and invitation acceptance as specified below |
| IDs | UUID strings in canonical hyphenated form; clients treat them as opaque values |
| Time | RFC 3339 timestamps in UTC, serialized with `Z`, for example `2026-10-25T10:15:30Z` |
| Coordinates | WGS 84 decimal degrees; latitude `[-90, 90]`, longitude `[-180, 180]` |
| Distance | Metres; location accuracy and zone radius are never expressed in kilometres |
| Empty success | `204 No Content` has no JSON body |
| Unknown fields | Clients should ignore unknown response fields; servers reject unknown enum values with a documented `400` error |
| Nullability | A missing optional field means “not supplied”; explicit `null` is allowed only where a schema says so |
| CORS | Browser access is limited to the explicitly configured dashboard origin; CORS is not authentication |

The backend currently has no business controllers. These are proposed paths and schemas for coordinated implementation; do not infer that a route is live from its appearance here.

## 2. Authentication and access model

The infrastructure currently verifies HS256 signatures and issuer `closecircle`; token issuance and authorization claim mapping are not implemented. The proposed contract requires `sub`, `iat`, and `exp` claims, but the backend must explicitly require them when token issuance is implemented. Password policy, token lifetime, and authorization claim mapping require approval (see [Open decisions](#10-open-decisions-for-scrum-42-review)).

Proposed access token claims:

| Claim | Type | Meaning |
| --- | --- | --- |
| `iss` | string | Always `closecircle` |
| `sub` | UUID string | Authenticated `userId` |
| `iat`, `exp` | integer | Issued-at and expiry Unix seconds |
| `jti` | UUID string | Unique token identifier; include only if revocation/audit needs are approved |

Group roles use the persisted values `OWNER`, `ADMIN`, and `MEMBER`. The API must authorize each request against current group membership; a client-provided `userId`, `role`, or group ID is never proof of access.

| Action | OWNER | ADMIN | MEMBER |
| --- | --- | --- | --- |
| Read group and member list | Yes | Yes | Yes |
| Rename group | Yes | Yes | No |
| Delete group / transfer ownership / change roles | Yes | No | No |
| Invite or revoke invitations | Yes | Yes | No |
| Remove another member | Yes | Yes, except OWNER | No |
| Change own location-sharing preferences | Yes | Yes | Yes |
| Upload own location | Yes | Yes | Yes |
| Read member locations | Yes | Yes | Yes, subject to each member's sharing settings |
| Create, update, or delete zones | Yes | Yes | No |
| Operate the development simulator | Yes | Yes | No; demo-only and subject to T-06/T-25 review |

The proposed generic page envelope is:

```json
{ "items": [], "nextCursor": null }
```

`items` contains the resource type named in the route catalogue. Empty collections return an empty `items` array, not `404`.

**Proposed self-service rule:** every member may change only their own sharing preferences and upload only their own location. A group administrator cannot silently enable another member's location sharing. These privacy rules need explicit team approval.

## 3. Route catalogue

`User` means the Android app. `Admin` means the React dashboard. `Simulator` means the local GPS simulator. All routes below are proposed under `/api/v1` and require HTTPS outside local development.

The currently implemented infrastructure routes live outside `/api/v1`:

| Method and path | Auth | Success response | Purpose |
| --- | --- | --- | --- |
| `GET /actuator/health` | Public | `200 {"status":"UP","groups":["liveness","readiness"]}` | Render health check; details are not exposed. |
| `GET /actuator/info` | Public | `200 {"app":{"commit":"<git-sha>"}}` | CI verifies which commit the API serves. |

All other current backend routes require a valid Bearer JWT. The business routes below are a proposal and are not live yet. Registration, login, and invitation acceptance are the only proposed public/user-establishment exceptions; invitation acceptance still requires the invitee's authenticated identity.

| Method and path | Auth | Client | Request | Success response |
| --- | --- | --- | --- | --- |
| `POST /auth/register` | Public | User | `RegisterRequest` | `201 RegisterResponse` |
| `POST /auth/login` | Public | User | `LoginRequest` | `200 RegisterResponse` |
| `GET /users/me` | User JWT | User | None | `200 User` |
| `PATCH /users/me` | User JWT | User | `UpdateProfileRequest` | `200 User` |
| `GET /groups` | User JWT | User, Admin | `limit`, `cursor` | `200 Page<GroupSummary>` |
| `POST /groups` | User JWT | User | `CreateGroupRequest` | `201 GroupDetails` |
| `GET /groups/{groupId}` | Group member | User, Admin | UUID `groupId` | `200 GroupDetails` |
| `PATCH /groups/{groupId}` | Owner or Admin | User, Admin | UUID `groupId`; `UpdateGroupRequest` | `200 GroupDetails` |
| `DELETE /groups/{groupId}` | Owner | User, Admin | UUID `groupId` | `204` |
| `GET /groups/{groupId}/members` | Group member | User, Admin | UUID `groupId`; `limit`, `cursor` | `200 Page<Member>` |
| `PATCH /groups/{groupId}/members/{userId}` | Owner | User, Admin | UUIDs; `UpdateMemberRoleRequest` | `200 Member` |
| `POST /groups/{groupId}/owner` | Current owner | User, Admin | UUID `groupId`; `TransferOwnerRequest` | `200 OwnerTransferResponse` |
| `DELETE /groups/{groupId}/members/{userId}` | Owner or Admin | User, Admin | UUIDs | `204` |
| `POST /groups/{groupId}/invitations` | Owner or Admin | User, Admin | UUID `groupId`; `CreateInvitationRequest` | `201 Invitation` |
| `GET /groups/{groupId}/invitations` | Owner or Admin | User, Admin | UUID `groupId`; optional `status`, `limit`, `cursor` | `200 Page<Invitation>` |
| `DELETE /groups/{groupId}/invitations/{invitationId}` | Owner or Admin | User, Admin | UUIDs | `204` |
| `POST /invitations/accept` | User JWT | User | `AcceptInvitationRequest` | `200 Membership` |
| `PUT /groups/{groupId}/me/sharing` | Group member | User | UUID `groupId`; `UpdateSharingRequest` | `200 SharingPreferences` |
| `POST /groups/{groupId}/locations` | Group member | User | UUID `groupId`; `CreateLocationRequest` | `201 Location` |
| `GET /groups/{groupId}/locations/latest` | Group member | User, Admin | UUID `groupId`; `limit`, `cursor` | `200 Page<LatestLocation>` |
| `GET /groups/{groupId}/members/{userId}/locations` | Group member | User, Admin | UUIDs; required `from`, `to`; `limit`, `cursor` | `200 Page<Location>` |
| `GET /groups/{groupId}/zones` | Group member | User, Admin | UUID `groupId`; `limit`, `cursor` | `200 Page<Zone>` |
| `POST /groups/{groupId}/zones` | Owner or Admin | User, Admin | UUID `groupId`; `CreateZoneRequest` | `201 Zone` |
| `PATCH /groups/{groupId}/zones/{zoneId}` | Owner or Admin | User, Admin | UUIDs; `UpdateZoneRequest` | `200 Zone` |
| `DELETE /groups/{groupId}/zones/{zoneId}` | Owner or Admin | User, Admin | UUIDs | `204` |
| `GET /notifications` | User JWT | User | `unreadOnly`, `limit`, `cursor` | `200 Page<Notification>` |
| `PATCH /notifications/{notificationId}` | User JWT | User | UUID; `MarkNotificationReadRequest` | `200 Notification` |
| `POST /admin/simulator/scenarios` | Owner or Admin | Simulator, Admin | `StartScenarioRequest` | `201 Scenario` |
| `GET /admin/simulator/scenarios/{scenarioId}` | Owner or Admin | Simulator, Admin | UUID `scenarioId` | `200 Scenario` |
| `POST /admin/simulator/scenarios/{scenarioId}/stop` | Owner or Admin | Simulator, Admin | UUID `scenarioId` | `204` |

The simulator routes are proposals, not an authorization bypass. They must be disabled outside the agreed development/demo environment, must emit only synthetic coordinates, and must not allow a client to impersonate a real account. If the team chooses to keep simulation entirely local, remove these routes before implementation.

## 4. JSON schemas and validation

The examples in this section are normative shapes for the draft contract. All names, addresses, UUIDs, tokens, and coordinates are fabricated; `example.invalid` is reserved for documentation. Fields not shown in a response may be added compatibly; required request fields may not silently change after client implementation begins.

### Common objects

```json
{
  "user": {
    "userId": "3f91d50d-98ab-4f20-b450-5f3311b28b81",
    "email": "alex@example.invalid",
    "displayName": "Alex",
    "createdAt": "2026-10-25T10:15:30Z"
  },
  "group": {
    "groupId": "2c76b551-b127-49b7-bfe7-9606f1e35da2",
    "name": "Weekend crew",
    "createdBy": "3f91d50d-98ab-4f20-b450-5f3311b28b81",
    "createdAt": "2026-10-25T10:16:00Z"
  },
  "member": {
    "userId": "3f91d50d-98ab-4f20-b450-5f3311b28b81",
    "displayName": "Alex",
    "role": "OWNER",
    "joinedAt": "2026-10-25T10:16:00Z"
  }
}
```

`GroupSummary` is `{groupId, name, createdBy, createdAt, myRole, memberCount}`. `GroupDetails` contains the same fields. In a member-list response, each `Member` is `{userId, displayName, role, joinedAt}`. `myRole` describes only the caller's membership and must not be trusted by the server for authorization.

- `email`: trim surrounding whitespace, validate as an email address, maximum 320 characters; comparisons are case-insensitive. Registration uniqueness conflicts return `409`.
- `password`: never echoed or logged; minimum 12 characters is proposed, with a maximum of 128 characters. Password hashing and any breach screening are backend responsibilities. The minimum requires team approval.
- `displayName`, group `name`, zone `name`: non-empty after trimming; proposed maximum 100 characters.
- `role`: exactly `OWNER`, `ADMIN`, or `MEMBER`; clients cannot assign `OWNER` through member-management routes.
- Path parameters ending in `Id` must parse as UUIDs; invalid UUIDs return `400`.
- `limit` is an integer; `cursor` is an opaque string; `unreadOnly` is a boolean; invitation `status` is one of `PENDING`, `ACCEPTED`, `REVOKED`, or `EXPIRED`.
- `latitude` and `longitude` must be finite JSON numbers within the ranges in section 1. `radiusM` must be an integer in the database-supported range 10–100000.

### Authentication

`POST /auth/register` request:

```json
{
  "email": "alex@example.invalid",
  "password": "correct-horse-example",
  "displayName": "Alex"
}
```

`POST /auth/login` request:

```json
{ "email": "alex@example.invalid", "password": "correct-horse-example" }
```

Successful registration (`201`) and login (`200`) return the same shape; a response example uses a fictitious token:

```json
{
  "accessToken": "eyJ...example-only...",
  "tokenType": "Bearer",
  "expiresAt": "2026-10-25T11:15:30Z",
  "user": {
    "userId": "3f91d50d-98ab-4f20-b450-5f3311b28b81",
    "email": "alex@example.invalid",
    "displayName": "Alex",
    "createdAt": "2026-10-25T10:15:30Z"
  }
}
```

`GET /users/me` returns the `user` object. `PATCH /users/me` accepts `{"displayName":"Alex Morgan"}` and returns the updated `user`. Empty updates and unknown fields return `400`. Clients discard the access token on logout; a stateless token cannot be revoked before expiry without a separately approved revocation mechanism.

### Groups and members

`GET /groups` and `GET /groups/{groupId}/members` return a `Page<T>` object (see [Pagination](#5-pagination-and-filtering)). Group creation request and response:

```json
{ "name": "Weekend crew" }
```

```json
{
  "groupId": "2c76b551-b127-49b7-bfe7-9606f1e35da2",
  "name": "Weekend crew",
  "createdBy": "3f91d50d-98ab-4f20-b450-5f3311b28b81",
  "createdAt": "2026-10-25T10:16:00Z",
  "myRole": "OWNER",
  "memberCount": 1
}
```

`GET /groups/{groupId}` returns the same group shape. `PATCH /groups/{groupId}` accepts `{"name":"Hiking crew"}` and returns the updated group. Successful group or member removal returns `204` with no body. Member lists contain only `userId`, `displayName`, `role`, and `joinedAt`; they never expose email unless a future contract explicitly approves it.

`PATCH /groups/{groupId}/members/{userId}` accepts `{"role":"ADMIN"}` or `{"role":"MEMBER"}` and returns the updated member object. `POST /groups/{groupId}/owner` accepts `{"newOwnerId":"27b2f319-3586-4ec8-a890-9b33021dc26a"}` and returns:

```json
{
  "groupId": "2c76b551-b127-49b7-bfe7-9606f1e35da2",
  "previousOwner": { "userId": "3f91d50d-98ab-4f20-b450-5f3311b28b81", "role": "ADMIN" },
  "newOwner": { "userId": "27b2f319-3586-4ec8-a890-9b33021dc26a", "role": "OWNER" }
}
```

Ownership transfer is atomic; the previous owner becomes `ADMIN` by default (proposed behavior).

### Invitations

Create request and response:

```json
{ "email": "sam@example.invalid" }
```

```json
{
  "invitationId": "ac4a01c0-0c6e-4a49-bfa7-e3db6768da57",
  "groupId": "2c76b551-b127-49b7-bfe7-9606f1e35da2",
  "email": "sam@example.invalid",
  "status": "PENDING",
  "createdAt": "2026-10-25T10:20:00Z",
  "expiresAt": "2026-11-01T10:20:00Z"
}
```

The raw invitation token must be delivered out-of-band through the team-approved invitation channel. It is never included in a list response, URL query, or logs; PostgreSQL stores only its hash. The response to `POST /groups/{groupId}/invitations` includes the token only if the team explicitly selects a client-generated invitation-link flow; this choice is unresolved. `GET /groups/{groupId}/invitations` returns a page of invitation objects without tokens. `POST /invitations/accept` request and response:

```json
{ "token": "one-time-example-token" }
```

```json
{
  "groupId": "2c76b551-b127-49b7-bfe7-9606f1e35da2",
  "userId": "27b2f319-3586-4ec8-a890-9b33021dc26a",
  "role": "MEMBER",
  "joinedAt": "2026-10-25T10:24:00Z"
}
```

Expired, revoked, already-used, or email-mismatched tokens return `400` with a stable error `code`; the precise public message must not disclose whether an email address has an account.

### Sharing and locations

`PUT /groups/{groupId}/me/sharing` replaces the caller's settings and returns the saved object:

```json
{ "sharingEnabled": true, "shareLiveLocation": true }
```

```json
{
  "groupId": "2c76b551-b127-49b7-bfe7-9606f1e35da2",
  "userId": "3f91d50d-98ab-4f20-b450-5f3311b28b81",
  "sharingEnabled": true,
  "shareLiveLocation": true,
  "updatedAt": "2026-10-25T10:30:00Z"
}
```

For v1, `sharingEnabled=false` suppresses all location visibility for the caller; `shareLiveLocation=false` suppresses live location while retaining only history that the user has explicitly agreed may be retained. Whether the API accepts or rejects location uploads while sharing is disabled, and how long history is retained, are blocking privacy decisions and remain unapproved.

`POST /groups/{groupId}/locations` request and response:

```json
{
  "latitude": 12.3456,
  "longitude": -45.6789,
  "accuracyM": 12.5,
  "recordedAt": "2026-10-25T10:30:00Z"
}
```

```json
{
  "locationId": "ba5bbefd-01d6-4313-9199-fbc12540b38f",
  "groupId": "2c76b551-b127-49b7-bfe7-9606f1e35da2",
  "userId": "3f91d50d-98ab-4f20-b450-5f3311b28b81",
  "latitude": 12.3456,
  "longitude": -45.6789,
  "accuracyM": 12.5,
  "recordedAt": "2026-10-25T10:30:00Z",
  "receivedAt": "2026-10-25T10:30:01Z"
}
```

`accuracyM` may be omitted or `null`; if present it must be finite and non-negative. `recordedAt` is required and may not be more than five minutes in the future (proposed clock-skew allowance). The server derives the user from JWT and checks membership and sharing permissions before persisting. `GET /groups/{groupId}/locations/latest` returns a page of `{userId, displayName, location}` entries, excluding the caller and any member whose sharing settings or permissions prohibit visibility. Each `location` is a `Location` object or `null` when an otherwise visible member has not uploaded a point; do not include a member whose sharing is disabled. `GET /groups/{groupId}/members/{userId}/locations` returns a page of location objects filtered by required `from` and `to` timestamps; maximum requested history range is a policy decision that must be agreed before implementation.

`LatestLocation` example:

```json
{
  "userId": "3f91d50d-98ab-4f20-b450-5f3311b28b81",
  "displayName": "Alex",
  "location": {
    "locationId": "ba5bbefd-01d6-4313-9199-fbc12540b38f",
    "latitude": 12.3456,
    "longitude": -45.6789,
    "accuracyM": 12.5,
    "recordedAt": "2026-10-25T10:30:00Z",
    "receivedAt": "2026-10-25T10:30:01Z"
  }
}
```

Precise coordinates, location payloads, authorization headers, and invitation tokens must not be written to application, access, SQL, or CI logs. HTTPS is required for every non-local location request.

### Zones

`POST /groups/{groupId}/zones` request and response:

```json
{
  "name": "Home",
  "center": { "latitude": 12.3456, "longitude": -45.6789 },
  "radiusM": 250,
  "enabled": true
}
```

```json
{
  "zoneId": "f100192f-81a1-4411-9c1c-e516e886d825",
  "groupId": "2c76b551-b127-49b7-bfe7-9606f1e35da2",
  "name": "Home",
  "center": { "latitude": 12.3456, "longitude": -45.6789 },
  "radiusM": 250,
  "enabled": true,
  "createdBy": "3f91d50d-98ab-4f20-b450-5f3311b28b81",
  "createdAt": "2026-10-25T10:35:00Z"
}
```

`GET /groups/{groupId}/zones` returns a page of zone objects. `PATCH /groups/{groupId}/zones/{zoneId}` accepts any non-empty subset of `name`, `center`, `radiusM`, and `enabled`, and returns the updated zone. `radiusM` must be an integer between 10 and 100000 inclusive, matching the initial database constraint. Example patch request: `{"center":{"latitude":12.3456,"longitude":-45.6789},"radiusM":500}`; success returns the complete updated `Zone` object shown above. `DELETE` returns `204`. Zone arrival/departure detection, hysteresis, offline behavior, and delivery are not implied by these CRUD routes; they belong to functional and live-update tasks.

### Notifications

`GET /notifications?unreadOnly=true&limit=50` returns a `Page<Notification>` object. `PATCH /notifications/{notificationId}` accepts only `{"read":true}` and returns the updated notification; setting `read` false is not supported in v1. Example object:

```json
{
  "notificationId": "5eb9014d-a8b4-410b-82cb-5ec2047d8b74",
  "groupId": "2c76b551-b127-49b7-bfe7-9606f1e35da2",
  "type": "ZONE_ENTRY",
  "createdAt": "2026-10-25T10:40:00Z",
  "readAt": null,
  "data": {
    "memberId": "3f91d50d-98ab-4f20-b450-5f3311b28b81",
    "displayName": "Alex",
    "zoneId": "f100192f-81a1-4411-9c1c-e516e886d825",
    "zoneName": "Home"
  }
}
```

`data` is a discriminated, versioned payload keyed by `type`; it contains no coordinates. Push delivery, retry, and live transport are outside this REST contract. The notification type registry and retention policy need team approval.

### Simulator

`POST /admin/simulator/scenarios` request and response proposal:

```json
{
  "groupId": "2c76b551-b127-49b7-bfe7-9606f1e35da2",
  "scenario": "DELAYED_UPDATES",
  "members": ["3f91d50d-98ab-4f20-b450-5f3311b28b81"],
  "durationSeconds": 120
}
```

```json
{
  "scenarioId": "04e6e20f-d5a3-46df-8e46-124418ab3b1d",
  "groupId": "2c76b551-b127-49b7-bfe7-9606f1e35da2",
  "scenario": "DELAYED_UPDATES",
  "status": "RUNNING",
  "startedAt": "2026-10-25T10:45:00Z",
  "endsAt": "2026-10-25T10:47:00Z"
}
```

Allowed scenarios and controls are provisional. A simulator must use synthetic accounts and locations, have a short maximum duration, and be disabled in production. `GET` returns the same scenario object; `POST .../{scenarioId}/stop` returns `204`. The simulator must never accept a client-supplied identity as authority to write a real member's location.

## 5. Pagination and filtering

Collection responses use:

```json
{
  "items": [],
  "nextCursor": null
}
```

`limit` is optional, defaults to 50, and is clamped to 1–100. `cursor` is an opaque server-issued string; clients pass it back unchanged and do not construct or interpret it. A missing `nextCursor` means the collection is complete. `unreadOnly` accepts only `true` or `false`. Invitation `status` accepts one of the enum values above. Location history requires `from` and `to` in RFC 3339 UTC, with `from < to`; `limit`/`cursor` paginate within that interval. Stable ordering is ascending `(recordedAt, locationId)` for history and descending `(createdAt, id)` for other collections. Exact time-window and retention limits remain open decisions.

## 6. Errors and HTTP status codes

Errors use the RFC 9457 Problem Details shape with a stable CloseCircle `code` extension. Example:

```json
{
  "type": "https://closecircle.example/problems/validation-error",
  "title": "Request validation failed",
  "status": 400,
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/groups",
  "code": "VALIDATION_ERROR",
  "traceId": "a120e885ebc644c7",
  "errors": [
    { "field": "name", "code": "REQUIRED", "message": "Name must not be blank." }
  ]
}
```

`detail`, `instance`, `traceId`, and `errors` may be omitted when not applicable. Never put credentials, JWTs, invitation tokens, SQL, or precise coordinates in error details.

| Status | Use |
| --- | --- |
| `200` | Successful read or update with a response body |
| `201` | Resource created; response includes the created representation and `Location` header where applicable |
| `204` | Successful deletion or action with no response body |
| `400` | Malformed JSON, validation error, invalid/expired invitation, unsupported query value |
| `401` | Missing, invalid, or expired access token; include `WWW-Authenticate: Bearer` |
| `403` | Authenticated caller lacks the required role, membership, or sharing permission |
| `404` | Resource absent or not visible to the caller; avoid confirming another group's existence |
| `409` | Unique conflict, duplicate membership/invitation, or invalid state transition |
| `413` | Request exceeds agreed body/batch limits |
| `415` | Unsupported media type |
| `429` | Rate limit exceeded; include `Retry-After` when known |
| `500` | Unexpected server failure; return only a generic message and trace ID |
| `503` | Service/database temporarily unavailable; clients may retry with bounded backoff |

Clients should retry idempotent reads on transient `503`/network errors with bounded exponential backoff. They must not blindly retry non-idempotent POSTs unless an idempotency key is supported and approved. Error codes, retry behavior, request-size limits, and rate limits must be shared across Android and React clients.

## 7. Main client–server flows

### Register, create a group, and invite a member

1. Android calls `POST /auth/register` or `POST /auth/login`, then stores the access token using platform-secure storage.
2. The app calls `POST /groups`; the server creates the group and an `OWNER` membership transactionally.
3. An owner/admin calls `POST /groups/{groupId}/invitations`; the invitation token is delivered through the approved channel.
4. The invitee authenticates, then calls `POST /invitations/accept` with the one-time token. The server verifies token hash, expiry, status, and email match before creating membership.

### Share and view locations

1. A member sets their own preference with `PUT /groups/{groupId}/me/sharing`.
2. Android uploads a point with `POST /groups/{groupId}/locations`; the server derives identity from JWT, checks membership/consent, validates coordinates and time, then writes the location.
3. A group member calls `GET /groups/{groupId}/locations/latest` or a bounded history query. The server filters every result by membership and sharing policy before returning it.
4. Android may keep unsent locations in SQLite while offline. It sends them later only if their age, consent, ordering, and duplicate semantics satisfy the final sync policy. Client-side SQLite is not the shared source of truth.

### Configure a zone and read notifications

1. An owner/admin creates or updates a circular zone through `/groups/{groupId}/zones`.
2. Functional processing determines entry/exit events and persists a notification. This contract exposes stored notifications but does not define continuous worker availability or push delivery.
3. Android reads `GET /notifications` and marks an item with `PATCH /notifications/{id}`.

### Run a demo scenario

1. An authorized demo operator starts a short scenario through `/admin/simulator/scenarios`.
2. The simulator reports state through `GET`; stop is idempotently requested through `POST .../{scenarioId}/stop`.
3. Only synthetic data is generated, and these endpoints are unavailable outside the configured demo environment.

## 8. Persistence boundary

The API exposes named JSON fields, never SQL rows or database column names. Relevant initial PostgreSQL entities are `users`, `groups`, `group_members`, `invitations`, `shares`, `locations`, `zones`, and `notifications`, defined in `V1__initial_schema.sql`. Flyway migrations evolve persistence independently; changing a table or index does not change the client contract unless an API field or behavior changes.

Sensitive-data rules apply end to end: do not log request bodies, Authorization headers, passwords, JWTs, invitation tokens, or precise coordinates. Avoid returning another member's email or internal security metadata. Location visibility and retention decisions must be resolved before implementing reads or exposing any coordinates to clients.

## 9. Compatibility and change process

- Coordinate every contract change with Android, React, simulator, and backend owners before implementation.
- Add optional response fields compatibly. Do not rename/remove fields, change units, tighten validation, or change authorization semantics in-place after clients depend on them; introduce a versioned change or a migration plan.
- Keep database changes in new Flyway migrations. Do not expose entities directly or couple JSON names to SQL columns.
- For each approved route, update this contract and its request/response examples in the same change as implementation. Add integration tests for authorization boundaries, validation, and persistence as part of the implementation task.
- Record decisions in the review table below. The first approved version should replace the DRAFT status at the top and list approvers/date.

## 10. Open decisions for SCRUM-42 review

| Decision | Current proposal | Owner(s) to confirm | Blocks |
| --- | --- | --- | --- |
| Registration and login | Email + password; JWT access token; no refresh/logout endpoint in v1 | Backend, Android | Auth implementation |
| Password policy and token lifetime | Password minimum 12 characters; JWT duration to be selected; no revocation without state | Backend, security | Auth implementation |
| Email verification and invite delivery | One-time hashed invitation token; delivery channel/provider not selected | Product, backend | Invitations |
| Owner transfer and group deletion | Owner-only deletion; owner must transfer before leaving; deletion retention behavior TBD | Product, backend | Group administration |
| Role capabilities | Matrix in section 2; owner-only role changes and atomic transfer proposed | Product, Android, backend | Authorization and admin UI |
| Invitation delivery | Out-of-band channel needed; whether the API returns a token once for client-generated links is unresolved | Product, backend, Android | Invitation acceptance |
| Sharing privacy and retention | Self-service controls; uploads while sharing is off and historical retention are unresolved | Product, privacy, Android | Location read/write |
| Location limits and offline sync | Five-minute future clock skew; history time window, max upload frequency, batch/idempotency semantics TBD | Android, backend | Location upload/history |
| Zone events | Circular zones only; detection state, hysteresis, deduplication, offline delivery handled by functional tasks | Backend, Android | Zone event processing |
| Notification types and retention | `ZONE_ENTRY` example only; registry, retention and push delivery are separate decisions | Product, backend, Android | Notifications |
| Simulator behavior | Demo-only scenarios; scenario names, member selection and operational controls TBD | T-06 owner, T-25 owner, backend | Simulator endpoints |
| Pagination and rate limits | Opaque cursor, default 50/max 100; per-route throttles and payload caps TBD | Backend, clients | Collection endpoints |

The endpoint catalogue and examples are a concrete review draft, not team approval. The task can be drafted now; its Definition of Done still requires the Android, React, and backend owners to review and agree on the unresolved behavior.
