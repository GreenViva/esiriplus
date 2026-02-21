package com.esiri.esiriplus.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "service_access_payments")
data class ServiceAccessPaymentEntity(
    @PrimaryKey val paymentId: String,
    val serviceType: String,
    val amount: Int,
    val status: String,
    val createdAt: Long,
)
