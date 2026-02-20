package com.esiri.esiriplus.core.domain.repository

import com.esiri.esiriplus.core.domain.model.DoctorProfile
import kotlinx.coroutines.flow.Flow

interface DoctorProfileRepository {
    fun getAllDoctors(): Flow<List<DoctorProfile>>
    suspend fun getDoctorById(doctorId: String): DoctorProfile?
    fun getDoctorsBySpecialty(specialty: String): Flow<List<DoctorProfile>>
    fun getAvailableDoctors(): Flow<List<DoctorProfile>>
    fun getDoctorsByRatingRange(minRating: Double): Flow<List<DoctorProfile>>
    suspend fun updateAvailability(doctorId: String, isAvailable: Boolean)
    suspend fun updateRating(doctorId: String, averageRating: Double, totalRatings: Int)
    suspend fun clearAll()
}
