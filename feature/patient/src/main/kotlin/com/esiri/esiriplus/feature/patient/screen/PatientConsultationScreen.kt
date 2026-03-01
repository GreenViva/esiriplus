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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.core.domain.model.ConsultationPhase
import com.esiri.esiriplus.feature.chat.ui.ChatContent
import com.esiri.esiriplus.feature.chat.ui.ConsultationTimerBar
import com.esiri.esiriplus.feature.chat.ui.GracePeriodBanner
import com.esiri.esiriplus.feature.chat.ui.PatientExtensionPrompt
import com.esiri.esiriplus.feature.patient.viewmodel.PatientConsultationViewModel
import java.io.File

private val BrandTeal = Color(0xFF2A9D8F)

@Composable
fun PatientConsultationScreen(
    onNavigateToPayment: (String) -> Unit,
    onNavigateToExtensionPayment: (String, Int, String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PatientConsultationViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()

    // Block back navigation during active consultation
    val isActive = sessionState.phase != ConsultationPhase.COMPLETED
    BackHandler(enabled = isActive) {
        // Swallow back press — patient must wait for doctor to end consultation
    }

    // Auto-navigate back to dashboard when consultation ends
    LaunchedEffect(sessionState.phase) {
        if (sessionState.phase == ConsultationPhase.COMPLETED) {
            onBack()
        }
    }

    // Navigate to extension payment when patient accepts extension (grace period starts)
    LaunchedEffect(sessionState.phase) {
        if (sessionState.phase == ConsultationPhase.GRACE_PERIOD && sessionState.consultationId.isNotBlank()) {
            onNavigateToExtensionPayment(
                sessionState.consultationId,
                sessionState.consultationFee,
                sessionState.serviceType,
            )
        }
    }

    val isInputEnabled = sessionState.phase == ConsultationPhase.ACTIVE
    var showAttachmentMenu by remember { mutableStateOf(false) }

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
        timerContent = {
            if (!sessionState.isLoading) {
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

    // Attachment popup menu
    DropdownMenu(
        expanded = showAttachmentMenu,
        onDismissRequest = { showAttachmentMenu = false },
    ) {
        DropdownMenuItem(
            text = { Text("Camera", color = Color.Black) },
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
            leadingIcon = {
                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = BrandTeal)
            },
        )
        DropdownMenuItem(
            text = { Text("Gallery", color = Color.Black) },
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
            leadingIcon = {
                Icon(Icons.Default.Image, contentDescription = null, tint = BrandTeal)
            },
        )
        DropdownMenuItem(
            text = { Text("Document", color = Color.Black) },
            onClick = {
                showAttachmentMenu = false
                documentPickerLauncher.launch("application/*")
            },
            leadingIcon = {
                Icon(Icons.Default.Description, contentDescription = null, tint = BrandTeal)
            },
        )
    }
}
