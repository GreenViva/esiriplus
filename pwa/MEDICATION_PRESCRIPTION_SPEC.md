# eSIRI Plus — Medication & Prescription System (for PWA Development)

This document describes the complete medication prescription system: the drug list, how doctors prescribe during report writing, the dosage configuration UI, serialization to the server, AI-enhanced report generation, and how patients view prescriptions. The PWA must replicate this exactly.

---

## Table of Contents

1. [Medication Database](#1-medication-database)
2. [Prescription Data Model](#2-prescription-data-model)
3. [Doctor Report Form — Full Layout](#3-doctor-report-form--full-layout)
4. [Medication Search & Selection](#4-medication-search--selection)
5. [Dosage Configuration Dialog](#5-dosage-configuration-dialog)
6. [Prescription Display Cards](#6-prescription-display-cards)
7. [Submission Payload](#7-submission-payload)
8. [Server-Side Processing & AI Report](#8-server-side-processing--ai-report)
9. [Database Storage](#9-database-storage)
10. [Patient Report View](#10-patient-report-view)
11. [Complete Medication List](#11-complete-medication-list)

---

## 1. Medication Database

Medications are **bundled client-side** as a static list (not fetched from the server). The list contains **74 medications** grouped by therapeutic category. Each entry is a single string containing the drug name, strength, and formulation.

**Injectable detection rule:** If the medication name contains `" inj"` (case-insensitive), it is classified as an injectable. All others default to Tablets/Syrup selection.

### Categories & Counts

| Category | Count | Examples |
|----------|-------|---------|
| Antibiotics & Anti-Infective | 30 | Amoxycillin 500mg caps, Ceftriaxone 1gm, Gentamycin 80mg inj |
| Sedatives & Hypnotics | 1 | Diazepam 5mg/ml inj |
| Anti-Fungal | 3 | Miconazole 2% cream, Nystatin ointment, Amphotericin B inj |
| Anti-Helminthics | 2 | Metronidazole 250mg tabs, Albendazole tabs |
| Anti-Viral | 2 | Acyclovir 3% eye ointment, Acyclovir 5% cream |
| NSAIDs | 4 | Paracetamol 500mg, Diclofenac 75mg inj, Ibuprofen 200mg/400mg |
| Anti-Malarials | 5 | Quinine 600mg inj, Artemether 80mg |
| Anti-Cholinergics | 1 | Atropine sulphate 1mg inj |
| Anti-Spasmodics | 2 | Hyoscine butyl bromide inj |
| Steroidal Anti-Inflammatory | 4 | Hydrocortisone 100mg inj, Dexamethasone 4mg inj, Betamethasone cream |
| Electrolyte Replenishers | 3 | Calcium gluconate inj, Sodium chloride inj, Potassium chloride inj |
| Vitamins | 2 | Cyanocobalamin 1mg inj, Ascorbic acid 500mg inj |
| Gynaecology | 4 | Ergometrine inj, Oxytocin 10IU/ml, Methylergometrine 0.2mg inj |
| Diuretics | 1 | Frusemide 10mg/ml inj |
| Anaesthetics | 16 | Propofol, Ketamine, Haloperidol, Bupivacaine, Lidocaine, Vecuronium, etc. |
| Coagulants & Anti-Coagulants | 3 | Vitamin K1 10mg inj, Heparin 5000IU, Ethamsylate 250mg inj |
| Anti-Epileptic | 2 | Phenobarbital 100mg inj, Phenobarbital 200mg/ml |

---

## 2. Prescription Data Model

```typescript
interface Prescription {
  medication: string;    // Full drug name from the list (e.g. "Amoxycillin trihydrate 500mg BP caps")
  form: string;          // "Tablets" | "Syrup" | "Injection"
  quantity: number;      // Tablets: count per dose, Syrup: ml per dose, Injection: unused (always 1)
  timesPerDay: number;   // How many times per day (1-99)
  days: number;          // Duration in days (1-999)
  route: string;         // Only for Injection: "IM" | "IV" | "SC", empty string otherwise
}
```

### Display Text Generation

The prescription generates a human-readable dosage string:

| Form | Template | Example |
|------|----------|---------|
| Tablets | `Take {quantity} tablet(s) × {timesPerDay} time(s) per day × {days} day(s)` | "Take 1 tablet × 3 times per day × 7 days" |
| Syrup | `Take {quantity}ml × {timesPerDay} time(s) per day × {days} day(s)` | "Take 5ml × 2 times per day × 5 days" |
| Injection | `{route}, {timesPerDay} time(s) per day × {days} day(s)` | "IM, 2 times per day × 3 days" |

### Injectable Detection

```typescript
function isInjectable(medicationName: string): boolean {
  return medicationName.toLowerCase().includes(" inj");
}
```

If injectable → only "Injection" form is available.
If not injectable → "Tablets" and "Syrup" are available.

---

## 3. Doctor Report Form — Full Layout

The report is a **modal bottom sheet** that appears after the doctor ends a consultation. It cannot be dismissed until submitted.

```
┌──────────────────────────────────────────┐
│  Consultation Report                     │
│  Complete the report to end session      │
├──────────────────────────────────────────┤
│                                          │
│  Diagnosed Problem *                     │
│  ┌────────────────────────────────────┐  │
│  │ [multiline text area]              │  │
│  └────────────────────────────────────┘  │
│                                          │
│  Category *                              │
│  ┌────────────────────────────── ▼ ──┐  │
│  │ General Medicine                   │  │
│  └────────────────────────────────────┘  │
│                                          │
│  (if "Other" selected:)                  │
│  Specify Category *                      │
│  ┌────────────────────────────────────┐  │
│  │ [text field]                       │  │
│  └────────────────────────────────────┘  │
│                                          │
│  Severity                                │
│  ┌────────────────────────────── ▼ ──┐  │
│  │ Mild                               │  │
│  └────────────────────────────────────┘  │
│                                          │
│  Treatment Plan *                        │
│  ┌────────────────────────────────────┐  │
│  │ [multiline text area]              │  │
│  └────────────────────────────────────┘  │
│                                          │
│  Further Notes                           │
│  ┌────────────────────────────────────┐  │
│  │ [multiline text area]              │  │
│  └────────────────────────────────────┘  │
│                                          │
│  ── Medication / Prescription ────────── │
│                                          │
│  🔍 Search medication...                 │
│  ┌────────────────────────────────────┐  │
│  │ [search field, min 2 chars]        │  │
│  ├────────────────────────────────────┤  │
│  │ Amoxycillin trihydrate 500mg...    │  │ ← dropdown (max 6 results)
│  │ Amoxicillin + Ac. Clavulanate...   │  │
│  │ Amoxicilline 250mg caps BP         │  │
│  └────────────────────────────────────┘  │
│                                          │
│  (after medication selected, DosageConfigDialog opens)
│                                          │
│  Prescribed Medications:                 │
│  ┌─────────────────────────────── ✕ ─┐  │
│  │ 💊 Amoxycillin 500mg              │  │ ← teal border, teal badge
│  │ Tablets                            │  │
│  │ Take 1 tablet × 3 times/day × 7d  │  │
│  └────────────────────────────────────┘  │
│  ┌─────────────────────────────── ✕ ─┐  │
│  │ 💉 Gentamycin 80mg inj            │  │ ← red border, red badge
│  │ Injection (IM)                     │  │
│  │ IM, 2 times per day × 3 days      │  │
│  └────────────────────────────────────┘  │
│                                          │
│  ☐ Follow-up recommended                │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │      Submit & Generate Report      │  │ ← BrandTeal button
│  └────────────────────────────────────┘  │
└──────────────────────────────────────────┘
```

### Form Fields

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| Diagnosed Problem | Textarea (4 lines) | Yes | Non-blank |
| Category | Dropdown | Yes | Must select one |
| Other Category | Text field | If "Other" | Non-blank when category is "Other" |
| Severity | Dropdown | No (defaults to "Mild") | — |
| Treatment Plan | Textarea (4 lines) | Yes | Non-blank |
| Further Notes | Textarea (3 lines) | No | — |
| Prescriptions | List of Prescription objects | No | Each must have valid dosage |
| Follow-up Recommended | Checkbox | No (defaults to false) | — |

### Category Options
```
General Medicine, Neurological Conditions, Cardiovascular, Respiratory,
Gastrointestinal, Musculoskeletal, Dermatological, Mental Health,
Infectious Disease, Other
```

### Severity Options
```
Mild, Moderate, Severe
```

---

## 4. Medication Search & Selection

### Search Behavior
- Input field with search icon
- Filtering starts at **2+ characters** (below 2 chars, dropdown hidden)
- Case-insensitive substring match against all medication names
- Shows **max 6 results** in a dropdown list
- Already-prescribed medications are **excluded** from results (no duplicates)
- Tapping a result selects it as `pendingMedication` and opens the Dosage Config Dialog

### Search Implementation
```typescript
const filtered = MEDICATIONS
  .filter(med => 
    med.toLowerCase().includes(query.toLowerCase()) &&
    !prescriptions.some(p => p.medication === med)
  )
  .slice(0, 6);
```

---

## 5. Dosage Configuration Dialog

When a medication is selected, a modal dialog appears for dosage configuration.

### For Non-Injectable Medications (Tablets/Syrup)

```
┌──────────────────────────────────────┐
│  Configure Dosage                    │
│  Amoxycillin trihydrate 500mg...     │
│                                      │
│  Form:                               │
│  ┌──────────┐  ┌──────────┐         │
│  │ Tablets  │  │  Syrup   │         │  ← toggle buttons
│  └──────────┘  └──────────┘         │
│                                      │
│  Quantity per dose:                  │
│  ┌──┐ ┌────┐ ┌──┐                   │
│  │ - │ │  1 │ │ + │                  │  ← stepper (1+)
│  └──┘ └────┘ └──┘                   │
│  (unit: "tablet(s)" or "ml")        │
│                                      │
│  Times per day:                      │
│  ┌──┐ ┌────┐ ┌──┐                   │
│  │ - │ │  3 │ │ + │                  │  ← stepper (1-99)
│  └──┘ └────┘ └──┘                   │
│                                      │
│  Duration (days):                    │
│  ┌──┐ ┌────┐ ┌──┐                   │
│  │ - │ │  7 │ │ + │                  │  ← stepper (1-999)
│  └──┘ └────┘ └──┘                   │
│                                      │
│  Preview:                            │
│  "Take 1 tablet × 3 times/day × 7d" │  ← live preview
│                                      │
│  [Cancel]              [Add ✓]       │
└──────────────────────────────────────┘
```

### For Injectable Medications

```
┌──────────────────────────────────────┐
│  Configure Dosage                    │
│  Gentamycine 80mg/2ml inj BP09      │
│                                      │
│  Form:                               │
│  ┌────────────────┐                  │
│  │   Injection    │                  │  ← single option, auto-selected
│  └────────────────┘                  │
│                                      │
│  Route of Administration:            │
│  ┌──────┐ ┌──────┐ ┌──────┐        │
│  │  IM  │ │  IV  │ │  SC  │        │  ← 3 toggle buttons
│  └──────┘ └──────┘ └──────┘        │
│  Intramuscular / Intravenous /      │
│  Subcutaneous                        │
│                                      │
│  Times per day:                      │
│  ┌──┐ ┌────┐ ┌──┐                   │
│  │ - │ │  2 │ │ + │                  │
│  └──┘ └────┘ └──┘                   │
│                                      │
│  Duration (days):                    │
│  ┌──┐ ┌────┐ ┌──┐                   │
│  │ - │ │  3 │ │ + │                  │
│  └──┘ └────┘ └──┘                   │
│                                      │
│  Preview:                            │
│  "IM, 2 times per day × 3 days"     │
│                                      │
│  [Cancel]              [Add ✓]       │
└──────────────────────────────────────┘
```

### Validation Rules
| Form | Required Fields |
|------|----------------|
| Tablets | form selected, quantity ≥ 1 |
| Syrup | form selected, quantity ≥ 1 |
| Injection | form auto-selected, route selected (IM/IV/SC) |

### Stepper Defaults
| Field | Min | Max | Default |
|-------|-----|-----|---------|
| Quantity | 1 | ∞ | 1 |
| Times per day | 1 | 99 | 1 |
| Duration (days) | 1 | 999 | 1 |

---

## 6. Prescription Display Cards

After adding a prescription, it appears as a card in the form:

```
┌─── border color matches form ──────── ✕ ──┐
│  [badge] Medication Name                   │
│  Form (Route for injections)               │
│  Dosage display text                       │
└────────────────────────────────────────────┘
```

### Badge Colors
| Form | Badge Color | Border Color |
|------|------------|--------------|
| Tablets | BrandTeal `#2A9D8F` | BrandTeal at 40% |
| Syrup | Orange `#F59E0B` | Orange at 40% |
| Injection | Red `#EF4444` | Red at 40% |

### Remove Button
- ✕ icon in top-right corner of each card
- Removes the prescription from the list immediately
- No confirmation dialog

---

## 7. Submission Payload

**Edge function:** `generate-consultation-report`
**Method:** POST
**Auth:** Doctor (X-Doctor-Token)

```json
{
  "consultation_id": "uuid",
  "diagnosed_problem": "Upper respiratory tract infection",
  "category": "Respiratory",
  "severity": "Mild",
  "treatment_plan": "Rest, fluids, paracetamol as needed",
  "further_notes": "Return if worsening after 48h",
  "follow_up_recommended": true,
  "prescriptions": [
    {
      "medication": "Amoxycillin trihydrate 500mg BP caps",
      "form": "Tablets",
      "dosage": "Take 1 tablet × 3 times per day × 7 days"
    },
    {
      "medication": "Paracetamol 500mg tabs BP 09",
      "form": "Tablets",
      "dosage": "Take 2 tablets × 3 times per day × 5 days"
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

**Notes:**
- `route` field is only included when `form === "Injection"`
- `dosage` is the pre-formatted display text string
- Empty `prescriptions` array is valid (no medications prescribed)

---

## 8. Server-Side Processing & AI Report

### Step 1: Validation
- Doctor must be authenticated and own the consultation
- Rate limit: 10 requests/minute per doctor
- Duplicate check: if report already exists for this consultation, return existing `report_id`

### Step 2: Fetch Context
- Consultation details (service_type, chief_complaint, session times)
- Doctor profile (full_name, specialist_field)
- Chat transcript (last 100 messages, formatted as `[DOCTOR]: text` / `[PATIENT]: text`)

### Step 3: AI Report Generation (GPT-4o-mini)

The OpenAI prompt includes the doctor's clinical notes with prescriptions formatted as:
```
- Prescribed Medications: Amoxycillin 500mg [Tablets] (Take 1 tablet × 3 times per day × 7 days); Gentamycin 80mg inj [Injection — IM] (IM, 2 times per day × 3 days)
```

The AI generates 5 JSON fields:
| Field | Description |
|-------|-------------|
| `presenting_symptoms` | 2-4 sentence professional summary of patient's complaints |
| `diagnosis_assessment` | 2-4 sentence clinical assessment |
| `treatment_plan_prose` | 2-4 sentence treatment plan incorporating medications |
| `prescribed_medications_prose` | Professional medication section listing each drug with dosage, or "No medications prescribed" |
| `follow_up_instructions` | 2-3 sentence follow-up guidance |

**Model:** `gpt-4o-mini`, temperature: 0.3, max_tokens: 2000, response_format: `json_object`

### Step 4: Save to Database

```sql
INSERT INTO consultation_reports (
  consultation_id, doctor_id,
  diagnosis,                    -- original field (NOT NULL), = diagnosed_problem
  diagnosed_problem, category, severity,
  treatment_plan,               -- AI-expanded prose (falls back to doctor's input)
  further_notes, follow_up_recommended,
  presenting_symptoms,          -- AI-generated
  assessment,                   -- AI-generated (diagnosis_assessment)
  follow_up_plan,               -- AI-generated (follow_up_instructions)
  history,                      -- formatted medication list (see below)
  prescriptions,                -- JSONB array of prescription objects
  doctor_name, patient_session_id, consultation_date,
  verification_code,            -- 12-char random uppercase alphanumeric
  is_ai_generated,              -- true
  created_at
)
```

### `history` field (text, human-readable medication list)
```
1. Amoxycillin trihydrate 500mg BP caps
   Take 1 tablet × 3 times per day × 7 days
2. Gentamycine 80mg/2ml inj BP09 [IM]
   IM, 2 times per day × 3 days
```

### `prescriptions` field (JSONB, machine-readable)
```json
[
  {
    "medication": "Amoxycillin trihydrate 500mg BP caps",
    "form": "Tablets",
    "dosage": "Take 1 tablet × 3 times per day × 7 days"
  },
  {
    "medication": "Gentamycine 80mg/2ml inj BP09",
    "form": "Injection",
    "dosage": "IM, 2 times per day × 3 days",
    "route": "IM"
  }
]
```

### Step 5: Post-Save
1. Set `consultations.report_submitted = true` → triggers `fn_sync_doctor_in_session()` → clears doctor's `in_session` flag
2. Send push notification to patient: "Consultation Report Ready"
3. Audit log the event

### Response
```json
{
  "message": "Report generated successfully",
  "report_id": "uuid",
  "verification_code": "A7K3M9X2P5B1",
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

## 9. Database Storage

### `consultation_reports` Table — Prescription-Related Columns

| Column | Type | Description |
|--------|------|-------------|
| `prescriptions` | JSONB, DEFAULT `'[]'` | Machine-readable array of prescription objects |
| `history` | TEXT | Human-readable numbered medication list |
| `treatment_plan` | TEXT | AI-expanded treatment plan (incorporates medications) |

### Patient-Side Cache (Room / IndexedDB for PWA)

| Field | Type | Description |
|-------|------|-------------|
| `prescribedMedications` | String | AI-generated prose about medications |
| `prescriptionsJson` | String, DEFAULT `"[]"` | Raw JSON array string of prescriptions |

---

## 10. Patient Report View

### How Prescriptions Appear in the Report

Section 5 of the report detail screen shows the AI-generated medication prose:

```
┌──────────────────────────────────────┐
│  § Prescribed Medications            │
│                                      │
│  The following medications have been │
│  prescribed: Amoxycillin trihydrate  │
│  500mg, one tablet to be taken three │
│  times daily for seven days.         │
│  Gentamycin 80mg administered via    │
│  intramuscular injection twice daily │
│  for three days.                     │
└──────────────────────────────────────┘
```

This comes from the `prescribed_medications_prose` field generated by AI. If no medications were prescribed, this section shows "No medications prescribed for this consultation." or is omitted entirely.

The raw `prescriptionsJson` is available for structured display if the PWA wants to render prescription cards instead of (or in addition to) the prose.

### PDF Generation
The PDF includes the same medication prose in Section 5. The `history` field (numbered list) can also be used for a more structured PDF layout.

---

## 11. Complete Medication List

Below is the full list of 74 medications, grouped by category. The PWA must bundle this list client-side.

### ANTIBIOTICS & ANTI-INFECTIVE (30)
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

### SEDATIVES & HYPNOTICS (1)
```
Diazepam 5mg/ml, 2ml inj BP
```

### ANTI-FUNGAL (3)
```
Miconazole 2% cream
Nystatine 100000 IU ointment BP
Amphotericine B for inj USP (50mg/vial)
```

### ANTI-HELMINTHICS (2)
```
Metronidazole 250mg tabs BP
Helmanil tabs (Albendazole)
```

### ANTI-VIRAL (2)
```
Acyclovir 3% 5gm eye ointment BP
Acyclovir 5% 10gm cream
```

### NSAIDs (4)
```
Paracetamol 500mg tabs BP 09
Diclofenac 75mg/3ml inj BP
Ibuprofen 200mg tabs BP
Ibuprofen 400mg tab
```

### ANTI-MALARIALS (5)
```
Quinine base 600mg/2ml inj
Quinine inj 100mg/ml, 2ml inj
Quinine 100mg/ml inj amp 2.4ml
Quinine 300mg (2ml amp) BP
Artemether 80mg
```

### ANTI-CHOLINERGICS (1)
```
Atropine sulphate 1mg inj
```

### ANTI-SPASMODICS (2)
```
Hyoscine butyl bromide injection BP
N-butyl hyoscine bromide inj 20mg
```

### STEROIDAL ANTI-INFLAMMATORY (4)
```
Hydrocortisone 100mg inj BP
Dexamethasone 4mg inj BP
Betamethasone 0.1%, 5gm cream BP
Hydrocortisone sodium succinate 100mg
```

### ELECTROLYTE REPLENISHERS (3)
```
Calcium gluconate inj BP
Sodium chloride inj
Potassium chloride inj
```

### VITAMINS (2)
```
Cyanocobalamine (Hydroxocobalamine) 1mg inj
Ascorbic acid inj 500mg BP
```

### GYNAECOLOGY (4)
```
Ergometrine inj BP
Oxytocin inj 10IU/ml, 1ml BP
Oxytocin inj 5IU/ml, 1ml amp BP
Methylergometrine 0.2mg/ml, 1ml inj USP
```

### DIURETICS (1)
```
Frusemide 10mg/ml, 2ml inj BP
```

### ANAESTHETICS (16)
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

### COAGULANTS & ANTI-COAGULANTS (3)
```
Vitamin K1 (Phytonadione) 10mg/ml inj BP
Heparinate sodium inj 5000IU/ml
Ethamsylate 250mg/2ml inj
```

### ANTI-EPILEPTIC (2)
```
Phenobarbital inj 100mg/ml, 2ml amp
Phenobarbital 200mg/ml
```

---

## PWA Implementation Notes

1. **Medication list:** Store as a static JSON array bundled with the app. No API call needed.
2. **Search:** Client-side filtering with `String.includes()`. Debounce the input (200ms).
3. **Dosage dialog:** Use a modal/dialog component. Stepper buttons for quantity/times/days.
4. **Injectable detection:** Check if medication name includes `" inj"` (case-insensitive).
5. **Form state:** Store prescriptions as an array in the report form state. Each prescription is immutable once added (edit = remove + re-add).
6. **Submission:** POST to `generate-consultation-report` edge function with the exact JSON structure shown in Section 7.
7. **Patient view:** Render the `prescribed_medications_prose` (AI text) in the report. Optionally parse `prescriptionsJson` for structured card display.
8. **PDF:** Include the `history` field (numbered list) in the PDF for structured layout, or use the AI prose.
