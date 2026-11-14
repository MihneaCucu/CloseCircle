# Checkpoint 2 readiness

The confirmed target is the weeks 7-8 milestone: implement at least one-third of the User Stories committed for implementation. The course handout, *Proiect de laborator MOPS*, page 8, section II, defines this target; pages 1 and 4-6 describe acceptance checks, Definition of Done, tests and development-process evidence. Backend endpoints, technical tasks and subtasks are not automatically separate completed User Stories.

## Current evidence on 14 November 2026

The Jira query `project = SCRUM AND sprint = 1 AND issuetype = Story ORDER BY key ASC` returns **18 User Stories**, all in Backlog, To Do or In Progress; none is marked Done. If Sprint 1 is the team's committed implementation scope, the checkpoint minimum is **ceil(18 / 3) = 6 accepted implemented stories**. The Product Owner must confirm that this is the scope agreed with the instructor; a different committed scope changes the denominator.

SCRUM-28 and SCRUM-31 are completed infrastructure **Tasks**, while SCRUM-43 is an upload **subtask** of SCRUM-21. They support delivery but do not raise the User Story completion count. The Jira status snapshot alone does not prove that unfinished code does not exist elsewhere; it means the board currently provides no Done-story evidence for checkpoint acceptance.

| User Story | Backend evidence | Still needed for whole-story acceptance |
| --- | --- | --- |
| SCRUM-19 member locations | Authenticated, group-isolated latest-location read; upload/read flow added by SCRUM-43 | Real auth/group/sharing integration, Android view/refresh, another Developer's acceptance check |
| SCRUM-20 recent versus last-known | Configurable freshness in the read response, original recording time, HTTP/database tests | Agree the window and proposed fields; Android display and integrated acceptance |
| SCRUM-21 connection loss and recovery | Persisted last-known point across API/database restart; real upload/retry checks supplement the SQL-seeded recovery regression | Android offline cache/reconnection, queue/consent agreement and integrated acceptance |
| SCRUM-22 poor accuracy | Configurable accuracy metadata, unknown-value handling and HTTP/database tests | Agree the threshold and fields; Android marker and integrated acceptance |

The feature work is on dependent PRs; it is not merged into the shared `dev` baseline or deployed by this work. Test fixtures and synthetic JWTs are not proof of the real user registration or group/invitation flow.

## Focus to reach six useful stories

A proposed core checkpoint slice is **SCRUM-11, SCRUM-12, SCRUM-13, SCRUM-15, SCRUM-19 and SCRUM-20**: create a group, invite/accept a member, enable sharing, view an uploaded location, and distinguish its freshness. This gives six connected user outcomes rather than six isolated endpoints. The Product Owner and team must validate this choice against current priorities.

1. Finish and integrate SCRUM-41 authentication as the prerequisite technical task, along with the group/invitation work owned by its teammate. Review PR #1 and subsequent backend dependencies in order.
2. Integrate the sharing endpoint and its current-consent timestamp with upload. Do not modify the teammate's `jira/T04` branch as part of this work.
3. Connect the Android core screens to the real authenticated endpoints, using stable upload keys for retries. Demonstrate two real member accounts, group isolation, sharing off/on and current/old recordings.
4. Verify the selected stories against their acceptance criteria and Definition of Done with a different Developer, record the evidence and update Jira truthfully.
5. Keep SCRUM-21 recovery and SCRUM-22 accuracy as follow-on or alternative completed outcomes when their client acceptance is ready; do not count backend-only slices as Done stories.

Record the actual test cases, coverage measurements, AI-assisted changes and any genuine TDD/Pair Programming evidence. The agent's automated work does not replace team review, Planning Poker or Scrum activities. **Adding upload closes an important backend gap, but does not by itself establish Checkpoint 2 compliance.**
