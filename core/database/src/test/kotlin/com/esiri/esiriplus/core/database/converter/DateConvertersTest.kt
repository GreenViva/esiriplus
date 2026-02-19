package com.esiri.esiriplus.core.database.converter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

class DateConvertersTest {

    private lateinit var converter: DateConverters

    @Before
    fun setUp() {
        converter = DateConverters()
    }

    @Test
    fun `fromTimestamp converts milliseconds to Instant`() {
        val millis = 1704067200000L // 2024-01-01T00:00:00Z
        val result = converter.fromTimestamp(millis)
        assertEquals(Instant.ofEpochMilli(millis), result)
    }

    @Test
    fun `fromTimestamp returns null for null input`() {
        assertNull(converter.fromTimestamp(null))
    }

    @Test
    fun `dateToTimestamp converts Instant to milliseconds`() {
        val instant = Instant.ofEpochMilli(1704067200000L)
        val result = converter.dateToTimestamp(instant)
        assertEquals(1704067200000L, result)
    }

    @Test
    fun `dateToTimestamp returns null for null input`() {
        assertNull(converter.dateToTimestamp(null))
    }

    @Test
    fun `round trip preserves value`() {
        val original = Instant.now()
        val millis = converter.dateToTimestamp(original)
        val restored = converter.fromTimestamp(millis)
        assertEquals(original, restored)
    }
}
