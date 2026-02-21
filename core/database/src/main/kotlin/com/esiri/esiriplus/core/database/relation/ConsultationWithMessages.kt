package com.esiri.esiriplus.core.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.esiri.esiriplus.core.database.entity.ConsultationEntity
import com.esiri.esiriplus.core.database.entity.MessageEntity

data class ConsultationWithMessages(
    @Embedded val consultation: ConsultationEntity,
    @Relation(parentColumn = "consultationId", entityColumn = "consultationId")
    val messages: List<MessageEntity>,
)
