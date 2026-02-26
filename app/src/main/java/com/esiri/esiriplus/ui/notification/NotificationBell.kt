package com.esiri.esiriplus.ui.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.R
import com.esiri.esiriplus.viewmodel.NotificationViewModel

/**
 * Notification bell icon with unread badge count.
 *
 * Usage:
 * ```
 * NotificationBell(onClick = { navController.navigate("notifications") })
 * ```
 */
@Composable
fun NotificationBell(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Black,
    viewModel: NotificationViewModel = hiltViewModel(),
) {
    val unreadCount by viewModel.unreadCount.collectAsState()

    Box(modifier = modifier) {
        IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_notifications),
                contentDescription = "Notifications",
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }

        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .offset(x = 4.dp, y = (-2).dp)
                    .clip(CircleShape)
                    .background(Color(0xFFDC2626))
                    .align(Alignment.TopEnd),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}
