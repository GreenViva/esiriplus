package com.esiri.esiriplus.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = ConsultationEntity::class,
            parentColumns = ["id"],
            childColumns = ["consultationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("consultationId")],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val consultationId: String,
    val uploaderId: String,
    val fileName: String,
    val fileType: String,
    val fileSize: Long,
    val url: String,
    val createdAt: Instant,
)
