package com.esiri.esiriplus.core.domain.model

data class DoctorProfile(
    val doctorId: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val specialty: String,
    val languages: List<String>,
    val bio: String,
    val licenseNumber: String,
    val yearsExperience: Int,
    val profilePhotoUrl: String? = null,
    val averageRating: Double = 0.0,
    val totalRatings: Int = 0,
    val isVerified: Boolean = false,
    val isAvailable: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
