package com.esiri.esiriplus.feature.auth.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonAddAlt1
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esiri.esiriplus.feature.auth.R
import com.esiri.esiriplus.feature.auth.ui.Geist
import com.esiri.esiriplus.feature.auth.ui.InstrumentSerif
import kotlinx.coroutines.launch

private val Teal         = Color(0xFF2DBE9E)
private val TealDeep     = Color(0xFF1E8E76)
private val TealSoft     = Color(0xFFE8F6F1)
private val TealBg       = Color(0xFFF4FAF7)
private val Ink          = Color(0xFF14201D)
private val Muted        = Color(0xFF6B7C77)
private val Hairline     = Color(0xFFE5EFEA)
private val WarmOrange   = Color(0xFFB86A1A)
private val WarmOrangeBg = Color(0xFFFFF1E0)

@Composable
@Suppress("LongParameterList")
fun RoleSelectionScreen(
    onPatientSelected: () -> Unit,
    onDoctorSelected: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onDoctorRegister: () -> Unit,
    onRecoverPatientId: () -> Unit,
    onHaveMyId: () -> Unit,
    onAgentSelected: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") modifier: Modifier = Modifier,
) {
    var patientSheetOpen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        containerColor = TealBg,
        topBar = { WelcomeTopBar() },
        bottomBar = {
            HelpFooter(
                onPhoneClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:+255663582994")),
                    )
                },
                onEmailClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:info@esiri.africa")),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            HeadlineBlock()
            Spacer(Modifier.height(12.dp))
            HeroCard(onClick = { patientSheetOpen = true })
            Spacer(Modifier.height(14.dp))
            TrustRow()
            SectionDivider(stringResource(R.string.role_not_a_patient_lc))
            AlternateRoleRow(
                onDoctorClick = onDoctorSelected,
                onAgentClick = onAgentSelected,
            )
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
private fun WelcomeTopBar() {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = TealBg),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_stethoscope),
                    contentDescription = "eSIRI Plus logo",
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = buildAnnotatedString {
                        append("e")
                        withStyle(
                            SpanStyle(color = TealDeep, fontWeight = FontWeight.SemiBold),
                        ) { append("SIRI") }
                        append(" Plus")
                    },
                    fontFamily = Geist,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                )
            }
        },
    )
}

@Composable
private fun HeadlineBlock() {
    Text(
        text = buildAnnotatedString {
            append("Need a doctor?\n")
            withStyle(
                SpanStyle(
                    color = TealDeep,
                    fontStyle = FontStyle.Italic,
                    fontFamily = InstrumentSerif,
                ),
            ) { append("We're here.") }
        },
        fontFamily = InstrumentSerif,
        fontSize = 24.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 28.sp,
        color = Ink,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.role_hero_subtitle),
        fontFamily = Geist,
        fontSize = 12.sp,
        color = Muted,
        lineHeight = 16.sp,
    )
}

@Composable
private fun HeroCard(onClick: () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "raindrops")

    val drop1 by infinite.dropPhase(durationMillis = 2400, delayMillis = 0, label = "drop1")
    val drop2 by infinite.dropPhase(durationMillis = 2800, delayMillis = 600, label = "drop2")
    val drop3 by infinite.dropPhase(durationMillis = 2200, delayMillis = 1100, label = "drop3")
    val drop4 by infinite.dropPhase(durationMillis = 3000, delayMillis = 300, label = "drop4")
    val drop5 by infinite.dropPhase(durationMillis = 2600, delayMillis = 1500, label = "drop5")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Teal, TealDeep)))
            .clickable(onClick = onClick),
    ) {
        // Rain-on-water: five fixed drop points across the card surface;
        // each spawns a ring that expands outward and fades. Different
        // durations + start offsets keep the ripples from syncing into
        // a single pulse. Drawn behind the content so it stays legible.
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRainRipple(xRatio = 0.20f, yRatio = 0.30f, phase = drop1)
            drawRainRipple(xRatio = 0.75f, yRatio = 0.25f, phase = drop2)
            drawRainRipple(xRatio = 0.40f, yRatio = 0.65f, phase = drop3)
            drawRainRipple(xRatio = 0.85f, yRatio = 0.70f, phase = drop4)
            drawRainRipple(xRatio = 0.15f, yRatio = 0.85f, phase = drop5)
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.White.copy(alpha = 0.18f)),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.role_hero_card_title),
                fontFamily = InstrumentSerif,
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal,
                fontStyle = FontStyle.Italic,
                color = Color.White,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(R.string.role_hero_card_subtitle),
                fontFamily = Geist,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.9f),
                lineHeight = 14.sp,
            )
            Spacer(Modifier.height(10.dp))

            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(11.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = TealDeep,
                ),
                contentPadding = PaddingValues(vertical = 9.dp),
            ) {
                Text(
                    text = stringResource(R.string.role_continue_as_patient),
                    fontFamily = Geist,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Outlined.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

@Composable
private fun InfiniteTransition.dropPhase(
    durationMillis: Int,
    delayMillis: Int,
    label: String,
): androidx.compose.runtime.State<Float> = animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
        initialStartOffset = StartOffset(delayMillis, StartOffsetType.Delay),
        repeatMode = RepeatMode.Restart,
    ),
    label = label,
)

private fun DrawScope.drawRainRipple(
    xRatio: Float,
    yRatio: Float,
    phase: Float,
) {
    val center = Offset(size.width * xRatio, size.height * yRatio)
    val startRadius = 2.dp.toPx()
    val maxRadius = 36.dp.toPx()
    val radius = startRadius + (maxRadius - startRadius) * phase
    val alpha = (1f - phase).coerceIn(0f, 1f) * 0.55f
    val stroke = (1.6.dp.toPx()) * (1f - phase * 0.6f).coerceAtLeast(0.4f)
    drawCircle(
        color = Color.White.copy(alpha = alpha),
        radius = radius,
        center = center,
        style = Stroke(width = stroke),
    )
    // Inner secondary ring lagging slightly — fakes the dual-pulse you
    // see when a real raindrop hits water.
    val innerPhase = (phase - 0.18f).coerceIn(0f, 1f)
    val innerRadius = startRadius + (maxRadius - startRadius) * innerPhase
    val innerAlpha = (1f - innerPhase).coerceIn(0f, 1f) * 0.3f
    if (innerAlpha > 0f) {
        drawCircle(
            color = Color.White.copy(alpha = innerAlpha),
            radius = innerRadius,
            center = center,
            style = Stroke(width = stroke * 0.7f),
        )
    }
}

@Composable
private fun TrustRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TrustItem(
            icon = Icons.Outlined.Security,
            label = stringResource(R.string.role_trust_private_combo),
            sub = stringResource(R.string.role_trust_private_subtitle),
        )
        TrustItem(
            icon = Icons.Outlined.Schedule,
            label = stringResource(R.string.role_trust_available_combo),
            sub = stringResource(R.string.role_trust_available_subtitle),
        )
        TrustItem(
            icon = Icons.Outlined.Verified,
            label = stringResource(R.string.role_trust_real_combo),
            sub = stringResource(R.string.role_trust_real_subtitle),
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun TrustItem(icon: ImageVector, label: String, sub: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(100.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(TealSoft),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TealDeep,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            fontFamily = Geist,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = Ink,
            textAlign = TextAlign.Center,
        )
        Text(
            text = sub,
            fontFamily = Geist,
            fontSize = 9.sp,
            color = Muted,
            textAlign = TextAlign.Center,
            lineHeight = 12.sp,
        )
    }
}

@Composable
private fun SectionDivider(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = Hairline)
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp),
            fontFamily = Geist,
            fontSize = 11.sp,
            color = Muted,
            fontWeight = FontWeight.Medium,
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = Hairline)
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun AlternateRoleRow(onDoctorClick: () -> Unit, onAgentClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AltCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.LocalHospital,
            iconBg = TealSoft,
            iconTint = TealDeep,
            title = stringResource(R.string.role_im_a_doctor_title),
            subtitle = stringResource(R.string.role_im_a_doctor_subtitle),
            onClick = onDoctorClick,
        )
        AltCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.AttachMoney,
            iconBg = WarmOrangeBg,
            iconTint = WarmOrange,
            title = stringResource(R.string.role_become_agent_title),
            subtitle = stringResource(R.string.role_become_agent_subtitle),
            onClick = onAgentClick,
        )
    }
}

@Composable
private fun AltCard(
    modifier: Modifier,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, Hairline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 9.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(iconBg),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(title, fontFamily = Geist, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Spacer(Modifier.height(1.dp))
        Text(subtitle, fontFamily = Geist, fontSize = 10.sp, color = Muted)
    }
}

@Composable
private fun HelpFooter(
    onPhoneClick: () -> Unit,
    onEmailClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TealBg)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.role_need_help) + " ",
                fontFamily = Geist,
                fontSize = 11.sp,
                color = Muted,
            )
            Text(
                text = stringResource(R.string.role_help_phone),
                fontFamily = Geist,
                fontSize = 11.sp,
                color = TealDeep,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onPhoneClick),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.role_help_email),
            fontFamily = Geist,
            fontSize = 11.sp,
            color = TealDeep,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onEmailClick),
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
                color = Ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.role_patient_sheet_subtitle),
                fontSize = 13.sp,
                color = Muted,
            )

            Spacer(Modifier.height(20.dp))

            PatientGateOption(
                title = stringResource(R.string.role_patient_sheet_new_title),
                subtitle = stringResource(R.string.role_patient_sheet_new_subtitle),
                icon = Icons.Outlined.PersonAddAlt1,
                onClick = { closeThen(onNewPatient) },
            )

            Spacer(Modifier.height(10.dp))

            PatientGateOption(
                title = stringResource(R.string.role_patient_sheet_have_id_title),
                subtitle = stringResource(R.string.role_patient_sheet_have_id_subtitle),
                icon = Icons.Outlined.Key,
                onClick = { closeThen(onHaveMyId) },
            )

            Spacer(Modifier.height(10.dp))

            PatientGateOption(
                title = stringResource(R.string.role_patient_sheet_forgot_id_title),
                subtitle = stringResource(R.string.role_patient_sheet_forgot_id_subtitle),
                icon = Icons.Outlined.Lock,
                onClick = { closeThen(onForgotId) },
            )
        }
    }
}

@Composable
private fun PatientGateOption(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, Hairline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(TealSoft),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TealDeep,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Muted,
            )
        }
        Icon(
            imageVector = Icons.Outlined.ArrowForward,
            contentDescription = null,
            tint = TealDeep,
            modifier = Modifier.size(20.dp),
        )
    }
}
