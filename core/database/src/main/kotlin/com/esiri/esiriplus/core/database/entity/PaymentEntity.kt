package com.esiri.esiriplus.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "payments")
data class PaymentEntity(
    @PrimaryKey val id: String,
    val consultationId: String,
    val amount: Int,
    val currency: String,
    val status: String,
    val mpesaReceiptNumber: String?,
    val createdAt: Instant,
)
