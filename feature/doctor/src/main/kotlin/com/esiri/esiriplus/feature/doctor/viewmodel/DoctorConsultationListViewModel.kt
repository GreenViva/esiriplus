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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class DoctorConsultationListViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val consultationDao: ConsultationDao,
    private val consultationService: DoctorConsultationService,
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _dataState = authRepository.currentSession
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

    val uiState: StateFlow<DoctorConsultationListUiState> = combine(
        _dataState,
        _isRefreshing,
    ) { state, refreshing ->
        state.copy(isRefreshing = refreshing)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DoctorConsultationListUiState(),
    )

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val session = authRepository.currentSession.first()
                val id = session?.user?.id ?: return@launch
                doSync(id)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private fun syncFromBackend(doctorId: String) {
        viewModelScope.launch { doSync(doctorId) }
    }

    private suspend fun doSync(doctorId: String) {
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
            parentConsultationId = parentConsultationId,
        )
    }

    companion object {
        private const val TAG = "DoctorConsListVM"
    }
}
