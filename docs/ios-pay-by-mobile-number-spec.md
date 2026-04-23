# iOS — Pay by Mobile Number

iOS implementation spec for the provider-driven mobile-money flow. Mirrors the
Android implementation; the edge-function contract is the source of truth.
See `docs/mobile-payment-architecture.md` for the full architecture.

## 1. User flow

```
┌────────────────────┐
│ Consultation pay   │   Amount + service type shown
│ CONFIRM            │   ┌──────────────────────┐
│                    │◄──┤ Method chooser:      │
│                    │   │ • M-Pesa STK push    │
│                    │   │ • Pay by Mobile Num. │
│                    │   └──────────────────────┘
└────────┬───────────┘
         │  Tap "Pay Now"
         ▼
┌────────────────────┐       ┌────────────────────┐
│  STK path (MPESA)  │       │  Mobile Number     │
│                    │       │  PHONE_ENTRY       │
│  Existing flow     │       │  User types number │
│  (outside scope)   │       └────────┬───────────┘
└────────────────────┘                │ submit
                                      ▼
                             ┌────────────────────┐
                             │  PROCESSING        │
                             │  "Check your       │
                             │   phone for the    │
                             │   wallet prompt"   │
                             │                    │
                             │  [poll status]     │
                             └────────┬───────────┘
                                      │
                                      ├─► COMPLETED  → dismiss, continue
                                      └─► FAILED     → retry / cancel
```

## 2. State machine

```swift
enum PaymentMethod: String, Codable {
  case mpesa = "MPESA"
  case mobileNumber = "MOBILE_NUMBER"
}

enum PaymentStep {
  case confirm        // amount + method chooser
  case phoneEntry     // MOBILE_NUMBER only
  case processing     // polling payments.status
  case completed
  case failed
}

struct PaymentUIState {
  var consultationId: String
  var amount: Int
  var serviceType: String
  var isLoading: Bool = false
  var paymentId: String? = nil
  var paymentStatus: PaymentStep = .confirm
  var errorMessage: String? = nil
  var paymentMethod: PaymentMethod = .mpesa
  var phoneNumberInput: String = ""
}
```

## 3. UI specs

### Color tokens
- Brand teal: `#2A9D8F`
- Selected card background: `#E8F6F4`
- Unselected card border: `#E5E7EB` (1pt); selected card border: `#2A9D8F` (2pt)
- Body text: always black. Never gray for body/label content.

### 3.1 `confirm` — consultation payment + method chooser
- Title "Consultation Payment" (headline, bold, black).
- Service type pill.
- Large amount: `"TZS \(amount)"` in brand teal, display small, bold.
- Charge message (body).
- Section label "Choose payment method".
- Two `MethodChoiceCard`s stacked, 8pt gap:
  - M-Pesa STK: SF Symbol `message.fill`, title "M-Pesa STK Push",
    subtitle "Enter PIN on your SIM prompt".
  - Mobile Number: SF Symbol `iphone`, title "Pay by Mobile Number",
    subtitle "Confirm on your mobile money wallet (M-Pesa, Yas, Halotel, Airtel)".
- Cards have 12pt rounded corners; selected state toggles background +
  border width as above.
- Primary "Pay Now" button (brand teal), 52pt height, full width.

### 3.2 `phoneEntry` — user types mobile number
- SF Symbol `phone.fill`, tint brand teal, 56pt.
- Title "Enter mobile number" (headline, bold, black).
- Hint: "We'll ask your mobile money provider to prompt this number with the
  exact amount. You'll confirm with your wallet PIN."
- `TextField` with:
  - `keyboardType = .phonePad`
  - Label "Mobile number"
  - Placeholder "2557XXXXXXXX"
  - `textContentType = .telephoneNumber`
  - On each change: strip non-digits, cap at 12 chars.
- Inline error text below field for validation failures.
- "Continue" button — enabled when `phone.count >= 12`.

### 3.3 `processing` — wait for wallet prompt + polling
- 64pt circular progress indicator in brand teal.
- Title "Processing Payment".
- Method-aware body text:
  - `mobileNumber`: "Check your phone. Approve TZS {amount} on your mobile
    money wallet prompt to complete payment."
  - `mpesa`: "Your payment of TZS {amount} is being processed. Please wait…"
- No back button (NavigationStack should hide it or swallow swipe-back).

### 3.4 `completed`
- SF Symbol `checkmark.circle.fill`, 80pt, brand teal.
- Title "Payment Successful".
- Body "TZS {amount} paid successfully. Connecting you to your doctor…"
- Auto-dismiss after 1500ms (matches Android).

### 3.5 `failed`
- SF Symbol `exclamationmark.triangle.fill`, 80pt, `.red`.
- Title "Payment Failed".
- Body: error message or "Payment failed" default.
- Outlined "Try Again" button, 60% width, teal text.

## 4. Connectivity

### 4.1 Edge function contract

**`POST {SUPABASE_FUNCTIONS_URL}/initiate-mobile-payment`**

Auth: standard Supabase session JWT in `Authorization: Bearer <token>` (same
as `mpesa-stk-push`). Rate limited to 10/min per user.

Request:
```json
{
  "phone_number": "255712345678",
  "amount": 15000,
  "payment_type": "service_access",
  "service_type": "gp",
  "consultation_id": "<uuid>",
  "idempotency_key": "<uuid>"
}
```

Response (200):
```json
{
  "message": "...",
  "payment_id": "<uuid>",
  "payment_env": "mock" | "sandbox" | "production",
  "status": "pending"
}
```

Error responses follow the shared error envelope used by all edge functions.

### 4.2 Swift DTOs

```swift
struct InitiateMobilePaymentRequest: Encodable {
  let phoneNumber: String
  let amount: Int
  let paymentType: String          // "service_access" | "call_recharge"
  let serviceType: String?
  let consultationId: String?
  let idempotencyKey: String

  enum CodingKeys: String, CodingKey {
    case phoneNumber = "phone_number"
    case amount
    case paymentType = "payment_type"
    case serviceType = "service_type"
    case consultationId = "consultation_id"
    case idempotencyKey = "idempotency_key"
  }
}

struct InitiateMobilePaymentResponse: Decodable {
  let message: String
  let paymentId: String
  let paymentEnv: String?
  let status: String?

  enum CodingKeys: String, CodingKey {
    case message
    case paymentId = "payment_id"
    case paymentEnv = "payment_env"
    case status
  }
}
```

### 4.3 Polling

Reuse whatever payment-status query the iOS app already has for STK — the
shape is identical. If it doesn't exist yet:

```
GET {SUPABASE_URL}/rest/v1/payments?payment_id=eq.<id>&select=payment_id,status,transaction_id,failure_reason
Authorization: Bearer <supabase_jwt>
apikey: <anon>
```

Returns a single-element array. `status` is one of `pending` / `completed` /
`failed`. There is no `awaiting_confirmation` or `confirmed_awaiting_provider`
to worry about — those are gone.

Poll cadence: every 3 seconds, timeout at 2 minutes (40 ticks). On
`completed` → `.completed`, on `failed` → `.failed`, anything else → continue.

### 4.4 Idempotency

Generate one `idempotencyKey = UUID().uuidString.lowercased()` per submit
attempt. On a retried submit (transient network error), reuse the key so the
server returns the same payment row instead of creating a second one. On a
fresh attempt after user-visible failure (`.failed` → retry), generate a new
key — the prior payment is terminal.

## 5. Persistence (local)

Same shape as Android's `PaymentEntity`. When polling lands on `completed`,
write a local row so the UI can short-circuit if the user re-opens the screen
for the same consultation:

```swift
struct PaymentRecord {
  let paymentId: String
  let patientSessionId: String
  let amount: Int
  let paymentMethod: String       // "MPESA" | "MOBILE_NUMBER"
  let transactionId: String?
  let phoneNumber: String
  let status: String              // "completed"
  let createdAt: Date
  let updatedAt: Date
  let consultationId: String
}
```

On screen open, check for an existing completed record by `consultationId` and
jump straight to `.completed` if one exists (matches Android's
`checkExistingPayment`).

## 6. Localization

English strings below match the Android set. Translate into `sw`, `fr`, `es`,
`ar`, `hi` before release.

| Key | English |
|---|---|
| `payment_method_header` | Choose payment method |
| `payment_method_mpesa_title` | M-Pesa STK Push |
| `payment_method_mpesa_subtitle` | Enter PIN on your SIM prompt |
| `payment_method_mobile_title` | Pay by Mobile Number |
| `payment_method_mobile_subtitle` | Confirm on your mobile money wallet (M-Pesa, Yas, Halotel, Airtel) |
| `payment_phone_title` | Enter mobile number |
| `payment_phone_hint` | We'll ask your mobile money provider to prompt this number with the exact amount. You'll confirm with your wallet PIN. |
| `payment_phone_label` | Mobile number |
| `payment_phone_continue` | Continue |
| `payment_phone_invalid` | Enter a valid Tanzanian number (2557XXXXXXXX) |
| `payment_processing` | Processing Payment |
| `payment_processing_message` | Your payment of TZS %d is being processed. Please wait… |
| `payment_processing_mobile_message` | Check your phone. Approve TZS %d on your mobile money wallet prompt to complete payment. |
| `payment_successful` | Payment Successful |
| `payment_success_message` | TZS %d paid successfully.\nConnecting you to your doctor… |
| `payment_failed` | Payment Failed |
| `payment_try_again` | Try Again |

## 7. Security

Because the user's wallet PIN is entered on the provider's own secure UI
(wallet app / SIM toolkit / provider webview), we have almost nothing to
worry about on the client:

1. **Never collect the wallet PIN.** No PIN field anywhere in the app. If a
   future spec says otherwise, push back — PINs belong on the provider's UI.
2. **Use one idempotency key per user-initiated submit.** See §4.4.
3. **Don't blindly retry an initiate call on transport error** — poll
   payment status first (up to 15s) before showing failure, in case the
   server processed the insert but we lost the response.

## 8. ViewModel skeleton

```swift
@MainActor
final class PatientPaymentViewModel: ObservableObject {
  @Published private(set) var state = PaymentUIState(
    consultationId: "", amount: 0, serviceType: ""
  )

  private let paymentService: PaymentService
  private var pollTask: Task<Void, Never>?

  func selectMethod(_ method: PaymentMethod) {
    state.paymentMethod = method
    state.errorMessage = nil
  }

  func onPayTapped() {
    switch state.paymentMethod {
    case .mpesa:          initiateSTK()
    case .mobileNumber:   state.paymentStatus = .phoneEntry
    }
  }

  func onPhoneChanged(_ raw: String) {
    state.phoneNumberInput = String(raw.filter(\.isNumber).prefix(12))
  }

  func submitPhoneNumber() async {
    guard !state.isLoading else { return }
    let phone = state.phoneNumberInput
    guard phone.range(of: "^2556\\d{8}$|^2557\\d{8}$", options: .regularExpression) != nil else {
      state.errorMessage = "Enter a valid Tanzanian number (2557XXXXXXXX)"
      return
    }
    state.isLoading = true
    state.errorMessage = nil

    do {
      let resp = try await paymentService.initiateMobilePayment(
        phoneNumber: phone,
        amount: state.amount,
        paymentType: "service_access",
        serviceType: state.serviceType,
        consultationId: state.consultationId.isEmpty ? nil : state.consultationId,
        idempotencyKey: UUID().uuidString
      )
      state.isLoading = false
      state.paymentId = resp.paymentId
      state.paymentStatus = .processing
      startPolling(paymentId: resp.paymentId)
    } catch {
      state.isLoading = false
      state.errorMessage = (error as? LocalizedError)?.errorDescription ?? "Network error"
    }
  }

  private func startPolling(paymentId: String) {
    pollTask?.cancel()
    pollTask = Task { [weak self] in
      for _ in 0..<40 {
        try? await Task.sleep(nanoseconds: 3_000_000_000)
        if Task.isCancelled { return }
        if let status = try? await self?.paymentService.getPaymentStatus(paymentId) {
          switch status.status.lowercased() {
          case "completed":
            await MainActor.run { self?.state.paymentStatus = .completed }
            return
          case "failed":
            await MainActor.run {
              self?.state.paymentStatus = .failed
              self?.state.errorMessage = status.failureReason ?? "Payment failed"
            }
            return
          default: continue
          }
        }
      }
      await MainActor.run {
        self?.state.paymentStatus = .failed
        self?.state.errorMessage = "Payment timed out. Please try again."
      }
    }
  }

  func retry() {
    pollTask?.cancel()
    state.paymentId = nil
    state.paymentStatus = .confirm
    state.errorMessage = nil
    state.isLoading = false
  }
}
```

## 9. Acceptance criteria

1. From the consultation pay screen, user can toggle between M-Pesa STK and
   Pay by Mobile Number; selection is visually obvious (teal border + tinted
   background).
2. Pay by Mobile Number path: phone entry → processing → completed without
   ever asking the user for a PIN inside the eSIRI+ app.
3. Invalid phone numbers produce an inline error on phone-entry; server never
   receives them.
4. Back-press/swipe-back is blocked during `.processing` and `.completed`.
5. Idempotency: submitting the same attempt twice in a row (e.g. double-tap)
   does not create two payment rows.
6. In `PAYMENT_ENV=mock`, the flow auto-completes within ~8 seconds of phone
   submission (5s mock provider + one poll tick).
7. A completed payment for the current `consultation_id` is recognised on
   screen re-open and skips straight to `.completed`.
8. All body text is black; no gray body text anywhere on the flow.
9. All user-facing strings are keyed, not hardcoded, and match §6.
10. No wallet PIN entry field exists in the iOS app at all — if one appears in
    a future draft, treat it as a spec regression.

## 10. Out of scope

- Provider selection UI (radio for M-Pesa/Yas/Halotel/Airtel). Server chooses
  via aggregator.
- PIN entry on our side — the provider handles it.
- Offline queueing. If the device has no network at submit time, show the
  error and let the user retry.
- Extension payments use M-Pesa STK only; the mobile-number method is not
  offered there (matches Android).
