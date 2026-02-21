package com.esiri.esiriplus.feature.doctor.data

import com.esiri.esiriplus.core.database.dao.DoctorAvailabilityDao
import com.esiri.esiriplus.core.database.dao.DoctorCredentialsDao
import com.esiri.esiriplus.core.database.dao.DoctorProfileDao
import com.esiri.esiriplus.core.database.entity.DoctorAvailabilityEntity
import com.esiri.esiriplus.core.database.entity.DoctorCredentialsEntity
import com.esiri.esiriplus.core.database.entity.DoctorProfileEntity
import com.esiri.esiriplus.core.domain.model.CredentialType
import com.esiri.esiriplus.core.domain.model.DoctorAvailability
import com.esiri.esiriplus.core.domain.model.DoctorCredentials
import com.esiri.esiriplus.core.domain.model.DoctorProfile
import com.esiri.esiriplus.core.domain.model.ServiceType
import com.esiri.esiriplus.core.domain.repository.DoctorProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DoctorProfileRepositoryImpl @Inject constructor(
    private val doctorProfileDao: DoctorProfileDao,
    private val doctorAvailabilityDao: DoctorAvailabilityDao,
    private val doctorCredentialsDao: DoctorCredentialsDao,
) : DoctorProfileRepository {

    @Volatile
    private var lastRefreshTimestamp: Long = 0L

    override fun getAllDoctors(): Flow<List<DoctorProfile>> =
        doctorProfileDao.getAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getDoctorById(doctorId: String): DoctorProfile? =
        doctorProfileDao.getById(doctorId)?.toDomain()

    override fun getDoctorsBySpecialty(specialty: String): Flow<List<DoctorProfile>> =
        doctorProfileDao.getBySpecialty(specialty).map { entities -> entities.map { it.toDomain() } }

    override fun getAvailableDoctors(): Flow<List<DoctorProfile>> =
        doctorProfileDao.getAvailableDoctors().map { entities -> entities.map { it.toDomain() } }

    override fun getDoctorsByRatingRange(minRating: Double): Flow<List<DoctorProfile>> =
        doctorProfileDao.getByRatingRange(minRating).map { entities -> entities.map { it.toDomain() } }

    override suspend fun updateAvailability(doctorId: String, isAvailable: Boolean) {
        doctorProfileDao.updateAvailability(doctorId, isAvailable, System.currentTimeMillis())
    }

    override suspend fun updateRating(doctorId: String, averageRating: Double, totalRatings: Int) {
        doctorProfileDao.updateRating(doctorId, averageRating, totalRatings, System.currentTimeMillis())
    }

    // Availability

    override fun getDoctorAvailability(doctorId: String): Flow<DoctorAvailability?> =
        doctorAvailabilityDao.getByDoctorId(doctorId).map { it?.toDomain() }

    override suspend fun updateDoctorAvailability(doctorId: String, isAvailable: Boolean) {
        doctorAvailabilityDao.updateAvailability(doctorId, isAvailable, System.currentTimeMillis())
    }

    // Credentials

    override fun getDoctorCredentials(doctorId: String): Flow<List<DoctorCredentials>> =
        doctorCredentialsDao.getByDoctorId(doctorId).map { entities -> entities.map { it.toDomain() } }

    // Cache management — stale-while-revalidate pattern

    override suspend fun refreshDoctors() {
        // TODO: Fetch from API and insert into Room
        // val doctors = api.getDoctors()
        // doctorProfileDao.insertAll(doctors.map { it.toEntity() })
        lastRefreshTimestamp = System.currentTimeMillis()
    }

    override fun isCacheStale(): Boolean {
        val elapsed = System.currentTimeMillis() - lastRefreshTimestamp
        return elapsed > CACHE_TTL_MS
    }

    override suspend fun clearAll() {
        doctorProfileDao.clearAll()
        lastRefreshTimestamp = 0L
    }

    companion object {
        private const val CACHE_TTL_MS = 60 * 60 * 1000L // 1 hour
    }
}

private fun DoctorProfileEntity.toDomain() = DoctorProfile(
    doctorId = doctorId,
    fullName = fullName,
    email = email,
    phone = phone,
    specialty = ServiceType.entries.find { it.name.equals(specialty, ignoreCase = true) }
        ?: ServiceType.GENERAL_CONSULTATION,
    languages = languages,
    bio = bio,
    licenseNumber = licenseNumber,
    yearsExperience = yearsExperience,
    profilePhotoUrl = profilePhotoUrl,
    averageRating = averageRating,
    totalRatings = totalRatings,
    isVerified = isVerified,
    isAvailable = isAvailable,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun DoctorAvailabilityEntity.toDomain() = DoctorAvailability(
    availabilityId = availabilityId,
    doctorId = doctorId,
    isAvailable = isAvailable,
    availabilitySchedule = availabilitySchedule,
    lastUpdated = lastUpdated,
)

private fun DoctorCredentialsEntity.toDomain() = DoctorCredentials(
    credentialId = credentialId,
    doctorId = doctorId,
    documentUrl = documentUrl,
    documentType = CredentialType.entries.find { it.name.equals(documentType, ignoreCase = true) }
        ?: CredentialType.LICENSE,
    verifiedAt = verifiedAt,
)
