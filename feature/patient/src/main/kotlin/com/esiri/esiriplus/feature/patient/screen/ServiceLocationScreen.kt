package com.esiri.esiriplus.feature.patient.screen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.esiri.esiriplus.feature.patient.R
import com.esiri.esiriplus.feature.patient.model.TZ_DISTRICTS
import com.esiri.esiriplus.feature.patient.model.TzDistrict
import androidx.compose.runtime.rememberCoroutineScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

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
    onSelectInsideTanzania: (district: String?, ward: String?) -> Unit,
    onSelectOutsideTanzania: () -> Unit,
    onBack: () -> Unit,
    tier: String = "ECONOMY",
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
    var hasRequestedOnce by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    var showInternationalDialog by remember { mutableStateOf(false) }
    var showDistrictPicker by remember { mutableStateOf(false) }
    var selectedDistrict by rememberSaveable { mutableStateOf<String?>(null) }
    var isDetecting by remember { mutableStateOf(false) }
    var detectionFailedReason by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    fun detectDistrictAndProceed() {
        if (isDetecting) return
        isDetecting = true
        detectionFailedReason = null
        scope.launch {
            val matched = detectDistrictFromGps(context)
            isDetecting = false
            if (matched != null) {
                selectedDistrict = matched
                onSelectInsideTanzania(matched, null)
            } else {
                // Couldn't resolve — fall back to manual picker
                detectionFailedReason = "We couldn't detect your district. Please select it."
                showDistrictPicker = true
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val anyGranted = results.values.any { it }
        locationGranted = anyGranted
        if (!anyGranted && hasRequestedOnce) {
            // Only treat as permanently denied after the second denial
            permanentlyDenied = true
        }
        hasRequestedOnce = true
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
                        contentDescription = stringResource(R.string.location_back),
                        tint = Color.Black,
                    )
                }
            }

            if (locationGranted) {
                // ── Granted: show the normal Inside/Outside Tanzania UI ──
                LocationSelectionContent(
                    onSelectInsideTanzania = { detectDistrictAndProceed() },
                    onSelectOutsideTanzania = { showInternationalDialog = true },
                    isDetecting = isDetecting,
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

    if (showInternationalDialog) {
        AlertDialog(
            onDismissRequest = { showInternationalDialog = false },
            title = { Text(stringResource(R.string.location_not_available_title), color = Color.Black) },
            text = {
                Text(
                    stringResource(R.string.location_not_available_message),
                    color = Color.Black,
                )
            },
            confirmButton = {
                TextButton(onClick = { showInternationalDialog = false }) {
                    Text(stringResource(R.string.location_ok), color = BrandTeal)
                }
            },
        )
    }

    if (showDistrictPicker) {
        DistrictPickerDialog(
            districts = TZ_DISTRICTS,
            headerMessage = detectionFailedReason,
            onDismiss = { showDistrictPicker = false },
            onSelect = { district ->
                selectedDistrict = district.district
                showDistrictPicker = false
                // For v1 we pass the district only; ward stays null. The edge
                // function can still match ward-scoped offers if the patient's
                // chosen ward aligns later (future enhancement).
                onSelectInsideTanzania(district.district, null)
            },
            onSkip = {
                showDistrictPicker = false
                onSelectInsideTanzania(null, null)
            },
        )
    }
}

@Composable
private fun DistrictPickerDialog(
    districts: List<TzDistrict>,
    onDismiss: () -> Unit,
    onSelect: (TzDistrict) -> Unit,
    onSkip: () -> Unit,
    headerMessage: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Where are you right now?",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    text = headerMessage
                        ?: "We use this to find nearby doctors and apply any offers in your area.",
                    color = Color.Black,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                ) {
                    items(districts) { d ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(d) }
                                .padding(vertical = 12.dp),
                        ) {
                            Text(
                                text = d.district,
                                color = Color.Black,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                            )
                            Text(
                                text = d.region,
                                color = Color.Black,
                                fontSize = 12.sp,
                            )
                        }
                        HorizontalDivider(color = CardBorder)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSkip) {
                Text("Skip", color = BrandTeal)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.location_ok), color = Color.Gray)
            }
        },
    )
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
        onClick = if (permanentlyDenied) onOpenSettings else onRequestPermission,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
    ) {
        Text(
            text = if (permanentlyDenied) stringResource(R.string.location_open_settings) else stringResource(R.string.location_enable),
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
    isDetecting: Boolean = false,
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
        text = stringResource(R.string.location_services_from),
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
    )

    Spacer(Modifier.height(8.dp))

    Text(
        text = stringResource(R.string.location_select_preferred),
        fontSize = 14.sp,
        color = Color.Gray,
    )

    Spacer(Modifier.height(32.dp))

    // Inside Tanzania card
    Card(
        onClick = { if (!isDetecting) onSelectInsideTanzania() },
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
                    text = stringResource(R.string.location_inside_tanzania),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isDetecting) "Detecting your district..."
                           else stringResource(R.string.location_local_doctors),
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
            if (isDetecting) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.location_content_desc_select),
                    tint = Color.White,
                )
            }
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
                    text = stringResource(R.string.location_outside_tanzania),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.Black,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.location_international_doctors),
                    fontSize = 14.sp,
                    color = Color.Gray,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.location_content_desc_select),
                tint = Color.Gray,
            )
        }
    }
}

/**
 * Fetch the device's current coordinates, reverse-geocode them to a district name,
 * and match against [TZ_DISTRICTS]. Returns the canonical district name on match,
 * or null on timeout / no match / failure — caller should fall back to manual picker.
 */
@Suppress("MissingPermission")
private suspend fun detectDistrictFromGps(context: Context): String? {
    // 1. Get coordinates via Play Services
    val location: Location = try {
        val client = LocationServices.getFusedLocationProviderClient(context)
        kotlinx.coroutines.withTimeoutOrNull(8_000) {
            kotlinx.coroutines.suspendCancellableCoroutine<Location?> { cont ->
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener { loc -> if (cont.isActive) cont.resumeWith(Result.success(loc)) }
                    .addOnFailureListener { if (cont.isActive) cont.resumeWith(Result.success(null)) }
            }
        }
    } catch (e: Exception) {
        Log.w("ServiceLocation", "FusedLocation failed", e)
        null
    } ?: return null

    // 2. Reverse-geocode off the main thread
    return withContext(Dispatchers.IO) {
        try {
            @Suppress("DEPRECATION")
            val addresses = Geocoder(context, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
            val addr = addresses?.firstOrNull() ?: return@withContext null

            // Build candidate district names from the address fields. Tanzanian
            // addresses from Google's geocoder typically surface district as
            // `locality` (e.g. "Ubungo") or `subAdminArea`.
            val candidates = listOfNotNull(
                addr.subAdminArea,
                addr.locality,
            ).map { it.trim() }

            // Match case-insensitively against our known list.
            candidates.firstNotNullOfOrNull { candidate ->
                TZ_DISTRICTS.firstOrNull { it.district.equals(candidate, ignoreCase = true) }?.district
            }.also {
                Log.d("ServiceLocation", "Geocoded candidates=$candidates matched=$it")
            }
        } catch (e: Exception) {
            Log.w("ServiceLocation", "Geocoder failed", e)
            null
        }
    }
}
