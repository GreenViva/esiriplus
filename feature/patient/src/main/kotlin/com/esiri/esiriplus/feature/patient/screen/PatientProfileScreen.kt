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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.core.ui.ScrollIndicatorBox
import com.esiri.esiriplus.feature.patient.R
import com.esiri.esiriplus.feature.patient.viewmodel.PatientProfileViewModel

private val BrandTeal = Color(0xFF2A9D8F)
private val MintLight = Color(0xFFE0F2F1)

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

    // Localized age group labels mapped to their internal (backend) values
    val ageGroupEntries = listOf(
        stringResource(R.string.profile_age_under_18) to "Under 18",
        stringResource(R.string.profile_age_18_24) to "18-24",
        stringResource(R.string.profile_age_25_34) to "25-34",
        stringResource(R.string.profile_age_35_44) to "35-44",
        stringResource(R.string.profile_age_45_54) to "45-54",
        stringResource(R.string.profile_age_55_64) to "55-64",
        stringResource(R.string.profile_age_65_plus) to "65+",
    )

    // Localized sex labels mapped to their internal (backend) values
    val sexEntries = listOf(
        stringResource(R.string.profile_male) to "Male",
        stringResource(R.string.profile_female) to "Female",
    )

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
            val scrollState = rememberScrollState()
            ScrollIndicatorBox(scrollState = scrollState) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 24.dp),
            ) {
                // Top bar
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.profile_back),
                        tint = Color.Black,
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.profile_title),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                )

                Spacer(Modifier.height(24.dp))

                // Sex selection
                Text(
                    text = stringResource(R.string.profile_sex),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.Black,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    sexEntries.forEach { (label, value) ->
                        FilterChip(
                            selected = uiState.sex == value,
                            onClick = { viewModel.onSexChanged(value) },
                            label = { Text(label, color = Color.Black) },
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
                    text = stringResource(R.string.profile_age_group),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.Black,
                )
                Spacer(Modifier.height(8.dp))
                DropdownSelector(
                    options = ageGroupEntries.map { it.first },
                    selected = ageGroupEntries.firstOrNull { it.second == uiState.ageGroup }?.first ?: uiState.ageGroup,
                    onSelected = { label ->
                        val internalValue = ageGroupEntries.firstOrNull { it.first == label }?.second ?: label
                        viewModel.onAgeGroupChanged(internalValue)
                    },
                    placeholder = stringResource(R.string.profile_select_age_group),
                )

                Spacer(Modifier.height(20.dp))

                // Blood Type dropdown
                Text(
                    text = stringResource(R.string.profile_blood_type),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.Black,
                )
                Spacer(Modifier.height(8.dp))
                DropdownSelector(
                    options = bloodTypeOptions,
                    selected = uiState.bloodType,
                    onSelected = viewModel::onBloodTypeChanged,
                    placeholder = stringResource(R.string.profile_select_blood_type),
                )

                Spacer(Modifier.height(20.dp))

                // Allergies
                Text(
                    text = stringResource(R.string.profile_allergies),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.Black,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.allergies,
                    onValueChange = viewModel::onAllergiesChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.profile_allergies_placeholder), color = Color.Gray) },
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
                    text = stringResource(R.string.profile_chronic_conditions),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.Black,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.chronicConditions,
                    onValueChange = viewModel::onChronicConditionsChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.profile_chronic_conditions_placeholder), color = Color.Gray) },
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
                            text = stringResource(R.string.profile_save),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
            } // ScrollIndicatorBox
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
