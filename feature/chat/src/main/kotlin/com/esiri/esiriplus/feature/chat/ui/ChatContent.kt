package com.esiri.esiriplus.feature.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.esiri.esiriplus.core.domain.repository.MessageData
import kotlinx.coroutines.delay

private val BrandTeal = Color(0xFF2A9D8F)
private val MintLight = Color(0xFFE0F2F1)

/**
 * Shared chat content composable used by both doctor and patient consultation screens.
 * The [topBarActions] slot allows each caller to add role-specific action buttons
 * (e.g., video call and write report for doctors).
 */
@Composable
fun ChatContent(
    messages: List<MessageData>,
    isLoading: Boolean,
    currentUserId: String,
    otherPartyTyping: Boolean,
    consultationId: String,
    onSendMessage: (String) -> Unit,
    onTypingChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    sendError: String? = null,
    topBarActions: @Composable RowScope.() -> Unit = {},
    timerContent: @Composable () -> Unit = {},
    bottomOverlay: @Composable () -> Unit = {},
) {
    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // 5.5: Only auto-scroll if user is near the bottom
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val isNearBottom = lastVisibleItem >= messages.size - 3
            if (isNearBottom || messages.size <= 1) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color.White, MintLight))),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            ChatTopBar(
                consultationId = consultationId,
                onBack = onBack,
                actions = topBarActions,
            )

            // Timer bar slot
            timerContent()

            // 5.4: Connection error banner
            if (error != null) {
                Surface(
                    color = Color(0xFFFFF3CD),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = Color(0xFF856404),
                        fontSize = 13.sp,
                    )
                }
            }

            // Message area
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = BrandTeal)
                }
            } else if (messages.isEmpty()) {
                EmptyMessagesState(modifier = Modifier.weight(1f))
            } else {
                MessageList(
                    messages = messages,
                    currentUserId = currentUserId,
                    otherPartyTyping = otherPartyTyping,
                    listState = listState,
                    modifier = Modifier.weight(1f),
                )
            }

            // Bottom overlay slot (extension prompts, etc.)
            bottomOverlay()

            // Send error banner
            if (sendError != null) {
                Surface(
                    color = Color(0xFFFEE2E2),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = sendError,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        color = Color(0xFF991B1B),
                        fontSize = 13.sp,
                    )
                }
            }

            // Input bar
            ChatInputBar(
                value = textInput,
                onValueChange = { newValue ->
                    textInput = newValue
                    onTypingChanged(newValue.isNotEmpty())
                },
                onSend = {
                    if (textInput.isNotBlank()) {
                        onSendMessage(textInput)
                        textInput = ""
                    }
                },
            )
        }
    }
}

@Composable
private fun ChatTopBar(
    consultationId: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.Black,
            )
        }
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Consultation",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
            )
            if (consultationId.isNotBlank()) {
                Text(
                    text = "ID: ${consultationId.take(8)}...",
                    fontSize = 12.sp,
                    color = Color.Black,
                )
            }
        }
        actions()
    }
}

@Composable
private fun MessageList(
    messages: List<MessageData>,
    currentUserId: String,
    otherPartyTyping: Boolean,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        items(
            items = messages,
            key = { it.messageId },
        ) { message ->
            MessageBubble(
                message = message,
                isOwn = message.senderId == currentUserId,
            )
        }
        if (otherPartyTyping) {
            item(key = "typing_indicator") {
                TypingIndicatorBubble()
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: MessageData,
    isOwn: Boolean,
) {
    val bubbleColor = if (isOwn) BrandTeal else Color.White
    val textColor = if (isOwn) Color.White else Color.Black
    val alignment = if (isOwn) Alignment.End else Alignment.Start
    val shape = if (isOwn) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = alignment,
    ) {
        Surface(
            shape = shape,
            color = bubbleColor,
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(min = 64.dp, max = 280.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = message.messageText,
                    color = textColor,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatMessageTime(message.createdAt),
                        color = if (isOwn) Color.White.copy(alpha = 0.75f) else Color(0xFF6B7280),
                        fontSize = 11.sp,
                    )
                    if (isOwn && !message.synced) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "\u2022\u2022\u2022",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingIndicatorBubble() {
    var dotCount by remember { mutableIntStateOf(1) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            dotCount = if (dotCount == 3) 1 else dotCount + 1
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = Color.White,
            shadowElevation = 1.dp,
        ) {
            Text(
                text = "Typing" + ".".repeat(dotCount),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = Color(0xFF6B7280),
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun EmptyMessagesState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No messages yet",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = Color.Black,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Start the conversation below",
                fontSize = 14.sp,
                color = Color(0xFF6B7280),
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(
        shadowElevation = 8.dp,
        color = Color.White,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text("Type a message...", color = Color(0xFF9CA3AF), fontSize = 14.sp)
                },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandTeal,
                    unfocusedBorderColor = Color(0xFFE5E7EB),
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(
                    onSend = { onSend() },
                ),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = value.isNotBlank(),
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = if (value.isNotBlank()) BrandTeal else Color(0xFFE5E7EB),
                        shape = CircleShape,
                    ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private fun formatMessageTime(epochMillis: Long): String {
    return try {
        java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    } catch (_: Exception) {
        ""
    }
}
