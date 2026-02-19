package com.esiri.esiriplus.core.domain.model

import java.time.Instant

data class Session(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Instant,
    val user: User,
) {
    val isExpired: Boolean get() = Instant.now().isAfter(expiresAt)
}
