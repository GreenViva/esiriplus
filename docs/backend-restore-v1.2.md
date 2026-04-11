# Backend Restore Instructions — stable-v1.2 (2026-04-11)

## What's in this version

### Edge Functions (67 total, key changes in v1.2)
- `handle-consultation-request` (v74) — appointment_id link, one-appointment-one-consultation guard, pending request dedup
- `rate-doctor` (v22) — unchanged, but client-side token refresh added
- `appointment-reminder` (v43) — unchanged, sends 20/10/5/0 min reminders
- `manage-consultation` (v36) — unchanged
- All other functions unchanged from v1.1

### Database Migration (new in v1.2)
- `20260411100000_appointment_consultation_link.sql`
  - Adds `appointment_id UUID REFERENCES appointments(appointment_id)` to `consultation_requests`
  - Index on `appointment_id WHERE NOT NULL`

### Migration History Repairs
- `20260404100000_fix_all_rls_policies.sql` renamed to `20260404100001` (duplicate timestamp fix)
- `20260404200000_prevent_unverified_availability.sql` renamed to `20260404200001` (duplicate timestamp fix)
- All repaired as `applied` in remote migration history

## Restore Steps

### 1. Checkout the tag
```bash
git checkout stable-v1.2
```

### 2. Deploy all edge functions
```bash
supabase functions deploy --no-verify-jwt
```

### 3. Apply migrations (if starting fresh)
```bash
supabase db push --include-all
```

### 4. Key edge function to redeploy if issues
```bash
supabase functions deploy handle-consultation-request --no-verify-jwt
```

### 5. Verify migration applied
```sql
SELECT column_name FROM information_schema.columns
WHERE table_name = 'consultation_requests' AND column_name = 'appointment_id';
-- Should return 1 row
```

## Cron Jobs (unchanged from v1.1)
- `appointment-reminder` — every minute
- `handle-missed-appointments` — every minute
- `lift-expired-suspensions` — daily
- `mark-stale-doctors-offline` — every 5 minutes
- `auto-close-unresponsive` — every minute
- `release-expired-holdbacks` — daily
- `expire-followup-escrow` — daily
