# iOS — Fix the Doctor Earnings % Pill (if it exists)

This is a **fix note**, not a from-scratch spec. It documents a bug that
shipped on Android and was fixed in commit `789f241` on 2026-04-28. If the
iOS doctor dashboard has the same hard-coded percentage label, apply the
same fix.

---

## 1. The bug to look for

Open the iOS doctor dashboard's earnings transaction list. For each row,
check whether there's a small label/pill that displays a percentage —
typically next to the type name ("Consultation", "Follow-up", etc.).

If the pill is hard-coded to two values — something like:

```swift
// the bug
Text(earningType.contains("follow") ? "20%" : "30%")
```

…then it's the same bug. Those constants come from a previous **50/30/20**
revenue-split model that was replaced by **60/25/15** on 2026-04-23 (and
Royal stays at 50/50). The pill now lies — a Royal `consultation` earning
of TSh 50,000 on a TSh 100,000 fee shows "30%", and an Economy `consultation`
earning of TSh 25,000 on a TSh 100,000 fee also shows "30%".

The Android version did exactly this — see the "before" diff in
`789f241`:

```
feature/doctor/.../DoctorDashboardScreen.kt
- Text(text = if (tx.earningType.contains("follow")) "20%" else "30%", …)
```

---

## 2. The fix — drop the pill

Remove the percentage label entirely. Keep:

- the type label ("Consultation" / "Follow-up" / "Substitute" / "Substitute FU")
- the date
- the formatted TSh amount
- the status

That's all the doctor needs at-a-glance.

If you want the type label to keep a visual cue, colour it instead of using
a separate pill — Android colours follow-up rows purple (`#8B5CF6`) and
everything else teal.

---

## 3. Why not just change "30%" → "25%"?

Tempting, but wrong:

- Royal consultations pay 50%, not 25%.
- Economy follow-up escrow releases at 15%, not 20%.
- Substitute splits are different again.
- The percentages live in `app_config` and can be re-tuned at runtime.

A single hard-coded label can't represent any of that without lying.
Computing `(amount / consultation_fee) * 100` per row is honest but needs
the consultation fee plumbed through the model — Android opted not to,
because the amount alone is what the doctor cares about.

---

## 4. Server-side is fine

The trigger that creates the earning row (`fn_auto_create_doctor_earning`)
already reads the correct percentages from `app_config`:

```
doctor_earnings_split_pct = 50    // Royal share
economy_consultation_pct  = 25    // Economy doctor share
economy_followup_pct      = 15    // Economy follow-up escrow
```

So the **amount** column on every `doctor_earnings` row is correct. Only
the UI label was lying. iOS only needs to fix the display.

---

## 5. Quick checklist

```
[ ] Search the iOS codebase for hard-coded "30%" or "20%" near earnings UI
[ ] If found: remove the pill, leave type label + date + amount + status
[ ] Optionally colour the type label (teal default, purple for follow-ups)
[ ] No backend / model changes needed
```
