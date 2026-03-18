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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DoctorConsultationListUiState> = authRepository.currentSession
        .flatMapLatest { session ->
            val id = session?.user?.id
            if (id == null) {
                flowOf(DoctorConsultationListUiState(isLoading = false))
            } else {
                // Trigger backend sync (fire-and-forget, results flow in via Room)
                syncFromBackend(id)
                consultationDao.getByDoctorId(id).map { consultations ->
                    DoctorConsultationListUiState(
                        consultations = consultations.sortedByDescending { c -> c.createdAt },
                        isLoading = false,
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DoctorConsultationListUiState(),
        )

    private fun syncFromBackend(doctorId: String) {
        viewModelScope.launch {
            when (val result = consultationService.getConsultationsForDoctor(doctorId)) {
                is ApiResult.Success -> {
                    val entities = result.data.map { it.toEntity() }
                    if (entities.isNotEmpty()) {
                        consultationDao.insertAll(entities)
                    }
                }
                is ApiResult.Error -> {
                    Log.w(TAG, "Failed to sync consultations: ${result.message}")
                }
                else -> { /* no-op */ }
            }
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
            status = status.uppercase(),
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
