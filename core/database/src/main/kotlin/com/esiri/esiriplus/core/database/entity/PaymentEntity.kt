package com.esiri.esiriplus.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = PatientSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["patientSessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("patientSessionId", "createdAt"),
        Index("status"),
        Index("transactionId"),
    ],
)
data class PaymentEntity(
    @PrimaryKey val paymentId: String,
    val patientSessionId: String,
    val amount: Int,
    val paymentMethod: String,
    val transactionId: String? = null,
    val phoneNumber: String,
    val status: String,
    val failureReason: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
)
