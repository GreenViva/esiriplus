package com.esiri.esiriplus.feature.patient.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.core.domain.model.PaymentMethod
import com.esiri.esiriplus.feature.patient.R
import com.esiri.esiriplus.feature.patient.viewmodel.PatientPaymentViewModel
import com.esiri.esiriplus.feature.patient.viewmodel.PaymentStep
import kotlinx.coroutines.delay

private val BrandTeal = Color(0xFF2A9D8F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientPaymentScreen(
    onPaymentComplete: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PatientPaymentViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // Block back press while payment is processing or during the success
    // auto-navigate delay — losing an in-flight payment would force the user
    // to restart and burn idempotency.
    BackHandler(
        enabled = uiState.paymentStatus == PaymentStep.PROCESSING ||
            uiState.paymentStatus == PaymentStep.COMPLETED,
    ) { /* swallow — prevent losing an in-flight payment */ }

    // Auto-navigate on payment completion
    LaunchedEffect(uiState.paymentStatus) {
        if (uiState.paymentStatus == PaymentStep.COMPLETED) {
            delay(1500)
            onPaymentComplete(uiState.consultationId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.payment_title), color = Color.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.payment_back))
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (uiState.paymentStatus) {
                PaymentStep.CONFIRM -> PaymentConfirmContent(
                    amount = uiState.amount,
                    serviceType = uiState.serviceType,
                    isLoading = uiState.isLoading,
                    errorMessage = uiState.errorMessage,
                    selectedMethod = uiState.paymentMethod,
                    onMethodSelected = viewModel::selectMethod,
                    onPay = viewModel::onPayClicked,
                )

                PaymentStep.PHONE_ENTRY -> PaymentPhoneEntryContent(
                    phone = uiState.phoneNumberInput,
                    isLoading = uiState.isLoading,
                    errorMessage = uiState.errorMessage,
                    onPhoneChanged = viewModel::onPhoneInputChanged,
                    onContinue = viewModel::submitPhoneNumber,
                )

                PaymentStep.PROCESSING -> PaymentProcessingContent(
                    amount = uiState.amount,
                    method = uiState.paymentMethod,
                )

                PaymentStep.COMPLETED -> PaymentSuccessContent(
                    amount = uiState.amount,
                )

                PaymentStep.FAILED -> PaymentFailedContent(
                    errorMessage = uiState.errorMessage ?: stringResource(R.string.payment_failed_default),
                    onRetry = viewModel::retryPayment,
                )
            }
        }
    }
}

@Composable
private fun PaymentConfirmContent(
    amount: Int,
    serviceType: String,
    isLoading: Boolean,
    errorMessage: String?,
    selectedMethod: PaymentMethod,
    onMethodSelected: (PaymentMethod) -> Unit,
    onPay: () -> Unit,
) {
    Spacer(Modifier.height(32.dp))

    Text(
        text = stringResource(R.string.payment_consultation_payment),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
    )

    Spacer(Modifier.height(8.dp))

    Text(
        text = serviceType.replaceFirstChar { it.uppercase() }.replace("_", " "),
        style = MaterialTheme.typography.bodyLarge,
        color = Color.Black,
    )

    Spacer(Modifier.height(24.dp))

    Text(
        text = "TZS $amount",
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = BrandTeal,
    )

    Spacer(Modifier.height(12.dp))

    Text(
        text = stringResource(R.string.payment_charge_message),
        style = MaterialTheme.typography.bodyMedium,
        color = Color.Black,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp),
    )

    Spacer(Modifier.height(24.dp))

    Text(
        text = stringResource(R.string.payment_method_header),
        style = MaterialTheme.typography.labelLarge,
        color = Color.Black,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(8.dp))

    MethodChoiceCard(
        icon = Icons.Default.Sms,
        title = stringResource(R.string.payment_method_mpesa_title),
        subtitle = stringResource(R.string.payment_method_mpesa_subtitle),
        selected = selectedMethod == PaymentMethod.MPESA,
        enabled = !isLoading,
        onClick = { onMethodSelected(PaymentMethod.MPESA) },
    )

    Spacer(Modifier.height(8.dp))

    MethodChoiceCard(
        icon = Icons.Default.PhoneIphone,
        title = stringResource(R.string.payment_method_mobile_title),
        subtitle = stringResource(R.string.payment_method_mobile_subtitle),
        selected = selectedMethod == PaymentMethod.MOBILE_NUMBER,
        enabled = !isLoading,
        onClick = { onMethodSelected(PaymentMethod.MOBILE_NUMBER) },
    )

    if (errorMessage != null) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = errorMessage,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    Spacer(Modifier.height(24.dp))

    Button(
        onClick = onPay,
        enabled = !isLoading,
        colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else {
            Text(stringResource(R.string.payment_pay_now), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MethodChoiceCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) BrandTeal else Color(0xFFE5E7EB)
    val bgColor = if (selected) Color(0xFFE8F6F4) else Color.White
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = BrandTeal,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = Color.Black, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PaymentPhoneEntryContent(
    phone: String,
    isLoading: Boolean,
    errorMessage: String?,
    onPhoneChanged: (String) -> Unit,
    onContinue: () -> Unit,
) {
    Spacer(Modifier.height(32.dp))

    Icon(
        imageVector = Icons.Default.Phone,
        contentDescription = null,
        tint = BrandTeal,
        modifier = Modifier.size(56.dp),
    )

    Spacer(Modifier.height(12.dp))

    Text(
        text = stringResource(R.string.payment_phone_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
    )

    Spacer(Modifier.height(6.dp))

    Text(
        text = stringResource(R.string.payment_phone_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = Color.Black,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp),
    )

    Spacer(Modifier.height(24.dp))

    OutlinedTextField(
        value = phone,
        onValueChange = onPhoneChanged,
        label = { Text(stringResource(R.string.payment_phone_label), color = Color.Black) },
        placeholder = { Text("2557XXXXXXXX", color = Color(0xFF6B7280)) },
        singleLine = true,
        enabled = !isLoading,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth(),
    )

    if (errorMessage != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = errorMessage,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    Spacer(Modifier.height(24.dp))

    Button(
        onClick = onContinue,
        enabled = !isLoading && phone.length >= 12,
        colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else {
            Text(stringResource(R.string.payment_phone_continue), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PaymentProcessingContent(amount: Int, method: PaymentMethod) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                color = BrandTeal,
                strokeWidth = 4.dp,
            )

            Text(
                text = stringResource(R.string.payment_processing),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
            )

            val messageRes = when (method) {
                PaymentMethod.MOBILE_NUMBER -> R.string.payment_processing_mobile_message
                PaymentMethod.MPESA -> R.string.payment_processing_message
            }
            Text(
                text = stringResource(messageRes, amount),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}

@Composable
private fun PaymentSuccessContent(amount: Int) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = BrandTeal,
                modifier = Modifier.size(80.dp),
            )

            Text(
                text = stringResource(R.string.payment_successful),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
            )

            Text(
                text = stringResource(R.string.payment_success_message, amount),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PaymentFailedContent(
    errorMessage: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(80.dp),
            )

            Text(
                text = stringResource(R.string.payment_failed),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
            )

            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(0.6f),
            ) {
                Text(stringResource(R.string.payment_try_again), color = BrandTeal)
            }
        }
    }
}
