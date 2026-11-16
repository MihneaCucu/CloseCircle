# Notification system analysis (T-18)

Task: compare delivery options for zone arrival/departure notifications (delivery mechanism, latency, delayed updates, duplicates), record a recommendation, and say what T-13 (arrival/departure notification) and T-16 (delayed notifications) take from it.

Status: analysis for team review. It changes no code. Numbers marked *not measured* have not been measured in this project; the plan for measuring them is in section 8.

## 1. Inputs and constraints

Requirements this analysis has to satisfy:

| ID | Requirement |
| --- | --- |
| CF-21 | Members choose which people and zones notify them of arrivals and departures. |
| CF-22 | Entry or exit is confirmed only after 2 consecutive updates or 2 minutes (anti-oscillation). |
| CF-23 | A notification is sent only if the person shares actively with the group when the event happens. |
| CF-24 | A notification built from an update delayed by more than 15 minutes is marked "delayed" and shows the real event time. |
| CF-25 | When sharing is revoked, notifications not yet sent for that person are cancelled. |
| CNF-04 | A valid update is visible to members within 10 s. |
| CNF-05 | Revocation applies within 5 s. |
| SE-02 | A delayed update that arrives after a newer one is ignored for display; it may only feed zone-event history. |
| SE-03, SE-04, SE-08 | Queued arrival cancelled on revocation; no notifications after temporary sharing expires; no repeated notifications at the zone border. |

What the repository already has (state of `dev`, 16 November 2026):

- `GET /notifications` and `PATCH /notifications/{id}` are **proposed** in [api-contract.md](api-contract.md#notifications), not implemented. The contract says push delivery, retry and live transport are outside the REST contract.
- The `notifications` table exists (`notification_id`, `group_id`, `recipient_user_id`, `notification_type`, `payload` JSONB, `created_at`, `read_at`). It has no delivery status and no uniqueness rule.
- Locations carry `recordedAt` (device time) and `receivedAt` (server time), and uploads are accepted only while the user shares ([location-upload.md](location-upload.md)). Sharing can expire without a background job ([group-sharing.md](group-sharing.md)).
- Infrastructure ([deployment.md](deployment.md)): the API runs on Render Free, which can suspend an idle service, and Neon can suspend an idle compute. There is no background worker and no message broker in the current deployment, so timely delivery is **not guaranteed**.
- The Android app is a single-device client (Kotlin, Compose). It does not use Firebase today.

Consequence: whatever is chosen must keep working when the API is asleep, when the phone is offline, and when no process is running between requests.

## 2. Three separate things

The word "notification" covers three different mechanisms. The contract should keep them apart:

| Concept | What it is | Source of truth? |
| --- | --- | --- |
| **Stored notification** | A row in `notifications`, read through REST. | **Yes.** |
| **Live update** | A message to an app that is open, telling it to refresh. | No. A hint. |
| **Push** | A message to an app that is closed or in the background. | No. A hint. |

Live updates and push only say "something new exists". The content always comes from the REST resource. This makes duplicates, reordering and lost messages harmless: the client re-reads the list.

## 3. Options compared

| Option | How it works | Latency | Fits Render Free + Neon | Battery / data | Complexity | Main risk |
| --- | --- | --- | --- | --- | --- | --- |
| **A. Polling** | App calls `GET /notifications` every N seconds while open and when it resumes. | Up to N seconds. *Not measured.* | Good: plain requests, nothing runs between them. Each poll can hit a cold start. | Costs battery and data in proportion to 1/N. | Lowest. The route is already in the contract. | Slow while the app is closed. Android limits background work to about every 15 minutes (WorkManager). |
| **B. SSE or WebSocket** | App keeps a connection open; server sends events. | Low while connected. | Poor: needs a process that stays alive and remembers who is connected. A suspended or restarted instance drops every connection. | Moderate while open; nothing when closed. | Medium: reconnect, `Last-Event-ID`, fan-out across instances. | Silent loss when the instance sleeps. Does nothing for a closed app. |
| **C. FCM push with content** | Server sends a notification message with names and text through Firebase. | Usually seconds. *Not measured.* | Needs a server-side sender, but no connection to keep. | Best for a closed app. | Medium: Firebase project, device tokens, platform account. | Names and zone names leave our system and go through Google. Delivery is best-effort. Doze can delay low-priority messages. |
| **D. FCM data push as a hint** | Server sends only `{type, notificationId, groupId}`. App wakes, fetches `GET /notifications`. | Same as C plus one request. | Same as C. | Same as C. | Medium. | One extra request after the wake-up. Needs the API to be awake at that moment. |
| **E. Hybrid (recommended)** | Store first. Poll while open. Add D for a closed app. Add B only if measurements require it. | See phases below. | Each phase works without an always-on process. | Low. | Grows in steps. | Phase 1 alone is slow for a closed app, which is accepted for the demo. |

Why not C alone: CF-24 wants the real event time and CF-25 wants cancellation. A notification already shown on the phone cannot be taken back, and its text is stored outside our control. Sending content through a third party also conflicts with the privacy rule that notification data contains no coordinates and as little personal data as possible.

Why not B first: Render Free can suspend the service, and a sleeping instance cannot hold connections. The effort buys little until there is an always-on process.

## 4. Latency

CNF-04 asks for 10 s from a valid update to visibility. For notifications the path is: device upload, server processing (zone check, confirmation under CF-22), insert into `notifications`, delivery to the recipient.

- CF-22 already adds up to 2 minutes of **intentional** delay before an entry or exit is confirmed. A notification is therefore never "instant", and 10 s applies after confirmation.
- Polling adds up to the poll interval (for example 30 s in the foreground).
- A cold start of a suspended Render Free service adds an unknown wait. *Not measured.* It is the main reason not to promise a number for the demo environment.
- FCM adds a delivery time that Google does not guarantee.

Proposed target for the demo, to be checked in T-19/T-22: confirmed event to visible stored notification under 10 s when the API is awake; recipients with the app open see it within one poll interval. Closed-app delivery is best-effort until phase 2.

## 5. Delayed updates and ordering

- **Event time.** Zone detection uses `recordedAt`, never `receivedAt`. A notification stores both the time of the event (`eventAt`) and the time it was created (`createdAt`).
- **Delayed flag.** `delayed = createdAt - eventAt > 15 minutes` (CF-24). The app shows "ÎNTÂRZIAT" and the real time, for example "Primită 08:10, eveniment real 07:40". The 15-minute value is a named constant on the server, not a client rule.
- **Older than the latest point (SE-02).** An update older than the newest stored point is not shown as the member's position. It may still produce a zone event only if the per-person-per-zone state allows it (next point). It must never move the position back.
- **Zone state.** Keep, per person and zone, the last confirmed state (inside or outside) and the candidate state with its count and first time. Events are produced only when the candidate is confirmed (2 updates or 2 minutes) and differs from the confirmed state. This is what stops border oscillation (SE-08) and duplicate events.
- **Poor accuracy.** Updates with accuracy worse than 500 m are stored but never advance the state machine (CF-16, SE-05).
- **Consent at event time (CF-23, SE-04).** The event is evaluated against the sharing state at `eventAt`. After revocation or expiry no event is created for that person.

## 6. Duplicates

Three layers, cheapest first:

1. **Server, at creation.** A unique key on `(recipient_user_id, dedupe_key)`, where `dedupe_key` is derived from person, zone, transition and the confirmed-state version (for example `ZONE_EXIT:{zoneId}:{memberId}:{n}`). Inserting twice is a no-op. Processing the same upload twice (a retry) gives the same key.
2. **Push.** Use the notification id as the collapse key and keep the data payload tiny, so two pushes for one notification show as one wake-up.
3. **Client.** Notifications are keyed by `notificationId`; the list is a set. Showing the Android system notification uses the id as the tag so a repeat replaces, not stacks.

Success criterion from the milestone: zero false or duplicate notifications in the oscillation scenarios (SE-08).

## 7. Reconnection and cancellation

- **Reconnect.** The client stores the newest `notificationId`/cursor it has seen and asks for everything newer on app open, on resume and after a push. The contract already has a cursor on this route. A missed push or live event therefore loses nothing.
- **Cancellation (CF-25, SE-03).** Notifications are created only when an event is confirmed, so a notification that exists was already valid. What can still be pending is delivery: with push, add an outbox of undelivered messages and delete the entries of a user when that user's sharing is revoked. A notification already stored stays; whether it is hidden after revocation is a product decision (open question 2).
- **Expiry of temporary sharing (SE-04).** Readers already apply "active only while `expires_at` is null or in the future". The zone worker must apply the same rule to the event time.

## 8. Recommendation

Adopt **E, in three phases**, all behind the same stored-notification resource:

| Phase | Adds | Needs |
| --- | --- | --- |
| 1. REST only | Create notifications when a zone event is confirmed. `GET /notifications` and `PATCH /notifications/{id}`. Android polls every 30 s while open and on resume. | Zone processing in the API (T-12/T-13). No new infrastructure. |
| 2. Push hint | Device registration and an FCM **data-only** message after insert. | A Firebase project, a server-side sender, a token table. |
| 3. Live stream (only if phase 1+2 miss the target) | `GET /notifications/stream` (SSE), event id = notification id. | An always-on process; revisit when the deployment is not Render Free. |

Measurement plan (for T-19 and T-22), using the simulator with fictional data: time from the upload of the confirming point to the stored row; time from the row to the Android list in each phase; behaviour after the API has been idle long enough to be suspended; and the oscillation, delayed and revocation scenarios SE-02, SE-03, SE-04 and SE-08 with the expected result "no duplicate, no false event, one delayed mark".

## 9. Input for T-13 and T-16

### Notification resource (extends the proposal in api-contract.md)

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
    "zoneName": "Home",
    "eventAt": "2026-10-25T10:10:00Z",
    "delayed": true
  }
}
```

- Types: `ZONE_ENTRY`, `ZONE_EXIT`. `data` has no coordinates.
- `eventAt` and `delayed` are new. `delayed` is computed by the server from the 15-minute rule; the client may show the minutes as `createdAt - eventAt`.
- Retention of notifications is still an open decision.

### T-13 (arrival/departure) needs

- Zone state machine and confirmation rule from section 5, one row of state per person and zone.
- Insert with the dedupe key from section 6; no insert when the person is not sharing at `eventAt`.
- `GET /notifications` with `unreadOnly`, `limit`, `cursor`, and `PATCH` to mark read.
- Per-member and per-zone preferences (CF-21) applied when choosing recipients.

### T-16 (delayed notifications) needs

- `eventAt` taken from `recordedAt`, `delayed` from the 15-minute constant.
- Late updates that arrive after a newer point: no position change; events only through the zone state rules.
- The Android "ÎNTÂRZIAT" mark and real time, driven only by `delayed` and `eventAt`.

### Proposed routes for later phases (not part of the current contract)

| Route | Phase | Purpose |
| --- | --- | --- |
| `PUT /users/me/push-token` with `{ "token": "...", "platform": "ANDROID" }` | 2 | Register or replace the device token; one device per user (milestone assumption). |
| `DELETE /users/me/push-token` | 2 | Remove it on sign-out. |
| `GET /notifications/stream` (`text/event-stream`) | 3 | Live hint; `id:` is the notification id; reconnect with `Last-Event-ID`. |

Push message (phase 2), data-only:

```json
{ "type": "notification", "notificationId": "5eb9014d-a8b4-410b-82cb-5ec2047d8b74", "groupId": "2c76b551-b127-49b7-bfe7-9606f1e35da2" }
```

The message carries no names, zone names or coordinates. The app fetches the resource, then builds the system notification locally.

## 10. Open questions for the team

1. Is a closed-app delay acceptable for the demo (phase 1 only), or must push be in the first version?
2. After sharing is revoked, are already stored notifications about that person hidden from other members?
3. Retention period for notifications, and whether read notifications are deleted.
4. Who owns the Firebase project, and are the Google terms acceptable for family location data?
5. Quiet hours ("Nu deranja") exist only as an Android client idea; should the server know about them?

## 11. Acceptance criteria

| Criterion | Where |
| --- | --- |
| Options compared (pros and cons) | Section 3 |
| Final recommendation documented | Section 8 |
| Conclusions usable as input for T-13 and T-16 | Section 9 |
| Alignment with the REST contract (SCRUM-42): separate REST, live and push; registration and read endpoints; message schema; deduplication; reconnection; cold start and processing limits from T-05 / SCRUM-28 | Sections 2, 4, 6, 7, 9 and the constraints in section 1 |
