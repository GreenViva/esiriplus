package com.esiri.esiriplus.feature.patient.screen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.esiri.esiriplus.feature.patient.R

private val GateBrandTeal = Color(0xFF2A9D8F)
private val GateMintLight = Color(0xFFE0F2F1)
private val GateIconBg = Color(0xFFF0FDFA)

private val LOCATION_PERMS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

/**
 * Hard gate that blocks all patient flows until location permission is
 * granted. Wraps the home screen so a returning patient who revoked
 * permission in Settings is forced through the prompt before anything
 * else loads. Re-checks on every ON_RESUME so revocation while backgrounded
 * re-engages the gate immediately.
 *
 * On grant, fires [onGranted] so the caller can kick LocationResolver
 * (we don't want to wait for the next backfill cycle).
 */
@Composable
fun PatientLocationGate(
    onGranted: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    fun checkGranted(): Boolean = LOCATION_PERMS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    var granted by remember { mutableStateOf(checkGranted()) }
    var hasRequestedOnce by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }

    // Re-check on resume — handles user revoking permission via Settings
    // while the app was backgrounded.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowGranted = checkGranted()
                if (nowGranted != granted) granted = nowGranted
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val anyGranted = results.values.any { it }
        if (anyGranted) {
            granted = true
            onGranted()
        } else if (hasRequestedOnce) {
            permanentlyDenied = true
        }
        hasRequestedOnce = true
    }

    if (granted) {
        content()
    } else {
        LocationGateUi(
            permanentlyDenied = permanentlyDenied,
            onRequest = { permissionLauncher.launch(LOCATION_PERMS) },
            onOpenSettings = {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
                context.startActivity(intent)
            },
        )
    }
}

@Composable
private fun LocationGateUi(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(Color.White, GateMintLight))),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Surface(
                shape = CircleShape,
                color = GateIconBg,
                modifier = Modifier.size(96.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_location),
                        contentDescription = null,
                        tint = GateBrandTeal,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.location_access_required),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.location_access_description),
                fontSize = 14.sp,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            if (permanentlyDenied) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.location_permission_denied),
                    fontSize = 13.sp,
                    color = Color(0xFFB91C1C),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            Spacer(Modifier.height(40.dp))

            Button(
                onClick = if (permanentlyDenied) onOpenSettings else onRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GateBrandTeal),
            ) {
                Text(
                    text = if (permanentlyDenied) stringResource(R.string.location_open_settings)
                           else stringResource(R.string.location_enable),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }
    }
}
