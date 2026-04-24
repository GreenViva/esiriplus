# iOS — Specialist serving as General Practitioner

iOS implementation spec for the feature that lets a specialist doctor opt in to
appearing on the **General Practitioner (GP) listing** at the **GP rate**.
Backend + PWA already ship this; this spec brings iOS to feature parity. See
`docs/ios-pay-by-mobile-number-spec.md` for the broader iOS delivery conventions.

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

| Layer | Status | Notes |
|---|---|---|
| Database | **Done** | `doctor_profiles.can_serve_as_gp BOOLEAN DEFAULT false` — migration `20260406300000_add_can_serve_as_gp_and_earnings_realtime.sql` |
| Backend — find-doctor filter | **Done** | `list-doctors` returns specialists with the flag in the GP query (see §6.1) |
| Backend — fee calculation | **Done** | `create-consultation` reads `service_tiers` keyed on `service_type`, so `service_type = "gp"` → GP price regardless of doctor's primary `specialty` |
| PWA — doctor toggle UI | **Done** | `pwa/src/app/(doctor)/dashboard/page.tsx:458-489` |
| Android — doctor toggle UI | **Missing** | Column exists on `DoctorProfileEntity`; no editor screen |
| Android — patient disclosure | **Missing** | FindDoctor lists specialists as GPs indistinguishably — `specialist_field` is fetched but not rendered |
| iOS — both sides | **This spec** | Ship the toggle on the doctor dashboard AND the specialist disclosure on FindDoctor |

iOS does **not** need any backend work. Read `can_serve_as_gp` from the
`doctor_profiles` row, write it with a single PostgREST PATCH, and surface
`specialist_field` in the patient list.

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
`TODO(i18n)`. Match the Android string catalogue style when Android
catches up — same keys.

## 5. Patient-side UX

### 5.1 Disclosure requirement

When the patient picks "General Practitioner" from the Services screen and
lands on FindDoctor, some rows in the list may be specialists who opted
into the GP pool. Today's Android doesn't distinguish them; the fix on
iOS is to show the specialist's sub-specialty inline so the patient is
never surprised at consultation start.

### 5.2 Where it shows

Inside each doctor card in the FindDoctor list (the same card that shows
avatar, name, rating, fee, availability pill). Only displayed when:

- The list is the GP list (the patient picked GP), **and**
- `doctor.specialty == "specialist"` **and** `doctor.canServeAsGp == true`.

Omit it for native GPs (`specialty == "gp"`) and for any non-GP list
(picking "Specialist" shows specialists at the specialist rate — no
disclosure needed).

### 5.3 Visual

A small pill directly under the doctor's name:

```
Dr. Amina Haji                              ★ 4.8 (32)
Specialist · Cardiology (serving as GP)
Available now                               TZS 5,000
```

Pill rendering:

- Background: `#FFFBEB` (light amber)
- Foreground text: `#B45309` (warm amber)
- Font: 11sp semibold
- Prefix: `"Specialist · "` literal, followed by `specialistField`
  (capitalised first letter). When `specialistField` is blank, fall back
  to `"Specialist (serving as GP)"`.
- Suffix: `" (serving as GP)"` — required so the patient reads the full
  sentence and understands the arrangement before tapping.

This is the same amber treatment used elsewhere in the app for
informational banners. Keep it subtle — the goal is disclosure, not
marketing.

### 5.4 Fee display

The TZS amount shown on the card and on the request confirmation is
always the **GP rate**. No recalculation on the client; the server
already computes it correctly based on `service_type = "gp"` in the
consultation request payload. The client must not show a "specialist
rate striked through" — the specialist's higher rate is not relevant
in the GP flow.

### 5.5 Copy matrix

| Key | English |
|---|---|
| `finddoctor_specialist_as_gp_prefix` | Specialist |
| `finddoctor_specialist_as_gp_suffix` | (serving as GP) |
| `finddoctor_specialist_as_gp_fallback` | Specialist (serving as GP) |

`finddoctor_specialist_as_gp_prefix` + middle dot + `specialistField` +
space + `finddoctor_specialist_as_gp_suffix` is the full sentence.

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

1. **Toggle visibility**: The "Serve as General Practitioner" row appears
   on the doctor dashboard **if and only if** the signed-in doctor's
   specialty is `specialist`, they're verified, and not suspended. A
   regular GP never sees the toggle.
2. **Toggle persists**: Flipping the toggle writes `can_serve_as_gp` to
   the doctor's own `doctor_profiles` row. The value persists across
   app restarts and across devices.
3. **Toggle fails gracefully**: If the PATCH fails, the switch snaps
   back to its prior value and a short toast appears.
4. **Patient list shows opt-in specialists**: With a test specialist
   account flagged `can_serve_as_gp = true`, opening the GP list as a
   patient shows that specialist alongside native GPs.
5. **Specialist disclosure is rendered**: Specialist cards in the GP
   list display the amber "Specialist · {field} (serving as GP)" pill.
   Native-GP cards show no such pill.
6. **Fee is GP rate**: Tapping a specialist's card in the GP list and
   proceeding to the confirmation screen shows the GP price (not the
   specialist price). Do not render a "crossed-out specialist rate"
   anywhere in this flow.
7. **Toggling off removes listing within ~60s**: After a specialist
   toggles off, patients opening fresh GP queries no longer see that
   specialist. (The cache on the patient side is the only limiting
   factor; no explicit invalidation required from iOS.)
8. **No regression on non-GP specialty queries**: Picking "Specialist"
   from services lists only `specialty = specialist` doctors regardless
   of their `can_serve_as_gp` value, at specialist rates as before.
9. **Strings are keyed**: All new copy lives in `Localizable.strings`
   (or string catalog equivalents) under the keys in §4.5 and §5.5.

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
