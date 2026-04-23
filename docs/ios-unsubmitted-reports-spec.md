# Unsubmitted Reports (Doctor) — iOS Implementation Spec

## Overview

Doctors must file a consultation report after every consultation. **Until they do, the server keeps them "in session"** and routes no new consultation requests to them. This feature surfaces that backlog and gives them a direct path to clear it.

Three UI surfaces, one data source, zero new endpoints.

Reference (source of truth):
- Android list screen: `feature/doctor/.../DoctorUnsubmittedReportsScreen.kt`
- Android VM: `feature/doctor/.../DoctorUnsubmittedReportsViewModel.kt`
- Dashboard badge + gating dialog: `feature/doctor/.../DoctorDashboardScreen.kt` (lines ~253–315, ~820–830, ~1310–1370)
- Network: `core/network/.../DoctorConsultationService.kt` (`getUnsubmittedReports`)
- Backend:
  - `supabase/migrations/20260404200000_block_requests_until_report_submitted.sql`
  - `supabase/migrations/20260416100000_fix_stale_cleanup_report_submitted.sql`
  - `supabase/functions/generate-consultation-report/index.ts`

---

## 1 — User-Visible Behavior

```
Doctor finishes a consultation
        │
        ▼
  status becomes "completed"  +  report_submitted=false
        │
        ▼
  server keeps doctor in_session=true  ──► NO new requests will arrive
        │
        ▼
  ┌──────────────────────────────────────────────────────────────────┐
  │  Dashboard detects count>0 and does three things:                │
  │   (a) renders an amber "N unsubmitted report(s)" badge           │
  │   (b) auto-opens a blocking dialog on appear (dismiss-once)      │
  │   (c) if doctor toggles OFFLINE→ONLINE while count>0,            │
  │       re-opens the dialog instead of going online                │
  └──────────────────────────────────────────────────────────────────┘
        │  (tap badge, or "File Reports Now" in dialog)
        ▼
  Unsubmitted Reports list screen (pull-to-refresh)
        │  (tap a row)
        ▼
  Consultation Detail → the report sheet auto-opens on completed phase
        │  (submit report via generate-consultation-report)
        ▼
  Server sets report_submitted=true → trigger clears in_session → count drops
        │
        ▼
  Back on dashboard: list empty, badge gone, doctor can go ONLINE
```

---

## 2 — The Three UI Surfaces

All three live on the doctor portal. Brand tokens below match the Android implementation; they are hex-exact.

### Shared Colors

| Token | Hex |
|---|---|
| `AmberBg` (surface) | `#FFF7ED` |
| `AmberAccent` (icon dot) | `#EA580C` |
| `AmberBorder` (card border) | `#FCD6B4` (badge) / `#EA580C` @ 30 % opacity (list rows) |
| `AmberTitleText` | `#B45309` |
| `AmberSubtitleText` | `#92400E` |
| `BadgeRed` (count circle) | `#DC2626` |
| `BrandTeal` | `#2A9D8F` |
| `ScreenBg` | `#F8FFFE` |
| `CardBorder` | `#E5E7EB` |
| Body text | `#000000` (per project convention — never grey) |

### 2.1 — Dashboard Badge

**Location:** Doctor dashboard, immediately below the online/offline toggle, horizontal padding 12 pt. Visible only when `unsubmittedReportsCount > 0`.

**Layout (left → right, 12 pt padding):**

```
┌─────────────────────────────────────────────────────────────────┐
│  [ ✎ ]③   N unsubmitted report(s)                         ▶     │
│           Tap to file and continue working                      │
└─────────────────────────────────────────────────────────────────┘
     │
     └── orange circle 28×28 pt with pencil glyph (✎, 14 pt white)
         red badge 18×18 pt with "N" (bold, white; 10 pt, or 8 pt if N≥10)
         positioned top-right with x=+4, y=-4 offset
```

**Styling:**
- Container: `AmberBg` fill, 1 pt `AmberBorder` (`#FCD6B4`), 12 pt corner radius, tappable.
- Left icon tile: 28×28 circle, `AmberAccent` fill, pencil SF Symbol `pencil` (14 pt, white).
- Red count badge: 18×18 circle, `BadgeRed` fill, centered "N" text.
- Title: `N unsubmitted report(s)` — 14 pt, bold, `AmberTitleText`.
- Subtitle: `Tap to file and continue working` — 11 pt, regular, `AmberSubtitleText`.
- Right glyph: play-style chevron `▶` (SF Symbol `chevron.right`, 12 pt, `AmberTitleText`).
- **Tap** → pushes *Unsubmitted Reports* list screen.

### 2.2 — Auto-Opening Blocking Dialog

**Trigger** — on dashboard, fires a `LaunchedEffect`-equivalent keyed on `unsubmittedReportsCount`:

| State | Action |
|---|---|
| `count == 0` | reset `dismissedThisSession = false`, hide dialog |
| `count > 0` && `!dismissedThisSession` | show dialog |

`dismissedThisSession` must survive configuration changes / rotations but **not** app process death → back it with the equivalent of `@SceneStorage` (SwiftUI) or a `@StateObject` persisted across view lifecycle.

**Content:**
- Title: `File pending reports` (bold, black)
- Body: `You have %d consultation report(s) waiting to be submitted. You won't receive new consultation requests until every report is filed.`
- Confirm button: `File Reports Now` — BrandTeal, semibold → navigates to list screen; sets `dismissedThisSession = true`.
- Cancel button: `Later` — black → sets `dismissedThisSession = true` and closes.

**The blocking rule (very important):**
```swift
// Doctor tries to toggle ONLINE while count > 0:
if uiState.unsubmittedReportsCount > 0 && !uiState.isOnline {
    showUnsubmittedDialog = true           // re-open, do NOT toggle
} else {
    viewModel.onToggleOnline()
}
```
Going **OFFLINE is always allowed**. Only ONLINE is blocked.

### 2.3 — Unsubmitted Reports List Screen

Full screen, pushed onto the nav stack.

**Screen chrome:**
- Background: `ScreenBg` (`#F8FFFE`).
- Header: back button (system chevron.left, 36 pt tap target, tint black) + title `Unsubmitted Reports` (20 pt, semibold, black), 12 pt padding.
- Body: `List`/`LazyVStack` with pull-to-refresh, content insets 12 pt horizontal / 8 pt vertical, 10 pt row spacing.

**First cell (hint row):**
- Text `Tap a row to open and file the report.` — 13 pt, regular, black, 4 pt bottom padding.

**Row layout:**

```
┌────────────────────────────────────────────────────────────────┐
│  Patient ABC123                                           ✎     │
│  Chief complaint text (max 2 lines, truncate)                   │
│  Wed, Apr 17 at 14:32                                           │
└────────────────────────────────────────────────────────────────┘
```

- Container: `AmberBg`, 1 pt border `AmberAccent` @ 30 % opacity, 12 pt radius, 14 pt padding.
- Leading column (flex 1):
  - Line 1: `Patient <id>` (or `Patient —` if blank) — 15 pt, semibold, black.
  - Line 2 (only if chief-complaint present): 13 pt, regular, black, maxLines 2.
  - Line 3: ended-at timestamp formatted `EEE, MMM d 'at' HH:mm`, **Africa/Nairobi time zone (EAT)**, English locale for the format pattern — 12 pt, black.
- Trailing icon: SF Symbol `pencil`, 22 pt, `AmberAccent`. Accessibility label: `File report`.
- **Tap row** → push `ConsultationDetail(consultationId)`. The detail screen auto-opens the report sheet when phase is completed — **do not route to a separate "report" screen**; that stub is deprecated.

**State overlays (inside pull-to-refresh container):**

| `isLoading` | Show centered `ProgressView` tinted BrandTeal. |
| `items.isEmpty` (after load) | Show empty state (see below) |
| otherwise | Render list |

**Empty state (centered, 32 pt padding):**
- SF Symbol `doc.text`, 64 pt, BrandTeal
- 16 pt spacer
- Title `All reports submitted` — 18 pt, semibold, black
- 8 pt spacer
- Body `You're clear to take new consultations.` — 14 pt, regular, black

### 2.4 — Localization Keys

Use the existing shared string catalog (all six languages already shipped). Keys:

| Key | English |
|---|---|
| `unsubmitted_reports_title` | Unsubmitted Reports |
| `unsubmitted_reports_badge_title` | %d unsubmitted report(s) |
| `unsubmitted_reports_badge_subtitle` | Tap to file and continue working |
| `unsubmitted_reports_hint` | Tap a row to open and file the report. |
| `unsubmitted_reports_row_patient` | Patient %@ |
| `unsubmitted_reports_file_cta` | File report |
| `unsubmitted_reports_empty_title` | All reports submitted |
| `unsubmitted_reports_empty_body` | You're clear to take new consultations. |
| `unsubmitted_reports_dialog_title` | File pending reports |
| `unsubmitted_reports_dialog_body` | You have %d consultation report(s) waiting to be submitted. You won't receive new consultation requests until every report is filed. |
| `unsubmitted_reports_dialog_file_now` | File Reports Now |
| `unsubmitted_reports_dialog_later` | Later |

Strings exist in `values/`, `values-sw/`, `values-fr/`, `values-es/`, `values-ar/`, `values-hi/` on Android; copy translations verbatim into the iOS `.strings` files.

---

## 3 — Connectivity Layer

### 3.1 — Endpoint

Single call, PostgREST SELECT against `consultations`:

```
GET /rest/v1/consultations
    ?select=consultation_id,service_type,service_tier,consultation_type,
            chief_complaint,updated_at,session_end_time,
            patient_sessions(patient_id)
    &doctor_id=eq.<doctorId>
    &status=eq.completed
    &report_submitted=eq.false
    &order=updated_at.desc
```

Headers:
- `apikey: <SUPABASE_ANON_KEY>`
- `Authorization: Bearer <doctor_access_token>`
- `Accept: application/json`

Response is an array of rows. Use `supabase-swift` (equivalent of Android's `supabase-kt`) or a hand-rolled PostgREST query — **do not** create a new Edge Function for this; the Android implementation hits the table directly and RLS handles authorization.

### 3.2 — DTO

```swift
struct UnsubmittedReportRow: Decodable {
    let consultationId: String           // "consultation_id"
    let serviceType: String              // "service_type", default "general"
    let serviceTier: String              // "service_tier", default "ECONOMY"
    let consultationType: String?        // "consultation_type"
    let chiefComplaint: String?          // "chief_complaint"
    let updatedAt: String                // ISO-8601
    let sessionEndTime: String?          // ISO-8601, "session_end_time"
    let patientSessions: PatientRef?     // embedded

    struct PatientRef: Decodable {
        let patientId: String?           // "patient_id"
    }
}
```

Use snake_case ↔ camelCase key mapping (either `CodingKeys` or a `JSONDecoder.keyDecodingStrategy = .convertFromSnakeCase`).

### 3.3 — UI Model Mapping

Compute `endedAtMillis` with the following precedence (same as Android):

```swift
let iso = row.sessionEndTime ?? row.updatedAt
let endedAt = ISO8601DateFormatter().date(from: iso) ?? Date()
```

Format for display using the device locale **for day-of-week & month names**, but keep the Android `'at'` literal (or its localized equivalent if your string catalog has one — Android uses English-only `EEE, MMM d 'at' HH:mm`; match that unless you decide to localize explicitly). Time zone: `TimeZone(identifier: "Africa/Nairobi")`.

### 3.4 — Auth Token Handling

Before the request, call the shared token manager to pull the **current** access/refresh token (they may have been rotated by another call):

```swift
let access = try await tokenManager.accessToken()   // refreshes if stale
let refresh = try await tokenManager.refreshToken()
supabase.auth.importSession(accessToken: access, refreshToken: refresh)
```

If the call 401s, the shared `Authenticator` must refresh the token and retry once — identical contract to the Android `TokenRefreshAuthenticator`. See `ios-homepage-spec.md` / existing iOS auth spec for token-manager semantics.

### 3.5 — Error Mapping

Map transport result into the UI state the same way Android does:

| Transport result | iOS UI behavior |
|---|---|
| Success, `data` | `items = mapped; errorMessage = nil; isLoading = false` |
| HTTP error (non-2xx with body) | `errorMessage = body.message ?? "Error"` |
| Network unreachable | `errorMessage = "Network error"` |
| 401 after refresh retry | `errorMessage = "Unauthorized"` — app-wide auth gate re-triggers |

The screen **renders the list even if errorMessage is non-nil** when `items` are stale — don't wipe the list on a refresh failure.

### 3.6 — Realtime?

**Not used.** The Android implementation polls on dashboard load and on pull-to-refresh. Do not add a realtime subscription on iOS either — count consistency comes from the dashboard refresh triggers (foreground, after completing a consultation, manual refresh).

---

## 4 — Dashboard Integration & Lifecycle

### 4.1 — Count Fetched Alongside Consultations

On every dashboard fetch, the doctor's consultation list **and** the unsubmitted-reports count are fetched. On iOS, extend the `DoctorDashboardViewModel` equivalent so it:

```swift
private func fetchUnsubmittedReportsCount(doctorId: String) async {
    let result = await consultationService.getUnsubmittedReports(doctorId: doctorId)
    if case .success(let rows) = result {
        state.unsubmittedReportsCount = rows.count
    } // non-success: log but do not overwrite prior count
}
```

Invoke this **before** `fetchConsultations` or unconditionally at the top of each refresh — Android comments specifically note: "Always refresh the unsubmitted-reports badge; it's independent of the main consultations list (which may early-exit when empty)." Preserve that property on iOS.

### 4.2 — Count Invalidation Triggers

Refetch the count in all of these situations:

1. Doctor dashboard appears (first time + every return).
2. App foregrounded while dashboard is topmost.
3. Pull-to-refresh on dashboard.
4. After the report-submit flow completes (`generate-consultation-report` returned success) — push fresh state without waiting for a manual refresh.
5. Dashboard realtime consultation update (if you already have one for the main list). No new subscription needed.

---

## 5 — Backend Contract (read-only reference)

iOS does not write any of these directly, but must understand them to predict counts correctly.

### 5.1 — `consultations.report_submitted` Column

- Type `BOOLEAN NOT NULL DEFAULT false`.
- Set to `true` by `generate-consultation-report` immediately after inserting into `consultation_reports`.
- Set to `true` by `fn_close_stale_consultations()` when abandoning stale sessions (no report will ever be written for those — they shouldn't block the doctor forever).

### 5.2 — `fn_sync_doctor_in_session()` Trigger

Fires on `INSERT`, `DELETE`, or `UPDATE OF status, report_submitted`. Sets `doctor_profiles.in_session = true` iff the doctor has at least one consultation matching:

```sql
status IN ('active','awaiting_extension','grace_period')
  OR (status = 'completed' AND report_submitted = false)
```

Implication: until the doctor finishes **all** pending reports, their `in_session = true` and the dispatch system will skip them.

### 5.3 — `fn_close_stale_consultations()`

Cron-invoked. Closes:
- `active` older than 2 h
- `awaiting_extension` older than 30 m
- `grace_period` older than 30 m

All three transitions now also set `report_submitted = true` so the doctor isn't locked out indefinitely by a crashed session.

### 5.4 — Edge Function: `generate-consultation-report`

Called from the **Consultation Detail → Report sheet** flow. In the same invocation:

1. Inserts into `consultation_reports`.
2. Updates the consultation with `status = 'completed', report_submitted = true`.
3. (ROYAL tier only) Writes any medication timetables.

When this returns 200, the iOS dashboard should **decrement optimistically** (or call the list fetch again) — `in_session` clears automatically on the server via the trigger, so any offline→online toggle the doctor attempts next will succeed.

### 5.5 — RLS Assumptions

The direct-to-PostgREST SELECT in §3.1 works because `consultations` RLS already restricts doctors to rows where `doctor_id = auth.uid()`. iOS does not need to add filters for security — only for correctness — but keep them in the query so the server plan is cheap.

---

## 6 — Swift Reference Skeletons

### 6.1 — Service

```swift
final class DoctorConsultationService {
    private let supabase: SupabaseClient

    func getUnsubmittedReports(doctorId: String) async -> Result<[UnsubmittedReportRow], APIError> {
        do {
            let rows: [UnsubmittedReportRow] = try await supabase
                .from("consultations")
                .select("""
                    consultation_id,service_type,service_tier,consultation_type,
                    chief_complaint,updated_at,session_end_time,
                    patient_sessions(patient_id)
                """)
                .eq("doctor_id", value: doctorId)
                .eq("status", value: "completed")
                .eq("report_submitted", value: false)
                .order("updated_at", ascending: false)
                .execute()
                .value
            return .success(rows)
        } catch let error as PostgrestError where error.isUnauthorized {
            return .failure(.unauthorized)
        } catch is URLError {
            return .failure(.network)
        } catch {
            return .failure(.message(error.localizedDescription))
        }
    }
}
```

### 6.2 — ViewModel

```swift
@MainActor
final class DoctorUnsubmittedReportsViewModel: ObservableObject {
    struct UIState {
        var isLoading = true
        var isRefreshing = false
        var items: [UnsubmittedReportItem] = []
        var errorMessage: String?
    }

    @Published private(set) var state = UIState()

    private let auth: AuthRepository
    private let service: DoctorConsultationService

    init(auth: AuthRepository, service: DoctorConsultationService) {
        self.auth = auth; self.service = service
        Task { await load(showSpinner: true) }
    }

    func refresh() async { await load(showSpinner: false) }

    private func load(showSpinner: Bool) async {
        if showSpinner { state.isLoading = true } else { state.isRefreshing = true }
        state.errorMessage = nil

        guard let doctorId = await auth.currentSession?.user.id else {
            state = .init(isLoading: false, items: [])
            return
        }

        switch await service.getUnsubmittedReports(doctorId: doctorId) {
        case .success(let rows):
            state.items = rows.map(UnsubmittedReportItem.init(from:))
            state.errorMessage = nil
        case .failure(.unauthorized):
            state.errorMessage = "Unauthorized"
        case .failure(.network):
            state.errorMessage = "Network error"
        case .failure(.message(let m)):
            state.errorMessage = m
        }
        state.isLoading = false
        state.isRefreshing = false
    }
}
```

### 6.3 — List View

```swift
struct DoctorUnsubmittedReportsView: View {
    @StateObject var vm: DoctorUnsubmittedReportsViewModel
    let onSelect: (_ consultationId: String) -> Void
    let onBack: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            header
            Group {
                if vm.state.isLoading { progress }
                else if vm.state.items.isEmpty { empty }
                else { list }
            }
            .refreshable { await vm.refresh() }
        }
        .background(Color(hex: "#F8FFFE"))
    }

    private var list: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 10) {
                Text("unsubmitted_reports_hint".localized)
                    .font(.system(size: 13)).foregroundColor(.black)
                ForEach(vm.state.items, id: \.consultationId) { item in
                    ReportRow(item: item) { onSelect(item.consultationId) }
                }
            }
            .padding(.horizontal, 12).padding(.vertical, 8)
        }
    }
    // header / empty / progress / ReportRow omitted — layout specified in §2.3
}
```

---

## 7 — Acceptance Criteria

- [ ] After a doctor completes a consultation without submitting a report, the dashboard shows the amber badge with the correct count within one dashboard refresh cycle.
- [ ] Tapping the badge opens the list screen with all matching consultations, newest first.
- [ ] The list row shows patient ID (or em-dash fallback), chief complaint (max 2 lines), and an EAT-formatted timestamp.
- [ ] Pull-to-refresh re-issues the PostgREST query and updates the list.
- [ ] Tapping a row opens Consultation Detail (not a dedicated report route) — the detail auto-opens the report sheet for completed consultations.
- [ ] On dashboard appear with count > 0, the blocking dialog opens exactly once per session (dismiss persists until count drops to 0 or app relaunches).
- [ ] Attempting to toggle OFFLINE → ONLINE while count > 0 **re-opens the dialog** and does not toggle.
- [ ] Toggling ONLINE → OFFLINE always works, regardless of count.
- [ ] After submitting a pending report, the dashboard badge disappears and online toggle works.
- [ ] Network failure on refresh sets `errorMessage` but preserves the previously loaded list.
- [ ] 401 triggers the shared token refresh flow once; persistent 401 surfaces as "Unauthorized".
- [ ] No new Edge Function or RLS change introduced; all backend contracts are pre-existing.
- [ ] Badge, dialog and list localize correctly in English, Swahili, French, Spanish, Arabic, Hindi.

---

## 8 — Out of Scope (explicitly not iOS's job)

- Defining `report_submitted`, writing the trigger, or stale-cleanup logic — all exist.
- Submitting the report itself — that flow is Consultation Detail → report sheet → `generate-consultation-report` (covered by the existing consultation/report iOS spec; if you don't have one yet, request a separate doc for it).
- Any realtime subscription on `consultations` specifically for this feature — polling is sufficient.
- Surfacing this anywhere in the patient app — it is a doctor-only concern.

---

## References

- `feature/doctor/src/main/kotlin/com/esiri/esiriplus/feature/doctor/screen/DoctorUnsubmittedReportsScreen.kt`
- `feature/doctor/src/main/kotlin/com/esiri/esiriplus/feature/doctor/viewmodel/DoctorUnsubmittedReportsViewModel.kt`
- `feature/doctor/src/main/kotlin/com/esiri/esiriplus/feature/doctor/screen/DoctorDashboardScreen.kt` (badge + dialog)
- `core/network/src/main/kotlin/com/esiri/esiriplus/core/network/service/DoctorConsultationService.kt`
- `supabase/migrations/20260404200000_block_requests_until_report_submitted.sql`
- `supabase/migrations/20260416100000_fix_stale_cleanup_report_submitted.sql`
- `supabase/migrations/20260418100000_capacity_indexes.sql` (index on `doctor_id, status, report_submitted`)
- `supabase/functions/generate-consultation-report/index.ts`
