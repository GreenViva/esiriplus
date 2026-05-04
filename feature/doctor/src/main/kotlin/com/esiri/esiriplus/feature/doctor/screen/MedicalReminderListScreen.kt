package com.esiri.esiriplus.feature.doctor.screen

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.core.network.service.AcceptedReminder
import com.esiri.esiriplus.feature.doctor.viewmodel.MedicalReminderEvent
import com.esiri.esiriplus.feature.doctor.viewmodel.MedicalReminderListViewModel

/** Nurse-only list of accepted medication reminders awaiting a Call. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicalReminderListScreen(
    onBack: () -> Unit,
    onStartCall: (eventId: String, roomId: String, patientSessionId: String, consultationId: String) -> Unit,
    viewModel: MedicalReminderListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { ev ->
            when (ev) {
                is MedicalReminderEvent.StartCall ->
                    onStartCall(ev.eventId, ev.roomId, ev.patientSessionId, ev.consultationId)
            }
        }
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.onCallScreenResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.autoAcceptError) {
        state.autoAcceptError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearAutoAcceptError()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Medical Reminders", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
        containerColor = Color(0xFFF8FAFA),
    ) { padding ->
        when {
            state.isLoading -> LoadingState(modifier = Modifier.fillMaxSize().padding(padding))
            state.reminders.isEmpty() -> EmptyState(modifier = Modifier.fillMaxSize().padding(padding))
            else -> ReminderList(
                state = state,
                onCall = viewModel::onCall,
                onDismiss = viewModel::onDismiss,
                onMarkUnreachable = viewModel::onMarkUnreachable,
                contentPadding = padding,
            )
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color(0xFF14B8A6))
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0xFFE7F8F4), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "⚕", fontSize = 28.sp, color = Color(0xFF14B8A6))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "No reminders waiting",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF115E59),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "When you accept a ring, the patient will appear here for you to call.",
                fontSize = 13.sp,
                color = Color(0xFF6B7C77),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ReminderList(
    state: com.esiri.esiriplus.feature.doctor.viewmodel.MedicalReminderListUiState,
    onCall: (AcceptedReminder) -> Unit,
    onDismiss: (eventId: String) -> Unit,
    onMarkUnreachable: (eventId: String) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.reminders, key = { it.eventId }) { reminder ->
            ReminderRow(
                reminder = reminder,
                isCalling = state.callingEventId == reminder.eventId,
                onCall = { onCall(reminder) },
                onDismiss = { onDismiss(reminder.eventId) },
                onMarkUnreachable = { onMarkUnreachable(reminder.eventId) },
            )
        }
    }
}

@Composable
private fun ReminderRow(
    reminder: AcceptedReminder,
    isCalling: Boolean,
    onCall: () -> Unit,
    onDismiss: () -> Unit,
    onMarkUnreachable: () -> Unit,
) {
    val confirmDismiss = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (confirmDismiss.value) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDismiss.value = false },
            title = { Text("Remove this reminder?") },
            text = {
                Text(
                    "The patient will be notified directly to take their medicine. " +
                        "You won't earn this reminder.",
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmDismiss.value = false
                    onDismiss()
                }) { Text("Remove", color = Color(0xFFDC2626)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDismiss.value = false }) {
                    Text("Cancel")
                }
            },
        )
    }
    val tt = reminder.timetable
    val medication = tt?.medicationName?.takeIf { it.isNotBlank() } ?: "Medication"
    val dosage = tt?.dosage?.takeIf { it.isNotBlank() }
    val form = tt?.form?.takeIf { it.isNotBlank() }
    val patientLabel = (tt?.patientSessionId ?: "")
        .take(8)
        .let { if (it.isNotBlank()) "Patient $it" else "Patient" }

    // Compact "500mg · Tablets" string when both are known.
    val dosageLine = listOfNotNull(dosage, form).joinToString(" · ").ifBlank { null }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE6EEEC), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFFE7F8F4), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "⚕", fontSize = 18.sp, color = Color(0xFF14B8A6))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = patientLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF6B7C77),
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = "Scheduled ${reminder.scheduledTime}",
                    fontSize = 11.sp,
                    color = Color(0xFF94A19D),
                )
            }
            IconButton(onClick = { confirmDismiss.value = true }) {
                Text(
                    text = "✕",
                    fontSize = 16.sp,
                    color = Color(0xFF94A19D),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Medicine block — visually distinct so the nurse confirms what to
        // remind the patient about BEFORE tapping Call.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF1FBF8))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = "MEDICINE",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F766E),
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = medication,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF115E59),
            )
            if (dosageLine != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = dosageLine,
                    fontSize = 13.sp,
                    color = Color(0xFF115E59),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // After start_call the event is nurse_calling. The screen tries
        // an auto-complete on resume; if the server rejects (no patient
        // join, or call <60s), the row stays nurse_calling and only the
        // "Couldn't reach" path is offered — the nurse can never claim a
        // call that didn't actually connect.
        if (reminder.status == "nurse_calling") {
            Button(
                onClick = onMarkUnreachable,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEA580C),
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Couldn't reach", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Button(
                onClick = onCall,
                enabled = !isCalling,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF14B8A6),
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                if (isCalling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Calling…", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(
                        Icons.Outlined.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Call patient", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

