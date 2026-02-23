package com.esiri.esiriplus.feature.auth.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.DoctorRegistration
import com.esiri.esiriplus.core.domain.usecase.RegisterDoctorUseCase
import com.esiri.esiriplus.feature.auth.biometric.BiometricAuthManager
import com.esiri.esiriplus.feature.auth.biometric.DeviceBindingManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DoctorRegistrationViewModel @Inject constructor(
    private val registerDoctorUseCase: RegisterDoctorUseCase,
    val biometricAuthManager: BiometricAuthManager,
    private val deviceBindingManager: DeviceBindingManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DoctorRegistrationUiState())
    val uiState: StateFlow<DoctorRegistrationUiState> = _uiState.asStateFlow()

    init {
        // Check biometric availability and device binding status on creation
        val biometricAvailable = biometricAuthManager.isAvailable()
        val boundDoctorId = deviceBindingManager.getBoundDoctorId()
        val deviceAlreadyBound = boundDoctorId != null
        _uiState.update {
            it.copy(
                biometricAvailable = biometricAvailable,
                deviceAlreadyBound = deviceAlreadyBound,
            )
        }
    }

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

    // Step 2: Profile Photo
    fun onProfilePhotoSelected(uri: Uri?) {
        _uiState.update { it.copy(profilePhotoUri = uri) }
    }

    // Step 3: Personal Info
    fun onFullNameChanged(fullName: String) {
        _uiState.update { it.copy(fullName = fullName) }
    }

    fun onPhoneChanged(phone: String) {
        _uiState.update { it.copy(phone = phone) }
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

    // Step 4: Location & Languages
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

    // Step 5: Professional Details
    fun onLicenseNumberChanged(licenseNumber: String) {
        _uiState.update { it.copy(licenseNumber = licenseNumber) }
    }

    fun onYearsExperienceChanged(yearsExperience: String) {
        _uiState.update { it.copy(yearsExperience = yearsExperience) }
    }

    fun onBioChanged(bio: String) {
        _uiState.update { it.copy(bio = bio) }
    }

    // Step 6: Services
    fun onServiceToggled(service: String) {
        _uiState.update { state ->
            val updated = state.selectedServices.toMutableSet()
            if (updated.contains(service)) updated.remove(service) else updated.add(service)
            state.copy(selectedServices = updated)
        }
    }

    // Step 7: Credentials
    fun onLicenseDocumentSelected(uri: Uri?) {
        _uiState.update { it.copy(licenseDocumentUri = uri) }
    }

    fun onCertificatesSelected(uri: Uri?) {
        _uiState.update { it.copy(certificatesUri = uri) }
    }
}

data class DoctorRegistrationUiState(
    val currentStep: Int = 1,
    val isComplete: Boolean = false,
    val isRegistering: Boolean = false,
    val isUploading: Boolean = false,
    val registrationError: String? = null,
    // Step 1
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val passwordVisible: Boolean = false,
    val confirmPasswordVisible: Boolean = false,
    // Step 2
    val profilePhotoUri: Uri? = null,
    // Step 3
    val fullName: String = "",
    val countryCode: String = "+255",
    val phone: String = "",
    val specialty: String = "",
    val customSpecialty: String = "",
    // Step 4
    val country: String = "Tanzania",
    val selectedLanguages: Set<String> = emptySet(),
    // Step 5
    val licenseNumber: String = "",
    val yearsExperience: String = "",
    val bio: String = "",
    // Step 6
    val selectedServices: Set<String> = emptySet(),
    // Step 7
    val licenseDocumentUri: Uri? = null,
    val certificatesUri: Uri? = null,
    // Step 8: Biometric
    val biometricAvailable: Boolean = true,
    val biometricEnrolled: Boolean = false,
    val deviceAlreadyBound: Boolean = false,
) {
    val isCurrentStepValid: Boolean
        get() = when (currentStep) {
            1 -> email.contains("@") && email.contains(".") &&
                password.length >= 8 && confirmPassword == password
            2 -> true // photo is optional
            3 -> fullName.isNotBlank() && phone.isNotBlank() && specialty.isNotBlank() &&
                (specialty != "Specialist" || customSpecialty.isNotBlank())
            4 -> selectedLanguages.isNotEmpty()
            5 -> licenseNumber.isNotBlank() && bio.isNotBlank()
            6 -> selectedServices.isNotEmpty()
            7 -> licenseDocumentUri != null
            8 -> biometricEnrolled
            else -> false
        }
}
