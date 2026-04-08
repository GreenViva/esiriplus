# eSIRI Plus — Agent Authentication Specification (for PWA Development)

This document describes how agents sign up and sign in, from UI layout to backend connectivity. The PWA must replicate this exactly.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Screen Layout](#2-screen-layout)
3. [Sign In Flow](#3-sign-in-flow)
4. [Sign Up Flow (2-Step with OTP)](#4-sign-up-flow-2-step-with-otp)
5. [Forgot Password](#5-forgot-password)
6. [Token Storage & Session](#6-token-storage--session)
7. [Edge Function Reference](#7-edge-function-reference)
8. [Database Tables](#8-database-tables)

---

## 1. Overview

eSIRI Plus Agents are independent referral partners who earn commissions by connecting patients with doctors. Agents have their own portal, separate from doctors and patients.

**Key differences from doctor auth:**
- Agents use `agent_profiles` table (not `doctor_profiles`)
- Agents use `login-agent` and `register-agent` edge functions
- Sign-up requires email OTP verification before account creation
- Agents have a simpler profile: name, mobile (for payments), email, place of residence
- All auth calls use `anonymous = true` (anon key in Authorization header)

**Shared with doctors:**
- Same Supabase Auth system (`auth.users`)
- Same `user_roles` table (role: `"agent"`)
- Same `email_verifications` table for OTP
- Same `reset-password` edge function for forgot password
- Same token format (Supabase Auth JWT)

---

## 2. Screen Layout

### Entry Point
From the role selection / login page, the agent section appears below the doctor portal:

```
┌──────────────────────────────────────┐
│  ┌──────────────────────────────┐    │
│  │ [e+]  eSIRIPlus Agents    → │    │  ← Amber/orange gradient badge
│  │       Earn money by          │    │
│  │       becoming an agent      │    │
│  └──────────────────────────────┘    │
└──────────────────────────────────────┘
```

### Agent Auth Screen

```
┌──────────────────────────────────────┐
│  ← eSIRIPlus Agent                   │  ← Top bar with back button
├──────────────────────────────────────┤
│                                      │
│            ┌──────┐                  │
│            │ e+   │                  │  ← 72dp circle, amber→orange gradient
│            └──────┘                  │
│                                      │
│        eSIRIPlus Agents              │  ← 22sp Bold
│   Earn money by helping patients     │  ← 14sp, centered
│        access healthcare             │
│                                      │
│  ┌──────────┬──────────┐            │
│  │ Sign In  │ Sign Up  │            │  ← Tab row with teal indicator
│  └──────────┴──────────┘            │
│                                      │
│  (tab content below)                 │
│                                      │
└──────────────────────────────────────┘
```

### Colors
| Element | Color |
|---------|-------|
| Agent badge circle | Linear gradient: `#F59E0B` → `#EF6C00` |
| Tab indicator | BrandTeal `#2A9D8F` |
| Selected tab text | BrandTeal, Bold |
| Unselected tab text | Black at 60% opacity |
| Text field borders (focused) | BrandTeal |
| Sign In / Sign Up buttons | BrandTeal |
| Mobile number hint | Amber `#B45309` |
| Error messages | Red |

---

## 3. Sign In Flow

### Layout

```
┌──────────────────────────────────────┐
│  📧 Email                            │  ← OutlinedTextField with email icon
│  ┌────────────────────────────────┐  │
│  │ agent@example.com              │  │
│  └────────────────────────────────┘  │
│                                      │
│  🔒 Password                         │  ← OutlinedTextField with lock icon
│  ┌────────────────────────────────┐  │
│  │ ••••••••                       │  │
│  └────────────────────────────────┘  │
│                                      │
│              Forgot Password? →      │  ← Right-aligned, BrandTeal, 13sp
│                                      │
│  ┌────────────────────────────────┐  │
│  │          Sign In               │  │  ← BrandTeal button, 52dp, 12dp radius
│  └────────────────────────────────┘  │
└──────────────────────────────────────┘
```

### API Call

**Edge function:** `login-agent`
**Auth:** Anonymous

**Request:**
```json
{
  "email": "agent@example.com",
  "password": "password123"
}
```

**Success Response:**
```json
{
  "access_token": "eyJ...",
  "refresh_token": "abc123...",
  "expires_at": 1775645620,
  "expires_in": 3600,
  "user": {
    "id": "uuid",
    "email": "agent@example.com",
    "full_name": "John Agent",
    "role": "AGENT"
  }
}
```

**Error Response:**
```json
{ "error": "Invalid credentials", "code": "VALIDATION_ERROR" }
{ "error": "No agent account found for this email. Please register first.", "code": "VALIDATION_ERROR" }
{ "error": "Your agent account has been deactivated. Contact support.", "code": "VALIDATION_ERROR" }
```

### Post-Login
1. Store `access_token` and `refresh_token` (localStorage for PWA)
2. Store agent name and ID in preferences
3. Navigate to the agent dashboard

---

## 4. Sign Up Flow (2-Step with OTP)

### Step 1: Agent Details

Shows a step indicator: `● 1 ── ○ 2` with label "Step 1: Your Details"

```
┌──────────────────────────────────────┐
│        ● 1 ────── ○ 2               │  ← Step indicator
│        Step 1: Your Details          │
│                                      │
│  👤 Full Name                        │
│  ┌────────────────────────────────┐  │
│  │ John Doe                       │  │
│  └────────────────────────────────┘  │
│                                      │
│  📞 Mobile Number                    │
│  ┌────────────────────────────────┐  │
│  │ 0712345678                     │  │
│  └────────────────────────────────┘  │
│  ⚠ Use a valid mobile money number  │  ← Amber warning text (11sp)
│    — this will be used for payments  │
│                                      │
│  📧 Email                            │
│  ┌────────────────────────────────┐  │
│  │ agent@example.com              │  │
│  └────────────────────────────────┘  │
│                                      │
│  📍 Place of Residence               │
│  ┌────────────────────────────────┐  │
│  │ Dar es Salaam                  │  │
│  └────────────────────────────────┘  │
│                                      │
│  🔒 Password                         │
│  ┌────────────────────────────────┐  │
│  │ ••••••••                       │  │
│  └────────────────────────────────┘  │
│                                      │
│  ┌────────────────────────────────┐  │
│  │          Sign Up               │  │  ← BrandTeal button
│  └────────────────────────────────┘  │
└──────────────────────────────────────┘
```

### Form Fields

| Field | Icon | Keyboard | Required | Validation |
|-------|------|----------|----------|------------|
| Full Name | Person | Text | Yes | Non-blank |
| Mobile Number | Phone | Phone | Yes | Non-blank (mobile money number) |
| Email | Email | Email | Yes | Must contain @ |
| Place of Residence | Place | Text | Yes | Non-blank |
| Password | Lock | Password | Yes | Min 6 characters |

### What Happens on "Sign Up"

1. Client validates all fields are non-blank
2. Stores all fields in pending state
3. Calls `send-doctor-otp` (same OTP system as doctor registration)
4. Advances to Step 2

### Step 2: Email OTP Verification

Step indicator: `● 1 ────── ● 2` with label "Step 2: Verify Email"

```
┌──────────────────────────────────────┐
│        ● 1 ────── ● 2               │
│        Step 2: Verify Email          │
│                                      │
│  A verification code was sent to:    │
│  agent@example.com                   │
│                                      │
│  ┌────────────────────────────────┐  │
│  │ Enter 6-digit code             │  │
│  └────────────────────────────────┘  │
│                                      │
│  ┌────────────────────────────────┐  │
│  │         Verify Code            │  │  ← BrandTeal button
│  └────────────────────────────────┘  │
│                                      │
│  Resend Code (45s)                   │  ← Disabled during cooldown
│                                      │
│  ← Back to details                   │  ← Returns to Step 1
└──────────────────────────────────────┘
```

### OTP Flow

**Send OTP:**
```
POST /functions/v1/send-doctor-otp (anonymous)
{ "email": "agent@example.com" }
→ { "sent": true }
```

**Verify OTP:**
```
POST /functions/v1/verify-doctor-otp (anonymous)
{ "email": "agent@example.com", "otp_code": "482951" }
→ { "verified": true }
```

- OTP is 6 digits, expires in 10 minutes
- Max 5 wrong attempts per OTP
- Resend cooldown: 60 seconds
- On verification success → automatically calls `register-agent`

### Account Creation (after OTP verified)

**Edge function:** `register-agent`
**Auth:** Anonymous

**Request:**
```json
{
  "full_name": "John Doe",
  "mobile_number": "0712345678",
  "email": "agent@example.com",
  "place_of_residence": "Dar es Salaam",
  "password": "password123"
}
```

**Server-side steps:**
1. Check email not already registered in `agent_profiles`
2. Create Supabase Auth user (`auth.admin.createUser` with `email_confirm: true`)
3. Insert into `agent_profiles` (agent_id, full_name, mobile_number, email, place_of_residence)
4. Insert into `user_roles` (user_id, role_name: "agent")
5. Sign in automatically to get session tokens

**Success Response:**
```json
{
  "access_token": "eyJ...",
  "refresh_token": "abc123...",
  "expires_at": 1775645620,
  "expires_in": 3600,
  "user": {
    "id": "uuid",
    "email": "agent@example.com",
    "full_name": "John Doe",
    "role": "AGENT"
  }
}
```

**Error Response:**
```json
{ "error": "An agent with this email already exists", "code": "VALIDATION_ERROR" }
{ "error": "Password must be at least 6 characters", "code": "VALIDATION_ERROR" }
```

---

## 5. Forgot Password

Same 3-step OTP flow as doctors. See `FORGOT_PASSWORD_SPEC.md` for full details.

**Entry point:** "Forgot Password?" link on the Sign In tab, right-aligned below the password field.

Uses the same `reset-password` edge function. The function looks up the email in `doctor_profiles` — but agents are also stored in `doctor_profiles` if they have a doctor account, OR the `reset-password` function should be updated to also check `agent_profiles`.

**Note:** If agents are NOT in `doctor_profiles`, the PWA needs to update the `reset-password` edge function to also check `agent_profiles`:

```typescript
// Check both tables
let doctorId = null;
const { data: doctor } = await supabase.from("doctor_profiles").select("doctor_id").eq("email", email).maybeSingle();
if (doctor) doctorId = doctor.doctor_id;
if (!doctorId) {
  const { data: agent } = await supabase.from("agent_profiles").select("agent_id").eq("email", email).maybeSingle();
  if (agent) doctorId = agent.agent_id;
}
```

---

## 6. Token Storage & Session

### After Login/Registration
```javascript
// Store tokens
localStorage.setItem('access_token', response.access_token);
localStorage.setItem('refresh_token', response.refresh_token);
localStorage.setItem('expires_at', response.expires_at);
localStorage.setItem('agent_name', response.user.full_name);
localStorage.setItem('agent_id', response.user.id);
localStorage.setItem('is_agent', 'true');
```

### Session Check on App Start
```javascript
const agentName = localStorage.getItem('agent_name');
const hasToken = !!localStorage.getItem('access_token');
if (agentName && hasToken) {
  // Agent is logged in → navigate to agent dashboard
}
```

### Token Refresh
Agents use standard Supabase Auth tokens — refresh via:
```
POST /auth/v1/token?grant_type=refresh_token
{ "refresh_token": "stored_refresh_token" }
```

---

## 7. Edge Function Reference

| Function | Auth | Description |
|----------|------|-------------|
| `login-agent` | Anonymous | Sign in with email + password |
| `register-agent` | Anonymous | Create account (after OTP verification) |
| `send-doctor-otp` | Anonymous | Send 6-digit OTP to email (shared with doctors) |
| `verify-doctor-otp` | Anonymous | Verify 6-digit OTP (shared with doctors) |
| `reset-password` | Anonymous | 3-step forgot password (shared with doctors) |

All calls use:
```
Authorization: Bearer <SUPABASE_ANON_KEY>
Content-Type: application/json
```

---

## 8. Database Tables

### `agent_profiles`
```sql
id                  SERIAL PRIMARY KEY
agent_id            UUID NOT NULL REFERENCES auth.users(id)
full_name           TEXT NOT NULL
mobile_number       TEXT NOT NULL        -- mobile money number for payments
email               TEXT NOT NULL UNIQUE
place_of_residence  TEXT NOT NULL
is_active           BOOLEAN DEFAULT true
created_at          TIMESTAMPTZ DEFAULT now()
```

### `user_roles` (shared)
```sql
user_id    UUID REFERENCES auth.users(id)
role_name  TEXT  -- "agent" for agents, "doctor" for doctors
```

### `email_verifications` (shared)
```sql
id          UUID PRIMARY KEY
email       TEXT NOT NULL
otp_code    TEXT NOT NULL       -- 6-digit code
attempts    INTEGER DEFAULT 0   -- max 5
verified_at TIMESTAMPTZ         -- NULL until verified
expires_at  TIMESTAMPTZ         -- 10 minutes from creation
created_at  TIMESTAMPTZ DEFAULT now()
```

---

## Step Indicator Component

The sign-up flow shows a step indicator with numbered circles connected by a line:

```
● 1 ────── ○ 2      (Step 1 active)
● 1 ────── ● 2      (Step 2 active)
```

| Element | Active State | Inactive State |
|---------|-------------|----------------|
| Circle | BrandTeal `#2A9D8F`, white text | Gray `#E5E7EB`, gray text |
| Line | BrandTeal (if step completed) | Gray `#E5E7EB` |
| Size | 28dp circle, 40dp × 2dp line | Same |
| Text | 13sp Bold | 13sp Bold at 50% opacity |

Step labels:
- Step 1: "Step 1: Your Details"
- Step 2: "Step 2: Verify Email"

---

## OTP Email Template

Same template as doctor registration — sent from `noreply@esiri.africa` via Resend:

```
Subject: 482951 is your eSIRI Plus verification code

Body: eSIRI Plus branding + 6-digit code in 32px bold BrandTeal + 10 min expiry notice
```

See `FORGOT_PASSWORD_SPEC.md` for the full email HTML template.

---

## PWA Implementation Summary

```javascript
// Sign In
const res = await fetch(`${BASE_URL}/login-agent`, {
  method: 'POST',
  headers: { 'Authorization': `Bearer ${ANON_KEY}`, 'Content-Type': 'application/json' },
  body: JSON.stringify({ email, password }),
});

// Sign Up - Step 1: Send OTP
await fetch(`${BASE_URL}/send-doctor-otp`, {
  method: 'POST',
  headers: { 'Authorization': `Bearer ${ANON_KEY}`, 'Content-Type': 'application/json' },
  body: JSON.stringify({ email }),
});

// Sign Up - Step 2: Verify OTP
await fetch(`${BASE_URL}/verify-doctor-otp`, {
  method: 'POST',
  headers: { 'Authorization': `Bearer ${ANON_KEY}`, 'Content-Type': 'application/json' },
  body: JSON.stringify({ email, otp_code: userInput }),
});

// Sign Up - Step 3: Create Account
const res = await fetch(`${BASE_URL}/register-agent`, {
  method: 'POST',
  headers: { 'Authorization': `Bearer ${ANON_KEY}`, 'Content-Type': 'application/json' },
  body: JSON.stringify({ full_name, mobile_number, email, place_of_residence, password }),
});
```
