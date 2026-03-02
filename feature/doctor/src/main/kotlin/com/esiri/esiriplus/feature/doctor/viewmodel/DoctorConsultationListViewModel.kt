package com.esiri.esiriplus.feature.doctor.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.database.dao.ConsultationDao
import com.esiri.esiriplus.core.database.entity.ConsultationEntity
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.network.service.ConsultationRow
import com.esiri.esiriplus.core.network.service.DoctorConsultationService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class DoctorConsultationListUiState(
    val consultations: List<ConsultationEntity> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class DoctorConsultationListViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val consultationDao: ConsultationDao,
    private val consultationService: DoctorConsultationService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DoctorConsultationListUiState())
    val uiState: StateFlow<DoctorConsultationListUiState> = _uiState.asStateFlow()

    private var doctorId: String = ""

    init {
        loadConsultations()
    }

    private fun loadConsultations() {
        viewModelScope.launch {
            val session = authRepository.currentSession.first()
            doctorId = session?.user?.id ?: return@launch

            // Observe local DB
            consultationDao.getByDoctorId(doctorId)
                .onEach { consultations ->
                    _uiState.update {
                        it.copy(
                            consultations = consultations.sortedByDescending { c -> c.createdAt },
                            isLoading = false,
                        )
                    }
                }
                .launchIn(viewModelScope)

            // Sync from backend
            syncFromBackend()
        }
    }

    private suspend fun syncFromBackend() {
        when (val result = consultationService.getConsultationsForDoctor(doctorId)) {
            is ApiResult.Success -> {
                val entities = result.data.map { it.toEntity() }
                if (entities.isNotEmpty()) {
                    consultationDao.insertAll(entities)
                }
            }
            is ApiResult.Error -> {
                Log.w(TAG, "Failed to sync consultations: ${result.message}")
                // Only show error if local DB is also empty
                if (_uiState.value.consultations.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to load consultations",
                        )
                    }
                }
            }
            else -> { /* no-op */ }
        }
    }

    private fun ConsultationRow.toEntity(): ConsultationEntity {
        fun parseTimestamp(iso: String?): Long? =
            iso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

        val now = System.currentTimeMillis()
        return ConsultationEntity(
            consultationId = consultationId,
            patientSessionId = patientSessionId,
            doctorId = doctorId,
            status = status,
            serviceType = serviceType,
            consultationFee = consultationFee,
            sessionStartTime = parseTimestamp(sessionStartTime),
            sessionEndTime = parseTimestamp(sessionEndTime),
            sessionDurationMinutes = sessionDurationMinutes,
            requestExpiresAt = parseTimestamp(requestExpiresAt) ?: now,
            scheduledEndAt = parseTimestamp(scheduledEndAt),
            extensionCount = extensionCount,
            gracePeriodEndAt = parseTimestamp(gracePeriodEndAt),
            originalDurationMinutes = originalDurationMinutes,
            createdAt = parseTimestamp(createdAt) ?: now,
            updatedAt = parseTimestamp(updatedAt) ?: now,
        )
    }

    companion object {
        private const val TAG = "DoctorConsListVM"
    }
}
