# iOS — Patient Dashboard (Home) — Implementation Spec

iOS implementation spec for the v1.3 patient dashboard. Mirrors the shipped
Android implementation in:

- `feature/patient/src/main/kotlin/.../screen/PatientHomeScreen.kt`
- `feature/patient/src/main/kotlin/.../viewmodel/PatientHomeViewModel.kt`

Hand this to the iOS developer. The Android files are the source of truth for
visual + behavioural details — if anything here is ambiguous, prefer matching
the Android shipped behaviour.

---

## 1. Visual layout (v1.3)

The home is a single non-scrolling Scaffold tinted with `TealBg` (`#F4FAF7`):

```
┌──────────────────────────────────────────────────┐
│  [Top bar]                                       │
│   Hi, friend.                       (⚙ settings) │
│   ID: ESR-******-P8FP                            │
├──────────────────────────────────────────────────┤
│  ┌────────────────────────────────────────────┐  │
│  │ HERO CARD (gradient teal → ink)            │  │
│  │  • Animated raindrop ripples (water)       │  │
│  │  • Eyebrow "BOOK A CONSULTATION"           │  │
│  │  • Headline "Need help? *Talk to a doc.*"  │  │
│  │  • Subtitle                                │  │
│  │  • Breathing CTA pill: "Start consultation"│  │
│  └────────────────────────────────────────────┘  │
│                                                  │
│  ┌────────────────────────────────────────────┐  │
│  │ PENDING BADGE (only if count > 0)          │  │
│  │  • Pulsing red dot                         │  │
│  │  • "n consultation(s) pending"             │  │
│  │  • Inline pill: "Resume"                   │  │
│  │   (whole badge is tappable)                │  │
│  └────────────────────────────────────────────┘  │
│                                                  │
│  RECORDS                                         │
│  ┌──────────────┐ ┌──────────────┐               │
│  │ Past chats   │ │ Reports • ●  │               │
│  └──────────────┘ └──────────────┘               │
│  ┌──────────────┐ ┌──────────────┐               │
│  │ Appointments │ │ My health    │               │
│  └──────────────┘ └──────────────┘               │
├──────────────────────────────────────────────────┤
│  Need a hand? Call +255 663 582 994              │
│              info@esiri.africa                   │
└──────────────────────────────────────────────────┘
```

Pull-to-refresh on the body re-runs the dashboard's read sequence. There is
**no scrolling** beyond pull-to-refresh — everything fits on one viewport.

Animations to mirror:

- **Raindrop ripples** behind the hero gradient (`RainDropsCanvas`) — same
  animation as the welcome screen hero badge.
- **Breathing CTA** — the white "Start consultation" pill scales 1.0 → 1.04
  on a 1.5 s ease-in-out, with a synced 0 → 4 pt nudge on the trailing
  arrow, and a glow border pulse 0.10 → 0.28 alpha. All three on the same
  cycle.
- **Pending badge** — pulsing red dot (1.0 → 1.5 scale).

Visual tokens (must match `core/ui/theme/Color.kt`):

| Token       | Hex        |
|-------------|------------|
| TealBg      | `#F4FAF7`  |
| Teal        | `#2DBE9E`  |
| TealDeep    | `#1E8E76`  |
| TealSoft    | `#E0F2EB`  |
| Ink         | `#14201D`  |
| InkSoft     | `#2A3A36`  |
| Muted       | `#8A9893`  |
| Hairline    | `#DCE7E1`  |
| Hero start  | `#2DBE9E`  |
| Hero end    | `#14302A`  |

Fonts:

- Display / italic accents → Instrument Serif (regular + italic)
- Body / labels → Geist (regular / medium / semibold)

---

## 2. State the screen exposes

Mirror this `PatientHomeUiState`:

```swift
struct PatientHomeUiState {
    var patientId: String           = ""
    var maskedPatientId: String     = ""
    var soundsEnabled: Bool         = true
    var isLoading: Bool             = true
    var isRefreshing: Bool          = false
    var activeConsultation: Consultation?            // any in-session
    var pendingRatingConsultation: Consultation?     // shows rating sheet
    var ongoingConsultations: [Consultation]         // drives pending badge
    var hasUnreadReports: Bool      = false
    var isDeletingAccount: Bool     = false
}
```

`patientId` is the session UUID (used as the patient's identity key
throughout). `maskedPatientId` masks the middle segments — see §10.

---

## 3. Where each UI element gets its value

| UI element                       | Source                                                                                         |
|----------------------------------|------------------------------------------------------------------------------------------------|
| Top-bar greeting                 | static string                                                                                  |
| Top-bar masked patient ID        | `maskPatientId(session.user.id)` — see §10                                                     |
| Settings gear                    | opens `SettingsSheet` (see §8)                                                                 |
| Hero CTA                         | navigates to consultation request flow                                                         |
| Pending badge count              | `ongoingConsultations.count` — see §6                                                          |
| Pending badge tap (whole row)    | navigate to "Ongoing consultations" list                                                       |
| Past chats card                  | navigate to consultation history                                                               |
| Reports card unread dot          | `hasUnreadReports` — see §7                                                                    |
| Reports card tap                 | call `markAllReportsRead()` then navigate to reports                                           |
| Appointments card                | navigate to appointments list                                                                  |
| My health card                   | navigate to demographics / profile                                                             |
| Help footer phone                | `tel:+255663582994`                                                                            |
| Help footer email                | `mailto:info@esiri.africa`                                                                     |
| Rating sheet                     | shown when `pendingRatingConsultation != nil` — see §9                                         |
| Deleting overlay                 | shown while `isDeletingAccount == true`                                                        |

---

## 4. Auth model

All patient calls use a custom HS256 patient JWT, **not** the Supabase
`Authorization` header. The combination on every request is:

```
apikey:           <SUPABASE_ANON_KEY>
Authorization:    Bearer <SUPABASE_ANON_KEY>
X-Patient-Token:  <patient_jwt>
Content-Type:     application/json
```

> Why two layers — `Authorization: Bearer ANON` gets past the Supabase
> gateway; `X-Patient-Token` is what the edge function actually validates.
> iOS must always send both.

JWT shape (HS256):

```
header.payload.signature

payload: {
  "sub":      "<session UUID>",
  "session_id": "<session UUID>",
  "role":     "authenticated",
  "app_role": "patient",
  "iat":      <unix>,
  "exp":      <unix + 86400>
}
```

The token is created by `create-patient-session`. iOS should store the
access token + refresh token + session_id in Keychain (the Android
equivalent is `EncryptedSharedPreferences` plus a plaintext `SessionBackup`).
Auto-refresh on 401 via `refresh-patient-session`.

When making a PostgREST query (e.g., for consultations) the same
`X-Patient-Token` header applies — the patient JWT is honoured by the
edge-side helpers, not by Supabase RLS.

---

## 5. Edge functions involved

All endpoints below are at `<SUPABASE_URL>/functions/v1/<name>`.

### `create-patient-session`
- **Method:** `POST`
- **Auth:** anonymous (no `X-Patient-Token`)
- **Request:** `{ "fcm_token": "...", "legacy_patient_id": "...", "device_info": { ... } }` — all optional
- **Response:**
  ```json
  {
    "session_id": "uuid",
    "patient_id": "ESR-XXXX-XXXX",
    "access_token": "<HS256 jwt>",
    "refresh_token": "<96 hex>",
    "token_type": "Bearer",
    "expires_in": 86400,
    "expires_at": "iso8601",
    "refresh_expires_at": "iso8601",
    "legacy_data_linked": false
  }
  ```
- Called once at first launch; not from the dashboard itself.

### `refresh-patient-session`
- **Method:** `POST`
- **Auth:** anonymous
- **Request:** `{ "refresh_token": "<token>" }`
- **Response:** same shape as `create-patient-session`
- Called transparently when any other call returns 401.

### `update-fcm-token`
- **Method:** `POST`
- **Auth:** `X-Patient-Token`
- **Request:** `{ "fcm_token": "<APNs/FCM token>" }`
- **Response:** `{ "ok": true }`
- Push it on every cold launch. Retry up to 3 times on failure (the Android
  side does so).

### `resolve-patient-location`
- **Method:** `POST`
- **Auth:** `X-Patient-Token`
- **Request:**
  ```json
  {
    "region": "Dar es Salaam",
    "district": "Kinondoni",
    "ward": "Sinza",
    "street": "Sinza B"
  }
  ```
  All four fields optional — server canonicalises whatever subset it gets
  (typically from a forward-geocoded GPS hit).
- **Response:**
  ```json
  {
    "region": "...",
    "district": "...",
    "ward": "...",
    "street": "..."
  }
  ```
- **Side effect (server):** updates the caller's `patient_sessions` row with
  the canonical hierarchy.
- **When to call:** on cold launch if `CLLocationManager` authorisation is
  `.authorizedWhenInUse` or `.authorizedAlways`; and immediately after the
  user grants permission.

### `get-patient-reports`
- **Method:** `POST`
- **Auth:** `X-Patient-Token`
- **Request:** `{}`
- **Response:**
  ```json
  {
    "reports": [
      {
        "report_id": "uuid",
        "consultation_id": "uuid",
        "patient_session_id": "uuid",
        "doctor_name": "Dr. ...",
        "consultation_date": "iso8601",
        "patient_age": "string",
        "patient_gender": "string",
        "diagnosed_problem": "string",
        "category": "string",
        "severity": "string",
        "presenting_symptoms": "string",
        "assessment": "string",
        "plan": "string",
        "follow_up": "string",
        "follow_up_recommended": false,
        "treatment_plan": "string",
        "history": "string",
        "follow_up_plan": "string",
        "prescriptions": { ... } ,
        "verification_code": "string",
        "created_at": "iso8601"
      }
    ]
  }
  ```
- Used by the dashboard solely to compute the unread dot — see §7.

### `rate-doctor`
- **Method:** `POST`
- **Auth:** `X-Patient-Token`
- **Request:**
  ```json
  {
    "consultation_id": "uuid",
    "rating": 1-5,
    "comment": "string (required when rating <= 3)"
  }
  ```
- **Response:** `{ "rating_id": "uuid", "ok": true }`
- Server validates: consultation status = `completed`, caller is the patient
  on the consultation, no existing rating. Idempotent — "already rated"
  returns success.
- Called from the rating sheet shown over the dashboard (§9).

### `submit-deletion-feedback`
- **Method:** `POST`
- **Auth:** `X-Patient-Token`
- **Request:**
  ```json
  {
    "reasons": ["no_longer_needed" | "privacy" | "too_slow" | "bad_experience" | "other"],
    "comment": "<= 2000 chars (optional)",
    "locale": "en-US (optional, BCP-47)"
  }
  ```
- **Response:** `{ "recorded": true }`
- Stored anonymously — no FK back to the session — so the row outlives the
  30-day purge.
- Called immediately **before** `delete-patient-account` in the deletion flow.

### `delete-patient-account`
- **Method:** `POST`
- **Auth:** `X-Patient-Token`
- **Request:** `{}`
- **Response:**
  ```json
  { "deleted": true, "purge_after_days": 30, "timestamp": "iso8601" }
  ```
- **Side effect:** soft-deletes the session row (`is_active=false`,
  `deleted_at=now()`, `purge_at=now()+30d`). The token is invalid from this
  moment on; iOS should run its local logout right after the call returns.
- A pg_cron job hard-purges the row + cascades after 30 days.

### Direct PostgREST: `consultations`
- **Method:** `GET`
- **URL:** `<SUPABASE_URL>/rest/v1/consultations?patient_session_id=eq.<sessionId>&select=*&order=created_at.desc`
- **Auth:** `X-Patient-Token` (plus the standard `apikey`/`Authorization`)
- iOS uses this to seed the local consultations cache (see §6 sync model).
  Android currently does this from PostgREST directly — there is **no**
  dedicated `list-patient-consultations` edge function as of today.

> **Gotcha:** the Android Retrofit interface still names the query
> parameter `patient_id`, but the actual column on the table is
> `patient_session_id`. iOS should use `patient_session_id` — that's the
> truth on the server.

---

## 6. Pending badge — what counts as "ongoing"

The pending badge shows the count of consultations classified as ongoing for
the current session. The Room query is:

```sql
SELECT * FROM consultations
WHERE patient_session_id = :sessionId
  AND (
    LOWER(status) IN ('active', 'in_progress', 'awaiting_extension', 'grace_period')
    OR (
      LOWER(status) = 'completed'
      AND follow_up_expiry > :nowMillis
      AND (follow_up_max = -1 OR follow_up_count < follow_up_max)
    )
  )
ORDER BY updated_at DESC
```

In English:

- **Active** — `status` is one of `active`, `in_progress`, `awaiting_extension`,
  `grace_period`.
- **Reopen-able follow-up** — `status = completed` AND the follow-up window
  is still open (`follow_up_expiry > now`) AND the patient still has
  follow-ups left (`follow_up_max == -1` for unlimited Royal, otherwise
  `follow_up_count < follow_up_max`).

iOS should:

1. On dashboard load (and on pull-to-refresh), `GET /rest/v1/consultations?patient_session_id=eq.<sessionId>` and cache locally.
2. Filter the cached list with the same predicate.
3. Show the badge only when the filtered list is non-empty.

Tapping anywhere on the badge (badge body, count, or "Resume" pill) navigates
to the ongoing-consultations list — they all share the same destination.

---

## 7. Reports unread dot — purely device-local

There is **no server-side unread flag**. The Android implementation:

1. Fetches every report via `get-patient-reports`.
2. Compares each `report_id` against a `Set<String>` stored at
   `SharedPreferences("report_read_prefs"):read_report_ids`.
3. Sets `hasUnreadReports = true` if any returned `report_id` is missing
   from the set.
4. When the user taps the Reports card, marks **all** currently-returned
   `report_id`s as read by writing them into the set.

iOS equivalent: store the set in `UserDefaults` (key
`patient.read_report_ids`). Same logic.

> Implication: the unread state is per-device. A user reading reports on
> Android won't clear the dot on iOS, and vice versa. This is intentional
> in the current design.

---

## 8. Settings sheet

Bottom sheet (modal) with three rows:

1. **Sounds** — toggle. Persist locally (`UserDefaults`, key
   `patient.sounds_enabled`, default `true`). The setting governs in-app
   sound effects (rating swoosh, message ping, etc.). On iOS, mirror that
   convention — read this single key from each sound-emitting view.
2. **Logout** — dismiss the sheet, show a confirmation alert ("Log out?
   You'll need your patient ID to log back in"), on confirm: clear
   Keychain, drop local DB, navigate to splash/welcome.
3. **Delete account** — dismiss the sheet, show a destructive confirmation
   alert that requires the user to type the literal phrase
   `delete my account` to enable the destructive button. On confirm: open
   the **Deletion Feedback Sheet**.

### Deletion feedback sheet

Optional. The user can dismiss without filling anything in.

- Multi-select chips: `no_longer_needed`, `privacy`, `too_slow`,
  `bad_experience`, `other`.
- Free-text comment (max 2000 chars).
- "Send & delete" CTA → submit reasons + comment to
  `submit-deletion-feedback`, then immediately call
  `delete-patient-account`, then run local logout.
- "Skip" CTA → call `delete-patient-account` directly, then local logout.

While both calls are in flight, show a centred opaque overlay with a teal
spinner and the label "Deleting your account…". This is the
`isDeletingAccount` state. Block back/swipe-down dismiss while it's up.

---

## 9. Rating prompt

Shown over the dashboard when there is at least one consultation that's
**completed but unrated**.

### Detection

```sql
SELECT c.* FROM consultations c
LEFT JOIN doctor_ratings r ON c.consultation_id = r.consultation_id
WHERE LOWER(c.status) = 'completed'
  AND r.rating_id IS NULL
  AND c.patient_session_id = :sessionId
ORDER BY c.updated_at DESC
LIMIT 1
```

iOS strategy: after fetching consultations from PostgREST (§6), also
`GET /rest/v1/doctor_ratings?patient_session_id=eq.<sessionId>&select=consultation_id`
once and compute the join client-side. Cache both lists.

### Sheet

- 1–5 star input.
- Optional free-text comment, **required if `rating <= 3`** (mirror the
  edge function's validation — rejects without comment).
- Submit → call `rate-doctor`. On 4xx, store the rating in a local pending
  queue (Android stores in Room `doctor_ratings` with `synced = false`)
  and retry on next dashboard load.
- "Not now" dismisses without saving — the prompt will re-appear on next
  launch until the user rates or the consultation row is purged.

---

## 10. Patient ID masking

```
ESR-ABCDEF-P8FP   →   ESR-******-P8FP
```

Algorithm (Android `maskPatientId`):

1. Split the ID by `-`.
2. If 3+ segments: keep first and last segment, replace each middle
   segment with `*` of the same length, rejoin with `-`.
3. If shorter (no segment structure): keep first 3 and last 4 chars,
   replace the middle with the same number of `*`.
4. If overall length ≤ 7: return as-is.

Use this in the top bar greeting line.

---

## 11. Sync model — pull only

The dashboard uses **no realtime subscriptions**. The model is:

- On screen load (init):
  1. Seed the `patient_sessions` row locally if missing (use the cached
     session UUID; insert a stub row).
  2. `resolve-patient-location` — best-effort, fire and forget.
  3. Fetch `consultations` from PostgREST → cache.
  4. Fetch `doctor_ratings` from PostgREST → cache.
  5. Fetch `get-patient-reports` → compute unread.
  6. Sync any locally-pending ratings (`synced=false`).
- On pull-to-refresh: re-run steps 3–6.
- On `applicationDidBecomeActive` (cold *or* warm resume): same as init.

Other screens (consultation, chat, find-doctor) use Postgres realtime
subscriptions to update their tables — those updates are written back to
the local cache, so the dashboard naturally picks them up the next time it
re-fetches. No realtime needed on the dashboard itself.

---

## 12. Recently added / changed edge functions (April 2026)

These are new since the v1.2 spec; iOS must implement them rather than
treating them as no-ops.

| Function | Purpose | Spec section |
|---|---|---|
| `submit-deletion-feedback` | Anonymous deletion-reason collection | §5, §8 |
| `delete-patient-account` | Soft-delete with 30-day grace | §5, §8 |
| `resolve-patient-location` | Canonical region/district/ward/street | §5 |
| `cleanup-expired-messages` | Server cron — 14-day chat TTL. iOS reads `created_at` and **client-side** hides messages older than 14 days, same as Android, in case the cron hasn't run yet. | — |

The `delete-patient-account` flow also depends on:

- Server migration `20260427110000_patient_account_deletion.sql` — adds
  `deleted_at`, `purge_at` columns + `fn_mark_patient_for_deletion` and
  `fn_purge_deleted_patients` RPCs, plus a pg_cron job. iOS doesn't need
  to read these columns directly — `validateAuth` already short-circuits a
  deleted session with a 401, so iOS just sees its tokens stop working
  after deletion.

---

## 13. Permissions

- **Notifications** — request on first dashboard load (Android requests
  `POST_NOTIFICATIONS` on Tiramisu+; iOS equivalent is the standard
  `UNUserNotificationCenter` `requestAuthorization` call). Push works
  without it; this is just for the system tray.
- **Location** — required for region/district/ward/street resolution.
  Android shows a hard gate elsewhere (memory: "location permission
  mandatory"); iOS should match. On the dashboard itself, we only
  re-resolve if permission is already granted — we do not re-prompt here.

---

## 14. Implementation checklist

```
[ ] Layout
    [ ] TealBg Scaffold with bottom help footer
    [ ] Top bar: greeting + masked ID + settings gear
    [ ] Hero card with raindrop ripples + breathing CTA
    [ ] Pending badge (visible when count > 0, pulsing dot)
    [ ] 2x2 quick card grid (Past chats, Reports, Appointments, My health)
    [ ] Reports card unread dot
    [ ] Pull-to-refresh

[ ] Auth
    [ ] X-Patient-Token + apikey + Authorization on every call
    [ ] Auto-refresh via refresh-patient-session on 401
    [ ] Keychain storage of access/refresh tokens + session_id

[ ] Data fetches on load and pull-to-refresh
    [ ] GET /rest/v1/consultations?patient_session_id=eq.<id>&select=*
    [ ] GET /rest/v1/doctor_ratings?patient_session_id=eq.<id>&select=consultation_id
    [ ] POST /functions/v1/get-patient-reports
    [ ] POST /functions/v1/resolve-patient-location (if location authorised)
    [ ] Sync pending ratings (rate-doctor with retry)

[ ] Local caches
    [ ] patient.sounds_enabled (UserDefaults, default true)
    [ ] patient.read_report_ids (UserDefaults, Set<String>)
    [ ] In-memory: consultations, ratings (not persisted; re-fetched each
        dashboard load is fine for v1)

[ ] Pending badge predicate
    [ ] status in {active, in_progress, awaiting_extension, grace_period}
    [ ] OR completed AND follow_up_expiry > now AND (follow_up_max == -1
        OR follow_up_count < follow_up_max)

[ ] Rating prompt
    [ ] Detect oldest completed-unrated consultation
    [ ] Show sheet with 1–5 stars, comment required if rating <= 3
    [ ] On submit: rate-doctor, idempotent (already-rated == success)
    [ ] On failure: queue locally, retry next load

[ ] Settings sheet
    [ ] Sounds toggle
    [ ] Logout (confirmation alert)
    [ ] Delete account (typed-phrase confirmation → feedback sheet
        → submit-deletion-feedback → delete-patient-account → logout)
    [ ] Deleting overlay (blocking, no dismiss)

[ ] Patient ID masking helper
```

---

## 15. Open gotchas

1. **`getConsultationsForPatient` query param mismatch on Android.** The
   Retrofit interface uses `?patient_id=eq.<id>` but the table column is
   `patient_session_id`. Android's call therefore returns
   nothing — but Past Chats still works because the local Room cache is
   hydrated by other flows. iOS should **use `patient_session_id`** — that
   matches the schema. We'll fix Android later.

2. **Consultation freshness after a fresh login.** Android relies on
   in-session realtime subscriptions in other screens to populate Room. A
   patient who logs out and back in won't see ongoing consultations until
   they navigate into a screen that fetches them. iOS doing a direct
   PostgREST fetch on every dashboard load (§11 step 3) actually fixes
   this and is the recommended behaviour.

3. **Race on deletion flow.** If the network drops between
   `submit-deletion-feedback` and `delete-patient-account`, the feedback
   is recorded but the session isn't deleted. The Android UI proceeds to
   the deletion step regardless of feedback failure. iOS should do the
   same — never block deletion on feedback succeeding.

4. **Rating sheet re-shows after rating until the consultation row
   refreshes.** The Android prompt is dismissed in-memory but reappears
   on next dashboard load if the local Room cache hasn't picked up the
   new `doctor_ratings` row yet. With the iOS fetch-from-PostgREST model
   (§9), this is fine because the freshly-fetched list will already
   include the new rating row.

5. **Sounds toggle scope.** Android currently reads
   `patient_prefs:sounds_enabled` from various points in the app. iOS
   should adopt a single `SoundsService` reading
   `UserDefaults.standard.bool(forKey: "patient.sounds_enabled")` and
   route every effect through it — don't scatter `UserDefaults` reads.

6. **JWT secret rotation.** The HS256 signing key (`SUPABASE_JWT_SECRET`)
   is server-side-only. If it rotates, all live patient tokens become
   invalid; the `refresh-patient-session` call would also fail and the
   user is forced through `create-patient-session` (or `recover-by-id`).
   iOS should treat any 401 from `refresh-patient-session` as a hard
   logout — not retry silently.
