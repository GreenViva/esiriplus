package com.esiri.esiriplus.feature.auth.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esiri.esiriplus.feature.auth.R
import com.esiri.esiriplus.feature.auth.ui.GradientBackground

private val BrandTeal = Color(0xFF2A9D8F)
private val BrandTealDeep = Color(0xFF238B7E)
private val CardBorder = Color(0xFFE5E7EB)

@Composable
@Suppress("LongParameterList")
fun RoleSelectionScreen(
    onPatientSelected: () -> Unit,
    onDoctorSelected: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onDoctorRegister: () -> Unit,
    onRecoverPatientId: () -> Unit,
    onHaveMyId: () -> Unit,
    onAgentSelected: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var patientSheetOpen by rememberSaveable { mutableStateOf(false) }

    GradientBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(12.dp))

            BrandHeader()

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.role_hero_title_1),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.role_hero_title_2),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = BrandTeal,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.role_hero_subtitle),
                fontSize = 13.sp,
                color = Color.Black,
            )

            Spacer(Modifier.height(14.dp))

            HeroPatientCard(onContinue = { patientSheetOpen = true })

            Spacer(Modifier.height(14.dp))

            TrustPillsRow()

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.role_not_a_patient),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NotPatientCard(
                    title = stringResource(R.string.role_im_a_doctor_title),
                    subtitle = stringResource(R.string.role_im_a_doctor_subtitle),
                    iconPainter = painterResource(R.drawable.ic_stethoscope),
                    onClick = onDoctorSelected,
                    modifier = Modifier.weight(1f),
                )
                NotPatientCard(
                    title = stringResource(R.string.role_become_agent_title),
                    subtitle = stringResource(R.string.role_become_agent_subtitle),
                    iconPainter = painterResource(R.drawable.ic_dollar),
                    onClick = onAgentSelected,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.weight(1f))

            NeedHelpLine()

            Spacer(Modifier.height(8.dp))
        }
    }

    if (patientSheetOpen) {
        PatientGateSheet(
            onDismiss = { patientSheetOpen = false },
            onNewPatient = onPatientSelected,
            onHaveMyId = onHaveMyId,
            onForgotId = onRecoverPatientId,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatientGateSheet(
    onDismiss: () -> Unit,
    onNewPatient: () -> Unit,
    onHaveMyId: () -> Unit,
    onForgotId: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun closeThen(action: () -> Unit) {
        scope.launch {
            sheetState.hide()
        }.invokeOnCompletion {
            onDismiss()
            action()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.role_patient_sheet_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.role_patient_sheet_subtitle),
                fontSize = 13.sp,
                color = Color.Black,
            )

            Spacer(Modifier.height(20.dp))

            PatientGateOption(
                title = stringResource(R.string.role_patient_sheet_new_title),
                subtitle = stringResource(R.string.role_patient_sheet_new_subtitle),
                iconPainter = painterResource(R.drawable.ic_person_add),
                onClick = { closeThen(onNewPatient) },
            )

            Spacer(Modifier.height(10.dp))

            PatientGateOption(
                title = stringResource(R.string.role_patient_sheet_have_id_title),
                subtitle = stringResource(R.string.role_patient_sheet_have_id_subtitle),
                iconPainter = painterResource(R.drawable.ic_key),
                onClick = { closeThen(onHaveMyId) },
            )

            Spacer(Modifier.height(10.dp))

            PatientGateOption(
                title = stringResource(R.string.role_patient_sheet_forgot_id_title),
                subtitle = stringResource(R.string.role_patient_sheet_forgot_id_subtitle),
                iconPainter = painterResource(R.drawable.ic_lock),
                onClick = { closeThen(onForgotId) },
            )
        }
    }
}

@Composable
private fun PatientGateOption(
    title: String,
    subtitle: String,
    iconPainter: Painter,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, CardBorder),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(BrandTeal.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = iconPainter,
                    contentDescription = null,
                    tint = BrandTeal,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color.Black,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = BrandTeal,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun BrandHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(BrandTeal.copy(alpha = 0.10f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_stethoscope),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Row {
            Text(
                text = stringResource(R.string.role_brand_primary),
                color = Color.Black,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.role_brand_accent),
                color = BrandTeal,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun HeroPatientCard(onContinue: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(BrandTeal, BrandTealDeep),
                ),
            ),
    ) {
        // Decorative circles top-right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = (-40).dp)
                .size(140.dp)
                .background(Color.White.copy(alpha = 0.08f), CircleShape),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 70.dp, y = 20.dp)
                .size(90.dp)
                .background(Color.White.copy(alpha = 0.06f), CircleShape),
        )

        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_chat_bubble_outline),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.role_hero_card_title),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.role_hero_card_subtitle),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
            )

            Spacer(Modifier.height(8.dp))

            ContinueWithWaves(onContinue = onContinue)
        }
    }
}

@Composable
private fun ContinueWithWaves(onContinue: () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "cta_waves")
    val waveDuration = 2200
    val wave1 by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(waveDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave1",
    )
    val wave2 by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(waveDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(waveDuration / 2, StartOffsetType.Delay),
        ),
        label = "wave2",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val buttonH = 46.dp.toPx()
            val buttonW = size.width
            val buttonY = (size.height - buttonH) / 2f
            val maxExpansion = 12.dp.toPx()
            val baseCorner = 12.dp.toPx()
            val ringStroke = 1.5.dp.toPx()

            listOf(wave1, wave2).forEach { progress ->
                val expansion = progress * maxExpansion
                val alpha = (1f - progress).coerceIn(0f, 1f) * 0.5f
                val corner = baseCorner + expansion
                drawRoundRect(
                    color = Color.White.copy(alpha = alpha),
                    topLeft = Offset(0f, buttonY - expansion),
                    size = Size(buttonW, buttonH + 2f * expansion),
                    cornerRadius = CornerRadius(corner, corner),
                    style = Stroke(width = ringStroke),
                )
            }
        }

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = BrandTeal,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = stringResource(R.string.role_continue_as_patient),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun TrustPillsRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        TrustPill(
            iconPainter = painterResource(R.drawable.ic_shield),
            value = stringResource(R.string.role_trust_private_value),
            label = stringResource(R.string.role_trust_private_label),
        )
        TrustPill(
            iconPainter = painterResource(R.drawable.ic_clock),
            value = stringResource(R.string.role_trust_available_value),
            label = stringResource(R.string.role_trust_available_label),
        )
        TrustPill(
            iconVector = Icons.Default.Check,
            value = stringResource(R.string.role_trust_real_value),
            label = stringResource(R.string.role_trust_real_label),
        )
    }
}

@Composable
private fun TrustPill(
    iconPainter: Painter? = null,
    iconVector: ImageVector? = null,
    value: String,
    label: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(BrandTeal.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (iconPainter != null) {
                Icon(
                    painter = iconPainter,
                    contentDescription = null,
                    tint = BrandTeal,
                    modifier = Modifier.size(18.dp),
                )
            } else if (iconVector != null) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = BrandTeal,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            color = Color.Black,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            color = Color.Black,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun NotPatientCard(
    title: String,
    subtitle: String,
    iconPainter: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(62.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, CardBorder),
        shadowElevation = 1.dp,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(BrandTeal.copy(alpha = 0.10f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = iconPainter,
                    contentDescription = null,
                    tint = BrandTeal,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    color = Color.Black,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun NeedHelpLine() {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.role_need_help),
                color = Color.Black,
                fontSize = 13.sp,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.role_help_phone),
                color = BrandTeal,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    context.startActivity(
                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:+255663582994")),
                    )
                },
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.role_help_email),
            color = BrandTeal,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable {
                context.startActivity(
                    Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:info@esiri.africa")),
                )
            },
        )
    }
}
