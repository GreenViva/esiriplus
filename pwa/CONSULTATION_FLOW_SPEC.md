# eSIRI Plus — Consultation Flow Specification (for PWA Development)

This document describes the **complete lifecycle** of a consultation: from a patient requesting a doctor, through the doctor accepting, the live chat session, to the final report submission. It covers UI layout, data models, edge function contracts, realtime subscriptions, authentication, and the earnings/escrow model. The PWA must replicate this behavior exactly.

---

## Table of Contents

1. [Authentication Model](#1-authentication-model)
2. [Patient Requests a Doctor](#2-patient-requests-a-doctor)
3. [Doctor Receives the Request](#3-doctor-receives-the-request)
4. [Doctor Accepts (or Declines / Expires)](#4-doctor-accepts-or-declines--expires)
5. [The Chat Environment](#5-the-chat-environment)
6. [Session Timer & Extensions](#6-session-timer--extensions)
7. [Report Submission](#7-report-submission)
8. [Patient Receives & Views Report](#8-patient-receives--views-report)
9. [Earnings & Escrow Split](#9-earnings--escrow-split)
10. [Realtime Infrastructure](#10-realtime-infrastructure)
11. [Edge Function Reference](#11-edge-function-reference)
12. [Key Data Models](#12-key-data-models)

---

## 1. Authentication Model

eSIRI uses a **dual-token architecture** because patients are anonymous (no Supabase Auth account) while doctors use standard Supabase Auth.

### Patient Auth
- Patients create a session via `create-patient-session` edge function, which issues a **custom HS256-signed JWT** containing `{ sub, session_id, app_role: "patient", exp, iat }`.
- The JWT secret is the project's `JWT_SECRET` (same as `SUPABASE_JWT_SECRET`).
- Since Supabase gateway requires a valid JWT in `Authorization`, the patient sends the **anon key** as the Bearer token and places the real patient JWT in the `X-Patient-Token` header.

### Doctor Auth
- Doctors have Supabase Auth accounts. Their JWT goes in the `X-Doctor-Token` header.
- The anon key still goes in `Authorization: Bearer` for the gateway.

### Edge Function Auth Contract
Every authenticated edge function calls `validateAuth(req)` which:
1. Checks `X-Patient-Token` first (HS256 verification + DB session check: active, not locked, not expired).
2. Falls back to `X-Doctor-Token` (Supabase Auth `getUser()` + role lookup from `user_roles` table).
3. Returns `AuthResult { userId: string|null, sessionId: string|null, role, jwt }`.
   - **Patient**: `userId=null`, `sessionId=<uuid>`, `role="patient"`
   - **Doctor**: `userId=<uuid>`, `sessionId=null`, `role="doctor"`
   - **Admin/HR/Finance/Audit**: `userId=<uuid>`, `sessionId=null`, `role=<role_name>`

### CORS
All edge functions accept CORS preflight. Allowed headers: `Authorization, Content-Type, X-Idempotency-Key, X-Patient-Token, X-Doctor-Token`.

---

## 2. Patient Requests a Doctor

### Navigation Flow (UI)
```
PatientHomeScreen → TierSelectionScreen → ServiceLocationScreen
→ ServicesScreen → FindDoctorScreen → [tap doctor card] → Symptoms dialog → Waiting overlay
```

### FindDoctorScreen
- Lists doctors filtered by service category (GP, Specialist, Nurse, etc.)
- Calls edge function `list-doctors` with `{ specialty: "gp"|"specialist"|... }`
- Each doctor card shows: avatar, name, specialty, rating stars, availability badge (green dot = online)
- Availability updates in realtime via Supabase Realtime subscription on `doctor_profiles` table (listens for `is_available` changes)
- Filter bar: search by name/specialty/bio, toggle ONLINE/OFFLINE/ALL

### Sending the Request
**Edge function:** `handle-consultation-request`
**Action:** `create`

**Request body:**
```json
{
  "action": "create",
  "doctor_id": "<uuid>",
  "service_type": "gp|specialist|nurse|clinical_officer|pharmacist|psychologist",
  "service_tier": "ECONOMY|ROYAL",
  "consultation_type": "chat",
  "chief_complaint": "<string>",
  "symptoms": "<string|null>",
  "patient_age_group": "<string|null>",
  "patient_sex": "<string|null>",
  "patient_blood_group": "<string|null>",
  "patient_allergies": "<string|null>",
  "patient_chronic_conditions": "<string|null>",
  "is_follow_up": false,
  "parent_consultation_id": null,
  "agent_id": "<string|null>",
  "is_substitute_follow_up": false,
  "original_doctor_id": null,
  "region": "<string|null>"
}
```

**Server-side validation:**
- Patient is authenticated (session_id exists)
- Doctor exists, is verified, is available, is not banned, is not suspended
- Doctor is NOT already in an active session (`in_session` flag)
- No duplicate active request from this patient to this doctor
- For follow-ups: validates parent consultation, tier inheritance, and follow-up limits (Economy = 1, Royal = unlimited within 14 days)

**Server creates a `consultation_requests` row:**
```
request_id, patient_session_id, doctor_id, service_type, service_tier,
consultation_type, chief_complaint, symptoms, patient_age_group, patient_sex,
patient_blood_group, patient_allergies, patient_chronic_conditions,
is_follow_up, parent_consultation_id, agent_id, is_substitute_follow_up,
original_doctor_id, status='pending', created_at, expires_at=created_at+60s
```

**Server then:**
1. Sends push notification to doctor via `send-push-notification` (FCM data-only message, no patient PII in payload — metadata only: `type: "consultation_request"`, `request_id`, `service_type`)
2. Returns `{ request_id, status: "pending", expires_at }`

### Patient Waiting UI
- Shows **60-second countdown** (circular progress + seconds text)
- Listens for status changes via Supabase Realtime on `consultation_requests` table filtered by `patient_session_id`
- **Polling fallback:** If realtime is unreliable, polls `handle-consultation-request?action=status` every 3 seconds
- On `accepted` → extract `consultation_id` → navigate to chat screen
- On `rejected` → show "Doctor declined" message, auto-clear after 3s
- On `expired` → show "Doctor did not respond" message, auto-clear after 3s

---

## 3. Doctor Receives the Request

### Delivery Channels (Redundant — all three are used)

**A. Push Notification (FCM):**
- Data-only FCM message arrives even when app is backgrounded
- FCM handler emits the request to the realtime service for ViewModel consumption
- If foreground service is not running, shows a system notification as fallback

**B. Supabase Realtime:**
- Doctor subscribes to channel `doctor-requests-{doctorId}` on `consultation_requests` table filtered by `doctor_id`
- Receives INSERT event when a new pending request is created

**C. Polling (last resort):**
- Not actively used on doctor side; realtime + FCM provide sufficient coverage

### IncomingRequestDialog (UI Layout)
When a pending request arrives, a full-screen dialog appears:

```
┌──────────────────────────────────────┐
│           🩺 New Request             │
│                                      │
│  ┌──────────────────────────────┐    │
│  │ Patient Info Card            │    │
│  │ • Age: 25  •  Sex: Male     │    │
│  │ • Blood: O+                 │    │
│  │ • Symptoms: "Chest pain..." │    │
│  │ • Allergies: "Penicillin"   │  ← (red text)
│  │ • Chronic: "Hypertension"   │    │
│  └──────────────────────────────┘    │
│                                      │
│       ⏱ 47 seconds remaining        │
│  ════════════════════════            │ ← progress bar (teal → red ≤10s)
│                                      │
│  ┌──────────┐  ┌───────────────┐    │
│  │  DECLINE  │  │    ACCEPT     │    │
│  │  (red)    │  │   (green)     │    │
│  └──────────┘  └───────────────┘    │
└──────────────────────────────────────┘
```

- **Countdown:** 60 → 0 seconds with progress bar
- Progress bar color: teal (BrandTeal `#2A9D8F`) until ≤10s remaining, then red
- Buttons disabled while processing (`isResponding` state)
- If accept fails: shows error + "Retry" button, countdown pauses
- If countdown reaches 0: auto-dismiss after 2 seconds

---

## 4. Doctor Accepts (or Declines / Expires)

### Accept Flow
**Edge function:** `handle-consultation-request`
**Action:** `accept`
**Body:** `{ "action": "accept", "request_id": "<uuid>" }`

**Server-side processing:**
1. Verify doctor owns the request and status is still `pending`
2. **Server-side expiry check:** if `expires_at < now`, auto-expire and reject
3. Create consultation via `create_consultation_with_cleanup` RPC (atomic):
   - Closes any stale open consultations for the patient
   - Creates `consultations` row with `status='active'`, `session_start_time=now`, `scheduled_end_at=now+duration`
   - Sets `consultation_fee` based on tier (Royal = 10× base fee)
4. Atomically update request: `status='accepted'`, `consultation_id=<new_id>`
5. Send push notification to patient: `type: "consultation_accepted"`, includes `consultation_id`
6. Return `{ request_id, status: "accepted", consultation_id }`

### Consultation Duration by Service Type
| Service Type      | Duration |
|-------------------|----------|
| Nurse             | 15 min   |
| Clinical Officer  | 15 min   |
| Pharmacist        | 5 min    |
| GP                | 15 min   |
| Specialist        | 20 min   |
| Psychologist      | 30 min   |

### Consultation Fee (TZS)
| Service Type      | Economy | Royal (10×) |
|-------------------|---------|-------------|
| Nurse             | 5,000   | 50,000      |
| Clinical Officer  | 7,000   | 70,000      |
| Pharmacist        | 3,000   | 30,000      |
| GP                | 10,000  | 100,000     |
| Specialist        | 30,000  | 300,000     |
| Psychologist      | 50,000  | 500,000     |
| Follow-up         | Free    | Free        |

### Navigation After Accept
- **Doctor:** Emits `ConsultationStartedEvent(consultationId)` → navigates to `DoctorConsultationDetailScreen`
- **Patient:** Receives realtime event (status=accepted + consultationId) → navigates to `PatientConsultationScreen`

### Decline Flow
**Action:** `reject`
**Body:** `{ "action": "reject", "request_id": "<uuid>" }`
- Updates request status to `rejected`
- Patient notified via realtime + push

### Expiry Flow
- Either side can call `action: "expire"` after 60 seconds
- Server validates that `expires_at < now` before allowing
- Request marked as `expired`

---

## 5. The Chat Environment

### Screen Layout (Both Doctor & Patient)

```
┌──────────────────────────────────────┐
│ ← Consultation  ID: a1b2c3d4        │  ← Top bar
│    [📞 ▼]  [📝]  [📋]  [✕]         │  ← Action icons (doctor only)
├──────────────────────────────────────┤
│  ⏱ 12:34                            │  ← Timer bar (color-coded)
├──────────────────────────────────────┤
│  ⚠ Connection lost — messages may    │  ← Connection banner (if disconnected)
│    be delayed                        │
├──────────────────────────────────────┤
│  ╌╌╌ Previous session ╌╌╌╌╌╌╌╌╌╌╌  │  ← Separator (if follow-up)
│  [dimmed messages from parent        │  ← 55% opacity
│   consultation]                      │
│  ╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌  │
│                                      │
│         ┌──────────────┐             │
│         │ Patient msg  │  10:32      │  ← White bubble, right-rounded
│         └──────────────┘             │
│  ┌──────────────┐                    │
│  │ Doctor msg   │  10:33             │  ← Teal bubble, left-rounded
│  └──────────────┘                    │
│                                      │
│  ┌──────────┐                        │
│  │ 📷 Image │  10:34                │  ← Tap for fullscreen pinch-to-zoom
│  └──────────┘                        │
│                                      │
│  Doctor is typing...                 │  ← Animated 3-dot indicator
│                                      │
├──────────────────────────────────────┤
│  ── Extension/Grace overlays ──      │  ← Bottom overlay slot
├──────────────────────────────────────┤
│  [📎]  [Type a message...     ] [➤] │  ← Input bar
└──────────────────────────────────────┘
```

### Doctor Toolbar Actions
| Icon | Action | Description |
|------|--------|-------------|
| 📞 ▼ | Dropdown | "Voice Call" or "Video Call" → navigates to VideoCallScreen |
| 📝 | Write Report | Opens report bottom sheet |
| 📋 | Generate Summary | AI-powered patient summary (calls OpenAI) |
| ✕ (red) | End Consultation | Confirmation dialog → ends session |

### Patient Toolbar Actions
| Icon | Action | Description |
|------|--------|-------------|
| 📞 ▼ | Dropdown | "Voice Call" or "Video Call" (hidden in follow-up mode) |

### Message Data Model
```typescript
interface Message {
  message_id: string;       // UUID
  consultation_id: string;
  sender_type: "doctor" | "patient" | "system";
  sender_id: string;        // doctor UUID or patient session UUID or "system"
  message_text: string;
  message_type: "text" | "image" | "document";
  attachment_url: string | null;
  is_read: boolean;
  created_at: string;       // ISO 8601
}
```

### Message Types
- **text**: Plain text
- **image**: Uploaded image (watermarked with doctor name). Tap to view fullscreen with pinch-to-zoom. No save/share allowed.
- **document**: Uploaded PDF (watermarked). Tap to open in Google Docs viewer (`https://docs.google.com/gview?embedded=true&url=<encoded_url>`)
- **system**: Auto-generated messages ("Session time has ended.", "Session extended by 15 minutes.", etc.)

### Sending Messages
**Edge function:** `handle-messages`
**Action:** `send`
```json
{
  "action": "send",
  "consultation_id": "<uuid>",
  "sender_type": "doctor|patient",
  "sender_id": "<uuid>",
  "message_text": "<string>",
  "message_type": "text|image|document",
  "attachment_url": "<string|null>"
}
```
Server validates: sender identity matches authenticated user, user is a participant in the consultation.

### Receiving Messages
**Primary:** Supabase Realtime subscription on `messages` table filtered by `consultation_id`
**Fallback polling:**
- Realtime connected → poll every 30 seconds
- Realtime disconnected → poll every 3 seconds
- Polls via: `{ "action": "get", "consultation_id": "<id>", "since": "<ISO timestamp>" }`

### Attachments
- **Allowed:** Images (all formats) and PDFs only
- **Max size:** 10 MB per file
- **Watermarking:** Doctor name overlaid on images and PDFs before upload
- **Storage:** Supabase Storage bucket `message-attachments`, path: `{consultationId}/{randomUUID}.{ext}`
- **Upload flow:** File picker → watermark → upload to storage → get public URL → send as message with `attachment_url`

### Typing Indicators
- **Send:** `{ "action": "typing", "consultation_id": "<id>", "user_id": "<id>", "is_typing": true|false }`
- **Throttle:** Only send `is_typing=true` if >2 seconds since last send
- **Auto-clear:** Send `is_typing=false` after 3 seconds of no typing
- **Display:** Show "Doctor/Patient is typing..." with animated dots, hide after 5 seconds of no event
- **Realtime:** Subscribed on `typing_indicators` table filtered by `consultation_id`

### Previous Session History (Follow-ups)
- When fetching messages with `include_parent=true`, the server walks up the `parent_consultation_id` chain
- Messages from parent consultations are returned with `is_from_previous_session: true`
- UI renders these above a separator line ("Previous session ─────") at 55% opacity

### Follow-Up Mode (Royal Tier)
- Completed Royal consultations remain open for 14 days (`follow_up_expiry`)
- Patient can send messages (read-only for doctor, no timer, no call buttons)
- Banner shows: "★ Royal Follow-up Mode" with days remaining
- Detected by: `status=completed` AND `serviceTier=ROYAL` AND `followUpExpiry > now`

---

## 6. Session Timer & Extensions

### Timer Synchronization
- On screen open: call `manage-consultation` with `action: "sync"` to get authoritative remaining time
- Response includes `scheduled_end_at`, `server_time`, `status`, `grace_period_end_at`, `extension_count`
- Client anchors countdown to `scheduled_end_at - server_time` at the moment of sync
- Countdown ticks every 1 second

### Timer Bar Colors
| Remaining | Color |
|-----------|-------|
| > 3 min   | Green (BrandTeal `#2A9D8F`) |
| 1–3 min   | Orange |
| ≤ 1 min   | Red |
| Grace period | Orange |
| 00:00 | Displayed during AWAITING_EXTENSION |

### Consultation Phase State Machine
```
ACTIVE (counting down)
  │
  ├─ timer reaches 0 ─→ call manage-consultation { action: "timer_expired" }
  │                      ↓
  │                AWAITING_EXTENSION (doctor sees "Waiting for patient...")
  │                      │
  │   ┌──────────────────┼──────────────────┐
  │   │                  │                  │
  │   ▼                  ▼                  ▼
  │ patient accepts    patient declines   timeout
  │ { action:          { action:          (auto-end)
  │  "accept_extension" "decline_extension" }
  │  }                      │
  │   ↓                     │
  │ GRACE_PERIOD             │
  │ (2-5 min for payment)    │
  │   │                     │
  │   ├─ payment confirmed   │
  │   │  { action:           │
  │   │   "payment_confirmed"│
  │   │   payment_id: "..." }│
  │   │   ↓                 │
  │   │  ACTIVE (extended)   │
  │   │                     │
  │   ├─ payment cancelled   │
  │   │  { action:           │
  │   │   "cancel_payment" } │
  │   │   ↓                 │
  │   │  AWAITING_EXTENSION  │
  │   │                     │
  │   └─ grace period expires│
  │      ↓                  │
  │    doctor can end ──────┘
  │                         │
  └─ doctor ends early ─────┘
         { action: "end" }
               ↓
          COMPLETED
```

### manage-consultation Edge Function
**All actions require:** `{ "action": "<action>", "consultation_id": "<uuid>" }`
**Additional for payment_confirmed:** `"payment_id": "<uuid>"`

| Action | Who Can Call | Effect |
|--------|-------------|--------|
| `sync` | Either | Returns current consultation state + server time |
| `end` | Doctor | Ends consultation, sets 14-day follow-up window, notifies patient |
| `timer_expired` | Either | Transitions to `awaiting_extension` |
| `request_extension` | Doctor | Notifies patient about extension, inserts system message |
| `accept_extension` | Patient | Starts grace period for payment processing |
| `decline_extension` | Patient | Doctor can now end consultation |
| `payment_confirmed` | Patient | Extends consultation by original duration |
| `cancel_payment` | Patient | Reverts to `awaiting_extension` |

### System Messages (Inserted Automatically)
- "Session time has ended."
- "Doctor requested time extension."
- "Patient is processing extension payment."
- "Session extended by {N} minutes."
- "Extension payment cancelled."
- "Patient declined the extension."
- "Consultation ended by doctor."

---

## 7. Report Submission

### When the Doctor Writes a Report
After ending a consultation (or during it via the 📝 button), the doctor opens a bottom sheet form.

**Important:** The doctor's `in_session` flag stays `true` until the report is submitted. This means they cannot receive new consultation requests until the report is filed. The database trigger `fn_sync_doctor_in_session()` checks for both active consultations AND completed consultations without `report_submitted=true`.

### Report Form Fields
| Field | Type | Required | Notes |
|-------|------|----------|-------|
| Diagnosed Problem | Textarea | Yes | Free-text clinical finding |
| Category | Dropdown | Yes | General Medicine, Neurological, Cardiovascular, Respiratory, Gastrointestinal, Musculoskeletal, Dermatological, Mental Health, Infectious Disease, Other |
| Other Category | Text | If "Other" | Custom category |
| Severity | Dropdown | No | Mild, Moderate, Severe |
| Treatment Plan | Textarea | Yes | Clinical instructions |
| Further Notes | Textarea | No | Additional observations |
| Prescriptions | List | No | Search from 370+ medication database. Each prescription has: medication name, form (Tablets/Syrup/Injection), dosage string, route (IM/IV/SC for injections) |
| Follow-up Recommended | Checkbox | No | Boolean flag |

### Submission Flow
**Edge function:** `generate-consultation-report`

**Request body:**
```json
{
  "consultation_id": "<uuid>",
  "diagnosed_problem": "Upper respiratory tract infection",
  "category": "Respiratory",
  "severity": "Mild",
  "treatment_plan": "Rest, fluids, and paracetamol 500mg 3x daily for 5 days",
  "further_notes": "Return if symptoms worsen after 48 hours",
  "follow_up_recommended": true,
  "prescriptions": [
    {
      "medication": "Amoxycillin trihydrate 500mg BP caps",
      "form": "Tablets",
      "dosage": "Take 1 tablet × 3 times per day × 7 days",
      "route": null
    },
    {
      "medication": "Diclofenac Sodium 75mg",
      "form": "Injection",
      "dosage": "75mg once",
      "route": "IM"
    }
  ]
}
```

### Server-Side Processing
1. **Auth & validation:** Only doctors, must own the consultation, rate limit 10/min
2. **Duplicate check:** Prevents submitting a second report for the same consultation
3. **Fetch context:** Consultation details, doctor profile, chat transcript (last 100 messages)
4. **AI report generation (GPT-4o-mini):** Takes the doctor's clinical notes + chat transcript and generates:
   - `presenting_symptoms` — professional 2-4 sentence patient summary
   - `diagnosis_assessment` — clinical assessment
   - `treatment_plan_prose` — expanded treatment instructions
   - `prescribed_medications_prose` — formatted medication list
   - `follow_up_instructions` — professional follow-up guidance
5. **Verification code:** Random 12-char uppercase alphanumeric (e.g., `A7K3M9X2P5B1`)
6. **Insert into `consultation_reports`:** All form fields + AI-generated fields + metadata
7. **Update consultation:** Set `report_submitted = true` → triggers `fn_sync_doctor_in_session()` → clears `in_session` flag → doctor can receive new requests
8. **Notify patient:** Push notification "Consultation Report Ready" with `consultation_id` and `report_id`
9. **Audit log:** Logs event with function_name, user_id, consultation_id, report_id

**Response:**
```json
{
  "message": "Report generated successfully",
  "report_id": "<uuid>",
  "verification_code": "A7K3M9X2P5B1",
  "report_content": {
    "presenting_symptoms": "...",
    "diagnosis_assessment": "...",
    "treatment_plan_prose": "...",
    "prescribed_medications_prose": "...",
    "follow_up_instructions": "..."
  }
}
```

---

## 8. Patient Receives & Views Report

### Fetching Reports
**Edge function:** `get-patient-reports`
- Queries `consultation_reports WHERE patient_session_id = ?`
- Returns all reports for the patient's session, ordered by `created_at DESC`

### Report Detail View (UI Layout)
```
┌──────────────────────────────────────┐
│  ESIRII HEALTH                       │
│  Consultation Report                 │
├──────────────────────────────────────┤
│  Date: 2026-04-06                    │
│  Verification: A7K3M9X2P5B1         │
│  Consultation Type: Telemedicine     │
├──────────────────────────────────────┤
│                                      │
│  § Presenting Symptoms               │
│  Patient presented with persistent   │
│  cough and mild fever for 3 days...  │
│                                      │
│  § Diagnosis & Assessment            │
│  Diagnosis: Upper respiratory tract  │
│  infection                           │
│  Category: Respiratory               │
│  Severity: Mild                      │
│  Assessment: ...                     │
│                                      │
│  § Treatment Plan                    │
│  Rest and adequate hydration...      │
│                                      │
│  § Prescribed Medications            │
│  1. Amoxycillin 500mg — Take 1      │
│     tablet × 3 times/day × 7 days   │
│  2. Diclofenac 75mg IM — once       │
│                                      │
│  § Follow-up Instructions            │
│  Follow-up recommended: Yes          │
│  Return if symptoms worsen...        │
│                                      │
│  § Further Notes                     │
│  Return if symptoms worsen after     │
│  48 hours.                           │
│                                      │
├──────────────────────────────────────┤
│  ⚕ Dr. John Doe                     │
│  Electronically signed               │
├──────────────────────────────────────┤
│  ⚠ Disclaimer: This consultation    │
│  was conducted via telemedicine...   │
├──────────────────────────────────────┤
│  Generated by ESIRII HEALTH          │
│                                      │
│  [📥 Download PDF]  [📤 Share]      │
└──────────────────────────────────────┘
```

### PDF Generation
- Generated client-side (in Android: `PdfDocument` API; for PWA: use a JS PDF library like jsPDF or html2pdf)
- A4 pages (595×842 points)
- Multi-page with automatic pagination
- Teal header with ESIRII HEALTH branding
- All sections preserved with exact formatting from UI
- Output: `consultation_report_{reportId}.pdf`
- Share via native share API or download

---

## 9. Earnings & Escrow Split

### Economy Tier (50/30/20 model)
```
100% collected from patient upfront
 ├── 50% → Platform revenue (immediate)
 ├── 30% → Consulting doctor (released on consultation completion + report)
 └── 20% → Follow-up escrow:
            ├── Same doctor does follow-up within 14 days → gets 30% + 20% = 50% total
            ├── Different doctor does follow-up → original gets 30%, substitute gets 20%
            └── No follow-up within 14 days → 20% moves to admin_review
```

### Royal Tier (50/50 model)
```
100% collected from patient
 ├── 50% → Platform
 └── 50% → Doctor (released on completion)
     (follow-ups are free — unlimited within 14 days, no additional earnings)
```

### Database Tables
- `doctor_earnings` — records each earning with `earning_type`: `consultation` (30%), `follow_up` (20%), `substitute_consultation`, `substitute_follow_up`
- `escrow_ledger` — full audit trail of all money movements: `consultation_hold`, `followup_hold`, `consultation_release`, `followup_release`, `followup_expired`, `admin_review`, `refund`

### Earnings Trigger
The PostgreSQL trigger `fn_auto_create_doctor_earning()` fires on `consultations` status transition to `completed`:
- **Original consultation:** Creates 30% earning (Economy) or 50% earning (Royal), holds 20% in escrow (Economy only)
- **Follow-up consultation:** Releases the parent's 20% escrow to the follow-up doctor

### Escrow Expiry
`fn_expire_followup_escrow()` is called by a cron job:
- Finds Economy consultations where `follow_up_expiry < NOW()` and `followup_escrow_status = 'held'`
- Moves them to `admin_review` status with audit trail

---

## 10. Realtime Infrastructure

### Supabase Realtime Channels Used

| Channel Purpose | Table | Filter | Events | Used By |
|----------------|-------|--------|--------|---------|
| Doctor availability | `doctor_profiles` | none (all doctors) | INSERT, UPDATE | Patient: FindDoctorScreen |
| Consultation requests (patient) | `consultation_requests` | `patient_session_id = ?` | INSERT, UPDATE | Patient: Waiting overlay |
| Consultation requests (doctor) | `consultation_requests` | `doctor_id = ?` | INSERT, UPDATE | Doctor: IncomingRequestDialog, OnlineService |
| Chat messages | `messages` | `consultation_id = ?` | INSERT, UPDATE | Both: ChatScreen |
| Typing indicators | `typing_indicators` | `consultation_id = ?` | INSERT, UPDATE | Both: ChatScreen |
| Consultation status | `consultations` | `consultation_id = ?` | UPDATE | Both: Timer/phase management |
| Doctor earnings | `doctor_earnings` | `doctor_id = ?` | INSERT, UPDATE | Doctor: Dashboard earnings |
| Doctor profile changes | `doctor_profiles` | `doctor_id = ?` | UPDATE | Doctor: Dashboard (verification, bans, warnings) |

### Connection States
```typescript
enum RealtimeConnectionState { CONNECTING, CONNECTED, DISCONNECTED }
```

### Reconnection Strategy
- **Exponential backoff:** 1s → 2s → 5s → 10s → 30s
- **Max attempts:** 10 (chat), 20 (doctor online service)
- **Network-aware:** Immediately reconnect when network becomes available
- **Fallback:** Polling kicks in when realtime is disconnected (3s interval for chat, 30s when connected)

### For the PWA
The PWA should use `@supabase/supabase-js` Realtime client. Key considerations:
- Subscribe to PostgreSQL changes (not Broadcast/Presence — all channels use table-level subscriptions)
- Filter syntax: `.on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'messages', filter: 'consultation_id=eq.<uuid>' }, callback)`
- Handle reconnection gracefully — show connection status banner
- Implement polling fallback for reliability

---

## 11. Edge Function Reference

| Function | Auth | Description |
|----------|------|-------------|
| `create-patient-session` | Anonymous | Creates patient session, returns custom JWT |
| `list-doctors` | Patient | Lists doctors by specialty (includes specialists serving as GP) |
| `handle-consultation-request` | Both | Create/accept/reject/expire/status consultation requests |
| `manage-consultation` | Both | Timer sync, end, extensions, payment flow |
| `handle-messages` | Both | Send/get messages, typing indicators, mark read |
| `generate-consultation-report` | Doctor | AI-powered report generation |
| `get-patient-reports` | Patient | Fetch all reports for a patient session |
| `send-push-notification` | Internal | FCM push delivery (called by other edge functions) |
| `videosdk-token` | Both | Generate VideoSDK meeting token for calls |
| `mpesa-callback` | Public | M-Pesa payment webhook |
| `extend-session` | Patient | Extend patient session expiry |
| `log-doctor-online` | Doctor | Track doctor online status |

### Common Headers (all authenticated requests)
```
Authorization: Bearer <SUPABASE_ANON_KEY>
Content-Type: application/json
X-Patient-Token: <patient_jwt>     (patient requests)
X-Doctor-Token: <doctor_jwt>       (doctor requests)
```

### Rate Limits
| Category | Limit | Used By |
|----------|-------|---------|
| `read` | 30/min | sync, get messages |
| `message` | varies | send messages |
| `consultation` | 15/min | end, timer, extensions |
| `payment` | 10/min | payment_confirmed, cancel_payment |
| `notification` | varies | typing, mark_read |

---

## 12. Key Data Models

### consultation_requests
```sql
request_id              UUID PRIMARY KEY
patient_session_id      UUID NOT NULL
doctor_id               UUID NOT NULL
service_type            service_type_enum
service_tier            TEXT ('ECONOMY' | 'ROYAL')
consultation_type       TEXT ('chat')
chief_complaint         TEXT
symptoms                TEXT
patient_age_group       TEXT
patient_sex             TEXT
patient_blood_group     TEXT
patient_allergies       TEXT
patient_chronic_conditions TEXT
is_follow_up            BOOLEAN DEFAULT FALSE
parent_consultation_id  UUID
agent_id                TEXT
is_substitute_follow_up BOOLEAN DEFAULT FALSE
original_doctor_id      UUID
status                  TEXT ('pending' | 'accepted' | 'rejected' | 'expired')
consultation_id         UUID (set on accept)
created_at              TIMESTAMPTZ
expires_at              TIMESTAMPTZ (created_at + 60s)
```

### consultations
```sql
consultation_id           UUID PRIMARY KEY
patient_session_id        UUID NOT NULL
doctor_id                 UUID NOT NULL
status                    TEXT ('active' | 'awaiting_extension' | 'grace_period' | 'completed')
service_type              service_type_enum
service_tier              TEXT ('ECONOMY' | 'ROYAL')
consultation_fee          INTEGER (TZS)
session_start_time        TIMESTAMPTZ
session_end_time          TIMESTAMPTZ
session_duration_minutes  INTEGER DEFAULT 15
scheduled_end_at          TIMESTAMPTZ
extension_count           INTEGER DEFAULT 0
grace_period_end_at       TIMESTAMPTZ
original_duration_minutes INTEGER DEFAULT 15
parent_consultation_id    UUID
follow_up_expiry          TIMESTAMPTZ
is_premium                BOOLEAN DEFAULT FALSE
is_substitute_follow_up   BOOLEAN DEFAULT FALSE
report_submitted          BOOLEAN DEFAULT FALSE
followup_escrow_amount    INTEGER DEFAULT 0
followup_escrow_status    TEXT ('none' | 'held' | 'released' | 'expired' | 'admin_review')
created_at                TIMESTAMPTZ
updated_at                TIMESTAMPTZ
```

### messages
```sql
message_id      UUID PRIMARY KEY
consultation_id UUID NOT NULL
sender_type     TEXT ('doctor' | 'patient' | 'system')
sender_id       TEXT NOT NULL
message_text    TEXT
message_type    TEXT ('text' | 'image' | 'document')
attachment_url  TEXT
is_read         BOOLEAN DEFAULT FALSE
created_at      TIMESTAMPTZ
```

### consultation_reports
```sql
report_id               UUID PRIMARY KEY
consultation_id         UUID NOT NULL
doctor_id               UUID NOT NULL
patient_session_id      UUID NOT NULL
diagnosed_problem       TEXT
category                TEXT
severity                TEXT
treatment_plan          TEXT
further_notes           TEXT
follow_up_recommended   BOOLEAN DEFAULT FALSE
prescriptions           JSONB DEFAULT '[]'
history                 TEXT (formatted medication list)
presenting_symptoms     TEXT (AI-generated)
diagnosis_assessment    TEXT (AI-generated)
treatment_plan_prose    TEXT (AI-generated)
prescribed_medications  TEXT (AI-generated)
follow_up_instructions  TEXT (AI-generated)
doctor_name             TEXT
consultation_date       TIMESTAMPTZ
verification_code       TEXT (12-char alphanumeric)
is_ai_generated         BOOLEAN DEFAULT TRUE
created_at              TIMESTAMPTZ
```

### doctor_earnings
```sql
earning_id      UUID PRIMARY KEY
doctor_id       UUID NOT NULL
consultation_id UUID NOT NULL
amount          INTEGER (TZS)
status          TEXT ('pending' | 'paid')
earning_type    TEXT ('consultation' | 'follow_up' | 'substitute_consultation' | 'substitute_follow_up')
paid_at         TIMESTAMPTZ
created_at      TIMESTAMPTZ
UNIQUE(doctor_id, consultation_id)
```

### escrow_ledger
```sql
id              UUID PRIMARY KEY
consultation_id UUID NOT NULL
doctor_id       UUID
entry_type      TEXT ('consultation_hold' | 'followup_hold' | 'consultation_release' | 'followup_release' | 'followup_expired' | 'admin_review' | 'refund')
amount          INTEGER (TZS)
currency        TEXT DEFAULT 'TZS'
notes           TEXT
created_at      TIMESTAMPTZ
```

---

## Notes for PWA Implementation

1. **Supabase client:** Use `@supabase/supabase-js` v2. The PWA shares the same Supabase project and edge functions.
2. **Auth tokens:** PWA must implement the same dual-header pattern (`X-Patient-Token` / `X-Doctor-Token` + anon key in `Authorization`).
3. **Realtime:** Use Supabase Realtime JS client with PostgreSQL changes subscriptions. Implement the same polling fallback.
4. **Push notifications:** Use Web Push API (FCM for web) or fall back to in-app polling.
5. **Watermarking:** Must watermark images and PDFs before upload (use Canvas API for images, pdf-lib for PDFs).
6. **Timer:** Use server-synced countdown (never trust client clock alone). Always sync via `manage-consultation?action=sync` on page load.
7. **Video calls:** Integrate VideoSDK web SDK for voice/video calls.
8. **PDF generation:** Use jsPDF or html2pdf.js for client-side report PDF generation.
9. **Offline resilience:** Cache messages locally (IndexedDB), retry failed sends, show sync status.
10. **Connection banner:** Show "Connection lost" / "Reconnecting..." when realtime disconnects, just like the Android app.
