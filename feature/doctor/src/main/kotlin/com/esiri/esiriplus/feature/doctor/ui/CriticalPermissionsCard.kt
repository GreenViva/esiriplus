package com.esiri.esiriplus.feature.doctor.ui

import android.app.NotificationManager
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
 * Banner shown to nurses (and any user whose role needs lock-screen call rings)
 * when one of the two ring-critical permissions isn't granted:
 *
 *   1. Full-screen intent (Android 14+) — required for setFullScreenIntent
 *      to wake the lock screen during an incoming reminder ring.
 *   2. Battery optimization exemption — required so the FCM service and
 *      foreground IncomingCallService aren't killed in background.
 *
 * Both permissions need user action through Settings; the card deep-links to
 * the exact panel for each. The card auto-hides once both are granted, and
 * re-checks on every resume so the granted state shows up immediately after
 * the user toggles the system switch.
 */
@Composable
fun CriticalPermissionsCard(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val fullScreenGranted = remember { mutableStateOf(canUseFullScreenIntent(context)) }
    val batteryGranted = remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

    // Re-check whenever the activity resumes (the user just came back from
    // Settings → state may have changed).
    DisposableLifecycleEffect(lifecycleOwner.lifecycle) { event ->
        if (event == Lifecycle.Event.ON_RESUME) {
            fullScreenGranted.value = canUseFullScreenIntent(context)
            batteryGranted.value = isIgnoringBatteryOptimizations(context)
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Refresh on return.
        fullScreenGranted.value = canUseFullScreenIntent(context)
        batteryGranted.value = isIgnoringBatteryOptimizations(context)
    }

    /**
     * Try the targeted Settings intent first; if the OEM build doesn't
     * resolve it (Samsung One UI sometimes lacks the granular panel), fall
     * back to the app's general details page so the user has somewhere to
     * land instead of a no-op tap.
     */
    fun safeLaunch(primary: Intent, fallback: Intent) {
        val activities = context.packageManager.queryIntentActivities(primary, 0)
        try {
            if (activities.isNotEmpty()) {
                launcher.launch(primary)
            } else {
                launcher.launch(fallback)
            }
        } catch (_: Exception) {
            try { launcher.launch(fallback) } catch (_: Exception) { /* give up silently */ }
        }
    }

    val needsFullScreen = !fullScreenGranted.value
    val needsBattery = !batteryGranted.value
    if (!needsFullScreen && !needsBattery) return  // all good — render nothing

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
            ) { androidx.compose.material3.Text(text = "!", color = Color.White, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(10.dp))
            androidx.compose.material3.Text(
                text = "Set up reminder calls",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFB45309),
            )
        }
        Spacer(Modifier.height(4.dp))
        androidx.compose.material3.Text(
            text = "Two settings are missing. Without them the phone won't ring or wake the screen for medication-reminder calls.",
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
                subtitle = "Required for reminder rings to arrive when the app is in background.",
                cta = "Open settings",
                onClick = {
                    safeLaunch(
                        primary = buildBatteryOptimizationsRequest(context),
                        fallback = buildAppDetailsSettings(context),
                    )
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
            androidx.compose.material3.Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFB45309),
            )
            Spacer(Modifier.height(2.dp))
            androidx.compose.material3.Text(
                text = subtitle,
                fontSize = 11.sp,
                color = Color(0xFF92400E),
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFEA580C))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            androidx.compose.material3.Text(
                text = cta,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}

// ── Status checks ──────────────────────────────────────────────────────────

private fun canUseFullScreenIntent(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        // < Android 14: permission is granted at install time.
        return true
    }
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    return nm.canUseFullScreenIntent()
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

// ── Settings intents ───────────────────────────────────────────────────────

private fun buildFullScreenIntentSettings(context: Context): Intent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
            .setData(Uri.parse("package:${context.packageName}"))
    } else {
        // Fall back to app-level notification settings.
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
}

@Suppress("BatteryLife")  // legitimate use-case for a clinical reminder app
private fun buildBatteryOptimizationsRequest(context: Context): Intent {
    return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData(Uri.parse("package:${context.packageName}"))
}

private fun buildAppDetailsSettings(context: Context): Intent {
    return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

// ── Lifecycle helper ───────────────────────────────────────────────────────

@Composable
private fun DisposableLifecycleEffect(
    lifecycle: Lifecycle,
    onEvent: (Lifecycle.Event) -> Unit,
) {
    val currentOnEvent by androidx.compose.runtime.rememberUpdatedState(onEvent)
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> currentOnEvent(event) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}
