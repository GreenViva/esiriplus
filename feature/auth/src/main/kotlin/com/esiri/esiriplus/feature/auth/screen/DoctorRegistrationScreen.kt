package com.esiri.esiriplus.feature.auth.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.feature.auth.R
import com.esiri.esiriplus.feature.auth.biometric.BiometricEnrollmentScreen
import com.esiri.esiriplus.feature.auth.ui.GradientBackground
import com.esiri.esiriplus.feature.auth.viewmodel.DoctorRegistrationUiState
import com.esiri.esiriplus.feature.auth.viewmodel.DoctorRegistrationViewModel

private val BrandTeal = Color(0xFF2A9D8F)
private val DarkText = Color.Black
private val SubtitleGray = Color.Black
private val CardBorder = Color(0xFFE5E7EB)
private val IconBg = Color(0xFFF0FDFA)
private val FieldBg = Color(0xFFF8FFFE)

private val StepTitles = listOf(
    "Create Your Account" to "Enter your email and create a secure password",
    "Profile Photo" to "Upload a professional photo of yourself",
    "Personal Information" to "Tell us about yourself",
    "Location & Languages" to "Where are you based and what languages do you speak?",
    "Professional Details" to "Your qualifications and experience",
    "Services Offered" to "Services available for your specialty",
    "Upload Credentials" to "Upload your medical license and certificates for verification",
    "Biometric Security" to "Set up fingerprint or face unlock for your account",
)

private val Specialties = listOf(
    "Nurse",
    "Clinical Officer",
    "Pharmacist",
    "General Practitioner",
    "Specialist",
    "Psychologist",
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
)

private val SpecialtyPrices = mapOf(
    "Nurse" to "TZS 3,000",
    "Clinical Officer" to "TZS 5,000",
    "Pharmacist" to "TZS 5,000",
    "General Practitioner" to "TZS 10,000",
    "Specialist" to "TZS 30,000",
    "Psychologist" to "TZS 50,000",
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

    val licenseDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> viewModel.onLicenseDocumentSelected(uri) }

    val certificatesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> viewModel.onCertificatesSelected(uri) }

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
                    .background(Color.White)
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
                    val (title, subtitle) = StepTitles[uiState.currentStep - 1]
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkText,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        fontSize = 14.sp,
                        color = SubtitleGray,
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
                        .background(Color.White)
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
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(BrandTeal, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_stethoscope),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = Color.White,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Doctor Portal",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = DarkText,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Join our network of verified healthcare professionals",
            fontSize = 14.sp,
            color = SubtitleGray,
        )
    }
}

@Composable
private fun SignInRegisterTabRow(onSignInClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF3F4F6))
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
                text = "Sign In",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = SubtitleGray,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Register",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = DarkText,
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
                            Modifier.border(1.5.dp, CardBorder, CircleShape)
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
                        color = if (isCurrent) Color.White else SubtitleGray,
                    )
                }
            }

            if (step < 8) {
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(2.dp)
                        .background(
                            if (step < currentStep) BrandTeal else CardBorder,
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
            color = DarkText,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = if (placeholder.isNotEmpty()) {
                { Text(text = placeholder, color = SubtitleGray, fontSize = 14.sp) }
            } else {
                null
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = SubtitleGray,
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
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            modifier = Modifier.size(20.dp),
                            tint = SubtitleGray,
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
                unfocusedBorderColor = CardBorder,
                focusedContainerColor = FieldBg,
                unfocusedContainerColor = FieldBg,
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
        label = "Email",
        iconRes = R.drawable.ic_email,
        keyboardType = KeyboardType.Email,
    )
    Spacer(modifier = Modifier.height(16.dp))
    RegistrationTextField(
        value = uiState.password,
        onValueChange = viewModel::onPasswordChanged,
        label = "Password",
        iconRes = R.drawable.ic_lock,
        isPassword = true,
        passwordVisible = uiState.passwordVisible,
        onTogglePasswordVisibility = viewModel::onPasswordVisibleToggled,
    )
    Spacer(modifier = Modifier.height(16.dp))
    RegistrationTextField(
        value = uiState.confirmPassword,
        onValueChange = viewModel::onConfirmPasswordChanged,
        label = "Confirm Password",
        iconRes = R.drawable.ic_lock,
        isPassword = true,
        passwordVisible = uiState.confirmPasswordVisible,
        onTogglePasswordVisibility = viewModel::onConfirmPasswordVisibleToggled,
    )
}

// ── Step 2: Profile Photo ───────────────────────────────────────────────────

@Composable
private fun Step2Content(
    uiState: DoctorRegistrationUiState,
    onPickPhoto: () -> Unit,
) {
    val dashedTeal = BrandTeal.copy(alpha = 0.5f)
    val dashedGray = CardBorder

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
                    contentDescription = "Profile photo",
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_camera),
                    contentDescription = "Select photo",
                    modifier = Modifier.size(40.dp),
                    tint = SubtitleGray,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Tap to select a photo (JPG, PNG, max 5MB)",
            fontSize = 13.sp,
            color = SubtitleGray,
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
        label = "Full Name",
        iconRes = R.drawable.ic_person,
        placeholder = "Dr. John Doe",
    )
    Spacer(modifier = Modifier.height(16.dp))

    // Phone Number with country code
    Text(
        text = "Phone Number",
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = DarkText,
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
                    unfocusedBorderColor = CardBorder,
                    focusedContainerColor = FieldBg,
                    unfocusedContainerColor = FieldBg,
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
            placeholder = { Text("700 000 000", color = DarkText, fontSize = 14.sp) },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_phone),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = DarkText,
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandTeal,
                unfocusedBorderColor = CardBorder,
                focusedContainerColor = FieldBg,
                unfocusedContainerColor = FieldBg,
            ),
            singleLine = true,
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Specialty dropdown
    var expanded by remember { mutableStateOf(false) }
    Text(
        text = "Medical Specialty",
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = DarkText,
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
            placeholder = { Text("Select your specialty", color = SubtitleGray, fontSize = 14.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandTeal,
                unfocusedBorderColor = CardBorder,
                focusedContainerColor = FieldBg,
                unfocusedContainerColor = FieldBg,
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
            text = "Specialist Field",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = DarkText,
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
                    tint = DarkText,
                )
            },
            placeholder = {
                Text(
                    "e.g. Dentist, Cardiologist, Dermatologist",
                    color = SubtitleGray,
                    fontSize = 14.sp,
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandTeal,
                unfocusedBorderColor = CardBorder,
                focusedContainerColor = FieldBg,
                unfocusedContainerColor = FieldBg,
                focusedTextColor = DarkText,
                unfocusedTextColor = DarkText,
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
            tint = DarkText,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Country",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = DarkText,
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
                unfocusedBorderColor = CardBorder,
                focusedContainerColor = FieldBg,
                unfocusedContainerColor = FieldBg,
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
            tint = DarkText,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Languages You Speak",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = DarkText,
        )
    }
    Spacer(modifier = Modifier.height(12.dp))

    // Common languages
    Text(
        text = "COMMON LANGUAGES",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = SubtitleGray,
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
        text = "OTHER LANGUAGES",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = SubtitleGray,
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
        text = "${uiState.selectedLanguages.size} language(s) selected",
        fontSize = 13.sp,
        color = SubtitleGray,
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
                    Modifier.border(1.dp, CardBorder, shape)
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
                    .border(1.5.dp, CardBorder, CircleShape),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) BrandTeal else DarkText,
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
        label = "Medical License Number",
        iconRes = R.drawable.ic_badge,
        placeholder = "e.g., TZ-MED-12345",
    )
    Spacer(modifier = Modifier.height(16.dp))
    RegistrationTextField(
        value = uiState.yearsExperience,
        onValueChange = viewModel::onYearsExperienceChanged,
        label = "Years of Experience",
        iconRes = R.drawable.ic_clock,
        placeholder = "0",
        keyboardType = KeyboardType.Number,
    )
    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Professional Bio",
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = DarkText,
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = uiState.bio,
        onValueChange = viewModel::onBioChanged,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = "Tell patients about your background, approach, and areas of expertise...",
                color = SubtitleGray,
                fontSize = 14.sp,
            )
        },
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BrandTeal,
            unfocusedBorderColor = CardBorder,
            focusedContainerColor = FieldBg,
            unfocusedContainerColor = FieldBg,
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
                    color = DarkText,
                )
                Text(
                    text = "${services.size} services available",
                    fontSize = 12.sp,
                    color = DarkText,
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
        text = "Select the services you will offer:",
        fontSize = 13.sp,
        color = DarkText,
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
                        Modifier.border(1.dp, CardBorder, shape)
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
                            Modifier.border(1.5.dp, CardBorder, CircleShape)
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
                color = DarkText,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "${uiState.selectedServices.size} of ${services.size} service(s) selected",
        fontSize = 13.sp,
        color = DarkText,
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
        title = "Medical License",
        subtitle = "Click to upload PDF or image",
        iconRes = R.drawable.ic_upload,
        hasFile = uiState.licenseDocumentUri != null,
        onClick = onUploadLicense,
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Additional Certificates upload
    UploadBox(
        title = "Additional Certificates",
        subtitle = "Optional: specialization certificates",
        iconRes = R.drawable.ic_document,
        hasFile = uiState.certificatesUri != null,
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
            text = "Your credentials will be reviewed by our team. You will be notified once your profile is verified.",
            fontSize = 13.sp,
            color = DarkText,
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
    onClick: () -> Unit,
) {
    val dashedColor = if (hasFile) BrandTeal else CardBorder
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
            tint = if (hasFile) BrandTeal else SubtitleGray,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (hasFile) "$title (selected)" else title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = BrandTeal,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            fontSize = 13.sp,
            color = SubtitleGray,
        )
    }
}

// ── Bottom Bar ──────────────────────────────────────────────────────────────

@Composable
private fun BottomBar(
    currentStep: Int,
    isStepValid: Boolean,
    isRegistering: Boolean,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
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
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = DarkText,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Back",
                    color = DarkText,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Button(
            onClick = onContinue,
            enabled = isStepValid && !isRegistering,
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
                    text = "Registering...",
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                )
            } else {
                Text(
                    text = if (currentStep == 8) "Complete Registration" else "Continue",
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
