package com.esiri.esiriplus.feature.patient.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.database.dao.PatientProfileDao
import com.esiri.esiriplus.core.database.entity.PatientProfileEntity
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PatientProfileUiState(
    val sex: String = "",
    val ageGroup: String = "",
    val bloodType: String = "",
    val allergies: String = "",
    val chronicConditions: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
)

@HiltViewModel
class PatientProfileViewModel @Inject constructor(
    private val patientProfileDao: PatientProfileDao,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PatientProfileUiState())
    val uiState: StateFlow<PatientProfileUiState> = _uiState.asStateFlow()

    private var userId: String = ""

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            val session = authRepository.currentSession.firstOrNull()
            userId = session?.user?.id ?: return@launch

            val profile = patientProfileDao.getByUserId(userId).firstOrNull()
            _uiState.update {
                it.copy(
                    sex = profile?.sex ?: "",
                    ageGroup = profile?.ageGroup ?: "",
                    bloodType = profile?.bloodGroup ?: "",
                    allergies = profile?.allergies ?: "",
                    chronicConditions = profile?.chronicConditions ?: "",
                    isLoading = false,
                )
            }
        }
    }

    fun onSexChanged(sex: String) {
        _uiState.update { it.copy(sex = sex) }
    }

    fun onAgeGroupChanged(ageGroup: String) {
        _uiState.update { it.copy(ageGroup = ageGroup) }
    }

    fun onBloodTypeChanged(bloodType: String) {
        _uiState.update { it.copy(bloodType = bloodType) }
    }

    fun onAllergiesChanged(allergies: String) {
        _uiState.update { it.copy(allergies = allergies) }
    }

    fun onChronicConditionsChanged(chronicConditions: String) {
        _uiState.update { it.copy(chronicConditions = chronicConditions) }
    }

    fun saveProfile() {
        if (userId.isBlank()) return
        val state = _uiState.value

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val profile = PatientProfileEntity(
                id = userId,
                userId = userId,
                bloodGroup = state.bloodType.ifBlank { null },
                allergies = state.allergies.ifBlank { null },
                sex = state.sex.ifBlank { null },
                ageGroup = state.ageGroup.ifBlank { null },
                chronicConditions = state.chronicConditions.ifBlank { null },
            )
            patientProfileDao.insert(profile)
            _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
        }
    }
}
