package com.esiri.esiriplus.core.network.service

import android.util.Log
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.network.model.safeApiCall
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class MessageRow(
    @SerialName("message_id") val messageId: String,
    @SerialName("consultation_id") val consultationId: String,
    @SerialName("sender_type") val senderType: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("message_text") val messageText: String,
    @SerialName("message_type") val messageType: String = "text",
    @SerialName("attachment_url") val attachmentUrl: String? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String,
)

@Singleton
class MessageService @Inject constructor(
    private val edgeFunctionClient: EdgeFunctionClient,
) {

    suspend fun getMessages(
        consultationId: String,
        since: String? = null,
    ): ApiResult<List<MessageRow>> {
        val body = buildJsonObject {
            put("action", "get")
            put("consultation_id", consultationId)
            if (since != null) put("since", since)
        }
        return safeApiCall {
            val raw = edgeFunctionClient.invoke(FUNCTION_NAME, body).getOrThrow()
            val result = edgeFunctionClient.json.decodeFromString<List<MessageRow>>(raw)
            Log.d(TAG, "Fetched ${result.size} messages for consultation $consultationId (since=$since)")
            result
        }
    }

    suspend fun sendMessage(
        messageId: String,
        consultationId: String,
        senderType: String,
        senderId: String,
        messageText: String,
        messageType: String = "text",
    ): ApiResult<MessageRow> {
        val body = buildJsonObject {
            put("action", "send")
            put("message_id", messageId)
            put("consultation_id", consultationId)
            put("sender_type", senderType)
            put("sender_id", senderId)
            put("message_text", messageText)
            put("message_type", messageType)
        }
        return safeApiCall {
            val raw = edgeFunctionClient.invoke(FUNCTION_NAME, body).getOrThrow()
            edgeFunctionClient.json.decodeFromString<MessageRow>(raw)
        }
    }

    suspend fun markAsRead(messageId: String): ApiResult<Unit> {
        val body = buildJsonObject {
            put("action", "mark_read")
            put("message_id", messageId)
        }
        return safeApiCall {
            edgeFunctionClient.invoke(FUNCTION_NAME, body).getOrThrow()
        }
    }

    suspend fun updateTypingIndicator(
        consultationId: String,
        userId: String,
        isTyping: Boolean,
    ): ApiResult<Unit> {
        val body = buildJsonObject {
            put("action", "typing")
            put("consultation_id", consultationId)
            put("user_id", userId)
            put("is_typing", isTyping)
        }
        return safeApiCall {
            edgeFunctionClient.invoke(FUNCTION_NAME, body).getOrThrow()
        }
    }

    companion object {
        private const val TAG = "MessageService"
        private const val FUNCTION_NAME = "handle-messages"
    }
}
