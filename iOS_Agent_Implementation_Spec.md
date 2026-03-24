# eSIRIPlus Agent System - iOS Implementation Guide

This document describes the complete Agent system so the iOS team can implement identical functionality without breaking the shared backend.

---

## 1. Overview

**What is an Agent?** An intermediary who helps patients access eSIRIPlus. Agents earn 10% commission on every consultation they facilitate. They authenticate with email/password (Supabase Auth), create patient sessions on behalf of clients, and guide them through the consultation flow.

**Key Principle:** The backend (edge functions, database triggers, RLS policies) is already deployed and shared between Android and iOS. The iOS app only needs to call the same APIs with the same JSON contracts.

---

## 2. Where the Button Lives

The "eSIRIPlus Agents" button is on the **Welcome/Role Selection screen** (the first screen after language selection). It sits below the Doctor Portal section, separated by an "or" divider.

### Layout (Role Selection Screen):
```
[App Logo]
"Welcome to eSIRI Plus"
"Your health companion"

── FOR PATIENTS ──
[Are you new? → Patient setup]
[I have my ID → Access records]
"Forgot your Patient ID?"

── or ──

── DOCTOR PORTAL ──
[Sign In] [Sign Up]
"Forgot your password?"

── or ──

[eSIRIPlus Agents card]          ← NEW
  Icon: orange gradient circle with "e+" text
  Title: "eSIRIPlus Agents"
  Subtitle: "Earn money by becoming an eSIRIPlus agent"
  Arrow icon on right

[Footer: Copyright]
```

Tapping the card navigates to the Agent Auth screen.

---

## 3. Agent Auth Screen (Sign In / Sign Up)

A single screen with two tabs: **Sign In** and **Sign Up**.

### Sign Up Tab - Fields:
| Field | Type | Validation | Hint |
|-------|------|------------|------|
| Full Name | Text | Required, non-empty | — |
| Mobile Number | Phone keyboard | Required, non-empty | "Use a valid mobile money number — this will be used for payments" (amber text below field) |
| Email | Email keyboard | Required, valid email | — |
| Place of Residence | Text | Required, non-empty | — |
| Password | Password (obscured) | Required, min 6 chars | — |

### Sign In Tab - Fields:
| Field | Type | Validation |
|-------|------|------------|
| Email | Email keyboard | Required, valid email |
| Password | Password (obscured) | Required, non-empty |

### Sign Up API Call:
```
POST {SUPABASE_URL}/functions/v1/register-agent
Authorization: Bearer {SUPABASE_ANON_KEY}
Content-Type: application/json

{
  "full_name": "John Doe",
  "mobile_number": "+255712345678",
  "email": "john@example.com",
  "place_of_residence": "Dar es Salaam",
  "password": "secret123"
}
```

**IMPORTANT:** This is an anonymous call. Use the Supabase anon key as the Bearer token, NOT a user JWT. No `X-Patient-Token` or `X-Doctor-Token` headers needed.

### Sign Up Response (201):
```json
{
  "access_token": "eyJhbG...",
  "refresh_token": "abc123...",
  "expires_at": 1774342350,
  "expires_in": 3600,
  "user": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "email": "john@example.com",
    "full_name": "John Doe",
    "role": "AGENT"
  }
}
```

### Sign In API Call:
```
POST {SUPABASE_URL}/functions/v1/login-agent
Authorization: Bearer {SUPABASE_ANON_KEY}
Content-Type: application/json

{
  "email": "john@example.com",
  "password": "secret123"
}
```

### Sign In Response (200):
Same format as sign up response.

### Error Response (400):
```json
{
  "error": "An agent with this email already exists",
  "code": "VALIDATION_ERROR"
}
```

### After Successful Auth:
1. Store `access_token` and `refresh_token` in secure storage (Keychain)
2. Store `user.id` as `agent_id` in local persistent storage (UserDefaults/Keychain)
3. Store `user.full_name` as `agent_name`
4. Set a flag `is_agent = true`
5. Navigate to **Agent Dashboard**

---

## 4. Agent Dashboard

Simple screen with agent greeting and two action cards.

### Layout:
```
[Top Bar: "Agent Dashboard"]

[Avatar circle with initial] Welcome back,
                             {agent_name}

[Start Consultation]         ← Orange gradient icon
  "Help a patient start a new consultation"

[Finished Consultations]     ← Gray gradient icon
  "View completed consultation history"

                    [Sign Out button - red]
```

### "Start Consultation" Flow:

**This is the critical part.** When the agent taps "Start Consultation":

1. **Create a patient session first.** The agent has a Supabase Auth JWT (like a doctor), but the consultation request edge function requires a patient session JWT. So:

```
POST {SUPABASE_URL}/functions/v1/create-patient-session
Authorization: Bearer {SUPABASE_ANON_KEY}
Content-Type: application/json

{
  "fcm_token": "optional-push-token"
}
```

This returns a patient session JWT. **Save the agent's tokens aside** before overwriting them with the patient session tokens.

2. **Store the patient session tokens** as the active tokens (so all subsequent edge function calls use the patient JWT).

3. **Navigate to the normal patient consultation flow** (Tier Selection → Service Location → Services → Find Doctor → Request Consultation).

4. The `agent_id` (from step 3 of auth) is stored locally and automatically attached to the consultation request (see Section 5).

### "Sign Out":
- Clear all agent tokens, agent_id, agent_name, is_agent flag
- Navigate back to the Role Selection screen

---

## 5. How agent_id Flows Through Consultation Creation

This is the key integration. When a consultation request is created, the `agent_id` must be included in the request body.

### Where to read agent_id:
From local storage (UserDefaults/Keychain) where it was saved during agent auth.

### Consultation Request API (already existing):
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
  "agent_id": "550e8400-e29b-41d4-a716-446655440000"   ← ADD THIS
}
```

**The `agent_id` field is optional.** If present, the backend:
1. Stores it on the `consultation_requests` row
2. Passes it to `consultations` when the doctor accepts
3. The DB trigger `fn_auto_create_agent_earning` fires when the consultation completes, calculating 10% commission and inserting into `agent_earnings`

**If `agent_id` is null/absent**, it's a normal patient consultation with no agent commission. This is backward-compatible — existing patient flows don't send it.

### When to include agent_id:
Check if `is_agent == true` AND `agent_id` is non-null in local storage. If yes, include it in every `create` request.

---

## 6. Token Management Strategy

The agent has TWO types of tokens at different times:

| Phase | Token Type | Where Used |
|-------|-----------|------------|
| Agent Dashboard | Supabase Auth JWT (agent) | Agent-specific calls (if any) |
| During Consultation | Patient Session JWT (custom) | All consultation edge functions |

### Token Swap Flow:
```
Agent Auth → Store agent tokens (Supabase Auth JWT)
           → Store agent_id in persistent local storage

"Start Consultation" tapped:
  1. Save agent tokens to separate keys (e.g., "saved_agent_access_token")
  2. Call create-patient-session (anonymous)
  3. Store patient session tokens as active tokens
  4. Navigate to consultation flow

Consultation ends / Agent returns to dashboard:
  1. Restore agent tokens from saved keys
  (Currently Android doesn't auto-restore — agent signs in again)
```

### Auth Headers for Edge Functions:

**For ALL edge function calls, use this pattern:**

```
Authorization: Bearer {SUPABASE_ANON_KEY}    ← Always the anon key
X-Patient-Token: {patient_jwt}               ← For patient session calls
X-Doctor-Token: {doctor_or_agent_jwt}        ← For doctor/agent calls
```

The Supabase gateway JWT validation is DISABLED (`--no-verify-jwt`). Our custom `validateAuth()` in the edge function reads the actual JWT from `X-Patient-Token` or `X-Doctor-Token` headers.

**Decision tree for which header to use:**
- If the stored token has `app_role: "patient"` or `role: "patient"` in its JWT payload → send via `X-Patient-Token`
- Otherwise (doctor, agent, or anon key) → send via `X-Doctor-Token`
- For anonymous calls (register-agent, login-agent, create-patient-session) → only `Authorization: Bearer {anon_key}`, no custom headers

---

## 7. Database Schema (Already Deployed)

### agent_profiles table:
```sql
id                  UUID PRIMARY KEY
agent_id            UUID NOT NULL REFERENCES auth.users(id)  -- UNIQUE
full_name           TEXT NOT NULL
mobile_number       TEXT NOT NULL
email               TEXT NOT NULL  -- UNIQUE
place_of_residence  TEXT NOT NULL
is_active           BOOLEAN DEFAULT true
created_at          TIMESTAMPTZ DEFAULT now()
updated_at          TIMESTAMPTZ DEFAULT now()
```

### agent_earnings table:
```sql
id                  UUID PRIMARY KEY
agent_id            UUID NOT NULL REFERENCES auth.users(id)
consultation_id     UUID NOT NULL REFERENCES consultations(consultation_id)  -- UNIQUE
amount              INTEGER NOT NULL CHECK (amount > 0)
status              TEXT DEFAULT 'pending' CHECK (status IN ('pending', 'paid', 'cancelled'))
created_at          TIMESTAMPTZ DEFAULT now()
updated_at          TIMESTAMPTZ DEFAULT now()
```

### Columns added to existing tables:
- `consultation_requests.agent_id` UUID (nullable)
- `consultations.agent_id` UUID (nullable)

### Auto-earnings trigger:
When `consultations.status` changes to `'completed'` AND `agent_id IS NOT NULL` AND `consultation_fee > 0`:
- Reads `agent_commission_pct` from `app_config` (default 10)
- Calculates `FLOOR(consultation_fee * pct / 100)`
- Inserts into `agent_earnings`

---

## 8. Consultation Fee Calculation

The edge function calculates fees based on tier:

```
Base fees (Economy):
  nurse: 5,000 TZS
  clinical_officer: 7,000 TZS
  pharmacist: 3,000 TZS
  gp: 10,000 TZS
  specialist: 30,000 TZS
  psychologist: 50,000 TZS

Royal multiplier: 10x
Economy multiplier: 1x

Example: GP Royal = 10,000 × 10 = 100,000 TZS
Agent commission (10%): 10,000 TZS
```

Follow-up consultations have `consultation_fee = 0` (free), so no agent commission is generated.

---

## 9. Follow-Up Consultations (Context)

If the iOS app also implements follow-ups:
- Royal: unlimited free follow-ups within 14 days
- Economy: 1 free follow-up within 14 days
- Follow-up requests include `is_follow_up: true` and `parent_consultation_id: "uuid"`
- Follow-up consultations have `consultation_fee = 0`
- The same `agent_id` from the original consultation does NOT auto-attach to follow-ups (the patient initiates follow-ups directly from Ongoing Consultations, not through the agent)

---

## 10. Edge Function Auth Pattern

ALL edge functions use `--no-verify-jwt` (gateway JWT validation disabled). Auth is handled by our shared `_shared/auth.ts` `validateAuth()` function:

```typescript
// Priority order:
1. X-Patient-Token header → Patient auth path (custom HS256 JWT)
2. X-Doctor-Token header → Doctor/Agent/Admin auth path (Supabase Auth JWT)
3. Authorization Bearer → Fallback (used by web admin panel)
```

For iOS:
- Patient calls: `Authorization: Bearer {anon_key}` + `X-Patient-Token: {patient_jwt}`
- Agent dashboard calls: `Authorization: Bearer {anon_key}` + `X-Doctor-Token: {agent_jwt}`
- Anonymous calls: `Authorization: Bearer {anon_key}` only

---

## 11. Error Handling

All edge functions return errors in this format:
```json
{
  "error": "Human-readable message",
  "code": "VALIDATION_ERROR" | "UNAUTHORIZED" | "RATE_LIMITED" | "INTERNAL_ERROR"
}
```

HTTP status codes:
- 400: Validation error
- 401: Auth error (expired/invalid token)
- 429: Rate limited (check Retry-After header)
- 500: Internal error

---

## 12. Admin Panel Integration

The admin panel (web, Next.js) already has an "eSIRI Agents" page that:
- Lists all agents with name, email, phone, residence, status
- Shows per-agent consultation count and total earnings
- Clicking an agent shows their consultations table with:
  - Date, Patient Session, Service, Tier, Fee, Agent 10% commission, Status
  - "Pay" button for each pending earning (marks status as 'paid')
- No iOS changes needed for admin panel

---

## 13. Testing Checklist

1. Agent can register with all fields
2. Agent can sign in with email/password
3. "Start Consultation" creates a patient session before entering flow
4. Consultation request includes `agent_id` in the JSON body
5. Doctor receives and accepts the request normally
6. After consultation completes, `agent_earnings` row is created with 10% of fee
7. Agent dashboard shows after auth (not patient home)
8. Sign out clears all agent data
9. Royal consultations show correct fee (10x base) and correct commission
10. Follow-up consultations have fee=0 and no agent commission
11. Normal patient flow (without agent) does NOT include agent_id
