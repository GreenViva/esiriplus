# Patient Authentication Architecture

## Why X-Patient-Token Exists

eSIRI patients are **anonymous** — they don't have Supabase Auth accounts (no email/password).
Patient sessions are created via `create-patient-session`, which issues a **custom HS256-signed JWT**
containing `session_id`, `app_role: "patient"`, and an expiry.

**The problem:** Supabase's API gateway rejects any JWT it didn't issue. Since patient JWTs are
signed with our own `JWT_SECRET` (not Supabase's internal key), they fail gateway verification.

**The workaround:**
1. Android sends the **anon key** in `Authorization: Bearer` (passes the gateway)
2. The real patient JWT goes in `X-Patient-Token` header
3. `validateAuth()` in `_shared/auth.ts` reads `X-Patient-Token` first, verifies the HS256
   signature, checks session status in the database, and returns an `AuthResult`

## Security Properties

The custom patient JWT is **properly validated**:
- HS256 signature verified against `JWT_SECRET` (not guessable from client)
- Token expiry checked
- Session verified in DB: must exist, be active, not locked, not expired
- All rejections are logged with truncated session ID (no PII)

## Two Auth Paths

```
┌─────────────────┐     ┌──────────────────┐
│  Patient (anon)  │     │  Doctor (Supabase │
│  Custom HS256    │     │  Auth JWT)        │
└────────┬────────┘     └────────┬─────────┘
         │                       │
    X-Patient-Token         Authorization:
    + Authorization:        Bearer <supabase-jwt>
      Bearer <anon-key>          │
         │                       │
    ┌────▼───────────────────────▼────┐
    │       Supabase API Gateway      │
    │  (validates Authorization only) │
    └────────────┬────────────────────┘
                 │
    ┌────────────▼────────────────────┐
    │     Edge Function: validateAuth()│
    │  1. Check X-Patient-Token first │
    │  2. Fall back to Bearer token   │
    │  3. Verify signature + DB check │
    └─────────────────────────────────┘
```

## Rules for New Edge Functions

1. **Every authenticated edge function MUST call `validateAuth(req)`** as its first action
2. If the function is intentionally public (no auth), add it to `PUBLIC_FUNCTIONS` in
   `scripts/check-edge-function-auth.sh`
3. The pre-commit hook will block commits that add edge functions without `validateAuth()`
4. After `validateAuth()`, use `requireRole(auth, "patient")` or `requireRole(auth, "doctor")`
   to enforce role-based access

## Public Functions (No Auth Required)

These functions are intentionally accessible without authentication:
- `create-patient-session` — creates a new anonymous session
- `refresh-patient-session` — refreshes an existing session (validates refresh token internally)
- `recover-by-questions` / `recover-by-id` — session recovery
- `login-doctor` / `register-doctor` — doctor auth flows
- `send-doctor-otp` / `verify-doctor-otp` — OTP verification
- `list-doctors` / `get-doctor-slots` — public doctor directory
- `mpesa-callback` — payment gateway callback (validates IP + payload)
- `lift-expired-suspensions` — cron job (validates `X-Cron-Secret`)
- `log-performance-metrics` — anonymous app metrics

## Monitoring

All patient auth events are logged:
- `[AUTH] Patient auth via X-Patient-Token | session=XXXXXXXX...` — successful auth
- `[AUTH] REJECTED patient token — <reason> | session_id=XXXXXXXX...` — failed auth

View logs in Supabase Dashboard → Edge Functions → Logs, or via:
```bash
supabase functions logs <function-name>
```
