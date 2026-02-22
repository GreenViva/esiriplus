package com.esiri.esiriplus.feature.patient.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.domain.usecase.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PatientHomeUiState(
    val patientId: String = "",
    val maskedPatientId: String = "",
    val languageDisplayName: String = "English",
    val soundsEnabled: Boolean = true,
    val isLoading: Boolean = true,
)

@HiltViewModel
class PatientHomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val logoutUseCase: LogoutUseCase,
) : ViewModel() {

    private val _soundsEnabled = MutableStateFlow(true)

    val uiState: StateFlow<PatientHomeUiState> = combine(
        authRepository.currentSession,
        _soundsEnabled,
    ) { session, soundsEnabled ->
        if (session != null) {
            val id = session.user.id
            PatientHomeUiState(
                patientId = id,
                maskedPatientId = maskPatientId(id),
                languageDisplayName = "English",
                soundsEnabled = soundsEnabled,
                isLoading = false,
            )
        } else {
            PatientHomeUiState(isLoading = false)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PatientHomeUiState(),
    )

    fun toggleSounds() {
        _soundsEnabled.value = !_soundsEnabled.value
    }

    fun logout() {
        viewModelScope.launch { logoutUseCase() }
    }

    companion object {
        /** Keeps prefix + last segment, masks the middle. e.g. "ESR-ABCDEF-P8FP" → "ESR-******-P8FP" */
        internal fun maskPatientId(id: String): String {
            val parts = id.split("-")
            if (parts.size < 3) {
                // Short ID — mask all but first 3 and last 4 chars
                return if (id.length <= 7) id
                else id.take(3) + "*".repeat(id.length - 7) + id.takeLast(4)
            }
            val first = parts.first()
            val last = parts.last()
            val middleMasked = parts.drop(1).dropLast(1).joinToString("-") { "*".repeat(it.length) }
            return "$first-$middleMasked-$last"
        }
    }
}
