package com.esiri.esiriplus.feature.doctor.screen

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.core.domain.model.ConsultationPhase
import com.esiri.esiriplus.feature.doctor.R
import com.esiri.esiriplus.feature.chat.ui.ChatContent
import com.esiri.esiriplus.feature.chat.ui.ConsultationTimerBar
import com.esiri.esiriplus.feature.chat.ui.DoctorExtensionOverlay
import com.esiri.esiriplus.feature.chat.ui.GracePeriodBanner
import com.esiri.esiriplus.feature.doctor.viewmodel.DoctorChatViewModel
import com.esiri.esiriplus.feature.doctor.viewmodel.PatientSummaryViewModel
import com.esiri.esiriplus.feature.doctor.viewmodel.SummarySection
import java.io.File

private val BrandTeal = Color(0xFF2A9D8F)

@Composable
private fun DoctorAttachmentOption(
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
fun DoctorConsultationDetailScreen(
    onStartCall: (String, String) -> Unit,
    onWriteReport: (String) -> Unit,
    onBack: () -> Unit,
    onConsultationCompleted: () -> Unit = onBack,
    modifier: Modifier = Modifier,
    viewModel: DoctorChatViewModel = hiltViewModel(),
    summaryViewModel: PatientSummaryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()
    val summaryState by summaryViewModel.uiState.collectAsState()

    // Block back navigation during active consultation
    val isActive = sessionState.phase != ConsultationPhase.COMPLETED
    BackHandler(enabled = isActive) {
        // Swallow back press — doctor must explicitly end consultation
    }

    // Show report bottom sheet when consultation ends (instead of navigating back)
    var showReportSheet by remember { mutableStateOf(false) }

    LaunchedEffect(sessionState.phase) {
        if (sessionState.phase == ConsultationPhase.COMPLETED) {
            showReportSheet = true
        }
    }

    val isInputEnabled = sessionState.phase == ConsultationPhase.ACTIVE
    val canEnd = sessionState.phase == ConsultationPhase.ACTIVE ||
        sessionState.phase == ConsultationPhase.AWAITING_EXTENSION
    var showEndDialog by remember { mutableStateOf(false) }
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

    if (showEndDialog) {
        AlertDialog(
            onDismissRequest = { showEndDialog = false },
            title = { Text(stringResource(R.string.consultation_detail_end_title), color = Color.Black) },
            text = { Text(stringResource(R.string.consultation_detail_end_message), color = Color.Black) },
            confirmButton = {
                TextButton(onClick = {
                    showEndDialog = false
                    viewModel.endConsultation()
                }) {
                    Text(stringResource(R.string.consultation_detail_end_button), color = Color(0xFFDC2626))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDialog = false }) {
                    Text(stringResource(R.string.common_cancel), color = Color.Black)
                }
            },
        )
    }

    // Show error dialog when ending consultation fails
    uiState.endError?.let { errorMsg ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissEndError() },
            title = { Text(stringResource(R.string.consultation_detail_end_error_title), color = Color.Black) },
            text = { Text(errorMsg, color = Color.Black) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissEndError() }) {
                    Text(stringResource(R.string.common_ok), color = BrandTeal)
                }
            },
        )
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
        topBarActions = {
            Box {
                IconButton(onClick = { showCallTypeMenu = true }) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = stringResource(R.string.consultation_detail_start_call),
                        tint = BrandTeal,
                    )
                }
                DropdownMenu(
                    expanded = showCallTypeMenu,
                    onDismissRequest = { showCallTypeMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.consultation_detail_voice_call), color = Color.Black) },
                        onClick = {
                            showCallTypeMenu = false
                            onStartCall(uiState.consultationId, "AUDIO")
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = BrandTeal)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.consultation_detail_video_call), color = Color.Black) },
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
            IconButton(onClick = { showReportSheet = true }) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.consultation_detail_write_report),
                    tint = BrandTeal,
                )
            }
            IconButton(
                onClick = { summaryViewModel.generateSummary() },
                enabled = !summaryState.isGenerating,
            ) {
                if (summaryState.isGenerating) {
                    CircularProgressIndicator(
                        color = BrandTeal,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = stringResource(R.string.consultation_detail_patient_summary),
                        tint = Color(0xFF6366F1),
                    )
                }
            }
            if (canEnd) {
                IconButton(onClick = { showEndDialog = true }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.consultation_detail_end_consultation),
                        tint = Color(0xFFDC2626),
                    )
                }
            }
        },
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
                    DoctorExtensionOverlay(
                        patientDeclined = sessionState.patientDeclined,
                        onEndConsultation = viewModel::endConsultation,
                    )
                }
                ConsultationPhase.GRACE_PERIOD -> {
                    GracePeriodBanner(remainingSeconds = sessionState.remainingSeconds)
                }
                else -> {}
            }
        },
    )

    // Attachment picker dialog
    if (showAttachmentMenu) {
        AlertDialog(
            onDismissRequest = { showAttachmentMenu = false },
            title = { Text(stringResource(R.string.consultation_detail_attach_title), color = Color.Black) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    DoctorAttachmentOption(
                        icon = { Icon(Icons.Default.CameraAlt, contentDescription = null, tint = BrandTeal, modifier = Modifier.size(24.dp)) },
                        label = stringResource(R.string.consultation_detail_camera),
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
                    DoctorAttachmentOption(
                        icon = { Icon(Icons.Default.Image, contentDescription = null, tint = BrandTeal, modifier = Modifier.size(24.dp)) },
                        label = stringResource(R.string.consultation_detail_gallery),
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
                    DoctorAttachmentOption(
                        icon = { Icon(Icons.Default.Description, contentDescription = null, tint = BrandTeal, modifier = Modifier.size(24.dp)) },
                        label = stringResource(R.string.consultation_detail_document),
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

    // Consultation report bottom sheet
    if (showReportSheet) {
        ConsultationReportBottomSheet(
            consultationId = uiState.consultationId,
            onDismiss = {
                showReportSheet = false
                if (sessionState.phase == ConsultationPhase.COMPLETED) {
                    onConsultationCompleted()
                }
            },
            onReportSubmitted = {
                showReportSheet = false
                onConsultationCompleted()
            },
        )
    }

    // Patient summary dialog
    if (summaryState.showSummary) {
        PatientSummaryDialog(
            patientName = summaryState.patientName,
            sections = summaryState.sections,
            onDismiss = { summaryViewModel.dismissSummary() },
        )
    }

    // Summary error dialog
    summaryState.error?.let { errorMsg ->
        AlertDialog(
            onDismissRequest = { summaryViewModel.dismissError() },
            title = { Text(stringResource(R.string.consultation_detail_summary_error_title), color = Color.Black) },
            text = { Text(errorMsg, color = Color.Black) },
            confirmButton = {
                TextButton(onClick = { summaryViewModel.dismissError() }) {
                    Text(stringResource(R.string.common_ok), color = BrandTeal)
                }
            },
        )
    }
}

@Composable
private fun PatientSummaryDialog(
    patientName: String,
    sections: List<SummarySection>,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(end = 40.dp)) {
                                Text(
                                    stringResource(R.string.consultation_detail_patient_medical_summary),
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                )
                                Text(
                                    patientName,
                                    color = BrandTeal,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.align(Alignment.TopEnd),
                            ) {
                                Icon(Icons.Default.Close, stringResource(R.string.common_close), tint = Color.Black)
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFE5E7EB))

                // Scrollable content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    sections.forEach { section ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    section.title,
                                    color = BrandTeal,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    section.content,
                                    color = Color.Black,
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}
