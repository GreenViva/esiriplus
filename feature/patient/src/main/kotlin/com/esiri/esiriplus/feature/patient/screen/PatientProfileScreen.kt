package com.esiri.esiriplus.feature.patient.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.feature.patient.viewmodel.PatientProfileViewModel

private val BrandTeal = Color(0xFF2A9D8F)
private val MintLight = Color(0xFFE0F2F1)

private val ageGroupOptions = listOf(
    "Under 18", "18-24", "25-34", "35-44", "45-54", "55-64", "65+",
)

private val bloodTypeOptions = listOf(
    "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientProfileScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PatientProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) onBack()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(Color.White, MintLight))),
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = BrandTeal,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
            ) {
                // Top bar
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.Black,
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Health Profile",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                )

                Spacer(Modifier.height(24.dp))

                // Sex selection
                Text(
                    text = "Sex",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.Black,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    listOf("Male", "Female").forEach { option ->
                        FilterChip(
                            selected = uiState.sex == option,
                            onClick = { viewModel.onSexChanged(option) },
                            label = { Text(option, color = Color.Black) },
                            modifier = Modifier.padding(end = 12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandTeal.copy(alpha = 0.15f),
                                selectedLabelColor = Color.Black,
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Age Group dropdown
                Text(
                    text = "Age Group",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.Black,
                )
                Spacer(Modifier.height(8.dp))
                DropdownSelector(
                    options = ageGroupOptions,
                    selected = uiState.ageGroup,
                    onSelected = viewModel::onAgeGroupChanged,
                    placeholder = "Select age group",
                )

                Spacer(Modifier.height(20.dp))

                // Blood Type dropdown
                Text(
                    text = "Blood Type",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.Black,
                )
                Spacer(Modifier.height(8.dp))
                DropdownSelector(
                    options = bloodTypeOptions,
                    selected = uiState.bloodType,
                    onSelected = viewModel::onBloodTypeChanged,
                    placeholder = "Select blood type",
                )

                Spacer(Modifier.height(20.dp))

                // Allergies
                Text(
                    text = "Allergies",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.Black,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.allergies,
                    onValueChange = viewModel::onAllergiesChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. Penicillin, Peanuts", color = Color.Gray) },
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandTeal,
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                    ),
                )

                Spacer(Modifier.height(20.dp))

                // Chronic Conditions
                Text(
                    text = "Chronic Conditions",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.Black,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.chronicConditions,
                    onValueChange = viewModel::onChronicConditionsChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. Diabetes, Hypertension", color = Color.Gray) },
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandTeal,
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                    ),
                )

                Spacer(Modifier.height(32.dp))

                // Save button
                Button(
                    onClick = viewModel::saveProfile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !uiState.isSaving,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.padding(4.dp),
                        )
                    } else {
                        Text(
                            text = "Save",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSelector(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    placeholder: String,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            placeholder = { Text(placeholder, color = Color.Gray) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandTeal,
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black,
            ),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, color = Color.Black) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
