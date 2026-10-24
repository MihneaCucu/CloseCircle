# CloseCircle REST contract

**Status:** infrastructure baseline. Business endpoints require team review under SCRUM-42 before Android, React, or simulator clients implement them. This document does not define those endpoints.

## Ownership and compatibility

- JSON request/response models and client-facing endpoint paths are an API contract, independent of PostgreSQL table names and columns.
- Persistent changes belong in versioned Flyway migrations under `backend/src/main/resources/db/migration/`.
- Coordinate contract changes with the Android, React, and backend owners before merging. Do not serialize database rows directly as API responses.
- Spring Security protects all routes other than the health endpoint with stateless Bearer JWT authentication. JWT signatures use HS256 and the issuer claim must be `closecircle`; expiry and standard time claims are validated. This infrastructure does not issue tokens or define role/claim authorization. Keep demo and simulator data behind authenticated endpoints when those features are added.

## Available infrastructure endpoint

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/actuator/health` | Public, status only | Render startup and service health check; does not expose component details. |
| `GET` | `/actuator/info` | Public, commit SHA only | CI deployment check; reports the Render Git commit serving the API. |

## Location representation

The persistence model stores a point as `latitude` and `longitude` in decimal degrees (WGS 84), `accuracy_m` in metres, and `recorded_at` as a timezone-aware timestamp. API JSON should use named fields and an ISO 8601 timestamp with an explicit UTC offset (prefer `Z`). This is a data-format baseline, not a final endpoint schema. Location visibility, retention, recipient authorization, and precision policy must be specified with the functional API contract before exposing coordinates to a client.

## Persistent entities

The initial migration defines `users`, `groups`, `group_members`, `invitations`, `shares`, `locations`, `zones`, and `notifications`. Relational constraints and indexes live in the migration; they are not the client contract.
