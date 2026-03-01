package com.esiri.esiriplus.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "doctor_profiles",
    indices = [
        Index("isVerified", "isAvailable", "specialty"),
        Index("averageRating"),
        Index("email"),
    ],
)
data class DoctorProfileEntity(
    @PrimaryKey val doctorId: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val specialty: String,
    @ColumnInfo(defaultValue = "NULL") val specialistField: String? = null,
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
    @ColumnInfo(defaultValue = "[]") val services: List<String> = emptyList(),
    @ColumnInfo(defaultValue = "+255") val countryCode: String = "+255",
    @ColumnInfo(defaultValue = "") val country: String = "",
    @ColumnInfo(defaultValue = "NULL") val licenseDocumentUrl: String? = null,
    @ColumnInfo(defaultValue = "NULL") val certificatesUrl: String? = null,
    @ColumnInfo(defaultValue = "NULL") val rejectionReason: String? = null,
    @ColumnInfo(defaultValue = "0") val isBanned: Boolean = false,
    @ColumnInfo(defaultValue = "NULL") val bannedAt: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val banReason: String? = null,
    @ColumnInfo(defaultValue = "0") val inSession: Boolean = false,
    @ColumnInfo(defaultValue = "10") val maxAppointmentsPerDay: Int = 10,
)
