package com.esiri.esiriplus.core.network.service

import android.util.Log
import com.esiri.esiriplus.core.network.SupabaseClientProvider
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

data class ChatMessageEvent(
    val messageId: String,
    val consultationId: String,
    val senderType: String,
    val senderId: String,
    val messageText: String,
    val messageType: String,
    val createdAt: String,
)

data class TypingEvent(
    val consultationId: String,
    val userId: String,
    val isTyping: Boolean,
)

enum class RealtimeConnectionState { CONNECTING, CONNECTED, DISCONNECTED }

@Singleton
class ChatRealtimeService @Inject constructor(
    private val supabaseClientProvider: SupabaseClientProvider,
) {

    private val _messageEvents = MutableSharedFlow<ChatMessageEvent>(extraBufferCapacity = 64)
    val messageEvents: SharedFlow<ChatMessageEvent> = _messageEvents.asSharedFlow()

    private val _typingEvents = MutableSharedFlow<TypingEvent>(extraBufferCapacity = 16)
    val typingEvents: SharedFlow<TypingEvent> = _typingEvents.asSharedFlow()

    private val _connectionState = MutableStateFlow(RealtimeConnectionState.DISCONNECTED)
    val connectionState: StateFlow<RealtimeConnectionState> = _connectionState.asStateFlow()

    private val lock = Any()
    @Volatile private var messageChannel: RealtimeChannel? = null
    @Volatile private var typingChannel: RealtimeChannel? = null

    private var reconnectJob: Job? = null
    private var currentConsultationId: String? = null
    private var currentScope: CoroutineScope? = null
    @Volatile private var intentionalUnsubscribe = false
    private var reconnectAttempt = 0

    private val backoffDelays = longArrayOf(1_000, 2_000, 5_000, 10_000, 30_000)

    suspend fun subscribeToMessages(consultationId: String, scope: CoroutineScope) {
        synchronized(lock) {
            currentConsultationId = consultationId
            currentScope = scope
            reconnectAttempt = 0
            intentionalUnsubscribe = false
        }
        doSubscribeMessages(consultationId, scope)
    }

    private suspend fun doSubscribeMessages(consultationId: String, scope: CoroutineScope) {
        try {
            // Clean up any existing channel without triggering reconnect
            messageChannel?.let { ch ->
                try { ch.unsubscribe() } catch (_: Exception) {}
            }
            synchronized(lock) { messageChannel = null }

            _connectionState.value = RealtimeConnectionState.CONNECTING

            val ch = supabaseClientProvider.client.channel("chat-messages-$consultationId-${System.currentTimeMillis()}")
            synchronized(lock) { messageChannel = ch }

            val changeFlow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "messages"
                filter("consultation_id", FilterOperator.EQ, consultationId)
            }

            changeFlow.onEach { action ->
                val event = extractMessageEvent(action)
                if (event != null) {
                    Log.d(TAG, "Realtime message: ${event.messageId} from ${event.senderType}")
                    _messageEvents.emit(event)
                }
            }.launchIn(scope)

            // Monitor channel status for unexpected disconnects
            ch.status.onEach { status ->
                Log.d(TAG, "Messages channel status: $status")
                when (status) {
                    RealtimeChannel.Status.SUBSCRIBED -> {
                        _connectionState.value = RealtimeConnectionState.CONNECTED
                        synchronized(lock) { reconnectAttempt = 0 }
                    }
                    RealtimeChannel.Status.UNSUBSCRIBED -> {
                        val shouldReconnect = synchronized(lock) {
                            !intentionalUnsubscribe && currentConsultationId != null
                        }
                        if (shouldReconnect) {
                            _connectionState.value = RealtimeConnectionState.DISCONNECTED
                            Log.w(TAG, "Messages channel disconnected unexpectedly, scheduling reconnect")
                            scheduleReconnect()
                        }
                    }
                    else -> {}
                }
            }.launchIn(scope)

            ch.subscribe()
            Log.d(TAG, "Subscribed to messages for consultation $consultationId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe to messages", e)
            _connectionState.value = RealtimeConnectionState.DISCONNECTED
            scheduleReconnect()
        }
    }

    suspend fun subscribeToTyping(consultationId: String, scope: CoroutineScope) {
        try {
            typingChannel?.let { ch ->
                try { ch.unsubscribe() } catch (_: Exception) {}
            }
            synchronized(lock) { typingChannel = null }

            val ch = supabaseClientProvider.client.channel("chat-typing-$consultationId-${System.currentTimeMillis()}")
            synchronized(lock) { typingChannel = ch }

            val changeFlow = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "typing_indicators"
                filter("consultation_id", FilterOperator.EQ, consultationId)
            }

            changeFlow.onEach { action ->
                val event = extractTypingEvent(action)
                if (event != null) {
                    _typingEvents.emit(event)
                }
            }.launchIn(scope)

            ch.subscribe()
            Log.d(TAG, "Subscribed to typing for consultation $consultationId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe to typing", e)
        }
    }

    private fun scheduleReconnect() {
        synchronized(lock) {
            if (reconnectJob?.isActive == true) return
            val consultationId = currentConsultationId ?: return
            val scope = currentScope ?: return
            val delayMs = backoffDelays[reconnectAttempt.coerceAtMost(backoffDelays.size - 1)]
            reconnectAttempt++

            reconnectJob = scope.launch {
                Log.d(TAG, "Reconnecting in ${delayMs}ms (attempt $reconnectAttempt)")
                delay(delayMs)
                if (!isActive) return@launch
                doSubscribeMessages(consultationId, scope)
                doSubscribeTyping(consultationId, scope)
            }
        }
    }

    private suspend fun doSubscribeTyping(consultationId: String, scope: CoroutineScope) {
        subscribeToTyping(consultationId, scope)
    }

    private fun extractMessageEvent(action: PostgresAction): ChatMessageEvent? {
        val record: JsonObject = when (action) {
            is PostgresAction.Insert -> action.record
            is PostgresAction.Update -> action.record
            else -> return null
        }
        val messageId = record["message_id"]?.jsonPrimitive?.content ?: return null
        val consultationId = record["consultation_id"]?.jsonPrimitive?.content ?: return null
        val senderType = record["sender_type"]?.jsonPrimitive?.content ?: return null
        val senderId = record["sender_id"]?.jsonPrimitive?.content ?: return null
        val messageText = record["message_text"]?.jsonPrimitive?.content ?: ""
        val messageType = record["message_type"]?.jsonPrimitive?.content ?: "text"
        val createdAt = record["created_at"]?.jsonPrimitive?.content ?: ""
        return ChatMessageEvent(messageId, consultationId, senderType, senderId, messageText, messageType, createdAt)
    }

    private fun extractTypingEvent(action: PostgresAction): TypingEvent? {
        val record: JsonObject = when (action) {
            is PostgresAction.Insert -> action.record
            is PostgresAction.Update -> action.record
            else -> return null
        }
        val consultationId = record["consultation_id"]?.jsonPrimitive?.content ?: return null
        val userId = record["user_id"]?.jsonPrimitive?.content ?: return null
        val isTyping = record["is_typing"]?.jsonPrimitive?.booleanOrNull ?: false
        return TypingEvent(consultationId, userId, isTyping)
    }

    suspend fun unsubscribeMessages() {
        try {
            messageChannel?.unsubscribe()
            synchronized(lock) { messageChannel = null }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unsubscribe from messages", e)
        }
    }

    private suspend fun unsubscribeTyping() {
        try {
            typingChannel?.unsubscribe()
            synchronized(lock) { typingChannel = null }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unsubscribe from typing", e)
        }
    }

    suspend fun unsubscribeAll() {
        synchronized(lock) {
            intentionalUnsubscribe = true
            reconnectJob?.cancel()
            reconnectJob = null
            currentConsultationId = null
            currentScope = null
        }
        unsubscribeMessages()
        unsubscribeTyping()
        _connectionState.value = RealtimeConnectionState.DISCONNECTED
    }

    /** Non-suspend variant safe to call from onCleared (where viewModelScope is cancelled). */
    fun unsubscribeAllSync() {
        synchronized(lock) {
            intentionalUnsubscribe = true
            reconnectJob?.cancel()
            reconnectJob = null
            currentConsultationId = null
            currentScope = null
        }
        try {
            kotlinx.coroutines.runBlocking { unsubscribeAll() }
        } catch (_: Exception) { }
    }

    companion object {
        private const val TAG = "ChatRealtimeSvc"
    }
}
