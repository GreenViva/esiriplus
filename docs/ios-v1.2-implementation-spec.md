# eSIRI Plus v1.2 — iOS Implementation Specification

Complete feature specification covering all changes from stable-v1.2: appointment consultation flow, medication reminder system, auth fixes, and contact info updates. This document describes the user-facing behavior, API contracts, data models, and backend connectivity so the iOS team can implement feature parity.

---

## Table of Contents

1. [Appointment → Consultation Flow](#1-appointment--consultation-flow)
2. [Medication Reminder System (Royal Only)](#2-medication-reminder-system-royal-only)
3. [Auth 401 Fix — Token Refresh & Retry](#3-auth-401-fix--token-refresh--retry)
4. [Rating Flow — Token Refresh](#4-rating-flow--token-refresh)
5. [Contact Info Updates](#5-contact-info-updates)
6. [Backend API Reference](#6-backend-api-reference)
7. [Database Schema Changes](#7-database-schema-changes)
8. [Notification Types](#8-notification-types)

---

## 1. Appointment → Consultation Flow

### Overview
Patients can now start a consultation directly from an appointment. When the appointment time arrives (or within 5 minutes before), a "Start Consultation" button appears on the appointment card. Tapping it sends a consultation request to the appointed doctor with a 60-second countdown. The appointment stays visible until a consultation is fulfilled.

### UI: Patient Appointments Screen

**Appointment Card States:**

| State | Condition | UI |
|-------|-----------|-----|
| **Future** | `scheduledAt > now + 5min` | Shows date/time, service type, status badge. Cancel button (red outline). |
| **Ready to Start** | `scheduledAt <= now + 5min` AND `consultationId == null` AND status in (booked, confirmed, missed) | Shows **"Start Consultation"** button (teal, full-width). No cancel button. |
| **Waiting for Doctor** | After tapping Start, request is pending | Shows **"Waiting for doctor... XXs"** countdown bar (teal background, spinner + seconds). Button disabled. |
| **Has Consultation** | `consultationId != null` | No action buttons — appointment is fulfilled. |

**Cancel Confirmation Dialog:**
When patient taps Cancel on a future appointment:
```
Title: "Cancel Appointment"
Body: "Are you sure you want to cancel your appointment? You can consult 
       another doctor, but there will be no refund for this cancellation."
Buttons: [Keep Appointment] [Yes, Cancel (red)]
```

**Doctor Unavailable Dialog:**
Shown when the appointed doctor is busy, offline, or in session:
```
Title: "Doctor Unavailable"
Body: "The doctor is currently unavailable or in another session. 
       Would you like to find another doctor for this appointment?"
Buttons: [Cancel] [Find Another Doctor (teal)]
```
"Find Another Doctor" navigates to the doctor list screen with:
- `serviceCategory` = appointment's service type
- `appointmentId` = appointment's ID (passed to consultation request)

### Flow: Start Consultation

```
1. Patient taps "Start Consultation"
2. Button shows spinner: "Requesting Doctor..."
3. App calls POST handle-consultation-request with:
   - action: "create"
   - doctor_id: appointment's doctor
   - service_type: appointment's service type
   - chief_complaint: appointment's complaint or "Scheduled appointment"
   - appointment_id: appointment's ID        ← NEW FIELD
4. On success (request created):
   - Card switches to countdown: "Waiting for doctor... 60s"
   - Start 60-second countdown timer
   - Start polling every 3 seconds: POST handle-consultation-request
     with action: "status", request_id: returned request_id
   - Also subscribe to Supabase Realtime on consultation_requests 
     filtered by patient_session_id
5. On doctor accepts (status == "accepted"):
   - Stop countdown + polling
   - Navigate to consultation chat screen with consultationId
6. On doctor rejects (status == "rejected") OR countdown expires:
   - Stop countdown + polling  
   - Show "Doctor Unavailable" dialog
7. On initial request error (doctor not available / in session):
   - Show "Doctor Unavailable" dialog immediately (no countdown)
```

### Appointment Persistence Rules

**Upcoming tab** (visible, actionable):
- Status: booked, confirmed, in_progress
- OR: status == missed AND consultationId == null (missed but patient can still start)

**Past tab** (archived):
- Status: completed, cancelled, rescheduled
- OR: status == missed AND consultationId != null (missed but consultation happened)

### One Appointment = One Consultation Guard

**Server-side (edge function):**
When `appointment_id` is provided in the create request:
1. Verify appointment exists and belongs to the patient
2. Verify `consultation_id` is null (not already linked)
3. Verify no pending request exists for this appointment
4. On accept: set appointment's `consultation_id` and `status = 'in_progress'`

**Client-side:**
- Disable "Start Consultation" button while a request is active
- Don't show the button if `consultationId` is already set

### API: handle-consultation-request Changes

**Create action — new optional field:**
```json
{
  "action": "create",
  "doctor_id": "uuid",
  "service_type": "gp",
  "appointment_id": "uuid"     // ← NEW: optional, links request to appointment
}
```

**Accept action — automatic appointment linking:**
When the doctor accepts a request that has `appointment_id`:
- Sets `appointments.consultation_id = new_consultation_id`
- Sets `appointments.status = 'in_progress'`
- Guarded by `WHERE consultation_id IS NULL` (optimistic lock)

---

## 2. Medication Reminder System (Royal Only)

### Overview
Royal-tier patients get nurse-assisted medication reminders. Doctors create medication timetables from the Royal Clients screen. A cron job runs every minute and when a medication time arrives, it finds an available online nurse, creates a voice call room, and sends the nurse a call notification to remind the patient.

### UI: Doctor — Royal Clients Screen

**Location:** Doctor Dashboard → Royal Clients card (existing screen)

**Bottom Sheet Enhancement:**
When doctor taps a Royal client card, the existing bottom sheet now has a 4th option:

```
┌─────────────────────────────┐
│      ★ Royal Patient        │
│    GP · Apr 11, 2026        │
│       [Completed]           │
│                             │
│   Connect with Patient      │
│                             │
│  ┌─────────┐ ┌───────────┐ │
│  │  Voice   │ │   Video   │ │
│  │  Call    │ │   Call    │ │
│  └─────────┘ └───────────┘ │
│                             │
│  ┌───────────────────────┐  │
│  │  View Chat History    │  │
│  └───────────────────────┘  │
│                             │
│  ┌───────────────────────┐  │  ← NEW
│  │ 🔔 Set Medication     │  │
│  │    Reminder           │  │
│  └───────────────────────┘  │
└─────────────────────────────┘
```

Button style: Gold/amber background (10% opacity), gold bell icon, black text.

**Medication Timetable Dialog:**
Triggered by "Set Medication Reminder" button.

```
┌──────────────────────────────────┐
│  Medication Reminder Schedule    │
│  Medication for Royal Patient    │
│                                  │
│  Times per day                   │
│  ┌────┐ ┌────┐ ┌────┐ ┌────┐   │
│  │ 1x │ │ 2x │ │ 3x │ │ 4x │   │
│  └────┘ └────┘ └────┘ └────┘   │
│         (selected = filled teal) │
│                                  │
│  Reminder times (EAT)            │
│  ┌──────────────┐               │
│  │ Time 1: 08:00│               │
│  ├──────────────┤               │
│  │ Time 2: 14:00│               │
│  ├──────────────┤               │
│  │ Time 3: 20:00│               │
│  └──────────────┘               │
│                                  │
│  Duration (days)                 │
│  ┌──────────────┐               │
│  │ 7            │               │
│  └──────────────┘               │
│                                  │
│  A nurse will voice-call the     │
│  patient at each scheduled time  │
│  to remind them to take this     │
│  medication.                     │
│                                  │
│  [Cancel]  [Set Schedule (teal)] │
└──────────────────────────────────┘
```

**Smart Time Defaults:**
| Times/day | Default times |
|-----------|---------------|
| 1x | 08:00 |
| 2x | 08:00, 20:00 |
| 3x | 08:00, 14:00, 20:00 |
| 4x | 06:00, 12:00, 18:00, 22:00 |

**API call on confirm:**
```
POST /functions/v1/medication-reminder-callback
Authorization: Bearer <doctor_jwt>
{
  "action": "create_timetable",
  "consultation_id": "<consultation_id>",
  "patient_session_id": "<patient_session_id>",
  "medication_name": "Prescribed medication",
  "times_per_day": 3,
  "scheduled_times": ["08:00", "14:00", "20:00"],
  "duration_days": 7,
  "dosage": "500mg",          // optional
  "form": "Tablets"           // optional
}
```

Response: `{ "ok": true, "timetable_id": "uuid" }`

### UI: Doctor — Report Screen (Alternative Entry Point)

When submitting a consultation report for a ROYAL consultation, each prescription card shows:

- **"+ Set Nurse Reminder"** link (teal text, clickable) — opens the same timetable dialog
- After setting: shows green badge: **"Nurse reminder: 08:00, 14:00, 20:00 for 7d"**

The timetable data is included in the report submission body:
```json
{
  "consultation_id": "...",
  "diagnosed_problem": "...",
  "prescriptions": [...],
  "medication_timetables": [        // ← NEW, included only for ROYAL
    {
      "medication_name": "Amoxicillin 500mg",
      "dosage": "Take 1 tablet × 3 times per day × 7 days",
      "form": "Tablets",
      "times_per_day": 3,
      "scheduled_times": ["08:00", "14:00", "20:00"],
      "duration_days": 7
    }
  ]
}
```

### UI: Patient — Medication Schedule Screen

**Location:** Accessible from patient dashboard (new route)

**Layout:**
- Header: "Medication Schedule" with subtitle "A nurse will call you at scheduled times"
- List of active timetable cards, each showing:
  - Medication name (bold, black)
  - Dosage (grey subtitle)
  - Active/Ended status badge (green/red)
  - Time pills: `08:00` `14:00` `20:00` (teal chips)
  - Date range: "2026-04-12 to 2026-04-18 (7 days)"
- Empty state: bell icon + "No Active Medication Schedules" + explanation text

**API call:**
```
POST /functions/v1/medication-reminder-callback
X-Patient-Token: <patient_jwt>
Authorization: Bearer <anon_key>
X-Skip-Auth: true
{
  "action": "get_schedules"
}
```

Response:
```json
{
  "timetables": [
    {
      "timetable_id": "uuid",
      "medication_name": "Amoxicillin 500mg",
      "dosage": "Take 1 tablet × 3 times per day × 7 days",
      "form": "Tablets",
      "times_per_day": 3,
      "scheduled_times": ["08:00", "14:00", "20:00"],
      "duration_days": 7,
      "start_date": "2026-04-12",
      "end_date": "2026-04-18",
      "is_active": true
    }
  ]
}
```

### Cron: Medication Reminder Processing

**Edge function:** `medication-reminder-cron` (called every minute by pg_cron)

**Flow:**
1. Get current time in EAT (Africa/Nairobi, UTC+3) as HH:MM
2. Query `medication_timetables` where:
   - `is_active = true`
   - `start_date <= today AND end_date >= today`
   - `scheduled_times` contains current HH:MM
3. For each match, INSERT into `medication_reminder_events` with UNIQUE(timetable_id, date, time) — duplicate prevention
4. For each new event:
   a. Find available nurse: `doctor_profiles WHERE specialty='nurse' AND is_verified=true AND is_available=true AND in_session=false`
   b. Create VideoSDK room (POST to `https://api.videosdk.live/v2/rooms`)
   c. Update event: nurse_id, video_room_id, status='nurse_notified'
   d. Push to nurse: type `MEDICATION_REMINDER_CALL` with room_id, event_id, medication info
   e. Push to patient: type `MEDICATION_REMINDER_PATIENT` — "A nurse will call you shortly"
5. **Retry:** Events with status='no_nurse' and retry_count < 3 are re-processed each minute
6. **Timeout:** Events with status='nurse_notified' older than 2 minutes → reassign to different nurse (max 2 reassignments)
7. **Fallback:** After 3 retries with no nurse, send text-only push to patient: "We couldn't reach a nurse. Please remember to take [medication]."

### Nurse: Incoming Medication Reminder Call

**FCM notification type:** `MEDICATION_REMINDER_CALL`

**FCM data payload:**
```json
{
  "type": "MEDICATION_REMINDER_CALL",
  "event_id": "uuid",
  "room_id": "videosdk-room-id",
  "patient_session_id": "uuid",
  "medication_name": "Amoxicillin 500mg",
  "dosage": "500mg"
}
```

**Behavior:**
- Treat like an incoming call: show full-screen notification, wake device
- Call type: **AUDIO only** (voice call, not video)
- Caller role: `medication_reminder` (used to show medication context in the call UI)
- Nurse accepts → joins VideoSDK room → patient receives incoming call notification
- After call ends, nurse's client calls:

```
POST /functions/v1/medication-reminder-callback
Authorization: Bearer <nurse_jwt>
{
  "action": "completed",
  "event_id": "uuid"
}
```

Or if patient didn't answer:
```json
{
  "action": "patient_unreachable",
  "event_id": "uuid"
}
```

### Nurse Earnings

**On completed call:** Nurse earns **1000 TZS** per completed medication reminder call.
- Inserted into `doctor_earnings` with `earning_type = 'medication_reminder'`
- Amount: 1000
- Status: 'pending' (paid out in monthly cycle like other earnings)
- Multiple earnings allowed per nurse per consultation (one per completed call event)

---

## 3. Auth 401 Fix — Token Refresh & Retry

### Problem
Patient JWTs are custom HS256 tokens that can't be refreshed by the OkHttp interceptor chain. When tokens expire, requests fail with 401.

### Fix Applied To

| Screen/Action | Fix |
|--------------|-----|
| **Consultation Request** (ConsultationRequestViewModel.sendRequest) | Proactive refresh before request. If refresh fails AND token still bad → abort with "Session expired" message. If request returns 401 → refresh + retry once. |
| **Follow-up Request** (FollowUpRequestViewModel.sendFollowUpRequest) | Same pattern: proactive refresh, abort on failure, 401 retry. |
| **Rating Submission** (RatingViewModel.submit) | Proactive refresh before server sync. If first attempt fails → refresh + retry once. |
| **Appointment Start** (PatientAppointmentsViewModel.startConsultation) | Proactive refresh before creating request. |

### Pattern (implement for all patient-initiated edge function calls)

```
1. Check token: tokenManager.getAccessTokenSync()
2. If null or expiring within 5 minutes:
   a. Call authRepository.refreshSession()
   b. If refresh fails AND token still null/expired:
      - Show "Your session has expired. Please log in again."
      - Abort the operation
3. Make the API call
4. If response is 401 (Unauthorized):
   a. Try refreshSession() once more
   b. If refresh succeeds → retry the API call
   c. If refresh fails → show "Session expired" error
```

### Doctor ID Backfill
For new consultations, `uiState.doctorId` was empty (set to "" on insert). Fixed by observing `ConsultationSessionManager.state` and backfilling from Room after the session manager syncs with the server. This ensures the rating sheet gets the correct `doctorId`.

---

## 4. Rating Flow — Token Refresh

The `RatingViewModel` now has `TokenManager` and `AuthRepository` injected. Before submitting to the `rate-doctor` edge function:

1. Proactive token refresh if null or expiring within 2 minutes
2. Call `submitRatingToServer(rating)`
3. If server sync fails → refresh token → retry once
4. Success if either server sync OR local save succeeded

---

## 5. Contact Info Updates

All contact sections in Terms of Service, Privacy Policy, Doctor Terms, and Informed Consent updated to:

**eSIRI Plus:**
- Email: support@esiri.africa
- Phone: +255 663 582 994

**Medical Council of Tanganyika (MCT):**
- PSSSF Building, Kambarage Tower, 10th Floor (near BOT Building)
- P.O. Box 2690, Dodoma, Tanzania
- Email: info@mct.go.tz
- Phone: +255 699 995 800

Updated in both patient terms (`TermsContent.kt`) and doctor terms (`DoctorTermsContent.kt`), across all CONTACT US sections.

---

## 6. Backend API Reference

### handle-consultation-request (modified)

**Create action — new field:**
```
POST /functions/v1/handle-consultation-request
Body: { "action": "create", ..., "appointment_id": "uuid" }
```

Validations when `appointment_id` provided:
- Appointment exists and belongs to caller
- `consultation_id IS NULL` (not already used)
- Status in (booked, confirmed, missed)
- No pending request for this appointment

On accept: links appointment to consultation.

### medication-reminder-callback (new, multi-action)

| Action | Auth | Body | Response |
|--------|------|------|----------|
| `create_timetable` | Doctor JWT | `{ action, consultation_id, patient_session_id, medication_name, times_per_day, scheduled_times[], duration_days, dosage?, form? }` | `{ ok, timetable_id }` |
| `get_schedules` | Patient JWT | `{ action: "get_schedules" }` | `{ timetables: [...] }` |
| `completed` | Doctor JWT (nurse) | `{ action: "completed", event_id }` | `{ ok: true }` |
| `patient_unreachable` | Doctor JWT (nurse) | `{ action: "patient_unreachable", event_id }` | `{ ok: true }` |

### medication-reminder-cron (new, cron-only)

Not called by clients. Triggered every minute by pg_cron. Processes due medication reminders.

### generate-consultation-report (modified)

New optional field in request body for ROYAL consultations:
```json
{
  "medication_timetables": [
    {
      "medication_name": "string",
      "dosage": "string",
      "form": "string",
      "times_per_day": 3,
      "scheduled_times": ["08:00", "14:00", "20:00"],
      "duration_days": 7
    }
  ]
}
```

Only processed when consultation `service_tier = 'ROYAL'`.

---

## 7. Database Schema Changes

### New column: consultation_requests.appointment_id
```sql
appointment_id UUID REFERENCES appointments(appointment_id)
```
Links a consultation request to an appointment. Set when patient starts consultation from an appointment.

### New table: medication_timetables
```sql
timetable_id       UUID PRIMARY KEY
consultation_id    UUID NOT NULL REFERENCES consultations
patient_session_id TEXT NOT NULL
doctor_id          UUID NOT NULL
medication_name    TEXT NOT NULL
dosage             TEXT
form               TEXT DEFAULT 'Tablets'
times_per_day      INT NOT NULL (1-6)
scheduled_times    TEXT[] NOT NULL        -- e.g. {'08:00','14:00','20:00'} EAT
duration_days      INT NOT NULL (>=1)
start_date         DATE NOT NULL
end_date           DATE NOT NULL
is_active          BOOLEAN DEFAULT true
created_at         TIMESTAMPTZ
updated_at         TIMESTAMPTZ
```

### New table: medication_reminder_events
```sql
event_id           UUID PRIMARY KEY
timetable_id       UUID NOT NULL REFERENCES medication_timetables
scheduled_date     DATE NOT NULL
scheduled_time     TEXT NOT NULL          -- 'HH:MM' EAT
status             TEXT DEFAULT 'pending' -- pending|nurse_notified|nurse_calling|completed|no_nurse|patient_unreachable|failed
nurse_id           UUID                  -- assigned nurse
video_room_id      TEXT                  -- VideoSDK room
nurse_notified_at  TIMESTAMPTZ
call_started_at    TIMESTAMPTZ
call_ended_at      TIMESTAMPTZ
retry_count        INT DEFAULT 0
reassign_count     INT DEFAULT 0
patient_notified   BOOLEAN DEFAULT false
created_at         TIMESTAMPTZ
UNIQUE (timetable_id, scheduled_date, scheduled_time)
```

### Modified: doctor_earnings unique constraint
Relaxed from `UNIQUE(doctor_id, consultation_id)` to a partial unique that excludes `medication_reminder` type, allowing multiple reminder earnings per nurse per consultation.

---

## 8. Notification Types

### New FCM types:

| Type | Target | Purpose |
|------|--------|---------|
| `MEDICATION_REMINDER_CALL` | Nurse (user_id) | Incoming voice call assignment for medication reminder |
| `MEDICATION_REMINDER_PATIENT` | Patient (session_id) | Heads-up that nurse will call, or fallback text reminder |

### MEDICATION_REMINDER_CALL payload:
```json
{
  "type": "MEDICATION_REMINDER_CALL",
  "event_id": "uuid",
  "room_id": "videosdk-room-id",
  "patient_session_id": "uuid",
  "medication_name": "Amoxicillin 500mg",
  "dosage": "500mg"
}
```
**Handling:** Treat as incoming AUDIO call. Show full-screen call notification. callerRole = "medication_reminder".

### MEDICATION_REMINDER_PATIENT payload:
```json
{
  "type": "MEDICATION_REMINDER_PATIENT",
  "event_id": "uuid",
  "room_id": "videosdk-room-id",     // present when nurse is calling
  "medication_name": "Amoxicillin 500mg"
}
```
**Handling:** Standard notification. No special action needed — the incoming call arrives separately via VIDEO_CALL_INCOMING from the nurse's client.

---

## Implementation Priority

1. **Auth 401 fix** — critical for all patient flows
2. **Appointment → Consultation** — complete user-facing flow
3. **Contact info updates** — simple text changes
4. **Medication Reminder (Doctor side)** — timetable creation UI
5. **Medication Reminder (Patient side)** — schedule view
6. **Medication Reminder (Nurse side)** — call handling (reuses existing call infra)
