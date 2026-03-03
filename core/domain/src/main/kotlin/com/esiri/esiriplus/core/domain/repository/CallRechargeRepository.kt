package com.esiri.esiriplus.core.domain.repository

interface CallRechargeRepository {
    /** Initiates an M-Pesa STK Push for call recharge. Returns true on success. */
    suspend fun submitRecharge(
        consultationId: String,
        minutes: Int,
        phoneNumber: String,
    ): Boolean
}
