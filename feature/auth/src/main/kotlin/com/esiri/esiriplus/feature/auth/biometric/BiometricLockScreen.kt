package com.esiri.esiriplus.feature.auth.biometric

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.esiri.esiriplus.feature.auth.R

private val BrandTeal = Color(0xFF2A9D8F)
private val CreamBackground = Color(0xFFF5F0EB)

@Composable
fun BiometricLockScreen(
    biometricAuthManager: BiometricAuthManager,
    doctorName: String?,
    onUnlocked: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preferredType = biometricAuthManager.getPreferredBiometricType()
    val biometricIconRes = when (preferredType) {
        BiometricType.FACE -> R.drawable.ic_face_unlock
        else -> R.drawable.ic_fingerprint
    }

    // Auto-trigger biometric prompt on first composition
    LaunchedEffect(Unit) {
        val activity = context as? FragmentActivity ?: return@LaunchedEffect
        biometricAuthManager.prompt(
            activity = activity,
            title = "Verify Identity",
            subtitle = "Unlock eSIRI+",
        ) { result ->
            if (result is BiometricAuthManager.BiometricResult.Success) {
                onUnlocked()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CreamBackground)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "eSIRI+",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = BrandTeal,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .size(96.dp)
                .background(BrandTeal.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(biometricIconRes),
                contentDescription = "Biometric lock",
                tint = BrandTeal,
                modifier = Modifier.size(52.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (!doctorName.isNullOrBlank()) {
                "Welcome back, Dr. $doctorName"
            } else {
                "Welcome back"
            },
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Use fingerprint or face unlock to continue",
            fontSize = 14.sp,
            color = Color.Black,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                val activity = context as? FragmentActivity ?: return@Button
                biometricAuthManager.prompt(
                    activity = activity,
                    title = "Verify Identity",
                    subtitle = "Unlock eSIRI+",
                ) { result ->
                    if (result is BiometricAuthManager.BiometricResult.Success) {
                        onUnlocked()
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
                painter = painterResource(biometricIconRes),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "Unlock",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onSignOut) {
            Text(
                text = "Sign out",
                fontSize = 14.sp,
                color = Color.Black,
            )
        }
    }
}
