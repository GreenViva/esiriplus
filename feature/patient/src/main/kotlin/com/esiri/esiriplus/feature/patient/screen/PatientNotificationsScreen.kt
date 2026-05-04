package com.esiri.esiriplus.feature.patient.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.outlined.PhoneCallback
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.core.database.entity.NotificationEntity
import com.esiri.esiriplus.feature.patient.viewmodel.PatientNotificationsViewModel

private val BrandTeal = Color(0xFF2A9D8F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientNotificationsScreen(
    onBack: () -> Unit,
    viewModel: PatientNotificationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val pullState = rememberPullToRefreshState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.unreadCount > 0) {
                        TextButton(onClick = viewModel::markAllAsRead) {
                            Text("Mark all read", color = BrandTeal, fontSize = 13.sp)
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = BrandTeal) }
            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                state = pullState,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                if (state.notifications.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = null,
                                tint = Color.LightGray,
                                modifier = Modifier.size(64.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No notifications yet",
                                color = Color.Black,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Missed calls and reminders show up here.",
                                color = Color.Gray,
                                fontSize = 13.sp,
                            )
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.notifications, key = { it.notificationId }) { row ->
                            NotificationItem(
                                notification = row,
                                onClick = { viewModel.markAsRead(row.notificationId) },
                                onDelete = { viewModel.deleteNotification(row.notificationId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationItem(
    notification: NotificationEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val isUnread = notification.readAt == null
    val bgColor = if (isUnread) Color(0xFFF0FDFA) else Color.White
    val (icon, tint) = notificationIcon(notification.type)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = notification.title,
                    fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp,
                    color = Color.Black,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isUnread) {
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.size(8.dp).background(BrandTeal, CircleShape))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = notification.body,
                fontSize = 13.sp,
                color = Color.Black,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatTimeAgo(notification.createdAt),
                fontSize = 11.sp,
                color = Color.Gray,
            )
        }
        IconButton(onClick = onDelete) {
            Text(text = "✕", fontSize = 16.sp, color = Color.Gray)
        }
    }
}

private fun notificationIcon(type: String): Pair<ImageVector, Color> = when (type.uppercase()) {
    "MEDICATION_REMINDER_PATIENT", "MEDICATION_REMINDER" ->
        Icons.Outlined.Healing to Color(0xFF14B8A6)
    "VIDEO_CALL_INCOMING" ->
        Icons.Outlined.Phone to BrandTeal
    "CONSULTATION_ACCEPTED", "CONSULTATION_REQUEST" ->
        Icons.Outlined.PhoneCallback to BrandTeal
    else ->
        Icons.Default.Info to BrandTeal
}

private fun formatTimeAgo(epochMillis: Long): String {
    val diff = System.currentTimeMillis() - epochMillis
    val minutes = diff / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> {
            val date = java.time.Instant.ofEpochMilli(epochMillis)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            "${date.dayOfMonth}/${date.monthValue}/${date.year}"
        }
    }
}
