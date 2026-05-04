package com.esiri.esiriplus.feature.patient.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.database.dao.ConsultationDao
import com.esiri.esiriplus.core.database.dao.NotificationDao
import com.esiri.esiriplus.core.database.dao.PatientSessionDao
import com.esiri.esiriplus.core.database.entity.NotificationEntity
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.network.service.NotificationSyncService
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.domain.repository.DoctorRatingRepository
import com.esiri.esiriplus.core.domain.repository.PatientReportRepository
import com.esiri.esiriplus.core.domain.usecase.DeletePatientAccountUseCase
import com.esiri.esiriplus.core.domain.usecase.LogoutUseCase
import com.esiri.esiriplus.core.domain.usecase.SubmitDeletionFeedbackUseCase
import com.esiri.esiriplus.core.database.entity.ConsultationEntity
import com.esiri.esiriplus.core.network.service.LocationResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PatientHomeUiState(
    val patientId: String = "",
    val maskedPatientId: String = "",
    val soundsEnabled: Boolean = true,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val activeConsultation: ConsultationEntity? = null,
    val pendingRatingConsultation: ConsultationEntity? = null,
    val ongoingConsultations: List<ConsultationEntity> = emptyList(),
    val hasUnreadReports: Boolean = false,
    val isDeletingAccount: Boolean = false,
    /** Active count from the missed-consultations RPC; 0 hides the dot. */
    val missedCount: Int = 0,
)

// TODO: Localize hardcoded user-facing strings (error messages).
//  Inject Application context and use context.getString(R.string.xxx) from feature.patient.R
@HiltViewModel
class PatientHomeViewModel @Inject constructor(
    private val application: Application,
    private val authRepository: AuthRepository,
    private val logoutUseCase: LogoutUseCase,
    private val deletePatientAccountUseCase: DeletePatientAccountUseCase,
    private val submitDeletionFeedbackUseCase: SubmitDeletionFeedbackUseCase,
    private val consultationDao: ConsultationDao,
    private val patientSessionDao: PatientSessionDao,
    private val notificationDao: NotificationDao,
    private val notificationSyncService: NotificationSyncService,
    private val doctorRatingRepository: DoctorRatingRepository,
    private val patientReportRepository: PatientReportRepository,
    private val locationResolver: LocationResolver,
    private val supabaseApi: com.esiri.esiriplus.core.network.api.SupabaseApi,
    private val tokenManager: com.esiri.esiriplus.core.network.TokenManager,
) : ViewModel() {

    private val prefs = application.getSharedPreferences("patient_prefs", Context.MODE_PRIVATE)
    private val reportPrefs = application.getSharedPreferences(REPORT_PREFS_NAME, Context.MODE_PRIVATE)
    private val _soundsEnabled = MutableStateFlow(prefs.getBoolean(KEY_SOUNDS_ENABLED, true))
    private val _pendingRating = MutableStateFlow<ConsultationEntity?>(null)
    private val _isRefreshing = MutableStateFlow(false)
    private val _hasUnreadReports = MutableStateFlow(false)
    private val _isDeletingAccount = MutableStateFlow(false)
    private val _missedCount = MutableStateFlow(0)

    init {
        checkPendingRatings()
        syncUnsyncedRatings()
        checkUnreadReports()
        backfillLocationIfMissing()
        refreshMissedCount()
        syncNotifications()
    }

    /**
     * One-shot pull of recent notifications from Supabase into Room so the
     * bell badge reflects server state immediately on home load — without
     * waiting for the user to open the notifications list.
     */
    private fun syncNotifications() {
        viewModelScope.launch {
            val sessionId = patientSessionDao.getSession().first()?.sessionId ?: return@launch
            try {
                when (val r = notificationSyncService.fetchRecentNotifications(sessionId)) {
                    is ApiResult.Success -> {
                        val entities = r.data.map { row ->
                            NotificationEntity(
                                notificationId = row.notificationId,
                                userId = row.userId,
                                title = row.title,
                                body = row.body,
                                type = row.type,
                                data = row.data,
                                readAt = row.readAt?.let {
                                    try { java.time.Instant.parse(it).toEpochMilli() } catch (_: Exception) { null }
                                },
                                createdAt = try { java.time.Instant.parse(row.createdAt).toEpochMilli() }
                                    catch (_: Exception) { System.currentTimeMillis() },
                            )
                        }
                        if (entities.isNotEmpty()) notificationDao.insertAll(entities)
                    }
                    else -> Log.w(TAG, "Notification sync skipped: $r")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Notification sync failed", e)
            }
        }
    }

    /**
     * Refresh the session's location on every app start (if permission is
     * granted). LocationResolver re-runs GPS + reverse-geocode + server
     * canonicalisation, so any stale or inconsistent cached hierarchy
     * (e.g. a ward that doesn't belong to the stored district) gets
     * overwritten with the canonical tuple. Non-fatal on failure.
     */
    private fun backfillLocationIfMissing() {
        viewModelScope.launch {
            // Seed patient_sessions row if it's missing — existing patients
            // who logged in before AuthRepositoryImpl started inserting the
            // row won't have one, and LocationResolver bails when it can't
            // find a session.
            seedPatientSessionRowIfMissing()

            val granted = ContextCompat.checkSelfPermission(
                application,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return@launch
            try {
                locationResolver.resolveAndPersist()
            } catch (e: Exception) {
                Log.w(TAG, "Location refresh failed (non-fatal)", e)
            }
        }
    }

    private suspend fun seedPatientSessionRowIfMissing() {
        if (patientSessionDao.getSession().first() != null) return
        val authSession = authRepository.currentSession.first() ?: return
        val now = System.currentTimeMillis()
        patientSessionDao.insert(
            com.esiri.esiriplus.core.database.entity.PatientSessionEntity(
                sessionId = authSession.user.id,
                sessionTokenHash = authSession.accessToken.hashCode().toString(),
                createdAt = now,
                updatedAt = now,
            ),
        )
        Log.d(TAG, "Seeded missing patient_sessions row for ${authSession.user.id}")
    }

    /**
     * Called by the location permission gate the moment the patient grants
     * access. Fires LocationResolver immediately so the session has a
     * resolved tuple by the time the patient navigates anywhere.
     */
    fun onLocationGranted() {
        viewModelScope.launch {
            try {
                locationResolver.resolveAndPersist()
            } catch (e: Exception) {
                Log.w(TAG, "Location resolve after grant failed", e)
            }
        }
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

    /** Unread notification count keyed on the patient's session_id. */
    val unreadNotificationCount: StateFlow<Int> = patientSessionDao.getSession()
        .flatMapLatest { session ->
            if (session != null) notificationDao.getUnreadCount(session.sessionId) else flowOf(0)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val uiState: StateFlow<PatientHomeUiState> = combine(
        authRepository.currentSession,
        _soundsEnabled,
        consultationDao.getActiveConsultation(),
        _pendingRating,
        combine(ongoingConsultations, _isRefreshing, _hasUnreadReports) { ongoing, refreshing, unread -> Triple(ongoing, refreshing, unread) },
        _isDeletingAccount,
        _missedCount,
    ) { values: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val session = values[0] as com.esiri.esiriplus.core.domain.model.Session?
        @Suppress("UNCHECKED_CAST")
        val soundsEnabled = values[1] as Boolean
        @Suppress("UNCHECKED_CAST")
        val activeConsultation = values[2] as ConsultationEntity?
        @Suppress("UNCHECKED_CAST")
        val pendingRating = values[3] as ConsultationEntity?
        @Suppress("UNCHECKED_CAST")
        val triple = values[4] as Triple<List<ConsultationEntity>, Boolean, Boolean>
        val (ongoing, refreshing, hasUnread) = triple
        @Suppress("UNCHECKED_CAST")
        val deleting = values[5] as Boolean
        @Suppress("UNCHECKED_CAST")
        val missed = values[6] as Int
        if (session != null) {
            val id = session.user.id
            PatientHomeUiState(
                patientId = id,
                maskedPatientId = maskPatientId(id),
                soundsEnabled = soundsEnabled,
                isLoading = false,
                isRefreshing = refreshing,
                activeConsultation = activeConsultation,
                pendingRatingConsultation = pendingRating,
                ongoingConsultations = ongoing,
                hasUnreadReports = hasUnread,
                isDeletingAccount = deleting,
                missedCount = missed,
            )
        } else {
            PatientHomeUiState(isLoading = false, isDeletingAccount = deleting)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PatientHomeUiState(),
    )

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            checkPendingRatings()
            syncUnsyncedRatings()
            checkUnreadReports()
            refreshMissedCount()
            // Allow the combined flow to re-emit with updated data
            kotlinx.coroutines.delay(500)
            _isRefreshing.value = false
        }
    }

    /**
     * Refreshes the missed-bucket badge count. Called on pull-to-refresh and
     * once on first emission of the home screen state. Failures are silent
     * (the badge just stays at its previous value).
     */
    fun refreshMissedCount() {
        viewModelScope.launch {
            try {
                val sessionId = sessionIdFromToken() ?: return@launch
                val resp = supabaseApi.listMissedConsultations(
                    com.esiri.esiriplus.core.network.api.model.ListMissedRpcBody(sessionId),
                )
                if (resp.isSuccessful) {
                    _missedCount.value = resp.body()?.size ?: 0
                }
            } catch (e: Exception) {
                Log.w(TAG, "refreshMissedCount failed", e)
            }
        }
    }

    private fun sessionIdFromToken(): String? {
        val token = tokenManager.getAccessTokenSync() ?: return null
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null
            val payload = String(
                android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING),
            )
            org.json.JSONObject(payload).optString("session_id", null)
        } catch (_: Exception) {
            null
        }
    }

    private fun checkUnreadReports() {
        viewModelScope.launch {
            try {
                val reports = patientReportRepository.fetchReportsFromServer()
                val readIds = reportPrefs.getStringSet(KEY_READ_REPORT_IDS, emptySet()) ?: emptySet()
                _hasUnreadReports.value = reports.any { it.reportId !in readIds }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to check unread reports: ${e.message}")
            }
        }
    }

    fun markAllReportsRead() {
        viewModelScope.launch {
            try {
                val reports = patientReportRepository.fetchReportsFromServer()
                val allIds = reports.map { it.reportId }.toSet()
                reportPrefs.edit().putStringSet(KEY_READ_REPORT_IDS, allIds).apply()
                _hasUnreadReports.value = false
            } catch (e: Exception) {
                Log.w(TAG, "Failed to mark all reports read: ${e.message}")
            }
        }
    }

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

    /** Optional feedback is submitted first (best-effort) while the JWT is
     *  still valid, then the account is marked for deletion + local logout
     *  runs. Empty `reasons` and blank `comment` skips the feedback POST.
     *  Drives `isDeletingAccount` so the UI can show a blocking overlay
     *  during the ~2-3 s of network + local-cleanup work. */
    fun deleteAccount(reasons: List<String> = emptyList(), comment: String? = null) {
        viewModelScope.launch {
            _isDeletingAccount.value = true
            try {
                submitDeletionFeedbackUseCase(reasons, comment)
                deletePatientAccountUseCase()
            } finally {
                _isDeletingAccount.value = false
            }
        }
    }

    companion object {
        private const val TAG = "PatientHomeVM"
        private const val KEY_SOUNDS_ENABLED = "sounds_enabled"
        private const val REPORT_PREFS_NAME = "report_read_prefs"
        private const val KEY_READ_REPORT_IDS = "read_report_ids"

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
