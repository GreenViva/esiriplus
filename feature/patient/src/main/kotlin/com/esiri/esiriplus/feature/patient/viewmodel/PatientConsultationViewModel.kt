package com.esiri.esiriplus.feature.patient.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.database.dao.ConsultationDao
import com.esiri.esiriplus.core.database.entity.ConsultationEntity
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.domain.repository.MessageData
import com.esiri.esiriplus.core.domain.repository.MessageRepository
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.domain.model.ConsultationSessionState
import com.esiri.esiriplus.core.network.SupabaseClientProvider
import com.esiri.esiriplus.core.network.TokenManager
import com.esiri.esiriplus.core.network.service.ChatRealtimeService
import com.esiri.esiriplus.core.network.service.ConsultationSessionManager
import com.esiri.esiriplus.core.network.service.MessageQueue
import com.esiri.esiriplus.core.network.service.MessageService
import com.esiri.esiriplus.core.network.service.RealtimeConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.util.Base64
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class ChatUiState(
    val messages: List<MessageData> = emptyList(),
    val isLoading: Boolean = true,
    val currentUserId: String = "",
    val currentUserType: String = "patient",
    val otherPartyTyping: Boolean = false,
    val consultationId: String = "",
    val error: String? = null,
    val sendError: String? = null,
)

@HiltViewModel
class PatientConsultationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messageRepository: MessageRepository,
    private val messageService: MessageService,
    private val chatRealtimeService: ChatRealtimeService,
    private val authRepository: AuthRepository,
    private val consultationDao: ConsultationDao,
    private val messageQueue: MessageQueue,
    private val consultationSessionManager: ConsultationSessionManager,
    private val tokenManager: TokenManager,
    private val supabaseClientProvider: SupabaseClientProvider,
) : ViewModel() {

    val consultationId: String = savedStateHandle["consultationId"] ?: ""

    private val _uiState = MutableStateFlow(ChatUiState(consultationId = consultationId))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val sessionState: StateFlow<ConsultationSessionState> = consultationSessionManager.state

    private var typingJob: Job? = null
    private var pollJob: Job? = null
    private var typingClearJob: Job? = null
    private var lastTypingSendMs = 0L

    /** Catches any uncaught coroutine exception to prevent app crash. */
    private val safeHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable is kotlinx.coroutines.CancellationException) throw throwable
        Log.e(TAG, "Unhandled coroutine exception (swallowed to prevent crash)", throwable)
    }

    init {
        if (consultationId.isNotBlank()) {
            initChat()
            consultationSessionManager.start(consultationId, viewModelScope)
        }
    }

    fun acceptExtension() {
        viewModelScope.launch(safeHandler) {
            consultationSessionManager.acceptExtension()
        }
    }

    fun declineExtension() {
        viewModelScope.launch(safeHandler) {
            consultationSessionManager.declineExtension()
        }
    }

    private fun initChat() {
        viewModelScope.launch(safeHandler) {
            try {
                Log.d(TAG, "initChat START consultationId=$consultationId")
                val session = authRepository.currentSession.first()
                Log.d(TAG, "initChat session=${session != null}, userId=${session?.user?.id}, role=${session?.user?.role}")
                val patientId = session?.user?.id ?: run {
                    Log.e(TAG, "initChat ABORT: no session/userId")
                    return@launch
                }
                // Patient sender_id must be the session UUID (from JWT sub/session_id claim),
                // NOT the human-readable patient_id — the edge function validates
                // senderId === auth.sessionId which is the session UUID.
                val sessionId = getSessionIdFromToken() ?: run {
                    Log.e(TAG, "initChat ABORT: could not extract session_id from JWT")
                    return@launch
                }
                Log.d(TAG, "initChat: patientId=$patientId, sessionId(sender)=$sessionId")
                _uiState.update { it.copy(currentUserId = sessionId, currentUserType = "patient") }

                // Ensure consultation exists in Room so resume FAB works
                val existing = consultationDao.getById(consultationId)
                if (existing == null) {
                    val now = System.currentTimeMillis()
                    consultationDao.insert(
                        ConsultationEntity(
                            consultationId = consultationId,
                            patientSessionId = sessionId,
                            doctorId = "",
                            status = "ACTIVE",
                            serviceType = "general",
                            consultationFee = 0,
                            requestExpiresAt = now,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                } else if (existing.status != "ACTIVE") {
                    consultationDao.updateStatus(consultationId, "ACTIVE")
                }

                // Observe local messages reactively
                Log.d(TAG, "initChat: observing local messages")
                messageRepository.getByConsultationId(consultationId)
                    .onEach { messages ->
                        Log.d(TAG, "initChat: local messages update count=${messages.size}")
                        _uiState.update { it.copy(messages = messages, isLoading = false) }
                    }
                    .launchIn(viewModelScope)

                // Import auth token so Supabase Realtime WebSocket authenticates
                // as "authenticated" role (required for RLS to pass).
                // Without this, Realtime connects as anon and receives zero events.
                try {
                    val freshToken = tokenManager.getAccessTokenSync() ?: session.accessToken
                    supabaseClientProvider.importAuthToken(accessToken = freshToken)
                    Log.d(TAG, "initChat: importAuthToken succeeded")
                } catch (e: Exception) {
                    Log.e(TAG, "initChat: importAuthToken FAILED — Realtime will be degraded", e)
                }

                // Fetch ALL remote messages on first load (no since filter)
                Log.d(TAG, "initChat: fetching remote messages")
                fetchRemoteMessages(fullSync = true)

                // Subscribe to realtime
                Log.d(TAG, "initChat: subscribing to realtime")
                launch { chatRealtimeService.subscribeToMessages(consultationId, viewModelScope) }
                launch { chatRealtimeService.subscribeToTyping(consultationId, viewModelScope) }

                // Process incoming realtime messages from the other party
                chatRealtimeService.messageEvents
                    .filter { it.consultationId == consultationId }
                    .filter { it.senderId != sessionId }
                    .onEach { event ->
                        val messageData = MessageData(
                            messageId = event.messageId,
                            consultationId = event.consultationId,
                            senderType = event.senderType,
                            senderId = event.senderId,
                            messageText = event.messageText,
                            messageType = event.messageType,
                            synced = true,
                            createdAt = parseTimestamp(event.createdAt),
                        )
                        messageRepository.saveMessage(messageData)
                    }
                    .launchIn(viewModelScope)

                // Process typing events — launch auto-clear in separate job (2.3 fix)
                chatRealtimeService.typingEvents
                    .filter { it.consultationId == consultationId }
                    .filter { it.userId != sessionId }
                    .onEach { event ->
                        _uiState.update { it.copy(otherPartyTyping = event.isTyping) }
                        typingClearJob?.cancel()
                        if (event.isTyping) {
                            typingClearJob = viewModelScope.launch {
                                delay(TYPING_DISPLAY_TIMEOUT_MS)
                                _uiState.update { it.copy(otherPartyTyping = false) }
                            }
                        }
                    }
                    .launchIn(viewModelScope)

                // Smart polling: adjust interval based on Realtime connection state (2.6)
                chatRealtimeService.connectionState
                    .onEach { state -> adjustPolling(state) }
                    .launchIn(viewModelScope)

                // Retry any unsent messages from a previous offline session
                Log.d(TAG, "initChat: syncing unsynced messages")
                syncUnsyncedMessages()
                Log.d(TAG, "initChat DONE — all subscriptions active")
            } catch (e: Exception) {
                Log.e(TAG, "initChat CRASHED", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private var currentPollInterval = POLL_FAST_MS

    private fun adjustPolling(state: RealtimeConnectionState) {
        val newInterval = when (state) {
            RealtimeConnectionState.CONNECTED -> POLL_SLOW_MS
            else -> POLL_FAST_MS
        }
        // 5.4: Show/hide connection error banner
        val errorMsg = when (state) {
            RealtimeConnectionState.DISCONNECTED -> "Connection lost \u2014 messages may be delayed"
            RealtimeConnectionState.CONNECTING -> "Reconnecting\u2026"
            RealtimeConnectionState.CONNECTED -> null
        }
        _uiState.update { it.copy(error = errorMsg) }

        // Only restart the poll job if the interval actually changed or no job is running.
        // This prevents cancelling an in-flight fetch when Realtime state flaps rapidly.
        if (newInterval != currentPollInterval || pollJob?.isActive != true) {
            currentPollInterval = newInterval
            pollJob?.cancel()
            Log.d(TAG, "Polling interval set to ${newInterval}ms (realtime=$state)")
            pollJob = viewModelScope.launch(safeHandler) {
                while (isActive) {
                    fetchRemoteMessages()
                    delay(newInterval)
                }
            }
        } else {
            Log.d(TAG, "Polling interval unchanged at ${newInterval}ms (realtime=$state), keeping existing job")
        }
    }

    private suspend fun fetchRemoteMessages(fullSync: Boolean = false) {
        val since = if (fullSync) {
            null
        } else {
            messageRepository.getLatestSyncedTimestamp(consultationId)?.let { ts ->
                Instant.ofEpochMilli(ts).toString()
            }
        }
        when (val result = messageService.getMessages(consultationId, since)) {
            is ApiResult.Success -> {
                val messages = result.data.map { row ->
                    MessageData(
                        messageId = row.messageId,
                        consultationId = row.consultationId,
                        senderType = row.senderType,
                        senderId = row.senderId,
                        messageText = row.messageText,
                        messageType = row.messageType,
                        attachmentUrl = row.attachmentUrl,
                        isRead = row.isRead,
                        synced = true,
                        createdAt = parseTimestamp(row.createdAt),
                    )
                }
                if (messages.isNotEmpty()) {
                    messageRepository.saveMessages(messages)
                }
                Log.d(TAG, "Fetched ${messages.size} remote messages (since=$since)")
            }
            else -> Log.w(TAG, "Failed to fetch remote messages: $result")
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val state = _uiState.value

        // Guard: cannot send without a valid user identity
        if (state.currentUserId.isBlank()) {
            Log.e(TAG, "sendMessage BLOCKED: currentUserId is blank (initChat may not have completed)")
            _uiState.update { it.copy(sendError = "Session not ready. Please wait a moment and try again.") }
            return
        }

        // Clear any previous send error
        _uiState.update { it.copy(sendError = null) }

        val messageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val messageData = MessageData(
            messageId = messageId,
            consultationId = consultationId,
            senderType = state.currentUserType,
            senderId = state.currentUserId,
            messageText = trimmed,
            messageType = "text",
            synced = false,
            createdAt = now,
        )

        viewModelScope.launch(safeHandler) {
            // 1. Insert locally immediately (optimistic UI)
            messageRepository.saveMessage(messageData)

            // 2. Send to Supabase
            when (val result = messageService.sendMessage(
                messageId = messageId,
                consultationId = consultationId,
                senderType = state.currentUserType,
                senderId = state.currentUserId,
                messageText = trimmed,
            )) {
                is ApiResult.Success -> {
                    messageRepository.markAsSynced(messageId)
                    Log.d(TAG, "Message $messageId sent successfully")
                }
                is ApiResult.Error -> {
                    Log.e(TAG, "Failed to send message $messageId: code=${result.code}, msg=${result.message}")
                    _uiState.update { it.copy(sendError = "Message failed to send. Retrying...") }
                    messageQueue.processUnsynced()
                    delay(3000)
                    _uiState.update { it.copy(sendError = null) }
                }
                is ApiResult.Unauthorized -> {
                    Log.e(TAG, "Failed to send message $messageId: UNAUTHORIZED")
                    _uiState.update { it.copy(sendError = "Session expired. Please re-open this consultation.") }
                }
                is ApiResult.NetworkError -> {
                    Log.e(TAG, "Failed to send message $messageId: NETWORK", result.exception)
                    _uiState.update { it.copy(sendError = "Network error. Message will retry automatically.") }
                    messageQueue.processUnsynced()
                    delay(3000)
                    _uiState.update { it.copy(sendError = null) }
                }
            }
        }

        // Clear typing indicator
        sendTypingIndicator(false)
    }

    fun dismissSendError() {
        _uiState.update { it.copy(sendError = null) }
    }

    fun onTypingChanged(isTyping: Boolean) {
        typingJob?.cancel()
        if (isTyping) {
            // Throttle: only send isTyping=true if >2s since last send (2.4 fix)
            val now = System.currentTimeMillis()
            if (now - lastTypingSendMs > TYPING_THROTTLE_MS) {
                lastTypingSendMs = now
                sendTypingIndicator(true)
            }
            typingJob = viewModelScope.launch {
                delay(TYPING_AUTO_CLEAR_MS)
                sendTypingIndicator(false)
            }
        } else {
            sendTypingIndicator(false)
        }
    }

    private fun sendTypingIndicator(isTyping: Boolean) {
        viewModelScope.launch(safeHandler) {
            val userId = _uiState.value.currentUserId
            if (userId.isBlank()) return@launch
            messageService.updateTypingIndicator(consultationId, userId, isTyping)
        }
    }

    private suspend fun syncUnsyncedMessages() {
        val unsynced = messageRepository.getUnsyncedMessages()
        for (msg in unsynced) {
            if (msg.consultationId != consultationId) continue
            when (messageService.sendMessage(
                messageId = msg.messageId,
                consultationId = msg.consultationId,
                senderType = msg.senderType,
                senderId = msg.senderId,
                messageText = msg.messageText,
            )) {
                is ApiResult.Success -> messageRepository.markAsSynced(msg.messageId)
                else -> {} // Will retry next session
            }
        }
    }

    private fun getSessionIdFromToken(): String? {
        val token = tokenManager.getAccessTokenSync() ?: return null
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING))
            JSONObject(payload).optString("session_id", null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode session_id from JWT", e)
            null
        }
    }

    override fun onCleared() {
        Log.d(TAG, "onCleared — ViewModel destroyed")
        super.onCleared()
        typingJob?.cancel()
        typingClearJob?.cancel()
        pollJob?.cancel()
        chatRealtimeService.unsubscribeAllSync()
        consultationSessionManager.stop()
    }

    companion object {
        private const val TAG = "PatientConsultVM"
        private const val POLL_FAST_MS = 3_000L
        private const val POLL_SLOW_MS = 30_000L
        private const val TYPING_THROTTLE_MS = 2_000L
        private const val TYPING_AUTO_CLEAR_MS = 3_000L
        private const val TYPING_DISPLAY_TIMEOUT_MS = 5_000L
    }
}

private fun parseTimestamp(value: String): Long {
    return try {
        Instant.parse(value).toEpochMilli()
    } catch (_: Exception) {
        System.currentTimeMillis()
    }
}
