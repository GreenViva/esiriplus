# eSIRI Edge Functions — Deployment Guide

## Project Structure

```
supabase/functions/
├── _shared/                        ← Shared utilities (no HTTP handler)
│   ├── auth.ts                     ← JWT validation + session resolution
│   ├── cors.ts                     ← CORS headers (no wildcard *)
│   ├── errors.ts                   ← Standardised error responses
│   ├── logger.ts                   ← admin_logs writer
│   ├── rateLimit.ts                ← Upstash Redis sliding window
│   └── supabase.ts                 ← Service role client
│
├── mpesa-stk-push/                 ← Initiate M-Pesa STK Push
├── mpesa-callback/                 ← Safaricom payment confirmation
├── service-access-payment/         ← Service tier payment flow
├── call-recharge-payment/          ← Video minutes top-up
├── create-consultation/            ← New consultation request
├── extend-session/                 ← Patient session extension
├── videosdk-token/                 ← VideoSDK JWT (role-differentiated)
├── generate-consultation-report/   ← AI-powered clinical report
├── verify-report/                  ← QR code report verification
├── send-push-notification/         ← FCM notifications
├── send-appointment-notification/  ← Appointment-specific notifications
├── appointment-reminder/           ← CRON-driven reminder job
├── get-vapid-key/                  ← Web push VAPID public key
└── create-portal-user/             ← Admin portal user creation
```

---

## Prerequisites

```bash
npm install -g supabase
supabase login
supabase link --project-ref your-project-ref
```

---

## Setting Secrets (Production)

Set ALL environment variables as Supabase secrets before deploying:

```bash
supabase secrets set SUPABASE_SERVICE_ROLE_KEY=xxx
supabase secrets set MPESA_CONSUMER_KEY=xxx
supabase secrets set MPESA_CONSUMER_SECRET=xxx
supabase secrets set MPESA_SHORTCODE=xxx
supabase secrets set MPESA_PASSKEY=xxx
supabase secrets set MPESA_CALLBACK_URL=https://YOUR_PROJECT.supabase.co/functions/v1/mpesa-callback
supabase secrets set MPESA_ENV=production
supabase secrets set UPSTASH_REDIS_REST_URL=xxx
supabase secrets set UPSTASH_REDIS_REST_TOKEN=xxx
supabase secrets set VIDEOSDK_API_KEY=xxx
supabase secrets set VIDEOSDK_SECRET=xxx
supabase secrets set FCM_PROJECT_ID=xxx
supabase secrets set FCM_SERVER_KEY=xxx
supabase secrets set VAPID_PUBLIC_KEY=xxx
supabase secrets set VAPID_PRIVATE_KEY=xxx
supabase secrets set OPENAI_API_KEY=xxx
supabase secrets set ALLOWED_ORIGIN=https://admin.esiri.co.ke
supabase secrets set CRON_SECRET=$(openssl rand -hex 32)
```

---

## Deploy All Functions

```bash
# Deploy all at once
supabase functions deploy

# Deploy a single function
supabase functions deploy mpesa-stk-push

# Deploy with no JWT verification (mpesa-callback is called by Safaricom)
supabase functions deploy mpesa-callback --no-verify-jwt
supabase functions deploy verify-report --no-verify-jwt
supabase functions deploy get-vapid-key --no-verify-jwt
```

> ⚠️ `mpesa-callback`, `verify-report`, and `get-vapid-key` must be deployed
> with `--no-verify-jwt` as they are called by external services, not by
> authenticated users.

---

## CRON Job Setup (appointment-reminder)

Add this to your Supabase SQL editor to trigger reminders every 15 minutes:

```sql
-- Enable pg_cron extension first (Supabase dashboard → Extensions)
SELECT cron.schedule(
  'appointment-reminders',
  '*/15 * * * *',
  $$
    SELECT net.http_post(
      url := 'https://YOUR_PROJECT.supabase.co/functions/v1/appointment-reminder',
      headers := jsonb_build_object(
        'Content-Type', 'application/json',
        'X-Cron-Secret', 'YOUR_CRON_SECRET'
      ),
      body := '{}'::jsonb
    );
  $$
);
```

---

## Security Summary

| Function                    | Auth Required | Rate Limit | Notes |
|-----------------------------|:-------------:|:----------:|-------|
| mpesa-stk-push              | ✅ Patient/Doctor | 10/min | Idempotency key required |
| mpesa-callback              | ❌ (Safaricom) | IP allowlist | Returns 200 always |
| service-access-payment      | ✅ Patient    | 10/min | Validates service tier |
| call-recharge-payment       | ✅ Patient    | 10/min | Validates consultation |
| create-consultation         | ✅ Patient    | 10/min | Requires paid access |
| extend-session              | ✅ Patient    | 5/min  | Sensitive |
| videosdk-token              | ✅ Both       | 30/min | Role-differentiated perms |
| generate-consultation-report| ✅ Doctor only | 10/min | AI-powered |
| verify-report               | ❌ Public     | 30/min | QR code only |
| send-push-notification      | ✅ Doctor/Admin | 20/min | Internal use |
| send-appointment-notification| ✅ Any auth  | 20/min | — |
| appointment-reminder        | ✅ Admin/CRON | N/A    | CRON secret |
| get-vapid-key               | ❌ Public     | 30/min | Public key only |
| create-portal-user          | ✅ Admin only | 5/min  | Sensitive |

---

## Local Development

```bash
supabase start
supabase functions serve --env-file .env.local

# Test a function
curl -X POST http://localhost:54321/functions/v1/create-consultation \
  -H "Authorization: Bearer YOUR_JWT" \
  -H "Content-Type: application/json" \
  -d '{"service_type":"gp","consultation_type":"chat","chief_complaint":"I have a headache"}'
```
