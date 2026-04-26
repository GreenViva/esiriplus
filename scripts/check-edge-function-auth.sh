#!/usr/bin/env bash
# Pre-commit check: Ensure all non-public edge functions call validateAuth().
#
# Public functions (no auth required) are explicitly listed below.
# Any NEW edge function added without validateAuth() will fail this check.

set -euo pipefail

PUBLIC_FUNCTIONS=(
  "check-device-binding"
  "create-patient-session"
  "get-doctor-slots"
  "get-security-questions"
  "get-vapid-key"
  "cleanup-expired-messages"
  "expire-followup-escrow"
  "lift-expired-suspensions"
  "purge-deleted-patients"
  "list-doctors"
  "log-performance-metrics"
  "login-agent"
  "login-doctor"
  "medication-reminder-cron"
  "mpesa-callback"
  "register-agent"
  "recover-by-id"
  "recover-by-questions"
  "refresh-patient-session"
  "register-doctor"
  "reset-password"
  "send-doctor-otp"
  "verify-doctor-otp"
  "verify-report"
)

is_public() {
  local name="$1"
  for pub in "${PUBLIC_FUNCTIONS[@]}"; do
    if [[ "$name" == "$pub" ]]; then
      return 0
    fi
  done
  return 1
}

EXIT_CODE=0

for index_file in supabase/functions/*/index.ts; do
  [ -f "$index_file" ] || continue
  dir_name=$(basename "$(dirname "$index_file")")

  # Skip shared utilities
  [[ "$dir_name" == "_shared" ]] && continue

  # Skip explicitly public functions
  if is_public "$dir_name"; then
    continue
  fi

  # Check for validateAuth call
  if ! grep -q "validateAuth" "$index_file"; then
    echo "ERROR: supabase/functions/$dir_name/index.ts does NOT call validateAuth()"
    echo "       If this function is intentionally public, add '$dir_name' to PUBLIC_FUNCTIONS in scripts/check-edge-function-auth.sh"
    EXIT_CODE=1
  fi
done

if [ $EXIT_CODE -eq 0 ]; then
  echo "Edge function auth check passed."
fi

exit $EXIT_CODE
