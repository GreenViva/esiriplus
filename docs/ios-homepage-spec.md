# Homepage (Splash + Role Selection) — iOS Implementation Spec

## Overview

The homepage is the **app's public entry point** — the first screen an unauthenticated user sees. It has two sequential states:

1. **Splash Screen** — branded logo + taglines, tappable after 2 s delay.
2. **Role Selection Screen** — routes the user into one of three flows: **Patient**, **Doctor Portal**, or **Agents**.

Authenticated users **never see the homepage**; they are auto-redirected to their dashboard (see *Auto-Redirect Rules* below).

Source of truth: `pwa/src/app/page.tsx`.

---

## State Machine

```
   App launch
       │
       ▼
  ┌─────────────────┐
  │ Check session   │───── session exists ─────► Redirect by role:
  │ (AuthStore)     │                             • doctor  → /dashboard
  └────────┬────────┘                             • patient → /home
           │ no session
           ▼
  ┌─────────────────┐
  │ Splash Screen   │◄── every cold launch (not persisted)
  │ (3 s fade-in)   │
  └────────┬────────┘
           │ user taps after 2 s
           ▼
  ┌─────────────────┐
  │ Role Selection  │
  └─────────────────┘
```

**Notes:**
- Splash appears on every fresh mount of the homepage, not only first install.
- If the auth session is already present when the page loads, **skip splash entirely** and navigate directly to the role-appropriate route.

---

## 1 — Splash Screen

### Layout

```
┌───────────────────────────────────────┐
│                                       │
│           (vertical center)           │
│                                       │
│          ┌──────────────┐             │
│          │              │             │
│          │  Stethoscope │ 112×112pt   │ teal-gradient circle
│          │   (white)    │             │
│          └──────────────┘             │
│                                       │
│            eSIRI Plus                 │ 30pt bold, black
│                                       │
│      Afya yako, kipaumbele chetu      │ 18pt italic, BrandTeal
│       Your health, our priority       │ 14pt, subtitle-grey
│                                       │
│                                       │
│           Tap to continue             │ 14pt, subtitle-grey (appears after 2 s)
│                 •                     │ 8pt pulsing dot, BrandTeal
│                                       │
└───────────────────────────────────────┘
```

### Visual Details

| Element | Value |
|---|---|
| Background | Vertical gradient `#F0FDFA` (top) → `#FFFFFF` (bottom) |
| Logo circle | 112×112 pt, corner-radius = 56, gradient `#2A9D8F` → `#1A7A6E` (top-left → bottom-right), soft drop shadow (radius 8, opacity 15 %) |
| Logo icon | SF Symbol `stethoscope` or custom asset, 56 pt, white, stroke-width ≈ 1.5 |
| App name | `eSIRI Plus` — 30 pt, bold, `#000000` |
| Swahili tagline | `Afya yako, kipaumbele chetu` — 18 pt, italic, BrandTeal `#2A9D8F` |
| English subtitle | `Your health, our priority` — 14 pt, regular, `#6B7280` |
| "Tap to continue" | 14 pt, regular, `#6B7280`, 48 pt above (spaced below subtitle) |
| Pulse dot | 8×8 pt circle, BrandTeal, infinite pulse (opacity 0.5 ↔ 1.0, ~1 s period) |

### Timing & Interaction

- **Fade-in**: all content fades from opacity 0 → 1 over **3000 ms** on appear.
- **Tap enable**: the screen is **not tappable for the first 2000 ms** after mount.
- After 2 s, "Tap to continue" label + pulsing dot animate in (fade 500 ms).
- Any tap on the screen (while tappable) transitions to Role Selection — no separate button needed; the whole view is a tap target.
- Transition to Role Selection: simple fade/cross-fade (no slide required).

---

## 2 — Role Selection Screen

Constrained to a **max-width of 448 pt (≈ iPhone Pro Max width)**, centered. On compact iPhones it fills the width with 24 pt horizontal padding.

### Layout Tree (top → bottom)

```
[Header row]
    icon tile (48 pt) · title + subtitle
[ "FOR PATIENTS" small label ]
[Card: New to eSIRI Plus?]
[Card: I have my Patient ID]
[Row: "Save your Patient ID..." left · "Forgot ID?" link right]
[Divider "or"]
[ "DOCTOR PORTAL" small label ]
[Row: SignIn (outline) | SignUp (filled) ]
[Centered link: "Forgot password?"]
[Divider "or"]
[Card: eSIRIPlus Agents (orange)]
[Flexible spacer — pushes footer to bottom]
[ContactUs component — see ios-contact-us-spec.md]
[Copyright: © 2026 eSIRI Plus. All rights reserved.]
```

### Global Styling

| Token | Value (light) | Dark theme |
|---|---|---|
| Screen background | Gradient `#F0FDFA` → `#FFFFFF` (vertical) | `#121212` solid |
| Card surface | `#FFFFFF` | `#1E1E1E` |
| Card border | 1 pt `#E5E7EB` | `#3A3A3A` |
| Card radius | 16 pt | same |
| Card shadow | y-offset 1, blur 2, opacity 5 % (rests); y-offset 2, blur 4, opacity 10 % (hover/press) | same |
| Primary text | `#000000` (always black — per project UI convention) | `#E0E0E0` |
| Subtitle text | `#6B7280` | `#BDBDBD` |
| BrandTeal | `#2A9D8F` | `#80CBC4` |
| RoyalGold | `#F59E0B` | same |
| AgentOrange | `#EF6C00` | same |

> **UI convention (project-wide):** body and label text is **always black** in light mode — never grey. Use `#6B7280` only for small subtitles/captions.

### Header Row (top, 32 pt top padding)

- Circle 48×48 pt, fill `#2A9D8F` at 10 % opacity, contains 24 pt stethoscope icon tinted BrandTeal.
- Right of circle (12 pt gap):
  - Line 1: `eSIRI Plus` — 22 pt, bold, black
  - Line 2: `Your health, our priority` — 14 pt, `#6B7280`
- 32 pt bottom margin below header.

### Section Label: "FOR PATIENTS"

- Text: `FOR PATIENTS`, 11 pt, **bold, uppercase, tracking +0.5**, color `#6B7280`.
- 12 pt bottom margin.

### Card — "New to eSIRI Plus?"

- Full-width card, 14 pt vertical / 14 pt horizontal padding.
- Left: 40×40 pt circle, fill BrandTeal 10 % opacity, icon `person.badge.plus` (20 pt) tinted BrandTeal.
- 12 pt gap.
- Middle (flex):
  - `New to eSIRI Plus?` — 15 pt, semibold, black
  - `Start a consultation in minutes` — 12 pt, `#6B7280`
- Right: chevron-right arrow, 18 pt, `#9CA3AF`.
- **Tap** → navigate to `PatientSetup` (mode: `new`).
- Active state: scale 0.99, 80 ms ease-out.
- 12 pt bottom margin.

### Card — "I have my Patient ID"

Identical structure to the above, with:
- Icon: `key` (SF Symbol `key.fill` or equivalent).
- Title: `I have my Patient ID`.
- Subtitle: `Access your medical records`.
- **Tap** → navigate to `PatientSetup` (mode: `returning`).
- 8 pt bottom margin.

### Patient Helper Row

- Left text: `Save your Patient ID for future visits` — 12 pt, `#6B7280`.
- Right link: `Forgot ID?` — 12 pt, semibold, BrandTeal, tappable.
- **Tap "Forgot ID?"** → navigate to `PatientSetup` (mode: `recover`).
- 16 pt bottom margin.

### Divider "or"

- Horizontal rule line (1 pt, `#E5E7EB`) — `"or"` label (14 pt, `#6B7280`) — horizontal rule line.
- 16 pt vertical margin top & bottom.

### Section Label: "DOCTOR PORTAL"

Same styling as "FOR PATIENTS". 12 pt bottom margin.

### Doctor Button Row

- Two equal-width buttons side by side, 12 pt gap:
  - **Sign In** — outline variant: `#FFFFFF` bg, 1 pt `#E5E7EB` border, black text, 44 pt tall, 12 pt radius, semibold 14 pt.
  - **Sign Up** — filled variant: BrandTeal bg, white text, same size/radius/weight. Pressed state: darker teal `#1D6E64`.
- 8 pt bottom margin.

### "Forgot password?" Link

- Centered, 12 pt, semibold, BrandTeal. 8 pt vertical padding as tap target.
- **Tap** → navigate to `Login` with `forgotPassword = true` pre-selected.
- 16 pt bottom margin.

### Second Divider "or"

Same as first divider.

### Card — "eSIRIPlus Agents" (orange variant)

- Full-width card, 14 pt padding, background `#FFF7ED`, border 1 pt `#F59E0B` at 30 % opacity, radius 16 pt.
- Left: 40×40 pt circle with **gradient** fill RoyalGold `#F59E0B` → AgentOrange `#EF6C00`. Inside it: text `e+` in white, extrabold, 14 pt.
- Middle (flex):
  - `eSIRIPlus Agents` — 15 pt, semibold, black
  - `Earn money by becoming an agent` — 12 pt, `#6B7280`
- Right: chevron-right arrow, 18 pt, RoyalGold `#F59E0B`.
- **Tap** → navigate to `Agent` entry screen.

### Flexible Spacer

Pushes ContactUs + copyright to the bottom of the screen when content doesn't fill the viewport.

### Footer

- **ContactUs** — reuse the shared component defined in `docs/ios-contact-us-spec.md`.
- **Copyright**: `© 2026 eSIRI Plus. All rights reserved.` — 11 pt, `#6B7280`, centered, 8 pt bottom padding (above safe-area inset).

---

## Auto-Redirect Rules

On **appear** of the homepage view, check the shared AuthStore:

| Session state | Action |
|---|---|
| No session | Show Splash → Role Selection normally |
| Session exists, `user.role == "doctor"` | Replace current route with `Dashboard` (doctor) |
| Session exists, any other role | Replace current route with `Home` (patient) |

Use `.replace` semantics — do **not** push onto the nav stack, so the user can't swipe back to the homepage after auto-login.

---

## Navigation Destinations (targets iOS must have wired)

| From | Action | iOS destination |
|---|---|---|
| Splash | Tap anywhere (after 2 s) | Role Selection (same screen, state change) |
| Card "New to eSIRI Plus?" | Tap | `PatientSetup(mode: .new)` |
| Card "I have my Patient ID" | Tap | `PatientSetup(mode: .returning)` |
| Link "Forgot ID?" | Tap | `PatientSetup(mode: .recover)` |
| Button "Sign In" | Tap | `Login` |
| Button "Sign Up" | Tap | `Register` |
| Link "Forgot password?" | Tap | `Login(forgotPassword: true)` |
| Card "eSIRIPlus Agents" | Tap | `Agent` |
| Auto-redirect (doctor) | On session detected | `DoctorDashboard` |
| Auto-redirect (patient) | On session detected | `PatientHome` |

---

## Assets Required

1. **Logo icon** — stethoscope (white, stroke-based). SF Symbol `stethoscope` works but confirm visual parity with the PWA's Lucide version. Provide custom SVG/PNG if tighter match is needed.
2. **Person-plus icon** — SF Symbol `person.badge.plus`.
3. **Key icon** — SF Symbol `key.fill` or `key`.
4. **Chevron-right** — SF Symbol `chevron.right`, 18 pt, weight regular.
5. No raster assets required; gradients are generated in code.

---

## Accessibility

- Splash: make the whole screen a single button with accessibility label `"eSIRI Plus. Afya yako, kipaumbele chetu. Your health, our priority. Tap to continue."` and traits `.button`. Announce automatically.
- All cards/buttons expose proper accessibility labels matching their visible title + subtitle (e.g. `"New to eSIRI Plus? Start a consultation in minutes"`).
- Minimum tap targets 44×44 pt — already satisfied by the card heights (≈68 pt).
- Respect **Dynamic Type**: use scaled fonts for titles, subtitles, body. Section labels and the pulsing-dot element should be visually fixed.
- Reduce Motion: if enabled, skip the 3 s fade-in and the pulsing dot; show content immediately at full opacity and show the "Tap to continue" hint without pulsing.
- High contrast / Dark mode: use the dark-theme tokens in the styling table above.

---

## Localization

| Key | English | Swahili |
|---|---|---|
| `app_name` | eSIRI Plus | eSIRI Plus |
| `splash_tagline_primary` | — | Afya yako, kipaumbele chetu |
| `splash_tagline_secondary` | Your health, our priority | Afya yako, kipaumbele chetu *(fallback identical)* |
| `tap_to_continue` | Tap to continue | Bonyeza kuendelea |
| `role_section_patients` | FOR PATIENTS | KWA WAGONJWA |
| `role_new_title` | New to eSIRI Plus? | Mpya kwenye eSIRI Plus? |
| `role_new_subtitle` | Start a consultation in minutes | Anza mashauriano ndani ya dakika |
| `role_returning_title` | I have my Patient ID | Nina Kitambulisho changu cha Mgonjwa |
| `role_returning_subtitle` | Access your medical records | Pata kumbukumbu zako za matibabu |
| `patient_id_hint` | Save your Patient ID for future visits | Hifadhi Kitambulisho chako kwa ziara zijazo |
| `forgot_id` | Forgot ID? | Umesahau Kitambulisho? |
| `or` | or | au |
| `role_section_doctor` | DOCTOR PORTAL | LANGO LA DAKTARI |
| `sign_in` | Sign In | Ingia |
| `sign_up` | Sign Up | Jisajili |
| `forgot_password` | Forgot password? | Umesahau nenosiri? |
| `agent_title` | eSIRIPlus Agents | Mawakala wa eSIRIPlus |
| `agent_subtitle` | Earn money by becoming an agent | Pata pesa kwa kuwa wakala |
| `copyright` | © 2026 eSIRI Plus. All rights reserved. | © 2026 eSIRI Plus. Haki zote zimehifadhiwa. |

Use the project's existing localization system (same keys as PWA `useTranslation`).

---

## SwiftUI Skeleton (reference only)

```swift
struct HomePage: View {
    @EnvironmentObject var auth: AuthStore
    @State private var showSplash = true

    var body: some View {
        Group {
            if let session = auth.session {
                EmptyView().onAppear { redirect(for: session.user.role) }
            } else if showSplash {
                SplashScreen { withAnimation { showSplash = false } }
            } else {
                RoleSelectionView()
            }
        }
    }

    private func redirect(for role: UserRole) {
        switch role {
        case .doctor:  Router.shared.replace(with: .dashboard)
        default:       Router.shared.replace(with: .home)
        }
    }
}

struct SplashScreen: View {
    let onContinue: () -> Void
    @State private var visible = false
    @State private var tappable = false
    private let teal = Color(hex: "#2A9D8F")
    private let dark = Color(hex: "#1A7A6E")

    var body: some View {
        ZStack {
            LinearGradient(colors: [Color(hex: "#F0FDFA"), .white],
                           startPoint: .top, endPoint: .bottom)
                .ignoresSafeArea()

            VStack(spacing: 12) {
                Circle()
                    .fill(LinearGradient(colors: [teal, dark],
                                         startPoint: .topLeading,
                                         endPoint: .bottomTrailing))
                    .frame(width: 112, height: 112)
                    .shadow(color: .black.opacity(0.15), radius: 8, y: 4)
                    .overlay(Image(systemName: "stethoscope")
                        .font(.system(size: 56, weight: .light))
                        .foregroundColor(.white))

                Text("eSIRI Plus")
                    .font(.system(size: 30, weight: .bold))
                    .foregroundColor(.black)
                    .padding(.top, 12)

                Text("Afya yako, kipaumbele chetu")
                    .font(.system(size: 18, design: .default)).italic()
                    .foregroundColor(teal)

                Text("Your health, our priority")
                    .font(.system(size: 14))
                    .foregroundColor(Color(hex: "#6B7280"))

                if tappable {
                    VStack(spacing: 12) {
                        Text("Tap to continue")
                            .font(.system(size: 14))
                            .foregroundColor(Color(hex: "#6B7280"))
                        Circle().fill(teal).frame(width: 8, height: 8)
                            .modifier(Pulsing())
                    }
                    .padding(.top, 36)
                    .transition(.opacity)
                }
            }
            .opacity(visible ? 1 : 0)
            .animation(.easeInOut(duration: 3), value: visible)
        }
        .contentShape(Rectangle())
        .onTapGesture { if tappable { onContinue() } }
        .onAppear {
            visible = true
            DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                withAnimation(.easeInOut(duration: 0.5)) { tappable = true }
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("eSIRI Plus. Afya yako, kipaumbele chetu. Your health, our priority. Tap to continue.")
        .accessibilityAddTraits(.isButton)
    }
}
```

(RoleSelectionView left to the implementer — the layout table above specifies every element.)

---

## Acceptance Criteria

- [ ] Cold launch with no session shows splash that fades in over ~3 s.
- [ ] Splash ignores taps during the first 2 s, then reveals "Tap to continue" + pulsing dot.
- [ ] Any tap (after 2 s) transitions to Role Selection via cross-fade.
- [ ] Cold launch **with** an active session skips splash entirely and lands on `PatientHome` or `DoctorDashboard` per role.
- [ ] All eight tap targets in Role Selection route to the correct destinations with the correct mode parameters.
- [ ] Dark mode produces the dark tokens listed above without adjustment.
- [ ] Dynamic Type scales titles and subtitles; section labels + pulse dot stay fixed.
- [ ] Reduce Motion disables fade and pulse animations.
- [ ] Both English and Swahili localizations render without truncation at default Dynamic Type size.
- [ ] ContactUs footer matches the spec in `ios-contact-us-spec.md`.

---

## References

- PWA source: `pwa/src/app/page.tsx`
- Shared footer component: `docs/ios-contact-us-spec.md`
- Brand tokens: `pwa/src/app/globals.css` (`:root` block)
- Button variants: `pwa/src/components/ui/Button.tsx`
