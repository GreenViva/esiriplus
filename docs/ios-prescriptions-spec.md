# iOS — Prescription of medicine (doctor side)

iOS implementation spec for the prescription feature inside the doctor's
consultation-report flow. Already ships on Android + backend + PWA-ready
schema; iOS is the last surface. When a detail is ambiguous, mirror Android
— `feature/doctor/src/main/kotlin/com/esiri/esiriplus/feature/doctor/`
and `supabase/functions/generate-consultation-report/index.ts` are the
source of truth.

Updated against shipped Android (no new backend work required). See
`docs/ios-royal-clients-and-medication-timetable-spec.md` for the sibling
nurse-reminder feature that shares this UI.

---

## 1. What the feature does

At the end of a consultation, while writing the report, the doctor picks
one or more **medicines** from a built-in catalogue and configures their
**dosage**, **form**, **quantity**, **times-per-day**, and **duration**.
The list is attached to the consultation report at submit time. The
backend stores it, renders it into the patient-facing PDF prose, and
exposes it via the Reports screen on the patient app.

No standalone "prescription" screen exists — prescriptions are a sub-form
of the report. They are created, edited, and submitted together with the
rest of the report; there's no "save a draft prescription and send later."

## 2. State at a glance

| Layer | Status | Reference |
|---|---|---|
| DB — `prescriptions` table | **Exists but unused by client writes** | `supabase/migrations/20260404300000_create_diagnoses_prescriptions.sql:19-27`. Read-only for portal users via RLS. |
| DB — `consultation_reports.prescriptions` JSONB | **Where prescriptions actually live** | `supabase/migrations/20260402120000_add_prescriptions_column.sql`. Stored as the JSON array the doctor submitted. |
| Backend — report generator | **Done** | `supabase/functions/generate-consultation-report/index.ts:150-162` — folds prescriptions into the OpenAI prompt + stores structured + narrative versions |
| Android — doctor UI | **Done** | `feature/doctor/.../screen/DoctorReportScreen.kt:359-532` + `DoctorReportViewModel.kt:23-459` |
| Android — medicine catalogue | **Done (static)** | `DoctorReportViewModel.MEDICATIONS` — ~150 entries, hardcoded |
| Android — patient report display | **Done** | `feature/patient/.../screen/ReportDetailScreen.kt:309-315` + `ReportPdfGenerator.kt:186-193` |
| PWA — prescription UI | **Missing / unknown** | Out of scope for this spec |
| **iOS — this spec** | **To build** | Doctor-side only; patient-side reads `prescribedMedications` string that the backend already provides |

**Key architectural decision iOS must preserve:** prescriptions are held
**in-memory** during the report form, and only submitted to the server
as part of `generate-consultation-report`. iOS must **not** write to the
`prescriptions` table directly — RLS denies it, and the canonical store
is the `consultation_reports.prescriptions` JSONB column which only the
edge function writes.

## 3. Data flow

```
┌───────────────┐      ┌───────────────────┐      ┌────────────────────────┐      ┌──────────────┐
│ Doctor taps   │      │ Report form with  │      │ POST generate-         │      │ patient app  │
│ "Write Report"│────▶ │ prescription list │────▶ │  consultation-report   │────▶ │ Reports tab  │
│ on consultation│      │ (in-memory only)  │      │                        │      │ (read prose) │
└───────────────┘      └───────────────────┘      │ • OpenAI prose         │      └──────────────┘
                                                  │ • INSERT consultation_ │
                                                  │   reports row          │
                                                  │ • Stores JSON array +  │
                                                  │   formatted history    │
                                                  └────────────────────────┘
```

The doctor can add / remove prescriptions from the in-memory list as
many times as they like before tapping Submit. Once Submit succeeds,
the list is frozen — there is no edit or add-a-prescription-later flow
on Android, and iOS should match.

## 4. Doctor-side UX

### 4.1 Entry point

Inside an active consultation, the doctor taps **Write Report** (or
"End & Write Report" when ending a live call). The report appears as
a full-screen sheet (blocks back navigation until the doctor submits
or explicitly cancels).

Android reference:
- `feature/doctor/.../screen/DoctorConsultationDetailScreen.kt:505-515` — sheet trigger
- `feature/doctor/.../screen/DoctorReportScreen.kt` — the sheet contents

### 4.2 Section in the report form

Under the "Treatment plan" and "Severity" sections and above the
"Further notes" section, the form has a **Prescribed Medications**
block with three pieces:

1. **Search + suggestions** — autocomplete against the catalogue.
2. **List of prescriptions added so far** — each item is a card with
   a delete button.
3. **Dosage configuration dialog** — opens when the doctor picks a
   medication from suggestions, captures form/quantity/frequency/
   duration, and adds the finished prescription to the list.

Android reference: `DoctorReportScreen.kt:359-532` (whole section) +
`816-1020` (dosage dialog).

### 4.3 Medication search

- Single-line input labelled "Search medications".
- Show the suggestions list only when the query length is ≥ 2 characters
  (don't flood the doctor with 150 items from a blank box).
- Filter the catalogue case-insensitively, **exclude medications already
  added to the list** (so the doctor can't accidentally double-prescribe
  from the suggestions — see §10 for the duplicate caveat), cap the
  suggestion list at **6 entries**.
- Tapping a suggestion:
  - Closes the suggestion dropdown.
  - Clears the search input.
  - Opens the dosage dialog with `pendingMedication = <tapped name>`.

Reference: `DoctorReportViewModel.kt:156-169`, `DoctorReportScreen.kt:372-377`.

### 4.4 Dosage configuration dialog

Full-screen or large sheet, modal. Must be dismissible only by tapping
Confirm or Cancel.

Fields:

| Field | Type | Visibility | Default | Notes |
|---|---|---|---|---|
| **Form** | Segmented control | Always | — | Three options: `Tablets`, `Syrup`, `Injection`. If the selected medication name contains `" inj"` (case-insensitive substring), pre-select `Injection`. |
| **Quantity** | Stepper (−/+) | When Form ∈ {Tablets, Syrup} | `1` | Tablets: label "tablets per dose". Syrup: label "ml per dose". Clamped ≥ 1. Hidden when Form is Injection. |
| **Route** | Segmented control | Only when Form = Injection | — | Three options: `IM`, `IV`, `SC`. Required for Injection. |
| **Times per day** | Stepper (−/+) | Always | `1` | Clamped ≥ 1, ≤ 6 (practical cap). |
| **Duration (days)** | Stepper (−/+) | Always | `1` | Clamped ≥ 1, ≤ 30. |
| **Preview** | Read-only text | Always | — | Regenerated on every change. See §4.5. |

**Confirm button gating:**
- Form must be selected.
- For Injection: `route` must be selected.
- For Tablets/Syrup: `quantity > 0`.
- Always: `timesPerDay > 0`, `days > 0`.

**Why the stepper with manual text entry is important**: the Android
version (`DoctorReportScreen.kt:1022-1073` `StepperRow`) allows typing
a number AND ±1 buttons. Keep that dual affordance on iOS — doctors
typing "14" for fourteen days is faster than tapping + fourteen times.
Enforce digit-only keyboard and max-length on the text field.

On Confirm, the dialog closes and the configured prescription is
appended to the list in the report state.

### 4.5 Preview string format (exact)

Render these strings live under the stepper block and also in the
final list card subtitle. These must match Android byte-for-byte
because they flow into the report prose:

| Form | Preview / display text |
|---|---|
| `Tablets` | `Take {quantity} tablet{s?} × {timesPerDay} time{s?} per day × {days} day{s?}` |
| `Syrup` | `Take {quantity}ml × {timesPerDay} time{s?} per day × {days} day{s?}` |
| `Injection` | `{route}, {timesPerDay} time{s?} per day × {days} day{s?}` |

Pluralise `tablet`, `time`, `day` on their own numeric fields — e.g.
`Take 1 tablet × 2 times per day × 7 days`. Reference:
`DoctorReportViewModel.kt:31-41` (Kotlin `displayText()`).

Do **not** localise this string for now — the backend prompt and the
report prose depend on this English phrasing. Mark as `TODO(i18n)`
for a coordinated Android + iOS + backend change later.

### 4.6 Prescription list card

Each prescription is a teal-bordered card with:

- **Medication name** (bold).
- **Form badge** — small pill, colour-coded:
  - `Tablets` → teal `#2A9D8F` bg at 15% alpha, `#1F7A6F` text
  - `Syrup` → orange `#F97316` bg at 15% alpha, `#9A3412` text
  - `Injection` → red `#DC2626` bg at 15% alpha, `#991B1B` text
- **Subtitle** — the exact preview string from §4.5.
- **Delete icon** (X) at trailing edge — removes this entry from the
  in-memory list. Tap must feel instant; no confirm dialog.
- (Royal tier only, see §4.7) — a chip or inline link labelled either
  `Set nurse reminder` or `Reminder: 08:00, 14:00 × 7d` once
  configured.

Reference: `DoctorReportScreen.kt:442-531`.

**Empty state:** when the list is empty, render **nothing** — no
placeholder, no "Add your first medicine" CTA. The search bar above
is the affordance. (Android does the same at line 443.)

### 4.7 Royal tier — optional medication timetable per prescription

If the consultation is Royal (`service_tier = "ROYAL"`), each
prescription card additionally shows a **Set nurse reminder** link. Tapping
it opens the medication timetable dialog described in
`docs/ios-royal-clients-and-medication-timetable-spec.md` Part B, but
prefilled with:
- `medicationName` = the medication name
- `consultationId` = the current consultation id

On confirm, the timetable is **added to a separate in-memory list on
the report** (`medicationTimetables`, distinct from `prescriptions`),
and the chip on the prescription card updates to show the schedule
summary (`08:00, 14:00 × 7d`).

On report submit, the medication timetables are sent alongside
prescriptions in the payload (see §6). The backend wires them up to
the nurse-reminder cron.

If not Royal, the Set nurse reminder link does not render. Android:
`DoctorReportScreen.kt:491-518` + `DoctorReportViewModel.kt:201-239`.

### 4.8 Submit

When the doctor taps **Submit Report** at the bottom of the form:

1. Validate the non-prescription parts (diagnosis, category,
   treatment plan — not covered by this spec, see
   `docs/ios-unsubmitted-reports-spec.md` for the full report contract).
2. Build the JSON payload including the `prescriptions` array and,
   if present, the `medication_timetables` array.
3. Fire `generate-consultation-report` (see §6).
4. On success: close the report sheet, show the success toast, let the
   consultation flow resume.
5. On failure: keep the sheet open, surface the inline error, keep the
   user's entire prescription list intact.

## 5. Medicine catalogue

### 5.1 Source

Android ships a static list of ~150 medications inside
`DoctorReportViewModel.kt` as a `companion object` constant
`MEDICATIONS: List<String>`. The list covers antibiotics,
anti-inflammatories, anaesthetics, and general-practice staples.

iOS must ship **the same list, byte-for-byte**, so that a doctor
working across Android and iOS sees identical autocomplete
suggestions. Copy the Kotlin list into Swift as a frozen constant:

```swift
enum MedicationCatalogue {
  static let all: [String] = [
    "Amoxicillin 500mg caps",
    "Azithromycin 500mg tabs",
    "Ibuprofen 400mg tabs",
    // ... 147 more entries, preserving exact order and spelling from
    // feature/doctor/.../viewmodel/DoctorReportViewModel.kt:352-456
  ]
}
```

When the Android list is edited in future, iOS must be updated in the
same PR. Consider promoting this to a shared JSON file under
`shared/medications.json` with a one-time import script — but that's
a later cleanup, not part of this spec.

### 5.2 Search semantics

- **Min query length**: 2 characters. Below that, show no suggestions.
- **Match rule**: `contains(query, caseInsensitive)` — not
  prefix-match. "cillin" should match "Amoxicillin".
- **Exclusion**: already-added medications must be filtered out of
  suggestions.
- **Cap**: show at most 6 suggestions.

Reference: `DoctorReportScreen.kt:372-377`.

### 5.3 Injection detection

A medication's **name** tells iOS whether to pre-select the Injection
form: if the name contains the substring `" inj"` (case-insensitive,
note the leading space), pre-select Injection and show the route
selector. Otherwise default to Tablets.

Reference: `Prescription.isInjectable()` at
`DoctorReportViewModel.kt:43-46`.

Examples:
- `"Ceftriaxone 1g inj"` → Injection pre-selected.
- `"Amoxicillin 500mg caps"` → Tablets pre-selected.

Don't use `.contains("inj")` — it false-positives on medications like
`"Injectionxl"` if that ever enters the catalogue. The leading-space
check matches Android's behaviour at the source.

## 6. Connectivity

### 6.1 Submit contract

Single endpoint handles both prescriptions and the rest of the
report:

```
POST {SUPABASE_FUNCTIONS_URL}/generate-consultation-report
Authorization: Bearer <doctor_jwt>
Content-Type: application/json

{
  "consultation_id": "<uuid>",
  "diagnosed_problem": "Acute pharyngitis",
  "category": "ENT",
  "severity": "moderate",
  "treatment_plan": "Rest + fluids + prescribed antibiotics",
  "further_notes": "Patient reports no allergies.",
  "follow_up_recommended": true,
  "prescriptions": [
    {
      "medication": "Amoxicillin 500mg caps",
      "form": "Tablets",
      "dosage": "Take 1 tablet × 3 times per day × 7 days"
    },
    {
      "medication": "Ceftriaxone 1g inj",
      "form": "Injection",
      "dosage": "IM, 1 time per day × 5 days",
      "route": "IM"
    }
  ],
  "medication_timetables": [
    {
      "medication_name": "Amoxicillin 500mg caps",
      "times_per_day": 3,
      "scheduled_times": ["08:00", "14:00", "20:00"],
      "duration_days": 7
    }
  ]
}
```

Notes on the `prescriptions[]` objects:

- `medication` = raw name from the catalogue, unchanged.
- `form` = `"Tablets"` | `"Syrup"` | `"Injection"`.
- `dosage` = the preview string from §4.5 (already composed on the
  client). The server does NOT recompute it.
- `route` = **only** included when `form == "Injection"`; must be
  `"IM"` | `"IV"` | `"SC"`. Omit for Tablets/Syrup.
- `quantity` and `timesPerDay` and `days` are **not** sent as separate
  fields — they're baked into `dosage`. This is the legacy contract
  and iOS must not diverge.

Reference: Kotlin payload builder at `DoctorReportViewModel.kt:266-299`.

### 6.2 Response

```json
{
  "report_id": "<uuid>",
  "verification_code": "ABC-123-XYZ",
  "report": {
    "history": "1. Amoxicillin 500mg caps\n   Take 1 tablet × 3 times per day × 7 days\n2. ...",
    "diagnosis": "...",
    "treatment": "..."
  }
}
```

iOS does not need to store the response beyond confirming success. The
patient will fetch the rendered report themselves later.

### 6.3 Swift DTOs

```swift
struct ReportSubmissionRequest: Encodable {
  let consultationId: String
  let diagnosedProblem: String
  let category: String
  let severity: String
  let treatmentPlan: String
  let furtherNotes: String?
  let followUpRecommended: Bool
  let prescriptions: [PrescriptionPayload]
  let medicationTimetables: [MedicationTimetablePayload]?

  enum CodingKeys: String, CodingKey {
    case consultationId = "consultation_id"
    case diagnosedProblem = "diagnosed_problem"
    case category
    case severity
    case treatmentPlan = "treatment_plan"
    case furtherNotes = "further_notes"
    case followUpRecommended = "follow_up_recommended"
    case prescriptions
    case medicationTimetables = "medication_timetables"
  }
}

struct PrescriptionPayload: Encodable {
  let medication: String
  let form: String          // "Tablets" | "Syrup" | "Injection"
  let dosage: String        // see §4.5
  let route: String?        // "IM" | "IV" | "SC", Injection only

  /// Convenience initialiser that encodes an in-memory Prescription.
  init(from p: Prescription) {
    self.medication = p.medication
    self.form = p.form
    self.dosage = p.displayText
    self.route = (p.form == "Injection") ? p.route : nil
  }
}
```

### 6.4 Authentication + rate limit

- Requires doctor JWT (role must be `doctor`). Calls from other roles
  return 403. Reference: `generate-consultation-report/index.ts:51`.
- Rate limited to 10 report submissions per minute per doctor. iOS
  must not retry bottomlessly — on 429, back off and surface the
  error.

## 7. Report integration — what the patient sees

### 7.1 Backend transformation

When the doctor submits, the edge function:

1. Builds an OpenAI prompt including a formatted prescription line:
   `Prescribed Medications: Amoxicillin 500mg caps [Tablets] (Take 1 tablet × 3 times per day × 7 days); Ceftriaxone 1g inj [Injection — IM] (IM, 1 time per day × 5 days)`.
2. Calls GPT-4o-mini to produce a narrative prose section.
3. INSERTS a row into `consultation_reports` with:
   - `prescriptions` = the raw JSON array (audit copy).
   - `history` = a numbered formatted list used for the PDF and the
     patient app.

Reference: `generate-consultation-report/index.ts:150-162,250-252`.

### 7.2 Patient app (Android, what iOS patient surfaces should mirror)

The patient's `ReportDetailScreen.kt:309-315` renders a section titled
"Prescribed Medications" if the report's `prescribedMedications` string
is non-empty. That string is the `history` field from the edge
function, not the JSON array. iOS patient app (when built) should do
the same — render the formatted text, not try to re-parse the JSON.

For **this spec's scope** iOS does not build the patient display. Just
verify after submission that the report ends up with a populated
`prescribedMedications` field by opening the Reports tab on the
existing Android patient build pointed at the same consultation.

### 7.3 PDF export

`ReportPdfGenerator.kt:186-193` draws a "PRESCRIBED MEDICATIONS" heading
and the formatted string in a prose block. No structured JSON
rendering. When iOS adds patient-side PDF export later, match this
— the server is the single formatting authority.

## 8. Localization

Defer most strings. The preview format in §4.5 is **not** localised
today because the backend depends on its English phrasing. iOS must
match Android and leave this deliberately English until a coordinated
Android + iOS + backend change adjusts the prompt to be
language-agnostic.

Strings that ARE safe to localise:

| Key | English |
|---|---|
| `report_prescriptions_title` | Prescribed Medications |
| `report_prescriptions_search` | Search medications |
| `report_prescriptions_empty` | (no string — render nothing) |
| `dosage_dialog_title` | Configure dosage |
| `dosage_form_tablets` | Tablets |
| `dosage_form_syrup` | Syrup |
| `dosage_form_injection` | Injection |
| `dosage_route_im` | IM |
| `dosage_route_iv` | IV |
| `dosage_route_sc` | SC |
| `dosage_quantity_tablets` | tablets per dose |
| `dosage_quantity_syrup` | ml per dose |
| `dosage_times_per_day` | Times per day |
| `dosage_duration_days` | Duration (days) |
| `dosage_confirm` | Add prescription |
| `dosage_cancel` | Cancel |
| `prescription_remove` | Remove |

Royal-tier timetable strings are covered in the medication-timetable
spec (`docs/ios-royal-clients-and-medication-timetable-spec.md` §6).

Translations: `sw`/`fr`/`es`/`ar`/`hi` — flag all `TODO(i18n)`.

## 9. Acceptance criteria

Cross-check each against Android; if iOS behaves the same, the
feature is done.

1. **Search only shows with ≥ 2 chars**: typing one letter in the
   medication search shows no suggestions.
2. **Max 6 suggestions**: a broad query (`"a"` with 3 chars like
   `"ami"`) never shows more than 6 results.
3. **Already-added exclusion**: after adding "Amoxicillin 500mg
   caps", searching "amox" does not suggest it again.
4. **Injection auto-detect**: selecting a medication whose name
   contains `" inj"` opens the dosage dialog with Injection
   pre-selected and the quantity stepper hidden.
5. **Tablets preview**: a Tablets prescription with quantity=2,
   times/day=3, days=5 shows
   `"Take 2 tablets × 3 times per day × 5 days"` — exact punctuation
   and casing.
6. **Confirm gate**: for Injection, Confirm is disabled until a
   route is picked. For Tablets/Syrup, Confirm is disabled until
   quantity ≥ 1 AND times/day ≥ 1 AND days ≥ 1.
7. **Delete is immediate, no confirm dialog**: tapping the X on a
   prescription card removes it instantly.
8. **Form badge colours match**: Tablets pill teal, Syrup orange,
   Injection red, per §4.6.
9. **Submit payload shape byte-exact**: the JSON posted to
   `generate-consultation-report` has prescriptions objects with
   keys `medication`, `form`, `dosage`, and `route` (only when
   Injection). No `quantity`, `times_per_day`, `days` at the top
   level of each object — they live inside `dosage`.
10. **No client write to `prescriptions` table**: monitoring the
    Network tab while submitting shows zero PostgREST traffic to
    `/rest/v1/prescriptions`. All persistence is through the edge
    function.
11. **Error preserves list**: if the submit call returns 500, the
    doctor's in-memory prescription list is still populated when
    the error dialog is dismissed.
12. **Royal-only timetable chip**: the Set nurse reminder link
    appears on prescription cards only when
    `consultation.service_tier == "ROYAL"`.
13. **No edit path**: there is no way to modify a prescription after
    adding it — only delete-and-re-add.

## 10. Known gaps (inherit from Android — do NOT fix on iOS alone)

These are Android's known limitations that iOS must match so the two
platforms behave identically. If you fix them on iOS, fix them on
Android in the same PR — otherwise doctors will hit divergent behavior.

1. **No edit**: once added, a prescription is delete-only.
2. **No duplicate guard on Confirm**: the suggestion filter hides
   already-added medications, but a doctor CAN type the name directly
   (if the search UI allows free text on iOS — don't add that UX)
   or add the same medication via another path. The backend does
   not dedup.
3. **Delete-by-name collision**: Android's `removePrescription`
   matches on medication name. If somehow two prescriptions have
   the same name (shouldn't happen given the suggestion filter, but
   possible via Royal's separate flow), deleting one deletes both.
   iOS: prefer deleting by array index or a client-generated UUID
   on each row. **This is an iOS-only improvement you may ship** —
   it's a strict bug-fix over Android, not divergent behaviour.
4. **No upper bound on quantity/times/days**: the spec suggests
   practical caps (§4.4) but Android enforces none. iOS should
   enforce the caps — again a strict improvement.
5. **English-only preview**: see §8. Do not localise `"Take"`, `"×"`,
   `"times per day"`, `"days"` until backend prompt catches up.

## 11. Out of scope

- **Drug-interaction checking**: no client-side or server-side
  check that a newly prescribed drug conflicts with one already in
  the list. Future work.
- **Patient allergy check**: doctor sees allergies on the patient
  summary already; the prescription form does not cross-reference.
- **Dispense integration**: this is a prescription record, not a
  dispensed-meds workflow. If a pharmacy integration ever lands,
  it'll be a separate feature.
- **Refill mechanism**: no re-prescribe-previous button. Each
  consultation starts from an empty list.
- **Patient-side prescription detail view (beyond the report)**:
  the patient sees prescriptions as part of the consultation report
  only. No standalone "My Medications" screen exists. If added
  later, it belongs in a separate spec.
- **Offline queue**: if the doctor loses network at submit time,
  the in-memory list is preserved (see §9.11) but there's no
  background retry queue. They retry when connectivity returns.
