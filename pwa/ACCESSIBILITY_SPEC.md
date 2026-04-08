# eSIRI Plus — Accessibility Button Specification (for PWA Development)

This document describes the floating accessibility button and its panel — from visual layout and interaction behavior to each setting's effect on the UI, and how preferences are persisted. The PWA must replicate this feature exactly.

---

## Table of Contents

1. [Floating Action Button (FAB)](#1-floating-action-button-fab)
2. [Accessibility Panel](#2-accessibility-panel)
3. [Theme Selection](#3-theme-selection)
4. [Text Size / Font Scaling](#4-text-size--font-scaling)
5. [High Contrast Mode](#5-high-contrast-mode)
6. [Reduce Motion](#6-reduce-motion)
7. [Language Selection](#7-language-selection)
8. [Sound / Ringtone Customization](#8-sound--ringtone-customization)
9. [Preferences Storage](#9-preferences-storage)
10. [Color System & Tokens](#10-color-system--tokens)
11. [PWA Implementation Notes](#11-pwa-implementation-notes)

---

## 1. Floating Action Button (FAB)

### Appearance
- **Shape:** Circle, 52×52 dp
- **Color:** BrandTeal `#2A9D8F` background, white icon
- **Icon:** Settings gear (⚙) when collapsed, Close (✕) when expanded
- **Rotation animation:** Icon rotates 45 degrees when toggling (250ms tween)
- **Elevation:** 6dp default, 10dp pressed
- **Default position:** Bottom-right corner, 16dp padding from edges

### Drag Behavior
- Fully draggable — user can drag it anywhere on screen
- Constrained to viewport bounds (never goes off-screen)
- Drag uses pointer gesture detection; position persists during the session (resets on page reload is acceptable for PWA)

### Directional Arrow Indicators
Four small translucent arrows surround the FAB to hint at draggability:
```
        ▲         (8sp, BrandTeal at 50% opacity)
   ◀    ⚙    ▶    (30dp offset from center in each direction)
        ▼
```
- Unicode characters: ▲ `\u25B2`, ▼ `\u25BC`, ◀ `\u25C0`, ▶ `\u25B6`
- Font size: 8sp, color: `#2A9D8F` at 50% opacity

### Expand/Collapse
- **Tap** toggles the panel open/closed
- **Panel animation:** Scale in from 80% + fade in (250ms/200ms), scale out to 80% + fade out (200ms/150ms)
- **Scrim:** Semi-transparent black overlay (`rgba(0,0,0,0.3)`) covers the rest of the screen when panel is open; tapping scrim closes the panel

### Layout (Visual)
```
┌──────────────────────────────────────────────┐
│                                              │
│                                              │
│                 [dark scrim overlay]          │
│                                              │
│                                              │
│                     ┌──────────────────┐     │
│                     │  Accessibility   │     │
│                     │  Panel (280dp)   │     │
│                     │  (see §2 below)  │     │
│                     └──────────────────┘     │
│                                        [⚙]  │ ← FAB (52dp circle)
│                                    ▲         │
│                               ◀   ✕   ▶     │ ← arrows + close icon
│                                    ▼         │
└──────────────────────────────────────────────┘
```
Panel anchors near the FAB — offset: `end: 16dp, bottom: 80dp` from FAB position.

---

## 2. Accessibility Panel

### Container
- **Width:** 280dp fixed
- **Shape:** Rounded rectangle, 20dp corner radius
- **Background:** `surface` color (white in light mode, `#1E1E1E` in dark mode)
- **Elevation:** 8dp shadow
- **Content padding:** 20dp all around
- **Scrollable:** Vertical scroll enabled (panel can exceed viewport on small screens)

### Panel Layout (Top to Bottom)
```
┌─────────────────────────────────────┐
│  Display & Accessibility            │  ← 16sp Bold, onSurface color
│  Customize your experience          │  ← 13sp, onSurfaceVariant color
│                                     │
│  THEME                              │  ← Section label (11sp Bold, 1sp letter spacing)
│  ┌─────────┐┌─────────┐┌─────────┐ │
│  │  Auto   ││  Light  ││  Dark   │ │  ← 3 equal-width buttons, 36dp tall
│  └─────────┘└─────────┘└─────────┘ │
│  ─────────────────────────────────  │  ← Divider
│                                     │
│  TEXT SIZE                          │
│  ┌─────────┐┌─────────┐┌─────────┐ │
│  │   A     ││    A    ││    A    │ │  ← 13sp / 16sp / 20sp "A" letters
│  │ (small) ││(normal) ││ (large) │ │     40dp tall, bottom-aligned
│  └─────────┘└─────────┘└─────────┘ │
│  ─────────────────────────────────  │
│                                     │
│  High contrast              [═══]  │  ← Toggle switch
│  Bolder text & borders              │     14sp label + 12sp subtitle
│                                     │
│  Reduce motion              [═══]  │  ← Toggle switch
│  Minimize animations                │     14sp label + 12sp subtitle
│  ─────────────────────────────────  │
│                                     │
│  LANGUAGE                           │
│  ┌─────────┐┌─────────┐┌─────────┐ │
│  │ English ││Kiswahili││Francais │ │  ← 3×2 grid, 34dp tall each
│  └─────────┘└─────────┘└─────────┘ │     6dp gap between rows
│  ┌─────────┐┌─────────┐┌─────────┐ │     8dp gap between columns
│  │ Espanol ││ العربية ││ हिन्दी  │ │
│  └─────────┘└─────────┘└─────────┘ │
│  ─────────────────────────────────  │
│                                     │
│  SOUNDS                             │
│  ┌─────────────────────────────┐   │
│  │ Incoming Call               │   │  ← Tap opens ringtone picker
│  │ System Default              │   │     13sp label + 11sp subtitle
│  └─────────────────────────────┘   │
│  ┌─────────────────────────────┐   │
│  │ Consultation Request        │   │
│  │ My Custom Ring    [Reset]   │   │  ← "Reset" button in red (#DC2626)
│  └─────────────────────────────┘   │
└─────────────────────────────────────┘
```

---

## 3. Theme Selection

### Options
| Option | Value | Behavior |
|--------|-------|----------|
| Auto | `SYSTEM` (ordinal 0) | Follow OS dark/light preference |
| Light | `LIGHT` (ordinal 1) | Force light theme — **this is the default** |
| Dark | `DARK` (ordinal 2) | Force dark theme |

### Button Styling
- **Selected:** BrandTeal `#2A9D8F` background, white text, SemiBold weight, no border
- **Unselected:** `surfaceContainerHigh` background, `onSurfaceVariant` text, Normal weight, 1dp border in `outline` color, 1dp shadow

### Shape
- 10dp corner radius, 36dp height, equal width (1/3 of panel width minus gaps)

### Effect on App
The theme selection changes the entire app's color scheme:

**Light Color Scheme:**
```
primary:            #2A9D8F (BrandTeal)
onPrimary:          #FFFFFF
background:         #F8FAF9
onBackground:       #1F2937
surface:            #FFFFFF
onSurface:          #1F2937
onSurfaceVariant:   #6B7280
outline:            #E5E7EB
error:              #E53935
```

**Dark Color Scheme:**
```
primary:            #80CBC4
onPrimary:          #003730
background:         #121212
onBackground:       #E0E0E0
surface:            #1E1E1E
onSurface:          #E0E0E0
onSurfaceVariant:   #BDBDBD
outline:            #3A3A3A
error:              #EF9A9A
```

### PWA Implementation
- Use CSS custom properties (variables) for all colors
- Apply `prefers-color-scheme` media query for "Auto" mode
- Toggle a `data-theme="light|dark"` attribute on `<html>` for forced themes
- Persist in `localStorage`

---

## 4. Text Size / Font Scaling

### Options
| Option | Value | Multiplier | Display |
|--------|-------|------------|---------|
| Small | `SMALL` (ordinal 0) | `0.85×` base font size | Small "A" at 13sp |
| Normal | `NORMAL` (ordinal 1) | `1.0×` base font size (default) | Medium "A" at 16sp |
| Large | `LARGE` (ordinal 2) | `1.2×` base font size | Large "A" at 20sp |

### Button Styling
- **Selected:** BrandTeal `#2A9D8F` background at 10% opacity, BrandTeal text, Bold weight, 2dp BrandTeal border
- **Unselected:** `surfaceContainerHigh` background, `onSurfaceVariant` text, Bold weight, 1dp `outline` border

### Shape
- 10dp corner radius, 40dp height, equal width, bottom-aligned (so the "A" letters visually scale along the bottom edge)

### Effect on App
The multiplier is applied to the Compose `Density.fontScale` property, which means **every `sp`-unit text in the app scales proportionally**. The layout density (dp) is unaffected — only text grows/shrinks.

### PWA Implementation
- Apply a CSS `font-size` multiplier on `<html>` element
- Use `rem` units throughout the app so everything scales:
  ```css
  html[data-font-scale="small"]  { font-size: 13.6px; } /* 16 × 0.85 */
  html[data-font-scale="normal"] { font-size: 16px; }
  html[data-font-scale="large"]  { font-size: 19.2px; } /* 16 × 1.2 */
  ```
- All text sizes should use `rem` so they respond to this root change

---

## 5. High Contrast Mode

### Toggle
- **Label:** "High contrast" (14sp Medium)
- **Subtitle:** "Bolder text & borders" (12sp, onSurfaceVariant)
- **Default:** Off
- **Switch colors:** Checked = white thumb on BrandTeal track; Unchecked = white thumb on `#D1D5DB` track

### Effect on App
When enabled, the text colors are pushed to pure black/white for maximum readability:

**Light mode + High Contrast:**
```
onSurface:        #000000 (pure black — was #1F2937)
onSurfaceVariant: #374151 (darker gray — was #6B7280)
onBackground:     #000000 (pure black — was #1F2937)
```

**Dark mode + High Contrast:**
```
onSurface:        #FFFFFF (pure white — was #E0E0E0)
onSurfaceVariant: #E0E0E0 (brighter — was #BDBDBD)
onBackground:     #FFFFFF (pure white — was #E0E0E0)
```

All other colors (backgrounds, primary, accents) remain unchanged.

### PWA Implementation
- Add a `data-high-contrast="true"` attribute on `<html>`
- Override CSS custom properties for text colors:
  ```css
  html[data-high-contrast="true"] {
    --color-on-surface: #000000;
    --color-on-surface-variant: #374151;
    --color-on-background: #000000;
  }
  html[data-theme="dark"][data-high-contrast="true"] {
    --color-on-surface: #FFFFFF;
    --color-on-surface-variant: #E0E0E0;
    --color-on-background: #FFFFFF;
  }
  ```

---

## 6. Reduce Motion

### Toggle
- **Label:** "Reduce motion" (14sp Medium)
- **Subtitle:** "Minimize animations" (12sp, onSurfaceVariant)
- **Default:** Off
- **Switch colors:** Same as High Contrast toggle

### Effect on App
When enabled, **all page transition animations are disabled**:
- Enter transitions → `none` (was: fade in 300ms + slide in from right 25%)
- Exit transitions → `none` (was: fade out 250ms)
- Pop enter transitions → `none` (was: fade in 300ms + slide in from left 25%)
- Pop exit transitions → `none` (was: fade out 250ms + slide out to right 25%)

This affects all navigation between screens. UI micro-animations within screens (e.g., button ripples, loading spinners) are not affected.

### PWA Implementation
- Add `data-reduce-motion="true"` attribute on `<html>`
- Use CSS:
  ```css
  html[data-reduce-motion="true"] * {
    animation-duration: 0s !important;
    transition-duration: 0s !important;
  }
  ```
- Also respect the OS-level `prefers-reduced-motion` media query as a default:
  ```css
  @media (prefers-reduced-motion: reduce) {
    /* Same as above unless user explicitly set a preference */
  }
  ```

---

## 7. Language Selection

### Supported Languages
| Code | Display Name | String Resources |
|------|-------------|-----------------|
| `en` | English | `values/strings.xml` (default) |
| `sw` | Kiswahili | `values-sw/strings.xml` |
| `fr` | Francais | `values-fr/strings.xml` |
| `es` | Espanol | `values-es/strings.xml` |
| `ar` | العربية | `values-ar/strings.xml` |
| `hi` | हिन्दी | `values-hi/strings.xml` |

### Grid Layout
- 3 columns × 2 rows
- Each cell: 34dp height, 8dp corner radius, equal width
- Gap: 6dp vertical, 6dp horizontal (via `Arrangement.spacedBy`)

### Button Styling
- **Selected:** BrandTeal `#2A9D8F` background, white text, Bold weight, no border
- **Unselected:** `surfaceContainerHigh` background, `onSurfaceVariant` text, Normal weight, 1dp `outline` border
- **Font size:** 11sp

### Behavior
- Tapping a language immediately changes the app locale
- The panel **dismisses on language change** (calls `onDismiss()`)
- Android uses `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))`
- Persisted by the Android framework automatically (survives app restart)

### Current Language Detection
```kotlin
val locales = AppCompatDelegate.getApplicationLocales()
if (locales.isEmpty) Locale.getDefault().language else locales[0]?.language ?: "en"
```

### PWA Implementation
- Use `i18next` or a similar i18n library
- Load translation JSON files per locale
- Persist selected language in `localStorage`
- Set `<html lang="...">` and `dir="rtl"` for Arabic (`ar`)
- **Important:** Arabic needs RTL layout direction — the entire UI should mirror
- On load, check `localStorage` first, then fall back to `navigator.language`

---

## 8. Sound / Ringtone Customization

### Layout
Two ringtone rows stacked vertically with 8dp gap:

**Row structure:**
```
┌────────────────────────────────────────┐
│  Incoming Call                         │  ← 13sp Medium, onSurface
│  My Custom Ring              [Reset]   │  ← 11sp, BrandTeal (or onSurfaceVariant if default)
└────────────────────────────────────────┘
```

- **Container:** 10dp corner radius, `surfaceContainerHigh` background, 1dp `outline` border, 12dp padding
- **Subtitle text:** Shows ringtone name if custom, "System Default" if null
  - Custom ringtone name: `BrandTeal` color
  - System default: `onSurfaceVariant` color
- **Reset button:** Only visible when a custom ringtone is set; red text `#DC2626`, 11sp

### Ringtone Types
| Ringtone | Key | Used For |
|----------|-----|----------|
| Incoming Call | `call_ringtone_uri` | Video/voice call incoming ring |
| Consultation Request | `request_ringtone_uri` | New consultation request alert |

### Behavior
- Tap row → opens system ringtone picker (Android `RingtoneManager.ACTION_RINGTONE_PICKER`)
- Reset → sets URI to null (system default)

### PWA Implementation
- **Web has no ringtone picker.** Instead, provide a dropdown of preset notification sounds or allow audio file upload.
- Use the Web Audio API or `<audio>` element to play preview sounds.
- Store the selected sound identifier in `localStorage`.
- Use the selected sound when playing notification alerts via the Notification API or in-app audio.

---

## 9. Preferences Storage

### Storage Keys & Defaults
| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `theme_mode` | Integer (ordinal) | `1` (LIGHT) | 0=SYSTEM, 1=LIGHT, 2=DARK |
| `font_scale` | Integer (ordinal) | `1` (NORMAL) | 0=SMALL, 1=NORMAL, 2=LARGE |
| `high_contrast` | Boolean | `false` | High contrast text mode |
| `reduce_motion` | Boolean | `false` | Disable page transition animations |
| `call_ringtone_uri` | String (URI) | `null` | Custom call ringtone, null = system default |
| `request_ringtone_uri` | String (URI) | `null` | Custom request ringtone, null = system default |

### Android Implementation
- Stored in `SharedPreferences` named `"user_preferences"`
- Each preference is exposed as a Kotlin `StateFlow` (reactive — UI updates instantly on change)
- Read synchronously on startup, written via `prefs.edit().putX().apply()` (async)

### PWA Implementation
- Use `localStorage` for all preferences
- Expose as reactive state (e.g., React `useState` + context, or Svelte stores, or Vue `ref`)
- On app load:
  1. Read all preferences from `localStorage`
  2. Apply to DOM attributes: `data-theme`, `data-font-scale`, `data-high-contrast`, `data-reduce-motion`, `lang`
  3. Subscribe to changes and update DOM + `localStorage` in sync

Example:
```javascript
// Read
const theme = localStorage.getItem('theme_mode') ?? '1'; // default LIGHT
const fontScale = localStorage.getItem('font_scale') ?? '1'; // default NORMAL
const highContrast = localStorage.getItem('high_contrast') === 'true';
const reduceMotion = localStorage.getItem('reduce_motion') === 'true';
const language = localStorage.getItem('language') ?? 'en';

// Apply
document.documentElement.dataset.theme = ['system', 'light', 'dark'][theme];
document.documentElement.dataset.fontScale = ['small', 'normal', 'large'][fontScale];
document.documentElement.dataset.highContrast = highContrast;
document.documentElement.dataset.reduceMotion = reduceMotion;
document.documentElement.lang = language;
document.documentElement.dir = language === 'ar' ? 'rtl' : 'ltr';
```

---

## 10. Color System & Tokens

### Brand Colors
| Token | Hex | Usage |
|-------|-----|-------|
| BrandTeal | `#2A9D8F` | Primary accent, FAB, selected states, switches |
| Teal80 | `#80CBC4` | Dark theme primary |
| TealGrey40 | `#4A7C6F` | Secondary |
| Mint40 | `#5FAD96` | Tertiary |

### Light Theme Tokens
| Token | Hex | Usage |
|-------|-----|-------|
| TextPrimary | `#1F2937` | Headings, body text (onSurface) |
| TextSecondary | `#6B7280` | Subtitles, labels (onSurfaceVariant) |
| TextTertiary | `#9CA3AF` | Hints, captions |
| TextDisabled | `#D1D5DB` | Disabled text |
| SurfaceLight | `#F8FAF9` | Background |
| SurfaceContainerLight | `#FFFFFF` | Cards, surface |
| OutlineLight | `#E5E7EB` | Borders, dividers |

### Dark Theme Tokens
| Token | Hex | Usage |
|-------|-----|-------|
| onSurface | `#E0E0E0` | Body text |
| onSurfaceVariant | `#BDBDBD` | Subtitles |
| SurfaceDark | `#121212` | Background |
| SurfaceContainerDark | `#1E1E1E` | Cards, surface |
| OutlineDark | `#3A3A3A` | Borders, dividers |

### Semantic Colors
| Token | Hex | Usage |
|-------|-----|-------|
| SuccessGreen | `#4CAF50` | Success states |
| WarningOrange | `#FFA726` | Warnings, timer 1-3 min |
| ErrorRed | `#E53935` | Errors, destructive actions |

### Switch Colors (All Toggle Switches)
| State | Thumb | Track | Border |
|-------|-------|-------|--------|
| Checked | `#FFFFFF` | `#2A9D8F` (BrandTeal) | — |
| Unchecked | `#FFFFFF` | `#D1D5DB` | Transparent |

---

## 11. PWA Implementation Notes

### 1. FAB Positioning & Drag
- Use `position: fixed; bottom: 16px; right: 16px;`
- Implement drag via pointer events (`pointerdown`, `pointermove`, `pointerup`)
- Clamp position to `window.innerWidth/Height` minus FAB size
- Use `touch-action: none` on the FAB to prevent scroll interference
- Store position in component state (not persisted across page loads)

### 2. Panel Animation
- Use CSS `transform: scale()` + `opacity` transitions
- `.panel-enter { transform: scale(0.8); opacity: 0; }`
- `.panel-active { transform: scale(1); opacity: 1; transition: transform 250ms, opacity 200ms; }`
- `.panel-exit { transform: scale(0.8); opacity: 0; transition: transform 200ms, opacity 150ms; }`

### 3. Scrim
- `position: fixed; inset: 0; background: rgba(0,0,0,0.3); z-index: 999;`
- Click handler closes the panel
- FAB and panel sit above scrim (`z-index: 1000+`)

### 4. CSS Custom Properties Strategy
Define all theme tokens as CSS custom properties on `:root`:
```css
:root,
html[data-theme="light"] {
  --color-primary: #2A9D8F;
  --color-on-primary: #FFFFFF;
  --color-background: #F8FAF9;
  --color-surface: #FFFFFF;
  --color-on-surface: #1F2937;
  --color-on-surface-variant: #6B7280;
  --color-outline: #E5E7EB;
  --color-surface-container-high: #F3F4F6;
  --color-error: #E53935;
}

html[data-theme="dark"] {
  --color-primary: #80CBC4;
  --color-on-primary: #003730;
  --color-background: #121212;
  --color-surface: #1E1E1E;
  --color-on-surface: #E0E0E0;
  --color-on-surface-variant: #BDBDBD;
  --color-outline: #3A3A3A;
  --color-surface-container-high: #2A2A2A;
  --color-error: #EF9A9A;
}

html[data-theme="system"] {
  /* inherit from prefers-color-scheme */
}

@media (prefers-color-scheme: dark) {
  html[data-theme="system"] {
    --color-primary: #80CBC4;
    /* ... dark tokens ... */
  }
}
```

### 5. Language / RTL
- Arabic (`ar`) requires `dir="rtl"` on `<html>`
- All layout should use logical properties (`margin-inline-start` not `margin-left`, `padding-inline-end` not `padding-right`)
- The FAB should flip to bottom-left in RTL mode
- The panel anchor should flip accordingly

### 6. Accessibility Panel Should Be Accessible
- FAB: `role="button"`, `aria-label="Accessibility settings"`, `aria-expanded="true|false"`
- Panel: `role="dialog"`, `aria-label="Display & Accessibility"`, trap focus when open
- Theme/Font/Language buttons: `role="radiogroup"` with `role="radio"` children
- Toggles: native `<input type="checkbox" role="switch">` or proper ARIA switch
- Scrim: `aria-hidden="true"`, keyboard `Escape` closes the panel

### 7. Notification Sounds (PWA alternative to Ringtone Picker)
Since web has no system ringtone picker:
- Bundle 4-6 preset notification sounds (e.g., chime, bell, pulse, tone)
- Show a dropdown/radio list with play-preview button next to each
- Allow "System Default" option (uses default Notification API sound)
- Store selected sound ID in `localStorage`
- Play via `new Audio('/sounds/selected.mp3').play()` for in-app alerts

### 8. Integration Checklist
- [ ] FAB renders on all pages (fixed position, persistent across navigation)
- [ ] Draggable with viewport clamping
- [ ] Panel opens/closes with scale+fade animation
- [ ] Scrim overlay when panel is open
- [ ] Theme: 3 options, immediately applied, persisted
- [ ] Font scale: 3 options, scales all text, persisted
- [ ] High contrast: toggle, modifies text color tokens, persisted
- [ ] Reduce motion: toggle, disables all transitions/animations, persisted
- [ ] Language: 6 options, switches i18n locale + RTL for Arabic, persisted
- [ ] Sounds: 2 configurable notification sounds with preview + reset
- [ ] All preferences survive page reload (localStorage)
- [ ] Panel is keyboard-navigable and screen-reader friendly
