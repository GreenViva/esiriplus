package com.esiri.esiriplus.feature.patient.screen

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.core.domain.model.ConsultationPhase
import com.esiri.esiriplus.feature.chat.ui.ChatContent
import com.esiri.esiriplus.feature.chat.ui.ConsultationTimerBar
import com.esiri.esiriplus.feature.chat.ui.GracePeriodBanner
import com.esiri.esiriplus.feature.chat.ui.PatientExtensionPrompt
import com.esiri.esiriplus.feature.patient.R
import com.esiri.esiriplus.feature.patient.viewmodel.GreetingPhase
import com.esiri.esiriplus.feature.patient.viewmodel.PatientConsultationViewModel
import java.io.File

private val BrandTeal = Color(0xFF2A9D8F)

@Composable
private fun AttachmentOption(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(16.dp))
        Text(label, color = Color.Black)
    }
}

@Composable
fun PatientConsultationScreen(
    onNavigateToPayment: (String, Int, String) -> Unit,
    onNavigateToExtensionPayment: (String, Int, String) -> Unit,
    onStartCall: (String, String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PatientConsultationViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()

    var showRatingSheet by remember { mutableStateOf(false) }

    val isFollowUpMode = uiState.isFollowUpMode

    // Block back navigation during active consultation (not in follow-up mode)
    val isActive = sessionState.phase != ConsultationPhase.COMPLETED
    BackHandler(enabled = isActive && !isFollowUpMode) {
        // Swallow back press — patient must wait for doctor to end consultation
    }

    // Handle consultation phase transitions (skip when in follow-up mode)
    LaunchedEffect(sessionState.phase) {
        if (isFollowUpMode) return@LaunchedEffect
        when (sessionState.phase) {
            ConsultationPhase.COMPLETED -> { showRatingSheet = true }
            ConsultationPhase.GRACE_PERIOD -> {
                if (sessionState.consultationId.isNotBlank()) {
                    onNavigateToExtensionPayment(
                        sessionState.consultationId,
                        sessionState.consultationFee,
                        sessionState.serviceType,
                    )
                }
            }
            else -> { /* no-op */ }
        }
    }

    val isInputEnabled = isFollowUpMode || sessionState.phase == ConsultationPhase.ACTIVE
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var showCallTypeMenu by remember { mutableStateOf(false) }

    // Camera capture URI
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    // File picker launchers
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let { viewModel.sendAttachment(it, context) }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let { viewModel.sendAttachment(it, context) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success: Boolean ->
        if (success) {
            cameraImageUri?.let { viewModel.sendAttachment(it, context) }
        }
    }

    // Permission launchers
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val photoFile = File.createTempFile("photo_", ".jpg", context.cacheDir)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
            cameraImageUri = uri
            cameraLauncher.launch(uri)
        }
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            imagePickerLauncher.launch("image/*")
        }
    }

    ChatContent(
        messages = uiState.messages,
        previousSessionMessages = uiState.previousSessionMessages,
        isLoading = uiState.isLoading,
        currentUserId = uiState.currentUserId,
        otherPartyTyping = uiState.otherPartyTyping,
        consultationId = uiState.consultationId,
        onSendMessage = viewModel::sendMessage,
        onTypingChanged = viewModel::onTypingChanged,
        onBack = {
            if (!isActive) onBack()
        },
        modifier = modifier,
        error = uiState.error,
        sendError = uiState.sendError,
        isInputEnabled = isInputEnabled,
        isUploading = uiState.isUploading,
        onAttachmentClick = { showAttachmentMenu = true },
        topBarActions = {
            Box {
                IconButton(onClick = { showCallTypeMenu = true }) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = stringResource(R.string.consultation_start_call),
                        tint = BrandTeal,
                    )
                }
                DropdownMenu(
                    expanded = showCallTypeMenu,
                    onDismissRequest = { showCallTypeMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.consultation_voice_call), color = Color.Black) },
                        onClick = {
                            showCallTypeMenu = false
                            onStartCall(uiState.consultationId, "AUDIO")
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = BrandTeal)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.consultation_video_call), color = Color.Black) },
                        onClick = {
                            showCallTypeMenu = false
                            onStartCall(uiState.consultationId, "VIDEO")
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Videocam, contentDescription = null, tint = BrandTeal)
                        },
                    )
                }
            }
        },
        timerContent = {
            if (isFollowUpMode) {
                FollowUpModeBanner(followUpExpiry = uiState.followUpExpiry)
            } else if (!sessionState.isLoading) {
                ConsultationTimerBar(
                    phase = sessionState.phase,
                    remainingSeconds = sessionState.remainingSeconds,
                    extensionCount = sessionState.extensionCount,
                )
            }
        },
        bottomOverlay = {
            when (sessionState.phase) {
                ConsultationPhase.AWAITING_EXTENSION -> {
                    if (!sessionState.patientDeclined) {
                        PatientExtensionPrompt(
                            consultationFee = sessionState.consultationFee,
                            durationMinutes = sessionState.originalDurationMinutes,
                            onAccept = viewModel::acceptExtension,
                            onDecline = viewModel::declineExtension,
                        )
                    }
                }
                ConsultationPhase.GRACE_PERIOD -> {
                    GracePeriodBanner(remainingSeconds = sessionState.remainingSeconds)
                }
                else -> {}
            }
        },
    )

    // Greeting overlay — shown once when chat first opens
    val greetingPhase = uiState.greetingPhase
    var showGreetingCallMenu by remember { mutableStateOf(false) }
    if (greetingPhase != GreetingPhase.NONE &&
        (greetingPhase != GreetingPhase.DONE || showGreetingCallMenu)
    ) {
        GreetingOverlay(
            phase = greetingPhase,
            doctorName = uiState.doctorName,
            showCallMenu = showGreetingCallMenu,
            onChooseText = {
                viewModel.onGreetingChooseText()
            },
            onChooseCall = {
                showGreetingCallMenu = true
            },
            onCallTypeSelected = { callType ->
                showGreetingCallMenu = false
                viewModel.onGreetingChooseCall()
                onStartCall(uiState.consultationId, callType)
            },
            onDismissCallMenu = {
                showGreetingCallMenu = false
                viewModel.onGreetingChooseCall()
            },
        )
    }

    // Rating bottom sheet
    if (showRatingSheet) {
        RatingBottomSheet(
            consultationId = uiState.consultationId,
            doctorId = uiState.doctorId,
            patientSessionId = uiState.currentUserId,
            onDismiss = { showRatingSheet = false; onBack() },
            onSubmitSuccess = { showRatingSheet = false; onBack() },
        )
    }

    // Attachment picker dialog
    if (showAttachmentMenu) {
        AlertDialog(
            onDismissRequest = { showAttachmentMenu = false },
            title = { Text(stringResource(R.string.consultation_attach_title), color = Color.Black) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    AttachmentOption(
                        icon = { Icon(Icons.Default.CameraAlt, contentDescription = null, tint = BrandTeal, modifier = Modifier.size(24.dp)) },
                        label = stringResource(R.string.consultation_camera),
                        onClick = {
                            showAttachmentMenu = false
                            val hasCameraPermission = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.CAMERA,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasCameraPermission) {
                                val photoFile = File.createTempFile("photo_", ".jpg", context.cacheDir)
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
                                cameraImageUri = uri
                                cameraLauncher.launch(uri)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                    )
                    AttachmentOption(
                        icon = { Icon(Icons.Default.Image, contentDescription = null, tint = BrandTeal, modifier = Modifier.size(24.dp)) },
                        label = stringResource(R.string.consultation_gallery),
                        onClick = {
                            showAttachmentMenu = false
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.READ_MEDIA_IMAGES,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPermission) {
                                    imagePickerLauncher.launch("image/*")
                                } else {
                                    mediaPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                                }
                            } else {
                                imagePickerLauncher.launch("image/*")
                            }
                        },
                    )
                    AttachmentOption(
                        icon = { Icon(Icons.Default.Description, contentDescription = null, tint = BrandTeal, modifier = Modifier.size(24.dp)) },
                        label = stringResource(R.string.consultation_document),
                        onClick = {
                            showAttachmentMenu = false
                            documentPickerLauncher.launch("application/pdf")
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAttachmentMenu = false }) {
                    Text(stringResource(R.string.common_cancel), color = Color.Black)
                }
            },
        )
    }
}

// ── Greeting Overlay ────────────────────────────────────────────────────────

@Composable
private fun GreetingOverlay(
    phase: GreetingPhase,
    doctorName: String,
    showCallMenu: Boolean,
    onChooseText: () -> Unit,
    onChooseCall: () -> Unit,
    onCallTypeSelected: (String) -> Unit,
    onDismissCallMenu: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            // Typing indicator
            val showTyping = phase == GreetingPhase.TYPING
            androidx.compose.animation.AnimatedVisibility(
                visible = showTyping,
                enter = androidx.compose.animation.fadeIn(
                    androidx.compose.animation.core.tween(200),
                ),
                exit = androidx.compose.animation.fadeOut(
                    androidx.compose.animation.core.tween(150),
                ),
            ) {
                GreetingTypingBubble()
            }

            // Message 1: Welcome
            val showWelcome = phase.ordinal >= GreetingPhase.MSG_WELCOME.ordinal && phase != GreetingPhase.TYPING
            androidx.compose.animation.AnimatedVisibility(
                visible = showWelcome,
                enter = androidx.compose.animation.slideInVertically(
                    androidx.compose.animation.core.tween(300),
                ) { it } + androidx.compose.animation.fadeIn(
                    androidx.compose.animation.core.tween(300),
                ),
            ) {
                GreetingBubble(text = stringResource(R.string.greeting_welcome))
            }

            Spacer(Modifier.height(8.dp))

            // Message 2: Here to serve
            val showServe = phase.ordinal >= GreetingPhase.MSG_SERVE.ordinal && phase != GreetingPhase.TYPING
            androidx.compose.animation.AnimatedVisibility(
                visible = showServe,
                enter = androidx.compose.animation.slideInVertically(
                    androidx.compose.animation.core.tween(300),
                ) { it } + androidx.compose.animation.fadeIn(
                    androidx.compose.animation.core.tween(300),
                ),
            ) {
                GreetingBubble(
                    text = if (doctorName.isNotBlank()) stringResource(R.string.greeting_here_to_serve, doctorName)
                    else stringResource(R.string.greeting_here_to_serve_generic),
                )
            }

            Spacer(Modifier.height(8.dp))

            // Message 3: How to proceed + choice buttons
            val showChoices = phase == GreetingPhase.MSG_CHOICES
            androidx.compose.animation.AnimatedVisibility(
                visible = showChoices,
                enter = androidx.compose.animation.slideInVertically(
                    androidx.compose.animation.core.tween(300),
                ) { it } + androidx.compose.animation.fadeIn(
                    androidx.compose.animation.core.tween(300),
                ),
            ) {
                Column {
                    GreetingBubble(text = stringResource(R.string.greeting_how_to_proceed))
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                    ) {
                        // Call button
                        Box(modifier = Modifier.weight(1f)) {
                            androidx.compose.material3.Button(
                                onClick = onChooseCall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = BrandTeal,
                                ),
                                elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                                    defaultElevation = 4.dp,
                                ),
                            ) {
                                Icon(
                                    Icons.Default.Phone,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.greeting_choice_call),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                )
                            }
                            // Call type dropdown
                            DropdownMenu(
                                expanded = showCallMenu,
                                onDismissRequest = onDismissCallMenu,
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.consultation_voice_call), color = Color.Black) },
                                    onClick = { onCallTypeSelected("AUDIO") },
                                    leadingIcon = { Icon(Icons.Default.Phone, null, tint = BrandTeal) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.consultation_video_call), color = Color.Black) },
                                    onClick = { onCallTypeSelected("VIDEO") },
                                    leadingIcon = { Icon(Icons.Default.Videocam, null, tint = BrandTeal) },
                                )
                            }
                        }
                        // Text button
                        androidx.compose.material3.Button(
                            onClick = onChooseText,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = BrandTeal,
                                contentColor = Color.White,
                            ),
                            elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                                defaultElevation = 4.dp,
                            ),
                        ) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.greeting_choice_text),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GreetingBubble(text: String) {
    Surface(
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp),
        color = BrandTeal,
        shadowElevation = 2.dp,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun GreetingTypingBubble() {
    var dotCount by remember { mutableStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(400)
            dotCount = (dotCount % 3) + 1
        }
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BrandTeal.copy(alpha = 0.8f),
        shadowElevation = 1.dp,
    ) {
        Text(
            text = ".".repeat(dotCount).padEnd(3, ' '),
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
    }
}

// ── Follow-Up Banner ────────────────────────────────────────────────────────

@Composable
private fun FollowUpModeBanner(followUpExpiry: Long?) {
    val daysRemaining = if (followUpExpiry != null) {
        val millis = followUpExpiry - System.currentTimeMillis()
        java.util.concurrent.TimeUnit.MILLISECONDS.toDays(millis).toInt().coerceAtLeast(0)
    } else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(listOf(Color(0xFF4C1D95), Color(0xFF7C3AED))),
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "★ Royal Follow-up Mode",
            color = Color(0xFFF59E0B),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (daysRemaining != null) {
            Text(
                text = "$daysRemaining days left",
                color = Color.White,
                fontSize = 12.sp,
            )
        }
    }
}
