package com.esiri.esiriplus.core.domain.repository

import com.esiri.esiriplus.core.domain.model.DoctorRating
import kotlinx.coroutines.flow.Flow

interface DoctorRatingRepository {
    suspend fun submitRating(rating: DoctorRating)
    fun getRatingsForDoctor(doctorId: String): Flow<List<DoctorRating>>
    fun getAverageRating(doctorId: String): Flow<Double>
    suspend fun getUnsyncedRatings(): List<DoctorRating>
    suspend fun markSynced(ratingId: String)
    suspend fun clearAll()
}
