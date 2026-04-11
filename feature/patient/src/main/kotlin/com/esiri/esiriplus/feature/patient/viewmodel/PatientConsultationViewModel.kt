package com.esiri.esiriplus.feature.patient.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esiri.esiriplus.core.common.util.WatermarkUtil
import com.esiri.esiriplus.core.database.dao.ConsultationDao
import com.esiri.esiriplus.core.database.dao.DoctorProfileDao
import com.esiri.esiriplus.core.database.entity.ConsultationEntity
import com.esiri.esiriplus.core.domain.repository.AuthRepository
import com.esiri.esiriplus.core.domain.repository.MessageData
import com.esiri.esiriplus.core.domain.repository.MessageRepository
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.domain.model.ConsultationSessionState
import com.esiri.esiriplus.core.network.SupabaseClientProvider
import com.esiri.esiriplus.core.network.TokenManager
import com.esiri.esiriplus.core.network.service.ChatRealtimeService
import com.esiri.esiriplus.core.network.storage.FileUploadService
import com.esiri.esiriplus.core.network.service.ConsultationSessionManager
import com.esiri.esiriplus.core.network.service.MessageQueue
import com.esiri.esiriplus.core.network.service.MessageService
import com.esiri.esiriplus.core.network.service.RealtimeConnectionState
import com.esiri.esiriplus.feature.patient.R
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

/** Tracks the auto-greeting flow shown once when the chat first opens. */
enum class GreetingPhase {
    /** Greeting sequence not started or already completed. */
    NONE,
    /** Typing indicator visible — "doctor is typing". */
    TYPING,
    /** First message visible: "Hello, welcome!" */
    MSG_WELCOME,
    /** Second message visible: "We are here to serve you." */
    MSG_SERVE,
    /** Third message visible: "How would you like to proceed?" + choice buttons. */
    MSG_CHOICES,
    /** Patient picked a choice — greeting done. */
    DONE,
}

data class ChatUiState(
    val previousSessionMessages: List<MessageData> = emptyList(),
    val messages: List<MessageData> = emptyList(),
    val isLoading: Boolean = true,
    val currentUserId: String = "",
    val currentUserType: String = "patient",
    val otherPartyTyping: Boolean = false,
    val consultationId: String = "",
    val doctorId: String = "",
    val doctorName: String = "",
    val error: String? = null,
    val sendError: String? = null,
    val isUploading: Boolean = false,
    /** True when a Royal consultation has completed but is still within its 14-day follow-up window. */
    val isFollowUpMode: Boolean = false,
    /** Epoch millis when the follow-up window closes (null when not in follow-up mode). */
    val followUpExpiry: Long? = null,
    /** Auto-greeting flow state. */
    val greetingPhase: GreetingPhase = GreetingPhase.NONE,
)

@HiltViewModel
class PatientConsultationViewModel @Inject constructor(
    private val application: Application,
    savedStateHandle: SavedStateHandle,
    private val messageRepository: MessageRepository,
    private val messageService: MessageService,
    private val chatRealtimeService: ChatRealtimeService,
    private val authRepository: AuthRepository,
    private val consultationDao: ConsultationDao,
    private val doctorProfileDao: DoctorProfileDao,
    private val messageQueue: MessageQueue,
    private val consultationSessionManager: ConsultationSessionManager,
    private val tokenManager: TokenManager,
    private val supabaseClientProvider: SupabaseClientProvider,
    private val fileUploadService: FileUploadService,
) : ViewModel() {

    val consultationId: String = savedStateHandle["consultationId"] ?: ""

    private val _uiState = MutableStateFlow(ChatUiState(consultationId = consultationId))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val sessionState: StateFlow<ConsultationSessionState> = consultationSessionManager.state

    private var typingJob: Job? = null
    private var pollJob: Job? = null
    private var typingClearJob: Job? = null
    private var lastTypingSendMs = 0L

    /**
     * Ensures the patient JWT is valid before a critical call.
     * If missing or expiring within 2 minutes, proactively refreshes.
     * This prevents 401s during active consultations.
     */
    private suspend fun ensureValidToken() {
        val token = tokenManager.getAccessTokenSync()
        if (token == null || tokenManager.isTokenExpiringSoon(2)) {
            try {
                authRepository.refreshSession()
                Log.d(TAG, "Patient token refreshed proactively")
            } catch (_: Exception) {
                Log.w(TAG, "Proactive token refresh failed")
            }
        }
    }

    /** Catches any uncaught coroutine exception to prevent app crash. */
    private val safeHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable is kotlinx.coroutines.CancellationException) throw throwable
        Log.e(TAG, "Unhandled coroutine exception (swallowed to prevent crash)", throwable)
    }

    init {
        if (consultationId.isNotBlank()) {
            // Start session manager only for non-follow-up consultations.
            // Follow-up mode (Royal, completed, within 14-day window) has no timer — starting the
            // session manager would re-sync phase = COMPLETED and race against isFollowUpMode.
            // Always start the session manager — it syncs with the server to
            // determine the correct phase. This handles reopened consultations
            // where Room's cache is stale (still shows completed/not-reopened).
            viewModelScope.launch(safeHandler) {
                consultationSessionManager.start(consultationId, viewModelScope)
            }
            initChat()
        }
    }

    fun acceptExtension() {
        viewModelScope.launch(safeHandler) {
            ensureValidToken()
            consultationSessionManager.acceptExtension()
        }
    }

    fun declineExtension() {
        viewModelScope.launch(safeHandler) {
            ensureValidToken()
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
                } else {
                    val doctorName = doctorProfileDao.getById(existing.doctorId)?.fullName ?: ""
                    val now = System.currentTimeMillis()
                    // Check follow-up mode — will be corrected by session manager sync
                    // if the consultation was actually reopened (server status = active).
                    val isFollowUp = existing.serviceTier.uppercase() == "ROYAL" &&
                        existing.status.lowercase() == "completed" &&
                        (existing.followUpExpiry ?: 0L) > now
                    _uiState.update {
                        it.copy(
                            doctorId = existing.doctorId,
                            doctorName = doctorName,
                            isFollowUpMode = isFollowUp,
                            followUpExpiry = if (isFollowUp) existing.followUpExpiry else null,
                        )
                    }
                    if (!isFollowUp && existing.status != "ACTIVE") {
                        consultationDao.updateStatus(consultationId, "ACTIVE")
                    }
                }

                // Resolve doctor name if not yet available (e.g. profile fetched later)
                if (_uiState.value.doctorName.isBlank()) {
                    val name = consultationDao.getDoctorNameForConsultation(consultationId)
                    if (!name.isNullOrBlank()) {
                        _uiState.update { it.copy(doctorName = name) }
                    }
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

                // Ensure token is fresh before any server calls
                ensureValidToken()

                // Fetch ALL remote messages on first load (no since filter).
                // Always request parent history — the server returns nothing extra
                // if this consultation has no parent, so it's a safe no-op.
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
                            attachmentUrl = event.attachmentUrl,
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

                // Correct isFollowUpMode if session manager sync reveals the
                // consultation is actually active (reopened), not passive follow-up.
                launch {
                    consultationSessionManager.state.collect { state ->
                        if (state.phase == com.esiri.esiriplus.core.domain.model.ConsultationPhase.ACTIVE &&
                            _uiState.value.isFollowUpMode
                        ) {
                            Log.d(TAG, "Session manager sync corrected: consultation is ACTIVE, disabling follow-up mode")
                            _uiState.update { it.copy(isFollowUpMode = false) }
                        }
                        // Backfill doctorId from Room once session manager has synced
                        // (new consultations start with doctorId="" in uiState).
                        if (_uiState.value.doctorId.isBlank() && !state.isLoading) {
                            val entity = consultationDao.getById(consultationId)
                            if (entity != null && entity.doctorId.isNotBlank()) {
                                val name = doctorProfileDao.getById(entity.doctorId)?.fullName ?: ""
                                _uiState.update { it.copy(doctorId = entity.doctorId, doctorName = name) }
                                Log.d(TAG, "Backfilled doctorId=${entity.doctorId} from Room after sync")
                            }
                        }
                    }
                }

                // Start the auto-greeting sequence:
                // - New consultations (no messages) → always show
                // - Reopened follow-ups (isReopened) → show again for the new session
                // - Passive follow-up mode (Royal, completed, browsing) → skip
                val currentConsultation = consultationDao.getById(consultationId)
                val isReopenedSession = currentConsultation?.isReopened == true
                if (_uiState.value.messages.isEmpty() || isReopenedSession) {
                    if (!_uiState.value.isFollowUpMode || isReopenedSession) {
                        startGreetingSequence()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "initChat CRASHED", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * Runs the staged greeting sequence with typing-indicator pauses between each message.
     * TYPING → MSG_WELCOME → MSG_SERVE → MSG_CHOICES (waits for patient pick).
     */
    private fun startGreetingSequence() {
        viewModelScope.launch {
            _uiState.update { it.copy(greetingPhase = GreetingPhase.TYPING) }
            delay(1200)
            _uiState.update { it.copy(greetingPhase = GreetingPhase.MSG_WELCOME) }
            delay(800)
            _uiState.update { it.copy(greetingPhase = GreetingPhase.TYPING) }
            delay(1000)
            _uiState.update { it.copy(greetingPhase = GreetingPhase.MSG_SERVE) }
            delay(800)
            _uiState.update { it.copy(greetingPhase = GreetingPhase.TYPING) }
            delay(1000)
            _uiState.update { it.copy(greetingPhase = GreetingPhase.MSG_CHOICES) }
        }
    }

    /** Patient chose "Text Messages" — dismiss greeting, send a system-like prompt. */
    fun onGreetingChooseText() {
        _uiState.update { it.copy(greetingPhase = GreetingPhase.DONE) }
        sendMessage(application.getString(R.string.greeting_text_auto_message))
    }

    /** Patient chose "Call" — returns true so the UI can open the call-type dialog. */
    fun onGreetingChooseCall() {
        _uiState.update { it.copy(greetingPhase = GreetingPhase.DONE) }
    }

    private var currentPollInterval = POLL_FAST_MS

    private fun adjustPolling(state: RealtimeConnectionState) {
        val newInterval = when (state) {
            RealtimeConnectionState.CONNECTED -> POLL_SLOW_MS
            else -> POLL_FAST_MS
        }
        // 5.4: Show/hide connection error banner
        val errorMsg = when (state) {
            RealtimeConnectionState.DISCONNECTED -> application.getString(R.string.vm_connection_lost)
            RealtimeConnectionState.CONNECTING -> application.getString(R.string.vm_reconnecting)
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

    private suspend fun fetchRemoteMessages(fullSync: Boolean = false, includeParent: Boolean = false) {
        ensureValidToken()
        val since = if (fullSync) {
            null
        } else {
            messageRepository.getLatestSyncedTimestamp(consultationId)?.let { ts ->
                Instant.ofEpochMilli(ts).toString()
            }
        }
        when (val result = messageService.getMessages(consultationId, since, includeParent = includeParent && fullSync)) {
            is ApiResult.Success -> {
                val previousSession = mutableListOf<MessageData>()
                val current = mutableListOf<MessageData>()
                for (row in result.data) {
                    val msg = MessageData(
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
                        isFromPreviousSession = row.isFromPreviousSession,
                    )
                    if (row.isFromPreviousSession) {
                        previousSession.add(msg)
                    } else {
                        current.add(msg)
                    }
                }
                if (current.isNotEmpty()) {
                    messageRepository.saveMessages(current)
                }
                if (previousSession.isNotEmpty()) {
                    _uiState.update { it.copy(previousSessionMessages = previousSession) }
                }
                Log.d(TAG, "Fetched ${current.size} current + ${previousSession.size} previous messages (since=$since)")
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
            _uiState.update { it.copy(sendError = application.getString(R.string.vm_session_not_ready)) }
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

            // 2. Ensure token is fresh before sending
            ensureValidToken()

            // 3. Send to Supabase
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
                    _uiState.update { it.copy(sendError = application.getString(R.string.vm_message_failed_retrying)) }
                    messageQueue.processUnsynced()
                    delay(3000)
                    _uiState.update { it.copy(sendError = null) }
                }
                is ApiResult.Unauthorized -> {
                    Log.e(TAG, "Failed to send message $messageId: UNAUTHORIZED")
                    _uiState.update { it.copy(sendError = application.getString(R.string.vm_session_expired_reopen)) }
                }
                is ApiResult.NetworkError -> {
                    Log.e(TAG, "Failed to send message $messageId: NETWORK", result.exception)
                    _uiState.update { it.copy(sendError = application.getString(R.string.vm_network_error_retry)) }
                    messageQueue.processUnsynced()
                    delay(3000)
                    _uiState.update { it.copy(sendError = null) }
                }
            }
        }

        // Clear typing indicator
        sendTypingIndicator(false)
    }

    fun sendAttachment(uri: Uri, context: Context) {
        val state = _uiState.value
        if (state.currentUserId.isBlank() || state.isUploading) return

        _uiState.update { it.copy(isUploading = true, sendError = null) }

        viewModelScope.launch(safeHandler) {
            ensureValidToken()
            try {
                val contentResolver = context.contentResolver
                val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

                // Only allow images and PDFs
                if (!mimeType.startsWith("image/") && mimeType != "application/pdf") {
                    _uiState.update {
                        it.copy(isUploading = false, sendError = application.getString(R.string.vm_unsupported_file_type))
                    }
                    delay(3000)
                    _uiState.update { it.copy(sendError = null) }
                    return@launch
                }

                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Cannot read file")

                if (bytes.size > 10 * 1024 * 1024) {
                    _uiState.update {
                        it.copy(isUploading = false, sendError = application.getString(R.string.vm_file_too_large))
                    }
                    delay(3000)
                    _uiState.update { it.copy(sendError = null) }
                    return@launch
                }

                val isImage = mimeType.startsWith("image/")
                val messageType = if (isImage) "image" else "document"
                val fileName = getFileNameFromUri(uri, context)

                // Apply eSIRI watermark for traceability
                val uploadBytes = when {
                    isImage -> {
                        val doctorName = getDoctorNameForWatermark()
                        WatermarkUtil.applyImageWatermark(bytes, doctorName, mimeType)
                    }
                    mimeType == "application/pdf" -> {
                        val doctorName = getDoctorNameForWatermark()
                        WatermarkUtil.applyPdfWatermark(context, bytes, doctorName)
                    }
                    else -> bytes
                }

                // WatermarkUtil re-encodes images: PNG→PNG, WebP→WebP, everything else
                // (incl. HEIC/HEIF from gallery) → JPEG. Normalize to the actual output
                // format so Supabase Storage accepts the upload.
                val (uploadMimeType, uploadExt) = when {
                    isImage && mimeType.contains("png", ignoreCase = true) -> "image/png" to "png"
                    isImage && mimeType.contains("webp", ignoreCase = true) -> "image/webp" to "webp"
                    isImage -> "image/jpeg" to "jpg"
                    else -> "application/pdf" to "pdf"
                }
                val displayFileName = fileName ?: "file.$uploadExt"

                val storagePath = "$consultationId/${UUID.randomUUID()}.$uploadExt"
                val uploadResult = fileUploadService.uploadFile(
                    bucketName = ATTACHMENT_BUCKET,
                    path = storagePath,
                    bytes = uploadBytes,
                    contentType = uploadMimeType,
                )

                when (uploadResult) {
                    is ApiResult.Success -> {
                        val publicUrl = fileUploadService.getPublicUrl(ATTACHMENT_BUCKET, storagePath)
                        val messageId = UUID.randomUUID().toString()
                        val now = System.currentTimeMillis()
                        val messageData = MessageData(
                            messageId = messageId,
                            consultationId = consultationId,
                            senderType = state.currentUserType,
                            senderId = state.currentUserId,
                            messageText = displayFileName,
                            messageType = messageType,
                            attachmentUrl = publicUrl,
                            synced = false,
                            createdAt = now,
                        )
                        messageRepository.saveMessage(messageData)

                        when (val sendResult = messageService.sendMessage(
                            messageId = messageId,
                            consultationId = consultationId,
                            senderType = state.currentUserType,
                            senderId = state.currentUserId,
                            messageText = displayFileName,
                            messageType = messageType,
                            attachmentUrl = publicUrl,
                        )) {
                            is ApiResult.Success -> {
                                messageRepository.markAsSynced(messageId)
                                Log.d(TAG, "Attachment $messageId sent successfully")
                            }
                            else -> {
                                Log.e(TAG, "Failed to send attachment message: $sendResult")
                                _uiState.update { it.copy(sendError = application.getString(R.string.vm_failed_send_attachment_retrying)) }
                                messageQueue.processUnsynced()
                                delay(3000)
                                _uiState.update { it.copy(sendError = null) }
                            }
                        }
                    }
                    else -> {
                        Log.e(TAG, "Failed to upload file: $uploadResult")
                        _uiState.update { it.copy(sendError = application.getString(R.string.vm_failed_upload_file)) }
                        delay(3000)
                        _uiState.update { it.copy(sendError = null) }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "sendAttachment failed", e)
                _uiState.update { it.copy(sendError = application.getString(R.string.vm_failed_send_attachment)) }
                delay(3000)
                _uiState.update { it.copy(sendError = null) }
            } finally {
                _uiState.update { it.copy(isUploading = false) }
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri, context: Context): String? {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    return cursor.getString(nameIndex)
                }
            }
        }
        return uri.lastPathSegment
    }

    private fun getDoctorNameForWatermark(): String {
        val cached = _uiState.value.doctorName
        return if (cached.isNotBlank()) cached else "Unknown"
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
                messageType = msg.messageType,
                attachmentUrl = msg.attachmentUrl,
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
        private const val ATTACHMENT_BUCKET = "message-attachments"
    }
}

private fun parseTimestamp(value: String): Long {
    return try {
        Instant.parse(value).toEpochMilli()
    } catch (_: Exception) {
        System.currentTimeMillis()
    }
}
