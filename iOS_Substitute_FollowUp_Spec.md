# eSIRIPlus Substitute Follow-Up Doctor - iOS Implementation Guide

When a patient's follow-up request fails because the original doctor is offline, the patient can either book an appointment for later or request another available doctor for a free substitute consultation. The earnings are split 60/40 between the original and substitute doctor.

---

## 1. User Flow

```
Patient taps follow-up eligible consultation (Ongoing Consultations)
  → Confirmation dialog: "Request follow-up with Dr. X?"
  → Confirm → Send follow-up request to original doctor
  → Edge function returns error: "Doctor is not currently available"
  → IMMEDIATELY show choice screen (no 60s wait):

  ┌──────────────────────────────────────────┐
  │  [Clock/X icon]                          │
  │  "Doctor is not currently available"     │
  │                                          │
  │  "The doctor is not available.           │
  │   What would you like to do?"            │
  │                                          │
  │  ┌──────────────────────────────────┐    │
  │  │   Book Appointment (outlined)    │    │
  │  └──────────────────────────────────┘    │
  │  ┌──────────────────────────────────┐    │
  │  │  Request Another Doctor (filled) │    │
  │  └──────────────────────────────────┘    │
  │                                          │
  │           Go Back (text link)            │
  └──────────────────────────────────────────┘

Option A: "Book Appointment"
  → Navigate to appointment booking screen with original doctor

Option B: "Request Another Doctor"
  → Navigate to doctor list (same as FindDoctor screen)
  → Patient picks any available doctor
  → Sends substitute follow-up request (free, no payment)
  → Doctor gets "Follow-up patient calling" notification
  → 60s countdown → doctor accepts → chat opens
```

**Important:** If the original doctor IS online and the request is sent successfully, the normal 60-second countdown runs. If the doctor doesn't respond within 60s (EXPIRED) or rejects (REJECTED), the same choice screen appears.

---

## 2. How to Detect "Doctor Offline" vs Other Errors

The edge function returns a 400 validation error with this body:
```json
{
  "error": "Doctor is not currently available",
  "code": "VALIDATION_ERROR"
}
```

**iOS logic:**
```swift
if errorMessage.contains("not currently available") ||
   errorMessage.contains("in a session") ||
   errorMessage.contains("doctor_in_session") {
    // Show choice screen (Book Appointment / Request Another Doctor)
} else {
    // Show generic error with "Go Back" button
}
```

The same choice screen is also shown when:
- Request status becomes `"expired"` (60s timeout, doctor didn't respond)
- Request status becomes `"rejected"` (doctor declined)

---

## 3. Substitute Follow-Up Request API

### Create Substitute Follow-Up Request:
```
POST {SUPABASE_URL}/functions/v1/handle-consultation-request
Authorization: Bearer {SUPABASE_ANON_KEY}
X-Patient-Token: {patient_session_jwt}
Content-Type: application/json

{
  "action": "create",
  "doctor_id": "substitute-doctor-uuid",         ← NEW doctor chosen by patient
  "service_type": "gp",
  "service_tier": "ROYAL",
  "consultation_type": "chat",
  "chief_complaint": "Follow-up consultation",
  "is_follow_up": true,
  "parent_consultation_id": "original-consultation-uuid",
  "is_substitute_follow_up": true,                ← NEW field
  "original_doctor_id": "original-doctor-uuid"    ← NEW field (must match parent's doctor)
}
```

### Key Differences from Normal Follow-Up:
| Field | Normal Follow-Up | Substitute Follow-Up |
|-------|-----------------|---------------------|
| `doctor_id` | Same as parent's doctor | Any available doctor |
| `is_follow_up` | `true` | `true` |
| `is_substitute_follow_up` | absent/false | `true` |
| `original_doctor_id` | absent | Parent consultation's doctor UUID |

### Server Validation:
- Parent consultation must exist, belong to patient, be completed, within follow-up window
- `original_doctor_id` must match the parent consultation's `doctor_id`
- The **same-doctor check is skipped** for substitute follow-ups
- Economy 1-follow-up limit still applies (substitute counts as the follow-up)
- `consultation_fee = 0` (free for patient)

### Response (201):
```json
{
  "request_id": "uuid",
  "status": "pending",
  "created_at": "2026-03-25T10:30:00.000Z",
  "expires_at": "2026-03-25T10:31:00.000Z",
  "ttl_seconds": 60
}
```

### Doctor Notification:
The substitute doctor receives:
- **Title:** "Follow-up Patient Request"
- **Body:** "Follow-up patient calling — gp consultation. Respond within 60s."

(Different from normal follow-up which says "Follow-up Request (Royal)")

---

## 4. Earnings Split

When a substitute follow-up consultation completes, the DB trigger `fn_auto_create_doctor_earning` handles the split automatically:

### Normal Consultation Earnings:
```
Consultation fee: 10,000 TZS (GP Economy)
Doctor share (50%): 5,000 TZS
→ 1 row in doctor_earnings: doctor gets 5,000
```

### Substitute Follow-Up Earnings:
```
Original consultation fee: 10,000 TZS (looked up from parent consultation)
Doctor share (50%): 5,000 TZS
→ Original doctor gets 60%: 3,000 TZS
→ Substitute doctor gets 40%: 2,000 TZS
→ 2 rows in doctor_earnings for the same consultation_id
```

**iOS does NOT need to implement any earnings logic.** The DB trigger handles everything automatically based on `is_substitute_follow_up` and `original_doctor_id` columns on the consultation.

---

## 5. Database Schema Changes (Already Deployed)

### consultation_requests table:
```sql
is_substitute_follow_up  BOOLEAN NOT NULL DEFAULT FALSE
original_doctor_id       UUID REFERENCES auth.users(id)
```

### consultations table:
```sql
is_substitute_follow_up  BOOLEAN NOT NULL DEFAULT FALSE
original_doctor_id       UUID REFERENCES auth.users(id)
```

### doctor_earnings table:
- Unique constraint changed from `UNIQUE(consultation_id)` to `UNIQUE(doctor_id, consultation_id)` — allows two earnings rows per consultation (one for original doctor, one for substitute)

---

## 6. Navigation Flow for "Request Another Doctor"

When the patient taps "Request Another Doctor":

1. Navigate to the **Find Doctor** screen with these parameters:
   - `serviceType` = original consultation's service type (e.g., "gp")
   - `serviceTier` = original consultation's tier (e.g., "ROYAL")
   - `servicePriceAmount` = 0 (free — show "Free follow-up" to patient)
   - `serviceDurationMinutes` = standard duration for the service type
   - Context flags:
     - `isSubstituteFollowUp = true`
     - `parentConsultationId` = original consultation ID
     - `originalDoctorId` = original doctor's UUID

2. Patient sees the doctor list (same as normal Find Doctor, but with price = 0)

3. Patient taps "Request Consultation" on a doctor → the request is sent with all substitute fields

4. Normal 60-second countdown starts

5. If substitute doctor accepts → navigate to consultation chat

### Duration by Service Type:
```
nurse: 15 min
clinical_officer: 15 min
pharmacist: 5 min
gp: 15 min
specialist: 20 min
psychologist: 30 min
```

---

## 7. Navigation Flow for "Book Appointment"

When the patient taps "Book Appointment":

Navigate to the **Book Appointment** screen with:
- `doctorId` = original doctor's UUID
- `serviceCategory` = original service type
- `servicePriceAmount` = 0 (follow-up appointment is free)
- `serviceTier` = original tier

The booking flow is the same as normal appointment booking. The patient picks a future date/time when the doctor is available.

---

## 8. Complete State Machine

```
SENDING → request created → WAITING (60s countdown)
SENDING → "not currently available" error → SHOW CHOICE (immediate)
SENDING → other error → ERROR (show "Go Back")
WAITING → doctor accepts → ACCEPTED → navigate to chat
WAITING → doctor rejects → SHOW CHOICE
WAITING → 60s timeout → SHOW CHOICE
WAITING → polling detects expired → SHOW CHOICE

SHOW CHOICE states:
  → "Book Appointment" → appointment booking screen
  → "Request Another Doctor" → find doctor screen (substitute mode)
  → "Go Back" → return to ongoing consultations
```

---

## 9. UI Colors & Styling

### Choice Screen:
- Top bar: Purple gradient `#4C1D95 → #7C3AED` with "Follow-up Consultation" title
- Icon: Orange clock `#F59E0B` for expired/unavailable, Red X `#EF4444` for rejected
- "Book Appointment" button: Outlined, BrandTeal `#2A9D8F` border
- "Request Another Doctor" button: Filled BrandTeal `#2A9D8F`
- "Go Back": Small text button, gray
- Prompt text: "The doctor is not available. What would you like to do?" in black

### Find Doctor (Substitute Mode):
- Same as normal Find Doctor screen
- Price shows 0 or "Free" since it's a follow-up
- Doctor cards look the same

---

## 10. Testing Checklist

- [ ] Doctor is offline → follow-up request immediately shows choice (no 60s wait)
- [ ] Doctor is online but doesn't respond → 60s countdown → choice appears
- [ ] Doctor rejects → choice appears
- [ ] "Book Appointment" navigates to booking with original doctor
- [ ] "Request Another Doctor" navigates to doctor list
- [ ] Substitute request includes `is_substitute_follow_up: true` and `original_doctor_id`
- [ ] Substitute doctor gets "Follow-up patient calling" notification
- [ ] Patient pays nothing for substitute consultation
- [ ] After substitute consultation completes, original doctor gets 60% earnings, substitute gets 40%
- [ ] Economy patients can only use 1 follow-up total (substitute counts)
- [ ] Royal patients can do unlimited follow-ups (substitute or normal)
- [ ] `original_doctor_id` must match parent consultation's doctor (server validates)
- [ ] Normal follow-up flow (doctor online + accepts) still works unchanged
- [ ] Normal consultation flow (non-follow-up) still works unchanged
