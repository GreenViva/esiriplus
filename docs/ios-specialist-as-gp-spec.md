# iOS — Specialist serving as General Practitioner

iOS implementation spec for the feature that lets a specialist doctor opt in to
appearing on the **General Practitioner (GP) listing** at the **GP rate**.
Backend, PWA, and Android all ship this now; iOS is the last surface. When
behaviour is ambiguous on iOS, mirror Android — it's the closest platform
match and the SSO-of-truth for copy, colours, and gating.

Updated 2026-04-24 after Android's patient-side disclosure pill shipped in
commit `27561d9`. See `docs/ios-pay-by-mobile-number-spec.md` for the broader
iOS delivery conventions.

## 1. What this feature does

A doctor whose primary `specialty` is `specialist` (e.g. cardiologist, paediatrician)
can flip a toggle in their own dashboard to **also** be visible in the GP
pool. Once enabled:

- Patients who pick "General Practitioner" from the service list see this
  specialist in the Find Doctor results alongside native GPs.
- The consultation is booked as a **GP consultation** (`service_type = "gp"`)
  and the fee is the GP rate, not the specialist rate — the doctor
  knowingly trades a higher per-consult fee for broader patient access.
- The specialist's actual sub-specialty (e.g. "Cardiology") is surfaced to
  the patient so they know they're seeing a specialist who's serving as a GP
  for this encounter.

Business rationale: specialist supply outstrips specialist demand during
off-peak hours, and GP demand outstrips GP supply. Letting specialists
cross-serve closes both gaps without double-booking.

## 2. State at a glance

| Layer | Status | Reference |
|---|---|---|
| Database | **Done** | `doctor_profiles.can_serve_as_gp BOOLEAN DEFAULT false` — migration `20260406300000_add_can_serve_as_gp_and_earnings_realtime.sql` |
| Backend — find-doctor filter | **Done** | `supabase/functions/list-doctors/index.ts:30-44` — returns specialists with the flag in the GP query (see §6.1) |
| Backend — fee calculation | **Done** | `create-consultation/index.ts` reads `service_tiers` keyed on `service_type`, so `service_type = "gp"` → GP price regardless of the doctor's primary `specialty` |
| PWA — doctor toggle UI | **Done** | `pwa/src/app/(doctor)/dashboard/page.tsx:458-489` |
| PWA — patient disclosure | **Missing** | Not in scope for PWA right now; same gap as Android had before today |
| Android — doctor toggle UI | **Done** | `feature/doctor/.../DoctorDashboardScreen.kt:1003-1009` (`ServeAsGpToggle`) + VM method `toggleServeAsGp()` at `DoctorDashboardViewModel.kt:1149-1158` |
| Android — patient disclosure | **Done (2026-04-24)** | `feature/patient/.../FindDoctorScreen.kt:809+` amber pill on `DoctorDetailSheet`. Commit `27561d9`. |
| iOS — both sides | **This spec** | Ship the toggle on the doctor dashboard AND the disclosure on the doctor detail view |

iOS does **not** need any backend, DTO, or service-layer work that Android hasn't already proven. `DoctorProfileRow` already has `specialty`, `specialist_field`, and `can_serve_as_gp`; `list-doctors` already returns all three on every row. Read them, render them, write the one boolean when the toggle flips.

## 3. Data model

```sql
doctor_profiles:
  doctor_id          UUID PK
  specialty          ENUM('gp','specialist','nurse','clinical_officer',
                          'pharmacist','psychologist','herbalist')
  specialist_field   TEXT         -- e.g. "Cardiology", "Paediatrics"
  can_serve_as_gp    BOOLEAN DEFAULT false
  is_verified        BOOLEAN
  is_suspended       BOOLEAN
  updated_at         TIMESTAMPTZ
```

The toggle only writes `can_serve_as_gp` + `updated_at`. It never touches
`specialty` or `specialist_field` — the doctor remains a specialist; they
just opt in to an additional pool.

Swift:

```swift
struct DoctorProfile: Codable {
  let doctorId: String
  let specialty: String              // "gp" | "specialist" | ...
  let specialistField: String?
  let canServeAsGp: Bool
  let isVerified: Bool
  let isSuspended: Bool

  enum CodingKeys: String, CodingKey {
    case doctorId = "doctor_id"
    case specialty
    case specialistField = "specialist_field"
    case canServeAsGp = "can_serve_as_gp"
    case isVerified = "is_verified"
    case isSuspended = "is_suspended"
  }

  var canShowGpToggle: Bool {
    specialty == "specialist" && isVerified && !isSuspended
  }
}
```

## 4. Doctor-side UX

### 4.1 Where it lives

On the **Doctor Dashboard**, between the verification/status badges and the
online/offline status banner. Exact position: directly under the header row
that shows the doctor's name + availability pill, above the "Verified
Doctor" banner. Mirrors the PWA at `pwa/src/app/(doctor)/dashboard/page.tsx:458-489`.

### 4.2 Visibility guard

The row is **only rendered** when all three are true:

- `profile.specialty == "specialist"`
- `profile.isVerified == true`
- `profile.isSuspended == false`

If any of those is false, the toggle is hidden entirely — not greyed out.
A doctor whose specialty is `gp` already IS a GP, and unverified or
suspended specialists must not be able to enter the GP pool.

### 4.3 Layout spec

```
┌────────────────────────────────────────────────────────┐
│ ┌──┐                                                   │
│ │GP│  Serve as General Practitioner          ⚪─── ▶   │
│ └──┘  Visible on GP listings — Paid at GP rates        │
└────────────────────────────────────────────────────────┘
```

Dimensions / tokens:

| Element | Off state | On state |
|---|---|---|
| Container background | `.white` | `#EFF6FF` (light blue) |
| Container border | 1pt `#E5E7EB` | 1pt rgba(`#2563EB`, α 0.40) |
| Container corner | 12pt rounded | 12pt rounded |
| Padding | 12pt horizontal, 10pt vertical |
| GP badge pill | 32×32pt circle, `#9CA3AF` bg, `"GP"` 11sp bold white | 32×32pt circle, `#2563EB` bg, same text |
| Title text | `"Serve as General Practitioner"` — 14sp medium, `.black` |
| Subtitle text (off) | `"Toggle to appear on GP patient listings"` — 11sp, `Color(UIColor.secondaryLabel)` |
| Subtitle text (on) | `"Visible on GP listings — Paid at GP rates"` — 11sp, same secondary |
| Toggle | `SwiftUI.Toggle` styled as `.switch`, 44×26pt. Tint `#2563EB` when on, `#D1D5DB` when off. |

Use the iOS blue (`#2563EB`) not brand teal — the PWA does this deliberately
to signal a secondary pool distinct from the primary teal brand. Don't
change it.

### 4.4 Toggle behaviour

Optimistic update. Matches the PWA's pattern (`toggleGpServing` at
`pwa/src/app/(doctor)/dashboard/page.tsx:334-347`):

1. User flips the switch → UI updates immediately.
2. Fire a PATCH:
   ```
   PATCH /rest/v1/doctor_profiles?doctor_id=eq.<me>
   { "can_serve_as_gp": <new>, "updated_at": "<nowISO>" }
   ```
3. On success — stay on the new state.
4. On error — revert the state back, show a short toast: *"Couldn't update
   GP availability. Try again."*

Do **not** show a spinner, full-screen loader, or blocking dialog — this
is a one-bit change that should feel instant.

RLS: the doctor can PATCH their own `doctor_profiles` row. The update path
uses the doctor's own session JWT, not service role.

### 4.5 Copy matrix

| Key | English |
|---|---|
| `specialist_gp_title` | Serve as General Practitioner |
| `specialist_gp_hint_off` | Toggle to appear on GP patient listings |
| `specialist_gp_hint_on` | Visible on GP listings — Paid at GP rates |
| `specialist_gp_toggle_failed` | Couldn't update GP availability. Try again. |

Translate for `sw`/`fr`/`es`/`ar`/`hi` before release. Mark with
`TODO(i18n)`. Use the same keys Android uses when it ships the same
strings so the translation pipeline stays aligned.

## 5. Patient-side UX

### 5.1 Disclosure requirement

When a patient picks "General Practitioner" from Services and lands on
FindDoctor, some rows will be specialists who opted into the GP pool.
Without a visual cue, the patient can't tell them apart from native GPs
and is surprised at consultation start. The pill fixes that.

### 5.2 Where it renders — match the Android placement

Android ships two levels of doctor UI on FindDoctor:

1. **Avatar grid** (`DoctorAvatarItem` in `feature/patient/.../FindDoctorScreen.kt:478+`) —
   a compact grid of profile photos with last name only. Too small for a
   disclosure pill; the pill is **not** shown here.
2. **Detail sheet** (`DoctorDetailSheet` in the same file, line 596+) —
   opens when the patient taps an avatar. Full info: name, specialty
   badge, availability, rating, experience, fee, request buttons. **This
   is where the pill lives.**

If the iOS FindDoctor has the same two-level UX, mirror it: pill on the
detail view, never on the compact list. If the iOS FindDoctor uses a
single list of rich cards (no separate detail sheet), render the pill
inside each card under the name/specialty row — but commit to one place,
don't duplicate.

**Gating conditions** (all three must be true):

- The list context is GP — i.e. the patient picked "General Practitioner"
  on the Services screen. Android gets this via the `serviceCategory`
  parameter passed into `DoctorDetailSheet`; iOS should carry an equivalent
  selection into its detail view.
- `doctor.specialty == "specialist"` (exact match, case-insensitive).
- `doctor.canServeAsGp == true`.

Omit the pill everywhere else — for native GPs, for any non-GP service
list, and when `canServeAsGp` is false. The Specialist list shows
specialists at the specialist rate and never needs this disclosure.

### 5.3 Visual — matches Android's commit `27561d9`

The Android reference implementation (source: `FindDoctorScreen.kt:814-843`)
renders the pill immediately below the specialty + availability row,
before the Spacer that leads into the info card.

```
Dr. Amina Haji                ✓
Specialist     [Available now]
┌──────────────────────────────────┐
│ Specialist · Cardiology          │
│ (serving as GP)                  │   ← the disclosure pill
└──────────────────────────────────┘

───── Info card (rating, experience, fee) ─────
```

Exact tokens from Android:

| Property | Value |
|---|---|
| Background | `#FFFBEB` (warm light amber) |
| Border | 0.5pt `#FBBF24` at 50% alpha |
| Corner radius | 10pt |
| Horizontal padding inside pill | 10pt |
| Vertical padding inside pill | 4pt |
| Gap from the specialty/status row above | 6pt |
| Text font | 11sp (~11pt on iOS), SemiBold |
| Text color | `#B45309` (warm amber) |

Use the same hex values on iOS — amber is the ambient "informational"
treatment used elsewhere in the brand.

### 5.4 Copy — text building rule

Android's implementation uses a conditional pattern the iOS team should
match exactly:

```
if specialistField is non-empty:
    text = L10n("Specialist · %@ (serving as GP)", specialistField.capitalizingFirstLetter())
else:
    text = L10n("Specialist (serving as GP)")
```

Specifics:
- **Prefix literal**: `"Specialist"` — translatable.
- **Separator**: a middle-dot `·` (U+00B7), not a colon or hyphen.
- **Middle arg**: `specialist_field` from the doctor row, with first
  character uppercased (Android uses
  `specialistField!!.replaceFirstChar { it.uppercase() }` at line 830).
  Don't touch the rest — `"paediatric surgery"` → `"Paediatric surgery"`.
- **Suffix**: `" (serving as GP)"` — required; this is what actually
  conveys the rate arrangement. Dropping the suffix makes the pill
  meaningless.
- **Fallback**: when `specialist_field` is null/empty, render exactly
  `"Specialist (serving as GP)"` — no middle dot, no empty placeholder.

### 5.5 Fee display

The TZS amount on every screen (list card, detail sheet, request
confirmation, consultation) is the **GP rate** — not the specialist rate.
The server already computes it correctly from `service_type = "gp"` in
the consultation request payload; iOS must not recalculate.

Do **not** render a "striked-through specialist rate" or any comparison
treatment — the specialist's native rate is irrelevant in the GP flow
and showing it just confuses the patient.

### 5.6 Copy matrix

Two keys, matching Android's `strings.xml` (see `feature/patient/src/main/res/values/strings.xml`, keys `find_doctor_specialist_as_gp` and `find_doctor_specialist_as_gp_fallback`):

| Key | English | Usage |
|---|---|---|
| `find_doctor_specialist_as_gp` | `Specialist · %1$s (serving as GP)` | Primary template — `%1$s` receives `specialist_field` (first char uppercased). |
| `find_doctor_specialist_as_gp_fallback` | `Specialist (serving as GP)` | Used verbatim when `specialist_field` is null or blank. |

Keep the placeholder token (`%1$s` on Android, `%@` or `%1$@` on iOS)
intact across translations. The middle-dot character is literally
embedded in the localized string — do not strip it.

### 5.7 Android reference — code pointer

For pixel-exact parity, the canonical Android implementation is at
`feature/patient/src/main/kotlin/com/esiri/esiriplus/feature/patient/screen/FindDoctorScreen.kt`,
approximately lines 814–843 inside `DoctorDetailSheet`. It shows the
`if/when/Spacer/Box/Text` shape of the final rendered pill. Read it
before starting iOS work; it answers all the questions this spec
hasn't anticipated.

## 6. Connectivity

### 6.1 Find Doctor — read path (no change needed; documented here for clarity)

```
POST {SUPABASE_FUNCTIONS_URL}/list-doctors
Authorization: Bearer <patient_session_jwt>
Content-Type: application/json

{
  "specialty": "gp",
  "tier": "ECONOMY" | "ROYAL",
  "service_region": "TANZANIA",
  "service_district": "<optional>",
  "service_ward":     "<optional>"
}
```

The server applies the cross-serve filter on the `gp` specialty:

```
or(specialty.eq.gp, and(specialty.eq.specialist, can_serve_as_gp.eq.true))
```

For any other specialty query (`specialist`, `nurse`, etc.) it uses a
plain equality filter — specialists do NOT appear in the GP list
because `can_serve_as_gp` is irrelevant there.

The response includes `specialty` and `specialist_field` per doctor
already — iOS does not need to pass any new parameter.

### 6.2 Doctor toggle — write path

```
PATCH {SUPABASE_URL}/rest/v1/doctor_profiles?doctor_id=eq.<me>
Authorization: Bearer <doctor_jwt>
apikey: <anon>
Content-Type: application/json
Prefer: return=minimal

{ "can_serve_as_gp": true, "updated_at": "2026-04-24T10:00:00Z" }
```

Response: `204 No Content` on success. RLS policy `doctors_update_own_profile`
lets the doctor PATCH their own row; attempts to PATCH someone else's
row return `0 rows updated` with `200`.

No edge function is needed for the toggle — it's a plain PostgREST
PATCH. Don't route it through `manage-doctor` (that function is for
admin actions: suspend, ban, flag, warn).

### 6.3 Profile load on dashboard

The dashboard already queries `doctor_profiles` for the signed-in doctor
on open. Just include `can_serve_as_gp` in the selected columns:

```
GET /rest/v1/doctor_profiles?doctor_id=eq.<me>&select=doctor_id,specialty,specialist_field,can_serve_as_gp,is_verified,is_suspended
```

## 7. ViewModel skeleton (doctor side)

```swift
@MainActor
final class DoctorDashboardViewModel: ObservableObject {
  @Published private(set) var profile: DoctorProfile?
  @Published var toggleError: String?

  private let client: SupabaseClient

  func loadProfile() async { /* GET as in §6.3 */ }

  func toggleServeAsGp() async {
    guard var p = profile, p.canShowGpToggle else { return }
    let newVal = !p.canServeAsGp
    // Optimistic
    profile = p.withCanServeAsGp(newVal)

    do {
      _ = try await client
        .from("doctor_profiles")
        .update([
          "can_serve_as_gp": AnyEncodable(newVal),
          "updated_at": AnyEncodable(ISO8601DateFormatter().string(from: Date())),
        ])
        .eq("doctor_id", value: p.doctorId)
        .execute()
    } catch {
      // Revert
      profile = p.withCanServeAsGp(!newVal)
      toggleError = NSLocalizedString("specialist_gp_toggle_failed", comment: "")
    }
  }
}
```

## 8. Acceptance criteria

Cross-check each against the Android build before sign-off — if iOS
behaves the same as Android on each point, the feature is done.

1. **Toggle visibility (doctor side)**: The "Serve as General Practitioner"
   row appears on the doctor dashboard **if and only if** the signed-in
   doctor's specialty is `specialist`, they're verified, and not
   suspended. A native GP never sees the toggle. An unverified or
   suspended specialist never sees it either.
2. **Toggle persists**: Flipping writes `can_serve_as_gp` to the
   doctor's own `doctor_profiles` row via PostgREST PATCH. The new value
   survives app restart and is the same across devices (next login
   reflects it).
3. **Toggle fails gracefully**: If the PATCH errors, the switch snaps
   back to its prior value and a short non-blocking toast appears
   ("Couldn't update GP availability. Try again.").
4. **Patient list shows opt-in specialists**: With a test specialist
   flagged `can_serve_as_gp = true`, opening the GP list as a patient
   shows that specialist alongside native GPs. Without the flag they
   do not appear.
5. **Disclosure pill is rendered (patient side)**: On the GP-context
   doctor detail view, specialists with the flag show the amber pill
   with copy built per §5.4. Native-GP detail views show no pill. The
   Specialist-list detail view shows no pill (even for the same
   doctor).
6. **Fallback copy**: A specialist with `specialist_field = null` or
   blank shows the verbatim fallback `"Specialist (serving as GP)"`
   — no empty middle-dot artefact, no crash.
7. **Fee is GP rate end-to-end**: From the detail sheet fee → request
   button → confirmation sheet → billed consultation, the TZS amount
   is the GP rate (server-computed). No striked-through specialist
   rate anywhere.
8. **Toggling off removes listing**: After a specialist toggles off,
   patients opening fresh GP queries no longer see that specialist
   within one query cycle. Cached/stale lists on the patient side
   clear on pull-to-refresh or next natural reload; no explicit
   invalidation required from iOS.
9. **No regression on non-GP queries**: Picking "Specialist" from
   Services still lists only `specialty = specialist` doctors
   regardless of `can_serve_as_gp`, and at specialist rates.
10. **Strings are keyed**: All new copy lives in `Localizable.strings`
    (or string-catalog equivalents) under the keys in §4.5 and §5.6.
    No hardcoded English in the pill or the toggle.

## 9. Out of scope

- **Reverse direction** — a GP appearing in the Specialist list is NOT
  supported and this spec does not add it. The underlying schema
  would need an inverse flag; deliberate non-goal.
- **Per-specialty toggle granularity** — a specialist can opt in to
  GP or stay out. No "opt in to Nurse" or "opt in to Clinical Officer"
  toggles. If the business ever wants that, it's a schema change plus
  UX redesign, not an extension of this toggle.
- **Admin-forced opt-in** — only the doctor themselves can toggle this.
  Admins can flag/suspend/verify via `manage-doctor`, but they cannot
  set `can_serve_as_gp` on a doctor's behalf. Keep it that way.
- **Patient-side filter to exclude specialists from GP list** — a few
  patients might prefer "only native GPs, please". Not in scope here;
  if added later, it's a filter chip on FindDoctor, not a change to
  this spec.
- **Analytics** — tracking how often the toggle flips, or per-doctor
  GP:Specialist consultation ratios, is an admin-panel concern, not
  an iOS concern.
