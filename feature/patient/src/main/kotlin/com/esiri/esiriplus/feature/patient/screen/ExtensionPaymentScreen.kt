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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoreTime
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.esiri.esiriplus.feature.patient.viewmodel.ExtensionPaymentViewModel
import com.esiri.esiriplus.feature.patient.viewmodel.PaymentStep
import kotlinx.coroutines.delay

private val InkDeepExt = Color(0xFF0E2622)
private val ErrorRedExt = Color(0xFFB5483A)
private val ErrorRedBgExt = Color(0xFFFBE0DD)
private val NoticeAmber = Color(0xFFC26A1F)
private val NoticeAmberBg = Color(0xFFFFE9D6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionPaymentScreen(
    onPaymentComplete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExtensionPaymentViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    BackHandler(enabled = uiState.paymentStatus == PaymentStep.PROCESSING) { /* block */ }

    LaunchedEffect(uiState.paymentStatus) {
        if (uiState.paymentStatus == PaymentStep.COMPLETED) {
            delay(1500)
            onPaymentComplete()
        }
    }

    val cancelAndExit = {
        viewModel.cancelExtensionPayment()
        onCancel()
    }

    val backDisabled = uiState.paymentStatus == PaymentStep.PROCESSING ||
        uiState.paymentStatus == PaymentStep.COMPLETED

    Scaffold(
        modifier = modifier,
        containerColor = TealBg,
        topBar = {
            ExtensionTopBar(
                onBack = { if (!backDisabled) cancelAndExit() },
                backEnabled = !backDisabled,
            )
        },
        bottomBar = {
            when (uiState.paymentStatus) {
                PaymentStep.CONFIRM -> ExtensionPayBar(
                    label = stringResource(R.string.extension_pay_now),
                    enabled = !uiState.isLoading,
                    isLoading = uiState.isLoading,
                    onClick = viewModel::initiatePayment,
                )
                PaymentStep.FAILED -> ExtensionPayBar(
                    label = stringResource(R.string.extension_try_again),
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
                PaymentStep.CONFIRM -> ExtensionConfirmStep(
                    amount = uiState.amount,
                    serviceType = uiState.serviceType,
                    errorMessage = uiState.errorMessage,
                )
                // Extensions always use M-Pesa STK; phone entry is unreachable.
                PaymentStep.PHONE_ENTRY -> Unit
                PaymentStep.PROCESSING -> ExtensionProcessingStep(amount = uiState.amount)
                PaymentStep.COMPLETED -> ExtensionDoneStep()
                PaymentStep.FAILED -> ExtensionFailedStep(
                    errorMessage = uiState.errorMessage
                        ?: stringResource(R.string.extension_payment_failed_default),
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtensionTopBar(onBack: () -> Unit, backEnabled: Boolean) {
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
                        contentDescription = stringResource(R.string.extension_back),
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
private fun ExtensionConfirmStep(
    amount: Int,
    serviceType: String,
    errorMessage: String?,
) {
    Spacer(Modifier.height(4.dp))

    Text(
        text = buildAnnotatedString {
            append(stringResource(R.string.extension_headline_prefix))
            withStyle(
                SpanStyle(
                    color = TealDeep,
                    fontStyle = FontStyle.Italic,
                    fontFamily = InstrumentSerif,
                ),
            ) { append(stringResource(R.string.extension_headline_accent)) }
        },
        fontFamily = InstrumentSerif,
        fontSize = 26.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 30.sp,
        color = Ink,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = serviceTypeLabelExt(serviceType),
        fontFamily = Geist,
        fontSize = 13.sp,
        color = Muted,
    )

    Spacer(Modifier.height(18.dp))

    ExtensionAmountCard(amount = amount)

    Spacer(Modifier.height(14.dp))

    NoticeBanner(message = stringResource(R.string.extension_fee_note))

    Spacer(Modifier.height(14.dp))

    Text(
        text = stringResource(R.string.extension_pay_description),
        fontFamily = Geist,
        fontSize = 13.sp,
        color = InkSoft,
        lineHeight = 18.sp,
    )

    if (errorMessage != null) {
        Spacer(Modifier.height(14.dp))
        ExtensionInlineError(message = errorMessage)
    }
}

@Composable
private fun ExtensionAmountCard(amount: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(InkDeepExt, TealDeep)))
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
                    text = formatTzsExt(amount),
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
private fun NoticeBanner(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NoticeAmberBg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.White),
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreTime,
                contentDescription = null,
                tint = NoticeAmber,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = message,
            fontFamily = Geist,
            fontSize = 12.sp,
            color = NoticeAmber,
            fontWeight = FontWeight.Medium,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun ExtensionProcessingStep(amount: Int) {
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
                text = stringResource(R.string.payment_check_phone_subtitle, formatTzsExt(amount)),
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
private fun ExtensionDoneStep() {
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
                    append(stringResource(R.string.extension_done_headline_prefix))
                    withStyle(
                        SpanStyle(
                            color = TealDeep,
                            fontStyle = FontStyle.Italic,
                            fontFamily = InstrumentSerif,
                        ),
                    ) { append(stringResource(R.string.extension_done_headline_accent)) }
                },
                fontFamily = InstrumentSerif,
                fontSize = 26.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 30.sp,
                color = Ink,
                textAlign = TextAlign.Center,
            )

            Text(
                text = stringResource(R.string.extension_returning),
                fontFamily = Geist,
                fontSize = 13.sp,
                color = InkSoft,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
            )
        }
    }
}

@Composable
private fun ExtensionFailedStep(errorMessage: String) {
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
                    .background(ErrorRedBgExt),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = ErrorRedExt,
                    modifier = Modifier.size(40.dp),
                )
            }

            Text(
                text = stringResource(R.string.extension_payment_failed),
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
private fun ExtensionInlineError(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ErrorRedBgExt)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = ErrorRedExt,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = message,
            fontFamily = Geist,
            fontSize = 12.sp,
            color = ErrorRedExt,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun ExtensionPayBar(
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

private fun serviceTypeLabelExt(raw: String): String =
    raw.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }

private fun formatTzsExt(amount: Int): String =
    java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(amount.toLong())
