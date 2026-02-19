package com.esiri.esiriplus.core.database.converter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ListStringConverterTest {

    private lateinit var converter: ListStringConverter

    @Before
    fun setUp() {
        converter = ListStringConverter()
    }

    @Test
    fun `fromList converts list to JSON string`() {
        val list = listOf("a", "b", "c")
        val result = converter.fromList(list)
        assertEquals("""["a","b","c"]""", result)
    }

    @Test
    fun `fromList returns null for null input`() {
        assertNull(converter.fromList(null))
    }

    @Test
    fun `fromList handles empty list`() {
        val result = converter.fromList(emptyList())
        assertEquals("[]", result)
    }

    @Test
    fun `toList converts JSON string to list`() {
        val json = """["a","b","c"]"""
        val result = converter.toList(json)
        assertEquals(listOf("a", "b", "c"), result)
    }

    @Test
    fun `toList returns null for null input`() {
        assertNull(converter.toList(null))
    }

    @Test
    fun `toList handles empty array`() {
        val result = converter.toList("[]")
        assertTrue(result!!.isEmpty())
    }

    @Test
    fun `round trip preserves values`() {
        val original = listOf("allergies", "penicillin", "peanuts")
        val json = converter.fromList(original)
        val restored = converter.toList(json)
        assertEquals(original, restored)
    }

    @Test
    fun `handles strings with special characters`() {
        val list = listOf("hello \"world\"", "foo\\bar", "new\nline")
        val json = converter.fromList(list)
        val restored = converter.toList(json)
        assertEquals(list, restored)
    }
}
