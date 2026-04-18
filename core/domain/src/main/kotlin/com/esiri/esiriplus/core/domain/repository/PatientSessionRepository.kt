package com.esiri.esiriplus.core.domain.repository

import kotlinx.coroutines.flow.Flow

interface PatientSessionRepository {
    fun getSession(): Flow<PatientSession?>
    suspend fun saveSession(session: PatientSession)
    suspend fun updateMedicalInfo(
        sessionId: String,
        allergies: List<String>,
        chronicConditions: List<String>,
    )
    /**
     * Persist the GPS-resolved hierarchy on the active session. Any level may
     * be null when the geocoder/resolver couldn't pin it down — downstream
     * matchers degrade gracefully on missing levels.
     */
    suspend fun updateLocation(
        sessionId: String,
        region: String?,
        district: String?,
        ward: String?,
        street: String?,
    )
    suspend fun clearSession()
}

/**
 * Domain representation of a patient session.
 * Maps to/from PatientSessionEntity at the data layer.
 */
data class PatientSession(
    val sessionId: String,
    val sessionTokenHash: String,
    val ageGroup: String? = null,
    val sex: String? = null,
    val region: String? = null,
    /** GPS-resolved canonical district from tz_locations. */
    val serviceDistrict: String? = null,
    val serviceWard: String? = null,
    val serviceStreet: String? = null,
    val bloodType: String? = null,
    val allergies: List<String> = emptyList(),
    val chronicConditions: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val lastSynced: Long? = null,
)
