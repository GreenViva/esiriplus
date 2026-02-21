package com.esiri.esiriplus.core.domain.model

data class TypingIndicator(
    val consultationId: String,
    val userId: String,
    val isTyping: Boolean,
    val updatedAt: Long,
)
