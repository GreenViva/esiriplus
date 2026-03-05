package com.esiri.esiriplus.feature.chat.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.esiri.esiriplus.core.network.dto.CallRechargePackage
import com.esiri.esiriplus.feature.chat.R

private val BrandTeal = Color(0xFF2A9D8F)

@Composable
fun CallRechargeModal(
    consultationId: String,
    onDismiss: () -> Unit,
    onRechargeSuccess: (minutes: Int) -> Unit,
    onSubmitRecharge: ((minutes: Int, phoneNumber: String, onResult: (Boolean) -> Unit) -> Unit)? = null,
) {
    var selectedPackage by remember { mutableIntStateOf(0) }
    var phoneNumber by remember { mutableStateOf("255") }
    var isSubmitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val packages = CallRechargePackage.ALL

    val invalidPhoneMessage = stringResource(R.string.recharge_invalid_phone)
    val paymentFailedMessage = stringResource(R.string.recharge_payment_failed)

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Text(
                text = stringResource(R.string.recharge_add_call_time),
                color = Color.Black,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.recharge_select_package),
                    color = Color.Black,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))

                packages.forEachIndexed { index, pkg ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { selectedPackage = index },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            width = if (selectedPackage == index) 2.dp else 1.dp,
                            color = if (selectedPackage == index) BrandTeal else Color.Gray.copy(alpha = 0.3f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedPackage == index,
                                onClick = { selectedPackage = index },
                                colors = RadioButtonDefaults.colors(selectedColor = BrandTeal),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = pkg.label,
                                color = Color.Black,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { value ->
                        val filtered = value.filter { it.isDigit() }.take(12)
                        phoneNumber = filtered
                        error = null
                    },
                    label = { Text(stringResource(R.string.recharge_phone_label), color = Color.Black) },
                    placeholder = { Text(stringResource(R.string.recharge_phone_placeholder)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error!!,
                        color = Color(0xFFEF4444),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pkg = packages[selectedPackage]
                    if (!phoneNumber.matches(Regex("^2556\\d{8}$|^2557\\d{8}$"))) {
                        error = invalidPhoneMessage
                        return@Button
                    }
                    if (onSubmitRecharge != null) {
                        isSubmitting = true
                        error = null
                        onSubmitRecharge(pkg.minutes, phoneNumber) { success ->
                            isSubmitting = false
                            if (success) {
                                onRechargeSuccess(pkg.minutes)
                            } else {
                                error = paymentFailedMessage
                            }
                        }
                    } else {
                        onRechargeSuccess(pkg.minutes)
                    }
                },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(stringResource(R.string.recharge_pay_mpesa), color = Color.White)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSubmitting,
            ) {
                Text(stringResource(R.string.recharge_cancel), color = Color.Black)
            }
        },
    )
}
