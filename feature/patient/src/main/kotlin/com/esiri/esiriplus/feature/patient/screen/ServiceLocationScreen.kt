package com.esiri.esiriplus.feature.patient.screen

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.esiri.esiriplus.feature.patient.R

private val BrandTeal = Color(0xFF2A9D8F)
private val MintLight = Color(0xFFE0F2F1)
private val IconBg = Color(0xFFF0FDFA)
private val CardBorder = Color(0xFFE5E7EB)

private val locationPermissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

@Composable
fun ServiceLocationScreen(
    onSelectInsideTanzania: () -> Unit,
    onSelectOutsideTanzania: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var locationGranted by remember {
        mutableStateOf(
            locationPermissions.any { perm ->
                ContextCompat.checkSelfPermission(context, perm) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            },
        )
    }

    // Track whether we've asked at least once (to detect "permanently denied")
    var hasRequestedOnce by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val anyGranted = results.values.any { it }
        locationGranted = anyGranted
        hasRequestedOnce = true
        if (!anyGranted) {
            // If all denied after requesting, treat as permanently denied
            permanentlyDenied = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(Color.White, MintLight))),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Back button (left aligned)
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.Black,
                    )
                }
            }

            if (locationGranted) {
                // ── Granted: show the normal Inside/Outside Tanzania UI ──
                LocationSelectionContent(
                    onSelectInsideTanzania = onSelectInsideTanzania,
                    onSelectOutsideTanzania = onSelectOutsideTanzania,
                )
            } else {
                // ── Not granted: permission prompt ──
                LocationPermissionPrompt(
                    permanentlyDenied = permanentlyDenied,
                    onRequestPermission = {
                        permissionLauncher.launch(locationPermissions)
                    },
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
    }
}

@Composable
private fun LocationPermissionPrompt(
    permanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Spacer(Modifier.height(48.dp))

    // Location icon in circle
    Surface(
        shape = CircleShape,
        color = IconBg,
        modifier = Modifier.size(96.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_location),
                contentDescription = null,
                tint = BrandTeal,
                modifier = Modifier.size(48.dp),
            )
        }
    }

    Spacer(Modifier.height(32.dp))

    Text(
        text = "Location Access Required",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(12.dp))

    Text(
        text = "To provide you with the best experience, we use your location to analyze service availability, match you with nearby qualified doctors, and tailor our healthcare recommendations to your region.",
        fontSize = 14.sp,
        color = Color.Black,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp),
    )

    if (permanentlyDenied) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Location permission was denied. Please enable it in your device settings to continue.",
            fontSize = 13.sp,
            color = Color(0xFFB91C1C),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }

    Spacer(Modifier.height(40.dp))

    Button(
        onClick = if (permanentlyDenied) onOpenSettings else onRequestPermission,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
    ) {
        Text(
            text = if (permanentlyDenied) "Open Settings" else "Enable Location",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}

@Composable
private fun LocationSelectionContent(
    onSelectInsideTanzania: () -> Unit,
    onSelectOutsideTanzania: () -> Unit,
) {
    Spacer(Modifier.height(32.dp))

    // Location pin icon in circle
    Surface(
        shape = CircleShape,
        color = IconBg,
        modifier = Modifier.size(72.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_location),
                contentDescription = null,
                tint = BrandTeal,
                modifier = Modifier.size(36.dp),
            )
        }
    }

    Spacer(Modifier.height(24.dp))

    Text(
        text = "Would you like services from",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
    )

    Spacer(Modifier.height(8.dp))

    Text(
        text = "Select your preferred doctor location",
        fontSize = 14.sp,
        color = Color.Gray,
    )

    Spacer(Modifier.height(32.dp))

    // Inside Tanzania card
    Card(
        onClick = onSelectInsideTanzania,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BrandTeal),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Inside Tanzania",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Local doctors",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Select",
                tint = Color.White,
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    // Outside Tanzania card
    OutlinedCard(
        onClick = onSelectOutsideTanzania,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CardBorder),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Outside Tanzania",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.Black,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "International doctors",
                    fontSize = 14.sp,
                    color = Color.Gray,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Select",
                tint = Color.Gray,
            )
        }
    }
}
