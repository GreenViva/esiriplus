package com.esiri.esiriplus.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "patient_sessions",
    indices = [Index("sessionId", unique = true)],
)
data class PatientSessionEntity(
    @PrimaryKey val sessionId: String,
    val sessionTokenHash: String,
    val ageGroup: String? = null,
    val sex: String? = null,
    val region: String? = null,
    val bloodType: String? = null,
    val allergies: List<String> = emptyList(),
    val chronicConditions: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val lastSynced: Long? = null,
)
