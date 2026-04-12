# Patient Chat Greeting Flow — iOS Implementation Spec

## Overview

When a patient opens the chat screen after a doctor accepts their consultation request, an **animated greeting overlay** plays on top of the chat. It simulates the doctor "typing" and sending welcome messages, then presents two action buttons (Call or Text) so the patient can choose how to proceed.

This is a **client-side UI-only sequence** — the greeting messages are NOT stored in the database. Only the auto-text message sent after the patient picks "Text" is a real persisted message.

---

## When to Show the Greeting

Show the greeting when **both** conditions are true:

1. **No messages exist yet** in the conversation, OR the consultation was **reopened** (`is_reopened = true`)
2. The patient is **NOT** in passive follow-up mode (i.e., not a Royal-tier user browsing a completed consultation's chat history)

```
if (messages.isEmpty || consultation.isReopened) {
    if (!isFollowUpMode || consultation.isReopened) {
        startGreetingSequence()
    }
}
```

**Do NOT show** for:
- Consultations that already have messages (patient is returning to an active chat)
- Passive follow-up browsing (Royal tier, consultation completed, within 14-day window)

---

## Greeting Phases (State Machine)

```
NONE → TYPING → MSG_WELCOME → TYPING → MSG_SERVE → TYPING → MSG_CHOICES → DONE
```

| Phase          | What the user sees                                                | Duration before next |
| -------------- | ----------------------------------------------------------------- | -------------------- |
| `NONE`         | Nothing — greeting not started or already completed               | —                    |
| `TYPING`       | Animated typing indicator ("..." dots cycling)                    | 1200ms (first), 1000ms (subsequent) |
| `MSG_WELCOME`  | First bubble: "Hello, welcome! 👋"                               | 800ms                |
| `MSG_SERVE`    | Second bubble: "Dr. {name} is here to serve you."                | 800ms                |
| `MSG_CHOICES`  | Third bubble: "How would you like to proceed?" + Call/Text buttons| Waits for user tap   |
| `DONE`         | Overlay dismissed, normal chat visible                           | —                    |

### Timing Sequence

```
t=0ms      → show TYPING indicator
t=1200ms   → show MSG_WELCOME bubble, hide typing
t=2000ms   → show TYPING indicator again
t=3000ms   → show MSG_SERVE bubble, hide typing
t=3800ms   → show TYPING indicator again
t=4800ms   → show MSG_CHOICES bubble + buttons, hide typing
             (wait for patient to tap Call or Text)
```

---

## Greeting Strings

| Key                              | English Value                                    | Notes                          |
| -------------------------------- | ------------------------------------------------ | ------------------------------ |
| `greeting_welcome`               | `Hello, welcome! 👋`                             | Always shown                   |
| `greeting_here_to_serve`         | `Dr. %@ is here to serve you.`                   | When doctor name is available  |
| `greeting_here_to_serve_generic` | `We are here to serve you.`                       | Fallback if no doctor name     |
| `greeting_how_to_proceed`        | `How would you like to proceed?`                  | Shown with action buttons      |
| `greeting_choice_call`           | `Call`                                            | Button label                   |
| `greeting_choice_text`           | `Text`                                            | Button label                   |
| `greeting_text_auto_message`     | `Hi, I'd like to consult via text messages.`      | Sent as real message on tap    |

Translations exist for: Swahili, Hindi, Arabic, French, Spanish.

---

## Visual Design

### Overlay Container
- **Full-screen** semi-transparent black background (`black @ 40% opacity`)
- Content aligned to **bottom center**
- Padding: 20pt horizontal, 24pt vertical

### Message Bubbles (`GreetingBubble`)
- **Background**: BrandTeal `#2A9D8F`
- **Text**: White, 15pt, medium weight
- **Shape**: Rounded rectangle — 16pt top-left, 16pt top-right, 16pt bottom-right, **4pt bottom-left** (chat-bubble tail effect)
- **Shadow**: 2pt elevation
- **Padding**: 16pt horizontal, 10pt vertical
- **Spacing**: 8pt between bubbles

### Typing Indicator (`GreetingTypingBubble`)
- **Background**: BrandTeal @ 80% opacity
- **Shape**: 12pt rounded rectangle
- **Content**: Animated dots — cycles between `.`, `..`, `...` every 400ms
- **Text**: White, 22pt, bold
- **Padding**: 20pt horizontal, 6pt vertical
- **Shadow**: 1pt elevation

### Animations
- **Typing indicator**: Fade in (200ms) / Fade out (150ms)
- **Message bubbles**: Slide up from bottom + Fade in (300ms each)

### Action Buttons (shown at `MSG_CHOICES` phase)

Two side-by-side buttons, equal width, 12pt gap between them:

| Button   | Background | Text Color | Icon (SF Symbol)  |
| -------- | ---------- | ---------- | ----------------- |
| **Call** | White      | BrandTeal  | `phone.fill`      |
| **Text** | BrandTeal  | White      | `doc.text.fill`   |

Both buttons:
- Height: 52pt
- Corner radius: 14pt
- Shadow elevation: 4pt
- Icon size: 20pt, 8pt spacing to label
- Label: Bold, 15pt

#### Call Button Behavior
On tap:
1. Dismiss greeting overlay (set phase = `DONE`)
2. Show a dropdown/action sheet with two options:
   - **Voice Call** — icon: `phone.fill`, action: initiate audio call with type `"AUDIO"`
   - **Video Call** — icon: `video.fill`, action: initiate video call with type `"VIDEO"`

#### Text Button Behavior
On tap:
1. Dismiss greeting overlay (set phase = `DONE`)
2. Automatically send a real message: `"Hi, I'd like to consult via text messages."`
   - This goes through the normal message sending flow (edge function `handle-messages`)
   - It appears in the chat as a regular patient message

---

## Architecture

### State

```swift
enum GreetingPhase {
    case none
    case typing
    case msgWelcome
    case msgServe
    case msgChoices
    case done
}
```

Add `greetingPhase` to your chat ViewModel/state:

```swift
@Published var greetingPhase: GreetingPhase = .none
```

### ViewModel Logic

```swift
func startGreetingSequence() async {
    greetingPhase = .typing
    try? await Task.sleep(for: .milliseconds(1200))

    greetingPhase = .msgWelcome
    try? await Task.sleep(for: .milliseconds(800))

    greetingPhase = .typing
    try? await Task.sleep(for: .milliseconds(1000))

    greetingPhase = .msgServe
    try? await Task.sleep(for: .milliseconds(800))

    greetingPhase = .typing
    try? await Task.sleep(for: .milliseconds(1000))

    greetingPhase = .msgChoices
    // Now wait for user tap
}

func onGreetingChooseText() {
    greetingPhase = .done
    sendMessage("Hi, I'd like to consult via text messages.")
}

func onGreetingChooseCall() {
    greetingPhase = .done
    // Caller shows call-type action sheet
}
```

### Trigger Point

In your chat initialization (after loading messages):

```swift
func initChat(consultationId: String) async {
    // ... load messages, set up realtime subscription ...

    let consultation = await getConsultation(consultationId)
    let isReopened = consultation?.isReopened == true

    if messages.isEmpty || isReopened {
        if !isFollowUpMode || isReopened {
            await startGreetingSequence()
        }
    }
}
```

---

## SwiftUI Reference — Greeting Overlay

```swift
struct GreetingOverlay: View {
    let phase: GreetingPhase
    let doctorName: String
    let onChooseText: () -> Void
    let onChooseCall: () -> Void

    private let brandTeal = Color(red: 0.165, green: 0.616, blue: 0.561)

    var body: some View {
        ZStack(alignment: .bottom) {
            // Semi-transparent backdrop
            Color.black.opacity(0.4)
                .ignoresSafeArea()

            VStack(alignment: .leading, spacing: 0) {

                // Typing indicator
                if phase == .typing {
                    GreetingTypingBubble()
                        .transition(.opacity)
                }

                // Message 1: Welcome
                if phase >= .msgWelcome && phase != .typing {
                    GreetingBubble(text: "Hello, welcome! 👋")
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                    Spacer().frame(height: 8)
                }

                // Message 2: Here to serve
                if phase >= .msgServe && phase != .typing {
                    GreetingBubble(
                        text: doctorName.isEmpty
                            ? "We are here to serve you."
                            : "Dr. \(doctorName) is here to serve you."
                    )
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    Spacer().frame(height: 8)
                }

                // Message 3: Choices
                if phase == .msgChoices {
                    GreetingBubble(text: "How would you like to proceed?")
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                    Spacer().frame(height: 14)

                    HStack(spacing: 12) {
                        // Call button (white bg, teal text)
                        Button(action: onChooseCall) {
                            Label("Call", systemImage: "phone.fill")
                                .font(.system(size: 15, weight: .bold))
                        }
                        .frame(maxWidth: .infinity, minHeight: 52)
                        .background(Color.white)
                        .foregroundColor(brandTeal)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                        .shadow(radius: 2, y: 2)

                        // Text button (teal bg, white text)
                        Button(action: onChooseText) {
                            Label("Text", systemImage: "doc.text.fill")
                                .font(.system(size: 15, weight: .bold))
                        }
                        .frame(maxWidth: .infinity, minHeight: 52)
                        .background(brandTeal)
                        .foregroundColor(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                        .shadow(radius: 2, y: 2)
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 24)
            .animation(.easeInOut(duration: 0.3), value: phase)
        }
    }
}
```

---

## Key Behaviors to Get Right

1. **Messages accumulate** — each new bubble stays visible while the next one animates in. Only the typing indicator hides when a message appears.
2. **Overlay blocks chat interaction** — the patient cannot type or scroll while the greeting is active.
3. **No database persistence** — greeting is purely UI state. If the patient kills the app mid-greeting and reopens, the greeting replays (because messages list is still empty).
4. **Only "Text" sends a message** — choosing "Call" does NOT send any message, it just opens the call-type picker.
5. **Doctor name** comes from the consultation data (fetched during chat init). Use the generic fallback if unavailable.
6. **Reopened consultations** always replay the greeting, even if previous session messages exist. This is because `is_reopened` means a new active session has started.

---

## Android Reference Files

| File | Lines | What it contains |
| ---- | ----- | ---------------- |
| `PatientConsultationViewModel.kt` | 50–63 | `GreetingPhase` enum |
| `PatientConsultationViewModel.kt` | 326–336 | Trigger conditions |
| `PatientConsultationViewModel.kt` | 348–373 | `startGreetingSequence()`, choice handlers |
| `PatientConsultationScreen.kt` | 369–530 | `GreetingOverlay` composable |
| `PatientConsultationScreen.kt` | 532–571 | `GreetingBubble` + `GreetingTypingBubble` |
| `strings.xml` (patient feature) | 326–333 | Greeting strings |
