-- ============================================================================
-- Extend escrow_ledger.entry_type CHECK constraint with the Royal-specific
-- entries the new earnings model emits.
--
--   'royal_followup_payout' — paid to doctor on a 2nd+ FU within the 24h
--                              cap (no escrow involved; comes from platform
--                              pocket against the Royal premium retained
--                              upfront).
--   'royal_call_payout'     — paid to doctor on a connected ≥60s call to a
--                              Royal client within the follow-up window
--                              (reserved for use if/when the call trigger
--                              writes its own ledger entry).
-- ============================================================================

ALTER TABLE escrow_ledger
    DROP CONSTRAINT IF EXISTS escrow_ledger_entry_type_check;

ALTER TABLE escrow_ledger
    ADD CONSTRAINT escrow_ledger_entry_type_check
    CHECK (entry_type IN (
        'consultation_hold',
        'followup_hold',
        'consultation_release',
        'followup_release',
        'followup_expired',
        'admin_review',
        'refund',
        'royal_followup_payout',
        'royal_call_payout'
    ));
