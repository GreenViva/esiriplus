package com.esiri.esiriplus.feature.patient.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.stringResource
import com.esiri.esiriplus.core.ui.theme.Geist
import com.esiri.esiriplus.core.ui.theme.Hairline
import com.esiri.esiriplus.core.ui.theme.Ink
import com.esiri.esiriplus.core.ui.theme.InkSoft
import com.esiri.esiriplus.core.ui.theme.InstrumentSerif
import com.esiri.esiriplus.core.ui.theme.Muted
import com.esiri.esiriplus.core.ui.theme.Teal
import com.esiri.esiriplus.core.ui.theme.TealDeep
import com.esiri.esiriplus.core.ui.theme.TealSoft
import com.esiri.esiriplus.core.ui.theme.pressableClick
import com.esiri.esiriplus.feature.patient.R
import kotlinx.coroutines.delay

private val MobileMoneyGreen = Color(0xFF16A34A)
private val ComingSoonGrey = Color(0xFF6B7280)
private val ProviderMpesa = Color(0xFF4CAF50)
private val ProviderAirtel = Color(0xFFE53935)
private val ProviderHalo = Color(0xFF2E7D32)
private val ProviderYas = Color(0xFF43A047)

/**
 * USSD-based mobile-money provider. [stepsArrayRes] points at a string-array
 * resource so the per-step instructions follow the user's locale; quoted
 * menu labels inside each step (e.g. "Lipa kwa M-Pesa") stay in their
 * original Swahili because that's what the carrier shows on the phone.
 */
private data class MobileProvider(
    val name: String,
    val letter: String,
    val color: Color,
    val ussdCode: String,
    @androidx.annotation.ArrayRes val stepsArrayRes: Int,
)

private val mobileProviders = listOf(
    MobileProvider(
        name = "M-Pesa",
        letter = "M",
        color = ProviderMpesa,
        ussdCode = "*150*00#",
        stepsArrayRes = R.array.payment_steps_mpesa,
    ),
    MobileProvider(
        name = "Airtel Money",
        letter = "A",
        color = ProviderAirtel,
        ussdCode = "*150*60#",
        stepsArrayRes = R.array.payment_steps_airtel,
    ),
    MobileProvider(
        name = "HaloPesa",
        letter = "H",
        color = ProviderHalo,
        ussdCode = "*150*88#",
        stepsArrayRes = R.array.payment_steps_halopesa,
    ),
    MobileProvider(
        name = "Yas",
        letter = "Y",
        color = ProviderYas,
        ussdCode = "*150*01#",
        stepsArrayRes = R.array.payment_steps_yas,
    ),
)

private enum class FlowStep { METHOD, PROVIDER, INSTRUCTIONS, BY_NUMBER_ENTRY, BY_NUMBER_WAIT, VERIFYING, SUCCESS }

@Composable
fun PaymentMethodFlow(
    serviceName: String,
    priceAmount: Int,
    patientId: String,
    onPaid: () -> Unit,
    onDismiss: () -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(FlowStep.METHOD) }
    var provider by remember { mutableStateOf<MobileProvider?>(null) }
    var phone by rememberSaveable { mutableStateOf("") }

    when (step) {
        FlowStep.METHOD -> PaymentMethodPickerDialog(
            serviceName = serviceName,
            priceAmount = priceAmount,
            onMobileMoney = { step = FlowStep.PROVIDER },
            onPayByNumber = { step = FlowStep.BY_NUMBER_ENTRY },
            onDismiss = onDismiss,
        )
        FlowStep.PROVIDER -> ProviderPickerDialog(
            onProvider = {
                provider = it
                step = FlowStep.INSTRUCTIONS
            },
            onBack = { step = FlowStep.METHOD },
            onDismiss = onDismiss,
        )
        FlowStep.INSTRUCTIONS -> provider?.let {
            PaymentInstructionsDialog(
                provider = it,
                priceAmount = priceAmount,
                patientId = patientId,
                onChangeProvider = { step = FlowStep.PROVIDER },
                onHavePaid = { step = FlowStep.VERIFYING },
                onDismiss = onDismiss,
            )
        }
        FlowStep.BY_NUMBER_ENTRY -> PayByNumberDialog(
            priceAmount = priceAmount,
            phone = phone,
            onPhoneChange = { phone = it },
            onSend = { step = FlowStep.BY_NUMBER_WAIT },
            onBack = { step = FlowStep.METHOD },
            onDismiss = onDismiss,
        )
        FlowStep.BY_NUMBER_WAIT -> WalletPromptWaitDialog(
            priceAmount = priceAmount,
            onHavePaid = { step = FlowStep.VERIFYING },
            onBack = { step = FlowStep.BY_NUMBER_ENTRY },
            onDismiss = onDismiss,
        )
        FlowStep.VERIFYING -> {
            VerifyingDialog()
            LaunchedEffect(Unit) {
                delay(1800)
                step = FlowStep.SUCCESS
            }
        }
        FlowStep.SUCCESS -> SuccessDialog(onContinue = onPaid)
    }
}

@Composable
private fun FlowDialog(
    onDismiss: () -> Unit,
    fillHeightFraction: Float? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val heightMod = if (fillHeightFraction != null) {
            Modifier.fillMaxHeight(fillHeightFraction)
        } else {
            Modifier
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .then(heightMod),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                content = content,
            )
        }
    }
}

private typealias ColumnScope = androidx.compose.foundation.layout.ColumnScope

@Composable
private fun DialogHeader(
    titlePrefix: String,
    titleAccent: String,
    subtitle: String? = null,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = buildAnnotatedString {
                append(titlePrefix)
                withStyle(
                    SpanStyle(
                        color = TealDeep,
                        fontStyle = FontStyle.Italic,
                        fontFamily = InstrumentSerif,
                    ),
                ) { append(titleAccent) }
            },
            fontFamily = InstrumentSerif,
            fontSize = 24.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 28.sp,
            color = Ink,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.services_close),
                tint = Muted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
    if (subtitle != null) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            fontFamily = Geist,
            fontSize = 12.sp,
            color = Muted,
        )
    }
}

@Composable
private fun PaymentMethodPickerDialog(
    serviceName: String,
    priceAmount: Int,
    onMobileMoney: () -> Unit,
    onPayByNumber: () -> Unit,
    onDismiss: () -> Unit,
) {
    FlowDialog(onDismiss = onDismiss) {
        DialogHeader(
            titlePrefix = stringResource(R.string.services_payment_method_title_prefix),
            titleAccent = stringResource(R.string.services_payment_method_title_accent),
            subtitle = "$serviceName · " + stringResource(
                R.string.services_currency_amount,
                formatTzsFlow(priceAmount),
            ),
            onClose = onDismiss,
        )

        Spacer(Modifier.height(18.dp))

        MethodCard(
            icon = Icons.Outlined.Smartphone,
            iconBg = MobileMoneyGreen,
            title = stringResource(R.string.services_mobile_money),
            subtitle = stringResource(R.string.services_mobile_money_subtitle),
            enabled = true,
            onClick = onMobileMoney,
        )
        Spacer(Modifier.height(10.dp))
        MethodCard(
            icon = Icons.Outlined.PhoneIphone,
            iconBg = TealDeep,
            title = stringResource(R.string.services_pay_by_number),
            subtitle = stringResource(R.string.services_pay_by_number_subtitle),
            enabled = true,
            onClick = onPayByNumber,
        )
        Spacer(Modifier.height(10.dp))
        MethodCard(
            icon = Icons.Outlined.CreditCard,
            iconBg = ComingSoonGrey,
            title = stringResource(R.string.services_card_payment),
            subtitle = stringResource(R.string.services_card_payment_subtitle),
            enabled = false,
            comingSoon = true,
            onClick = {},
        )
        Spacer(Modifier.height(10.dp))
        MethodCard(
            icon = Icons.Outlined.AccountBalance,
            iconBg = ComingSoonGrey,
            title = stringResource(R.string.services_bank_transfer),
            subtitle = stringResource(R.string.services_bank_transfer_subtitle),
            enabled = false,
            comingSoon = true,
            onClick = {},
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.services_secure_payments),
            fontFamily = Geist,
            fontSize = 11.sp,
            color = Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MethodCard(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    subtitle: String,
    enabled: Boolean,
    comingSoon: Boolean = false,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.55f
    val border = if (enabled) Hairline else Hairline
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .pressableClick(enabled = enabled, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconBg.copy(alpha = alpha)),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = Geist,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink.copy(alpha = alpha),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontFamily = Geist,
                fontSize = 11.sp,
                color = Muted.copy(alpha = alpha),
                lineHeight = 14.sp,
            )
        }
        if (comingSoon) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF1F2F4))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.services_coming_soon),
                    fontFamily = Geist,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = ComingSoonGrey,
                )
            }
        } else if (enabled) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Outlined.ArrowForward,
                contentDescription = null,
                tint = TealDeep,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ProviderPickerDialog(
    onProvider: (MobileProvider) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    FlowDialog(onDismiss = onDismiss) {
        DialogHeader(
            titlePrefix = stringResource(R.string.services_provider_title_prefix),
            titleAccent = stringResource(R.string.services_provider_title_accent),
            onClose = onDismiss,
        )
        Spacer(Modifier.height(8.dp))
        BackPill(onClick = onBack)

        Spacer(Modifier.height(18.dp))

        for (rowStart in mobileProviders.indices step 2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProviderCard(
                    provider = mobileProviders[rowStart],
                    onClick = { onProvider(mobileProviders[rowStart]) },
                    modifier = Modifier.weight(1f),
                )
                if (rowStart + 1 < mobileProviders.size) {
                    ProviderCard(
                        provider = mobileProviders[rowStart + 1],
                        onClick = { onProvider(mobileProviders[rowStart + 1]) },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
            if (rowStart + 2 < mobileProviders.size) Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ProviderCard(
    provider: MobileProvider,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, Hairline, RoundedCornerShape(14.dp))
            .pressableClick(onClick = onClick)
            .padding(vertical = 16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(provider.color),
        ) {
            Text(
                text = provider.letter,
                fontFamily = Geist,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = provider.name,
            fontFamily = Geist,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Ink,
        )
    }
}

@Composable
private fun BackPill(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(TealSoft)
            .pressableClick(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.ArrowBack,
            contentDescription = null,
            tint = TealDeep,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.services_back),
            fontFamily = Geist,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TealDeep,
        )
    }
}

@Composable
private fun PaymentInstructionsDialog(
    provider: MobileProvider,
    priceAmount: Int,
    patientId: String,
    onChangeProvider: () -> Unit,
    onHavePaid: () -> Unit,
    onDismiss: () -> Unit,
) {
    val reference = formatReference(patientId)

    FlowDialog(onDismiss = onDismiss, fillHeightFraction = 0.92f) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(provider.color),
                ) {
                    Text(
                        text = provider.letter,
                        fontFamily = Geist,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = provider.name,
                    fontFamily = InstrumentSerif,
                    fontSize = 22.sp,
                    color = Ink,
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.services_close),
                    tint = Muted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        BackPill(onClick = onChangeProvider)
        Spacer(Modifier.height(16.dp))

        // Reference + amount info card.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(TealSoft)
                .padding(14.dp),
        ) {
            InfoLine(
                label = stringResource(R.string.services_reference_number),
                value = reference,
                valueColor = TealDeep,
            )
            Spacer(Modifier.height(8.dp))
            InfoLine(
                label = stringResource(R.string.services_amount_to_pay),
                value = "TSh ${formatTzsFlow(priceAmount)}",
                valueColor = Ink,
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = stringResource(R.string.services_exact_amount_warning, formatTzsFlow(priceAmount)),
            fontFamily = Geist,
            fontSize = 12.sp,
            color = InkSoft,
            lineHeight = 17.sp,
        )

        Spacer(Modifier.height(14.dp))

        Text(
            text = stringResource(R.string.services_follow_steps),
            fontFamily = Geist,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Ink,
        )

        Spacer(Modifier.height(8.dp))

        val steps = androidx.compose.ui.res.stringArrayResource(provider.stepsArrayRes)
        steps.forEachIndexed { index, stepText ->
            StepRow(number = index + 1, text = stepText)
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Change provider button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.dp, Hairline, RoundedCornerShape(12.dp))
                    .pressableClick(onClick = onChangeProvider),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.services_change_provider).replace("\n", " "),
                    fontFamily = Geist,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Ink,
                )
            }
            // I have paid button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(TealDeep, Teal)))
                    .pressableClick(onClick = onHavePaid),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.services_i_have_paid),
                    fontFamily = Geist,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = Geist,
            fontSize = 11.sp,
            color = Muted,
            letterSpacing = 0.6.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            fontFamily = Geist,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
    }
}

@Composable
private fun StepRow(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(TealDeep),
        ) {
            Text(
                text = number.toString(),
                fontFamily = Geist,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            fontFamily = Geist,
            fontSize = 12.sp,
            color = InkSoft,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun PayByNumberDialog(
    priceAmount: Int,
    phone: String,
    onPhoneChange: (String) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    FlowDialog(onDismiss = onDismiss) {
        DialogHeader(
            titlePrefix = stringResource(R.string.payment_phone_headline_prefix),
            titleAccent = stringResource(R.string.payment_phone_headline_accent),
            subtitle = stringResource(
                R.string.services_currency_amount,
                formatTzsFlow(priceAmount),
            ),
            onClose = onDismiss,
        )
        Spacer(Modifier.height(8.dp))
        BackPill(onClick = onBack)

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = onPhoneChange,
            label = {
                Text(
                    stringResource(R.string.services_enter_mobile_number),
                    fontFamily = Geist,
                    fontSize = 13.sp,
                    color = Muted,
                )
            },
            placeholder = {
                Text(
                    "2557XXXXXXXX",
                    fontFamily = Geist,
                    fontSize = 14.sp,
                    color = Muted,
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TealDeep,
                unfocusedBorderColor = Hairline,
                focusedTextColor = Ink,
                unfocusedTextColor = Ink,
                cursorColor = TealDeep,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (phone.length >= 12) {
                        Brush.linearGradient(listOf(TealDeep, Teal))
                    } else {
                        Brush.linearGradient(listOf(Hairline, Hairline))
                    },
                )
                .pressableClick(enabled = phone.length >= 12, onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.services_send_prompt),
                fontFamily = Geist,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun WalletPromptWaitDialog(
    priceAmount: Int,
    onHavePaid: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    FlowDialog(onDismiss = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.services_close),
                    tint = Muted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(TealSoft),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(56.dp),
                    color = TealDeep,
                    strokeWidth = 3.dp,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.services_check_your_phone),
                fontFamily = InstrumentSerif,
                fontSize = 22.sp,
                color = Ink,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.services_approve_on_wallet, formatTzsFlow(priceAmount)),
                fontFamily = Geist,
                fontSize = 13.sp,
                color = InkSoft,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(1.dp, Hairline, RoundedCornerShape(12.dp))
                        .pressableClick(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.services_back),
                        fontFamily = Geist,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Ink,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(TealDeep, Teal)))
                        .pressableClick(onClick = onHavePaid),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.services_i_have_paid),
                        fontFamily = Geist,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun VerifyingDialog() {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = TealDeep,
                    strokeWidth = 3.dp,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.services_verifying_payment),
                    fontFamily = InstrumentSerif,
                    fontSize = 20.sp,
                    color = Ink,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.services_please_wait_confirm),
                    fontFamily = Geist,
                    fontSize = 12.sp,
                    color = Muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SuccessDialog(onContinue: () -> Unit) {
    Dialog(
        onDismissRequest = onContinue,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(TealDeep, Teal))),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.services_payment_successful),
                    fontFamily = InstrumentSerif,
                    fontSize = 22.sp,
                    color = Ink,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.services_payment_confirmed_message),
                    fontFamily = Geist,
                    fontSize = 12.sp,
                    color = InkSoft,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(TealDeep, Teal)))
                        .pressableClick(onClick = onContinue),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.services_continue),
                            fontFamily = Geist,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Outlined.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun formatReference(patientId: String): String {
    val cleaned = patientId.uppercase()
    return if (cleaned.length > 12) {
        "ESP-${cleaned.take(8)}-${cleaned.takeLast(4)}"
    } else if (cleaned.isNotBlank()) {
        "ESP-$cleaned"
    } else {
        "ESP-PATIENT"
    }
}

private fun formatTzsFlow(amount: Int): String =
    java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(amount.toLong())
