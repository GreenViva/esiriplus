package com.esiri.esiriplus.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "doctor_earnings",
    foreignKeys = [
        ForeignKey(
            entity = DoctorProfileEntity::class,
            parentColumns = ["doctorId"],
            childColumns = ["doctorId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ConsultationEntity::class,
            parentColumns = ["consultationId"],
            childColumns = ["consultationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("doctorId", "status"),
        Index("consultationId"),
    ],
)
data class DoctorEarningsEntity(
    @PrimaryKey val earningId: String,
    val doctorId: String,
    val consultationId: String,
    val amount: Int,
    val status: String,
    val paidAt: Long? = null,
    val createdAt: Long,
)
