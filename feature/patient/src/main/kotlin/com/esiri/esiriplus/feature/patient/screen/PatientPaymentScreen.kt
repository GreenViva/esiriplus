package com.esiri.esiriplus.feature.patient.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.core.domain.model.PaymentMethod
import com.esiri.esiriplus.core.ui.theme.Geist
import com.esiri.esiriplus.core.ui.theme.Hairline
import com.esiri.esiriplus.core.ui.theme.Ink
import com.esiri.esiriplus.core.ui.theme.InkSoft
import com.esiri.esiriplus.core.ui.theme.InstrumentSerif
import com.esiri.esiriplus.core.ui.theme.Muted
import com.esiri.esiriplus.core.ui.theme.Teal
import com.esiri.esiriplus.core.ui.theme.TealBg
import com.esiri.esiriplus.core.ui.theme.TealDeep
import com.esiri.esiriplus.core.ui.theme.TealSoft
import com.esiri.esiriplus.core.ui.theme.pressableClick
import com.esiri.esiriplus.feature.patient.R
import com.esiri.esiriplus.feature.patient.viewmodel.PatientPaymentViewModel
import com.esiri.esiriplus.feature.patient.viewmodel.PaymentStep
import kotlinx.coroutines.delay

private val InkDeep = Color(0xFF0E2622)
private val ErrorRed = Color(0xFFB5483A)
private val ErrorRedBg = Color(0xFFFBE0DD)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientPaymentScreen(
    onPaymentComplete: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PatientPaymentViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    val backDisabled = uiState.paymentStatus == PaymentStep.PROCESSING ||
        uiState.paymentStatus == PaymentStep.COMPLETED

    // Block back press while payment is in flight or during the success delay —
    // losing an in-flight payment would force the user to restart and burn idempotency.
    BackHandler(enabled = backDisabled) { /* swallow */ }

    LaunchedEffect(uiState.paymentStatus) {
        if (uiState.paymentStatus == PaymentStep.COMPLETED) {
            delay(1500)
            onPaymentComplete(uiState.consultationId)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = TealBg,
        topBar = {
            PaymentTopBar(
                onBack = onBack,
                backEnabled = !backDisabled,
            )
        },
        bottomBar = {
            when (uiState.paymentStatus) {
                PaymentStep.CONFIRM -> PaymentPayBar(
                    label = stringResource(R.string.payment_pay_now),
                    enabled = !uiState.isLoading,
                    isLoading = uiState.isLoading,
                    onClick = viewModel::onPayClicked,
                )
                PaymentStep.PHONE_ENTRY -> PaymentPayBar(
                    label = stringResource(R.string.payment_phone_continue),
                    enabled = !uiState.isLoading && uiState.phoneNumberInput.length >= 12,
                    isLoading = uiState.isLoading,
                    onClick = viewModel::submitPhoneNumber,
                )
                PaymentStep.FAILED -> PaymentPayBar(
                    label = stringResource(R.string.payment_try_again),
                    enabled = true,
                    isLoading = false,
                    onClick = viewModel::retryPayment,
                )
                else -> Unit
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            when (uiState.paymentStatus) {
                PaymentStep.CONFIRM -> PaymentConfirmStep(
                    amount = uiState.amount,
                    serviceType = uiState.serviceType,
                    selectedMethod = uiState.paymentMethod,
                    isLoading = uiState.isLoading,
                    errorMessage = uiState.errorMessage,
                    onMethodSelect = viewModel::selectMethod,
                )
                PaymentStep.PHONE_ENTRY -> PaymentPhoneStep(
                    amount = uiState.amount,
                    phone = uiState.phoneNumberInput,
                    isLoading = uiState.isLoading,
                    errorMessage = uiState.errorMessage,
                    onPhoneChange = viewModel::onPhoneInputChanged,
                )
                PaymentStep.PROCESSING -> PaymentProcessingStep(amount = uiState.amount)
                PaymentStep.COMPLETED -> PaymentDoneStep(amount = uiState.amount)
                PaymentStep.FAILED -> PaymentFailedStep(
                    errorMessage = uiState.errorMessage
                        ?: stringResource(R.string.payment_failed_default),
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentTopBar(onBack: () -> Unit, backEnabled: Boolean) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = TealBg),
        navigationIcon = {
            IconButton(onClick = onBack, enabled = backEnabled) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (backEnabled) Color.White else Color.White.copy(alpha = 0.5f))
                        .border(1.dp, Hairline, CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.payment_back),
                        tint = if (backEnabled) Ink else Muted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
        title = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.ic_stethoscope),
                    contentDescription = "eSIRI Plus",
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape),
                )
            }
        },
    )
}

@Composable
private fun PaymentConfirmStep(
    amount: Int,
    serviceType: String,
    selectedMethod: PaymentMethod,
    isLoading: Boolean,
    errorMessage: String?,
    onMethodSelect: (PaymentMethod) -> Unit,
) {
    Spacer(Modifier.height(4.dp))

    Text(
        text = buildAnnotatedString {
            append(stringResource(R.string.payment_headline_prefix))
            withStyle(
                SpanStyle(
                    color = TealDeep,
                    fontStyle = FontStyle.Italic,
                    fontFamily = InstrumentSerif,
                ),
            ) { append(stringResource(R.string.payment_headline_accent_pay)) }
        },
        fontFamily = InstrumentSerif,
        fontSize = 26.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 30.sp,
        color = Ink,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = serviceTypeLabel(serviceType),
        fontFamily = Geist,
        fontSize = 13.sp,
        color = Muted,
    )

    Spacer(Modifier.height(18.dp))

    AmountCard(amount = amount)

    Spacer(Modifier.height(20.dp))

    Text(
        text = stringResource(R.string.payment_method_header).uppercase(),
        fontFamily = Geist,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = Muted,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
    )

    MethodChoiceCard(
        icon = Icons.Outlined.Sms,
        title = stringResource(R.string.payment_method_mpesa_title),
        subtitle = stringResource(R.string.payment_method_mpesa_subtitle),
        selected = selectedMethod == PaymentMethod.MPESA,
        enabled = !isLoading,
        onClick = { onMethodSelect(PaymentMethod.MPESA) },
    )
    Spacer(Modifier.height(10.dp))
    MethodChoiceCard(
        icon = Icons.Outlined.PhoneIphone,
        title = stringResource(R.string.payment_method_mobile_title),
        subtitle = stringResource(R.string.payment_method_mobile_subtitle),
        selected = selectedMethod == PaymentMethod.MOBILE_NUMBER,
        enabled = !isLoading,
        onClick = { onMethodSelect(PaymentMethod.MOBILE_NUMBER) },
    )

    if (errorMessage != null) {
        Spacer(Modifier.height(14.dp))
        InlineError(message = errorMessage)
    }
}

@Composable
private fun AmountCard(amount: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(InkDeep, TealDeep)))
            .padding(horizontal = 22.dp, vertical = 22.dp),
    ) {
        Column {
            Text(
                text = stringResource(R.string.payment_amount_label),
                fontFamily = Geist,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.7f),
                letterSpacing = 1.6.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "TSh ",
                    fontFamily = Geist,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Text(
                    text = formatTzs(amount),
                    fontFamily = InstrumentSerif,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White,
                    lineHeight = 48.sp,
                )
            }
        }
    }
}

@Composable
private fun MethodChoiceCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val border = if (selected) Teal else Hairline
    val borderWidth = if (selected) 1.5.dp else 1.dp
    val bg = if (selected) TealSoft else Color.White
    val iconBg = if (selected) Color.White else TealSoft
    val iconTint = TealDeep

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(borderWidth, border, RoundedCornerShape(14.dp))
            .pressableClick(enabled = enabled, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconBg),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
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
                color = Ink,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontFamily = Geist,
                fontSize = 11.sp,
                color = Muted,
                lineHeight = 14.sp,
            )
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(TealDeep),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Composable
private fun PaymentPhoneStep(
    amount: Int,
    phone: String,
    isLoading: Boolean,
    errorMessage: String?,
    onPhoneChange: (String) -> Unit,
) {
    Spacer(Modifier.height(4.dp))

    Text(
        text = buildAnnotatedString {
            append(stringResource(R.string.payment_phone_headline_prefix))
            withStyle(
                SpanStyle(
                    color = TealDeep,
                    fontStyle = FontStyle.Italic,
                    fontFamily = InstrumentSerif,
                ),
            ) { append(stringResource(R.string.payment_phone_headline_accent)) }
        },
        fontFamily = InstrumentSerif,
        fontSize = 26.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 30.sp,
        color = Ink,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.payment_phone_hint),
        fontFamily = Geist,
        fontSize = 12.sp,
        color = Muted,
        lineHeight = 17.sp,
    )

    Spacer(Modifier.height(18.dp))

    AmountCard(amount = amount)

    Spacer(Modifier.height(18.dp))

    OutlinedTextField(
        value = phone,
        onValueChange = onPhoneChange,
        label = {
            Text(
                stringResource(R.string.payment_phone_label),
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
        enabled = !isLoading,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TealDeep,
            unfocusedBorderColor = Hairline,
            focusedTextColor = Ink,
            unfocusedTextColor = Ink,
            cursorColor = TealDeep,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            disabledContainerColor = Color.White,
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    )

    if (errorMessage != null) {
        Spacer(Modifier.height(14.dp))
        InlineError(message = errorMessage)
    }
}

@Composable
private fun PaymentProcessingStep(amount: Int) {
    Spacer(Modifier.height(48.dp))
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
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

            Text(
                text = stringResource(R.string.payment_check_phone_title),
                fontFamily = InstrumentSerif,
                fontSize = 24.sp,
                fontWeight = FontWeight.Normal,
                color = Ink,
            )

            Text(
                text = stringResource(R.string.payment_check_phone_subtitle, formatTzs(amount)),
                fontFamily = Geist,
                fontSize = 13.sp,
                color = InkSoft,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun PaymentDoneStep(amount: Int) {
    Spacer(Modifier.height(48.dp))
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(TealDeep, Teal))),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }

            Text(
                text = buildAnnotatedString {
                    append(stringResource(R.string.payment_done_headline_prefix))
                    withStyle(
                        SpanStyle(
                            color = TealDeep,
                            fontStyle = FontStyle.Italic,
                            fontFamily = InstrumentSerif,
                        ),
                    ) { append(stringResource(R.string.payment_done_headline_accent)) }
                },
                fontFamily = InstrumentSerif,
                fontSize = 26.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 30.sp,
                color = Ink,
                textAlign = TextAlign.Center,
            )

            Text(
                text = stringResource(R.string.payment_done_subtitle, formatTzs(amount)),
                fontFamily = Geist,
                fontSize = 13.sp,
                color = InkSoft,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun PaymentFailedStep(errorMessage: String) {
    Spacer(Modifier.height(48.dp))
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(ErrorRedBg),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = ErrorRed,
                    modifier = Modifier.size(40.dp),
                )
            }

            Text(
                text = stringResource(R.string.payment_failed),
                fontFamily = InstrumentSerif,
                fontSize = 26.sp,
                fontWeight = FontWeight.Normal,
                color = Ink,
            )

            Text(
                text = errorMessage,
                fontFamily = Geist,
                fontSize = 13.sp,
                color = InkSoft,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun InlineError(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ErrorRedBg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = ErrorRed,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = message,
            fontFamily = Geist,
            fontSize = 12.sp,
            color = ErrorRed,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun PaymentPayBar(
    label: String,
    enabled: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        color = Color.White,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (enabled) {
                            Brush.linearGradient(listOf(TealDeep, Teal))
                        } else {
                            Brush.linearGradient(listOf(Hairline, Hairline))
                        },
                    )
                    .pressableClick(enabled = enabled && !isLoading, onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = label,
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

private fun serviceTypeLabel(raw: String): String =
    raw.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }

private fun formatTzs(amount: Int): String =
    java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(amount.toLong())
