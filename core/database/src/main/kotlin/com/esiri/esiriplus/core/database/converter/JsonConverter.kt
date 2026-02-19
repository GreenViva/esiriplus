package com.esiri.esiriplus.core.database.converter

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class JsonConverter {

    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromJsonObject(value: JsonObject?): String? =
        value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toJsonObject(value: String?): JsonObject? =
        value?.let { json.parseToJsonElement(it).jsonObject }

    @TypeConverter
    fun fromMap(value: Map<String, String>?): String? =
        value?.let { json.encodeToString(it) }

    @TypeConverter
    fun toMap(value: String?): Map<String, String>? =
        value?.let { json.decodeFromString<Map<String, String>>(it) }
}
