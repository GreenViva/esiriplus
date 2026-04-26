package com.esiri.esiriplus.feature.patient.screen

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esiri.esiriplus.core.database.entity.MessageEntity
import com.esiri.esiriplus.core.ui.LoadingScreen
import com.esiri.esiriplus.core.ui.theme.Geist
import com.esiri.esiriplus.core.ui.theme.Hairline
import com.esiri.esiriplus.core.ui.theme.Ink
import com.esiri.esiriplus.core.ui.theme.InstrumentSerif
import com.esiri.esiriplus.core.ui.theme.Muted
import com.esiri.esiriplus.core.ui.theme.TealBg
import com.esiri.esiriplus.core.ui.theme.TealDeep
import com.esiri.esiriplus.core.ui.theme.TealSoft
import com.esiri.esiriplus.feature.patient.viewmodel.PastChatDetailViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dayFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastChatDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PastChatDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        containerColor = TealBg,
        topBar = { DetailTopBar(title = state.title, onBack = onBack) },
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { LoadingScreen() }

            state.messages.isEmpty() -> EmptyMessages(padding)

            else -> MessageList(
                padding = padding,
                messages = state.messages,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailTopBar(title: String, onBack: () -> Unit) {
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
            Column {
                Text(
                    text = title,
                    fontFamily = InstrumentSerif,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal,
                    color = Ink,
                )
                Text(
                    text = "Read-only · expires after 14 days",
                    fontFamily = Geist,
                    fontSize = 10.sp,
                    color = Muted,
                )
            }
        },
    )
}

@Composable
private fun MessageList(
    padding: PaddingValues,
    messages: List<MessageEntity>,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        var lastDay: String? = null
        items(messages, key = { it.messageId }) { message ->
            val day = Instant.ofEpochMilli(message.createdAt)
                .atZone(ZoneId.systemDefault())
                .format(dayFormatter)
            if (day != lastDay) {
                DayDivider(day)
                Spacer(Modifier.height(2.dp))
                lastDay = day
            }
            MessageBubble(message)
        }
    }
}

@Composable
private fun DayDivider(day: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = day,
            fontFamily = Geist,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = Muted,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(TealSoft)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun MessageBubble(message: MessageEntity) {
    val isPatient = message.senderType.equals("patient", ignoreCase = true)
    val time = Instant.ofEpochMilli(message.createdAt)
        .atZone(ZoneId.systemDefault())
        .format(timeFormatter)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isPatient) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isPatient) Alignment.End else Alignment.Start,
        ) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 14.dp,
                            topEnd = 14.dp,
                            bottomStart = if (isPatient) 14.dp else 4.dp,
                            bottomEnd = if (isPatient) 4.dp else 14.dp,
                        ),
                    )
                    .background(if (isPatient) TealDeep else Color.White)
                    .border(
                        width = 1.dp,
                        color = if (isPatient) TealDeep else Hairline,
                        shape = RoundedCornerShape(
                            topStart = 14.dp,
                            topEnd = 14.dp,
                            bottomStart = if (isPatient) 14.dp else 4.dp,
                            bottomEnd = if (isPatient) 4.dp else 14.dp,
                        ),
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = message.messageText,
                    fontFamily = Geist,
                    fontSize = 13.sp,
                    color = if (isPatient) Color.White else Ink,
                    lineHeight = 18.sp,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = time,
                fontFamily = Geist,
                fontSize = 9.sp,
                color = Muted,
            )
        }
    }
}

@Composable
private fun EmptyMessages(padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(TealSoft),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = null,
                    tint = TealDeep,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Nothing here",
                fontFamily = InstrumentSerif,
                fontSize = 18.sp,
                color = Ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "This chat may have already expired or never had any messages.",
                fontFamily = Geist,
                fontSize = 12.sp,
                color = Muted,
            )
        }
    }
}
