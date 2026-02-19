package com.esiri.esiriplus.core.common.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object DateUtils {
    private val displayFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")
    private val isoFormatter = DateTimeFormatter.ISO_INSTANT

    fun formatForDisplay(instant: Instant, zoneId: ZoneId = ZoneId.of("Africa/Nairobi")): String =
        LocalDateTime.ofInstant(instant, zoneId).format(displayFormatter)

    fun parseIso(isoString: String): Instant = Instant.from(isoFormatter.parse(isoString))

    fun nowUtc(): Instant = Instant.now()
}
