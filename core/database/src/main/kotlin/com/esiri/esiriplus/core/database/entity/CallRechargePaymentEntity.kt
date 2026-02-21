package com.esiri.esiriplus.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "call_recharge_payments",
    foreignKeys = [
        ForeignKey(
            entity = ConsultationEntity::class,
            parentColumns = ["consultationId"],
            childColumns = ["consultationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("consultationId"),
    ],
)
data class CallRechargePaymentEntity(
    @PrimaryKey val paymentId: String,
    val consultationId: String,
    @ColumnInfo(defaultValue = "2500") val amount: Int = 2500,
    @ColumnInfo(defaultValue = "3") val additionalMinutes: Int = 3,
    val status: String,
    val createdAt: Long,
)
