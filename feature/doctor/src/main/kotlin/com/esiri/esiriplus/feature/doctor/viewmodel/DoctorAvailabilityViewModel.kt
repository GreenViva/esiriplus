package com.esiri.esiriplus.feature.doctor.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.network.SupabaseClientProvider
import com.esiri.esiriplus.core.network.TokenManager
import com.esiri.esiriplus.core.network.service.AvailabilitySlotRow
import com.esiri.esiriplus.core.network.service.DoctorAvailabilityService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DoctorAvailabilityUiState(
    val slots: List<AvailabilitySlotRow> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val maxAppointmentsPerDay: Int = 10,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    // Add slot form
    val showAddDialog: Boolean = false,
    val editDayOfWeek: Int = 1, // Monday default
    val editStartTime: String = "08:00",
    val editEndTime: String = "17:00",
    val editBufferMinutes: Int = 5,
)

private val DAY_NAMES = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

@HiltViewModel
class DoctorAvailabilityViewModel @Inject constructor(
    private val availabilityService: DoctorAvailabilityService,
    private val authRepository: AuthRepository,
    private val supabaseClientProvider: SupabaseClientProvider,
    private val tokenManager: TokenManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DoctorAvailabilityUiState())
    val uiState: StateFlow<DoctorAvailabilityUiState> = _uiState.asStateFlow()

    init {
        ensureAuthAndLoad()
    }

    private fun ensureAuthAndLoad() {
        viewModelScope.launch {
            try {
                val session = authRepository.currentSession.first()
                if (session != null) {
                    val freshAccess = tokenManager.getAccessTokenSync() ?: session.accessToken
                    val freshRefresh = tokenManager.getRefreshTokenSync() ?: session.refreshToken
                    supabaseClientProvider.importAuthToken(freshAccess, freshRefresh)
                    Log.d(TAG, "importAuthToken succeeded for availability")
                }
            } catch (e: Exception) {
                Log.e(TAG, "importAuthToken FAILED", e)
            }
            loadSlots()
        }
    }

    fun loadSlots() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val doctorId = authRepository.currentSession.first()?.user?.id ?: return@launch
                val slots = availabilityService.getSlots(doctorId)

                _uiState.update {
                    it.copy(
                        slots = slots.sortedWith(compareBy({ it.dayOfWeek }, { it.startTime })),
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load availability slots", e)
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Failed to load availability")
                }
            }
        }
    }

    fun showAddDialog() {
        _uiState.update { it.copy(showAddDialog = true) }
    }

    fun dismissAddDialog() {
        _uiState.update { it.copy(showAddDialog = false) }
    }

    fun updateEditDay(day: Int) {
        _uiState.update { it.copy(editDayOfWeek = day) }
    }

    fun updateEditStartTime(time: String) {
        _uiState.update { it.copy(editStartTime = time) }
    }

    fun updateEditEndTime(time: String) {
        _uiState.update { it.copy(editEndTime = time) }
    }

    fun updateEditBufferMinutes(minutes: Int) {
        _uiState.update { it.copy(editBufferMinutes = minutes) }
    }

    fun saveSlot() {
        val state = _uiState.value
        _uiState.update { it.copy(isSaving = true, showAddDialog = false) }
        viewModelScope.launch {
            try {
                val doctorId = authRepository.currentSession.first()?.user?.id ?: return@launch

                Log.d(TAG, "Inserting slot: doctor=$doctorId day=${state.editDayOfWeek} ${state.editStartTime}-${state.editEndTime}")
                availabilityService.insertSlot(
                    AvailabilitySlotRow(
                        doctorId = doctorId,
                        dayOfWeek = state.editDayOfWeek,
                        startTime = state.editStartTime,
                        endTime = state.editEndTime,
                        bufferMinutes = state.editBufferMinutes,
                    ),
                )
                Log.d(TAG, "Slot inserted successfully")

                _uiState.update {
                    it.copy(isSaving = false, successMessage = "Slot added")
                }
                loadSlots()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save slot", e)
                _uiState.update {
                    it.copy(isSaving = false, errorMessage = "Failed to save slot: ${e.message}")
                }
            }
        }
    }

    fun deleteSlot(slotId: String) {
        viewModelScope.launch {
            try {
                availabilityService.deleteSlot(slotId)
                loadSlots()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete slot", e)
                _uiState.update { it.copy(errorMessage = "Failed to delete slot") }
            }
        }
    }

    fun dismissMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    companion object {
        private const val TAG = "DoctorAvailabilityVM"
        val dayNames = DAY_NAMES
    }
}
