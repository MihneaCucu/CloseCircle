# Checkpoint 2 readiness

The confirmed target is the weeks 7-8 milestone: implement at least one-third of the User Stories committed for implementation. The course handout, *Proiect de laborator MOPS*, page 8, section II, defines this target; pages 1 and 4-6 describe acceptance checks, Definition of Done, tests and development-process evidence. Backend endpoints, technical tasks and subtasks are not automatically separate completed User Stories.

## Backend handoff for SCRUM-19 and SCRUM-20

Backend PRs [#1](https://github.com/MihneaCucu/CloseCircle/pull/1), [#3](https://github.com/MihneaCucu/CloseCircle/pull/3), [#2](https://github.com/MihneaCucu/CloseCircle/pull/2), [#4](https://github.com/MihneaCucu/CloseCircle/pull/4) and [#5](https://github.com/MihneaCucu/CloseCircle/pull/5) were reviewed and merged into `dev` in that order on 15 November 2026. Read/upload, freshness, accuracy and recovery are now in the shared backend baseline. [PR #6](https://github.com/MihneaCucu/CloseCircle/pull/6) supplies the executable contract, same-checkout CI checks and coverage reports. Backend review approvals do not replace whole-story/client acceptance.

- SCRUM-19: authenticated member-location reads, latest recorded point, missing-point handling, pagination, group isolation, both sharing controls, and current membership checks are implemented. Malformed parameters/cursors and noncanonical token subjects return controlled errors.
- SCRUM-20: the response includes RECENT, LAST_KNOWN or UNKNOWN and the original `location.recordedAt` when a freshness window is configured. Repeated reads re-evaluate age without deleting/replacing the last point; receipt time cannot make an old recording recent.
- The upload regression verifies the connected backend flow using real HTTP uploads: old point -> LAST_KNOWN, new point -> RECENT, time passing -> LAST_KNOWN with the exact same point, resumed upload -> RECENT, sharing off -> hidden. Account, membership and consent prerequisites are synthetic fixtures; authentication issuance and group/sharing screens remain integration dependencies.

For a local backend demonstration, use `dev` after the contract integration PR #6 is merged and run from the repository root:

```sh
mvn -f backend/pom.xml clean verify
python3 backend/src/test/python/check_location_freshness.py
python3 backend/src/test/python/check_location_upload.py
```

Java 21+, Maven, Python 3 and PostgreSQL tools (`initdb`, `pg_ctl`, `createdb`, `psql`) are required. The checks launch disposable local databases and the actual API; they never connect to Neon. CI runs these checks too. Use `--closecircle.location.recent-for-seconds=300` or `CLOSECIRCLE_LOCATION_RECENTFORSECONDS=300` when starting the API for a manual demo. This is an explicit example window; the team must select the product value. Without a configured window, `freshness` is omitted. The five-second window in the automatic transition test is only a test setting.

Android presentation, map refresh and UI acceptance belong to the client owners. Environment configuration, remaining team agreement and integrated client acceptance remain before whole stories can be marked Done. No additional Android implementation is part of this backend handoff.

## Jira scope snapshot from 14 November 2026

The Jira query `project = SCRUM AND sprint = 1 AND issuetype = Story ORDER BY key ASC` returns **18 User Stories**, all in Backlog, To Do or In Progress; none is marked Done. If Sprint 1 is the team's committed implementation scope, the checkpoint minimum is **ceil(18 / 3) = 6 accepted implemented stories**. The Product Owner must confirm that this is the scope agreed with the instructor; a different committed scope changes the denominator.

SCRUM-28 and SCRUM-31 are completed infrastructure **Tasks**, while SCRUM-43 is an upload **subtask** of SCRUM-21. They support delivery but do not raise the User Story completion count. The Jira status snapshot alone does not prove that unfinished code does not exist elsewhere; it means the board currently provides no Done-story evidence for checkpoint acceptance.

| User Story | Backend evidence | Still needed for whole-story acceptance |
| --- | --- | --- |
| SCRUM-19 member locations | Authenticated, group-isolated latest-location read; upload/read flow added by SCRUM-43 | Real auth/group/sharing integration, Android view/refresh, another Developer's acceptance check |
| SCRUM-20 recent versus last-known | Configurable freshness in the read response, original recording time, HTTP/database tests | Agree the window and proposed fields; Android display and integrated acceptance |
| SCRUM-21 connection loss and recovery | Persisted last-known point across API/database restart; real upload/retry checks supplement the SQL-seeded recovery regression | Android offline cache/reconnection, queue/consent agreement and integrated acceptance |
| SCRUM-22 poor accuracy | Configurable accuracy metadata, unknown-value handling and HTTP/database tests | Agree the threshold and fields; Android marker and integrated acceptance |

The backend endpoint PRs are now merged into `dev`. Deployed availability must be verified separately. Test fixtures and synthetic JWTs are not proof of the real user registration or group/invitation flow.

## Focus to reach six useful stories

A proposed core checkpoint slice is **SCRUM-11, SCRUM-12, SCRUM-13, SCRUM-15, SCRUM-19 and SCRUM-20**: create a group, invite/accept a member, enable sharing, view an uploaded location, and distinguish its freshness. This gives six connected user outcomes rather than six isolated endpoints. The Product Owner and team must validate this choice against current priorities.

1. Finish and integrate SCRUM-41 authentication as the prerequisite technical task, along with the group/invitation work owned by its teammate. The location backend dependencies have been reviewed and merged; integrate against that shared baseline.
2. Integrate the sharing endpoint and its current-consent timestamp with upload. Do not modify the teammate's `jira/T04` branch as part of this work.
3. Connect the Android core screens to the real authenticated endpoints, using stable upload keys for retries. Demonstrate two real member accounts, group isolation, sharing off/on and current/old recordings.
4. Verify the selected stories against their acceptance criteria and Definition of Done with a different Developer, record the evidence and update Jira truthfully.
5. Keep SCRUM-21 recovery and SCRUM-22 accuracy as follow-on or alternative completed outcomes when their client acceptance is ready; do not count backend-only slices as Done stories.

Record the actual test cases, coverage measurements, AI-assisted changes and any genuine TDD/Pair Programming evidence. The agent's automated work does not replace team review, Planning Poker or Scrum activities. **Adding upload closes an important backend gap, but does not by itself establish Checkpoint 2 compliance.**
