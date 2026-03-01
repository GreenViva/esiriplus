package com.esiri.esiriplus.feature.patient.screen

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.feature.patient.viewmodel.ExtensionPaymentViewModel
import com.esiri.esiriplus.feature.patient.viewmodel.PaymentStep
import kotlinx.coroutines.delay

private val BrandTeal = Color(0xFF2A9D8F)
private val WarningOrange = Color(0xFFE76F51)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionPaymentScreen(
    onPaymentComplete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExtensionPaymentViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // Auto-navigate back to chat on payment completion
    LaunchedEffect(uiState.paymentStatus) {
        if (uiState.paymentStatus == PaymentStep.COMPLETED) {
            delay(1500)
            onPaymentComplete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Extension Payment", color = Color.Black) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.cancelExtensionPayment()
                        onCancel()
                    }) {
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
                PaymentStep.CONFIRM -> ExtensionConfirmContent(
                    amount = uiState.amount,
                    serviceType = uiState.serviceType,
                    isLoading = uiState.isLoading,
                    errorMessage = uiState.errorMessage,
                    onPay = viewModel::initiatePayment,
                    onCancel = {
                        viewModel.cancelExtensionPayment()
                        onCancel()
                    },
                )

                PaymentStep.PROCESSING -> Box(
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
                            text = "Processing Extension Payment",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                        )
                        Text(
                            text = "Your payment of TZS ${uiState.amount} is being processed...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Black,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                }

                PaymentStep.COMPLETED -> Box(
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
                            text = "Session Extended!",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                        )
                        Text(
                            text = "Returning to consultation...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Black,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                PaymentStep.FAILED -> Box(
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
                            text = uiState.errorMessage ?: "Payment failed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Black,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = viewModel::retryPayment,
                            modifier = Modifier.fillMaxWidth(0.6f),
                        ) {
                            Text("Try Again", color = BrandTeal)
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.cancelExtensionPayment()
                                onCancel()
                            },
                            modifier = Modifier.fillMaxWidth(0.6f),
                        ) {
                            Text("Cancel", color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtensionConfirmContent(
    amount: Int,
    serviceType: String,
    isLoading: Boolean,
    errorMessage: String?,
    onPay: () -> Unit,
    onCancel: () -> Unit,
) {
    Spacer(Modifier.height(32.dp))

    // Extension banner
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(WarningOrange.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text(
            text = "Extension fee equals the original consultation fee.",
            color = WarningOrange,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(Modifier.height(24.dp))

    Text(
        text = "Extend Session",
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

    Text(
        text = "TZS $amount",
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = BrandTeal,
    )

    Spacer(Modifier.height(16.dp))

    Text(
        text = "Pay to extend your consultation session.",
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

    Spacer(Modifier.height(12.dp))

    OutlinedButton(
        onClick = onCancel,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text("Cancel", color = Color.Black)
    }
}
