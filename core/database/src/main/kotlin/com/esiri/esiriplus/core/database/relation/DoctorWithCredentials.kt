package com.esiri.esiriplus.core.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.esiri.esiriplus.core.database.entity.DoctorCredentialsEntity
import com.esiri.esiriplus.core.database.entity.DoctorProfileEntity

data class DoctorWithCredentials(
    @Embedded val doctor: DoctorProfileEntity,
    @Relation(parentColumn = "doctorId", entityColumn = "doctorId")
    val credentials: List<DoctorCredentialsEntity>,
)
