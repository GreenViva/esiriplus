# Pay by Mobile Number — Architecture

**Status:** scaffolding. Database column + Android wiring + edge-function shell
are in place. **No money moves yet** — the provider call is a single clearly
marked `TODO PROVIDER` block in `initiate-mobile-payment`. PWA is out of scope.
iOS follows the Android contract; spec in `docs/ios-pay-by-mobile-number-spec.md`.

## What the feature does

Alternative to M-Pesa STK push. The user enters any Tanzanian mobile money
number, the server asks the provider (Selcom / Azampay / M-Pesa / Yas /
Halotel / Airtel) to push a wallet prompt to that number showing the exact
amount, the user confirms by typing their **wallet PIN on the provider's own
secure UI**. We never see the PIN, never generate one, never route one.

The difference from STK push is reach: STK push is tied to the SIM routing, so
it only works if the provider's SIM toolkit happens to pop. Wallet-push goes
through the provider's aggregator and is more robust across devices and SIM
stacks — any device linked to the user's wallet account can confirm.

## Flow

```
┌──────────┐                                                  ┌──────────┐     ┌──────────┐
│  CLIENT  │                                                  │ BACKEND  │     │ PROVIDER │
└────┬─────┘                                                  └────┬─────┘     └────┬─────┘
     │  POST initiate-mobile-payment                               │                │
     │  { phone, amount, payment_type, idempotency_key }           │                │
     │  ─────────────────────────────────────────────────────────► │                │
     │                                                             │                │
     │  ┌───────────────────────────────────────────────────────┐  │                │
     │  │ 1. INSERT payments                                    │  │                │
     │  │      status='pending'                                 │  │                │
     │  │      payment_method='mobile_number'                   │  │                │
     │  │ 2. ── TODO PROVIDER ──────────────────────────────    │  │                │
     │  │    Call Selcom / Azampay / M-Pesa B2C with            │  │                │
     │  │    { msisdn, amount, reference=payment_id }           │  │                │
     │  └───────────────────────────────────────────────────────┘  │                │
     │                                                             │ push prompt   │
     │                                                             │ ────────────► │
     │                                                             │                │
     │  ◄────────────────────────────────────────────────────────  │                │
     │  { payment_id, status: 'pending' }                          │                │
     │                                                             │                │
     │  [user sees provider's wallet prompt on their device,       │                │
     │   enters their wallet PIN on the provider UI — we never     │                │
     │   see the PIN]                                              │                │
     │                                                             │   callback     │
     │                                                             │ ◄──────────    │
     │                                                             │                │
     │                                                  ┌──────────┴──────────────┐ │
     │                                                  │ mpesa-callback settles: │ │
     │                                                  │  payments.status =      │ │
     │                                                  │    'completed'/'failed' │ │
     │                                                  │  + service_access_      │ │
     │                                                  │    payments row         │ │
     │                                                  │  + success push         │ │
     │                                                  └──────────┬──────────────┘ │
     │                                                             │                │
     │  GET payments?payment_id=eq.<id>  (poll every 3s)           │                │
     │  ─────────────────────────────────────────────────────────► │                │
     │  ◄────────────────────────────────────────────────────────  │                │
     │  { status: 'completed' | 'failed' | 'pending' }             │                │
```

## Layer map

### Database

**Migration:** `supabase/migrations/20260422100000_mobile_payment_method_column.sql`

Single change: add `payments.payment_method TEXT NOT NULL DEFAULT 'mpesa_stk'`
with a CHECK constraint limiting values to `('mpesa_stk', 'mobile_number')`.
Existing rows back-fill to `'mpesa_stk'`. No new tables, no enum changes.

There is no PIN challenge table, no intermediate status column — the row moves
`pending` → `completed`/`failed` exactly like the STK path.

### Edge functions

- **`supabase/functions/initiate-mobile-payment/index.ts`**
  - Reuses `_shared/auth.ts`, `_shared/rateLimit.ts` (payment tier: 10/min/user),
    `_shared/errors.ts`, `_shared/logger.ts`, `_shared/supabase.ts`.
  - Validates phone / amount / payment_type / idempotency_key.
  - Honours idempotency (returns existing row on duplicate key).
  - Inserts payment row with `status='pending'`, `payment_method='mobile_number'`.
  - Calls the provider (the `TODO PROVIDER` block). In `PAYMENT_ENV=mock` it
    schedules a 5-second auto-success via `mpesa-callback` so the whole UI can
    be exercised end-to-end before a real provider is wired.
  - Returns `{ payment_id, payment_env, status }`.

- **Settlement path is shared with M-Pesa.** The provider's webhook should
  land on (or proxy into) the existing `mpesa-callback` function so the
  post-processing (`service_access_payments` / `call_recharge_payments` row,
  `send-push-notification payment_success`) runs the same way. See the
  recommendation in the TODO block.

### Android

- **Domain:** `PaymentMethod.MOBILE_NUMBER` added in
  `core/domain/.../model/Payment.kt`.
- **Network DTOs:** `InitiateMobilePaymentRequest / InitiateMobilePaymentResponse`
  in `core/network/.../dto/PaymentDto.kt`. No confirm DTO.
- **Service method:** `PaymentService.initiateMobilePayment(...)` in
  `core/network/.../service/PaymentService.kt`. Single method — polling reuses
  the existing `getPaymentStatus`.
- **Database:** `PaymentEntity.paymentMethod` is already a free-form `String`.
  Room persists `"MPESA"` or `"MOBILE_NUMBER"` as the enum's `name()`.
- **UI:** `PatientPaymentScreen.kt` carries the flow:
  `CONFIRM` (method chooser) → `PHONE_ENTRY` → `PROCESSING` → `COMPLETED|FAILED`.
  The processing screen shows a mobile-money-specific hint when the chosen
  method is `MOBILE_NUMBER` so the user knows to look for their wallet prompt.
- **ViewModel:** `PatientPaymentViewModel.kt` — `submitPhoneNumber()` calls
  `initiateMobilePayment` then transitions straight to `PROCESSING` and starts
  the same polling loop as STK.
- **Strings:** English added under the "Pay by Mobile Number" comment block in
  `feature/patient/src/main/res/values/strings.xml`. Localizations for
  `sw`/`fr`/`es`/`ar`/`hi` flagged `TODO(i18n)` — translate before release.

### iOS

Follows the Android contract; full spec in
`docs/ios-pay-by-mobile-number-spec.md`.

## Security notes

1. **PIN never enters our stack.** The user types their wallet PIN on the
   provider's own secure UI (SIM toolkit, wallet app, or provider-hosted
   webview). We don't receive it, can't log it, can't leak it.
2. **Idempotency keys are required** on every initiate call — prevents a
   retry from double-charging if the response is lost.
3. **Rate limiting** (10 req/min/user, payment tier) matches `mpesa-stk-push`.
4. **Reference = payment_id** in the provider call. Settlement callbacks are
   matched on this, so a rogue callback with a random reference is ignored.

## Provider integration — the TODO

There is exactly **one** place to wire the real money mover. Search for
`TODO PROVIDER` in `supabase/functions/initiate-mobile-payment/index.ts` —
the block between the payments-row insert and the success response.

Until it's wired, the function throws in non-mock envs so no one accidentally
ships a silent no-op. In `PAYMENT_ENV=mock` the flow auto-completes after
5 seconds via a simulated `mpesa-callback` invocation, exercising every
downstream row and push the real flow needs to.

Suggested providers (rough order of fit for Tanzania):
- **Selcom wallet-push** — HMAC plumbing already lives in `_shared/payment.ts`
  and it covers all major Tanzanian mobile money operators through one API.
- **Azampay aggregator** — similar coverage, JSON-first.
- **Safaricom M-Pesa B2C** — same credentials as the STK flow; works if you
  only need M-Pesa and want one fewer vendor.

Recommended shape once wired:
```ts
// _shared/payment.ts
export async function pushWalletPrompt(
  paymentId: string,
  msisdn: string,
  amount: number,
  env: PaymentEnv,
): Promise<{ providerReference: string }>
```
— auto-selects mock / sandbox / production off `PAYMENT_ENV`, mirrors how
the STK path is structured so there is one money-mover interface, not two.

On provider callback, reuse `mpesa-callback` (or proxy into it) so the
post-processing pipeline — `service_access_payments` / `call_recharge_payments`
row creation, `send-push-notification payment_success` — runs identically.
That shared settlement path is the reason we don't need a separate confirm
endpoint on our side.

## File inventory

| Layer | File | Status |
|---|---|---|
| DB | `supabase/migrations/20260422100000_mobile_payment_method_column.sql` | new |
| Edge fn | `supabase/functions/initiate-mobile-payment/index.ts` | new, **TODO PROVIDER** |
| Domain | `core/domain/.../model/Payment.kt` | edited (enum) |
| Network | `core/network/.../dto/PaymentDto.kt` | edited (new DTOs) |
| Network | `core/network/.../service/PaymentService.kt` | edited (one new method) |
| Android UI | `feature/patient/.../PatientPaymentScreen.kt` | wired |
| Android VM | `feature/patient/.../PatientPaymentViewModel.kt` | wired |
| Android strings | `feature/patient/src/main/res/values/strings.xml` | English only — **TODO(i18n)** |
| iOS | `docs/ios-pay-by-mobile-number-spec.md` | spec only |
| PWA | — | explicitly out of scope |

## Open questions

- Should we expose provider choice to the user (radio: M-Pesa / Yas / Halotel /
  Airtel) or let the aggregator auto-route from the phone prefix? Current UI
  has no chooser — aggregator decides. If we add one, it goes inside
  `PHONE_ENTRY`.
- The polling timeout is 2 minutes (40 × 3s). Provider wallet prompts can sit
  un-actioned longer than that — should the app keep polling in the background
  and just show "we'll notify you" in the UI, or do we hard-fail at 2 min?
  Current behaviour: hard-fail.
- If the provider's callback is slower than the client poll (provider says OK,
  we haven't processed the webhook yet), the client briefly sees `pending` past
  the provider's success. Acceptable latency budget: one poll tick (≤3s). If
  we see drift worse than this, switch the poll to use a server-pushed
  realtime channel instead of REST polling.
