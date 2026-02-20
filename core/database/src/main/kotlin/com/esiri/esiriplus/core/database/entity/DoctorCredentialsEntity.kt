package com.esiri.esiriplus.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "doctor_credentials",
    foreignKeys = [
        ForeignKey(
            entity = DoctorProfileEntity::class,
            parentColumns = ["doctorId"],
            childColumns = ["doctorId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("doctorId")],
)
data class DoctorCredentialsEntity(
    @PrimaryKey val credentialId: String,
    val doctorId: String,
    val documentUrl: String,
    val documentType: String,
    val verifiedAt: Long? = null,
)
