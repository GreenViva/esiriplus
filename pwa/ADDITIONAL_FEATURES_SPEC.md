# eSIRI Plus — Additional Features Spec (for PWA Development)

This document describes all features added in the April 7, 2026 session that the PWA must implement.

---

## Table of Contents

1. [Serve as GP Toggle (Specialists)](#1-serve-as-gp-toggle-specialists)
2. [GP Listing Includes Specialists](#2-gp-listing-includes-specialists)
3. [Patient Greeting Flow](#3-patient-greeting-flow)
4. [Contact Us on Login Page](#4-contact-us-on-login-page)
5. [Real Acceptance Rate](#5-real-acceptance-rate)
6. [Logout Cleanup & Device Guard](#6-logout-cleanup--device-guard)
7. [Session Persistence & Recovery](#7-session-persistence--recovery)
8. [Patient Token Auto-Refresh](#8-patient-token-auto-refresh)

---

## 1. Serve as GP Toggle (Specialists)

### What
Specialist doctors can toggle "Serve as General Practitioner" on their dashboard. When enabled, they appear in GP patient listings alongside regular GPs, but are paid at GP rates.

### Doctor Dashboard UI
The toggle appears in the TopBar, below the online/offline switch, **only for verified specialists who are not suspended**:

```
┌──────────────────────────────────────┐
│  Dr. Mtambo         [Online ═══]    │
│  Specialist - Cardiologist           │
│                                      │
│  ┌────────────────────────────────┐  │
│  │ [GP]  Serve as General         │  │
│  │       Practitioner    [═══]    │  │
│  │       Visible on GP listings   │  │
│  └────────────────────────────────┘  │
└──────────────────────────────────────┘
```

### Toggle Row Styling
| State | Border | Background | Subtitle | Badge Color |
|-------|--------|------------|----------|-------------|
| OFF | `outline` (gray) | `surface` | "Toggle to appear on GP patient listings" | Gray `#9CA3AF` |
| ON | Blue `#2563EB` at 40% | Light blue `#EFF6FF` | "Visible on GP listings - Paid at GP rates" | Blue `#2563EB` |

### Badge
- 32dp circle with "GP" text (11sp Bold, white)
- Blue background when ON, gray when OFF

### API
**Toggle update:**
```
POST to Supabase PostgREST:
UPDATE doctor_profiles SET can_serve_as_gp = true/false WHERE doctor_id = :id
```
Requires doctor authentication (RLS: `doctor_id = auth.uid()`).

### Visibility Rules
- Only shown if `specialty = "specialist"` (case-insensitive check on both raw and display values)
- Only shown if `is_verified = true`
- Only shown if not suspended (`suspended_until` is null or past)

### Database Column
```sql
can_serve_as_gp BOOLEAN NOT NULL DEFAULT false  -- on doctor_profiles table
```

---

## 2. GP Listing Includes Specialists

### What
When a patient browses GP doctors, the list includes both regular GPs and specialists who have `can_serve_as_gp = true`.

### Edge Function: `list-doctors`
When `specialty = "gp"`:
```sql
WHERE is_verified = true
  AND is_banned = false
  AND (suspended_until IS NULL OR suspended_until <= NOW())
  AND (specialty = 'gp' OR (specialty = 'specialist' AND can_serve_as_gp = true))
```

For all other specialties, exact match: `WHERE specialty = :specialty`.

### Response
The `can_serve_as_gp` field is included in the response for each doctor. The client can use this to show a "Also serves as GP" badge if desired.

### Client Caching
When caching doctors locally (IndexedDB for PWA):
- Store `can_serve_as_gp` on each doctor profile
- When querying cached GP doctors, include specialists with `can_serve_as_gp = true`
- When replacing cached data, delete + insert atomically (transaction) to avoid flicker

---

## 3. Patient Greeting Flow

### What
When a patient enters a new consultation chat (after the doctor accepts), an animated greeting sequence plays before the chat input becomes active.

### Trigger
- Only on **new consultations** (no existing messages)
- **Not** on follow-up consultations
- Plays once per consultation — dismissed after patient picks a choice

### Sequence (Timed)

| Time | Phase | What Shows |
|------|-------|------------|
| 0ms | TYPING | Animated dots "..." in teal bubble |
| 1200ms | MSG_WELCOME | "Hello, welcome! 👋" slides in |
| 2000ms | TYPING | Dots again |
| 3000ms | MSG_SERVE | "Dr. {name} is here to serve you." slides in |
| 3800ms | TYPING | Dots again |
| 4800ms | MSG_CHOICES | "How would you like to proceed?" + two buttons |

### UI Layout
```
┌──────────────────────────────────────┐
│                                      │
│          (dark scrim overlay)        │
│                                      │
│  ┌─ teal bubble ──────────────────┐  │
│  │ Hello, welcome! 👋             │  │
│  └────────────────────────────────┘  │
│                                      │
│  ┌─ teal bubble ──────────────────┐  │
│  │ Dr. Greenviva is here to       │  │
│  │ serve you.                     │  │
│  └────────────────────────────────┘  │
│                                      │
│  ┌─ teal bubble ──────────────────┐  │
│  │ How would you like to proceed? │  │
│  └────────────────────────────────┘  │
│                                      │
│  ┌──────────┐  ┌──────────────┐     │
│  │ 📞 Call  │  │ 📝 Text     │     │
│  │  (white) │  │   (teal)    │     │
│  └──────────┘  └──────────────┘     │
└──────────────────────────────────────┘
```

### Message Bubbles
- Shape: rounded rectangle (16dp top-start, 16dp top-end, 16dp bottom-end, 4dp bottom-start)
- Color: BrandTeal `#2A9D8F`
- Text: white, 15sp, Medium weight
- Padding: 16dp horizontal, 10dp vertical
- Elevation: 2dp shadow

### Typing Indicator Bubble
- Shape: 12dp rounded
- Color: BrandTeal at 80% opacity
- Text: animated ".", "..", "..." cycling every 400ms (22sp Bold, white)
- Padding: 20dp horizontal, 6dp vertical

### Animation
- Each message slides in from bottom: `slideInVertically` + `fadeIn` (300ms)
- Typing indicator: `fadeIn` (200ms) / `fadeOut` (150ms)

### Scrim
- Full-screen overlay: `rgba(0, 0, 0, 0.4)`
- Positioned at bottom, messages stack upward

### Choice Buttons
| Button | Background | Text Color | Icon |
|--------|-----------|------------|------|
| Call | White | BrandTeal | Phone icon |
| Text | BrandTeal | White | Document icon |

- Height: 52dp, corner radius: 14dp, elevation: 4dp
- Equal width (50/50 split), 12dp gap
- Text: 15sp Bold

### Call Button Behavior
1. Patient taps **Call**
2. A dropdown appears (overlay stays visible):
   - **Voice Call** → starts audio call, dismisses overlay
   - **Video Call** → starts video call, dismisses overlay
   - Dismiss dropdown → dismisses overlay
3. The call follows the normal VideoSDK flow

### Text Button Behavior
1. Patient taps **Text**
2. Overlay dismisses
3. Auto-sends message: "Hi, I'd like to consult via text messages." (localized)
4. Normal chat input becomes active

### Localized Strings
| Key | EN | SW | FR | ES | AR | HI |
|-----|----|----|----|----|----|----|
| greeting_welcome | Hello, welcome! 👋 | Habari, karibu! 👋 | Bonjour, bienvenue ! 👋 | ¡Hola, bienvenido! 👋 | مرحبا، أهلا بك! 👋 | नमस्ते, स्वागत है! 👋 |
| greeting_here_to_serve | Dr. %s is here to serve you. | Dkt. %s yuko hapa kukuhudumia. | Dr %s est ici pour vous servir. | El Dr. %s está aquí para atenderle. | الدكتور %s هنا لخدمتك. | डॉ. %s आपकी सेवा के लिए यहाँ हैं। |
| greeting_here_to_serve_generic | We are here to serve you. | Tuko hapa kukuhudumia. | Nous sommes ici pour vous servir. | Estamos aquí para atenderle. | نحن هنا لخدمتك. | हम आपकी सेवा के लिए यहाँ हैं। |
| greeting_how_to_proceed | How would you like to proceed? | Ungependa kuendelea vipi? | Comment souhaitez-vous continuer ? | ¿Cómo le gustaría continuar? | كيف تود المتابعة؟ | आप कैसे आगे बढ़ना चाहेंगे? |
| greeting_choice_call | Call | Piga simu | Appeler | Llamar | اتصل | कॉल |
| greeting_choice_text | Text | Ujumbe | Message | Mensaje | رسالة | संदेश |
| greeting_text_auto_message | Hi, I'd like to consult via text messages. | Habari, ningependa kushauriana kupitia ujumbe wa maandishi. | Bonjour, je souhaite consulter par messages texte. | Hola, me gustaría consultar por mensajes de texto. | مرحبا، أود الاستشارة عبر الرسائل النصية. | नमस्ते, मैं टेक्स्ट संदेश के माध्यम से परामर्श लेना चाहता/चाहती हूँ। |
| contact_us_label | For help contact us | Kwa msaada wasiliana nasi | Pour de l'aide, contactez-nous | Para ayuda contáctenos | للمساعدة تواصل معنا | सहायता के लिए हमसे संपर्क करें |

---

## 4. Contact Us on Login Page

### What
A "For help contact us" section with phone and email on the role selection / login page, positioned above the copyright footer.

### Layout
```
┌──────────────────────────────────────┐
│  ... (role selection content) ...    │
│                                      │
│       For help contact us            │  ← 13sp SemiBold, onSurfaceVariant
│  📞 +255 663 582 994   ✉ support@esiri.africa │
│                                      │  ← 12sp Medium, BrandTeal
│  © 2026 eSIRI Plus                   │
└──────────────────────────────────────┘
```

### Behavior
- Phone number: tappable → opens dialer (`tel:+255663582994`)
- Email: tappable → opens mail client (`mailto:support@esiri.africa`)
- Icons: Phone and Email icons, 14dp, BrandTeal color
- 4dp spacing between icon and text, 16dp between phone and email groups

### Also Present On
- Patient home screen (same layout)
- Doctor dashboard (same layout)

---

## 5. Real Acceptance Rate

### What
The doctor dashboard's "Acceptance Rate" stat card now shows the real rate from `consultation_requests`, not a fake 100% from consultations.

### Formula
```
acceptance_rate = accepted / (accepted + rejected + expired) × 100
```
- `pending` requests are excluded (not yet decided)
- If no requests exist, show "—" (em dash)

### Data Source
```sql
SELECT status, COUNT(*)
FROM consultation_requests
WHERE doctor_id = :doctor_id
GROUP BY status
```

### Color Coding
| Rate | Value Color | Icon Color | Icon Background |
|------|------------|------------|-----------------|
| ≥ 75% | Default (onSurface) | BrandTeal `#2A9D8F` | Light green `#D1FAE5` |
| < 75% | Red `#DC2626` | Red `#DC2626` | Light red `#FEE2E2` |

### Stat Card Layout
```
┌────────────────────┐
│  [●]               │  ← 32dp rounded square bg, 8dp circle dot
│                    │
│  50%               │  ← 20sp Bold (red if < 75%)
│  Acceptance Rate   │  ← 11sp, onSurface
└────────────────────┘
```

---

## 6. Logout Cleanup & Device Guard

(See `LOGOUT_AND_DEVICE_GUARD_SPEC.md` for full details)

### Summary
On doctor logout:
1. Server: delete FCM token, mark offline, revoke session (`/functions/v1/logout`)
2. Client: destroy push token, clear all local state
3. Guard: every push notification includes `doctor_id` — client drops it if it doesn't match the current user

### PWA Implementation
- On logout: call `logout` edge function, unsubscribe Web Push, clear localStorage/IndexedDB
- On push received: validate `doctor_id` matches current session before showing notification

---

## 7. Session Persistence & Recovery

### What
Doctor sessions survive app restarts, process kills, and even database wipes. Biometric lock screen shows instead of login page.

### Plain SharedPreferences Backup
A non-encrypted backup stores essential session data:
```json
{
  "uid": "doctor-uuid",
  "role": "DOCTOR",
  "name": "Dr. Greenviva",
  "email": "dr@example.com",
  "verified": true,
  "rt": "base64-encoded-refresh-token"
}
```

### Recovery Flow
```
App starts → Room empty (DB wiped)
  ↓
Check backup → has refresh token
  ↓
Refresh via Supabase /auth/v1/token
  ↓
Reconstruct session → write to Room
  ↓
AuthState.Authenticated → Biometric lock screen
```

### PWA Implementation
- Store session backup in `localStorage` (separate from main state)
- On startup: check `localStorage` backup if IndexedDB session is missing
- Use stored refresh token to get fresh access token from Supabase
- Show biometric/PIN prompt if available (Web Authentication API), otherwise auto-restore

---

## 8. Patient Token Auto-Refresh

### What
Patient JWTs expire in 24 hours. Before every critical call, the app proactively refreshes the token if it's missing or expiring within 2 minutes.

### Protected Calls
| Call | When |
|------|------|
| Consultation request (`handle-consultation-request`) | Before sending |
| Follow-up request | Before sending |
| Chat init (fetch messages) | On screen open |
| Send message | Before each send |
| Send attachment | Before upload |
| Fetch remote messages (polling) | Before each poll |
| Accept/decline extension | Before calling |

### Refresh Mechanism
```
POST /functions/v1/refresh-patient-session (anonymous — no JWT validation)
Body: { "refresh_token": "...", "session_id": "..." }
Response: { "access_token": "new-jwt", "refresh_token": "new-rt", "expires_in": 86400 }
```

**Critical:** The refresh call uses `anonymous = true` (no `X-Patient-Token` header). The refresh token in the body IS the credential. The server validates it via bcrypt comparison against the stored hash.

### PWA Implementation
```javascript
async function ensureValidToken() {
  const token = getStoredAccessToken();
  const expiresAt = getStoredExpiresAt();
  const twoMinutes = 2 * 60 * 1000;

  if (!token || !expiresAt || Date.now() + twoMinutes >= expiresAt) {
    const refreshToken = getStoredRefreshToken();
    const sessionId = getStoredSessionId();
    if (!refreshToken || !sessionId) return;

    const res = await fetch('/functions/v1/refresh-patient-session', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${SUPABASE_ANON_KEY}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ refresh_token: refreshToken, session_id: sessionId }),
    });

    if (res.ok) {
      const data = await res.json();
      saveTokens(data.access_token, data.refresh_token, data.expires_in);
    }
  }
}

// Call before every critical fetch:
await ensureValidToken();
```

### Important Notes
- **Never send an expired patient JWT to the server** — `validateAuth()` will reject it
- **The refresh endpoint does NOT call `validateAuth()`** — it validates via the refresh token body
- **Refresh tokens are single-use** — after each refresh, the old token is invalidated and a new one is returned. Store the NEW refresh token immediately.
- **7-day absolute window** — refresh tokens cannot extend the session beyond 7 days from creation. After 7 days, the patient must create a new session.
