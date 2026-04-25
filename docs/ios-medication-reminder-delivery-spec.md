# iOS — Medication reminder delivery (nurse + patient)

iOS implementation spec for the **time-of-firing** side of the medication
reminder feature: when a scheduled dose time arrives, the system finds an
online nurse, has the nurse call the patient on a fresh VideoSDK room, and
falls back to a direct push if no nurse is available.

The **doctor-side creation** (the dialog where the doctor sets up the
schedule) is spec'd separately in
`docs/ios-royal-clients-and-medication-timetable-spec.md` Part B.
This spec picks up where that one ends — at the moment the cron fires.

The flow is **server-driven** by `supabase/functions/medication-reminder-cron`.
iOS work is purely on the receiving end: handle the inbound pushes
correctly on both nurse and patient devices.

---

## 1. What this feature does

Royal-tier patients have medications scheduled by their doctor. At each
scheduled time, the eSIRI+ system tries to deliver an in-person reminder
**by phone call from a nurse**, not just a notification. The chain is:

```
                 ┌────────────────────┐
[scheduled time] │ medication-reminder│
       ───────► │      -cron         │ (every minute)
                 └─────────┬──────────┘
                           │
              ┌────────────┴───────────┐
              │ Find any online nurse  │
              │ (specialty='nurse',    │
              │  is_available=true,    │
              │  in_session=false)     │
              └────────────┬───────────┘
                           │
            ┌──────────────┴──────────────┐
            │                             │
       NURSE FOUND                  NO NURSE (after 3 retries)
            │                             │
   ┌────────┴────────┐           ┌────────┴────────┐
   │ Create VideoSDK │           │ Send fallback   │
   │ room            │           │ text push to    │
   │ Push to nurse   │           │ patient:        │
   │ Push to patient │           │ "We couldn't    │
   │ ("nurse will    │           │  reach a nurse  │
   │  call you...")  │           │  — please       │
   └────────┬────────┘           │  remember to    │
            │                    │  take it now."  │
            │                    └─────────────────┘
   [nurse joins room,
    patient sees incoming
    call, conversation
    happens, nurse marks
    completed or
    patient_unreachable]
```

Two pushes for the happy path, one fallback push for the sad path. The
client just needs to render each correctly.

## 2. State at a glance

| Layer | Status | Reference |
|---|---|---|
| DB — `medication_timetables` | **Done** | migration `20260411200000_medication_reminder_tables.sql:13-29` |
| DB — `medication_reminder_events` | **Done** | same migration, lines 46-65. Status enum: `pending`, `nurse_notified`, `nurse_calling`, `completed`, `no_nurse`, `patient_unreachable`, `failed` |
| Backend — cron + nurse assignment | **Done** | `supabase/functions/medication-reminder-cron/index.ts` |
| Backend — nurse outcome callback | **Done** | `supabase/functions/medication-reminder-callback/index.ts` actions `completed`, `patient_unreachable` |
| Backend — VideoSDK token | **Done, shared** | `supabase/functions/videosdk-token/index.ts` (same endpoint as normal consultations) |
| Android — patient push handler | **Half-done** | Generic notification, no specialised banner — see §6 gap |
| Android — nurse push handler | **Done (reuses doctor incoming-call UI)** | `app/.../EsiriplusFirebaseMessagingService.kt:118-147` |
| **iOS — both sides** | **This spec** | |

## 3. Data model — events table

Each scheduled dose is a row in `medication_reminder_events`. iOS doesn't
write to this table directly (RLS would deny it; it's service-role only),
but it does observe the `event_id` carried in pushes so the nurse can
report outcome via `medication-reminder-callback`.

```sql
medication_reminder_events:
  event_id           UUID PK
  timetable_id       UUID FK
  scheduled_date     DATE
  scheduled_time     TEXT     -- 'HH:MM' EAT
  status             TEXT     -- enum, see below
  nurse_id           UUID
  video_room_id      TEXT
  nurse_notified_at  TIMESTAMPTZ
  call_started_at    TIMESTAMPTZ
  call_ended_at      TIMESTAMPTZ
  retry_count        INT      -- nurse-search retries (max 3)
  reassign_count     INT      -- nurse-reassign count (max 2)
  patient_notified   BOOLEAN
  UNIQUE (timetable_id, scheduled_date, scheduled_time)
```

Status transitions iOS will observe via push:

| Status | Cause | Push fired |
|---|---|---|
| `pending` | Initial insert by cron | (none) |
| `nurse_notified` | Cron found a nurse, room created | Nurse push + Patient heads-up push |
| `nurse_calling` | Nurse joined the room | (handled by VideoSDK / videosdk-token push) |
| `completed` | Nurse marked the call successful | (none — this is the success terminal) |
| `patient_unreachable` | Nurse marked the call as no-answer | (none — terminal, may trigger a separate non-cron push) |
| `no_nurse` | Cron couldn't find a nurse (still retrying ≤ 3) | (no push yet, retrying) |
| `failed` | After 3 nurse-search retries | **Fallback patient push** |

Reassignment loop: an event sitting in `nurse_notified` for >2 minutes
without a `nurse_calling` transition is reset to `pending` with the
prior `nurse_id` excluded; max 2 reassignments before giving up. iOS
nurses will simply observe a new `MEDICATION_REMINDER_CALL` push if
they're picked for a reassignment — no special handling needed.

## 4. Push payloads — exact shapes iOS must accept

### 4.1 Nurse path — `MEDICATION_REMINDER_CALL`

```json
{
  "type": "MEDICATION_REMINDER_CALL",
  "title": "Medication Reminder Call",
  "body": "Please call patient for {medication_name}",
  "data": {
    "event_id": "<uuid>",
    "room_id": "<videosdk-room-id>",
    "patient_session_id": "<uuid>",
    "medication_name": "Amoxicillin 500mg caps",
    "dosage": "Take 1 tablet × 3 times per day × 7 days"
  }
}
```

iOS nurse app must **immediately** treat this as an incoming call —
same lockscreen-grade UX as a normal consultation request, NOT a
tap-through notification. See §5 for the rendering contract.

### 4.2 Patient heads-up — `MEDICATION_REMINDER_PATIENT`

```json
{
  "type": "MEDICATION_REMINDER_PATIENT",
  "title": "Medication Reminder",
  "body": "A nurse will call you shortly to remind you to take {medication_name}.",
  "data": {
    "event_id": "<uuid>",
    "medication_name": "Amoxicillin 500mg caps",
    "dosage": "Take 1 tablet × 3 times per day × 7 days",
    "fallback": false
  }
}
```

This lands moments after the nurse push. The patient sees a heads-up
notification telling them what's about to happen so the incoming call
doesn't surprise them. See §6.

### 4.3 Patient fallback — also `MEDICATION_REMINDER_PATIENT` but `fallback: true`

```json
{
  "type": "MEDICATION_REMINDER_PATIENT",
  "title": "Medication Reminder",
  "body": "We couldn't reach a nurse for your {medication_name} reminder. Please remember to take it now.",
  "data": {
    "event_id": "<uuid>",
    "medication_name": "Amoxicillin 500mg caps",
    "dosage": "...",
    "fallback": true
  }
}
```

Same `type` as the heads-up, but the `data.fallback` boolean is
`true`. Use that flag to render a different visual (warning amber
instead of brand teal). The patient knows there's no follow-up call
coming and they're on their own for this dose.

### 4.4 Real incoming-call push (later in the flow)

When the nurse actually joins the VideoSDK room, the existing
`videosdk-token` function fires a `VIDEO_CALL_INCOMING` push to the
patient — same payload your iOS app already handles for normal
consultations. The patient surface for that push is shared, not new.

## 5. Nurse-side iOS

### 5.1 Who is a nurse?

A nurse is a doctor profile with `specialty = "nurse"`. They use the
**same iOS doctor app** as GPs and specialists — there's no separate
nurse app or nurse dashboard. They appear in the doctor pool with
`specialty='nurse'` and they receive the doctor-side dashboards,
notifications, and call screens.

The medication reminder is the only push type that targets nurses
exclusively.

### 5.2 Receiving `MEDICATION_REMINDER_CALL`

When the push arrives:

1. **If app is foregrounded** — render an in-app full-screen incoming
   call sheet (the same one used for `VIDEO_CALL_INCOMING` from normal
   consultations) with:
   - Title: `Medication Reminder Call`
   - Subtitle: `For: {medication_name}`
   - Body: `{dosage}`
   - Actions: **Accept** / **Decline**.

2. **If app is backgrounded or device is locked** — use **CallKit** if
   you support it (recommended for parity with the normal incoming
   call). Otherwise use `UNUserNotificationCenter` with a custom
   category that has Accept / Decline actions visible on the lockscreen.

The Android equivalent (`EsiriplusFirebaseMessagingService.kt:118-147`)
calls `IncomingCallService.start()` with `callerRole="medication_reminder"`,
extracts `event_id`, `room_id`, `medication_name` from the data payload,
and reuses the consultation incoming-call UI verbatim. iOS should mirror
that — the user-facing flow is *"a call is coming in"*, just labelled
differently.

### 5.3 On Accept

The nurse needs a VideoSDK token to join `room_id`. Call the existing
`videosdk-token` endpoint with the room_id pre-supplied:

```
POST {SUPABASE_FUNCTIONS_URL}/videosdk-token
Authorization: Bearer <nurse_jwt>

{ "consultation_id": null,
  "room_id": "<from push>",
  "call_type": "AUDIO" }
```

Server returns a token with permissions `["allow_join"]`. Note:
medication-reminder calls are **audio-only**. The nurse's screen is
the existing audio-call screen (no video), which iOS should already
have for consultations.

While the call is active, the server transitions
`medication_reminder_events.status` from `nurse_notified` →
`nurse_calling` automatically. iOS does not write that.

### 5.4 On call end — outcome reporting

The nurse must mark each call's outcome via
`medication-reminder-callback`. Two outcomes only:

```
POST {SUPABASE_FUNCTIONS_URL}/medication-reminder-callback
Authorization: Bearer <nurse_jwt>

{ "action": "completed",
  "event_id": "<uuid>" }
```

OR, if the patient didn't pick up after a reasonable wait:

```
POST {SUPABASE_FUNCTIONS_URL}/medication-reminder-callback
Authorization: Bearer <nurse_jwt>

{ "action": "patient_unreachable",
  "event_id": "<uuid>" }
```

Server-side effects:
- `completed` → status set, **nurse credited TZS 1,000** for the call.
  Reference: `medication-reminder-callback/index.ts:119-142`.
- `patient_unreachable` → server fires a follow-up patient push
  ("Nurse couldn't reach you — please remember to take {medication}").
  No payment to nurse. Reference: same file, lines 144-167.

iOS UI: a 2-button bottom sheet shown the moment the audio call ends:

```
┌──────────────────────────────────────┐
│ How did the call go?                 │
│                                      │
│ ┌─────────────────────────────┐      │
│ │ Patient confirmed              │   │  → action: completed
│ └─────────────────────────────┘      │
│                                      │
│ ┌─────────────────────────────┐      │
│ │ Patient did not answer         │   │  → action: patient_unreachable
│ └─────────────────────────────┘      │
└──────────────────────────────────────┘
```

The sheet is **mandatory** — don't let the nurse dismiss without
choosing. They get paid only on `completed`, so they have a strong
incentive to mark accurately.

### 5.5 On Decline

If the nurse declines the call entirely (e.g., they're now genuinely
busy), iOS should NOT call `medication-reminder-callback`. The server's
2-minute reassignment timer will pick up the abandoned event and try
another nurse. Just dismiss the incoming-call UI and don't post
anything to the server — abandoning is a valid signal.

(There's no explicit "decline" action on the callback today. Adding one
would be a backend change; not in scope for this spec.)

## 6. Patient-side iOS

### 6.1 Receiving `MEDICATION_REMINDER_PATIENT` — heads-up flavour

Push payload `data.fallback == false`. The patient sees a notification
*"A nurse will call you shortly..."* and possibly a custom banner if
the app is foregrounded.

**Foreground UX**: a non-blocking banner at the top of the patient
home, amber/teal-tinted depending on remaining time. Tap dismisses.
Auto-dismiss after 30 seconds.

```
┌─────────────────────────────────────────┐
│ 💊 Medication Reminder                  │
│ A nurse will call you shortly to remind │
│ you to take Amoxicillin 500mg caps.     │
│                              [Dismiss]  │
└─────────────────────────────────────────┘
```

**Background UX**: standard `UNNotification` with the same body. No
custom action buttons.

### 6.2 The actual incoming call (later)

A few seconds after the heads-up, the nurse joins the VideoSDK room
and the server fires `VIDEO_CALL_INCOMING` to the patient. **Reuse
the existing iOS incoming-call sheet for normal consultations** —
this isn't a special UI, it's the same incoming call. The only
difference: `data.caller_role == "nurse"` (or `"medication_reminder"`,
match Android — `EsiriplusFirebaseMessagingService.kt:126-132`),
which iOS can use to show the call title as
`Medication Reminder · {medication_name}` instead of the doctor's
name + photo.

If the patient declines, the call simply doesn't connect; the nurse
will mark `patient_unreachable` after waiting and the patient will
get the unreachable follow-up push.

### 6.3 Receiving the fallback — `data.fallback == true`

When no nurse was found after 3 retries, the heads-up push is
replaced with a fallback push. Same `type`, same data shape, but
`data.fallback == true`.

iOS rendering must be **visually distinct** so the patient knows
no call is coming and they're on their own for this dose:

- **Banner colour**: warning amber (`#FEF3C7` bg, `#92400E` text)
  instead of teal.
- **Icon**: `exclamationmark.circle.fill` instead of `pills.fill`.
- **Body** (verbatim from the server):
  *"We couldn't reach a nurse for your {medication_name} reminder.
   Please remember to take it now."*
- **No expectation of an incoming call** — auto-dismiss after 60 seconds
  (longer than the heads-up because there's no follow-up call to wait for).

```
┌─────────────────────────────────────────┐
│ ⚠️  Medication Reminder                  │
│ We couldn't reach a nurse for your      │
│ Amoxicillin 500mg caps reminder.        │
│ Please remember to take it now.         │
│                              [Dismiss]  │
└─────────────────────────────────────────┘
```

This is the **one thing iOS must NOT inherit from Android**: today the
Android patient handler renders both heads-up and fallback as identical
notifications — there's no visual distinction. The fallback being clearly
different is a strict UX improvement on iOS that doesn't change any
backend contract; eventually Android should also adopt it.

### 6.4 Schedule view (read-only)

If the patient opens the dedicated medication schedule screen on iOS
(equivalent to `MedicationScheduleScreen.kt`), it's a list of active
timetables fetched from the server via:

```
POST {SUPABASE_FUNCTIONS_URL}/medication-reminder-callback
Authorization: Bearer <patient_jwt>

{ "action": "get_schedules" }
```

Returns the patient's active timetables (where `is_active = true`).
Render: medication name, scheduled times as chips, date range, and a
status pill (active green / ended grey).

There is **no edit, no cancel, no add-from-patient-side**. Every
schedule originates from the doctor; the patient is read-only. Mirror
Android's behaviour at `feature/patient/.../screen/MedicationScheduleScreen.kt`.

## 7. Connectivity summary

| Endpoint | Caller | When | Purpose |
|---|---|---|---|
| (none — push subscribe) | iOS nurse | App start, after login | Receive `MEDICATION_REMINDER_CALL` pushes |
| (none — push subscribe) | iOS patient | App start, after login | Receive `MEDICATION_REMINDER_PATIENT` + `VIDEO_CALL_INCOMING` |
| `videosdk-token` | iOS nurse | On Accept of incoming reminder call | Token to join `room_id` |
| `videosdk-token` | iOS patient | On Accept of incoming call | Token to join the same room |
| `medication-reminder-callback` action `completed` | iOS nurse | Call ended successfully | Mark event done, credit nurse |
| `medication-reminder-callback` action `patient_unreachable` | iOS nurse | Patient didn't pick up | Mark event unreachable, server pushes follow-up to patient |
| `medication-reminder-callback` action `get_schedules` | iOS patient | Open schedule screen | List active timetables |

No new endpoints needed. Everything reuses existing infrastructure.

## 8. Visual + copy tokens

### Tokens

| Token | Hex | Usage |
|---|---|---|
| `reminderTeal` | `#2A9D8F` | Heads-up banner background fill (subtle) |
| `reminderTealText` | `#1F7A6F` | Heads-up banner title |
| `fallbackAmberBg` | `#FEF3C7` | Fallback banner background |
| `fallbackAmberText` | `#92400E` | Fallback banner title + body |
| `nurseRoyalPurple` | `#7C3AED` | Nurse incoming-call accent (matches Royal theme) |

### Copy matrix

| Key | English |
|---|---|
| `med_reminder_heads_up_title` | Medication Reminder |
| `med_reminder_heads_up_body` | A nurse will call you shortly to remind you to take %1$@. |
| `med_reminder_fallback_title` | Medication Reminder |
| `med_reminder_fallback_body` | We couldn't reach a nurse for your %1$@ reminder. Please remember to take it now. |
| `med_reminder_call_title` | Medication Reminder Call |
| `med_reminder_call_subtitle` | For: %1$@ |
| `med_reminder_outcome_prompt` | How did the call go? |
| `med_reminder_outcome_completed` | Patient confirmed |
| `med_reminder_outcome_unreachable` | Patient did not answer |
| `med_reminder_dismiss` | Dismiss |

`%1$@` placeholder receives the medication name from the push data.
Translations into `sw`/`fr`/`es`/`ar`/`hi` flagged `TODO(i18n)`.

The nurse outcome strings exist on Android implicitly (the buttons
are inside `IncomingCallService`'s call-end UI). iOS should expose
them as keyed strings rather than hardcoding.

## 9. Acceptance criteria

Cross-check each against staging with `PAYMENT_ENV=mock` (so test
nurse receives real payment row with TZS 1,000 — confirms the server
side fired correctly).

### Nurse side

1. **Push wakes nurse phone**: with the iOS doctor app installed and
   nurse signed in, scheduling a test reminder for "+30 seconds"
   makes the device ring within ≤ 3s of that time.
2. **Incoming call UI shows medication name**: subtitle reads `For:
   {medication_name}` not the patient's name. Patient identity is not
   surfaced before the nurse accepts.
3. **Accept → audio call connects** to the same room as the patient.
   Both sides hear each other. Video is off by default.
4. **Outcome sheet is mandatory**: dismissing the call without
   tapping Confirmed or No-answer keeps the sheet up and shows a
   validation hint.
5. **Completed credits 1,000 TZS**: after tapping "Patient
   confirmed", the nurse's earnings ledger gains a TZS 1,000 row
   linked to the `event_id` (verify with `doctor-status` script we
   have).
6. **Unreachable triggers patient follow-up push**: after tapping
   "Patient did not answer", the patient phone receives a push with
   the unreachable copy within ~5s.
7. **Decline is silent**: declining doesn't call the callback;
   server's 2-min reassign loop picks the event up and tries
   another nurse.

### Patient side

8. **Heads-up arrives ahead of call**: patient phone shows
   "A nurse will call you shortly..." before any incoming-call UI.
9. **Heads-up is teal-tinted**, not amber.
10. **Foreground banner auto-dismisses** after 30 seconds.
11. **Real call UI is the existing consultation incoming-call sheet**
    — no separate medication call screen on the patient side.
12. **Fallback path renders distinctly**: when staging is configured to
    have zero online nurses for a 4-minute window (3 retries × 1 min),
    the patient eventually receives a push with `data.fallback == true`,
    rendered in amber with the warning icon, no follow-up call expected.
13. **Schedule screen is read-only**: opening `/medication-schedule`
    in iOS shows the timetables but offers no edit/cancel/add controls.

## 10. Out of scope

- **Patient-initiated cancel** of a schedule. Doctor must cancel
  (not built yet anywhere).
- **Group / family member calls** — strict 1-on-1 between assigned
  nurse and patient.
- **Call recording / transcription** — not part of medication
  reminder VideoSDK rooms.
- **Custom audio devices for nurses** — standard CallKit / VideoSDK
  audio routing only.
- **Multi-language nurse outcome notes** — outcome is binary
  (completed / unreachable). No free-text note.
- **Patient confirms in-app instead of nurse marking** — nurse is the
  source of truth for whether the dose was acknowledged. If we ever
  add a patient-side "I've taken it" button, that's a separate spec.
- **Reminders in a different timezone than EAT** — cron compares
  `scheduled_times` against the EAT clock (`UTC+3`). Patients abroad
  receive at EAT-relative times. Future improvement; out of scope.
- **Doctor-side schedule edits trigger event-level updates** — today,
  doctor edits don't propagate to already-scheduled events for the
  same day. iOS can't fix that client-side.
