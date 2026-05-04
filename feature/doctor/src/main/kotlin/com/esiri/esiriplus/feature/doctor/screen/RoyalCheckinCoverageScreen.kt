package com.esiri.esiriplus.feature.doctor.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.esiri.esiriplus.core.network.service.RoyalEscalationItem
import com.esiri.esiriplus.feature.doctor.viewmodel.RoyalCheckinCoverageEvent
import com.esiri.esiriplus.feature.doctor.viewmodel.RoyalCheckinCoverageViewModel

private val BrandTeal = Color(0xFF2A9D8F)

/**
 * Clinical-officer coverage screen for Royal check-in escalations. Each
 * escalation is one patient assigned to one CO — multiple COs cover a
 * doctor's slot in parallel. The CO accepts the ring (auto on first load
 * if [autoAcceptEscalationId] is supplied via the route), calls the single
 * patient on the card, and marks the coverage complete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoyalCheckinCoverageScreen(
    onBack: () -> Unit,
    onStartCall: (callId: String, roomId: String, consultationId: String, patientSessionId: String) -> Unit,
    viewModel: RoyalCheckinCoverageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { ev ->
            when (ev) {
                is RoyalCheckinCoverageEvent.StartCall ->
                    onStartCall(ev.callId, ev.roomId, ev.consultationId, ev.patientSessionId)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.markActiveCallEnded()
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
                title = { Text("Check-Ins", fontWeight = FontWeight.SemiBold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = BrandTeal) }
            state.escalations.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No active check-ins.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                items(state.escalations, key = { it.escalationId }) { esc ->
                    EscalationCard(
                        escalation = esc,
                        callIdForEsc = state.callIds[esc.escalationId],
                        durationForEsc = state.durations[esc.escalationId],
                        qualifiesForEsc = state.qualifying[esc.escalationId],
                        isCalling = state.callingEscalationId == esc.escalationId,
                        completed = esc.escalationId in state.completedEscalationIds,
                        onCall = { viewModel.onCall(esc.escalationId, esc.patientSessionId) },
                        onComplete = { viewModel.onCompleteEscalation(esc.escalationId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EscalationCard(
    escalation: RoyalEscalationItem,
    callIdForEsc: String?,
    durationForEsc: Int?,
    qualifiesForEsc: Boolean?,
    isCalling: Boolean,
    completed: Boolean,
    onCall: () -> Unit,
    onComplete: () -> Unit,
) {
    val doctorName = (escalation.doctor?.fullName ?: "")
        .replace(Regex("^[Dd]r\\s+"), "")
        .ifBlank { "the doctor" }
    val slotLabel = String.format("%02d:00", escalation.slotHour)
    val callPlaced = callIdForEsc != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text(
            "$slotLabel slot · Dr $doctorName",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "Royal client · ${escalation.patientSessionId.take(8)}…",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        val statusLine = when {
            completed -> "Coverage complete"
            durationForEsc != null && qualifiesForEsc == true ->
                "Called · ${durationForEsc}s · qualifies for payment"
            durationForEsc != null ->
                "Called · ${durationForEsc}s · under 60s, no payment"
            callPlaced -> "Calling…"
            else -> "Not yet called"
        }
        Text(
            statusLine,
            fontSize = 12.sp,
            color = if (qualifiesForEsc == true || completed) BrandTeal
                    else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (completed) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = BrandTeal, modifier = Modifier.size(22.dp))
                Spacer(Modifier.size(8.dp))
                Text("Done", fontSize = 13.sp, color = BrandTeal, fontWeight = FontWeight.Medium)
            } else {
                OutlinedButton(
                    onClick = onCall,
                    enabled = !isCalling,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    if (isCalling) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = BrandTeal,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("Connecting…", fontSize = 12.sp)
                    } else {
                        Icon(Icons.Outlined.Phone, contentDescription = null, tint = BrandTeal, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(if (callPlaced) "Call again" else "Call", fontSize = 12.sp, color = BrandTeal)
                    }
                }
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = onComplete,
                    enabled = callPlaced,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandTeal,
                        disabledContainerColor = BrandTeal.copy(alpha = 0.4f),
                    ),
                    modifier = Modifier.height(36.dp),
                ) {
                    Text("Mark complete", fontSize = 12.sp, color = Color.White)
                }
            }
        }
    }
}
