package com.esiri.esiriplus.core.domain.model

import java.time.Instant
import java.time.temporal.ChronoUnit

data class Session(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Instant,
    val user: User,
    val createdAt: Instant = Instant.now(),
) {
    val isExpired: Boolean get() = Instant.now().isAfter(expiresAt)

    val isRefreshWindowExpired: Boolean
        get() = Instant.now().isAfter(createdAt.plus(REFRESH_WINDOW_DAYS, ChronoUnit.DAYS))

    companion object {
        private const val REFRESH_WINDOW_DAYS = 7L
    }
}
