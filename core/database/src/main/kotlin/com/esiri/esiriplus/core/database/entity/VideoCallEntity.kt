package com.esiri.esiriplus.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "video_calls",
    foreignKeys = [
        ForeignKey(
            entity = ConsultationEntity::class,
            parentColumns = ["consultationId"],
            childColumns = ["consultationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("consultationId")],
)
data class VideoCallEntity(
    @PrimaryKey val callId: String,
    val consultationId: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val durationSeconds: Int,
    val callQuality: String,
    val createdAt: Long,
)
