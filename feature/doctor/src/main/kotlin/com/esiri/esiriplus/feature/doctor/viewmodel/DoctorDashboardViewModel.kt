package com.esiri.esiriplus.feature.doctor.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.database.dao.ConsultationDao
import com.esiri.esiriplus.core.database.dao.DoctorAvailabilityDao
import com.esiri.esiriplus.core.database.dao.DoctorEarningsDao
import com.esiri.esiriplus.core.database.dao.DoctorProfileDao
import com.esiri.esiriplus.core.database.dao.PatientSessionDao
import com.esiri.esiriplus.core.database.entity.ConsultationEntity
import com.esiri.esiriplus.core.database.entity.DoctorAvailabilityEntity
import com.esiri.esiriplus.core.database.entity.DoctorEarningsEntity
import com.esiri.esiriplus.core.database.entity.DoctorProfileEntity
import com.esiri.esiriplus.core.database.entity.PatientSessionEntity
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.domain.repository.DoctorProfileRepository
import com.esiri.esiriplus.core.domain.usecase.LogoutUseCase
import com.esiri.esiriplus.core.network.SupabaseClientProvider
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.network.service.DoctorAvailabilityService
import com.esiri.esiriplus.core.network.service.DoctorConsultationService
import com.esiri.esiriplus.core.network.service.DoctorEarningsService
import com.esiri.esiriplus.core.network.service.DoctorProfileService
import com.esiri.esiriplus.core.network.service.DoctorRealtimeService
import com.esiri.esiriplus.core.network.storage.FileUploadService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

// ─── Day schedule model ─────────────────────────────────────────────────────────

@Serializable
data class DaySchedule(
    val enabled: Boolean = false,
    val start: String = "09:00",
    val end: String = "17:00",
)

data class WeeklySchedule(
    val sunday: DaySchedule = DaySchedule(),
    val monday: DaySchedule = DaySchedule(enabled = true),
    val tuesday: DaySchedule = DaySchedule(enabled = true),
    val wednesday: DaySchedule = DaySchedule(enabled = true),
    val thursday: DaySchedule = DaySchedule(enabled = true),
    val friday: DaySchedule = DaySchedule(enabled = true),
    val saturday: DaySchedule = DaySchedule(),
) {
    fun toMap(): Map<String, DaySchedule> = mapOf(
        "Sunday" to sunday,
        "Monday" to monday,
        "Tuesday" to tuesday,
        "Wednesday" to wednesday,
        "Thursday" to thursday,
        "Friday" to friday,
        "Saturday" to saturday,
    )

    fun withDay(dayName: String, schedule: DaySchedule): WeeklySchedule = when (dayName) {
        "Sunday" -> copy(sunday = schedule)
        "Monday" -> copy(monday = schedule)
        "Tuesday" -> copy(tuesday = schedule)
        "Wednesday" -> copy(wednesday = schedule)
        "Thursday" -> copy(thursday = schedule)
        "Friday" -> copy(friday = schedule)
        "Saturday" -> copy(saturday = schedule)
        else -> this
    }
}

// ─── Earnings transaction model ─────────────────────────────────────────────────

data class EarningsTransaction(
    val id: String,
    val patientName: String,
    val amount: String,
    val date: String,
    val status: String,
)

// ─── UI State ───────────────────────────────────────────────────────────────────

data class DoctorDashboardUiState(
    val isLoading: Boolean = true,
    val doctorId: String = "",
    val doctorName: String = "",
    val specialty: String = "",
    val isVerified: Boolean = false,
    val isOnline: Boolean = false,
    val pendingRequests: Int = 0,
    val activeConsultations: Int = 0,
    val todaysEarnings: String = "TSh 0",
    val totalPatients: Int = 0,
    val acceptanceRate: String = "\u2014",
    val isAvailable: Boolean = false,
    // Consultation lists by status
    val pendingConsultations: List<ConsultationEntity> = emptyList(),
    val activeConsultationsList: List<ConsultationEntity> = emptyList(),
    val completedConsultations: List<ConsultationEntity> = emptyList(),
    val cancelledConsultations: List<ConsultationEntity> = emptyList(),
    // Availability
    val weeklySchedule: WeeklySchedule = WeeklySchedule(),
    val availabilitySaved: Boolean = false,
    // Profile (read-only fields populated from registration)
    val profilePhone: String = "",
    val profileEmail: String = "",
    val profileSpecialty: String = "",
    val profileLicenseNumber: String = "",
    val profileYearsExperience: String = "",
    val profileBio: String = "",
    val profileCountry: String = "Tanzania",
    // Editable profile fields
    val profileLanguages: List<String> = listOf("English", "Swahili"),
    val profileServices: List<String> = listOf("General Health"),
    val profileAvailableForConsultations: Boolean = true,
    val profilePhotoUrl: String? = null,
    val profileSaved: Boolean = false,
    val profileSyncFailed: Boolean = false,
    val profileUploading: Boolean = false,
    // For preserving original DB values when saving
    val registeredYearsExperience: Int = 0,
    val registrationTimestamp: Long = 0L,
    // Earnings
    val totalEarnings: String = "TSh 0",
    val thisMonthEarnings: String = "TSh 0",
    val lastMonthEarnings: String = "TSh 0",
    val pendingPayout: String = "TSh 0",
    val recentTransactions: List<EarningsTransaction> = emptyList(),
)

// ─── ViewModel ──────────────────────────────────────────────────────────────────

@HiltViewModel
class DoctorDashboardViewModel @Inject constructor(
    private val application: Application,
    private val authRepository: AuthRepository,
    private val doctorProfileRepository: DoctorProfileRepository,
    private val consultationDao: ConsultationDao,
    private val availabilityDao: DoctorAvailabilityDao,
    private val doctorProfileDao: DoctorProfileDao,
    private val doctorEarningsDao: DoctorEarningsDao,
    private val patientSessionDao: PatientSessionDao,
    private val availabilityService: DoctorAvailabilityService,
    private val consultationService: DoctorConsultationService,
    private val earningsService: DoctorEarningsService,
    private val profileService: DoctorProfileService,
    private val realtimeService: DoctorRealtimeService,
    private val fileUploadService: FileUploadService,
    private val supabaseClientProvider: SupabaseClientProvider,
    private val logoutUseCase: LogoutUseCase,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(DoctorDashboardUiState())
    val uiState: StateFlow<DoctorDashboardUiState> = _uiState.asStateFlow()

    init {
        loadDoctorProfile()
    }

    private fun loadDoctorProfile() {
        viewModelScope.launch {
            val session = authRepository.currentSession.first()
            val userId = session?.user?.id ?: return@launch

            // 1. Load local profile instantly
            val profile = doctorProfileRepository.getDoctorById(userId)
            val dbProfile = doctorProfileDao.getById(userId)
            if (profile != null) {
                val createdAt = dbProfile?.createdAt ?: System.currentTimeMillis()
                val storedYears = profile.yearsExperience
                val yearsSinceRegistration = ((System.currentTimeMillis() - createdAt) / (365.25 * 24 * 3600 * 1000)).toInt()
                val currentYearsExperience = storedYears + yearsSinceRegistration

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        doctorId = userId,
                        doctorName = profile.fullName,
                        specialty = profile.specialty.name.lowercase()
                            .replaceFirstChar { c -> c.uppercase() }
                            .replace("_", " "),
                        isVerified = profile.isVerified,
                        isOnline = profile.isAvailable,
                        isAvailable = profile.isAvailable,
                        profilePhone = profile.phone,
                        profileEmail = profile.email,
                        profileSpecialty = profile.specialty.name.lowercase()
                            .replaceFirstChar { c -> c.uppercase() }
                            .replace("_", " "),
                        profileLicenseNumber = profile.licenseNumber,
                        profileYearsExperience = currentYearsExperience.toString(),
                        profileBio = profile.bio,
                        profileCountry = dbProfile?.country?.ifBlank { "Tanzania" } ?: "Tanzania",
                        profileLanguages = profile.languages.ifEmpty { listOf("English", "Swahili") },
                        profileServices = dbProfile?.services?.ifEmpty { listOf("General Health") } ?: listOf("General Health"),
                        profileAvailableForConsultations = profile.isAvailable,
                        profilePhotoUrl = profile.profilePhotoUrl,
                        registeredYearsExperience = storedYears,
                        registrationTimestamp = createdAt,
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        doctorId = userId,
                        doctorName = session.user.fullName,
                        isVerified = session.user.isVerified,
                        profileEmail = session.user.email.orEmpty(),
                    )
                }
            }

            // 2. Start observing Room flows (will update UI reactively as data is cached)
            observeConsultations(userId)
            observeEarnings(userId)
            loadAvailability(userId)

            // 3. Authenticate with Supabase and fetch remote data
            authenticateAndFetchRemoteData(session.accessToken, session.refreshToken, userId)
        }
    }

    private fun authenticateAndFetchRemoteData(
        accessToken: String,
        refreshToken: String,
        doctorId: String,
    ) {
        viewModelScope.launch {
            try {
                supabaseClientProvider.importAuthToken(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                )
                Log.d(TAG, "Supabase auth token imported for $doctorId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import auth token", e)
                return@launch
            }

            // Fetch all remote data in parallel
            launch { fetchProfileFromSupabase(doctorId) }
            launch { fetchConsultationsFromSupabase(doctorId) }
            launch { fetchEarningsFromSupabase(doctorId) }
            launch { subscribeToRealtime(doctorId) }
        }
    }

    private suspend fun fetchProfileFromSupabase(doctorId: String) {
        when (val result = profileService.getDoctorProfile(doctorId)) {
            is ApiResult.Success -> {
                val row = result.data ?: return
                val now = System.currentTimeMillis()
                val remoteUpdatedAt = parseInstantToMillis(row.updatedAt) ?: now
                val localProfile = doctorProfileDao.getById(doctorId)

                if (localProfile != null && localProfile.updatedAt > remoteUpdatedAt) {
                    // Local is newer — re-push local editable fields to Supabase
                    Log.d(TAG, "Local profile is newer, re-syncing to Supabase")
                    try {
                        fileUploadService.updateDoctorEditableFields(
                            doctorId = doctorId,
                            languages = localProfile.languages,
                            services = localProfile.services,
                            isAvailable = localProfile.isAvailable,
                            profilePhotoUrl = localProfile.profilePhotoUrl,
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to re-sync local profile to Supabase", e)
                    }
                    return
                }

                val createdAtMillis = parseInstantToMillis(row.createdAt) ?: now
                val entity = DoctorProfileEntity(
                    doctorId = row.doctorId,
                    fullName = row.fullName,
                    email = row.email,
                    phone = row.phone,
                    specialty = row.specialty,
                    specialistField = row.specialistField,
                    languages = row.languages.ifEmpty { localProfile?.languages ?: emptyList() },
                    bio = row.bio,
                    licenseNumber = row.licenseNumber,
                    yearsExperience = row.yearsExperience,
                    profilePhotoUrl = row.profilePhotoUrl ?: localProfile?.profilePhotoUrl,
                    averageRating = row.averageRating,
                    totalRatings = row.totalRatings,
                    isVerified = row.isVerified,
                    isAvailable = row.isAvailable,
                    services = row.services.ifEmpty { localProfile?.services ?: emptyList() },
                    countryCode = row.countryCode,
                    country = row.country,
                    licenseDocumentUrl = row.licenseDocumentUrl,
                    certificatesUrl = row.certificatesUrl,
                    createdAt = createdAtMillis,
                    updatedAt = remoteUpdatedAt,
                )
                doctorProfileDao.insert(entity)
                Log.d(TAG, "Cached profile from Supabase for $doctorId")
            }
            else -> Log.e(TAG, "Failed to fetch profile from Supabase: $result")
        }
    }

    private suspend fun fetchConsultationsFromSupabase(doctorId: String) {
        when (val result = consultationService.getConsultationsForDoctor(doctorId)) {
            is ApiResult.Success -> {
                val rows = result.data
                if (rows.isEmpty()) {
                    Log.d(TAG, "No consultations found on Supabase for $doctorId")
                    return
                }

                // Ensure FK stub PatientSessionEntity exists for each unique session
                val sessionIds = rows.map { it.patientSessionId }.distinct()
                for (sessionId in sessionIds) {
                    val existing = patientSessionDao.getById(sessionId)
                    if (existing == null) {
                        patientSessionDao.insert(
                            PatientSessionEntity(
                                sessionId = sessionId,
                                sessionTokenHash = "",
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                }

                val now = System.currentTimeMillis()
                val entities = rows.map { row ->
                    ConsultationEntity(
                        consultationId = row.consultationId,
                        patientSessionId = row.patientSessionId,
                        doctorId = row.doctorId,
                        status = row.status.uppercase(),
                        serviceType = row.serviceType,
                        consultationFee = row.consultationFee,
                        sessionStartTime = parseInstantToMillis(row.sessionStartTime),
                        sessionEndTime = parseInstantToMillis(row.sessionEndTime),
                        sessionDurationMinutes = row.sessionDurationMinutes,
                        requestExpiresAt = parseInstantToMillis(row.requestExpiresAt) ?: (now + 3600_000),
                        createdAt = parseInstantToMillis(row.createdAt) ?: now,
                        updatedAt = parseInstantToMillis(row.updatedAt) ?: now,
                    )
                }
                consultationDao.insertAll(entities)
                Log.d(TAG, "Cached ${entities.size} consultations from Supabase")

                // Compute dashboard stats from fetched data
                updateDashboardStats(entities)
            }
            else -> Log.e(TAG, "Failed to fetch consultations from Supabase: $result")
        }
    }

    private suspend fun fetchEarningsFromSupabase(doctorId: String) {
        when (val result = earningsService.getEarningsForDoctor(doctorId)) {
            is ApiResult.Success -> {
                val rows = result.data
                if (rows.isEmpty()) {
                    Log.d(TAG, "No earnings found on Supabase for $doctorId")
                    return
                }

                val now = System.currentTimeMillis()
                val entities = rows.map { row ->
                    DoctorEarningsEntity(
                        earningId = row.earningId,
                        doctorId = row.doctorId,
                        consultationId = row.consultationId,
                        amount = row.amount,
                        status = row.status,
                        paidAt = parseInstantToMillis(row.paidAt),
                        createdAt = parseInstantToMillis(row.createdAt) ?: now,
                    )
                }
                doctorEarningsDao.insertAll(entities)
                Log.d(TAG, "Cached ${entities.size} earnings from Supabase")
            }
            else -> Log.e(TAG, "Failed to fetch earnings from Supabase: $result")
        }
    }

    private fun observeConsultations(doctorId: String) {
        viewModelScope.launch {
            consultationDao.getByDoctorIdAndStatus(doctorId, "PENDING").collect { list ->
                _uiState.update {
                    it.copy(pendingConsultations = list, pendingRequests = list.size)
                }
            }
        }
        viewModelScope.launch {
            consultationDao.getByDoctorIdAndStatus(doctorId, "ACTIVE").collect { list ->
                _uiState.update {
                    it.copy(activeConsultationsList = list, activeConsultations = list.size)
                }
            }
        }
        viewModelScope.launch {
            consultationDao.getByDoctorIdAndStatus(doctorId, "COMPLETED").collect { list ->
                _uiState.update { it.copy(completedConsultations = list) }
                // Recompute stats when completed consultations change
                updateDashboardStatsFromRoom(doctorId)
            }
        }
        viewModelScope.launch {
            consultationDao.getByDoctorIdAndStatus(doctorId, "CANCELLED").collect { list ->
                _uiState.update { it.copy(cancelledConsultations = list) }
            }
        }
    }

    private fun observeEarnings(doctorId: String) {
        viewModelScope.launch {
            doctorEarningsDao.getEarningsForDoctor(doctorId).collect { earnings ->
                val total = earnings.sumOf { it.amount }
                val pending = earnings.filter { it.status.equals("pending", ignoreCase = true) }.sumOf { it.amount }

                val now = LocalDate.now()
                val thisMonthStart = now.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val lastMonthStart = now.minusMonths(1).withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

                val thisMonth = earnings.filter { it.createdAt >= thisMonthStart }.sumOf { it.amount }
                val lastMonth = earnings.filter { it.createdAt >= lastMonthStart && it.createdAt < thisMonthStart }.sumOf { it.amount }

                val transactions = earnings.take(10).map { e ->
                    EarningsTransaction(
                        id = e.earningId,
                        patientName = "Patient",
                        amount = formatTsh(e.amount),
                        date = formatDate(e.createdAt),
                        status = e.status,
                    )
                }

                _uiState.update {
                    it.copy(
                        totalEarnings = formatTsh(total),
                        thisMonthEarnings = formatTsh(thisMonth),
                        lastMonthEarnings = formatTsh(lastMonth),
                        pendingPayout = formatTsh(pending),
                        recentTransactions = transactions,
                    )
                }
            }
        }
    }

    private fun updateDashboardStats(consultations: List<ConsultationEntity>) {
        val uniquePatients = consultations.map { it.patientSessionId }.distinct().size
        val completed = consultations.count { it.status.equals("COMPLETED", ignoreCase = true) }
        val cancelled = consultations.count { it.status.equals("CANCELLED", ignoreCase = true) }
        val totalDecided = completed + cancelled
        val rate = if (totalDecided > 0) "${(completed * 100 / totalDecided)}%" else "\u2014"

        val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todayEarnings = consultations
            .filter { it.status.equals("COMPLETED", ignoreCase = true) && it.createdAt >= todayStart }
            .sumOf { it.consultationFee }

        _uiState.update {
            it.copy(
                totalPatients = uniquePatients,
                acceptanceRate = rate,
                todaysEarnings = formatTsh(todayEarnings),
            )
        }
    }

    private fun updateDashboardStatsFromRoom(doctorId: String) {
        viewModelScope.launch {
            val all = consultationDao.getByDoctorId(doctorId).first()
            updateDashboardStats(all)
        }
    }

    private fun subscribeToRealtime(doctorId: String) {
        viewModelScope.launch {
            realtimeService.subscribeToConsultations(doctorId, viewModelScope)

            realtimeService.consultationEvents.collect {
                Log.d(TAG, "Realtime event received, re-fetching consultations")
                fetchConsultationsFromSupabase(doctorId)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            realtimeService.unsubscribe()
        }
    }

    private suspend fun loadAvailability(doctorId: String) {
        availabilityDao.getByDoctorId(doctorId).collect { entity ->
            if (entity != null) {
                try {
                    val map = json.decodeFromString<Map<String, DaySchedule>>(
                        entity.availabilitySchedule,
                    )
                    val schedule = WeeklySchedule(
                        sunday = map["Sunday"] ?: DaySchedule(),
                        monday = map["Monday"] ?: DaySchedule(enabled = true),
                        tuesday = map["Tuesday"] ?: DaySchedule(enabled = true),
                        wednesday = map["Wednesday"] ?: DaySchedule(enabled = true),
                        thursday = map["Thursday"] ?: DaySchedule(enabled = true),
                        friday = map["Friday"] ?: DaySchedule(enabled = true),
                        saturday = map["Saturday"] ?: DaySchedule(),
                    )
                    _uiState.update { it.copy(weeklySchedule = schedule) }
                } catch (_: Exception) {
                    // Keep default schedule on parse error
                }
            }
        }
    }

    // ─── Availability actions ───────────────────────────────────────────────────

    fun onDayToggled(dayName: String) {
        _uiState.update { state ->
            val current = state.weeklySchedule.toMap()[dayName] ?: DaySchedule()
            val updated = current.copy(enabled = !current.enabled)
            state.copy(
                weeklySchedule = state.weeklySchedule.withDay(dayName, updated),
                availabilitySaved = false,
            )
        }
    }

    fun onDayStartChanged(dayName: String, start: String) {
        _uiState.update { state ->
            val current = state.weeklySchedule.toMap()[dayName] ?: DaySchedule()
            state.copy(
                weeklySchedule = state.weeklySchedule.withDay(dayName, current.copy(start = start)),
                availabilitySaved = false,
            )
        }
    }

    fun onDayEndChanged(dayName: String, end: String) {
        _uiState.update { state ->
            val current = state.weeklySchedule.toMap()[dayName] ?: DaySchedule()
            state.copy(
                weeklySchedule = state.weeklySchedule.withDay(dayName, current.copy(end = end)),
                availabilitySaved = false,
            )
        }
    }

    fun applyPresetMonFri() {
        _uiState.update { state ->
            state.copy(
                weeklySchedule = WeeklySchedule(
                    sunday = DaySchedule(enabled = false),
                    monday = DaySchedule(enabled = true, start = "09:00", end = "17:00"),
                    tuesday = DaySchedule(enabled = true, start = "09:00", end = "17:00"),
                    wednesday = DaySchedule(enabled = true, start = "09:00", end = "17:00"),
                    thursday = DaySchedule(enabled = true, start = "09:00", end = "17:00"),
                    friday = DaySchedule(enabled = true, start = "09:00", end = "17:00"),
                    saturday = DaySchedule(enabled = false),
                ),
                availabilitySaved = false,
            )
        }
    }

    fun applyPresetEveryDay() {
        _uiState.update { state ->
            state.copy(
                weeklySchedule = WeeklySchedule(
                    sunday = DaySchedule(enabled = true, start = "09:00", end = "17:00"),
                    monday = DaySchedule(enabled = true, start = "09:00", end = "17:00"),
                    tuesday = DaySchedule(enabled = true, start = "09:00", end = "17:00"),
                    wednesday = DaySchedule(enabled = true, start = "09:00", end = "17:00"),
                    thursday = DaySchedule(enabled = true, start = "09:00", end = "17:00"),
                    friday = DaySchedule(enabled = true, start = "09:00", end = "17:00"),
                    saturday = DaySchedule(enabled = true, start = "09:00", end = "17:00"),
                ),
                availabilitySaved = false,
            )
        }
    }

    fun clearAllAvailability() {
        _uiState.update { state ->
            state.copy(
                weeklySchedule = WeeklySchedule(
                    sunday = DaySchedule(),
                    monday = DaySchedule(),
                    tuesday = DaySchedule(),
                    wednesday = DaySchedule(),
                    thursday = DaySchedule(),
                    friday = DaySchedule(),
                    saturday = DaySchedule(),
                ),
                availabilitySaved = false,
            )
        }
    }

    fun saveAvailability() {
        viewModelScope.launch {
            val doctorId = _uiState.value.doctorId
            if (doctorId.isBlank()) return@launch

            val isAvailable = _uiState.value.weeklySchedule.toMap().values.any { it.enabled }
            val scheduleJson = json.encodeToString(_uiState.value.weeklySchedule.toMap())

            try {
                // 1. Save locally
                val entity = DoctorAvailabilityEntity(
                    availabilityId = "${doctorId}_weekly",
                    doctorId = doctorId,
                    isAvailable = isAvailable,
                    availabilitySchedule = scheduleJson,
                    lastUpdated = System.currentTimeMillis(),
                )
                availabilityDao.insert(entity)
            } catch (_: Exception) {
                // Local save may fail if FK constraints exist; continue to remote sync
            }

            // 2. Sync to remote Supabase so patients can see it
            availabilityService.syncAvailability(
                doctorId = doctorId,
                isAvailable = isAvailable,
                scheduleJson = scheduleJson,
            )

            _uiState.update { it.copy(availabilitySaved = true) }
        }
    }

    // ─── Profile actions (only editable fields: photo, languages, services, availability) ─

    fun onProfilePhotoSelected(uri: Uri) {
        viewModelScope.launch {
            val doctorId = _uiState.value.doctorId
            if (doctorId.isBlank()) return@launch

            _uiState.update { it.copy(profileUploading = true) }
            try {
                // Import auth token so Storage uploads are authenticated
                val session = authRepository.currentSession.first()
                if (session != null) {
                    supabaseClientProvider.importAuthToken(
                        accessToken = session.accessToken,
                        refreshToken = session.refreshToken,
                    )
                }

                val bytes = application.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) {
                    Log.e(TAG, "Failed to read photo bytes")
                    _uiState.update { it.copy(profileUploading = false) }
                    return@launch
                }

                val contentType = application.contentResolver.getType(uri) ?: "image/jpeg"
                val storagePath = "$doctorId/profile-photo"

                val uploadResult = fileUploadService.uploadFile(
                    bucketName = PROFILE_PHOTOS_BUCKET,
                    path = storagePath,
                    bytes = bytes,
                    contentType = contentType,
                )

                when (uploadResult) {
                    is ApiResult.Success -> {
                        val publicUrl = fileUploadService.getPublicUrl(PROFILE_PHOTOS_BUCKET, uploadResult.data)
                        // Update remote DB with new photo URL
                        fileUploadService.updateDoctorProfileUrls(
                            doctorId = doctorId,
                            profilePhotoUrl = publicUrl,
                            licenseDocumentUrl = null,
                            certificatesUrl = null,
                        )
                        // Update local DB
                        val existing = doctorProfileDao.getById(doctorId)
                        if (existing != null) {
                            doctorProfileDao.insert(existing.copy(profilePhotoUrl = publicUrl, updatedAt = System.currentTimeMillis()))
                        }
                        _uiState.update { it.copy(profilePhotoUrl = publicUrl, profileUploading = false, profileSaved = false) }
                        Log.d(TAG, "Profile photo uploaded: $publicUrl")
                    }
                    else -> {
                        Log.e(TAG, "Photo upload failed: $uploadResult")
                        _uiState.update { it.copy(profileUploading = false) }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload profile photo", e)
                _uiState.update { it.copy(profileUploading = false) }
            }
        }
    }

    fun onProfileLanguageToggled(language: String) {
        _uiState.update { state ->
            val current = state.profileLanguages.toMutableList()
            if (current.contains(language)) current.remove(language) else current.add(language)
            state.copy(profileLanguages = current, profileSaved = false)
        }
    }

    fun onProfileServiceToggled(service: String) {
        _uiState.update { state ->
            val current = state.profileServices.toMutableList()
            if (current.contains(service)) current.remove(service) else current.add(service)
            state.copy(profileServices = current, profileSaved = false)
        }
    }

    fun onProfileAvailableToggled() {
        _uiState.update { it.copy(profileAvailableForConsultations = !it.profileAvailableForConsultations, profileSaved = false) }
    }

    fun saveProfile() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.doctorId.isBlank()) return@launch

            try {
                // 1. Save locally
                val entity = DoctorProfileEntity(
                    doctorId = state.doctorId,
                    fullName = state.doctorName,
                    email = state.profileEmail,
                    phone = state.profilePhone,
                    specialty = state.profileSpecialty.uppercase().replace(" ", "_"),
                    languages = state.profileLanguages,
                    bio = state.profileBio,
                    licenseNumber = state.profileLicenseNumber,
                    yearsExperience = state.registeredYearsExperience,
                    profilePhotoUrl = state.profilePhotoUrl,
                    isVerified = state.isVerified,
                    isAvailable = state.profileAvailableForConsultations,
                    services = state.profileServices,
                    country = state.profileCountry,
                    createdAt = state.registrationTimestamp,
                    updatedAt = System.currentTimeMillis(),
                )
                doctorProfileDao.insert(entity)

                // 2. Sync editable fields to Supabase
                val synced = syncProfileToSupabase(state)

                _uiState.update {
                    it.copy(profileSaved = true, profileSyncFailed = !synced)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save profile", e)
            }
        }
    }

    private suspend fun syncProfileToSupabase(state: DoctorDashboardUiState): Boolean {
        return try {
            // Import auth token for authenticated Postgrest calls
            val session = authRepository.currentSession.first()
            if (session != null) {
                supabaseClientProvider.importAuthToken(
                    accessToken = session.accessToken,
                    refreshToken = session.refreshToken,
                )
            }

            fileUploadService.updateDoctorEditableFields(
                doctorId = state.doctorId,
                languages = state.profileLanguages,
                services = state.profileServices,
                isAvailable = state.profileAvailableForConsultations,
                profilePhotoUrl = state.profilePhotoUrl,
            )
            Log.d(TAG, "Profile synced to Supabase for ${state.doctorId}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync profile to Supabase", e)
            false
        }
    }

    // ─── Other actions ──────────────────────────────────────────────────────────

    fun onToggleOnline() {
        viewModelScope.launch {
            val session = authRepository.currentSession.first() ?: return@launch
            val newState = !_uiState.value.isOnline
            doctorProfileRepository.updateAvailability(session.user.id, newState)
            _uiState.update { it.copy(isOnline = newState, isAvailable = newState) }
        }
    }

    fun onSignOut() {
        viewModelScope.launch {
            logoutUseCase()
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private fun parseInstantToMillis(isoString: String?): Long? {
        if (isoString.isNullOrBlank()) return null
        return try {
            Instant.parse(isoString).toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }

    private fun formatTsh(amount: Int): String {
        val formatted = NumberFormat.getNumberInstance(Locale.US).format(amount)
        return "TSh $formatted"
    }

    private fun formatDate(epochMillis: Long): String {
        return try {
            val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            "${date.dayOfMonth}/${date.monthValue}/${date.year}"
        } catch (_: Exception) {
            ""
        }
    }

    companion object {
        private const val TAG = "DoctorDashboardVM"
        private const val PROFILE_PHOTOS_BUCKET = "profile-photos"
    }
}
