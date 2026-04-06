package com.esiri.esiriplus.feature.doctor.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.core.database.entity.ConsultationEntity
import com.esiri.esiriplus.feature.doctor.R
import com.esiri.esiriplus.feature.doctor.viewmodel.DoctorDashboardViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val RoyalPurple = Color(0xFF4C1D95)
private val RoyalGold = Color(0xFFF59E0B)
private val BrandTeal = Color(0xFF2A9D8F)
private val SubtitleGrey = Color(0xFF6B7280)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoyalClientsScreen(
    onBack: () -> Unit,
    onOpenConsultation: (consultationId: String) -> Unit = {},
    onStartCall: (consultationId: String, callType: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: DoctorDashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val royalConsultations = uiState.royalConsultations
    val pullRefreshState = rememberPullToRefreshState()
    val nicknameStore = rememberNicknameStore()

    // Track which consultation the doctor tapped — null means sheet is hidden
    var selectedConsultation by remember { mutableStateOf<ConsultationEntity?>(null) }
    // Force recomposition when a nickname changes
    var nicknameVersion by remember { mutableStateOf(0) }
    // Track rename dialog
    var renamingConsultationId by remember { mutableStateOf<String?>(null) }

    Surface(modifier = modifier.fillMaxSize(), color = Color.White) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(listOf(RoyalPurple, Color(0xFF7C3AED))),
                    )
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column {
                    Text(
                        text = "Royal Clients",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    Text(
                        text = "${royalConsultations.size} consultation${if (royalConsultations.size != 1) "s" else ""}",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                    )
                }
            }

            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refresh() },
                state = pullRefreshState,
                modifier = Modifier.weight(1f),
            ) {
                if (royalConsultations.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "\u2605",
                                fontSize = 48.sp,
                                color = RoyalGold.copy(alpha = 0.4f),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "No Royal consultations yet",
                                color = Color.Black,
                                fontSize = 16.sp,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(royalConsultations, key = { it.consultationId }) { consultation ->
                            // Read nickname (nicknameVersion forces recomposition on change)
                            val nickname = remember(consultation.consultationId, nicknameVersion) {
                                nicknameStore.get(consultation.consultationId)
                            }
                            RoyalClientCard(
                                consultation = consultation,
                                nickname = nickname,
                                onClick = { selectedConsultation = consultation },
                                onRename = { renamingConsultationId = consultation.consultationId },
                            )
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }

    // ── Call Option Bottom Sheet ──────────────────────────────────────────────────
    val selected = selectedConsultation
    if (selected != null) {
        val selectedNickname = remember(selected.consultationId, nicknameVersion) {
            nicknameStore.get(selected.consultationId)
        }
        RoyalCallOptionSheet(
            consultation = selected,
            nickname = selectedNickname,
            onVoiceCall = {
                selectedConsultation = null
                onStartCall(selected.consultationId, "AUDIO")
            },
            onVideoCall = {
                selectedConsultation = null
                onStartCall(selected.consultationId, "VIDEO")
            },
            onViewChat = {
                selectedConsultation = null
                onOpenConsultation(selected.consultationId)
            },
            onRename = { renamingConsultationId = selected.consultationId },
            onDismiss = { selectedConsultation = null },
        )
    }

    // ── Rename Dialog ────────────────────────────────────────────────────────────
    val renamingId = renamingConsultationId
    if (renamingId != null) {
        val currentNickname = remember(renamingId, nicknameVersion) {
            nicknameStore.get(renamingId) ?: ""
        }
        RenameClientDialog(
            currentName = currentNickname,
            onConfirm = { newName ->
                nicknameStore.set(renamingId, newName)
                nicknameVersion++
                renamingConsultationId = null
            },
            onDismiss = { renamingConsultationId = null },
        )
    }
}

// ── Call Option Bottom Sheet ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoyalCallOptionSheet(
    consultation: ConsultationEntity,
    nickname: String?,
    onVoiceCall: () -> Unit,
    onVideoCall: () -> Unit,
    onViewChat: () -> Unit,
    onRename: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Header: Royal badge + consultation info ──
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        Brush.radialGradient(listOf(RoyalPurple, Color(0xFF7C3AED))),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "\u2605",
                    fontSize = 28.sp,
                    color = RoyalGold,
                )
            }

            Spacer(Modifier.height(12.dp))

            // Nickname or default label + edit icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = nickname ?: "Royal Patient",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Rename",
                    tint = RoyalPurple.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(18.dp)
                        .clickable(onClick = onRename),
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = consultation.serviceType.replace("_", " ")
                    .lowercase().replaceFirstChar { it.uppercase() },
                fontSize = 14.sp,
                color = SubtitleGrey,
            )

            Text(
                text = dateFormat.format(Date(consultation.createdAt)),
                fontSize = 12.sp,
                color = SubtitleGrey,
            )

            Spacer(Modifier.height(6.dp))

            // Status chip
            val statusColor = when (consultation.status.lowercase()) {
                "active", "in_progress" -> BrandTeal
                "completed" -> Color(0xFF10B981)
                else -> RoyalGold
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = statusColor.copy(alpha = 0.12f),
            ) {
                Text(
                    text = consultation.status.lowercase().replaceFirstChar { it.uppercase() },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor,
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── Call option buttons ──
            Text(
                text = "Connect with Patient",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Patient will be notified if they are online",
                fontSize = 12.sp,
                color = SubtitleGrey,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(16.dp))

            // Voice + Video buttons side by side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Voice Call
                Surface(
                    onClick = onVoiceCall,
                    shape = RoundedCornerShape(16.dp),
                    color = BrandTeal.copy(alpha = 0.08f),
                    modifier = Modifier
                        .weight(1f)
                        .height(110.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(BrandTeal, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Voice Call",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Voice Call",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandTeal,
                        )
                    }
                }

                // Video Call
                Surface(
                    onClick = onVideoCall,
                    shape = RoundedCornerShape(16.dp),
                    color = RoyalPurple.copy(alpha = 0.08f),
                    modifier = Modifier
                        .weight(1f)
                        .height(110.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(RoyalPurple, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_videocam),
                                contentDescription = "Video Call",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Video Call",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = RoyalPurple,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // View Chat button
            Surface(
                onClick = onViewChat,
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF3F4F6),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chat_bubble),
                        contentDescription = "Chat",
                        tint = SubtitleGrey,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "View Chat History",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black,
                    )
                }
            }
        }
    }
}

// ── Royal Client Card ────────────────────────────────────────────────────────────

@Composable
private fun RoyalClientCard(
    consultation: ConsultationEntity,
    nickname: String?,
    onClick: () -> Unit,
    onRename: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault()) }
    val feeFormatted = remember(consultation.consultationFee) {
        "TSh ${"%,d".format(consultation.consultationFee)}"
    }

    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Purple header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(listOf(RoyalPurple, Color(0xFF7C3AED))),
                        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "\u2605 Royal",
                        color = RoyalGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val daysLeft = consultation.daysRemaining()
                        if (daysLeft != null) {
                            Text(
                                text = if (daysLeft <= 1) "Last day" else "$daysLeft days left",
                                color = if (daysLeft <= 3) RoyalGold else Color.White.copy(alpha = 0.8f),
                                fontWeight = if (daysLeft <= 3) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp,
                            )
                        }
                        Text(
                            text = consultation.status.lowercase().replaceFirstChar { it.uppercase() },
                            color = Color.White,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            // Body
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Status dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            when (consultation.status.lowercase()) {
                                "active", "in_progress" -> BrandTeal
                                "completed" -> Color(0xFF10B981)
                                else -> RoyalGold
                            },
                        ),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // Nickname (if set)
                    if (nickname != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = nickname,
                                color = RoyalPurple,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Rename",
                                tint = RoyalPurple.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable(onClick = onRename),
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                    // Service type
                    Text(
                        text = consultation.serviceType.replace("_", " ")
                            .lowercase().replaceFirstChar { it.uppercase() },
                        color = if (nickname != null) SubtitleGrey else Color.Black,
                        fontWeight = if (nickname != null) FontWeight.Normal else FontWeight.SemiBold,
                        fontSize = if (nickname != null) 13.sp else 15.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    // Date
                    Text(
                        text = dateFormat.format(Date(consultation.createdAt)),
                        color = Color(0xFF6B7280),
                        fontSize = 12.sp,
                    )
                }

                // Call icon hint
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(RoyalPurple.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call",
                        tint = RoyalPurple,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** Returns the number of days remaining in the Royal follow-up window, or null if no expiry set. */
private fun ConsultationEntity.daysRemaining(): Int? {
    val expiry = followUpExpiry ?: return null
    val remaining = expiry - System.currentTimeMillis()
    if (remaining <= 0) return 0
    return (remaining / (24 * 60 * 60 * 1000)).toInt() + 1 // ceiling
}

// ── Local nickname storage (frontend-only) ───────────────────────────────────────

private const val PREFS_NAME = "royal_client_nicknames"

@Composable
private fun rememberNicknameStore(): NicknameStore {
    val context = LocalContext.current
    return remember {
        NicknameStore(context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE))
    }
}

private class NicknameStore(private val prefs: android.content.SharedPreferences) {
    fun get(consultationId: String): String? = prefs.getString(consultationId, null)
    fun set(consultationId: String, name: String) {
        if (name.isBlank()) prefs.edit().remove(consultationId).apply()
        else prefs.edit().putString(consultationId, name.trim()).apply()
    }
}

@Composable
private fun RenameClientDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Text(
                text = "Name this Client",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
            )
        },
        text = {
            Column {
                Text(
                    text = "Give this Royal client a name for easy reference. This is only visible to you.",
                    fontSize = 13.sp,
                    color = SubtitleGrey,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 30) name = it },
                    placeholder = { Text("e.g. Mama Salma, Mr. John", color = Color.Gray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RoyalPurple,
                        unfocusedBorderColor = Color(0xFFE5E7EB),
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) {
                Text("Save", color = RoyalPurple, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SubtitleGrey)
            }
        },
    )
}
