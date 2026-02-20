package com.esiri.esiriplus.feature.auth.data

import com.esiri.esiriplus.core.common.di.IoDispatcher
import com.esiri.esiriplus.core.database.dao.PatientSessionDao
import com.esiri.esiriplus.core.database.entity.PatientSessionEntity
import com.esiri.esiriplus.core.domain.repository.PatientSession
import com.esiri.esiriplus.core.domain.repository.PatientSessionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PatientSessionRepositoryImpl @Inject constructor(
    private val patientSessionDao: PatientSessionDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PatientSessionRepository {

    override fun getSession(): Flow<PatientSession?> =
        patientSessionDao.getSession()
            .map { it?.toDomain() }
            .flowOn(ioDispatcher)

    override suspend fun saveSession(session: PatientSession) {
        withContext(ioDispatcher) {
            patientSessionDao.insert(session.toEntity())
        }
    }

    override suspend fun updateMedicalInfo(
        sessionId: String,
        allergies: List<String>,
        chronicConditions: List<String>,
    ) {
        withContext(ioDispatcher) {
            val existing = patientSessionDao.getById(sessionId) ?: return@withContext
            patientSessionDao.update(
                existing.copy(
                    allergies = allergies,
                    chronicConditions = chronicConditions,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    override suspend fun clearSession() {
        withContext(ioDispatcher) {
            patientSessionDao.clearAll()
        }
    }
}

private fun PatientSessionEntity.toDomain(): PatientSession = PatientSession(
    sessionId = sessionId,
    sessionTokenHash = sessionTokenHash,
    ageGroup = ageGroup,
    sex = sex,
    region = region,
    bloodType = bloodType,
    allergies = allergies,
    chronicConditions = chronicConditions,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastSynced = lastSynced,
)

private fun PatientSession.toEntity(): PatientSessionEntity = PatientSessionEntity(
    sessionId = sessionId,
    sessionTokenHash = sessionTokenHash,
    ageGroup = ageGroup,
    sex = sex,
    region = region,
    bloodType = bloodType,
    allergies = allergies,
    chronicConditions = chronicConditions,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastSynced = lastSynced,
)
