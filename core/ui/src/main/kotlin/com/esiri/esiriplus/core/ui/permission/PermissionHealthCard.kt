package com.esiri.esiriplus.core.ui.permission

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Universal "your phone won't ring reliably without these" banner. Shown to
 * any user whose role depends on real-time pushes — patients (incoming
 * call rings, medication reminders), doctors (consultation requests),
 * nurses (medication-reminder rings), clinical officers (royal escalation
 * rings).
 *
 * Three permissions are checked, each with a deep-link CTA:
 *
 *   1. Full-screen intent (Android 14+) — required for `setFullScreenIntent`
 *      to wake the lock screen on incoming calls.
 *   2. Battery-optimization exemption — required so the FCM service +
 *      foreground IncomingCallService aren't killed in background.
 *   3. OEM autostart (Xiaomi / OPPO / Vivo / Huawei / OnePlus) — required
 *      on aggressive Chinese-vendor builds to receive FCM after device
 *      reboot or aggressive task-killing.
 *
 * The card auto-hides when nothing is missing, and re-checks every onResume
 * so the granted state shows up immediately after a Settings round-trip.
 */
@Composable
fun PermissionHealthCard(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val fullScreenGranted = remember { mutableStateOf(canUseFullScreenIntent(context)) }
    val batteryGranted = remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    // OEM autostart can't be queried reliably — we expose the link if the
    // manufacturer is known to need it, and let the user dismiss it.
    val showAutostart = remember { mutableStateOf(needsOemAutostartHint()) }

    DisposableLifecycleEffect(lifecycleOwner.lifecycle) { event ->
        if (event == Lifecycle.Event.ON_RESUME) {
            fullScreenGranted.value = canUseFullScreenIntent(context)
            batteryGranted.value = isIgnoringBatteryOptimizations(context)
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        fullScreenGranted.value = canUseFullScreenIntent(context)
        batteryGranted.value = isIgnoringBatteryOptimizations(context)
    }

    fun safeLaunch(primary: Intent, fallback: Intent) {
        try {
            val resolved = context.packageManager.queryIntentActivities(primary, 0)
            if (resolved.isNotEmpty()) launcher.launch(primary) else launcher.launch(fallback)
        } catch (_: Exception) {
            try { launcher.launch(fallback) } catch (_: Exception) { /* nothing else we can do */ }
        }
    }

    val needsFullScreen = !fullScreenGranted.value
    val needsBattery = !batteryGranted.value
    val needsAutostart = showAutostart.value
    if (!needsFullScreen && !needsBattery && !needsAutostart) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFFCD6B4), RoundedCornerShape(12.dp))
            .background(Color(0xFFFFF7ED))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(28.dp).background(Color(0xFFEA580C), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(text = "!", color = Color.White, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Make sure your phone can reach us",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFB45309),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Without these settings the app may not ring or wake the screen for incoming calls and reminders.",
            fontSize = 12.sp,
            color = Color(0xFF92400E),
        )

        if (needsFullScreen) {
            Spacer(Modifier.height(10.dp))
            PermissionRow(
                title = "Allow full-screen ringing",
                subtitle = "Lets the call wake the lock screen instead of being a silent notification.",
                cta = "Open settings",
                onClick = {
                    safeLaunch(
                        primary = buildFullScreenIntentSettings(context),
                        fallback = buildAppDetailsSettings(context),
                    )
                },
            )
        }
        if (needsBattery) {
            Spacer(Modifier.height(8.dp))
            PermissionRow(
                title = "Stop battery from killing the app",
                subtitle = "Required for rings and reminders to arrive when the app is in background.",
                cta = "Open settings",
                onClick = {
                    safeLaunch(
                        primary = buildBatteryOptimizationsRequest(context),
                        fallback = buildAppDetailsSettings(context),
                    )
                },
            )
        }
        if (needsAutostart) {
            Spacer(Modifier.height(8.dp))
            PermissionRow(
                title = "Allow autostart",
                subtitle = "On ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} phones the app must be allowed to start in background.",
                cta = "Open settings",
                onClick = {
                    val intent = buildOemAutostartIntent()
                    if (intent != null) {
                        safeLaunch(intent, buildAppDetailsSettings(context))
                    } else {
                        safeLaunch(buildAppDetailsSettings(context), buildAppDetailsSettings(context))
                    }
                    showAutostart.value = false  // dismiss after the user's been pointed at it
                },
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    cta: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFFCD6B4), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFB45309))
            Spacer(Modifier.height(2.dp))
            Text(text = subtitle, fontSize = 11.sp, color = Color(0xFF92400E))
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFEA580C))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(text = cta, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}

// ── Status checks ──────────────────────────────────────────────────────────

private fun canUseFullScreenIntent(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    return nm.canUseFullScreenIntent()
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun needsOemAutostartHint(): Boolean {
    val m = Build.MANUFACTURER.lowercase()
    return m.contains("xiaomi") || m.contains("redmi") || m.contains("oppo") ||
        m.contains("vivo") || m.contains("realme") || m.contains("huawei") ||
        m.contains("honor") || m.contains("oneplus")
}

// ── Settings intents ───────────────────────────────────────────────────────

private fun buildFullScreenIntentSettings(context: Context): Intent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
            .setData(Uri.parse("package:${context.packageName}"))
    } else {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
}

@Suppress("BatteryLife")
private fun buildBatteryOptimizationsRequest(context: Context): Intent {
    return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData(Uri.parse("package:${context.packageName}"))
}

private fun buildAppDetailsSettings(context: Context): Intent {
    return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/**
 * Best-effort deep link to the OEM autostart panel. Returns null when the
 * manufacturer is unknown / doesn't expose an autostart panel — the caller
 * falls back to the generic app-details page.
 */
private fun buildOemAutostartIntent(): Intent? {
    val m = Build.MANUFACTURER.lowercase()
    val intent = Intent().setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return when {
        m.contains("xiaomi") || m.contains("redmi") -> intent.setComponent(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        )
        m.contains("oppo") || m.contains("realme") -> intent.setComponent(
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        )
        m.contains("vivo") -> intent.setComponent(
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        )
        m.contains("huawei") || m.contains("honor") -> intent.setComponent(
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        )
        m.contains("oneplus") -> intent.setComponent(
            ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
        )
        else -> null
    }
}

@Composable
private fun DisposableLifecycleEffect(
    lifecycle: Lifecycle,
    onEvent: (Lifecycle.Event) -> Unit,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> currentOnEvent(event) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}
