package com.esiri.esiriplus.feature.patient.screen

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val BrandTeal = Color(0xFF2A9D8F)
private val CardBorder = Color(0xFFE5E7EB)

@Composable
fun SymptomsEntryDialog(
    patientAgeGroup: String?,
    patientSex: String?,
    onConfirm: (symptoms: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var symptoms by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Describe Your Symptoms",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "This helps the doctor prepare for your consultation.",
                    fontSize = 13.sp,
                    color = Color(0xFF6B7280),
                )

                // Show patient profile summary if available
                val profileParts = listOfNotNull(patientAgeGroup, patientSex).filter { it.isNotBlank() }
                if (profileParts.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BrandTeal.copy(alpha = 0.08f),
                    ) {
                        Text(
                            text = "Profile: ${profileParts.joinToString(" | ")}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 12.sp,
                            color = BrandTeal,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = symptoms,
                    onValueChange = { symptoms = it },
                    label = { Text("What symptoms are you experiencing?") },
                    placeholder = { Text("e.g., headache for 3 days, mild fever...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandTeal,
                        unfocusedBorderColor = CardBorder,
                        cursorColor = BrandTeal,
                    ),
                    maxLines = 5,
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = 15.sp,
                            color = Color(0xFF6B7280),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Button(
                        onClick = { onConfirm(symptoms) },
                        enabled = symptoms.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandTeal,
                            disabledContainerColor = BrandTeal.copy(alpha = 0.4f),
                        ),
                    ) {
                        Text(
                            text = "Send Request",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}
