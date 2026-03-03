package com.esiri.esiriplus.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CallRechargeResponse(
    val message: String = "",
    val minutes: Int = 0,
    val amount: Int = 0,
)

data class CallRechargePackage(
    val minutes: Int,
    val price: Int,
    val label: String,
) {
    companion object {
        val ALL = listOf(
            CallRechargePackage(10, 200, "10 minutes — TZS 200"),
            CallRechargePackage(30, 500, "30 minutes — TZS 500"),
            CallRechargePackage(60, 900, "60 minutes — TZS 900"),
            CallRechargePackage(120, 1500, "120 minutes — TZS 1,500"),
        )
    }
}
