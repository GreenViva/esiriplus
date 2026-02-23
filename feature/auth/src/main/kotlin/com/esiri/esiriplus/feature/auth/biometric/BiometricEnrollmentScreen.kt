package com.esiri.esiriplus.feature.auth.biometric

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.esiri.esiriplus.feature.auth.R

private val BrandTeal = Color(0xFF2A9D8F)

@Composable
fun BiometricEnrollmentScreen(
    biometricAvailable: Boolean,
    biometricEnrolled: Boolean,
    deviceAlreadyBound: Boolean,
    biometricAuthManager: BiometricAuthManager,
    onEnrollmentSuccess: () -> Unit,
    onRefreshBiometricState: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPrompted by remember { mutableStateOf(false) }
    var promptError by remember { mutableStateOf<String?>(null) }

    // Re-check biometric state on every resume (e.g., returning from settings)
    var resumeCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            onRefreshBiometricState()
            resumeCount++
        }
    }

    val hasHardware = biometricAuthManager.hasHardware()
    val hasBiometrics = biometricAuthManager.hasEnrolledBiometrics()
    val availableTypes = biometricAuthManager.getAvailableTypes()
    var selectedType by remember {
        mutableStateOf(
            biometricAuthManager.getPreferredBiometricType()
                ?: availableTypes.firstOrNull()
        )
    }

    // Auto-trigger prompt when biometrics are enrolled and not yet verified
    LaunchedEffect(hasBiometrics, resumeCount) {
        if (hasBiometrics && !biometricEnrolled && !hasPrompted && selectedType != null) {
            val activity = context as? FragmentActivity ?: return@LaunchedEffect
            hasPrompted = true
            biometricAuthManager.prompt(
                activity = activity,
                title = "Verify Identity",
                subtitle = "Confirm your identity to complete enrollment",
            ) { result ->
                when (result) {
                    is BiometricAuthManager.BiometricResult.Success -> {
                        promptError = null
                        selectedType?.let { biometricAuthManager.setPreferredBiometricType(it) }
                        onEnrollmentSuccess()
                    }
                    is BiometricAuthManager.BiometricResult.Cancelled -> {
                        hasPrompted = false
                        promptError = null
                    }
                    is BiometricAuthManager.BiometricResult.Error -> {
                        hasPrompted = false
                        promptError = result.message
                    }
                    is BiometricAuthManager.BiometricResult.NotAvailable -> {
                        hasPrompted = false
                        promptError = "Biometric authentication is not available."
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Icon — show based on selected type
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(BrandTeal.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(
                    when (selectedType) {
                        BiometricType.FACE -> R.drawable.ic_face_unlock
                        else -> R.drawable.ic_fingerprint
                    }
                ),
                contentDescription = "Biometric security",
                tint = if (biometricEnrolled) BrandTeal else Color.Black,
                modifier = Modifier.size(44.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Set Up Biometric Security",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "For the security of your patients and practice, " +
                "biometric authentication is required for all doctors.",
            fontSize = 14.sp,
            color = Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        when {
            deviceAlreadyBound -> {
                InfoBox(
                    text = "This device is already registered to another doctor. " +
                        "Each device can only be linked to one doctor account. " +
                        "Please use a different device or contact support.",
                    backgroundColor = Color(0xFFFEE2E2),
                    textColor = Color(0xFFDC2626),
                )
            }

            biometricEnrolled -> {
                InfoBox(
                    text = "Biometric security is set up. " +
                        "You can now complete your registration.",
                    backgroundColor = Color(0xFFD1FAE5),
                    textColor = Color(0xFF065F46),
                )
            }

            !hasHardware -> {
                InfoBox(
                    text = "This device does not support biometric authentication. " +
                        "Please use a device with fingerprint or face unlock " +
                        "to register as a doctor.",
                    backgroundColor = Color(0xFFFEE2E2),
                    textColor = Color(0xFFDC2626),
                )
            }

            !hasBiometrics -> {
                // Hardware exists but nothing enrolled — hint to enable
                InfoBox(
                    text = "No biometric is set up on this device yet. " +
                        "Please go to your device Settings and enable " +
                        "fingerprint or face unlock, then come back here. " +
                        "The app will automatically detect it.",
                    backgroundColor = Color(0xFFFEF3C7),
                    textColor = Color(0xFF92400E),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Tip: If your phone supports both fingerprint and face unlock, " +
                        "you can enable both for easier access.",
                    fontSize = 12.sp,
                    color = Color.Black.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandTeal,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        text = "Open Device Settings",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            else -> {
                // Biometric type selection when multiple types available
                if (availableTypes.size > 1) {
                    Text(
                        text = "Choose your preferred unlock method:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    ) {
                        availableTypes.forEach { type ->
                            BiometricTypeOption(
                                type = type,
                                isSelected = selectedType == type,
                                onClick = { selectedType = type },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Biometrics enrolled, waiting for verification
                if (promptError != null) {
                    InfoBox(
                        text = "Verification failed: $promptError. Please try again.",
                        backgroundColor = Color(0xFFFEE2E2),
                        textColor = Color(0xFFDC2626),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                } else {
                    InfoBox(
                        text = "Biometric detected. Please verify your identity to continue.",
                        backgroundColor = Color(0xFFDBEAFE),
                        textColor = Color(0xFF1E40AF),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Button(
                    onClick = {
                        val activity = context as? FragmentActivity ?: return@Button
                        hasPrompted = true
                        biometricAuthManager.prompt(
                            activity = activity,
                            title = "Verify Identity",
                            subtitle = "Confirm your identity to complete enrollment",
                        ) { result ->
                            when (result) {
                                is BiometricAuthManager.BiometricResult.Success -> {
                                    promptError = null
                                    selectedType?.let {
                                        biometricAuthManager.setPreferredBiometricType(it)
                                    }
                                    onEnrollmentSuccess()
                                }
                                is BiometricAuthManager.BiometricResult.Cancelled -> {
                                    hasPrompted = false
                                    promptError = null
                                }
                                is BiometricAuthManager.BiometricResult.Error -> {
                                    hasPrompted = false
                                    promptError = result.message
                                }
                                is BiometricAuthManager.BiometricResult.NotAvailable -> {
                                    hasPrompted = false
                                    promptError = "Biometric authentication is not available."
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandTeal,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        painter = painterResource(
                            when (selectedType) {
                                BiometricType.FACE -> R.drawable.ic_face_unlock
                                else -> R.drawable.ic_fingerprint
                            }
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "Verify Identity",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun BiometricTypeOption(
    type: BiometricType,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val iconRes = when (type) {
        BiometricType.FINGERPRINT -> R.drawable.ic_fingerprint
        BiometricType.FACE -> R.drawable.ic_face_unlock
    }
    val label = when (type) {
        BiometricType.FINGERPRINT -> "Fingerprint"
        BiometricType.FACE -> "Face Unlock"
    }

    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) BrandTeal else Color.Black.copy(alpha = 0.2f),
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) BrandTeal.copy(alpha = 0.08f) else Color.White,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = if (isSelected) BrandTeal else Color.Black,
                modifier = Modifier.size(40.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) BrandTeal else Color.Black,
            )
        }
    }
}

@Composable
private fun InfoBox(
    text: String,
    backgroundColor: Color,
    textColor: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
