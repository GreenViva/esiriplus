package com.esiri.esiriplus.feature.patient.viewmodel

import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.database.dao.ConsultationDao
import com.esiri.esiriplus.core.database.dao.DoctorProfileDao
import com.esiri.esiriplus.core.database.entity.ConsultationEntity
import com.esiri.esiriplus.core.network.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

data class OngoingConsultationItem(
    val consultation: ConsultationEntity,
    val doctorName: String,
)

data class OngoingConsultationsUiState(
    val consultations: List<OngoingConsultationItem> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
)

@HiltViewModel
class OngoingConsultationsViewModel @Inject constructor(
    private val consultationDao: ConsultationDao,
    private val doctorProfileDao: DoctorProfileDao,
    private val tokenManager: TokenManager,
) : ViewModel() {

    private companion object {
        const val TAG = "OngoingConsultVM"
    }

    private val _refreshTrigger = MutableStateFlow(0)
    private val _isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<OngoingConsultationsUiState> =
        combine(
            _refreshTrigger.flatMapLatest {
                flow { emit(getSessionIdFromToken()) }
                    .flatMapLatest { sessionId ->
                        if (sessionId != null) {
                            Log.d(TAG, "Querying ongoing consultations for sessionId=$sessionId")
                            consultationDao.getOngoingConsultationsForPatient(
                                patientSessionId = sessionId,
                                currentTimeMillis = System.currentTimeMillis(),
                            )
                        } else {
                            Log.w(TAG, "No session ID from token — returning empty list")
                            flowOf(emptyList())
                        }
                    }
                    .map { list ->
                        Log.d(TAG, "Ongoing consultations count=${list.size}")
                        list.map { consultation ->
                            val doctorName = if (consultation.doctorId.isNotBlank()) {
                                doctorProfileDao.getById(consultation.doctorId)?.fullName ?: "Doctor"
                            } else "Doctor"
                            OngoingConsultationItem(consultation = consultation, doctorName = doctorName)
                        }
                    }
            },
            _isRefreshing,
        ) { items, refreshing ->
            OngoingConsultationsUiState(consultations = items, isLoading = false, isRefreshing = refreshing)
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = OngoingConsultationsUiState(),
            )

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _refreshTrigger.value++
            kotlinx.coroutines.delay(500)
            _isRefreshing.value = false
        }
    }

    private fun getSessionIdFromToken(): String? {
        val token = tokenManager.getAccessTokenSync() ?: return null
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING))
            JSONObject(payload).optString("session_id", null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode session_id from JWT", e)
            null
        }
    }
}
