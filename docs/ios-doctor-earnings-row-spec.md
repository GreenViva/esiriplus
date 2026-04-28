# iOS — Doctor Dashboard Earnings Row

iOS implementation note for the doctor-side earnings transaction list.
Mirrors the Android change shipped as commit `789f241` — drops a misleading
hardcoded "30%/20%" percentage pill from each row.

This is the **source of truth** for iOS so the same bug isn't reintroduced.

---

## 1. The revenue-split model (so the UI doesn't lie)

The platform applies different splits per tier and per earning type.
Server-side, `fn_auto_create_doctor_earning` reads three keys from
`app_config` and inserts the right amount into `doctor_earnings`:

| Key                          | Default | Meaning                                                  |
|------------------------------|---------|----------------------------------------------------------|
| `doctor_earnings_split_pct`  | `50`    | Royal tier: doctor's share of the consultation fee       |
| `economy_consultation_pct`   | `25`    | Economy tier: doctor's share at consultation completion  |
| `economy_followup_pct`       | `15`    | Economy tier: held in escrow, released to follow-up doctor |

Real-world per-row percentages:

| `earning_type`                | Tier    | Doctor's % of patient fee |
|-------------------------------|---------|---------------------------|
| `consultation`                | Economy | 25                        |
| `consultation`                | Royal   | 50                        |
| `follow_up`                   | Economy | 15 (escrow release)       |
| `substitute_consultation`     | Economy | varies (see Note)         |
| `substitute_follow_up`        | Economy | 15 (escrow release)       |
| Royal follow-up               | Royal   | none — free bonus to patient |

**Note** — these percentages can change via `app_config` updates at runtime,
and grandfather rules apply to in-flight escrows, so any percentage shown in
the UI risks being wrong, stale, or misleading.

> **The Android lesson:** a previous build hard-coded `"30%"` for any
> non-follow-up row and `"20%"` for any follow-up row in
> `DoctorDashboardScreen.kt:3270`. After the migration to 60/25/15, those
> labels became flat-out wrong (Royal 50%-earnings displayed "30%"; Economy
> 25%-earnings also displayed "30%"). The fix shipped on 2026-04-28 was to
> remove the pill entirely.

---

## 2. Row layout — what to render

```
┌────────────────────────────────────────────────────────┐
│ Consultation                            TSh 25,000     │
│ Apr 28, 2026                            Pending        │
└────────────────────────────────────────────────────────┘
```

```
┌────────────────────────────────────────────────────────┐
│ Follow-up                               TSh 15,000     │
│ Apr 28, 2026                            Completed      │
└────────────────────────────────────────────────────────┘
```

Two columns inside an HStack:

**Left column** (leading, takes available width):
- `typeLabel` — 13 pt, medium weight, colour = `typeColor` (see §4)
- `formattedDate` — 11 pt, secondary text colour

**Right column** (trailing, end-aligned):
- `formattedAmount` (e.g. `"TSh 25,000"`) — 13 pt, semibold, primary text colour
- `statusLabel` (e.g. `"Pending"` / `"Completed"`) — 11 pt, status colour

Vertical padding: 8 pt per row.

**Do not render any percentage pill.** The earning amount is the doctor's
take-home for that row; the type label conveys what kind of earning it is.
Both numbers can be reconciled against `consultation.consultation_fee` if a
doctor questions the split, but that belongs on a detail screen, not in the
dashboard row.

---

## 3. Type label mapping

```swift
func typeLabel(for earningType: String) -> String {
    switch earningType {
    case "follow_up":              return "Follow-up"
    case "substitute_follow_up":   return "Substitute FU"
    case "substitute_consultation": return "Substitute"
    default:                       return "Consultation"
    }
}
```

The default (`"Consultation"`) catches anything else the server might add
later — it's better to fall back to the most common label than to crash or
show a raw `earning_type` string.

---

## 4. Type colour mapping

```swift
func typeColor(for earningType: String) -> Color {
    switch earningType {
    case "follow_up", "substitute_follow_up":
        return Color(hex: 0x8B5CF6)      // purple — follow-up family
    default:
        return Color("BrandTeal")        // teal — main consultation
    }
}
```

Status colour (right column second line):

```swift
let statusColor: Color = (status == "Completed")
    ? Color(hex: 0x22C55E)              // green
    : Color(hex: 0xF59E0B)              // amber (pending / on hold / etc.)
```

---

## 5. Data shape

iOS model (matches the Android `EarningsTransaction`):

```swift
struct EarningsTransaction: Identifiable, Equatable {
    let id: String              // earning_id
    let patientName: String     // currently always "Patient" — privacy default
    let amount: String          // pre-formatted "TSh 25,000"
    let date: String            // pre-formatted, e.g. "Apr 28, 2026"
    let status: String          // "Pending" / "Completed" — server casing normalised
    let earningType: String     // raw server value: consultation | follow_up | substitute_follow_up | substitute_consultation
}
```

Source: `doctor_earnings` table, fetched directly from PostgREST:

```
GET <SUPABASE_URL>/rest/v1/doctor_earnings
    ?doctor_id=eq.<doctorId>
    &select=earning_id,amount,status,earning_type,created_at,consultation_id
    &order=created_at.desc
```

Auth headers: `apikey` + `Authorization: Bearer ANON_KEY` + `X-Doctor-Token: <jwt>`.

The dashboard summary (today's / month's / pending payout) is `SUM(amount)`
client-side over the same fetched rows — there's no separate aggregate
endpoint.

---

## 6. Server-side guarantees

- `fn_auto_create_doctor_earning` runs on every consultation transition into
  `completed`; it inserts at most one row per `(doctor_id, consultation_id)`
  pair (`ON CONFLICT DO NOTHING`).
- For Economy parents, `consultations.followup_escrow_amount` and
  `followup_escrow_status` track the held 15% — released when a follow-up
  child consultation completes. iOS doesn't need to read these directly;
  the resulting `doctor_earnings` row is what shows up in the list.
- An `escrow_ledger` table records every hold/release with notes — useful
  for an admin reconciliation screen but not the doctor dashboard row.

---

## 7. Implementation checklist

```
[ ] Earnings tab fetches doctor_earnings via PostgREST with X-Doctor-Token
[ ] Each row renders:
    [ ] Left: typeLabel (type-coloured) + date
    [ ] Right: formatted TSh amount + status text (status-coloured)
[ ] No percentage pill anywhere on the row
[ ] Type label mapping (§3)
[ ] Type colour mapping (§4)
[ ] Status colour for "Completed" vs everything else (§4)
[ ] Sum-card stats (today / this month / last month / pending) computed
    client-side over the fetched rows
```

---

## 8. Why we resisted "compute the % dynamically"

A reasonable alternative: keep the pill but compute
`percent = round(amount / consultation_fee * 100)` per row.

We rejected this because:

- **Extra plumbing for redundant info** — the doctor sees the amount; the
  percentage is derivable but not needed at-a-glance.
- **Cross-table dependency** — every earning row would need a join into
  `consultations.consultation_fee`, doubling the dashboard's data fetch.
- **Edge cases** — substitute splits, free-follow-up Royal cases, and any
  future tier all have idiosyncratic percentages; a single pill encourages
  doctors to misread variance as a bug.

The plain row (label + amount) is the right default. If product later wants
a "what's my effective rate?" panel, that's a separate detail screen with
proper context (tier, fee, earning type, escrow state).
