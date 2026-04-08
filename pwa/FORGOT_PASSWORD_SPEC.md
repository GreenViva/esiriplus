# eSIRI Plus — Forgot Password (OTP-Based) Specification (for PWA Development)

This document describes the OTP-based password reset flow for doctors and agents. The PWA must replicate this exactly.

---

## Overview

A 3-step flow: **Email → OTP → New Password**. No magic links, no redirects — everything happens in a single dialog/modal without leaving the page.

Used on both the **Doctor Login** screen and the **Agent Login** screen. The flow is identical — same edge function, same OTP table, same password update RPC. Agents are stored in the same `doctor_profiles` table and use the same `auth.users` accounts as doctors. No separate implementation needed — the PWA just needs one forgot-password component reused on both login pages.

---

## Edge Function: `reset-password`

**URL:** `POST /functions/v1/reset-password`
**Auth:** Anonymous (`Authorization: Bearer <anon_key>`)
**Rate limits:** 10/min per IP for send, 5 per 5 min per email, 20/min for verify/set

### Action: `send_otp`

Sends a 6-digit OTP to the doctor/agent's email.

**Request:**
```json
{
  "action": "send_otp",
  "email": "doctor@example.com"
}
```

**Response (always 200 — prevents email enumeration):**
```json
{ "sent": true }
```

**Server-side:**
1. Checks `doctor_profiles` for a matching email
2. If not found → returns `{ sent: true }` anyway (don't reveal)
3. Invalidates any previous unexpired OTPs for this email
4. Generates 6-digit OTP, stores in `email_verifications` table (10 min expiry)
5. Sends OTP email via Resend API

### Action: `verify_otp`

Verifies the 6-digit code.

**Request:**
```json
{
  "action": "verify_otp",
  "email": "doctor@example.com",
  "otp_code": "482951"
}
```

**Success:**
```json
{ "verified": true }
```

**Failure examples:**
```json
{ "error": "Incorrect code. 3 attempts remaining.", "code": "VALIDATION_ERROR" }
{ "error": "Too many attempts. Please request a new code.", "code": "VALIDATION_ERROR" }
{ "error": "No pending verification found. Please request a new code.", "code": "VALIDATION_ERROR" }
```

**Server-side:**
- Looks up latest unverified, unexpired OTP for the email
- Max 5 attempts per OTP (increments counter on each wrong attempt)
- On match → marks `verified_at = NOW()`

### Action: `set_password`

Sets the new password after OTP verification.

**Request:**
```json
{
  "action": "set_password",
  "email": "doctor@example.com",
  "new_password": "MyNewPassword123."
}
```

**Success:**
```json
{ "reset": true }
```

**Failure:**
```json
{ "error": "OTP verification expired. Please start over.", "code": "VALIDATION_ERROR" }
{ "error": "Password must be at least 6 characters", "code": "VALIDATION_ERROR" }
```

**Server-side:**
1. Checks for a verified OTP within the last 15 minutes for this email
2. Finds the doctor's `auth.users` ID via `doctor_profiles`
3. Updates password via PostgreSQL RPC: `reset_doctor_password(doctor_id, new_password)` which calls `crypt(password, gen_salt('bf'))`
4. Invalidates the used verification record

---

## Database

### `email_verifications` Table (existing, shared with registration OTP)
```sql
id          UUID PRIMARY KEY DEFAULT gen_random_uuid()
email       TEXT NOT NULL
otp_code    TEXT NOT NULL          -- 6-digit string
attempts    INTEGER DEFAULT 0      -- wrong attempt counter (max 5)
verified_at TIMESTAMPTZ            -- NULL until verified
expires_at  TIMESTAMPTZ NOT NULL   -- 10 minutes from creation
created_at  TIMESTAMPTZ DEFAULT now()
```

### `reset_doctor_password` RPC Function
```sql
CREATE OR REPLACE FUNCTION reset_doctor_password(p_doctor_id UUID, p_new_password TEXT)
RETURNS VOID AS $$
BEGIN
  UPDATE auth.users
  SET encrypted_password = crypt(p_new_password, gen_salt('bf'))
  WHERE id = p_doctor_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
```

---

## UI Flow

### Step 1: Enter Email

```
┌──────────────────────────────────────┐
│  Reset Password                      │
│                                      │
│  Enter your email and we'll send     │
│  you a verification code.            │
│                                      │
│  ┌────────────────────────────────┐  │
│  │ doctor@example.com             │  │
│  └────────────────────────────────┘  │
│                                      │
│  [Cancel]              [Send Code]   │
└──────────────────────────────────────┘
```

- Email field pre-filled from login form if available
- "Send Code" button → calls `send_otp` → always advances to Step 2

### Step 2: Enter OTP

```
┌──────────────────────────────────────┐
│  Enter Verification Code             │
│                                      │
│  A 6-digit code has been sent to     │
│  doctor@example.com                  │
│                                      │
│  ┌────────────────────────────────┐  │
│  │ 482951                         │  │
│  └────────────────────────────────┘  │
│                                      │
│  [Cancel]           [Verify Code]    │
└──────────────────────────────────────┘
```

- Input limited to 6 characters
- On verify → calls `verify_otp`
- Error shown inline if code is wrong (with remaining attempts)
- On success → advances to Step 3

### Step 3: Set New Password

```
┌──────────────────────────────────────┐
│  Set New Password                    │
│                                      │
│  Enter your new password below.      │
│                                      │
│  ┌────────────────────────────────┐  │
│  │ New password                   │  │
│  └────────────────────────────────┘  │
│  ┌────────────────────────────────┐  │
│  │ Confirm password               │  │
│  └────────────────────────────────┘  │
│                                      │
│  [Cancel]        [Reset Password]    │
└──────────────────────────────────────┘
```

- Both fields use password masking
- Validation: min 6 chars, passwords must match
- On submit → calls `set_password`
- On success → advances to Step 4

### Step 4: Success

```
┌──────────────────────────────────────┐
│  Password Reset!  ✓                  │
│                                      │
│  Your password has been              │
│  successfully changed. You can       │
│  now sign in with your new           │
│  password.                           │
│                                      │
│                            [OK]      │
└──────────────────────────────────────┘
```

- Title in BrandTeal color
- "OK" dismisses dialog, returns to login form

---

## Entry Points

### Doctor Login Screen
- "Forgot Password?" link positioned below the password field, right-aligned
- Pre-fills the email from the login form

### Agent Login Screen
- "Forgot Password?" link positioned below the password field, right-aligned
- Same flow, same edge function

### Role Selection Screen (Login Page)
- "Forgot your password?" link below the Doctor Portal Sign In/Sign Up buttons
- Navigates to the Doctor Login screen (which has the forgot password dialog)

---

## OTP Email Template

Sent via Resend API from `noreply@esiri.africa`:

```
Subject: 482951 is your eSIRI Plus verification code

Body:
┌──────────────────────────────────────┐
│         eSIRI Plus                   │
│                                      │
│  Hello,                              │
│                                      │
│  Your verification code is:          │
│                                      │
│         4 8 2 9 5 1                  │
│                                      │
│  This code expires in 10 minutes.    │
│                                      │
│  If you did not request this code,   │
│  please ignore this email.           │
│                                      │
│  ─────────────────────────────────   │
│  © eSIRI Plus                        │
└──────────────────────────────────────┘
```

- Code displayed in 32px bold with 8px letter spacing, BrandTeal color
- Subject line includes the OTP for quick access from notification

---

## Security Notes

1. **Email enumeration prevention:** `send_otp` always returns `{ sent: true }` regardless of whether the email exists
2. **Brute force protection:** Max 5 attempts per OTP, rate limited per IP and per email
3. **OTP expiry:** 10 minutes from generation
4. **Verification expiry:** New password must be set within 15 minutes of OTP verification
5. **Single-use OTP:** Previous OTPs invalidated when a new one is requested
6. **Password hashing:** bcrypt via `crypt(password, gen_salt('bf'))` — same as Supabase Auth
7. **No auth required:** All three actions use anonymous auth (anon key only) — the OTP is the credential

---

## PWA Implementation

```javascript
const ANON_KEY = 'your-supabase-anon-key';
const BASE_URL = 'https://your-project.supabase.co/functions/v1';

async function resetPasswordStep(action, body) {
  const res = await fetch(`${BASE_URL}/reset-password`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${ANON_KEY}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ action, ...body }),
  });
  const data = await res.json();
  if (!res.ok || data.error) throw new Error(data.error || 'Request failed');
  return data;
}

// Step 1
await resetPasswordStep('send_otp', { email });

// Step 2
await resetPasswordStep('verify_otp', { email, otp_code: userInput });

// Step 3
await resetPasswordStep('set_password', { email, new_password: newPassword });
```

### UI Component
- Use a modal/dialog that stays on the login page
- 4 states: EMAIL → OTP → NEW_PASSWORD → DONE
- Show loading spinner on confirm button during API calls
- Show inline error messages below the input fields
- "Cancel" at any step closes the modal and returns to login
