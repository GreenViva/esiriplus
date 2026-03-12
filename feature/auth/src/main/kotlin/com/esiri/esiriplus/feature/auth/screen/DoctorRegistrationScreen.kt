package com.esiri.esiriplus.feature.auth.screen

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.feature.auth.R
import com.esiri.esiriplus.feature.auth.biometric.BiometricEnrollmentScreen
import com.esiri.esiriplus.feature.auth.ui.GradientBackground
import com.esiri.esiriplus.feature.auth.viewmodel.DoctorRegistrationUiState
import com.esiri.esiriplus.feature.auth.viewmodel.DoctorRegistrationViewModel

private val BrandTeal = Color(0xFF2A9D8F)

private val Specialties = listOf(
    "Nurse",
    "Clinical Officer",
    "Pharmacist",
    "General Practitioner",
    "Specialist",
    "Psychologist",
    "Herbalist",
)

private val CommonLanguages = listOf("English", "Swahili")

private val OtherLanguages = listOf(
    "Afrikaans", "Amharic", "Arabic", "Bengali",
    "Chichewa", "Chinese", "Dutch", "French",
    "German", "Greek", "Hausa", "Hindi",
    "Indonesian", "Italian", "Japanese", "Korean",
    "Malay", "Pashto", "Persian", "Polish",
    "Portuguese", "Punjabi", "Romanian", "Russian",
    "Somali", "Spanish", "Tamil", "Thai",
    "Turkish", "Ukrainian", "Urdu", "Vietnamese",
    "Yoruba", "Zulu",
)

private val SpecialtyServices = mapOf(
    "Nurse" to listOf(
        "Health Education & Wellness Guidance",
        "Nutrition & Lifestyle Counseling",
        "Chronic Disease Monitoring Support",
        "Post-Operative & Wound Care Guidance",
        "Maternal & Child Health Education",
        "Medication Adherence Coaching",
        "Basic Symptom Advice (Non-diagnostic)",
    ),
    "Clinical Officer" to listOf(
        "Common Acute Illness Treatment",
        "Minor Infection Management",
        "Basic Chronic Disease Management",
        "Women's Health (Non-Complicated Cases)",
        "Minor Skin Conditions",
        "Musculoskeletal Pain (Mild Cases)",
        "Medication Prescription (Basic List Only)",
        "Malaria Diagnosis & Treatment (After Test)",
    ),
    "Pharmacist" to listOf(
        "Medication Counseling",
        "Drug Interaction Check",
        "Prescription Review",
        "OTC Medication Recommendation",
        "Chronic Medication Support",
        "Herbal & Traditional Medicine Interaction Advice",
    ),
    "General Practitioner" to listOf(
        "Comprehensive Medical Consultation",
        "Chronic Disease Diagnosis & Management",
        "Women's Health (Full Primary Care)",
        "Men's Health Consultation",
        "Mental Health (Mild to Moderate)",
        "Pediatric Primary Care",
        "Infectious Disease Management",
        "Medical Referral & Lab Ordering",
    ),
    "Specialist" to listOf(
        "Complex Condition Management",
        "Second Opinion Consultation",
        "Specialized Chronic Disease Management",
        "Post-Hospital Follow-Up",
        "Advanced Diagnostic Interpretation",
        "Specialty-Specific Consultation",
    ),
    "Psychologist" to listOf(
        "Individual Therapy (CBT / Talk Therapy)",
        "Depression & Anxiety Counseling",
        "Trauma & PTSD Therapy",
        "Relationship & Family Therapy",
        "Stress & Burnout Management",
        "Behavioral & Habit Modification",
        "Psychological Assessment",
    ),
    "Herbalist" to listOf(
        "Herbal Medicine Consultation",
        "Traditional Remedy Guidance",
        "Natural Supplement Advice",
        "Herbal Wellness Assessment",
        "Plant-Based Treatment Plans",
        "Herbal Drug Interaction Advice",
    ),
)

private val SpecialtyPrices = mapOf(
    "Nurse" to "TZS 3,000",
    "Clinical Officer" to "TZS 5,000",
    "Pharmacist" to "TZS 5,000",
    "General Practitioner" to "TZS 10,000",
    "Specialist" to "TZS 30,000",
    "Psychologist" to "TZS 50,000",
    "Herbalist" to "TZS 5,000",
)

private val SpecialistFields = listOf(
    "Dermatology",
    "Cardiology",
    "Endocrinology",
    "Neurology",
    "Psychiatry",
    "Pediatrics",
    "OBGYN",
    "Rheumatology",
    "Gastroenterology",
)

private val Countries = listOf(
    "Tanzania", "Kenya", "Uganda", "Rwanda",
    "Burundi", "Ethiopia", "Nigeria", "South Africa",
    "Ghana", "Mozambique",
)

private val CountryCodes = listOf(
    "+255" to "TZ",
    "+254" to "KE",
    "+256" to "UG",
    "+250" to "RW",
    "+257" to "BI",
    "+251" to "ET",
    "+234" to "NG",
    "+27" to "ZA",
    "+233" to "GH",
    "+258" to "MZ",
    "+1" to "US",
    "+44" to "UK",
    "+91" to "IN",
    "+86" to "CN",
)

@Composable
fun DoctorRegistrationScreen(
    onComplete: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DoctorRegistrationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // Activity result launchers
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> viewModel.onProfilePhotoSelected(uri) }

    val context = LocalContext.current

    val licenseDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val name = uri?.let { resolveFileName(context, it) }
        viewModel.onLicenseDocumentSelected(uri, name)
    }

    val certificatesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val name = uri?.let { resolveFileName(context, it) }
        viewModel.onCertificatesSelected(uri, name)
    }

    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) onComplete()
    }

    GradientBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Doctor Portal Header
            DoctorPortalHeader()

            // White card content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Sign In / Register tab row
                SignInRegisterTabRow(onSignInClick = onNavigateToLogin)

                Spacer(modifier = Modifier.height(20.dp))

                // Step indicator
                StepIndicator(currentStep = uiState.currentStep)

                // Scrollable step content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Spacer(modifier = Modifier.height(20.dp))

                    // Step title and subtitle
                    val stepTitle = when (uiState.currentStep) {
                        1 -> stringResource(R.string.doctor_reg_step1_title)
                        2 -> stringResource(R.string.doctor_reg_step2_title)
                        3 -> stringResource(R.string.doctor_reg_step3_title)
                        4 -> stringResource(R.string.doctor_reg_step4_title)
                        5 -> stringResource(R.string.doctor_reg_step5_title)
                        6 -> stringResource(R.string.doctor_reg_step6_title)
                        7 -> stringResource(R.string.doctor_reg_step7_title)
                        8 -> stringResource(R.string.doctor_reg_step8_title)
                        else -> ""
                    }
                    val stepSubtitle = when (uiState.currentStep) {
                        1 -> stringResource(R.string.doctor_reg_step1_subtitle)
                        2 -> stringResource(R.string.doctor_reg_step2_subtitle)
                        3 -> stringResource(R.string.doctor_reg_step3_subtitle)
                        4 -> stringResource(R.string.doctor_reg_step4_subtitle)
                        5 -> stringResource(R.string.doctor_reg_step5_subtitle)
                        6 -> stringResource(R.string.doctor_reg_step6_subtitle)
                        7 -> stringResource(R.string.doctor_reg_step7_subtitle)
                        8 -> stringResource(R.string.doctor_reg_step8_subtitle)
                        else -> ""
                    }
                    Text(
                        text = stepTitle,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stepSubtitle,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    // Step content
                    when (uiState.currentStep) {
                        1 -> Step1Content(uiState, viewModel)
                        2 -> Step2Content(uiState) {
                            photoPickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        }
                        3 -> Step3Content(uiState, viewModel)
                        4 -> Step4Content(uiState, viewModel)
                        5 -> Step5Content(uiState, viewModel)
                        6 -> Step6Content(uiState, viewModel)
                        7 -> Step7Content(
                            uiState = uiState,
                            onUploadLicense = {
                                licenseDocLauncher.launch(arrayOf("application/pdf", "image/*"))
                            },
                            onUploadCertificates = {
                                certificatesLauncher.launch(arrayOf("application/pdf", "image/*"))
                            },
                        )
                        8 -> BiometricEnrollmentScreen(
                            biometricAvailable = uiState.biometricAvailable,
                            biometricEnrolled = uiState.biometricEnrolled,
                            deviceAlreadyBound = uiState.deviceAlreadyBound,
                            biometricAuthManager = viewModel.biometricAuthManager,
                            onEnrollmentSuccess = viewModel::onBiometricEnrolled,
                            onRefreshBiometricState = viewModel::refreshBiometricState,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Error message
            val errorMessage = uiState.registrationError
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = Color.Red,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    textAlign = TextAlign.Center,
                )
            }

            // Bottom bar
            BottomBar(
                currentStep = uiState.currentStep,
                isStepValid = uiState.isCurrentStepValid,
                isRegistering = uiState.isRegistering,
                onBack = viewModel::onBack,
                onContinue = viewModel::onContinue,
            )
        }
    }
}

@Composable
private fun DoctorPortalHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_stethoscope),
            contentDescription = null,
            modifier = Modifier.size(56.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.doctor_reg_portal_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.doctor_reg_portal_subtitle),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SignInRegisterTabRow(onSignInClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onSignInClick)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.doctor_reg_sign_in_tab),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.doctor_reg_register_tab),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun StepIndicator(currentStep: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        for (step in 1..8) {
            val isCompleted = step < currentStep
            val isCurrent = step == currentStep

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .then(
                        if (isCompleted || isCurrent) {
                            Modifier.background(BrandTeal, CircleShape)
                        } else {
                            Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White,
                    )
                } else {
                    Text(
                        text = "$step",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (step < 9) {
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(2.dp)
                        .background(
                            if (step < currentStep) BrandTeal else MaterialTheme.colorScheme.outline,
                        ),
                )
            }
        }
    }
}

@Composable
private fun RegistrationTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    iconRes: Int,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePasswordVisibility: (() -> Unit)? = null,
    minLines: Int = 1,
    singleLine: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = if (placeholder.isNotEmpty()) {
                { Text(text = placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) }
            } else {
                null
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = if (isPassword && onTogglePasswordVisibility != null) {
                {
                    IconButton(onClick = onTogglePasswordVisibility) {
                        Icon(
                            painter = painterResource(
                                if (passwordVisible) R.drawable.ic_visibility
                                else R.drawable.ic_visibility_off,
                            ),
                            contentDescription = if (passwordVisible) stringResource(R.string.doctor_reg_hide_password) else stringResource(R.string.doctor_reg_show_password),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                null
            },
            visualTransformation = if (isPassword && !passwordVisible) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandTeal,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
            singleLine = singleLine,
            minLines = minLines,
        )
    }
}

// ── Step 1: Create Account ──────────────────────────────────────────────────

@Composable
private fun Step1Content(
    uiState: DoctorRegistrationUiState,
    viewModel: DoctorRegistrationViewModel,
) {
    RegistrationTextField(
        value = uiState.email,
        onValueChange = viewModel::onEmailChanged,
        label = stringResource(R.string.doctor_reg_email_label),
        iconRes = R.drawable.ic_email,
        keyboardType = KeyboardType.Email,
    )
    Spacer(modifier = Modifier.height(16.dp))
    RegistrationTextField(
        value = uiState.password,
        onValueChange = viewModel::onPasswordChanged,
        label = stringResource(R.string.doctor_reg_password_label),
        iconRes = R.drawable.ic_lock,
        isPassword = true,
        passwordVisible = uiState.passwordVisible,
        onTogglePasswordVisibility = viewModel::onPasswordVisibleToggled,
    )
    Text(
        text = stringResource(R.string.doctor_reg_password_hint),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
    Spacer(modifier = Modifier.height(16.dp))
    RegistrationTextField(
        value = uiState.confirmPassword,
        onValueChange = viewModel::onConfirmPasswordChanged,
        label = stringResource(R.string.doctor_reg_confirm_password_label),
        iconRes = R.drawable.ic_lock,
        isPassword = true,
        passwordVisible = uiState.confirmPasswordVisible,
        onTogglePasswordVisibility = viewModel::onConfirmPasswordVisibleToggled,
    )
}

// ── Step 2: OTP Verification ─────────────────────────────────────────────────

@Composable
private fun OtpVerificationContent(
    uiState: DoctorRegistrationUiState,
    viewModel: DoctorRegistrationViewModel,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // OTP sending indicator
        if (uiState.otpSending) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = BrandTeal,
                strokeWidth = 3.dp,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.doctor_reg_otp_sending),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }

        // 6-digit OTP input
        OutlinedTextField(
            value = uiState.otpCode,
            onValueChange = viewModel::onOtpCodeChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("000000", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandTeal,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                letterSpacing = 8.sp,
                color = MaterialTheme.colorScheme.onSurface,
            ),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Error message
        val otpError = uiState.otpError
        if (otpError != null) {
            Text(
                text = otpError,
                color = Color.Red,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Verify button
        if (!uiState.otpVerified) {
            Button(
                onClick = viewModel::verifyOtp,
                enabled = uiState.otpCode.length == 6 && !uiState.otpVerifying,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandTeal,
                    disabledContainerColor = BrandTeal.copy(alpha = 0.4f),
                ),
            ) {
                if (uiState.otpVerifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.doctor_reg_otp_verify),
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                    )
                }
            }
        } else {
            // Verified indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = BrandTeal,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.doctor_reg_otp_verified),
                    color = BrandTeal,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Resend button
        if (!uiState.otpVerified) {
            val cooldown = uiState.resendCooldown
            OutlinedButton(
                onClick = viewModel::resendOtp,
                enabled = cooldown == 0 && !uiState.otpSending,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Text(
                    text = if (cooldown > 0) {
                        stringResource(R.string.doctor_reg_otp_resend_cooldown, cooldown)
                    } else {
                        stringResource(R.string.doctor_reg_otp_resend)
                    },
                    color = if (cooldown > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

// ── Step 3: Profile Photo ───────────────────────────────────────────────────

@Composable
private fun Step2Content(
    uiState: DoctorRegistrationUiState,
    onPickPhoto: () -> Unit,
) {
    val dashedTeal = BrandTeal.copy(alpha = 0.5f)
    val dashedGray = MaterialTheme.colorScheme.outline

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .clickable(onClick = onPickPhoto)
                .drawBehind {
                    drawCircle(
                        color = Color(0xFFF3F4F6),
                        radius = size.minDimension / 2,
                    )
                    drawCircle(
                        color = if (uiState.profilePhotoUri != null) dashedTeal else dashedGray,
                        radius = size.minDimension / 2,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(10f, 10f),
                                0f,
                            ),
                        ),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (uiState.profilePhotoUri != null) {
                coil3.compose.AsyncImage(
                    model = uiState.profilePhotoUri,
                    contentDescription = stringResource(R.string.doctor_reg_profile_photo_cd),
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_camera),
                    contentDescription = stringResource(R.string.doctor_reg_select_photo_cd),
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.doctor_reg_photo_hint),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Step 3: Personal Information ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Step3Content(
    uiState: DoctorRegistrationUiState,
    viewModel: DoctorRegistrationViewModel,
) {
    RegistrationTextField(
        value = uiState.fullName,
        onValueChange = viewModel::onFullNameChanged,
        label = stringResource(R.string.doctor_reg_full_name_label),
        iconRes = R.drawable.ic_person,
        placeholder = stringResource(R.string.doctor_reg_full_name_placeholder),
    )
    Spacer(modifier = Modifier.height(16.dp))

    // Phone Number with country code
    Text(
        text = stringResource(R.string.doctor_reg_phone_label),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(8.dp))

    var codeExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Country code dropdown
        ExposedDropdownMenuBox(
            expanded = codeExpanded,
            onExpandedChange = { codeExpanded = it },
            modifier = Modifier.width(120.dp),
        ) {
            OutlinedTextField(
                value = uiState.countryCode,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = codeExpanded) },
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandTeal,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
                singleLine = true,
            )
            ExposedDropdownMenu(
                expanded = codeExpanded,
                onDismissRequest = { codeExpanded = false },
            ) {
                CountryCodes.forEach { (code, label) ->
                    DropdownMenuItem(
                        text = { Text("$code ($label)") },
                        onClick = {
                            viewModel.onCountryCodeChanged(code)
                            codeExpanded = false
                        },
                    )
                }
            }
        }

        // Phone number input
        OutlinedTextField(
            value = uiState.phone,
            onValueChange = viewModel::onPhoneChanged,
            modifier = Modifier.weight(1f),
            placeholder = { Text("700 000 000", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp) },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_phone),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandTeal,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
            singleLine = true,
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.doctor_reg_phone_payment_hint),
        fontSize = 12.sp,
        color = Color(0xFFB45309),
        fontWeight = FontWeight.Medium,
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Specialty dropdown
    var expanded by remember { mutableStateOf(false) }
    Text(
        text = stringResource(R.string.doctor_reg_specialty_label),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(8.dp))
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = uiState.specialty,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            placeholder = { Text(stringResource(R.string.doctor_reg_specialty_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandTeal,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            Specialties.forEach { specialty ->
                DropdownMenuItem(
                    text = { Text(specialty) },
                    onClick = {
                        viewModel.onSpecialtyChanged(specialty)
                        expanded = false
                    },
                )
            }
        }
    }

    // Free-text specialist field when "Specialist" is selected
    if (uiState.specialty == "Specialist") {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.doctor_reg_specialist_field_label),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.customSpecialty,
            onValueChange = viewModel::onCustomSpecialtyChanged,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_badge),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            },
            placeholder = {
                Text(
                    stringResource(R.string.doctor_reg_specialist_field_placeholder),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandTeal,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            ),
        )
    }
}

// ── Step 4: Location & Languages ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Step4Content(
    uiState: DoctorRegistrationUiState,
    viewModel: DoctorRegistrationViewModel,
) {
    // Country dropdown
    var countryExpanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.ic_globe),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.doctor_reg_country_label),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    ExposedDropdownMenuBox(
        expanded = countryExpanded,
        onExpandedChange = { countryExpanded = it },
    ) {
        OutlinedTextField(
            value = uiState.country,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = countryExpanded) },
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandTeal,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
        )
        ExposedDropdownMenu(
            expanded = countryExpanded,
            onDismissRequest = { countryExpanded = false },
        ) {
            Countries.forEach { country ->
                DropdownMenuItem(
                    text = { Text(country) },
                    onClick = {
                        viewModel.onCountryChanged(country)
                        countryExpanded = false
                    },
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Languages section
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.ic_translate),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.doctor_reg_languages_label),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    Spacer(modifier = Modifier.height(12.dp))

    // Common languages
    Text(
        text = stringResource(R.string.doctor_reg_common_languages),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CommonLanguages.forEach { lang ->
            LanguageChip(
                label = when (lang) {
                    "English" -> "English (English)"
                    "Swahili" -> "Swahili (Kiswahili)"
                    else -> lang
                },
                selected = uiState.selectedLanguages.contains(lang),
                onClick = { viewModel.onLanguageToggled(lang) },
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Other languages
    Text(
        text = stringResource(R.string.doctor_reg_other_languages),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))
    OtherLanguages.chunked(2).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            row.forEach { lang ->
                LanguageChip(
                    label = lang,
                    selected = uiState.selectedLanguages.contains(lang),
                    onClick = { viewModel.onLanguageToggled(lang) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (row.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.doctor_reg_languages_selected, uiState.selectedLanguages.size),
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LanguageChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, BrandTeal, shape)
                        .background(BrandTeal.copy(alpha = 0.08f))
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(BrandTeal, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = Color.White,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) BrandTeal else MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ── Step 5: Professional Details ────────────────────────────────────────────

@Composable
private fun Step5Content(
    uiState: DoctorRegistrationUiState,
    viewModel: DoctorRegistrationViewModel,
) {
    RegistrationTextField(
        value = uiState.licenseNumber,
        onValueChange = viewModel::onLicenseNumberChanged,
        label = stringResource(R.string.doctor_reg_license_label),
        iconRes = R.drawable.ic_badge,
        placeholder = stringResource(R.string.doctor_reg_license_placeholder),
    )
    Spacer(modifier = Modifier.height(16.dp))
    RegistrationTextField(
        value = uiState.yearsExperience,
        onValueChange = viewModel::onYearsExperienceChanged,
        label = stringResource(R.string.doctor_reg_experience_label),
        iconRes = R.drawable.ic_clock,
        placeholder = "0",
        keyboardType = KeyboardType.Number,
    )
    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = stringResource(R.string.doctor_reg_bio_label),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = uiState.bio,
        onValueChange = viewModel::onBioChanged,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = stringResource(R.string.doctor_reg_bio_placeholder),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
            )
        },
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BrandTeal,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
        minLines = 4,
        singleLine = false,
    )
}

// ── Step 6: Services Offered ────────────────────────────────────────────────

@Composable
private fun Step6Content(
    uiState: DoctorRegistrationUiState,
    viewModel: DoctorRegistrationViewModel,
) {
    val services = SpecialtyServices[uiState.specialty] ?: emptyList()
    val price = SpecialtyPrices[uiState.specialty] ?: ""

    // Specialty tier header
    if (price.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(BrandTeal.copy(alpha = 0.08f))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = uiState.specialty +
                        if (uiState.specialty == "Specialist" && uiState.customSpecialty.isNotBlank()) {
                            " — ${uiState.customSpecialty}"
                        } else {
                            ""
                        },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.doctor_reg_services_available, services.size),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = price,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = BrandTeal,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    Text(
        text = stringResource(R.string.doctor_reg_select_services),
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(12.dp))

    services.forEach { service ->
        val isSelected = uiState.selectedServices.contains(service)
        val shape = RoundedCornerShape(12.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .then(
                    if (isSelected) {
                        Modifier.border(1.5.dp, BrandTeal, shape)
                            .background(BrandTeal.copy(alpha = 0.04f))
                    } else {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape)
                    },
                )
                .clickable { viewModel.onServiceToggled(service) }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .then(
                        if (isSelected) {
                            Modifier.border(2.dp, BrandTeal, CircleShape)
                        } else {
                            Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(BrandTeal, CircleShape),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = service,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.doctor_reg_services_selected, uiState.selectedServices.size, services.size),
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

// ── Step 7: Upload Credentials ──────────────────────────────────────────────

@Composable
private fun Step7Content(
    uiState: DoctorRegistrationUiState,
    onUploadLicense: () -> Unit,
    onUploadCertificates: () -> Unit,
) {
    // Medical License upload
    UploadBox(
        title = stringResource(R.string.doctor_reg_license_upload_title),
        subtitle = stringResource(R.string.doctor_reg_license_upload_subtitle),
        iconRes = R.drawable.ic_upload,
        hasFile = uiState.licenseDocumentUri != null,
        fileName = uiState.licenseDocumentName,
        onClick = onUploadLicense,
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Additional Certificates upload
    UploadBox(
        title = stringResource(R.string.doctor_reg_certificates_upload_title),
        subtitle = stringResource(R.string.doctor_reg_certificates_upload_subtitle),
        iconRes = R.drawable.ic_document,
        hasFile = uiState.certificatesUri != null,
        fileName = uiState.certificatesName,
        onClick = onUploadCertificates,
    )

    Spacer(modifier = Modifier.height(20.dp))

    // Info card
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFFEF9E7))
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(text = "\uD83D\uDCCB", fontSize = 16.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.doctor_reg_credentials_review_notice),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun UploadBox(
    title: String,
    subtitle: String,
    iconRes: Int,
    hasFile: Boolean,
    fileName: String? = null,
    onClick: () -> Unit,
) {
    val dashedColor = if (hasFile) BrandTeal else MaterialTheme.colorScheme.outline
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .drawBehind {
                drawRoundRect(
                    color = dashedColor,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(10f, 8f),
                            0f,
                        ),
                    ),
                )
            }
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = if (hasFile) BrandTeal else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (hasFile) "$title ${stringResource(R.string.doctor_reg_selected_suffix)}" else title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = BrandTeal,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hasFile && fileName != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = BrandTeal,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = fileName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = BrandTeal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ── Bottom Bar ──────────────────────────────────────────────────────────────

@Composable
private fun BottomBar(
    currentStep: Int,
    isStepValid: Boolean,
    isRegistering: Boolean,
    isSendingOtp: Boolean = false,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    // (OTP step disabled — will be re-enabled after DNS verification)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (currentStep > 1) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.doctor_reg_back_button),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Button(
            onClick = onContinue,
            enabled = isStepValid && !isRegistering && !isSendingOtp,
            modifier = Modifier
                .weight(if (currentStep > 1) 1.5f else 1f)
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandTeal,
                contentColor = Color.White,
                disabledContainerColor = BrandTeal.copy(alpha = 0.4f),
                disabledContentColor = Color.White.copy(alpha = 0.6f),
            ),
        ) {
            if (isRegistering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.doctor_reg_registering),
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                )
            } else {
                Text(
                    text = when (currentStep) {
                        8 -> stringResource(R.string.doctor_reg_complete_registration)
                        else -> stringResource(R.string.doctor_reg_continue)
                    },
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                )
                if (currentStep < 8) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

private fun resolveFileName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
    }
}
