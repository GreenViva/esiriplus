package com.esiri.esiriplus.ui.notification

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.core.domain.model.Notification
import com.esiri.esiriplus.core.domain.model.NotificationType
import com.esiri.esiriplus.viewmodel.NotificationViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val BrandTeal = Color(0xFF2A9D8F)

@Composable
fun NotificationListScreen(
    onBack: () -> Unit,
    onNotificationClick: (Notification) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FFFE)),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.Black,
                )
            }
            Text(
                text = "Notifications",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { viewModel.markAllAsRead() }) {
                Text(
                    text = "Mark all read",
                    fontSize = 12.sp,
                    color = BrandTeal,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        if (uiState.isLoading && uiState.notifications.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = BrandTeal)
            }
        } else if (uiState.notifications.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No notifications yet",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "You'll see updates here when they arrive.",
                        fontSize = 13.sp,
                        color = Color.Gray,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                items(
                    items = uiState.notifications,
                    key = { it.notificationId },
                ) { notification ->
                    NotificationItem(
                        notification = notification,
                        onClick = {
                            viewModel.markAsRead(notification.notificationId)
                            onNotificationClick(notification)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationItem(
    notification: Notification,
    onClick: () -> Unit,
) {
    val isUnread = notification.readAt == null
    val bgColor = if (isUnread) Color(0xFFF0FDFA) else Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Type icon
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(getTypeColor(notification.type).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = getTypeEmoji(notification.type),
                fontSize = 16.sp,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = notification.title,
                    fontSize = 14.sp,
                    fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Medium,
                    color = Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isUnread) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(BrandTeal),
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = notification.body,
                fontSize = 12.sp,
                color = Color.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatTimestamp(notification.createdAt),
                fontSize = 11.sp,
                color = Color.Gray,
            )
        }
    }
}

private fun getTypeColor(type: NotificationType): Color = when (type) {
    NotificationType.CONSULTATION_REQUEST -> Color(0xFF3B82F6)
    NotificationType.CONSULTATION_ACCEPTED -> Color(0xFF22C55E)
    NotificationType.MESSAGE_RECEIVED -> Color(0xFF8B5CF6)
    NotificationType.VIDEO_CALL_INCOMING -> Color(0xFFEF4444)
    NotificationType.REPORT_READY -> Color(0xFF06B6D4)
    NotificationType.PAYMENT_STATUS -> Color(0xFFF59E0B)
    NotificationType.DOCTOR_APPROVED -> Color(0xFF22C55E)
    NotificationType.DOCTOR_REJECTED -> Color(0xFFEF4444)
    NotificationType.DOCTOR_WARNED -> Color(0xFFF59E0B)
    NotificationType.DOCTOR_SUSPENDED -> Color(0xFFF97316)
    NotificationType.DOCTOR_UNSUSPENDED -> Color(0xFF22C55E)
    NotificationType.DOCTOR_BANNED -> Color(0xFFEF4444)
    NotificationType.DOCTOR_UNBANNED -> Color(0xFF22C55E)
}

private fun getTypeEmoji(type: NotificationType): String = when (type) {
    NotificationType.CONSULTATION_REQUEST -> "\uD83D\uDCCB"
    NotificationType.CONSULTATION_ACCEPTED -> "\u2705"
    NotificationType.MESSAGE_RECEIVED -> "\uD83D\uDCAC"
    NotificationType.VIDEO_CALL_INCOMING -> "\uD83D\uDCF9"
    NotificationType.REPORT_READY -> "\uD83D\uDCC4"
    NotificationType.PAYMENT_STATUS -> "\uD83D\uDCB3"
    NotificationType.DOCTOR_APPROVED -> "\uD83C\uDF89"
    NotificationType.DOCTOR_REJECTED -> "\u274C"
    NotificationType.DOCTOR_WARNED -> "\u26A0\uFE0F"
    NotificationType.DOCTOR_SUSPENDED -> "\u23F8\uFE0F"
    NotificationType.DOCTOR_UNSUSPENDED -> "\u25B6\uFE0F"
    NotificationType.DOCTOR_BANNED -> "\uD83D\uDEAB"
    NotificationType.DOCTOR_UNBANNED -> "\u2705"
}

private fun formatTimestamp(epochMillis: Long): String {
    return try {
        val now = System.currentTimeMillis()
        val diffMinutes = (now - epochMillis) / 60_000

        when {
            diffMinutes < 1 -> "Just now"
            diffMinutes < 60 -> "${diffMinutes}m ago"
            diffMinutes < 1440 -> "${diffMinutes / 60}h ago"
            diffMinutes < 10080 -> "${diffMinutes / 1440}d ago"
            else -> {
                val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
                Instant.ofEpochMilli(epochMillis)
                    .atZone(ZoneId.systemDefault())
                    .format(formatter)
            }
        }
    } catch (_: Exception) {
        ""
    }
}
