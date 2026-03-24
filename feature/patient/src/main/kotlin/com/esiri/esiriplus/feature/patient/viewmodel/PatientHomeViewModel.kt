package com.esiri.esiriplus.feature.patient.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.database.dao.ConsultationDao
import com.esiri.esiriplus.core.database.dao.PatientSessionDao
import com.esiri.esiriplus.core.database.entity.ConsultationEntity
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.domain.repository.DoctorRatingRepository
import com.esiri.esiriplus.core.domain.usecase.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PatientHomeUiState(
    val patientId: String = "",
    val maskedPatientId: String = "",
    val soundsEnabled: Boolean = true,
    val isLoading: Boolean = true,
    val activeConsultation: ConsultationEntity? = null,
    val pendingRatingConsultation: ConsultationEntity? = null,
    val ongoingConsultations: List<ConsultationEntity> = emptyList(),
)

// TODO: Localize hardcoded user-facing strings (error messages).
//  Inject Application context and use context.getString(R.string.xxx) from feature.patient.R
@HiltViewModel
class PatientHomeViewModel @Inject constructor(
    private val application: Application,
    private val authRepository: AuthRepository,
    private val logoutUseCase: LogoutUseCase,
    private val consultationDao: ConsultationDao,
    private val patientSessionDao: PatientSessionDao,
    private val doctorRatingRepository: DoctorRatingRepository,
) : ViewModel() {

    private val prefs = application.getSharedPreferences("patient_prefs", android.content.Context.MODE_PRIVATE)
    private val _soundsEnabled = MutableStateFlow(prefs.getBoolean(KEY_SOUNDS_ENABLED, true))
    private val _pendingRating = MutableStateFlow<ConsultationEntity?>(null)

    init {
        checkPendingRatings()
        syncUnsyncedRatings()
    }

    private val ongoingConsultations = patientSessionDao.getSession().flatMapLatest { session ->
        if (session != null) {
            consultationDao.getOngoingConsultationsForPatient(
                patientSessionId = session.sessionId,
                currentTimeMillis = System.currentTimeMillis(),
            )
        } else {
            flowOf(emptyList())
        }
    }

    val uiState: StateFlow<PatientHomeUiState> = combine(
        authRepository.currentSession,
        _soundsEnabled,
        consultationDao.getActiveConsultation(),
        _pendingRating,
        ongoingConsultations,
    ) { session, soundsEnabled, activeConsultation, pendingRating, ongoing ->
        if (session != null) {
            val id = session.user.id
            PatientHomeUiState(
                patientId = id,
                maskedPatientId = maskPatientId(id),
                soundsEnabled = soundsEnabled,
                isLoading = false,
                activeConsultation = activeConsultation,
                pendingRatingConsultation = pendingRating,
                ongoingConsultations = ongoing,
            )
        } else {
            PatientHomeUiState(isLoading = false)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PatientHomeUiState(),
    )

    fun dismissPendingRating() {
        _pendingRating.value = null
    }

    fun clearPendingRating() {
        _pendingRating.value = null
    }

    private fun checkPendingRatings() {
        viewModelScope.launch {
            try {
                val session = patientSessionDao.getSession().first { it != null } ?: return@launch
                val unrated = consultationDao.getUnratedCompletedConsultation(session.sessionId)
                _pendingRating.value = unrated
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check pending ratings", e)
            }
        }
    }

    private fun syncUnsyncedRatings() {
        viewModelScope.launch {
            try {
                val unsynced = doctorRatingRepository.getUnsyncedRatings()
                for (rating in unsynced) {
                    val synced = doctorRatingRepository.submitRatingToServer(rating)
                    if (synced) {
                        doctorRatingRepository.markSynced(rating.ratingId)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync unsynced ratings", e)
            }
        }
    }

    fun toggleSounds() {
        val newValue = !_soundsEnabled.value
        _soundsEnabled.value = newValue
        prefs.edit().putBoolean(KEY_SOUNDS_ENABLED, newValue).apply()
    }

    fun logout() {
        viewModelScope.launch { logoutUseCase() }
    }

    companion object {
        private const val TAG = "PatientHomeVM"
        private const val KEY_SOUNDS_ENABLED = "sounds_enabled"

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
