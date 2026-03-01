package com.esiri.esiriplus.feature.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

private val BrandTeal = Color(0xFF2A9D8F)
private val WarningOrange = Color(0xFFE76F51)

/**
 * Shown to the doctor when the session is in AWAITING_EXTENSION phase.
 * The doctor can ask the patient to extend or end the consultation.
 */
@Composable
fun DoctorExtensionOverlay(
    extensionRequested: Boolean,
    patientDeclined: Boolean,
    onRequestExtension: () -> Unit,
    onEndConsultation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF8E1))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Session time has ended",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color.Black,
        )
        Spacer(Modifier.height(4.dp))

        val statusText = when {
            patientDeclined -> "Patient declined the extension."
            extensionRequested -> "Waiting for patient response..."
            else -> "Would you like to extend the session?"
        }
        Text(
            text = statusText,
            fontSize = 14.sp,
            color = Color.Black,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!extensionRequested) {
                Button(
                    onClick = onRequestExtension,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Ask Patient", color = Color.White)
                }
            }
            OutlinedButton(
                onClick = onEndConsultation,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("End Consultation", color = Color.Black)
            }
        }
    }
}

/**
 * Shown to the patient when the doctor requests a time extension.
 */
@Composable
fun PatientExtensionPrompt(
    consultationFee: Int,
    durationMinutes: Int,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formattedFee = NumberFormat.getNumberInstance(Locale("en", "TZ"))
        .format(consultationFee)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFF0FFF4))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Extend Session?",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color.Black,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Your doctor would like to extend the session by $durationMinutes minutes for TZS $formattedFee.",
            fontSize = 14.sp,
            color = Color.Black,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Yes, Extend", color = Color.White)
            }
            OutlinedButton(
                onClick = onDecline,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("No, Thanks", color = Color.Black)
            }
        }
    }
}

/**
 * Shown to both parties during the GRACE_PERIOD phase (patient is processing payment).
 */
@Composable
fun GracePeriodBanner(
    remainingSeconds: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(WarningOrange.copy(alpha = 0.1f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Processing extension payment... (%02d:%02d)".format(
                remainingSeconds / 60,
                remainingSeconds % 60,
            ),
            color = WarningOrange,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
    }
}
