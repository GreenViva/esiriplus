# iOS — Home Redesign (Splash + Role Selection) — Implementation Spec

iOS implementation spec for the v1.3 home redesign. Mirrors the shipped
Android implementation in `feature/auth/src/main/kotlin/.../screen/SplashScreen.kt`
and `RoleSelectionScreen.kt`. Hand this to the iOS developer; the Android files
are the visual source of truth if anything here is ambiguous.

This **supersedes** `docs/ios-homepage-spec.md` for the splash + role-selection
screens. Other flows (consultation, agent, etc.) are unchanged.

---

## What's new in v1.3

1. **Editorial splash** — replaces the centered-logo splash with an editorial
   magazine-cover layout: corner brackets, glowing emblem with rotating dotted
   ring, canvas-drawn stethoscope, animated heartbeat (ECG) line, italic
   "eSIRI" wordmark with a gold "PLUS" pill, animated progress bar, and an
   "Eden World Co. — Dar es Salaam" footer.
2. **Patient-first home** — the symmetric three-role layout is replaced with a
   single hero card foregrounding "Continue as Patient", three trust pills,
   and a demoted "NOT A PATIENT?" section with side cards for Doctor and
   Agent.
3. **Wave halos on the CTA** — the white "Continue as Patient" button has two
   staggered button-shaped wave rings emanating outward.
4. **Patient gate bottom sheet** — tapping "Continue as Patient" opens a sheet
   with three options (new / have my ID / forgot my ID) instead of going
   straight into the new-patient flow.

---

## State machine — unchanged

```
   App launch
       │
       ▼
  ┌─────────────────┐
  │ Check session   │── session exists ─► Redirect by role:
  │ (AuthStore)     │                       • doctor  → /dashboard
  └────────┬────────┘                       • patient → /home
           │ no session
           ▼
  ┌─────────────────┐
  │ Splash Screen   │◄── every cold launch (not persisted)
  │ (3 s preparing) │
  └────────┬────────┘
           │ user taps after 3 s
           ▼
  ┌─────────────────┐
  │ Role Selection  │  (patient-first redesign)
  └────────┬────────┘
           │ Continue as Patient
           ▼
  ┌─────────────────┐
  │ Patient gate    │  (modal bottom sheet)
  │ • New           │── /terms → /patient-setup
  │ • Have my ID    │── /access-records
  │ • Forgot my ID  │── /patient-recovery
  └─────────────────┘
```

If the user is already authenticated, **skip splash entirely** and navigate
to the role-appropriate dashboard.

---

## Brand palette

These colors are specific to the splash. The role-selection home uses the
existing app `BrandTeal` (`#2A9D8F`).

```swift
enum SplashColors {
    static let teal       = Color(hex: 0x2DBE9E)
    static let tealDeep   = Color(hex: 0x1E8E76)
    static let cream      = Color(hex: 0xFBF9F3)
    static let mint       = Color(hex: 0xF0F7F2)
    static let ink        = Color(hex: 0x14201D)
    static let inkSoft    = Color(hex: 0x2A3A36)
    static let muted      = Color(hex: 0x8A9893)
    static let hairline   = Color(hex: 0xDCE7E1)
    static let gold       = Color(hex: 0xC99A4A)
    static let goldSoft   = Color(hex: 0xC99A4A).opacity(0.10)
    static let goldBorder = Color(hex: 0xC99A4A).opacity(0.25)
}

enum BrandColors {
    static let teal       = Color(hex: 0x2A9D8F)   // app-wide
    static let tealDeep   = Color(hex: 0x238B7E)   // hero card gradient
    static let cardBorder = Color(hex: 0xE5E7EB)
}
```

---

## Typography

Splash uses **Instrument Serif** (italic + regular) for the wordmark and
tagline, and **Geist** (regular / medium / semibold) for everything else.
Both fonts are open-source (Google Fonts and Vercel respectively).

Add to the app target:

- `InstrumentSerif-Regular.ttf`
- `InstrumentSerif-Italic.ttf`
- `Geist-Regular.ttf`
- `Geist-Medium.ttf`
- `Geist-SemiBold.ttf`

Register in `Info.plist` under `UIAppFonts`. Provide accessor helpers:

```swift
extension Font {
    static func instrumentSerif(size: CGFloat, italic: Bool = false) -> Font {
        Font.custom(italic ? "InstrumentSerif-Italic" : "InstrumentSerif-Regular", size: size)
    }
    static func geist(size: CGFloat, weight: Font.Weight = .regular) -> Font {
        let name: String = switch weight {
            case .medium:   "Geist-Medium"
            case .semibold: "Geist-SemiBold"
            default:        "Geist-Regular"
        }
        return Font.custom(name, size: size)
    }
}
```

If the fonts are not yet bundled, fall back to `Font.system(.serif)` and
`Font.system(.default)` so the splash still renders.

---

## 1 — Splash screen

### Layout

```
┌─────────────────────────────────────────┐
│ ┐                                     ┌ │  corner brackets, hairline grey
│                                         │  (16pt long, 1pt stroke)
│                                         │
│                                         │
│                  ●                      │  emblem  (196×196pt area)
│                  │                      │
│                  ●─●  ●●  ECG           │
│                                         │
│                                         │
│            e𝑆𝐼𝑅𝐼  PLUS                  │  wordmark (serif e + italic
│                                         │  SIRI in tealDeep, gold pill)
│                                         │
│         "Afya yako, kipaumbele         │
│              chetu."                    │
│                ─────                    │  hairline divider (32×1pt)
│         YOUR HEALTH · OUR PRIORITY     │  Geist medium 11pt, muted
│                                         │
│                                         │
│              ──────                     │  progress bar (80×2pt)
│         PREPARING YOUR SPACE            │  (becomes TAP TO CONTINUE)
│                                         │
│         ──────────────                  │  hairline divider, full-width
│              Eden World Co.             │  italic serif 13pt, inkSoft
│      DAR ES SALAAM · TANZANIA · EST.    │  Geist medium 10pt, muted
│ ┘                                     └ │
└─────────────────────────────────────────┘
```

Background: vertical gradient `cream → mint`.

### Behavior

- On appear: progress bar animates 0 → 1 over 3 s; bottom label reads
  **"PREPARING YOUR SPACE"**; whole-screen tap is **disabled**.
- After 3 s: bar reaches full, label flips to **"TAP TO CONTINUE"** in
  `tealDeep` semibold; the filled portion of the bar gently pulses
  (alpha 0.5 ↔ 1.0 over 0.9 s, ease-in-out, repeat reverse). Whole-screen
  tap is enabled.
- Tap → call `onContinue()` → routes to language picker (first run) or
  role selection.
- The emblem animations (rotating ring, glow pulse, heartbeat) keep running
  the entire time, regardless of `ready` state.

### Emblem animations

| Element | Animation | Duration | Repeat |
|---|---|---|---|
| Dotted ring (172pt circle stroke) | rotate 0° → 360° | 30 s | linear, repeat forever |
| Soft radial glow (196pt) | scale 0.95 ↔ 1.05, alpha 0.6 ↔ 1.0 | 3 s | ease-in-out, reverse |
| Heartbeat line | path-reveal 0 → 1 | 2.4 s | ease-in-out, reverse |

### Emblem composition

Outer-to-inner (one `ZStack`):

1. **Soft radial glow** — `RadialGradient` from `teal.opacity(0.15 * glowAlpha)`
   to `Color.clear`, radius `98pt * glowScale`.
2. **Dotted ring** — `Circle()` stroked with `tealDeep.opacity(0.25)`, line
   width 1pt, dash `[4pt, 6pt]`, rotated `ringRotation°`. Diameter 172pt.
3. **Seal** — 156pt circle. Fill `Color.white.opacity(0.4)`. Border 1pt
   `hairline`.
4. **Stethoscope** — 86pt, drawn in a `Canvas`. See path data below.
5. **Heartbeat line** — 90×14pt at the bottom of the seal (32pt up from the
   bottom edge). Drawn in a `Canvas`. See path data below.

### Stethoscope path (drawn in 100×100 viewport)

```swift
Canvas { ctx, size in
    let s = size.width / 100
    let color = SplashColors.tealDeep
    let stroke = StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round)

    // Earpieces — filled circles
    ctx.fill(Path(ellipseIn: CGRect(x: 30*s - 2.5*s, y: 16*s - 2.5*s, width: 5*s, height: 5*s)),
             with: .color(color))
    ctx.fill(Path(ellipseIn: CGRect(x: 70*s - 2.5*s, y: 16*s - 2.5*s, width: 5*s, height: 5*s)),
             with: .color(color))

    // Left tube — down then arc to center (50, 60)
    var leftTube = Path()
    leftTube.move(to: CGPoint(x: 30*s, y: 18*s))
    leftTube.addLine(to: CGPoint(x: 30*s, y: 42*s))
    leftTube.addQuadCurve(to: CGPoint(x: 50*s, y: 60*s),
                          control: CGPoint(x: 30*s, y: 60*s))
    ctx.stroke(leftTube, with: .color(color), style: stroke)

    // Right tube — symmetric
    var rightTube = Path()
    rightTube.move(to: CGPoint(x: 70*s, y: 18*s))
    rightTube.addLine(to: CGPoint(x: 70*s, y: 42*s))
    rightTube.addQuadCurve(to: CGPoint(x: 50*s, y: 60*s),
                           control: CGPoint(x: 70*s, y: 60*s))
    ctx.stroke(rightTube, with: .color(color), style: stroke)

    // Tube down to bell
    var stem = Path()
    stem.move(to: CGPoint(x: 50*s, y: 60*s))
    stem.addLine(to: CGPoint(x: 50*s, y: 76*s))
    ctx.stroke(stem, with: .color(color), style: stroke)

    // Bell — outer 6pt + inner 3pt
    ctx.stroke(Path(ellipseIn: CGRect(x: 50*s - 6*s, y: 82*s - 6*s, width: 12*s, height: 12*s)),
               with: .color(color), style: StrokeStyle(lineWidth: 1.6))
    ctx.stroke(Path(ellipseIn: CGRect(x: 50*s - 3*s, y: 82*s - 3*s, width: 6*s, height: 6*s)),
               with: .color(color.opacity(0.5)), style: StrokeStyle(lineWidth: 1))
}
.frame(width: 86, height: 86)
.padding(.bottom, 12)  // makes room for the pulse line
```

### Heartbeat (ECG) path — viewport 90×14

The path is built once; the **trim end** animates from 0 → 1 → 0 (via reversing
ease-in-out repeat) and is applied with `.trim(from:to:)`. SwiftUI doesn't
animate `Path` directly — use `TimelineView` + `.trim(to: progress)` or a
`Canvas` with manual subpath measurement.

Path coordinates (in 90×14 viewport, midY = 7):

```
move (2,  7)
line (28, 7)
line (34, 2)   ← sharp up
line (40, 12)  ← sharp down
line (46, 4)   ← up again
line (52, 7)
line (88, 7)
```

Stroke: `teal`, line width 1.4pt, round cap, round join.

```swift
struct HeartbeatLine: View {
    let progress: Double  // 0 ... 1, animated externally
    var body: some View {
        Canvas { ctx, size in
            let path = ecgPath(in: size)
            ctx.stroke(
                path.trimmedPath(from: 0, to: progress),
                with: .color(SplashColors.teal),
                style: StrokeStyle(lineWidth: 1.4, lineCap: .round, lineJoin: .round)
            )
        }
        .frame(width: 90, height: 14)
    }
    private func ecgPath(in size: CGSize) -> Path {
        let w = size.width, h = size.height, midY = h / 2
        var p = Path()
        p.move(to: .init(x: 2/90 * w, y: midY))
        p.addLine(to: .init(x: 28/90 * w, y: midY))
        p.addLine(to: .init(x: 34/90 * w, y: 2/14 * h))
        p.addLine(to: .init(x: 40/90 * w, y: 12/14 * h))
        p.addLine(to: .init(x: 46/90 * w, y: 4/14 * h))
        p.addLine(to: .init(x: 52/90 * w, y: midY))
        p.addLine(to: .init(x: 88/90 * w, y: midY))
        return p
    }
}
```

### Wordmark

```
e ITALIC-SIRI    [ PLUS ]    ← "PLUS" pill
```

- "e" — Instrument Serif Regular, 56pt, color `ink`, tracking ≈ −1.1pt.
- "SIRI" — Instrument Serif **Italic**, 56pt, color `tealDeep`. Same tracking.
- Pill — corner radius 20pt, fill `goldSoft`, 1pt border `goldBorder`,
  10pt horizontal × 4pt vertical padding. Text: "PLUS" Geist SemiBold 11pt,
  tracking ≈ 2.2pt, color `gold`. 10pt gap from wordmark.

### Tagline

```
"Afya yako, kipaumbele chetu."
       ────                         ← 32×1pt hairline divider
  YOUR HEALTH  ·  OUR PRIORITY
```

- Swahili — Instrument Serif Italic, 22pt, color `tealDeep`, line height 26pt,
  tracking ≈ −0.1pt, centered. Wrap in straight quotes.
- Divider — 32pt × 1pt, color `hairline`, 12pt above and below.
- English — Geist Medium 11pt, tracking ≈ 2.4pt, color `muted`. Use a center
  dot (`·`) glyph between words.

### Bottom block

```
        ───────                  ← progress bar (80×2pt)
    PREPARING YOUR SPACE          (or TAP TO CONTINUE)
                                  Geist Medium 11pt, tracking 1.8pt
                                  Color muted (or tealDeep when ready)

────────────────────────────      ← full-width hairline
        Eden World Co.            ← Instrument Serif Italic 13pt, inkSoft
DAR ES SALAAM · TANZANIA · EST.   ← Geist Medium 10pt, tracking 1.6pt, muted
```

Progress bar: 80×2pt, 1pt corner radius, track `hairline`, fill `tealDeep`.
Use `withAnimation(.easeInOut(duration: 3))` to drive the fill from 0 to 1
on appear.

### Corner brackets

Four 28×28pt L-shaped marks, 1pt stroke `hairline`, inset 24pt from the
screen edges. Each bracket is two perpendicular hairlines forming the
corner of the bracket — never the full square. Use a `Canvas` per corner
or four `Rectangle()` strokes.

---

## 2 — Role-selection home

### Layout (single screen, no scroll)

```
┌──────────────────────────────────────────┐
│ [🩺]  eSIRI Plus                         │  brand row (36pt tile)
│                                          │
│ Need a doctor?                           │  24pt bold, ink black
│ We're here.                              │  24pt bold, BrandColors.teal
│ Talk to a real doctor from your phone.   │  13pt body, ink black
│ Private, simple, and available whenever  │
│ you need it.                             │
│                                          │
│ ╔══════════════════════════════════════╗ │  hero card — teal gradient,
│ ║ [💬]                              ◯ ◯║ │  decorative circles top-right
│ ║                                      ║ │
│ ║ Get medical help                     ║ │  18pt bold white
│ ║ Chat with a licensed doctor in       ║ │  12pt white-85%
│ ║ minutes — no appointment needed.     ║ │
│ ║                                      ║ │
│ ║ ┌──────────────────────────────────┐ ║ │  WHITE button with WAVE HALOS
│ ║ │ ((( Continue as Patient → ))))   │ ║ │  46pt tall, 12pt corners
│ ║ └──────────────────────────────────┘ ║ │
│ ╚══════════════════════════════════════╝ │
│                                          │
│   [🛡]      [🕐]      [✓]                │  trust pills
│   100%     24/7     Real                 │  (40pt teal-tinted circle)
│   Private  Available Doctors             │
│                                          │
│ NOT A PATIENT?                           │  11pt SemiBold black uppercase
│ ┌──────────────┐ ┌──────────────┐        │
│ │ [🩺] I'm a   │ │ [$] Become   │        │  side cards 62pt height
│ │     Doctor   │ │    Agent     │        │
│ │ Manage prac. │ │ Earn money   │        │
│ └──────────────┘ └──────────────┘        │
│                                          │
│         (flexible spacer)                │
│                                          │
│          Need help? +255 663 582 994     │  contact line
│              info@esiri.africa           │  (both tappable)
└──────────────────────────────────────────┘
```

The screen does **not** scroll. Use a `VStack` with `.frame(maxHeight:.infinity)`
and a `Spacer()` before the contact line so it stays pinned to the bottom.

### Brand row

- Stethoscope tile: 36×36pt, corner radius 10pt, fill `BrandColors.teal.opacity(0.10)`,
  stethoscope icon centered, 20pt, fill teal.
- Wordmark: "eSIRI" 18pt bold, ink. 4pt gap. "Plus" 18pt bold, BrandColors.teal.

### Hero title

Two stacked lines, 24pt bold each:

- Line 1: "Need a doctor?" — `Color.black`
- Line 2: "We're here." — `BrandColors.teal`

Then 6pt gap, then 13pt body subtitle in black:
"Talk to a real doctor from your phone. Private, simple, and available whenever you need it."

### Hero card

```swift
ZStack(alignment: .topTrailing) {
    // Background gradient
    LinearGradient(
        colors: [BrandColors.teal, BrandColors.tealDeep],
        startPoint: .topLeading, endPoint: .bottomTrailing
    )

    // Decorative circles (top-right, behind content)
    Circle()
        .fill(.white.opacity(0.08))
        .frame(width: 140, height: 140)
        .offset(x: 40, y: -40)
    Circle()
        .fill(.white.opacity(0.06))
        .frame(width: 90, height: 90)
        .offset(x: 70, y: 20)

    // Content
    VStack(alignment: .leading, spacing: 0) {
        // Chat icon tile
        ZStack {
            RoundedRectangle(cornerRadius: 10)
                .fill(.white.opacity(0.18))
                .frame(width: 38, height: 38)
            Image(systemName: "message")
                .font(.system(size: 20, weight: .regular))
                .foregroundStyle(.white)
        }
        Spacer().frame(height: 20)
        Text("Get medical help")
            .font(.system(size: 18, weight: .bold))
            .foregroundStyle(.white)
        Spacer().frame(height: 4)
        Text("Chat with a licensed doctor in minutes — no appointment needed.")
            .font(.system(size: 12))
            .foregroundStyle(.white.opacity(0.85))
        Spacer().frame(height: 8)
        ContinueWithWaves(action: openPatientGate)
    }
    .padding(.horizontal, 18)
    .padding(.vertical, 16)
}
.clipShape(RoundedRectangle(cornerRadius: 24))
```

### Continue button — wave halos

The button is plain white (corner radius 12pt, height 46pt) but is **wrapped**
in a 70pt-tall container behind which two staggered button-shaped wave
rings emanate vertically.

Animation params:
- Wave duration: 2.2 s, linear
- Two waves, second offset by 1.1 s (half-cycle)
- Each ring: rounded-rect outline, white stroke 1.5pt
- Expansion: rect grows vertically from button height (46pt) up to (46+24)pt
- Alpha: linear fade 0.5 → 0 across the cycle
- Lateral edges stay flush with the button (no horizontal expansion).

```swift
struct ContinueWithWaves: View {
    let action: () -> Void
    @State private var animate = false

    var body: some View {
        ZStack {
            // Two staggered waves (use TimelineView for continuous animation)
            TimelineView(.animation) { context in
                let t = context.date.timeIntervalSinceReferenceDate
                let waveDur: Double = 2.2
                let p1 = (t.truncatingRemainder(dividingBy: waveDur)) / waveDur
                let p2 = ((t + waveDur / 2).truncatingRemainder(dividingBy: waveDur)) / waveDur

                Canvas { ctx, size in
                    drawWave(in: ctx, size: size, progress: p1)
                    drawWave(in: ctx, size: size, progress: p2)
                }
            }

            // Real button
            Button(action: action) {
                HStack(spacing: 6) {
                    Text("Continue as Patient")
                        .font(.system(size: 14, weight: .bold))
                    Image(systemName: "arrow.right")
                        .font(.system(size: 16, weight: .bold))
                }
                .frame(maxWidth: .infinity)
                .frame(height: 46)
                .background(.white)
                .foregroundStyle(BrandColors.teal)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
        }
        .frame(height: 70)  // 46 button + 12pt above + 12pt below for waves
    }

    private func drawWave(in ctx: GraphicsContext, size: CGSize, progress: Double) {
        let buttonH: CGFloat = 46
        let buttonW: CGFloat = size.width
        let buttonY: CGFloat = (size.height - buttonH) / 2
        let maxExpansion: CGFloat = 12
        let baseCorner: CGFloat = 12
        let strokeWidth: CGFloat = 1.5

        let expansion = CGFloat(progress) * maxExpansion
        let alpha = max(0, 1 - progress) * 0.5
        let corner = baseCorner + expansion
        let rect = CGRect(
            x: 0,
            y: buttonY - expansion,
            width: buttonW,
            height: buttonH + 2 * expansion
        )
        let path = Path(roundedRect: rect, cornerRadius: corner)
        ctx.stroke(path, with: .color(.white.opacity(alpha)), lineWidth: strokeWidth)
    }
}
```

### Trust pills

Three columns, evenly spaced. Each column:

```swift
VStack(spacing: 6) {
    ZStack {
        Circle()
            .fill(BrandColors.teal.opacity(0.10))
            .frame(width: 40, height: 40)
        Image(systemName: iconName)
            .font(.system(size: 18))
            .foregroundStyle(BrandColors.teal)
    }
    Text(value).font(.system(size: 12, weight: .bold)).foregroundStyle(.black)
    Text(label).font(.system(size: 11)).foregroundStyle(.black)
}
```

| Position | SF Symbol | Value | Label |
|---|---|---|---|
| Left | `shield` (or `shield.fill`) | `100%` | `Private` |
| Middle | `clock` | `24/7` | `Available` |
| Right | `checkmark` | `Real` | `Doctors` |

### NOT A PATIENT? section

11pt SemiBold black uppercase label, 8pt gap below, then a horizontal
`HStack(spacing: 10)` of two equal-width 62pt-tall cards:

```swift
struct NotPatientCard: View {
    let title: String
    let subtitle: String
    let iconName: String      // SF Symbol or asset name
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                ZStack {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(BrandColors.teal.opacity(0.10))
                        .frame(width: 32, height: 32)
                    Image(systemName: iconName)
                        .font(.system(size: 16))
                        .foregroundStyle(BrandColors.teal)
                }
                VStack(alignment: .leading, spacing: 0) {
                    Text(title).font(.system(size: 12, weight: .semibold)).foregroundStyle(.black)
                    Text(subtitle).font(.system(size: 10)).foregroundStyle(.black)
                }
                Spacer()
            }
            .padding(.horizontal, 10)
            .frame(maxWidth: .infinity)
            .frame(height: 62)
            .background(.white)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(BrandColors.cardBorder, lineWidth: 1)
            )
            .shadow(color: .black.opacity(0.04), radius: 1, y: 1)
        }
    }
}
```

| Card | Title | Subtitle | Icon | Action |
|---|---|---|---|---|
| Left | I'm a Doctor | Manage practice | `stethoscope` | route to doctor login |
| Right | Become Agent | Earn money | `dollarsign` | route to agent auth |

### Need help line

```swift
VStack(spacing: 2) {
    HStack(spacing: 6) {
        Text("Need help?").font(.system(size: 13)).foregroundStyle(.black)
        Text("+255 663 582 994")
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(BrandColors.teal)
            .onTapGesture { openURL(URL(string: "tel:+255663582994")!) }
    }
    Text("info@esiri.africa")
        .font(.system(size: 12, weight: .semibold))
        .foregroundStyle(BrandColors.teal)
        .onTapGesture { openURL(URL(string: "mailto:info@esiri.africa")!) }
}
```

> **Cross-app note:** the rest of the app (terms, doctor screens) currently
> uses `support@esiri.africa`. The home line is being moved to
> `info@esiri.africa` per product. Don't fix the inconsistency in this
> screen alone — it's tracked separately.

---

## 3 — Patient gate bottom sheet

Tapping "Continue as Patient" opens a modal sheet (NOT navigation).

### Layout

```
        ───  drag handle (system)

  Continue as Patient                    ← 20pt bold, black
  What brings you here today?            ← 13pt body, black

  ┌────────────────────────────────────┐
  │ [👤+] I'm new to eSIRI Plus      → │   40pt teal-tint circle icon
  │       Start a new consultation     │   15pt SemiBold + 12pt body
  └────────────────────────────────────┘
                                            10pt gap
  ┌────────────────────────────────────┐
  │ [🔑]  I have my Patient ID       → │
  │       Sign in to your records      │
  └────────────────────────────────────┘
                                            10pt gap
  ┌────────────────────────────────────┐
  │ [🔒]  I forgot my Patient ID     → │
  │       Recover with security questions │
  └────────────────────────────────────┘

  (24pt bottom safe-area padding)
```

Sheet container: white, 20pt horizontal padding. Use the iOS 16+
`.presentationDetents([.medium])` modifier or a fully custom sheet that
matches the height of its content.

### Option row component

Each row is a `Button` styled as a card:

```swift
struct PatientGateOption: View {
    let title: String
    let subtitle: String
    let iconName: String
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .fill(BrandColors.teal.opacity(0.10))
                        .frame(width: 40, height: 40)
                    Image(systemName: iconName)
                        .font(.system(size: 20))
                        .foregroundStyle(BrandColors.teal)
                }
                VStack(alignment: .leading, spacing: 0) {
                    Text(title).font(.system(size: 15, weight: .semibold)).foregroundStyle(.black)
                    Text(subtitle).font(.system(size: 12)).foregroundStyle(.black)
                }
                Spacer()
                Image(systemName: "arrow.right")
                    .font(.system(size: 18))
                    .foregroundStyle(BrandColors.teal)
            }
            .padding(14)
            .background(.white)
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(BrandColors.cardBorder, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 14))
        }
    }
}
```

### Routing

Each option:

1. **Dismisses the sheet** (await dismissal so the navigation transition
   isn't behind the sheet animation).
2. **Then** triggers the navigation:

| Option | Navigation target |
|---|---|
| I'm new to eSIRI Plus | `/terms` → `/patient-setup` |
| I have my Patient ID | `/access-records` (existing screen) |
| I forgot my Patient ID | `/patient-recovery` (existing screen) |

### iOS implementation hint

```swift
struct PatientGateSheet: View {
    @Environment(\.dismiss) private var dismiss
    let onNew: () -> Void
    let onHaveId: () -> Void
    let onForgot: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Continue as Patient")
                .font(.system(size: 20, weight: .bold))
                .foregroundStyle(.black)
            Spacer().frame(height: 4)
            Text("What brings you here today?")
                .font(.system(size: 13))
                .foregroundStyle(.black)

            Spacer().frame(height: 20)
            PatientGateOption(title: "I'm new to eSIRI Plus",
                              subtitle: "Start a new consultation",
                              iconName: "person.fill.badge.plus",
                              action: { dismiss(); onNew() })
            Spacer().frame(height: 10)
            PatientGateOption(title: "I have my Patient ID",
                              subtitle: "Sign in to your records",
                              iconName: "key.fill",
                              action: { dismiss(); onHaveId() })
            Spacer().frame(height: 10)
            PatientGateOption(title: "I forgot my Patient ID",
                              subtitle: "Recover with security questions",
                              iconName: "lock.fill",
                              action: { dismiss(); onForgot() })
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 24)
        .padding(.top, 16)
    }
}
```

---

## Strings

iOS should localize these. English values:

```
brand.primary                     = "eSIRI"
brand.accent                      = "Plus"

splash.preparing                  = "PREPARING YOUR SPACE"
splash.tap_to_continue            = "TAP TO CONTINUE"
splash.swahili_tagline            = "\"Afya yako, kipaumbele chetu.\""
splash.english_tagline            = "YOUR HEALTH  ·  OUR PRIORITY"
splash.footer_company             = "Eden World Co."
splash.footer_locale              = "DAR ES SALAAM  ·  TANZANIA  ·  EST. 2024"

home.hero_title_1                 = "Need a doctor?"
home.hero_title_2                 = "We're here."
home.hero_subtitle                = "Talk to a real doctor from your phone. Private, simple, and available whenever you need it."
home.card_title                   = "Get medical help"
home.card_subtitle                = "Chat with a licensed doctor in minutes — no appointment needed."
home.continue_as_patient          = "Continue as Patient"
home.trust_private_value          = "100%"
home.trust_private_label          = "Private"
home.trust_available_value        = "24/7"
home.trust_available_label        = "Available"
home.trust_real_value             = "Real"
home.trust_real_label             = "Doctors"
home.not_a_patient                = "NOT A PATIENT?"
home.im_a_doctor_title            = "I'm a Doctor"
home.im_a_doctor_subtitle         = "Manage practice"
home.become_agent_title           = "Become Agent"
home.become_agent_subtitle        = "Earn money"
home.need_help                    = "Need help?"
home.help_phone                   = "+255 663 582 994"
home.help_email                   = "info@esiri.africa"

gate.title                        = "Continue as Patient"
gate.subtitle                     = "What brings you here today?"
gate.new_title                    = "I'm new to eSIRI Plus"
gate.new_subtitle                 = "Start a new consultation"
gate.have_id_title                = "I have my Patient ID"
gate.have_id_subtitle             = "Sign in to your records"
gate.forgot_id_title              = "I forgot my Patient ID"
gate.forgot_id_subtitle           = "Recover with security questions"
```

For the six in-app languages (en, sw, ar, es, fr, hi), translations follow
the existing convention. The Swahili tagline on splash stays in Swahili
even when the app language is set to a non-Swahili locale.

---

## Acceptance checklist

- [ ] Splash auto-fills its progress bar over 3 s, then shows TAP TO CONTINUE.
- [ ] Whole screen is tappable only after 3 s; tapping during the preparing
      phase does nothing.
- [ ] Emblem dotted ring rotates one full revolution every 30 s.
- [ ] Heartbeat ECG line traces and erases continuously while the screen is
      visible.
- [ ] Wordmark renders with serif italic "SIRI" in `tealDeep` and a gold
      "PLUS" pill.
- [ ] Splash and home both fit on a single iPhone screen (test on iPhone SE
      3rd gen and iPhone 15 Pro Max — no scroll on either).
- [ ] Continue as Patient button shows two staggered wave halos that
      expand vertically and fade.
- [ ] Tapping Continue as Patient opens the bottom sheet, **not** a
      navigation push.
- [ ] All three sheet options dismiss the sheet before navigating.
- [ ] Phone number is tappable and opens the dialer.
- [ ] Email is tappable and opens the mail composer.
- [ ] Authenticated users skip both splash and role selection on cold launch.
- [ ] No system back button on the home screen (iOS doesn't have one but
      ensure no in-app back chevron).

---

## What is NOT in this spec

- The post-login dashboards (patient, doctor, agent) — unchanged.
- The terms / patient-setup / access-records / patient-recovery downstream
  screens — already specified.
- Push registration, biometric gating, device binding — all unchanged.

If anything in this spec disagrees with the Android source files
(`SplashScreen.kt` and `RoleSelectionScreen.kt` under
`feature/auth/src/main/kotlin/com/esiri/esiriplus/feature/auth/screen/`),
the Android source wins. Open an issue rather than diverging silently.
