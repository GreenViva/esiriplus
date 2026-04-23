-- ============================================================================
-- "Pay by Mobile Number" — schema scaffolding.
--
-- Adds `payments.payment_method` so the table can distinguish between M-Pesa
-- STK push and the new phone-number flow (and any future providers).
-- Existing rows are back-filled to 'mpesa_stk'.
--
-- The phone-number flow is provider-driven: the user enters their number, the
-- provider (Selcom / Azampay / M-Pesa / Yas / Halotel / etc.) pushes a wallet
-- prompt to the user's device, and the user confirms with their wallet PIN on
-- the provider's UI. We never see the PIN — so no challenge table, no OTP.
--
-- The provider call itself is a TODO inside
-- `supabase/functions/initiate-mobile-payment/index.ts` (search TODO PROVIDER).
-- ============================================================================

ALTER TABLE payments
  ADD COLUMN IF NOT EXISTS payment_method TEXT NOT NULL DEFAULT 'mpesa_stk';

ALTER TABLE payments
  DROP CONSTRAINT IF EXISTS payments_payment_method_check;

ALTER TABLE payments
  ADD CONSTRAINT payments_payment_method_check
  CHECK (payment_method IN ('mpesa_stk', 'mobile_number'));

CREATE INDEX IF NOT EXISTS idx_payments_payment_method
  ON payments (payment_method, status);
