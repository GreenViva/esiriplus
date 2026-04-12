# Medication & Report Submission — iOS Implementation Spec

## Overview

When a doctor finishes a consultation, they submit a **patient report** that includes diagnosis details and optionally **prescribed medications**. The report form collects structured data, sends it to a Supabase Edge Function (`generate-consultation-report`), which uses OpenAI GPT-4o-mini to generate professional prose, stores the report, and sends a push notification to the patient.

Medications are selected from a **hardcoded list of 106 medicines**, configured with dosage details via a dialog, and attached to the report as a JSON array.

---

## Report Form Fields

| Field | Type | Required | Notes |
| ----- | ---- | -------- | ----- |
| Patient Age | Text (numeric) | No | Free text |
| Patient Gender | Toggle (Male / Female) | No | Two-button selector |
| Diagnosed Problem | Multi-line text | **Yes** | Min 3 lines visible |
| Category | Dropdown | **Yes** | 10 options (see list below) |
| Other Category | Text | **Yes** if category = "Other" | Shown conditionally |
| Severity | Dropdown | No | Default: "Mild" |
| Treatment Plan | Multi-line text | **Yes** | Min 3 lines visible |
| Further Notes | Multi-line text | No | |
| Medications | Search + list | No | See medication flow below |
| Follow-up Recommended | Checkbox | No | Default: unchecked |

### Categories
```
General Medicine, Neurological Conditions, Cardiovascular, Respiratory,
Gastrointestinal, Musculoskeletal, Dermatological, Mental Health,
Infectious Disease, Other
```

### Severity Levels
```
Mild, Moderate, Severe
```

---

## Medication Flow — Step by Step

### Step 1: Search

- Section label: **"Medication / Prescription (Optional)"**
- Search field with placeholder: `"Search medication..."`
- Leading icon: magnifying glass (SF Symbol: `magnifyingglass`) tinted BrandTeal
- Minimum **2 characters** before results appear
- Filters the hardcoded `MEDICATIONS` list (case-insensitive substring match)
- Shows **max 6 results** in a dropdown
- Already-prescribed medications are **excluded** from suggestions

### Step 2: Select

- Each result row shows: `+` icon (BrandTeal) + medication name (13pt, black)
- Tapping a result:
  1. Sets it as `pendingMedication`
  2. Clears the search field
  3. Opens the **Dosage Configuration Dialog**

### Step 3: Configure Dosage (Dialog)

The dialog adapts based on whether the medication is **injectable** (name contains `" inj"`) or not.

#### Dialog Header
- Title: **"Dosage Instructions"** (18pt, bold, black)
- Subtitle: medication name (13pt, gray)
- If injectable: red badge **"Injectable medication"** (red text on red/10% bg)

#### Form Selection
- **Non-injectable**: Two toggle buttons — `Tablets` | `Syrup`
- **Injectable**: Locked to `Injection` (single button, auto-selected)

#### Route of Administration (Injection only)
Three toggle buttons, each showing abbreviation + full name:
- **IM** — Intramuscular
- **IV** — Intravenous
- **SC** — Subcutaneous

#### Quantity Per Dose (Tablets/Syrup only, NOT shown for Injections)
- Label: "How many tablets per dose?" or "How many ml per dose?"
- Stepper control: `[-]` `[input]` `[+]` `tablet(s)` / `ml`

#### Times Per Day (all forms)
- Label: "How many times per day?"
- Stepper control: `[-]` `[input]` `[+]` `time(s)/day`

#### Duration (all forms)
- Label: "For how many days?"
- Stepper control: `[-]` `[input]` `[+]` `day(s)`

#### Live Preview
A teal-tinted preview box showing the formatted dosage string:
- Tablets: `"Take 2 tablets × 3 times per day × 7 days"`
- Syrup: `"Take 5ml × 2 times per day × 5 days"`
- Injection: `"IM, 2 times per day × 3 days"`

#### Buttons
- **"Add Prescription"** (BrandTeal, enabled when form is selected + route if injection + quantity > 0 if not injection)
- **"Cancel"** (gray text button)

### Step 4: Prescription Card (Added to List)

Each prescribed medication appears as a card below the search field:

```
┌──────────────────────────────────────────────────┐
│ Amoxycillin trihydrate 500mg BP caps         [✕] │
│ ┌──────────┐                                     │
│ │ Tablets  │  (badge, color-coded by form)       │
│ └──────────┘                                     │
│ Take 2 tablets × 3 times per day × 7 days        │
└──────────────────────────────────────────────────┘
```

**Badge colors by form:**

| Form | Background | Text Color |
| ---- | ---------- | ---------- |
| Tablets | BrandTeal @ 12% | BrandTeal |
| Syrup | `#F59E0B` @ 12% | `#B45309` (amber) |
| Injection | `#EF4444` @ 12% | `#EF4444` (red) |

- Injection badge shows: `"Injection (IM)"` (includes route)
- Card background: BrandTeal @ 6%, border: BrandTeal @ 25%
- Delete button: `✕` icon in `#EF4444` @ 70%, removes the medication
- Multiple medications can be added; cards stack vertically with 8pt spacing

### Stepper Control Component

Reusable component used for quantity, times per day, and duration:

```
[ - ]  [ 1 ]  [ + ]  tablet(s)
```

- Minus/Plus: 36pt teal circles with white `−`/`+` text
- Input: 70pt wide outlined text field, centered bold 16pt, digits only
- Unit label: 13pt gray text
- Minimum value: 1 (minus button does nothing below 1)

---

## Prescription Data Model

```swift
struct Prescription {
    let medication: String
    let form: String          // "Tablets", "Syrup", or "Injection"
    let quantity: Int          // Tablets count or ml (unused for Injection)
    let timesPerDay: Int
    let days: Int
    let route: String          // "IM", "IV", or "SC" (only for Injection)

    var displayText: String {
        switch form {
        case "Tablets":
            return "Take \(quantity) tablet\(quantity > 1 ? "s" : "") × \(timesPerDay) time\(timesPerDay > 1 ? "s" : "") per day × \(days) day\(days > 1 ? "s" : "")"
        case "Syrup":
            return "Take \(quantity)ml × \(timesPerDay) time\(timesPerDay > 1 ? "s" : "") per day × \(days) day\(days > 1 ? "s" : "")"
        case "Injection":
            return "\(route), \(timesPerDay) time\(timesPerDay > 1 ? "s" : "") per day × \(days) day\(days > 1 ? "s" : "")"
        default:
            return medication
        }
    }

    static func isInjectable(_ name: String) -> Bool {
        name.localizedCaseInsensitiveContains(" inj")
    }
}
```

---

## Report Submission — API Payload

**Edge Function:** `generate-consultation-report`

**Method:** POST to `{SUPABASE_URL}/functions/v1/generate-consultation-report`

**Headers:**
```
Content-Type: application/json
apikey: {SUPABASE_ANON_KEY}
Authorization: Bearer {SUPABASE_ANON_KEY}
X-Skip-Auth: true
X-Doctor-Token: {doctor_jwt_token}
```

**Request Body:**
```json
{
  "consultation_id": "uuid-string",
  "patient_age": "25",
  "patient_gender": "Male",
  "diagnosed_problem": "Upper respiratory tract infection...",
  "category": "Respiratory",
  "severity": "Mild",
  "treatment_plan": "Rest, hydration, antibiotics course...",
  "further_notes": "Patient allergic to penicillin.",
  "follow_up_recommended": true,
  "prescriptions": [
    {
      "medication": "Ciprofloxacine 500mg tab USP",
      "form": "Tablets",
      "dosage": "Take 1 tablet × 2 times per day × 7 days"
    },
    {
      "medication": "Gentamycine 80mg/2ml inj BP09",
      "form": "Injection",
      "dosage": "IM, 2 times per day × 3 days",
      "route": "IM"
    }
  ]
}
```

**Key rules:**
- `prescriptions` is always sent (empty array `[]` if none)
- `dosage` field = the `displayText` output from the Prescription model
- `route` is only included when `form == "Injection"`
- `patient_age` and `patient_gender` are only sent when not blank

**Success Response (201):**
```json
{
  "message": "Report generated successfully",
  "report_id": "uuid",
  "verification_code": "A1B2C3D4E5F6",
  "report": {
    "presenting_symptoms": "...",
    "diagnosis_assessment": "...",
    "treatment_plan_prose": "...",
    "prescribed_medications_prose": "...",
    "follow_up_instructions": "..."
  }
}
```

---

## Backend Processing (What the Edge Function Does)

1. **Auth**: Validates doctor role from JWT
2. **Rate limit**: 10 requests/minute per doctor
3. **Validation**: Requires `consultation_id`, `diagnosed_problem`, `category`, `treatment_plan`
4. **Lookup**: Fetches consultation (must belong to this doctor) + doctor profile
5. **Duplicate check**: If report exists and consultation was reopened (`follow_up_count > 0`), deletes old report; otherwise returns existing
6. **Chat context**: Fetches up to 100 chat messages as transcript
7. **AI generation**: Sends structured prompt to OpenAI GPT-4o-mini including prescription details
8. **Storage**: Inserts into `consultation_reports` table with:
   - `prescriptions` column: JSON array of prescription objects
   - `history` column: Formatted text list of medications
   - All AI-generated prose fields
   - 12-character verification code
9. **Status update**: Sets `report_submitted = true` on the consultation
10. **Push notification**: Sends "Consultation Report Ready" to patient via `send-push-notification` edge function

---

## Validation Rules

| Rule | Error Message |
| ---- | ------------- |
| `diagnosedProblem` is blank | "Diagnosed problem is required" |
| `category` is blank | "Category is required" |
| `category == "Other"` and `otherCategory` is blank | "Please specify the category" |
| `treatmentPlan` is blank | "Treatment plan is required" |

Medications are **optional** — the report can be submitted without any prescriptions.

---

## Complete Medication List (106 Items)

### Antibiotics & Anti-Infectives
```
Amoxycillin trihydrate 500mg BP caps
Ceftriaxone 1gm vial USP 30
Amoxicillin + Ac. Clavulanate 500/125mg tab
Ciprofloxacine 500mg tab USP
Sulphamethoxazole 400mg + Trimethoprim 80mg tabs BP
Gentamycine 80mg/2ml inj BP09
Amoxicilline 250mg caps BP
Neomycine + Bacitracin (0.5% + 500IU/gm) ointment
Streptomycine 1gm inj BP
Gentamycine 20mg/2ml inj BP09
Ceftazidime 1gm inj
Cefotaxime 1gm + WFI inj USP
Amoxicilline + Ac. Clavulanate 1gm/200mg inj
Kanamycine 1gm inj BP
Erythromycine 500mg tabs BP
Clanoxy 1.2gm inj (Amox + Pot. Clav)
Ceftriaxone 500mg inj
Amoxicillin and clavulanate potassium tabs USP
Cefixime tabs USP 200mg
Keftaz 1000 (Ceftazidime for inj USP 1gm)
Clanoxy 625mg (Amox + Pot Clav) tabs USP
Chloramphenicol inj BP
Gentamicine inj 80mg BP
Amoxicillin 500mg and clavulanate potassium 62.5mg tabs USP
Chloramphenicol inj 1gm BP
Imipenem + Cilastatin 500mg
Teicoplanin 400mg
Vancomycin 500mg/1gm
Meropenem 500mg
Cefepime 1gm
```

### Sedatives & Hypnotics
```
Diazepam 5mg/ml, 2ml inj BP
```

### Anti-Fungal
```
Miconazole 2% cream
Nystatine 100000 IU ointment BP
Amphotericine B for inj USP (50mg/vial)
```

### Anti-Anthelmatics
```
Metronidazole 250mg tabs BP
Helmanil tabs (Albendazole)
```

### Anti-Viral
```
Acyclovir 3% 5gm eye ointment BP
Acyclovir 5% 10gm cream
```

### NSAIDs
```
Paracetamol 500mg tabs BP 09
Diclofenac 75mg/3ml inj BP
Ibuprofen 200mg tabs BP
Ibuprofen 400mg tab
```

### Anti-Malarials
```
Quinine base 600mg/2ml inj
Quinine inj 100mg/ml, 2ml inj
Quinine 100mg/ml inj amp 2.4ml
Quinine 300mg (2ml amp) BP
Artemether 80mg
```

### Anti-Cholinergics
```
Atropine sulphate 1mg inj
```

### Anti-Spasmodics
```
Hyoscine butyl bromide injection BP
N-butyl hyoscine bromide inj 20mg
```

### Steroidal Anti-Inflammatory
```
Hydrocortisone 100mg inj BP
Dexamethasone 4mg inj BP
Betamethasone 0.1%, 5gm cream BP
Hydrocortisone sodium succinate 100mg
```

### Electrolyte Replenishers
```
Calcium gluconate inj BP
Sodium chloride inj
Potassium chloride inj
```

### Vitamins
```
Cyanocobalamine (Hydroxocobalamine) 1mg inj
Ascorbic acid inj 500mg BP
```

### Gynaecology
```
Ergometrine inj BP
Oxytocin inj 10IU/ml, 1ml BP
Oxytocin inj 5IU/ml, 1ml amp BP
Methylergometrine 0.2mg/ml, 1ml inj USP
```

### Diuretics
```
Frusemide 10mg/ml, 2ml inj BP
```

### Anaesthetics
```
Propofol 1gm inj BP
Propofol inj (1% w/v) BP
Ketamine 50mg/ml 10ml inj BP
Haloperidol 5mg/ml, 1ml inj
Bupivacaine 0.25% (950mg/20ml) inj BP
Thiopental 0.5gm inj BP
Lignocaine injection BP
Lidocaine injection
Bupivacaine 0.5% inj BP
Vecuronium bromide 4mg & 10mg
Pancuronium bromide BP 4mg
Ropivacaine hydrochloride 40/150/200
Atracurium besylate USP 10mg/ml
Suxamethonium chloride 100mg/2ml inj
Neostigmine inj 0.5mg/ml BP
Lignocaine hydrochloride & dextrose inj USP
Lidocaine 2% + adrenaline inj
Bupivacaine rachi anaesthesia
```

### Coagulants & Anti-Coagulants
```
Vitamin K1 (Phytonadione) 10mg/ml inj BP
Heparinate sodium inj 5000IU/ml
Ethamsylate 250mg/2ml inj
```

### Anti-Epileptic
```
Phenobarbital inj 100mg/ml, 2ml amp
Phenobarbital 200mg/ml
```

---

## Injectable Detection Rule

A medication is **injectable** if its name contains `" inj"` (case-insensitive). This auto-locks the form to "Injection" and shows the route selector instead of quantity.

Examples: `"Gentamycine 80mg/2ml inj BP09"` → injectable, `"Paracetamol 500mg tabs BP 09"` → not injectable.

---

## Android Reference Files

| File | Lines | What it contains |
| ---- | ----- | ---------------- |
| `DoctorReportViewModel.kt` | 23–43 | `Prescription` data class + `displayText()` + `isInjectable()` |
| `DoctorReportViewModel.kt` | 45–68 | `DoctorReportUiState` |
| `DoctorReportViewModel.kt` | 139–181 | Medication search, select, confirm, remove methods |
| `DoctorReportViewModel.kt` | 183–264 | `submitReport()` with JSON payload construction |
| `DoctorReportViewModel.kt` | 268–386 | `CATEGORIES`, `SEVERITIES`, `MEDICATIONS` lists |
| `DoctorReportScreen.kt` | 346–491 | Medication search UI + prescription cards |
| `DoctorReportScreen.kt` | 777–979 | `DosageConfigDialog` |
| `DoctorReportScreen.kt` | 982–1032 | `StepperRow` component |
| `generate-consultation-report/index.ts` | 1–332 | Full edge function (shared backend, no iOS changes needed) |
