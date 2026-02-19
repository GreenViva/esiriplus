package com.esiri.esiriplus.core.database.converter

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JsonConverterTest {

    private lateinit var converter: JsonConverter

    @Before
    fun setUp() {
        converter = JsonConverter()
    }

    // JsonObject tests

    @Test
    fun `fromJsonObject converts to JSON string`() {
        val obj = buildJsonObject {
            put("key", "value")
            put("number", 42)
        }
        val result = converter.fromJsonObject(obj)
        assertTrue(result!!.contains("\"key\""))
        assertTrue(result.contains("\"value\""))
    }

    @Test
    fun `fromJsonObject returns null for null input`() {
        assertNull(converter.fromJsonObject(null))
    }

    @Test
    fun `toJsonObject converts JSON string to JsonObject`() {
        val json = """{"key":"value","number":42}"""
        val result = converter.toJsonObject(json)
        assertEquals(JsonPrimitive("value"), result!!["key"])
    }

    @Test
    fun `toJsonObject returns null for null input`() {
        assertNull(converter.toJsonObject(null))
    }

    @Test
    fun `JsonObject round trip preserves values`() {
        val original = buildJsonObject {
            put("name", "test")
            put("count", 5)
            put("active", true)
        }
        val json = converter.fromJsonObject(original)
        val restored = converter.toJsonObject(json)
        assertEquals(original, restored)
    }

    // Map<String, String> tests

    @Test
    fun `fromMap converts to JSON string`() {
        val map = mapOf("key1" to "val1", "key2" to "val2")
        val result = converter.fromMap(map)
        assertTrue(result!!.contains("key1"))
        assertTrue(result.contains("val1"))
    }

    @Test
    fun `fromMap returns null for null input`() {
        assertNull(converter.fromMap(null))
    }

    @Test
    fun `toMap converts JSON string to map`() {
        val json = """{"key1":"val1","key2":"val2"}"""
        val result = converter.toMap(json)
        assertEquals("val1", result!!["key1"])
        assertEquals("val2", result["key2"])
    }

    @Test
    fun `toMap returns null for null input`() {
        assertNull(converter.toMap(null))
    }

    @Test
    fun `Map round trip preserves values`() {
        val original = mapOf("a" to "1", "b" to "2", "c" to "3")
        val json = converter.fromMap(original)
        val restored = converter.toMap(json)
        assertEquals(original, restored)
    }

    @Test
    fun `handles empty JsonObject`() {
        val empty = JsonObject(emptyMap())
        val json = converter.fromJsonObject(empty)
        val restored = converter.toJsonObject(json)
        assertEquals(empty, restored)
    }

    @Test
    fun `handles empty map`() {
        val empty = emptyMap<String, String>()
        val json = converter.fromMap(empty)
        val restored = converter.toMap(json)
        assertEquals(empty, restored)
    }
}
