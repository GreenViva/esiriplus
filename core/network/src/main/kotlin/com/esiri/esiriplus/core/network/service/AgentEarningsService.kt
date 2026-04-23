package com.esiri.esiriplus.core.network.service

import com.esiri.esiriplus.core.network.api.SupabaseApi
import com.esiri.esiriplus.core.network.api.model.AgentEarningApiModel
import com.esiri.esiriplus.core.network.model.ApiResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads agent_earnings via PostgREST. RLS policy agents_read_own_earnings
 * ensures each request only sees the agent's own rows.
 *
 * Pagination uses PostgREST's `Range` header (items N-M inclusive) so
 * the response Content-Range gives us the total for free — cheap enough
 * to re-query on every screen open without burning a separate count RPC.
 */
@Singleton
class AgentEarningsService @Inject constructor(
    private val api: SupabaseApi,
) {

    /** One page of earnings, ordered newest first. pageSize rows per page. */
    suspend fun getEarningsPage(
        page: Int = 0,
        pageSize: Int = PAGE_SIZE,
        status: String? = null,
    ): ApiResult<EarningsPage> {
        return try {
            val from = page * pageSize
            val to = from + pageSize - 1
            val resp = api.getAgentEarnings(
                statusFilter = status?.let { "eq.$it" },
                range = "$from-$to",
            )
            if (!resp.isSuccessful) {
                return ApiResult.Error(resp.code(), resp.errorBody()?.string() ?: "Failed to load earnings")
            }
            val rows = resp.body() ?: emptyList()
            // Content-Range: "0-19/42" → total = 42
            val total = resp.headers()["Content-Range"]
                ?.substringAfter('/')
                ?.toIntOrNull()
                ?: rows.size
            ApiResult.Success(EarningsPage(rows = rows, total = total, page = page, pageSize = pageSize))
        } catch (e: Exception) {
            ApiResult.NetworkError(e, e.message ?: "Network error")
        }
    }

    /**
     * Summary numbers for the dashboard badge. Single round-trip that pulls
     * only the two columns we need; totals summed client-side.
     */
    suspend fun getSummary(): ApiResult<EarningsSummary> {
        return try {
            // Fetch up to 500 rows (amount + status only). If an agent crosses
            // that in a billing cycle before admins mark paid, this under-counts
            // the pending sum — but the list view can still paginate fully.
            val resp = api.getAgentEarnings(
                select = "amount,status",
                range = "0-499",
            )
            if (!resp.isSuccessful) {
                return ApiResult.Error(resp.code(), resp.errorBody()?.string() ?: "Failed to load earnings")
            }
            val rows = resp.body() ?: emptyList()
            var pendingCount = 0
            var pendingAmount = 0L
            var paidAmount = 0L
            for (row in rows) {
                when (row.status) {
                    "pending" -> { pendingCount++; pendingAmount += row.amount }
                    "paid" -> paidAmount += row.amount
                    else -> {}
                }
            }
            ApiResult.Success(
                EarningsSummary(
                    pendingCount = pendingCount,
                    pendingAmount = pendingAmount,
                    paidAmount = paidAmount,
                    totalAmount = pendingAmount + paidAmount,
                ),
            )
        } catch (e: Exception) {
            ApiResult.NetworkError(e, e.message ?: "Network error")
        }
    }

    companion object {
        const val PAGE_SIZE = 20
    }
}

data class EarningsPage(
    val rows: List<AgentEarningApiModel>,
    val total: Int,
    val page: Int,
    val pageSize: Int,
) {
    val hasMore: Boolean get() = (page + 1) * pageSize < total
}

data class EarningsSummary(
    val pendingCount: Int,
    val pendingAmount: Long,
    val paidAmount: Long,
    val totalAmount: Long,
)
