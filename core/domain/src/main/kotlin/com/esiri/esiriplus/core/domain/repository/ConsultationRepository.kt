package com.esiri.esiriplus.core.domain.repository

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.Consultation
import com.esiri.esiriplus.core.domain.model.ServiceType
import kotlinx.coroutines.flow.Flow

interface ConsultationRepository {
    fun getConsultationsForPatient(patientId: String): Flow<List<Consultation>>
    fun getConsultationsForDoctor(doctorId: String): Flow<List<Consultation>>
    suspend fun createConsultation(patientId: String, serviceType: ServiceType): Result<Consultation>
    suspend fun bookAppointment(
        doctorId: String,
        serviceType: String,
        consultationType: String,
        chiefComplaint: String,
        preferredLanguage: String,
    ): Result<Consultation>
    suspend fun updateConsultationStatus(consultationId: String, status: String): Result<Consultation>
    suspend fun getConsultation(consultationId: String): Result<Consultation>
}
