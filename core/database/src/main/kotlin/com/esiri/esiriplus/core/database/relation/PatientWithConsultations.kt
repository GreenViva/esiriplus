package com.esiri.esiriplus.core.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.esiri.esiriplus.core.database.entity.ConsultationEntity
import com.esiri.esiriplus.core.database.entity.PatientSessionEntity

data class PatientWithConsultations(
    @Embedded val patientSession: PatientSessionEntity,
    @Relation(parentColumn = "sessionId", entityColumn = "patientSessionId")
    val consultations: List<ConsultationEntity>,
)
