# CloseCircle

The CloseCircle project repository contains the Android app, backend API, and admin dashboard.

## About CloseCircle

CloseCircle is a location-sharing app designed for small, trusted groups. Members can create a group, invite people they know, and share location updates with the group. A shared map makes it easier to see where everyone is, while location zones can signal when a member arrives or leaves an important place.

The Android app is the main experience for group members. A Spring Boot backend handles authentication, group access, and shared data. The React admin dashboard and GPS simulator support development and demonstrations: they let the team inspect simulated users, review zone events, and reproduce scenarios such as delayed updates or lost connectivity.

This repository currently contains the project scaffold; these capabilities are being developed.

### Planned capabilities

- Create groups and invite members.
- Share and view location updates within a group.
- Define geographic zones and receive arrival and departure notifications.
- Review simulated activity and test scenarios through the admin dashboard.

## Structure

```text
CloseCircle/
├── mobile/                 # Android app (Kotlin, SQLite)
├── backend/                # REST API (Java, Spring Boot)
├── dashboard-admin/        # Admin dashboard (React, TypeScript)
├── docs/                   # REST contract, architecture, and deployment docs
├── infra/                  # Local service configuration and Docker Compose
├── .github/workflows/      # CI/CD workflows (GitHub Actions)
├── .gitignore
└── README.md
```

## Conventions

- The shared client–server contract lives in `docs/api-contract.md` and is reviewed by the Android, React, and backend owners.
- `mobile/`, `backend/`, and `dashboard-admin/` are separate applications that integrate through the documented REST API.
- PostgreSQL migrations and the code that applies them belong to the backend (`backend/src/main/resources/db/migration/` for Flyway).
- `infra/` is for local development and testing. In the demo environment, the backend runs on Render, the database runs on Neon, and the dashboard is deployed as a Static Site.
- GitHub Actions workflows live in `.github/workflows/`. Configure deployment secrets in GitHub; never commit them to the repository.

## Services

| Component | Technology | Responsibility |
| --- | --- | --- |
| Mobile app | Kotlin, Android, SQLite | User interface and local storage |
| Backend | Java, Spring Boot, Docker | REST API, JWT authentication, and application logic |
| Database | PostgreSQL on Neon | Persistent storage for shared data |
| Admin dashboard | React, TypeScript | Administration, map, and simulator control |
