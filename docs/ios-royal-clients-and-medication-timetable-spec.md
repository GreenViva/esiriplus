# iOS — Royal Clients calling + Medication Timetable

iOS implementation spec for two doctor-side features that already ship on
Android. When a question isn't answered here, mirror the Android build —
`feature/doctor/src/main/kotlin/com/esiri/esiriplus/feature/doctor/` is the
source of truth. Both features share the `videosdk-token` + push
notification plumbing that `docs/ios-pay-by-mobile-number-spec.md` and the
existing iOS video-call flow already exercise, so most connectivity is
already half-wired on iOS.

---

# Part A — Royal Clients calling

## A.1 What this feature does

A doctor with **Royal-tier** clients in an active follow-up window can open a
dedicated list of those patients and call them **directly** — no patient
request, no acceptance dance, no waiting. The patient's phone receives an
incoming-call push and they join the VideoSDK room on tap.

It's the doctor-initiated inverse of the normal patient-requests-doctor flow,
available only within Royal's 14-day follow-up window because that's the
commercial guarantee Royal patients paid for (premium access to their doctor).

## A.2 Where it lives in the app

From the doctor dashboard, a "Royal Clients" stat card routes to the list
screen. See Android reference:
- `feature/doctor/.../screen/DoctorDashboardScreen.kt:1413-1419` — the dashboard entry point.
- `feature/doctor/.../navigation/DoctorNavigation.kt` — `RoyalClientsRoute`.
- `feature/doctor/.../screen/RoyalClientsScreen.kt` — the list + call-options sheet.

## A.3 Data source

The list is filtered from the doctor's consultations:

```
serviceTier == "ROYAL"
AND (
  status IN ('active', 'awaiting_extension', 'grace_period')
  OR (status = 'completed' AND (followUpExpiry IS NULL OR followUpExpiry > now()))
)
ORDER BY completedAt DESC NULLS FIRST
LIMIT 200
```

Reference: `core/database/.../dao/ConsultationDao.kt:38-45` (Android pulls
this from the local Room cache, which is kept in sync via the standard
consultation subscription). On iOS, either:
- Read from the local cache iOS already maintains for consultations, and
  apply the same filter, OR
- Query PostgREST directly when simpler:
  `GET /rest/v1/consultations?doctor_id=eq.<me>&service_tier=eq.ROYAL&...`

Either is fine as long as the Royal + follow-up-window predicate is applied.

## A.4 List screen layout

Each row is a Royal-themed card. Mirror Android's `RoyalClientsScreen.kt:159-172`
and the row internals at `RoyalClientsScreen.kt:536-655`.

### Row shape

```
┌─────────────────────────────────────────┐
│ ★ ROYAL        Active · 3 days left   ▸ │  ← header strip, purple gradient
├─────────────────────────────────────────┤
│ ● ┌──┐  Nickname or patient code           │
│   │JP│  General consultation · 18 Apr      │
│   └──┘                                     │  ← body
│                                       📞  │  ← purple phone affordance
└─────────────────────────────────────────┘
```

### Elements

- **Header strip**: purple gradient background (`#7C3AED → #6D28D9`), Royal
  star + "ROYAL" label, and a status + expiry countdown.
  - Status labels: `Active`, `In Session`, `Completed`, `Grace Period`.
  - Expiry countdown format: `"N days left"` (ceil the difference in days).
- **Status dot** (left of avatar): teal `#2A9D8F` if status in `active`,
  green `#22C55E` if `completed` + follow-up still valid, amber `#F59E0B` if
  `awaiting_extension` or `grace_period`.
- **Avatar initials**: two uppercase letters from the doctor-set nickname or
  the patient code. Circle, 40pt, neutral grey background.
- **Nickname** (see A.5): optional, doctor-editable, overrides the patient
  session code when present.
- **Service type** sub-text: `General consultation`, `Specialist consultation`,
  etc. — same display names the patient Home uses.
- **Date**: the `completed_at` or `created_at` formatted as `18 Apr` (day +
  short-month) in the doctor's locale.
- **Phone affordance** on the right — a 36pt filled circle in brand purple
  with a phone-handset SF Symbol. Doesn't trigger a call by itself (tapping
  the row does); it's a visual cue that the row is callable.

### Empty state

SF Symbol `star.circle` at 64pt, tinted `#7C3AED`, with copy:
- Title: `"No Royal consultations yet"`
- Body: `"Royal clients whose 14-day follow-up window is still open will appear here."`

Android reference: `RoyalClientsScreen.kt:136-151`.

### Loading

Show a centred spinner on first load. Pull-to-refresh re-queries from the
backend. There is no pagination; the DAO query is capped at `LIMIT 200`
and Android treats that as the practical ceiling. If your doctor somehow
has >200 Royal clients in a rolling 14-day window, the top 200 by recency
is fine.

## A.5 Nickname (optional)

The doctor can tag each Royal client with a short free-text nickname so
they recognise the patient on sight. Android stores this client-side only
in `SharedPreferences` (key namespace `royal_client_nicknames`, one entry
per `consultation_id`).

On iOS:
- Store in `UserDefaults` under a sandboxed suite name
  `com.esiri.esiriplus.royal_client_nicknames`.
- Key = `consultation_id`. Value = the nickname string. Max 24 characters.
- Clear the entry if/when the consultation_id disappears from the list
  (follow-up expired).

No server round-trip. PHI lives on the device only. If the doctor switches
devices they lose nicknames — same behaviour as Android, acceptable.

Nickname editor: long-press the row → sheet with a single text field
("Nickname for this client") + Save / Clear / Cancel.

## A.6 Call-options bottom sheet

Tapping a row opens a bottom sheet with two options:

- **Voice Call** → starts an `AUDIO` VideoSDK call.
- **Video Call** → starts a `VIDEO` VideoSDK call.
- **Set Medication Reminder** → opens the medication timetable form (see
  Part B). This is why both features share one spec: the UX affordance to
  schedule a reminder lives on the Royal client's own row, so an iOS dev
  will build it at the same time.

Android reference: `RoyalClientsScreen.kt:178-206`, button at `484-510`.

### Sheet layout

```
┌─────────────────────────────┐
│ ━━━                         │  ← drag handle
│                             │
│ Dr Name — 16sp, semibold    │
│ Royal client                │
│                             │
│ ┌─────────┐ ┌─────────┐     │
│ │📞 Voice │ │📹 Video │     │  ← two primary call buttons
│ └─────────┘ └─────────┘     │
│                             │
│ ─────────────────────────   │
│                             │
│ [Set Medication Reminder]   │  ← secondary outline
│                             │
└─────────────────────────────┘
```

## A.7 Call initiation — connectivity

Doctor taps Voice or Video. Navigate to the existing iOS video call screen
(the one your stack already uses for patient-initiated consultations). Pass
`consultationId` and `callType` (`"AUDIO"` or `"VIDEO"`).

The call screen invokes the existing `videosdk-token` edge function:

```
POST {SUPABASE_FUNCTIONS_URL}/videosdk-token
Authorization: Bearer <doctor_jwt>
Content-Type: application/json

{
  "consultation_id": "<uuid>",
  "call_type": "AUDIO" | "VIDEO",
  "room_id": null      // omit on doctor's first call; the function creates a room
}
```

Reference: `feature/doctor/.../data/VideoRepositoryImpl.kt:25-49` +
`supabase/functions/videosdk-token/index.ts`.

Server-side sequence (already deployed, no changes needed):

1. Validates the consultation belongs to this doctor and is in a callable
   state: `status IN ('active', 'in_progress', 'awaiting_extension', 'grace_period')`
   OR (`service_tier='ROYAL'` AND `status='completed'` AND within 14-day
   follow-up window). If not, returns 403.
2. Creates a new VideoSDK room (first call only) and persists
   `consultations.video_room_id`. Subsequent calls re-use the same room ID.
3. Issues a JWT for the doctor with permissions
   `["allow_join", "allow_mod"]`.
4. Sends a push notification to the patient session:
   ```
   { type: "VIDEO_CALL_INCOMING",
     title: "Incoming Video Call" | "Incoming Voice Call",
     body:  "Your doctor is calling",
     data:  { consultation_id, room_id, call_type, caller_role: "doctor" } }
   ```
5. Returns `{ token, room_id }` to the doctor.

On iOS:

- Doctor's `videosdk-token` call: identical to the existing iOS flow.
  Nothing new.
- When the token + room_id come back, join the VideoSDK room immediately
  and wait for the patient.

## A.8 Patient-side incoming call

The patient receives `VIDEO_CALL_INCOMING` via APNs (for iOS patients) or
FCM (for Android patients). The notification payload carries
`consultation_id`, `room_id`, `call_type`.

For iOS patients, your notification handler must:

1. Display an incoming-call UI when the notification arrives. If the app is
   foregrounded, raise a custom SwiftUI call sheet. If backgrounded, use
   `UNUserNotificationCenter` with a category that surfaces Accept / Decline
   actions. If you support CallKit, prefer that — it gives lockscreen UX
   identical to a phone call.
2. On Accept → launch the existing video call screen with the received
   `consultation_id`, and request a **patient** token:
   ```
   POST /videosdk-token
   { "consultation_id": <uuid>, "call_type": <as received>, "room_id": <as received> }
   ```
   The server returns a patient token with `["allow_join"]` only — joining
   the same room the doctor already created.
3. On Decline → POST to a "decline" endpoint if one exists, or silently
   dismiss. Android currently doesn't have a decline plumbed separately;
   the room just stays empty and the doctor gives up. OK to match.

Android reference for notification handling: look under
`feature/patient/src/main/kotlin/...` for `MESSAGING_SERVICE` and
`VIDEO_CALL_INCOMING` type dispatch. Mirror its behaviour.

## A.9 Guards (enforced server-side)

These are already enforced by `videosdk-token`. The iOS client doesn't
re-check them — just surfaces any 403 response as a friendly error
("This client's follow-up window has expired").

- Consultation row must exist and `doctor_id = auth.uid()`.
- Tier must be Royal for out-of-session calls, OR the consultation must
  still be in-session.
- Rate limit: 30 tokens/minute per user.

---

# Part B — Medication Timetable

## B.1 What this feature does

The doctor creates a medication schedule for a patient — medication name,
N times a day, specific times, and duration in days. When each scheduled
time arrives, the server cron dispatches a **nurse** to call the patient and
remind them to take the medication, and sends the patient a supporting push
notification ("A nurse will call you shortly").

This is one of the Royal-tier value propositions: medication adherence
backed by human outreach, not just a silent reminder.

## B.2 Where it's triggered

From the Royal client call-options bottom sheet (A.6), the doctor taps
**Set Medication Reminder**. That opens a form dialog scoped to the
current consultation.

Android reference: `feature/doctor/.../screen/MedicationTimetableDialog.kt`.

(Also potentially triggered from the consultation detail screen. If the
iOS consultation detail has a matching affordance, expose it there too
with the same dialog.)

## B.3 Form

```
┌────────────────────────────────────────┐
│ Medication Schedule            ✕       │
│                                        │
│ Medication name                        │
│ ┌──────────────────────────────────┐   │
│ │ Amoxicillin 500 mg               │   │
│ └──────────────────────────────────┘   │
│                                        │
│ Times per day                          │
│ ( 1x )  ( 2x )  [ 3x ]  ( 4x )         │  ← chip group, 3x pre-selected
│                                        │
│ Scheduled times                        │
│ 08:00    14:00    20:00                │  ← N inline time fields
│                                        │
│ Duration                               │
│ ┌────┐  days                           │
│ │ 7  │                                 │
│ └────┘                                 │
│                                        │
│ [ Cancel ]       [ Save schedule ]     │
└────────────────────────────────────────┘
```

### Fields

| Field | Type | Default | Validation |
|---|---|---|---|
| `medicationName` | Text | — | Non-empty after trim |
| `timesPerDay` | Int (1/2/3/4) | `3` | One of `{1, 2, 3, 4}` |
| `scheduledTimes` | `[String]` | see below | Each matches `/^\d{2}:\d{2}$/` |
| `durationDays` | Int | `7` | `> 0` |

### Default times by `timesPerDay`

| `timesPerDay` | Default `scheduledTimes` |
|---|---|
| 1 | `["08:00"]` |
| 2 | `["08:00", "20:00"]` |
| 3 | `["08:00", "14:00", "20:00"]` |
| 4 | `["06:00", "12:00", "18:00", "22:00"]` |

When the chip changes, reset `scheduledTimes` to the new default — the
array length must always equal `timesPerDay`. Reference:
`MedicationTimetableDialog.kt:41-46`.

### Time field behaviour

Each time is a 24-hour `HH:MM` string. On iOS, present a native
`DatePicker(.hourAndMinute)` bound to a `Date` and format with
`DateFormatter` (`HH:mm`) when serialising. Validate on change — show an
inline error under the first invalid slot.

### Validation summary

Save button enabled iff:
- `medicationName.trimmingCharacters(in: .whitespaces).isEmpty == false`
- every slot in `scheduledTimes` matches `^\d{2}:\d{2}$`
- `durationDays > 0`

Matches the Android guard at `MedicationTimetableDialog.kt:175-176`.

## B.4 Payload — create schedule

```
POST {SUPABASE_FUNCTIONS_URL}/medication-reminder-callback
Authorization: Bearer <doctor_jwt>
Content-Type: application/json

{
  "action": "create_timetable",
  "consultation_id": "<uuid>",
  "patient_session_id": "<uuid>",
  "medication_name": "Amoxicillin 500 mg",
  "times_per_day": 3,
  "scheduled_times": ["08:00", "14:00", "20:00"],
  "duration_days": 7
}
```

Response (200):

```json
{ "ok": true, "timetable_id": "<uuid>" }
```

Swift DTOs:

```swift
struct CreateMedicationTimetableRequest: Encodable {
  let action: String             // "create_timetable"
  let consultationId: String
  let patientSessionId: String
  let medicationName: String
  let timesPerDay: Int
  let scheduledTimes: [String]   // ["08:00", "14:00", ...]
  let durationDays: Int

  enum CodingKeys: String, CodingKey {
    case action
    case consultationId = "consultation_id"
    case patientSessionId = "patient_session_id"
    case medicationName = "medication_name"
    case timesPerDay = "times_per_day"
    case scheduledTimes = "scheduled_times"
    case durationDays = "duration_days"
  }
}

struct CreateMedicationTimetableResponse: Decodable {
  let ok: Bool
  let timetableId: String?

  enum CodingKeys: String, CodingKey {
    case ok
    case timetableId = "timetable_id"
  }
}
```

## B.5 Server-side flow (background, no iOS work)

- `medication-reminder-callback` (action `create_timetable`) inserts a row
  into `medication_timetables` with `start_date = today`,
  `end_date = today + (duration_days - 1)`. Reference:
  `supabase/functions/medication-reminder-callback/index.ts:26-67`.
- A cron job (`medication-reminder-cron`) runs every minute, finds
  timetables whose `scheduled_times` include the current EAT time, and
  dispatches a **nurse call + patient notification** per entry.
- The nurse receives `MEDICATION_REMINDER_CALL` push with
  `{ event_id, room_id, patient_session_id, medication_name, dosage }`.
- The patient receives `MEDICATION_REMINDER_PATIENT` push with
  `"A nurse will call you shortly to remind you..."`.
- If no nurse is available, the cron retries up to 3 times, then sends a
  text-only fallback push. Stale nurse assignments (>2 min without
  answering) reassign to another nurse, max 2 reassignments.

None of that needs iOS work on the doctor side. The doctor only creates.

## B.6 Patient-side schedule view

Patients see their active schedules on a dedicated screen. Android:
`feature/patient/.../screen/MedicationScheduleScreen.kt`. Query pattern via
edge function action `get_schedules`:

```
POST /medication-reminder-callback
{ "action": "get_schedules" }
```

Returns active timetables for the authenticated patient session. The
patient-side iOS screen is out of scope for this spec (the doctor-side
work is the ask). If iOS also needs a patient-side medication schedule
screen, build it behind the same action — it's symmetric with the
doctor-side shape.

## B.7 Edit / cancel — deliberate gap

Android does **not** currently expose edit or cancel for a schedule.
Create-once only. If you build cancel on iOS, you'll be ahead of Android.

Recommendation: **don't** build edit/cancel on iOS yet — match Android
and flag it as a platform gap in the iOS PR. When the business asks
for it, build it on both platforms at the same time.

Notes for the eventual cancel flow when we do build it:
- Add a `deactivate_timetable` action to `medication-reminder-callback`
  that sets `medication_timetables.is_active = false`.
- Requires auth to be the doctor who created the schedule OR the patient
  themselves.
- Also exposes a cancel UI on the patient side.

## B.8 Success + failure UX

On successful create:
- Dismiss the dialog.
- Show a brief toast / banner: `"Medication schedule saved — reminders will start today."`

On failure (non-200 or thrown):
- Keep the dialog open.
- Show an inline error above the Save button:
  `"Couldn't save schedule. Try again."`
- The Save button re-enables on retry.

---

# Part C — Shared UI tokens

Royal feature purple is used across both flows, so define it once:

| Token | Hex | Usage |
|---|---|---|
| `royalPurple` | `#7C3AED` | Primary purple |
| `royalPurpleDeep` | `#6D28D9` | Gradient end, pressed-state |
| `royalStar` | `#FBBF24` | Star glyph in Royal badge |
| `royalCardBg` | gradient `#7C3AED → #6D28D9` | Row header strip |

Other tokens (teal brand, amber info pill, black text) follow the project
conventions — body text is always black, never grey.

---

# Part D — Localization

Strings to key, matching Android's `strings.xml` (exact key parity when
Android adds them in `feature/doctor/src/main/res/values/strings.xml`).

| Key | English |
|---|---|
| `royal_clients_title` | Royal Clients |
| `royal_clients_empty_title` | No Royal consultations yet |
| `royal_clients_empty_body` | Royal clients whose 14-day follow-up window is still open will appear here. |
| `royal_clients_status_active` | Active |
| `royal_clients_status_in_session` | In Session |
| `royal_clients_status_completed` | Completed |
| `royal_clients_status_grace` | Grace Period |
| `royal_clients_days_left` | %d days left |
| `royal_clients_days_left_one` | %d day left |
| `royal_clients_call_voice` | Voice Call |
| `royal_clients_call_video` | Video Call |
| `royal_clients_set_reminder` | Set Medication Reminder |
| `royal_clients_nickname_hint` | Nickname for this client |
| `royal_clients_nickname_clear` | Clear nickname |
| `med_schedule_title` | Medication Schedule |
| `med_schedule_name_label` | Medication name |
| `med_schedule_times_per_day` | Times per day |
| `med_schedule_scheduled_times` | Scheduled times |
| `med_schedule_duration` | Duration |
| `med_schedule_duration_days` | days |
| `med_schedule_save` | Save schedule |
| `med_schedule_cancel` | Cancel |
| `med_schedule_saved_toast` | Medication schedule saved — reminders will start today. |
| `med_schedule_save_failed` | Couldn't save schedule. Try again. |

Translate into `sw`/`fr`/`es`/`ar`/`hi` before release. Mark with
`TODO(i18n)` until then.

---

# Part E — Acceptance criteria

Cross-check each against Android; if iOS behaves the same on every item,
the feature is done.

## Royal Clients

1. **Entry point**: A Royal Clients card/stat on the doctor dashboard
   routes to the list screen.
2. **Filter**: Only consultations with `service_tier = "ROYAL"` and a
   valid follow-up window (or in an active-session status) appear.
3. **Header strip**: Purple gradient with Royal star, status, and
   "N days left" countdown visible on every row.
4. **Nickname persistence**: A nickname set on one consultation survives
   app restart, but disappears when the consultation leaves the list
   (follow-up expired).
5. **Row tap → call sheet**: Bottom sheet shows Voice / Video / Set
   Medication Reminder.
6. **Voice + Video calls**: Tapping either starts a VideoSDK room via
   `videosdk-token` and launches the existing call screen.
7. **Patient notification**: Within ~2s of the doctor starting a call,
   the patient's device receives `VIDEO_CALL_INCOMING` with
   `consultation_id`, `room_id`, `call_type`.
8. **Guard — expired follow-up**: If the follow-up window expires between
   listing and call, the server returns 403; the iOS client surfaces a
   user-friendly error and doesn't retry.
9. **Empty state**: When the doctor has zero Royal clients in the active
   window, the empty illustration + copy from §A.4 renders.

## Medication Timetable

10. **Form validation**: Save is disabled until all four fields pass.
11. **Times reset on chip change**: Changing `timesPerDay` resets
    `scheduledTimes` to the matching default array.
12. **Payload parity**: The JSON sent to
    `medication-reminder-callback` is byte-identical to Android's (same
    keys, same order of scheduled times, 24-hour `HH:MM` strings).
13. **Success UX**: On 200 response with `ok: true`, dialog dismisses
    and success toast appears.
14. **Failure UX**: On non-200 or thrown, dialog stays open with inline
    error above Save.
15. **Server effect**: A row appears in `medication_timetables` with
    `start_date = today`, `end_date = today + duration_days - 1`,
    `is_active = true`, `doctor_id = <me>`.
16. **Reminder dispatch (observable, not iOS work)**: At the first
    scheduled time, the patient receives a `MEDICATION_REMINDER_PATIENT`
    push and a nurse is assigned. Verify in staging before release.

---

# Part F — Out of scope

- **Doctor-side schedule edit / cancel** — Android doesn't ship this;
  iOS shouldn't either. See §B.7.
- **Patient-side medication screen** — not part of the doctor-side
  ask. Build separately when business asks for parity.
- **Call recording / transcription** — not part of the VideoSDK
  integration we have.
- **Group calls** — Royal calls are 1-on-1 only. No group/family-member
  participation.
- **Offline call queueing** — if the patient is offline when the doctor
  calls, the push is queued by FCM/APNs per their standard delivery,
  and the doctor just waits. No custom offline handling.
- **Schedule conflicts** — the client doesn't check if two medications
  are scheduled at the same minute (overlapping doses can happen). If
  that becomes a problem, add dedup on the server, not the client.
- **Push token registration** — assumed to already be wired for iOS
  doctors (APNs token stored in `fcm_tokens.apns_token` per migration
  `20260422110000`). If not, that's a prereq before this feature works.
