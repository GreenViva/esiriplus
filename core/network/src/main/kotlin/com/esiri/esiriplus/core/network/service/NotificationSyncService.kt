package com.esiri.esiriplus.core.network.service

import android.util.Log
import com.esiri.esiriplus.core.network.SupabaseClientProvider
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.network.model.safeApiCall
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notification row from Supabase `notifications` table.
 *
 * Medical privacy: this DTO carries generic notification text only.
 * Clinical data lives in consultation/message tables, never in notification payloads.
 */
@Serializable
data class NotificationRow(
    @SerialName("notification_id") val notificationId: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val body: String,
    val type: String,
    val data: String = "{}",
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Singleton
class NotificationSyncService @Inject constructor(
    private val supabaseClientProvider: SupabaseClientProvider,
) {

    /**
     * Fetch a single notification by ID from Supabase.
     * Used when FCM push arrives with only notification_id.
     */
    suspend fun fetchNotificationById(notificationId: String): ApiResult<NotificationRow?> {
        return safeApiCall {
            val result = supabaseClientProvider.client.from("notifications")
                .select {
                    filter { eq("notification_id", notificationId) }
                }
                .decodeSingleOrNull<NotificationRow>()
            Log.d(TAG, "Fetched notification $notificationId: ${result != null}")
            result
        }
    }

    /**
     * Fetch all unread notifications for a user from Supabase.
     * Used for bulk sync on app open or when coming back online.
     */
    suspend fun fetchUnreadNotifications(userId: String): ApiResult<List<NotificationRow>> {
        return safeApiCall {
            val result = supabaseClientProvider.client.from("notifications")
                .select {
                    filter {
                        eq("user_id", userId)
                        filter("read_at", FilterOperator.IS, "null")
                    }
                    order("created_at", Order.DESCENDING)
                    limit(100)
                }
                .decodeList<NotificationRow>()
            Log.d(TAG, "Fetched ${result.size} unread notifications for $userId")
            result
        }
    }

    /**
     * Fetch recent notifications (last 30 days) for a user.
     * Used for full notification history sync.
     */
    suspend fun fetchRecentNotifications(userId: String): ApiResult<List<NotificationRow>> {
        return safeApiCall {
            val thirtyDaysAgo = java.time.Instant.now()
                .minus(java.time.Duration.ofDays(30))
                .toString()

            val result = supabaseClientProvider.client.from("notifications")
                .select {
                    filter {
                        eq("user_id", userId)
                        gte("created_at", thirtyDaysAgo)
                    }
                    order("created_at", Order.DESCENDING)
                    limit(200)
                }
                .decodeList<NotificationRow>()
            Log.d(TAG, "Fetched ${result.size} recent notifications for $userId")
            result
        }
    }

    /**
     * Mark a notification as read in Supabase (sync read status upstream).
     */
    suspend fun markAsReadRemote(notificationId: String): ApiResult<Unit> {
        return safeApiCall {
            supabaseClientProvider.client.from("notifications")
                .update(mapOf("read_at" to java.time.Instant.now().toString())) {
                    filter { eq("notification_id", notificationId) }
                }
        }
    }

    companion object {
        private const val TAG = "NotificationSyncSvc"
    }
}
