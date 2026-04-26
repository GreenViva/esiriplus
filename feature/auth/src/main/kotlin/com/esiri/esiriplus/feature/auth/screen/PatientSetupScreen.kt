package com.esiri.esiriplus.feature.auth.screen

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.esiri.esiriplus.feature.auth.R
import com.esiri.esiriplus.core.ui.theme.Geist
import com.esiri.esiriplus.core.ui.theme.Hairline
import com.esiri.esiriplus.core.ui.theme.Ink
import com.esiri.esiriplus.core.ui.theme.InstrumentSerif
import com.esiri.esiriplus.core.ui.theme.Muted
import com.esiri.esiriplus.core.ui.theme.pressableClick
import com.esiri.esiriplus.core.ui.theme.Teal
import com.esiri.esiriplus.core.ui.theme.TealBg
import com.esiri.esiriplus.core.ui.theme.TealDeep
import com.esiri.esiriplus.core.ui.theme.TealSoft
import com.esiri.esiriplus.feature.auth.viewmodel.PatientSetupViewModel

private val CardDark1   = Color(0xFF14302A)
private val CardDark2   = Color(0xFF1E8E76)
private val WarnBg      = Color(0xFFFFF8E8)
private val WarnBorder  = Color(0xFFF4E1B0)
private val WarnIconBg  = Color(0xFFF7DD7E)
private val WarnIconFg  = Color(0xFF7A5A0A)
private val WarnTextFg  = Color(0xFF5A4400)
private val WarnTitleFg = Color(0xFF3D2E00)
private val HeartBg     = Color(0xFFFCE7E9)
private val HeartFg     = Color(0xFFC84856)

private val AGE_GROUPS = listOf("Under 18", "18-25", "26-35", "36-45", "46-55", "56-65", "65+")
private val BLOOD_TYPES = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientSetupScreen(
    onComplete: () -> Unit,
    onBack: () -> Unit,
    onNavigateToRecoveryQuestions: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PatientSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var healthSheetOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onComplete()
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.resolveCurrentLocation() }

    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    LaunchedEffect(state.patientId) {
        if (state.patientId.isBlank()) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.resolveCurrentLocation()
    }

    Scaffold(
        modifier = modifier,
        containerColor = TealBg,
        topBar = { PatientIdTopBar(onBack = onBack) },
        bottomBar = {
            if (!state.isCreatingSession && state.sessionError == null) {
                ContinueBottomBar(
                    isSaving = state.isSaving,
                    enabled = state.patientId.isNotBlank() && !state.isSaving,
                    saveError = state.saveError,
                    onContinue = viewModel::onContinue,
                    onSkip = onComplete,
                )
            }
        },
    ) { padding ->
        when {
            state.isCreatingSession -> SessionLoading(padding)
            state.sessionError != null -> SessionErrorView(
                padding = padding,
                error = state.sessionError!!,
                onRetry = viewModel::retryCreateSession,
            )
            else -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
            ) {
                StepStrip(currentStep = 3, totalSteps = 3)

                Spacer(Modifier.height(2.dp))

                Text(
                    text = buildAnnotatedString {
                        append(stringResource(R.string.setup_headline_prefix))
                        withStyle(
                            SpanStyle(
                                color = TealDeep,
                                fontStyle = FontStyle.Italic,
                                fontFamily = InstrumentSerif,
                            ),
                        ) { append(stringResource(R.string.setup_headline_accent)) }
                    },
                    fontFamily = InstrumentSerif,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 26.sp,
                    color = Ink,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.setup_headline_subtitle),
                    fontFamily = Geist,
                    fontSize = 12.sp,
                    color = Muted,
                    lineHeight = 16.sp,
                )

                Spacer(Modifier.height(12.dp))

                IdCard(
                    patientId = state.patientId,
                    copied = copied,
                    onCopy = {
                        clipboard.setText(AnnotatedString(state.patientId))
                        copied = true
                    },
                    onSave = { viewModel.downloadIdCard(context) },
                    canSave = state.canDownloadPdf,
                    isSaving = state.isGeneratingPdf,
                )

                state.pdfError?.let { error ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = error,
                        fontFamily = Geist,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(10.dp))

                SaveTip()

                Spacer(Modifier.height(12.dp))

                OptionRow(
                    icon = Icons.Outlined.Lock,
                    iconBg = TealSoft,
                    iconTint = TealDeep,
                    title = stringResource(R.string.setup_option_recovery_title),
                    tag = if (state.recoveryQuestionsCompleted) {
                        stringResource(R.string.setup_option_recovery_done)
                    } else {
                        stringResource(R.string.setup_option_recovery_recommended)
                    },
                    tagStyle = OptionTagStyle.Teal,
                    description = if (state.recoveryQuestionsCompleted) {
                        stringResource(R.string.setup_option_recovery_completed)
                    } else {
                        stringResource(R.string.setup_option_recovery_pending)
                    },
                    onClick = onNavigateToRecoveryQuestions,
                )

                Spacer(Modifier.height(8.dp))

                OptionRow(
                    icon = Icons.Outlined.Favorite,
                    iconBg = HeartBg,
                    iconTint = HeartFg,
                    title = stringResource(R.string.setup_option_health_title),
                    tag = stringResource(R.string.setup_option_health_optional),
                    tagStyle = OptionTagStyle.Neutral,
                    description = stringResource(R.string.setup_option_health_subtitle),
                    onClick = { healthSheetOpen = true },
                )
            }
        }
    }

    if (healthSheetOpen) {
        HealthProfileSheet(
            viewModel = viewModel,
            onDismiss = { healthSheetOpen = false },
        )
    }
}

@Composable
private fun ContinueBottomBar(
    isSaving: Boolean,
    enabled: Boolean,
    saveError: String?,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TealBg)
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        saveError?.let { error ->
            Text(
                text = error,
                fontFamily = Geist,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(13.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TealDeep,
                contentColor = Color.White,
            ),
            contentPadding = PaddingValues(vertical = 12.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
            enabled = enabled,
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
            } else {
                Text(
                    text = stringResource(R.string.setup_continue),
                    fontFamily = Geist,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Outlined.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.setup_skip_prefix),
                fontFamily = Geist,
                fontSize = 11.sp,
                color = Muted,
            )
            Text(
                text = stringResource(R.string.setup_skip_action),
                fontFamily = Geist,
                fontSize = 11.sp,
                color = TealDeep,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(enabled = !isSaving, onClick = onSkip),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatientIdTopBar(onBack: () -> Unit) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = TealBg),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.dp, Hairline, CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = Ink,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
        title = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.ic_stethoscope),
                    contentDescription = "eSIRI Plus",
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape),
                )
            }
        },
    )
}

@Composable
private fun StepStrip(currentStep: Int, totalSteps: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.setup_step_label),
            fontFamily = Geist,
            fontSize = 12.sp,
            color = Muted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(totalSteps) { index ->
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (index < currentStep) Teal else Hairline),
                )
            }
        }
    }
}

@Composable
private fun IdCard(
    patientId: String,
    copied: Boolean,
    onCopy: () -> Unit,
    onSave: () -> Unit,
    canSave: Boolean,
    isSaving: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(CardDark1, CardDark2)))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.setup_id_label),
                    fontFamily = Geist,
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 1.3.sp,
                )
            }

            Text(
                text = patientId.ifBlank { stringResource(R.string.setup_id_placeholder) },
                fontFamily = Geist,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp,
                color = Color.White,
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = stringResource(R.string.setup_id_hint),
                fontFamily = Geist,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.7f),
            )

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IdActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.ContentCopy,
                    label = if (copied) {
                        stringResource(R.string.setup_id_copied)
                    } else {
                        stringResource(R.string.setup_id_copy)
                    },
                    onClick = onCopy,
                    enabled = patientId.isNotBlank(),
                )
                IdActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Download,
                    label = if (isSaving) {
                        stringResource(R.string.setup_id_saving)
                    } else {
                        stringResource(R.string.setup_id_save)
                    },
                    onClick = onSave,
                    enabled = canSave && !isSaving,
                )
            }
        }
    }
}

@Composable
private fun IdActionButton(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Color.White.copy(alpha = if (enabled) 0.16f else 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(9.dp))
            .pressableClick(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.5f),
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            fontFamily = Geist,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = if (enabled) 1f else 0.5f),
        )
    }
}

@Composable
private fun SaveTip() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(WarnBg)
            .border(1.dp, WarnBorder, RoundedCornerShape(11.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(WarnIconBg),
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = WarnIconFg,
                modifier = Modifier.size(13.dp),
            )
        }

        Spacer(Modifier.width(8.dp))

        Column {
            Text(
                text = stringResource(R.string.setup_tip_title),
                fontFamily = Geist,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = WarnTitleFg,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = stringResource(R.string.setup_tip_body),
                fontFamily = Geist,
                fontSize = 10.sp,
                color = WarnTextFg,
                lineHeight = 14.sp,
            )
        }
    }
}

private enum class OptionTagStyle { Teal, Neutral }

@Composable
private fun OptionRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    tag: String,
    tagStyle: OptionTagStyle,
    description: String,
    onClick: () -> Unit,
) {
    val tagBg = if (tagStyle == OptionTagStyle.Teal) TealSoft else Color(0xFFFFF1E0)
    val tagFg = if (tagStyle == OptionTagStyle.Teal) TealDeep else Color(0xFFB86A1A)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, Hairline, RoundedCornerShape(12.dp))
            .pressableClick(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(iconBg),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(14.dp),
            )
        }

        Spacer(Modifier.width(9.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontFamily = Geist,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = tag,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(tagBg)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                    fontFamily = Geist,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                    color = tagFg,
                )
            }
            Spacer(Modifier.height(1.dp))
            Text(
                text = description,
                fontFamily = Geist,
                fontSize = 10.sp,
                color = Muted,
                lineHeight = 13.sp,
            )
        }

        Spacer(Modifier.width(6.dp))

        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = Muted,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun SessionLoading(padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = TealDeep)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.patient_setup_creating_account),
                fontFamily = Geist,
                fontSize = 14.sp,
                color = Muted,
            )
        }
    }
}

@Composable
private fun SessionErrorView(
    padding: PaddingValues,
    error: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = TealDeep),
            ) {
                Text(stringResource(R.string.patient_setup_retry))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HealthProfileSheet(
    viewModel: PatientSetupViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.setup_option_health_title),
                fontFamily = InstrumentSerif,
                fontSize = 22.sp,
                fontWeight = FontWeight.Normal,
                color = Ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.patient_setup_health_profile_subtitle),
                fontFamily = Geist,
                fontSize = 13.sp,
                color = Muted,
                lineHeight = 18.sp,
            )

            Spacer(Modifier.height(20.dp))

            HealthProfileForm(
                state = state,
                onSexChanged = viewModel::onSexChanged,
                onAgeGroupChanged = viewModel::onAgeGroupChanged,
                onBloodTypeChanged = viewModel::onBloodTypeChanged,
                onAllergiesChanged = viewModel::onAllergiesChanged,
                onChronicConditionsChanged = viewModel::onChronicConditionsChanged,
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TealDeep,
                    contentColor = Color.White,
                ),
                contentPadding = PaddingValues(vertical = 13.dp),
            ) {
                Text(
                    text = stringResource(R.string.setup_health_sheet_done),
                    fontFamily = Geist,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HealthProfileForm(
    state: com.esiri.esiriplus.feature.auth.viewmodel.PatientSetupUiState,
    onSexChanged: (String) -> Unit,
    onAgeGroupChanged: (String) -> Unit,
    onBloodTypeChanged: (String) -> Unit,
    onAllergiesChanged: (String) -> Unit,
    onChronicConditionsChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column {
            Text(
                text = stringResource(R.string.patient_setup_sex_label),
                fontFamily = Geist,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            val sexOptions = listOf(
                "Male" to stringResource(R.string.patient_setup_male),
                "Female" to stringResource(R.string.patient_setup_female),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                sexOptions.forEach { (sex, label) ->
                    FilterChip(
                        selected = state.sex == sex,
                        onClick = { onSexChanged(sex) },
                        label = {
                            Text(
                                text = label,
                                fontFamily = Geist,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TealDeep,
                            selectedLabelColor = Color.White,
                        ),
                        enabled = !state.isSaving,
                    )
                }
            }
        }

        Column {
            Text(
                text = stringResource(R.string.patient_setup_age_group_label),
                fontFamily = Geist,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            var ageExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = ageExpanded,
                onExpandedChange = { ageExpanded = it },
            ) {
                OutlinedTextField(
                    value = state.ageGroup,
                    onValueChange = {},
                    readOnly = true,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.patient_setup_age_group_placeholder),
                            color = Muted,
                            fontSize = 14.sp,
                        )
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = ageExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    enabled = !state.isSaving,
                    shape = RoundedCornerShape(10.dp),
                )
                ExposedDropdownMenu(
                    expanded = ageExpanded,
                    onDismissRequest = { ageExpanded = false },
                ) {
                    AGE_GROUPS.forEach { group ->
                        DropdownMenuItem(
                            text = { Text(group, fontFamily = Geist) },
                            onClick = {
                                onAgeGroupChanged(group)
                                ageExpanded = false
                            },
                        )
                    }
                }
            }
        }

        Column {
            Text(
                text = stringResource(R.string.patient_setup_blood_type_label),
                fontFamily = Geist,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            var bloodExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = bloodExpanded,
                onExpandedChange = { bloodExpanded = it },
            ) {
                OutlinedTextField(
                    value = state.bloodType,
                    onValueChange = {},
                    readOnly = true,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.patient_setup_blood_type_placeholder),
                            color = Muted,
                            fontSize = 14.sp,
                        )
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = bloodExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    enabled = !state.isSaving,
                    shape = RoundedCornerShape(10.dp),
                )
                ExposedDropdownMenu(
                    expanded = bloodExpanded,
                    onDismissRequest = { bloodExpanded = false },
                ) {
                    BLOOD_TYPES.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type, fontFamily = Geist) },
                            onClick = {
                                onBloodTypeChanged(type)
                                bloodExpanded = false
                            },
                        )
                    }
                }
            }
        }

        Column {
            Text(
                text = stringResource(R.string.patient_setup_allergies_label),
                fontFamily = Geist,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            OutlinedTextField(
                value = state.allergies,
                onValueChange = onAllergiesChanged,
                placeholder = {
                    Text(
                        text = stringResource(R.string.patient_setup_allergies_placeholder),
                        color = Muted,
                        fontSize = 14.sp,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                enabled = !state.isSaving,
                shape = RoundedCornerShape(10.dp),
            )
        }

        Column {
            Text(
                text = stringResource(R.string.patient_setup_chronic_conditions_label),
                fontFamily = Geist,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            OutlinedTextField(
                value = state.chronicConditions,
                onValueChange = onChronicConditionsChanged,
                placeholder = {
                    Text(
                        text = stringResource(R.string.patient_setup_chronic_conditions_placeholder),
                        color = Muted,
                        fontSize = 14.sp,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                enabled = !state.isSaving,
                shape = RoundedCornerShape(10.dp),
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Patient ID", text)
    clipboardManager.setPrimaryClip(clip)
    Toast.makeText(context, "Patient ID copied", Toast.LENGTH_SHORT).show()
}
