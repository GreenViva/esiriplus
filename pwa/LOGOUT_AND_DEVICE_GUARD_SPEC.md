# eSIRI Plus — Logout Cleanup & Device Guard (for PWA Development)

This document describes how the app ensures that when a doctor logs out, their device stops receiving consultation requests — and how stale push notifications for a previous account are rejected. The PWA must replicate this behavior exactly.

---

## Table of Contents

1. [The Problem](#1-the-problem)
2. [Logout Flow — Server-Side Cleanup](#2-logout-flow--server-side-cleanup)
3. [Logout Flow — Client-Side Cleanup](#3-logout-flow--client-side-cleanup)
4. [FCM / Push Token Lifecycle](#4-fcm--push-token-lifecycle)
5. [Client-Side Push Guard](#5-client-side-push-guard)
6. [Login Flow — Fresh Token Registration](#6-login-flow--fresh-token-registration)
7. [Edge Function: `logout`](#7-edge-function-logout)
8. [Edge Function: `handle-consultation-request` (Updated)](#8-edge-function-handle-consultation-request-updated)
9. [PWA Implementation](#9-pwa-implementation)

---

## 1. The Problem

FCM/push tokens are **device-bound**, not account-bound. When Doctor A logs out and Doctor B logs in on the same device:

- The push token registered for Doctor A still exists in the `fcm_tokens` table pointing to Doctor A's `user_id`
- When a patient sends a consultation request to Doctor A, the server looks up Doctor A's token, finds the old device token, and sends the push
- The device receives the push and rings — even though Doctor B is now logged in
- This causes duplicate ringing, confusion, and potential double-payment if Doctor B accidentally accepts Doctor A's request

**The fix has three layers:**

| Layer | What | Where | Catches |
|-------|------|-------|---------|
| 1. Server cleanup | Delete FCM token + mark offline on logout | `logout` edge function | Normal logout |
| 2. Token destruction | Delete the push token from the push service | Client-side on logout | Prevents ANY push to old token |
| 3. Client guard | Verify push is for the current doctor before ringing | Client-side push handler | Stale pushes that slip through |

---

## 2. Logout Flow — Server-Side Cleanup

**Edge function:** `logout` (POST, authenticated)

When called, the server:

1. **Deletes the FCM token** from `fcm_tokens` table for this `user_id`
   ```sql
   DELETE FROM fcm_tokens WHERE user_id = :doctor_id
   ```
   → The server can no longer send pushes to the old device

2. **Marks the doctor offline**
   ```sql
   UPDATE doctor_profiles SET is_available = false WHERE doctor_id = :doctor_id
   ```

3. **Closes the online log entry**
   ```sql
   UPDATE doctor_online_logs SET went_offline_at = NOW()
   WHERE doctor_id = :doctor_id AND went_offline_at IS NULL
   ```

4. **Revokes the Supabase Auth session** (best-effort)
   ```
   supabase.auth.admin.signOut(jwt, "global")
   ```

### Request
```
POST /functions/v1/logout
Headers:
  Authorization: Bearer <anon_key>
  X-Doctor-Token: <doctor_jwt>
  Content-Type: application/json
Body: {} (empty)
```

### Response
```json
{ "logged_out": true }
```

Errors are non-fatal — client proceeds with local cleanup even if the server call fails.

---

## 3. Logout Flow — Client-Side Cleanup

When the doctor taps "Sign Out" (from the biometric lock screen), the following happens in order:

### Step 1: Stop the Online Service
- Stop the `DoctorOnlineService` (foreground service that keeps realtime subscription alive)
- Unsubscribe from all Supabase Realtime channels
- Stop ringtone/vibration
- Hide the floating bubble overlay

### Step 2: Call the `logout` Edge Function
- POST to `/functions/v1/logout` with the doctor's JWT
- Best-effort — failure doesn't block logout

### Step 3: Delete the Push Token
- **Android:** `FirebaseMessaging.getInstance().deleteToken()` — destroys the FCM token on the device. Firebase will generate a fresh one on next login.
- **PWA:** Unsubscribe the Web Push subscription (see Section 9)

### Step 4: Clear Local State
- Clear auth tokens from encrypted storage
- Clear the plain-prefs session backup
- Clear all Room/IndexedDB tables (sessions, users, consultations, messages, etc.)
- Re-seed reference data (service tiers, app config)

### Sequence Diagram
```
Doctor taps "Sign Out"
    ↓
DoctorOnlineService.stop()
    → unsubscribe realtime
    → log offline
    ↓
edgeFunctionClient.invoke("logout")
    → server deletes FCM token
    → server marks offline
    → server revokes session
    ↓
FirebaseMessaging.deleteToken()  /  pushSubscription.unsubscribe()
    → push token destroyed
    ↓
tokenManager.clearTokens()
sessionBackup.clear()
database.clearAllTables()
    ↓
Navigate to role selection / login screen
```

---

## 4. FCM / Push Token Lifecycle

### Registration (Login)
```
Doctor logs in
    ↓
Get push token (FCM / Web Push)
    ↓
POST /functions/v1/update-fcm-token
  { "fcm_token": "<token>" }
    ↓
Server upserts: fcm_tokens (user_id, token, updated_at)
    → PRIMARY KEY is user_id
    → One doctor = one token at a time
```

### Deregistration (Logout)
```
Doctor logs out
    ↓
POST /functions/v1/logout
    → DELETE FROM fcm_tokens WHERE user_id = :doctor_id
    ↓
Delete push token locally
    → FirebaseMessaging.deleteToken() / pushSubscription.unsubscribe()
```

### Re-registration (Next Login)
```
New doctor logs in on same device
    ↓
Push service generates a FRESH token (old one is dead)
    ↓
POST /functions/v1/update-fcm-token
  { "fcm_token": "<new_token>" }
    ↓
Server upserts with new doctor's user_id
```

### `fcm_tokens` Table Schema
```sql
CREATE TABLE fcm_tokens (
  user_id TEXT PRIMARY KEY,      -- doctor UUID (one row per doctor)
  token   TEXT NOT NULL,          -- FCM/Web Push token
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

---

## 5. Client-Side Push Guard

Even with server cleanup and token deletion, there's a window where a stale push could arrive (e.g., push was already in-flight when logout happened). The client adds a final safety check.

### How It Works

Every `CONSULTATION_REQUEST` push now includes `doctor_id` in the data payload:

```json
{
  "type": "consultation_request",
  "request_id": "uuid",
  "service_type": "gp",
  "doctor_id": "uuid-of-target-doctor"    ← NEW
}
```

When the client receives this push:

1. Extract `doctor_id` from the push data
2. Get the currently logged-in doctor's ID (from the JWT `sub` claim or stored session)
3. **If they don't match → drop the push silently** (log a warning)
4. If they match → proceed normally (ring, show dialog, etc.)

### Pseudocode
```javascript
function onPushReceived(data) {
  if (data.type === "consultation_request") {
    const targetDoctorId = data.doctor_id;
    const currentUserId = getCurrentUserId(); // from stored JWT or session

    if (currentUserId && targetDoctorId && currentUserId !== targetDoctorId) {
      console.warn(`Push for doctor ${targetDoctorId} but current user is ${currentUserId} — dropping`);
      return; // silently ignore
    }

    // Process the consultation request normally
    showIncomingRequestDialog(data.request_id, data.service_type);
  }
}
```

---

## 6. Login Flow — Fresh Token Registration

When a doctor logs in (or the app starts with a valid session):

1. **Get a push token** from the push service
   - Android: `FirebaseMessaging.getInstance().token`
   - PWA: `pushManager.subscribe()` or existing `pushManager.getSubscription()`

2. **Register it on the server**
   ```
   POST /functions/v1/update-fcm-token
   { "fcm_token": "<token>" }
   ```
   - Uses the doctor's auth token (X-Doctor-Token)
   - Server upserts into `fcm_tokens` with `user_id = doctor's UUID`

3. **This replaces any stale token** for this doctor
   - If the doctor was previously on a different device, the old token is overwritten
   - The old device can no longer receive pushes for this doctor

This happens:
- On initial login (after `loginDoctor` succeeds)
- On app cold start (in `MainViewModel.syncFcmTokenIfNeeded()`)
- When Firebase/Web Push issues a new token (token refresh callback)

---

## 7. Edge Function: `logout`

**File:** `supabase/functions/logout/index.ts`

```typescript
// POST /functions/v1/logout
// Auth: Doctor (X-Doctor-Token)
// Rate limit: 5/min

Steps:
1. validateAuth(req) → get doctor's user_id
2. DELETE FROM fcm_tokens WHERE user_id = :user_id
3. UPDATE doctor_profiles SET is_available = false WHERE doctor_id = :user_id
4. UPDATE doctor_online_logs SET went_offline_at = NOW() WHERE doctor_id = :user_id AND went_offline_at IS NULL
5. supabase.auth.admin.signOut(jwt, "global") — best-effort session revocation
6. Return { logged_out: true }
```

All steps are best-effort — failures are logged but don't block the response.

---

## 8. Edge Function: `handle-consultation-request` (Updated)

The push notification data payload now includes `doctor_id`:

```typescript
// In handle-consultation-request, when sending push to doctor:
await supabase.functions.invoke("send-push-notification", {
  body: {
    user_id: body.doctor_id,
    title: "New Consultation Request",
    body: "Patient requesting consultation...",
    type: "consultation_request",
    data: {
      request_id: request.request_id,
      service_type: body.service_type,
      doctor_id: body.doctor_id,          // ← ADDED for client-side guard
    },
  },
});
```

---

## 9. PWA Implementation

### Web Push Setup

The PWA uses the **Web Push API** (via service workers) instead of FCM. The concepts are identical:

#### Registration
```javascript
// After doctor login
const registration = await navigator.serviceWorker.ready;
const subscription = await registration.pushManager.subscribe({
  userVisibleOnly: true,
  applicationServerKey: VAPID_PUBLIC_KEY,
});

// Send the subscription to the server
await fetch('/functions/v1/update-fcm-token', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${SUPABASE_ANON_KEY}`,
    'X-Doctor-Token': doctorJwt,
    'Content-Type': 'application/json',
  },
  body: JSON.stringify({
    fcm_token: JSON.stringify(subscription),  // Web Push subscription object
  }),
});
```

#### Deregistration (Logout)
```javascript
async function logout() {
  // 1. Call server logout
  await fetch('/functions/v1/logout', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${SUPABASE_ANON_KEY}`,
      'X-Doctor-Token': doctorJwt,
      'Content-Type': 'application/json',
    },
    body: '{}',
  });

  // 2. Unsubscribe Web Push
  const registration = await navigator.serviceWorker.ready;
  const subscription = await registration.pushManager.getSubscription();
  if (subscription) {
    await subscription.unsubscribe();
  }

  // 3. Clear local state
  localStorage.clear();              // or selective clear
  await clearIndexedDB();            // clear cached data
  
  // 4. Navigate to login
  window.location.href = '/login';
}
```

#### Service Worker Push Handler (with guard)
```javascript
// sw.js
self.addEventListener('push', (event) => {
  const data = event.data?.json();
  
  if (data?.type === 'consultation_request') {
    // Guard: check if this push is for the current doctor
    // Read current user ID from IndexedDB or a dedicated cache
    event.waitUntil(
      getCurrentUserId().then((currentUserId) => {
        if (currentUserId && data.doctor_id && currentUserId !== data.doctor_id) {
          console.warn(`Push for ${data.doctor_id} but logged in as ${currentUserId} — dropping`);
          return; // silently ignore
        }

        // Show notification
        return self.registration.showNotification('New Consultation Request', {
          body: 'A patient is requesting a consultation. Respond within 60s.',
          tag: `request-${data.request_id}`,
          requireInteraction: true,
          data: data,
        });
      })
    );
  }
});
```

### `send-push-notification` Adaptation for Web Push

The `send-push-notification` edge function currently uses FCM (Firebase Cloud Messaging HTTP v1 API). For Web Push, you have two options:

**Option A: Use FCM for Web Push too**
- Register the Web Push subscription with FCM (via Firebase JS SDK)
- Store the FCM token (not the raw subscription) in `fcm_tokens`
- `send-push-notification` continues using FCM API — works for both Android and Web

**Option B: Store raw Web Push subscriptions**
- Store the full subscription JSON (`endpoint`, `keys.p256dh`, `keys.auth`) in `fcm_tokens.token`
- `send-push-notification` detects the format and uses the Web Push protocol (via `web-push` npm package) for Web clients and FCM for Android clients

**Recommended:** Option A (FCM for Web) — simpler, reuses existing infrastructure.

### Key Differences from Android

| Aspect | Android | PWA |
|--------|---------|-----|
| Push service | FCM (Firebase Cloud Messaging) | Web Push API (or FCM via Firebase JS SDK) |
| Token deletion | `FirebaseMessaging.deleteToken()` | `subscription.unsubscribe()` |
| Push handler | `FirebaseMessagingService.onMessageReceived()` | Service Worker `push` event |
| Background delivery | FCM data messages wake the app | Service Worker handles push even when tab is closed |
| Token refresh | `onNewToken()` callback | `pushsubscriptionchange` event in service worker |
| Token storage | `fcm_tokens` table (same) | `fcm_tokens` table (same) |

### Checklist

- [ ] On login: subscribe to Web Push, register token with `/functions/v1/update-fcm-token`
- [ ] On logout: call `/functions/v1/logout`, unsubscribe Web Push, clear all local state
- [ ] Service worker: validate `doctor_id` in push data matches current session before showing notification
- [ ] On token refresh: re-register with server via `update-fcm-token`
- [ ] Clicking a push notification opens the app/tab and navigates to the incoming request dialog
- [ ] If no doctor is logged in when push arrives, drop it silently
