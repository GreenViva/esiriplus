package com.esiri.esiriplus.feature.auth.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.DoctorRegistration
import com.esiri.esiriplus.core.domain.usecase.RegisterDoctorUseCase
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.feature.auth.biometric.BiometricAuthManager
import com.esiri.esiriplus.feature.auth.biometric.DeviceBindingManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

@HiltViewModel
class DoctorRegistrationViewModel @Inject constructor(
    private val registerDoctorUseCase: RegisterDoctorUseCase,
    val biometricAuthManager: BiometricAuthManager,
    private val deviceBindingManager: DeviceBindingManager,
    private val edgeFunctionClient: EdgeFunctionClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DoctorRegistrationUiState())
    val uiState: StateFlow<DoctorRegistrationUiState> = _uiState.asStateFlow()

    init {
        // Device binding is temporarily disabled.
        val biometricAvailable = biometricAuthManager.isAvailable()
        _uiState.update {
            it.copy(
                biometricAvailable = biometricAvailable,
                deviceAlreadyBound = false,
            )
        }
    }

    private var resendCooldownJob: Job? = null

    // Step navigation
    fun onBack() {
        _uiState.update { it.copy(currentStep = (it.currentStep - 1).coerceAtLeast(1)) }
    }

    fun onContinue() {
        val state = _uiState.value
        if (state.currentStep == 8) {
            completeRegistration()
        } else {
            _uiState.update { it.copy(currentStep = (it.currentStep + 1).coerceAtMost(8)) }
        }
    }

    // OTP: Send
    fun sendOtp() {
        val email = _uiState.value.email.trim().lowercase()
        if (email.isBlank()) return
        if (_uiState.value.otpSending) return

        viewModelScope.launch {
            _uiState.update { it.copy(otpSending = true, otpError = null, registrationError = null) }
            Log.d("DoctorRegVM", "Sending OTP to $email")
            try {
                val body = buildJsonObject { put("email", email) }
                val result = edgeFunctionClient.invoke(
                    functionName = "send-doctor-otp",
                    body = body,
                    anonymous = true,
                )
                Log.d("DoctorRegVM", "sendOtp result: $result")
                when (result) {
                    is ApiResult.Success -> {
                        Log.d("DoctorRegVM", "OTP sent successfully, moving to step 2")
                        _uiState.update {
                            it.copy(
                                otpSending = false,
                                otpSent = true,
                                currentStep = 2,
                            )
                        }
                        startResendCooldown()
                    }
                    else -> {
                        val errorMsg = parseApiError(result, "Failed to send verification code")
                        Log.e("DoctorRegVM", "sendOtp error: $errorMsg")
                        _uiState.update { it.copy(otpSending = false, registrationError = errorMsg) }
                    }
                }
            } catch (e: Exception) {
                Log.e("DoctorRegVM", "sendOtp failed", e)
                _uiState.update { it.copy(otpSending = false, registrationError = e.message ?: "Failed to send code") }
            }
        }
    }

    // OTP: Verify
    fun verifyOtp() {
        val state = _uiState.value
        val email = state.email.trim().lowercase()
        val otp = state.otpCode.trim()
        if (otp.length != 6) return

        viewModelScope.launch {
            _uiState.update { it.copy(otpVerifying = true, otpError = null) }
            try {
                val body = buildJsonObject {
                    put("email", email)
                    put("otp_code", otp)
                }
                val result = edgeFunctionClient.invoke(
                    functionName = "verify-doctor-otp",
                    body = body,
                    anonymous = true,
                )
                when (result) {
                    is ApiResult.Success -> {
                        _uiState.update {
                            it.copy(otpVerifying = false, otpVerified = true, otpError = null)
                        }
                    }
                    else -> {
                        _uiState.update { it.copy(otpVerifying = false, otpError = parseApiError(result, "Incorrect code")) }
                    }
                }
            } catch (e: Exception) {
                Log.e("DoctorRegVM", "verifyOtp failed", e)
                _uiState.update { it.copy(otpVerifying = false, otpError = e.message ?: "Verification failed") }
            }
        }
    }

    /** Extract human-readable error from ApiResult.Error JSON body. */
    private fun parseApiError(result: ApiResult<*>, fallback: String): String {
        val rawMessage = (result as? ApiResult.Error)?.message ?: return fallback
        return try {
            val jsonObj = kotlinx.serialization.json.Json.parseToJsonElement(rawMessage)
            (jsonObj as? kotlinx.serialization.json.JsonObject)
                ?.get("error")
                ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                ?: rawMessage
        } catch (_: Exception) { rawMessage }
    }

    fun onOtpCodeChanged(code: String) {
        val filtered = code.filter { it.isDigit() }.take(6)
        _uiState.update { it.copy(otpCode = filtered, otpError = null) }
    }

    fun resendOtp() {
        if (_uiState.value.resendCooldown > 0) return
        sendOtp()
    }

    private fun startResendCooldown() {
        resendCooldownJob?.cancel()
        resendCooldownJob = viewModelScope.launch {
            for (i in 60 downTo 1) {
                _uiState.update { it.copy(resendCooldown = i) }
                delay(1000)
            }
            _uiState.update { it.copy(resendCooldown = 0) }
        }
    }

    fun onBiometricEnrolled() {
        _uiState.update { it.copy(biometricEnrolled = true) }
    }

    fun refreshBiometricState() {
        val available = biometricAuthManager.hasHardware()
        val enrolled = biometricAuthManager.hasEnrolledBiometrics()
        _uiState.update {
            it.copy(biometricAvailable = available && enrolled)
        }
    }

    private fun completeRegistration() {
        val state = _uiState.value
        if (state.isRegistering) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(isRegistering = true, isUploading = true, registrationError = null)
            }

            val registration = DoctorRegistration(
                email = state.email.trim(),
                password = state.password,
                fullName = state.fullName.trim(),
                countryCode = state.countryCode,
                phone = state.phone.trim(),
                specialty = state.specialty,
                customSpecialty = state.customSpecialty,
                country = state.country,
                languages = state.selectedLanguages.toList(),
                licenseNumber = state.licenseNumber.trim(),
                yearsExperience = state.yearsExperience.toIntOrNull() ?: 0,
                bio = state.bio.trim(),
                services = state.selectedServices.toList(),
                profilePhotoUri = state.profilePhotoUri?.toString(),
                licenseDocumentUri = state.licenseDocumentUri?.toString(),
                certificatesUri = state.certificatesUri?.toString(),
            )

            _uiState.update { it.copy(isUploading = false) }

            when (val result = registerDoctorUseCase(registration)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isRegistering = false, isComplete = true) }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isRegistering = false,
                            registrationError = result.exception.message
                                ?: "Registration failed",
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(registrationError = null) }
    }

    // Step 1: Account
    fun onEmailChanged(email: String) {
        _uiState.update { it.copy(email = email) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    fun onConfirmPasswordChanged(confirmPassword: String) {
        _uiState.update { it.copy(confirmPassword = confirmPassword) }
    }

    fun onPasswordVisibleToggled() {
        _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun onConfirmPasswordVisibleToggled() {
        _uiState.update { it.copy(confirmPasswordVisible = !it.confirmPasswordVisible) }
    }

    // Step 3: Profile Photo (was step 2)
    fun onProfilePhotoSelected(uri: Uri?) {
        _uiState.update { it.copy(profilePhotoUri = uri) }
    }

    // Step 4: Personal Info (was step 3)
    fun onFullNameChanged(fullName: String) {
        _uiState.update { it.copy(fullName = fullName) }
    }

    fun onPhoneChanged(phone: String) {
        // Only allow digits, max 15 characters (E.164 max)
        val filtered = phone.filter { it.isDigit() }.take(15)
        _uiState.update { it.copy(phone = filtered) }
    }

    fun onCountryCodeChanged(countryCode: String) {
        _uiState.update { it.copy(countryCode = countryCode) }
    }

    fun onSpecialtyChanged(specialty: String) {
        _uiState.update {
            it.copy(specialty = specialty, customSpecialty = "", selectedServices = emptySet())
        }
    }

    fun onCustomSpecialtyChanged(customSpecialty: String) {
        _uiState.update { it.copy(customSpecialty = customSpecialty) }
    }

    // Step 5: Location & Languages (was step 4)
    fun onCountryChanged(country: String) {
        _uiState.update { it.copy(country = country) }
    }

    fun onLanguageToggled(language: String) {
        _uiState.update { state ->
            val updated = state.selectedLanguages.toMutableSet()
            if (updated.contains(language)) updated.remove(language) else updated.add(language)
            state.copy(selectedLanguages = updated)
        }
    }

    // Step 6: Professional Details (was step 5)
    fun onLicenseNumberChanged(licenseNumber: String) {
        _uiState.update { it.copy(licenseNumber = licenseNumber) }
    }

    fun onYearsExperienceChanged(yearsExperience: String) {
        // Only allow digits, max 2 characters
        val filtered = yearsExperience.filter { it.isDigit() }.take(2)
        _uiState.update { it.copy(yearsExperience = filtered) }
    }

    fun onBioChanged(bio: String) {
        _uiState.update { it.copy(bio = bio) }
    }

    // Step 7: Services (was step 6)
    fun onServiceToggled(service: String) {
        _uiState.update { state ->
            val updated = state.selectedServices.toMutableSet()
            if (updated.contains(service)) updated.remove(service) else updated.add(service)
            state.copy(selectedServices = updated)
        }
    }

    // Step 8: Credentials (was step 7)
    fun onLicenseDocumentSelected(uri: Uri?, fileName: String? = null) {
        _uiState.update { it.copy(licenseDocumentUri = uri, licenseDocumentName = fileName) }
    }

    fun onCertificatesSelected(uri: Uri?, fileName: String? = null) {
        _uiState.update { it.copy(certificatesUri = uri, certificatesName = fileName) }
    }
}

data class DoctorRegistrationUiState(
    val currentStep: Int = 1,
    val isComplete: Boolean = false,
    val isRegistering: Boolean = false,
    val isUploading: Boolean = false,
    val registrationError: String? = null,
    // Step 1: Account
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val passwordVisible: Boolean = false,
    val confirmPasswordVisible: Boolean = false,
    // Step 2: OTP Verification
    val otpCode: String = "",
    val otpSent: Boolean = false,
    val otpVerified: Boolean = false,
    val otpSending: Boolean = false,
    val otpVerifying: Boolean = false,
    val otpError: String? = null,
    val resendCooldown: Int = 0,
    // Step 3: Profile Photo
    val profilePhotoUri: Uri? = null,
    // Step 4: Personal Info
    val fullName: String = "",
    val countryCode: String = "+255",
    val phone: String = "",
    val specialty: String = "",
    val customSpecialty: String = "",
    // Step 5: Location & Languages
    val country: String = "Tanzania",
    val selectedLanguages: Set<String> = emptySet(),
    // Step 6: Professional Details
    val licenseNumber: String = "",
    val yearsExperience: String = "",
    val bio: String = "",
    // Step 7: Services
    val selectedServices: Set<String> = emptySet(),
    // Step 8: Credentials
    val licenseDocumentUri: Uri? = null,
    val licenseDocumentName: String? = null,
    val certificatesUri: Uri? = null,
    val certificatesName: String? = null,
    // Step 9: Biometric
    val biometricAvailable: Boolean = true,
    val biometricEnrolled: Boolean = false,
    val deviceAlreadyBound: Boolean = false,
) {
    val totalSteps: Int get() = 8

    val isCurrentStepValid: Boolean
        get() = when (currentStep) {
            1 -> EMAIL_REGEX.matches(email.trim()) &&
                password.length >= 8 &&
                password.any { it.isUpperCase() } &&
                password.any { it.isDigit() } &&
                confirmPassword == password
            2 -> true // photo is optional
            3 -> fullName.trim().length in 2..100 &&
                phone.trim().length in 7..15 &&
                phone.trim().all { it.isDigit() } &&
                specialty.isNotBlank() &&
                (specialty != "Specialist" || customSpecialty.isNotBlank())
            4 -> selectedLanguages.isNotEmpty()
            5 -> licenseNumber.isNotBlank() &&
                bio.trim().length in 10..1000 &&
                (yearsExperience.toIntOrNull()?.let { it in 0..70 } ?: false)
            6 -> selectedServices.isNotEmpty()
            7 -> licenseDocumentUri != null
            8 -> biometricEnrolled
            else -> false
        }

    companion object {
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}
