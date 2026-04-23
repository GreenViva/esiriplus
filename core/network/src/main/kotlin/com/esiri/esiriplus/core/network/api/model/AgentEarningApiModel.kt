package com.esiri.esiriplus.core.network.api.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Row from agent_earnings — 10% commission accrual for a consultation
 * initiated by this agent. RLS (agents_read_own_earnings) restricts the
 * agent to their own rows, so no explicit agent_id filter is required
 * when the request carries the agent's own JWT.
 */
@JsonClass(generateAdapter = true)
data class AgentEarningApiModel(
    val id: String,
    @Json(name = "agent_id") val agentId: String,
    @Json(name = "consultation_id") val consultationId: String,
    val amount: Int,
    /** "pending" | "paid" | "cancelled" */
    val status: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String? = null,
)
