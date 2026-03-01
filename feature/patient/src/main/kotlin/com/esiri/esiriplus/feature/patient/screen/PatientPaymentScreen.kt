package com.esiri.esiriplus.feature.patient.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
                title = { Text("Payment", color = Color.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                    onPay = viewModel::initiatePayment,
                )

                PaymentStep.PROCESSING -> PaymentProcessingContent(
                    amount = uiState.amount,
                )

                PaymentStep.COMPLETED -> PaymentSuccessContent(
                    amount = uiState.amount,
                )

                PaymentStep.FAILED -> PaymentFailedContent(
                    errorMessage = uiState.errorMessage ?: "Payment failed",
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
    onPay: () -> Unit,
) {
    Spacer(Modifier.height(48.dp))

    Text(
        text = "Consultation Payment",
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

    Spacer(Modifier.height(32.dp))

    // Amount display
    Text(
        text = "TZS $amount",
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = BrandTeal,
    )

    Spacer(Modifier.height(16.dp))

    Text(
        text = "You will be charged for this consultation session.",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.Black,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp),
    )

    if (errorMessage != null) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = errorMessage,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    Spacer(Modifier.height(32.dp))

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
            Text("Pay Now", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PaymentProcessingContent(amount: Int) {
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
                text = "Processing Payment",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
            )

            Text(
                text = "Your payment of TZS $amount is being processed. Please wait...",
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
                text = "Payment Successful",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
            )

            Text(
                text = "TZS $amount paid successfully.\nConnecting you to your doctor...",
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
                text = "Payment Failed",
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
                Text("Try Again", color = BrandTeal)
            }
        }
    }
}
