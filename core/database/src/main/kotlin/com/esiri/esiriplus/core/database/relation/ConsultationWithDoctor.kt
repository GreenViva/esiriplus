package com.esiri.esiriplus.core.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.esiri.esiriplus.core.database.entity.ConsultationEntity
import com.esiri.esiriplus.core.database.entity.DoctorProfileEntity

data class ConsultationWithDoctor(
    @Embedded val consultation: ConsultationEntity,
    @Relation(parentColumn = "doctorId", entityColumn = "doctorId")
    val doctor: DoctorProfileEntity?,
)
