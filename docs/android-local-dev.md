# Android app: local development (T-23)

The app in `mobile/` is a single Gradle module (Kotlin, Jetpack Compose, Material 3). Debug builds talk to a backend running on your machine; release builds point at the Render API.

## Run it against a local backend

1. Start PostgreSQL and the API as described in [deployment.md](deployment.md#local-development).
2. Build and install the debug app. The emulator reaches the host machine at `10.0.2.2`:

```sh
cd mobile
./gradlew :app:installDebug                                   # API on http://10.0.2.2:8080/
./gradlew :app:installDebug -PapiBaseUrl=http://10.0.2.2:8081/  # API on another port
```

Debug builds allow plain HTTP only to `10.0.2.2` and `localhost` (`app/src/debug/res/xml/network_security_config.xml`). Release builds use HTTPS only.

3. Create an account on the sign-in screen, then create a group on the Hartă screen.

## What is real and what is not yet

| Screen / action | Source |
| --- | --- |
| Register, sign in, token kept on the phone | `POST /auth/register`, `POST /auth/login` |
| Group list, create a group | `GET /groups`, `POST /groups` |
| Start sharing, pick a duration, stop sharing | `PUT` / `DELETE /groups/{id}/me/sharing`, `GET /users/me/sharing` |
| Members, freshness, accuracy, map positions | `GET /groups/{id}/locations/latest` |
| Map | MapLibre with public OpenStreetMap tiles; the camera fits the members who have a position |
| "Oprește partajarea în toate grupurile" | one `DELETE` per group that is on (there is no bulk endpoint) |
| Invitations (Acceptă / Refuză), zones, zone alerts | **local sample data** (`PendingBackendData`): `dev` has no endpoint for them yet |

The map uses OpenStreetMap's public tile server, which is fine for development but not for a release: its [tile usage policy](https://operations.osmfoundation.org/policies/tiles/) asks for a hosted provider in production. The app sends a `User-Agent` of its own and shows the "© OpenStreetMap" credit. The emulator needs internet access to load tiles.

Members who do not share with you are not returned by the API, so they do not appear in the member list. The app does not upload the phone's location yet.

## Seeding a second member for a manual check

There is no invitation endpoint on `dev` yet, so add members to a group directly in the local database. This is for local testing only.

```sh
docker exec infra-postgres-1 psql -U closecircle -d closecircle -c \
  "insert into group_members(group_id, user_id, member_role) values ('<group-id>', '<user-id>', 'MEMBER')"
```

Then, as that user, `PUT /groups/{id}/me/sharing` with `{"sharingEnabled":true,"shareLiveLocation":true}` and `POST /groups/{id}/locations`. A recording earlier than the last sharing change is refused (`403 LOCATION_CONSENT_CHANGED`), so upload after turning sharing on.

## Tests and the coverage gate

```sh
cd mobile
./gradlew :app:testDebugUnitTest :app:coverageVerification :app:coverageReport
```

`coverageVerification` fails below 80% line coverage on `data`, `domain` and the view model. The Compose screens are not part of that number: they are checked by running the app. The HTML report is in `app/build/reports/jacoco/coverageReport/html`. CI runs the same tasks in `.github/workflows/android.yml`.
