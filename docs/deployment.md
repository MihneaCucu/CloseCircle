# Initial infrastructure and deployment

This is a development/demo deployment. It is not intended for production. Render Free can suspend an idle web service, and Neon can suspend an idle compute. Background processing and timely location delivery are therefore not guaranteed. RabbitMQ and a background worker are outside this initial deployment.

## Architecture

```text
Android (SQLite) ── HTTPS REST ──> Spring Boot API (Render Docker Web Service)
                                          │ JDBC/TLS, Hikari pool max 5
                                          ▼
                                     PostgreSQL (Neon)

React dashboard / local simulator ── HTTPS + authenticated API (functional tasks)
React baseline ── HTTPS GET /actuator/health (this task)
```

SQLite is client-side/offline storage, not a shared source of truth. Shared application data persists in Neon across API restarts and redeploys. The current baseline only serves the health endpoint; future business routes are protected by stateless JWT Bearer validation (HS256, issuer `closecircle`) but routes, token issuance, and authorization claims are functional work. Never put a database credential or JWT signing secret in the React build, Android app, Docker image, logs, or Git.

## Create the Neon database

1. Create a Neon project in the Frankfurt region (`eu-central-1`) if it is available for the account. Place Render's API in Frankfurt as configured in `render.yaml` to keep the services close.
2. Create a database, a runtime role, and a schema-migration role. Keep the project owner/admin credential separate from both. Use `closecircle_app` as the runtime role and `closecircle_migrator` as the migration role (or keep the names consistent with the grants in the SQL below).
3. Use Neon CLI's pooled `DATABASE_URL` for application traffic and its direct `DATABASE_URL_UNPOOLED` for Flyway. Both values contain role credentials and require TLS (`sslmode=require`). The backend accepts Neon's `postgresql://` URIs and translates them to JDBC internally. Keep the CLI-provided values intact; never paste them into tracked files.
4. `DB_POOL_MAX_SIZE=5` caps the API's Hikari pool. Do not multiply service instances on the Free plan. This app-side cap also avoids consuming a large number of Neon connections. For a future multi-instance deployment, revisit the aggregate connection budget and Neon pooler mode.
5. The CLI supplies both URLs for the selected Neon role. For this initial development deploy, the branch role must have DDL access because Flyway applies versioned migrations at API startup. If you later split runtime and migration roles, supply the pooled runtime URI as `DATABASE_URL`, the direct migration URI as `DATABASE_URL_UNPOOLED`, and configure explicit username/password overrides for each connection. Example grants for such a split, executed as the Neon project owner in the target database:

   ```sql
   CREATE ROLE closecircle_migrator LOGIN PASSWORD 'set-out-of-band';
   CREATE ROLE closecircle_app LOGIN PASSWORD 'set-out-of-band';
   GRANT CONNECT ON DATABASE closecircle TO closecircle_migrator, closecircle_app;
   GRANT USAGE, CREATE ON SCHEMA public TO closecircle_migrator;
   ALTER DEFAULT PRIVILEGES FOR ROLE closecircle_migrator IN SCHEMA public
     GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO closecircle_app;
   ALTER DEFAULT PRIVILEGES FOR ROLE closecircle_migrator IN SCHEMA public
     GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO closecircle_app;
   ```

   If V1 has already run, also apply `GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO closecircle_app;` and `GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO closecircle_app;`. Migration `V1__initial_schema.sql` runs automatically at API startup using the direct URL. Hibernate is set to validate the schema, not create or alter it.

## Local development

Prerequisites: Docker Compose, Java 21, and Maven (or use the backend Dockerfile for container builds).

```sh
docker compose -f infra/compose.yaml up -d postgres
cp backend/.env.example backend/.env
cd backend
set -a; . ./.env; set +a
mvn spring-boot:run
```

The local profile defaults to `localhost:5432`, `sslmode=disable`, and a disposable local-only password. Flyway applies the migration at startup. For the linked Neon branch, run `npx --yes neon@latest env pull` from the repository root (the CLI normally pulls during `link`/`checkout`) and source the resulting `.env.local` without printing it:

```sh
set -a; . ./.env.local; set +a
cd backend
mvn spring-boot:run
```

Neon URIs include credentials; the backend converts them in memory and does not log them. In a separate terminal:

```sh
curl -i http://localhost:8080/actuator/health
cd dashboard-admin
npm install
VITE_API_BASE_URL=http://localhost:8080 npm run dev
```

For local dashboard health checks, add the exact local dashboard origin to `CORS_ALLOWED_ORIGINS` (default `http://localhost:5173`). Never copy local credentials into Render.

## Initial Render deployment

1. Push this repository to GitHub and connect it to Render. Create a Blueprint from the root `render.yaml`, or create the services manually from that file's settings.
2. The API builds from `backend/Dockerfile` using a Maven build stage and a small Java runtime stage. It listens on Render's `PORT` (default 10000) and limits JVM heap sizing with `MaxRAMPercentage=70` and Serial GC for the Free instance memory budget.
3. Set the prompted backend values in Render's environment settings: `DATABASE_URL` (Neon's pooled URL), `DATABASE_URL_UNPOOLED` (direct URL for Flyway), `JWT_SECRET`, and `CORS_ALLOWED_ORIGINS`. For the last value, use the exact HTTPS origin of the deployed Static Site, without a path or trailing slash. If the static site's final hostname is not known during Blueprint setup, update `CORS_ALLOWED_ORIGINS` in the backend service settings after creation. Blueprint `sync: false` values are only prompted on initial creation; manage later changes directly in Render. `JWT_SECRET` should be a randomly generated secret with at least 32 bytes; rotate it through Render if exposed. The API validates HS256 Bearer tokens signed with this key and issuer `closecircle`; token issuance is future functional work.
4. Set the dashboard's `VITE_API_BASE_URL` to the API's HTTPS `onrender.com` base URL. It is a public URL, not a secret. The page calls `/actuator/health` and reports whether the API responds. Do not add database or JWT values to dashboard environment variables.
5. Render assigns HTTPS URLs and certificates. `/actuator/health` is the API health path. The root `render.yaml` disables automatic deploys so the T-26 / SCRUM-34 workflow can deploy only a verified commit. Until that workflow exists, deploy manually from Render after reviewing the commit.

## Schema and persistence smoke check

After the API deploy, confirm `/actuator/health` is `UP`, and check the Render startup log for Flyway migration `V1` success. To verify an actual write/read on the published database, connect with an authorized `psql` client using the same Neon database, but do not put the password in shell history. Create a disposable sentinel using a test email, query it back, restart or redeploy the API, query it again, and delete it after the check:

```sql
INSERT INTO users (email, password_hash, display_name)
VALUES ('infra-smoke@example.invalid', 'not-a-real-login', 'Infra smoke check')
RETURNING user_id, email, created_at;

SELECT user_id, email, display_name
FROM users
WHERE email = 'infra-smoke@example.invalid';
```

On Oct 24, 2026, the public API was deployed at `https://closecircle-api.onrender.com` and `/actuator/health` returned `UP` after the Free instance woke. The public database reported Flyway version `1`. A committed synthetic user was inserted and read back, the API service was restarted, and the row was read again from Neon. The test row was deleted afterward. An earlier transaction-only write/read was rolled back. After rotating the Neon password, the new pooled and direct URLs were saved in Render; the API returned `UP` and the committed write/read still passed across a Render restart.

The dashboard is published at `https://closecircle-dashboard.onrender.com` and returned HTTP 200. An OPTIONS preflight from that exact origin returned HTTP 200 with `Access-Control-Allow-Origin: https://closecircle-dashboard.onrender.com`.

## Export and restore rehearsal

Use `pg_dump`/`pg_restore` from a trusted workstation with the PostgreSQL client tools installed. Prefer `PGPASSWORD` in a short-lived process environment or an interactive password prompt over including credentials in command history. Keep the dump out of Git (the repository ignores `*.dump`). Use synthetic demo data only.

```sh
pg_dump --format=custom --no-owner --no-acl \
  --dbname="$NEON_DATABASE_URL" --file=closecircle-demo.dump

# Create a separate empty restore database/branch in Neon first, then:
pg_restore --clean --if-exists --no-owner --no-acl \
  --dbname="$NEON_RESTORE_DATABASE_URL" closecircle-demo.dump

psql "$NEON_RESTORE_DATABASE_URL" -c \
  'SELECT count(*) FROM users;'
```

On Oct 24, 2026, this logical export/restore procedure was rehearsed with PostgreSQL client tools 18.6. The source database had no application rows at export time. A separate Neon database named `closecircle_restore_test_20261024` received only a synthetic user, a custom-format dump was created outside the repository, and `pg_restore --clean --if-exists --no-owner --no-acl` restored it. Verification found Flyway migration V1 and the synthetic user in the restored database. The source synthetic user and temporary restore database were removed afterward. The temporary dump was deleted and is not a backup artifact for ongoing recovery. A Neon branch/PITR restore and a logical `pg_dump` archive serve different recovery needs; confirm current retention and restore windows in the Neon console before relying on them.

## Logs, secrets, and recovery

- Keep request bodies, authorization headers, passwords, JWTs, and precise coordinates out of logs. The baseline does not install request-body logging or SQL statement logging. Never log entity `toString()` values containing credentials or coordinates.
- If any credential is exposed, rotate it in Neon/Render, update the service secret, and redeploy. If schema migration fails, inspect the migration error and database history table; fix forward with a new migration rather than editing an already-applied version.
- Free Render services spin down after 15 idle minutes and can take about a minute to wake. Their local filesystem is ephemeral and Free Web Services have no persistent disks. Do not store application data or backups on the service filesystem.
- Neon Free limits can change. As of Oct 24, 2026, Neon documents 100 CU-hours and 1 GB database storage per Free project; monitor current project quotas, egress, compute use, and restore window in the console. Neon can scale idle compute to zero, adding connection/startup delay.
- This small API uses synchronous request processing only. Do not assume scheduled or continuous background work will run reliably on Render Free.

## T-26 / SCRUM-34 automation handoff

The root Blueprint has `autoDeploy: false` for both Render services so a later GitHub Actions workflow can test/build a commit, trigger the matching Render deploy hook only after success, then poll `/actuator/health` and fail the workflow on a bad status. Store deploy-hook URLs and any service credentials as GitHub Actions secrets. The workflow itself belongs in T-26 and is intentionally not implemented here.
