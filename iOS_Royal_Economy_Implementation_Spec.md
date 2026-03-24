# eSIRIPlus Royal & Economy Tier System - iOS Implementation Guide

This document describes the complete tier-based consultation system (Royal & Economy) and the follow-up consultation feature so the iOS team can implement identical functionality using the shared backend.

---

## 1. Overview

Patients choose a service tier before selecting a service. The tier affects pricing, follow-up privileges, and UI presentation.

| Aspect | Royal | Economy |
|--------|-------|---------|
| **Price** | Base fee x 10 | Base fee x 1 |
| **Follow-Up Window** | 14 days after completion | 14 days after completion |
| **Max Follow-Ups** | Unlimited (free) | 1 (free) |
| **Card Color** | Purple gradient + gold accents | Blue header / BrandTeal outline |
| **Follow-Up Label** | "Follow-up window open (unlimited)" | "Follow-up (1 free)" |

---

## 2. Complete Navigation Flow

```
Patient Home → "Start Consultation"
    ↓
[1] Tier Selection Screen
    ├── Select Royal → ServiceLocation(tier="ROYAL")
    └── Select Economy → ServiceLocation(tier="ECONOMY")
    ↓
[2] Service Location Screen
    ├── Inside Tanzania → Services(tier=...)
    └── Outside Tanzania → "Not available" dialog
    ↓
[3] Services Screen (prices multiplied by tier)
    └── Select service → FindDoctor(category, price, duration, tier)
    ↓
[4] Find Doctor Screen
    └── Request consultation → Doctor receives request
    ↓
[5] Patient Consultation Chat Screen
    └── Doctor ends consultation → status = "completed"
    ↓
[6] Ongoing Consultations Screen (from Patient Home)
    └── Shows completed consultations with follow-up windows
    └── Tap follow-up eligible card → Confirmation dialog
    ↓
[7] Follow-Up Waiting Screen (60s countdown)
    └── Doctor accepts → New consultation chat
```

### Route Parameters:
```
TierSelectionRoute          (no params)
ServiceLocationRoute        (tier: String = "ECONOMY")
ServicesRoute               (tier: String = "ECONOMY")
FindDoctorRoute             (serviceCategory: String, servicePriceAmount: Int,
                             serviceDurationMinutes: Int, serviceTier: String = "ECONOMY")
FollowUpWaitingRoute        (parentConsultationId: String, doctorId: String, serviceType: String)
```

The `tier` / `serviceTier` parameter flows through every screen from Tier Selection to Find Doctor.

---

## 3. Screen-by-Screen Implementation

### 3.1 Tier Selection Screen

**Layout:**
```
[Back button]

"Choose Your Plan"

┌─────────────────────────────────────────┐
│ ROYAL TIER (Purple gradient bg)         │
│ ★ Royal                                  │
│                                          │
│ Benefits:                                │
│ • 10x premium consultation               │
│ • Unlimited follow-ups for 14 days       │
│ • Priority support                       │
│ • Same doctor continuity                 │
│ • No hidden fees                         │
│                                          │
│ [Select →]  (Gold button)                │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│ ECONOMY TIER (White bg, teal border)    │
│ Economy                                  │
│                                          │
│ Benefits:                                │
│ • Standard consultation                  │
│ • 1 free follow-up                       │
│ • Quality healthcare access              │
│                                          │
│ [Select →]  (BrandTeal button)           │
└─────────────────────────────────────────┘
```

**Colors:**
- Royal: Background gradient `#4C1D95 → #7C3AED`, Gold accent `#F59E0B`
- Economy: White background, BrandTeal `#2A9D8F` border and accents

### 3.2 Service Location Screen

Requests location permission. Shows two options:
- "Inside Tanzania" → navigates to Services with tier
- "Outside Tanzania" → shows "Coming soon" alert dialog

### 3.3 Services Screen

Displays available medical services. **Prices are multiplied by the tier.**

**Base Service Fees (TZS):**
| Service | Base Fee | Duration |
|---------|----------|----------|
| Nurse | 5,000 | 15 min |
| Clinical Officer | 7,000 | 15 min |
| Pharmacist | 3,000 | 5 min |
| GP | 10,000 | 15 min |
| Specialist | 30,000 | 20 min |
| Psychologist | 50,000 | 30 min |

**Effective Price Calculation:**
```
effectivePrice = basePrice × tierMultiplier
  Royal:   tierMultiplier = 10
  Economy: tierMultiplier = 1

Example: GP Royal = 10,000 × 10 = 100,000 TZS
Example: GP Economy = 10,000 × 1 = 10,000 TZS
```

The `effectivePrice` is displayed on screen and passed to FindDoctorRoute as `servicePriceAmount`.

---

## 4. Consultation Request API

### Create Request:
```
POST {SUPABASE_URL}/functions/v1/handle-consultation-request
Authorization: Bearer {SUPABASE_ANON_KEY}
X-Patient-Token: {patient_session_jwt}
Content-Type: application/json

{
  "action": "create",
  "doctor_id": "uuid",
  "service_type": "gp",
  "service_tier": "ROYAL",
  "consultation_type": "chat",
  "chief_complaint": "General consultation",
  "symptoms": "headache",
  "patient_age_group": "25-34",
  "patient_sex": "male",
  "patient_blood_group": null,
  "patient_allergies": null,
  "patient_chronic_conditions": null,
  "is_follow_up": false,
  "parent_consultation_id": null,
  "agent_id": null
}
```

**IMPORTANT:** `service_tier` must be sent as uppercase: `"ROYAL"` or `"ECONOMY"`.

### Response (201):
```json
{
  "request_id": "uuid",
  "status": "pending",
  "created_at": "2026-03-24T10:30:00.000Z",
  "expires_at": "2026-03-24T10:31:00.000Z",
  "ttl_seconds": 60
}
```

### Server-Side Fee Calculation:
The edge function calculates the actual `consultation_fee` stored in the DB:
```typescript
const baseFee = SERVICE_FEES[request.service_type] ?? 5000;
const tierMultiplier = service_tier === "ROYAL" ? 10 : 1;
const consultationFee = isFollowUp ? 0 : baseFee * tierMultiplier;
```

The iOS app does NOT send the fee — the server calculates it from `service_type` + `service_tier`.

---

## 5. Follow-Up Consultation System

### 5.1 How Follow-Up Expiry is Set

When the doctor ends a consultation, the `manage-consultation` edge function stamps `follow_up_expiry`:

```typescript
// Server sets 14-day window for ALL tiers
await supabase
  .from("consultations")
  .update({ follow_up_expiry: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString() })
  .eq("consultation_id", consultation_id)
  .is("follow_up_expiry", null);
```

**On the client side**, when the patient receives the "completed" realtime event:
1. Do a final sync: `POST manage-consultation { action: "sync", consultation_id }` — the response includes `doctor_id`, `service_type`, `service_tier`, `status`
2. Update local DB with `status = "completed"` and `service_tier` from the sync
3. Set `follow_up_expiry` locally (14 days from now) for ALL completed consultations — the local DB query filters by tier

### 5.2 Ongoing Consultations Screen

**Local DB Query:**
```sql
SELECT * FROM consultations
WHERE patientSessionId = :currentSessionId
  AND (
    LOWER(status) IN ('active', 'in_progress', 'awaiting_extension', 'grace_period')
    OR (LOWER(status) = 'completed' AND followUpExpiry > :currentTimeMillis)
  )
ORDER BY updatedAt DESC
```

This returns BOTH active consultations AND completed consultations with valid follow-up windows (any tier).

**Getting the Session ID:**
Extract from the patient JWT payload: `session_id` claim (base64-decode the JWT payload).

**Resolving Doctor Name:**
Look up `doctor_id` from the consultation in the local doctor profiles cache.

### Card Display Logic:
```swift
let isFollowUpEligible = consultation.status == "completed"
    && consultation.followUpExpiry > Date().timeIntervalSince1970 * 1000

let isRoyal = consultation.serviceTier.uppercased() == "ROYAL"
```

**Card Layout:**
```
┌─────────────────────────────────────────────┐
│ [Purple gradient band] (Royal)              │
│  "Follow-up window open (unlimited)" "14d"  │
├─────────────────────────────────────────────┤
│ ● Dr. John Smith                            │
│   Gp                                        │
│   Mar 24, 2026 · 10:30 AM                   │
│   Follow-up window open (unlimited)    ★Royal│
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│ [Blue gradient band] (Economy)              │
│  "Follow-up (1 free)"              "14d"    │
├─────────────────────────────────────────────┤
│ ● Dr. Jane Doe                              │
│   Specialist                                │
│   Mar 24, 2026 · 11:00 AM                   │
│   Follow-up window open (1 free)   Economy  │
└─────────────────────────────────────────────┘
```

**Colors:**
- Royal band: `#4C1D95 → #7C3AED`, label in gold `#F59E0B`
- Economy band: `#0D6EFD → #3B82F6`, label in white
- Royal status dot: gold `#F59E0B`
- Economy status dot: blue `#0D6EFD`
- Royal badge: gold text on purple-tinted bg
- Economy badge: blue text on blue-tinted bg

### 5.3 Tapping a Follow-Up Card

1. Show confirmation dialog: "Request a follow-up consultation with Dr. {name}?"
2. On confirm, navigate to Follow-Up Waiting screen with:
   - `parentConsultationId` = the completed consultation's ID
   - `doctorId` = the original doctor's ID
   - `serviceType` = the original service type

### 5.4 Follow-Up Request API

```
POST {SUPABASE_URL}/functions/v1/handle-consultation-request
Authorization: Bearer {SUPABASE_ANON_KEY}
X-Patient-Token: {patient_session_jwt}
Content-Type: application/json

{
  "action": "create",
  "doctor_id": "original-doctor-uuid",
  "service_type": "gp",
  "service_tier": "ROYAL",
  "consultation_type": "chat",
  "chief_complaint": "Follow-up consultation",
  "is_follow_up": true,
  "parent_consultation_id": "original-consultation-uuid"
}
```

### Server Validation for Follow-Ups:
1. Parent consultation exists and belongs to this patient
2. Parent status = "completed"
3. `follow_up_expiry` > now (14-day window not expired)
4. Doctor matches the parent consultation's doctor
5. **Economy only:** count of existing consultations with this `parent_consultation_id` must be < 1

### Follow-Up Consultation Fee:
**Always 0** (free). The server sets `consultation_fee = 0` for follow-ups. No payment needed.

### 5.5 Follow-Up Waiting Screen

**Layout:**
```
[Purple gradient top bar]
← Follow-up Consultation

[State-dependent content:]

SENDING:
  [Spinner] "Requesting follow-up consultation..."

WAITING (60s countdown):
  [Large circular progress - 120dp]
    {seconds}s
  "Waiting for your doctor to respond..."
  "{seconds} seconds remaining"
  [Cancel] (gray button)

ACCEPTED:
  [Green checkmark circle]
  "Follow-up accepted!"
  → Auto-navigate to consultation chat

REJECTED:
  [Red X circle]
  "Doctor declined the follow-up request."
  [Go Back]

EXPIRED:
  [Orange clock circle]
  "Doctor did not respond within 60 seconds."
  [Go Back]

ERROR:
  [Red X circle]
  "{error message from server}"
  [Go Back]
```

### 5.6 Realtime + Polling

**Realtime subscription:** Subscribe to `consultation_requests` table changes filtered by `patient_session_id`.

**Events to handle:**
- `status = "accepted"` + `consultation_id` → navigate to chat
- `status = "rejected"` → show rejected state
- `status = "expired"` → show expired state

**Polling fallback:** Every 3 seconds, call:
```json
{
  "action": "status",
  "request_id": "uuid"
}
```
Response includes `status` and `consultation_id` (when accepted).

---

## 6. Sync Response Contract

The `manage-consultation` sync response now includes `doctor_id` and `service_tier`:

```
POST {SUPABASE_URL}/functions/v1/manage-consultation
Authorization: Bearer {SUPABASE_ANON_KEY}
X-Patient-Token: {patient_session_jwt}

{ "action": "sync", "consultation_id": "uuid" }
```

**Response:**
```json
{
  "consultation_id": "uuid",
  "doctor_id": "uuid",
  "status": "active",
  "service_type": "gp",
  "service_tier": "ROYAL",
  "consultation_fee": 100000,
  "scheduled_end_at": "2026-03-24T10:45:00.000Z",
  "extension_count": 0,
  "grace_period_end_at": null,
  "original_duration_minutes": 15,
  "session_start_time": "2026-03-24T10:30:00.000Z",
  "server_time": "2026-03-24T10:31:00.000Z"
}
```

**Critical:** Store `doctor_id`, `service_type`, and `service_tier` from the sync response in local DB. These are needed later for the Ongoing Consultations screen and follow-up requests.

---

## 7. Database Schema (Supabase - Already Deployed)

### consultation_requests table additions:
```sql
service_tier        TEXT NOT NULL DEFAULT 'ECONOMY'
is_follow_up        BOOLEAN NOT NULL DEFAULT FALSE
parent_consultation_id  UUID REFERENCES consultations(consultation_id)
agent_id            UUID REFERENCES auth.users(id)
```

### consultations table additions:
```sql
service_tier            TEXT NOT NULL DEFAULT 'ECONOMY'
parent_consultation_id  UUID REFERENCES consultations(consultation_id)
follow_up_expiry        TIMESTAMPTZ
agent_id                UUID REFERENCES auth.users(id)
```

---

## 8. Doctor Earnings Trigger

When consultation completes with `consultation_fee > 0`:
- Doctor gets `FLOOR(fee × doctor_split_pct / 100)` (default 50%)
- If `agent_id` is set: agent gets `FLOOR(fee × 10 / 100)` (10%)
- Follow-ups (`fee = 0`): no earnings for either

---

## 9. Auth Headers Pattern

**For ALL edge function calls from iOS:**

```
// Patient consultation calls:
Authorization: Bearer {SUPABASE_ANON_KEY}
X-Patient-Token: {patient_session_jwt}

// Anonymous calls (create-patient-session):
Authorization: Bearer {SUPABASE_ANON_KEY}
// No custom headers
```

Detect token type by decoding JWT payload:
- If `app_role == "patient"` or `role == "patient"` → use `X-Patient-Token`
- Otherwise → use `X-Doctor-Token`

The Supabase gateway JWT validation is **disabled** (`--no-verify-jwt`). Our `validateAuth()` in edge functions reads from custom headers.

---

## 10. Testing Checklist

### Tier Selection:
- [ ] Royal card shows purple gradient with gold accents
- [ ] Economy card shows white with teal border
- [ ] Selecting tier passes it through all subsequent screens

### Pricing:
- [ ] Services screen shows base price × tier multiplier
- [ ] GP Economy = 10,000 TZS; GP Royal = 100,000 TZS
- [ ] Server-calculated `consultation_fee` matches displayed price

### Follow-Up (Royal):
- [ ] Completed Royal consultation appears in Ongoing Consultations
- [ ] Shows "Follow-up window open (unlimited)" with days remaining
- [ ] Tapping shows confirmation dialog with doctor name
- [ ] Follow-up request sends `is_follow_up: true` + `parent_consultation_id`
- [ ] 60s countdown + realtime acceptance works
- [ ] New consultation has `consultation_fee = 0`
- [ ] Can do unlimited follow-ups within 14 days

### Follow-Up (Economy):
- [ ] Completed Economy consultation appears in Ongoing Consultations
- [ ] Shows "Follow-up (1 free)"
- [ ] First follow-up succeeds
- [ ] Second follow-up is rejected: "Economy consultations are limited to 1 follow-up"

### Edge Cases:
- [ ] Follow-up after 14 days is rejected: "Follow-up window has expired"
- [ ] Follow-up with wrong doctor is rejected
- [ ] Follow-up on non-completed consultation is rejected
- [ ] Normal patient (non-agent) does NOT send `agent_id`
