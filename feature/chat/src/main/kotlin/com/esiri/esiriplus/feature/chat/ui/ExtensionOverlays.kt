package com.esiri.esiriplus.feature.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esiri.esiriplus.feature.chat.R
import java.text.NumberFormat
import java.util.Locale

private val BrandTeal = Color(0xFF2A9D8F)
private val WarningOrange = Color(0xFFE76F51)

/**
 * Shown to the doctor when the session is in AWAITING_EXTENSION phase.
 * Doctor waits for the patient to decide; can only end after patient declines.
 */
@Composable
fun DoctorExtensionOverlay(
    patientDeclined: Boolean,
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
            text = stringResource(R.string.extension_session_time_ended),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color.Black,
        )
        Spacer(Modifier.height(4.dp))

        if (patientDeclined) {
            Text(
                text = stringResource(R.string.extension_patient_declined),
                fontSize = 14.sp,
                color = Color.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onEndConsultation,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(stringResource(R.string.extension_end_consultation), color = Color.Black)
            }
        } else {
            Text(
                text = stringResource(R.string.extension_waiting_for_patient),
                fontSize = 14.sp,
                color = Color.Black,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Shown to the patient when the session timer expires.
 * Patient decides whether to extend or end the session.
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
            text = stringResource(R.string.extension_times_up),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color.Black,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.extension_prompt_message, durationMinutes, formattedFee),
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
                Text(stringResource(R.string.extension_yes_extend), color = Color.White)
            }
            OutlinedButton(
                onClick = onDecline,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(stringResource(R.string.extension_no_thanks), color = Color.Black)
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
            text = stringResource(
                R.string.extension_processing_payment,
                remainingSeconds / 60,
                remainingSeconds % 60,
            ),
            color = WarningOrange,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
    }
}
