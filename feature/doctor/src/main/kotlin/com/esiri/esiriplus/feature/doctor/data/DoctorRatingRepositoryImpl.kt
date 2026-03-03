package com.esiri.esiriplus.feature.doctor.data

import android.util.Log
import com.esiri.esiriplus.core.database.dao.DoctorRatingDao
import com.esiri.esiriplus.core.database.entity.DoctorRatingEntity
import com.esiri.esiriplus.core.domain.model.DoctorRating
import com.esiri.esiriplus.core.domain.repository.DoctorRatingRepository
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.model.ApiResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DoctorRatingRepositoryImpl @Inject constructor(
    private val doctorRatingDao: DoctorRatingDao,
    private val edgeFunctionClient: EdgeFunctionClient,
) : DoctorRatingRepository {

    override suspend fun submitRating(rating: DoctorRating) {
        doctorRatingDao.insert(rating.toEntity())
    }

    override suspend fun hasRating(consultationId: String): Boolean =
        doctorRatingDao.hasRating(consultationId)

    override suspend fun submitRatingToServer(rating: DoctorRating): Boolean {
        val body = buildJsonObject {
            put("consultation_id", rating.consultationId)
            put("rating", rating.rating)
            rating.comment?.let { put("comment", it) }
        }
        return when (val result = edgeFunctionClient.invoke("rate-doctor", body, patientAuth = true)) {
            is ApiResult.Success -> {
                Log.d(TAG, "Rating submitted to server: consultationId=${rating.consultationId}")
                true
            }
            is ApiResult.Error -> {
                // "already been rated" is idempotent — treat as success
                if (result.message?.contains("already been rated", ignoreCase = true) == true) {
                    Log.d(TAG, "Rating already exists on server (idempotent): consultationId=${rating.consultationId}")
                    true
                } else {
                    Log.e(TAG, "Failed to submit rating: code=${result.code}, msg=${result.message}")
                    false
                }
            }
            else -> {
                Log.e(TAG, "Failed to submit rating: $result")
                false
            }
        }
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

    companion object {
        private const val TAG = "DoctorRatingRepo"
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
