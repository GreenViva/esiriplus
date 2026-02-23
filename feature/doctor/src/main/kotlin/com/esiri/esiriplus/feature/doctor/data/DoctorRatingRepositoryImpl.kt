package com.esiri.esiriplus.feature.doctor.data

import com.esiri.esiriplus.core.database.dao.DoctorRatingDao
import com.esiri.esiriplus.core.database.entity.DoctorRatingEntity
import com.esiri.esiriplus.core.domain.model.DoctorRating
import com.esiri.esiriplus.core.domain.repository.DoctorRatingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DoctorRatingRepositoryImpl @Inject constructor(
    private val doctorRatingDao: DoctorRatingDao,
) : DoctorRatingRepository {

    override suspend fun submitRating(rating: DoctorRating) {
        doctorRatingDao.insert(rating.toEntity())
    }

    override fun getRatingsForDoctor(doctorId: String): Flow<List<DoctorRating>> =
        doctorRatingDao.getByDoctorId(doctorId).map { entities -> entities.map { it.toDomain() } }

    override fun getAverageRating(doctorId: String): Flow<Double> =
        doctorRatingDao.getAverageRating(doctorId)

    override suspend fun getUnsyncedRatings(): List<DoctorRating> =
        doctorRatingDao.getUnsyncedRatings().map { it.toDomain() }

    override suspend fun markSynced(ratingId: String) {
        doctorRatingDao.markSynced(ratingId)
    }

    override suspend fun clearAll() {
        doctorRatingDao.clearAll()
    }
}

private fun DoctorRatingEntity.toDomain() = DoctorRating(
    ratingId = ratingId,
    doctorId = doctorId,
    consultationId = consultationId,
    patientSessionId = patientSessionId,
    rating = rating,
    comment = comment,
    createdAt = createdAt,
    synced = synced,
)

private fun DoctorRating.toEntity() = DoctorRatingEntity(
    ratingId = ratingId,
    doctorId = doctorId,
    consultationId = consultationId,
    patientSessionId = patientSessionId,
    rating = rating,
    comment = comment,
    createdAt = createdAt,
    synced = synced,
)
