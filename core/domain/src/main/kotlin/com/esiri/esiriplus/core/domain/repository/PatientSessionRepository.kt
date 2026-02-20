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
    val bloodType: String? = null,
    val allergies: List<String> = emptyList(),
    val chronicConditions: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val lastSynced: Long? = null,
)
