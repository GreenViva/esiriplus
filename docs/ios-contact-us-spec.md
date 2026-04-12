# Contact Us Section — iOS Implementation Spec

## Overview

Add a **"Contact Us" section** to three screens: **Role Selection (landing/homepage)**, **Patient Dashboard**, and **Doctor Dashboard**. This is a small, centered inline widget — not a separate screen.

---

## Placement

| Screen              | Position                                                              |
| ------------------- | --------------------------------------------------------------------- |
| **Role Selection**  | Bottom of screen, above the copyright footer, pushed down with spacer |
| **Patient Home**    | Bottom of the scrollable content area                                 |
| **Doctor Dashboard**| Bottom of the scrollable content area                                 |

All three use the **identical** reusable component — extract it as a shared SwiftUI view.

---

## Layout (reusable `ContactUsView`)

```
┌──────────────────────────────────────────────────────┐
│              For help contact us                     │  <- heading, centered
│                 (6pt spacing)                        │
│  phone +255 663 582 994    mail support@esiri.africa │  <- single row, centered
└──────────────────────────────────────────────────────┘
```

**Structure:**
- `VStack(alignment: .center)`
  - Heading `Text("For help contact us")`
  - 6pt spacer
  - `HStack` with phone + email items side by side

---

## Styling

| Element              | Value                                            |
| -------------------- | ------------------------------------------------ |
| **Heading font**     | System 13pt, semibold                            |
| **Heading color**    | `.secondary` (equivalent to `onSurfaceVariant`)  |
| **Contact text font**| System 12pt, medium                              |
| **Contact text color**| `Color(hex: "#2A9D8F")` (BrandTeal)             |
| **Icon size**        | 14pt                                             |
| **Icon tint**        | BrandTeal `#2A9D8F`                              |
| **Icon–text spacing**| 4pt between icon and text                        |
| **Group spacing**    | 16pt between phone group and email group         |
| **Horizontal padding**| 4pt                                             |

---

## Contact Methods & Actions

| Method    | Display Text         | SF Symbol       | Tap Action                    |
| --------- | -------------------- | --------------- | ----------------------------- |
| **Phone** | `+255 663 582 994`   | `phone.fill`    | Open dialer: `tel:+255663582994` |
| **Email** | `support@esiri.africa` | `envelope.fill` | Open mail: `mailto:support@esiri.africa` |

**iOS tap handler:**
```swift
// Phone
if let url = URL(string: "tel:+255663582994") {
    UIApplication.shared.open(url)
}

// Email
if let url = URL(string: "mailto:support@esiri.africa") {
    UIApplication.shared.open(url)
}
```

---

## SwiftUI Reference Implementation

```swift
struct ContactUsView: View {
    private let brandTeal = Color(red: 0.165, green: 0.616, blue: 0.561) // #2A9D8F

    var body: some View {
        VStack(spacing: 6) {
            Text("For help contact us")
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(.secondary)

            HStack(spacing: 16) {
                // Phone
                HStack(spacing: 4) {
                    Image(systemName: "phone.fill")
                        .font(.system(size: 14))
                        .foregroundColor(brandTeal)
                    Text("+255 663 582 994")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(brandTeal)
                }
                .onTapGesture {
                    if let url = URL(string: "tel:+255663582994") {
                        UIApplication.shared.open(url)
                    }
                }

                // Email
                HStack(spacing: 4) {
                    Image(systemName: "envelope.fill")
                        .font(.system(size: 14))
                        .foregroundColor(brandTeal)
                    Text("support@esiri.africa")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(brandTeal)
                }
                .onTapGesture {
                    if let url = URL(string: "mailto:support@esiri.africa") {
                        UIApplication.shared.open(url)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 4)
    }
}
```

---

## Key Notes

- **Single shared component** — don't duplicate across screens
- **No navigation** — taps open native dialer/mail directly via URL schemes
- **No WhatsApp or other channels** — phone + email only
- **Localization**: The heading string `"For help contact us"` has translations (Swahili, Hindi, Arabic, French, Spanish) — add to `Localizable.strings` if the iOS app supports localization
- **Brand color must match exactly**: `#2A9D8F`

---

## Android Reference

The Android implementation lives in these files (identical `ContactUsSection()` composable in each):

- `feature/patient/.../PatientHomeScreen.kt` — line 952
- `feature/doctor/.../DoctorDashboardScreen.kt` — line 3350
- `feature/auth/.../RoleSelectionScreen.kt` — line 397
