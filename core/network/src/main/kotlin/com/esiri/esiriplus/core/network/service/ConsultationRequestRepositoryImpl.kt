package com.esiri.esiriplus.core.network.service

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.ConsultationRequest
import com.esiri.esiriplus.core.domain.model.ConsultationRequestStatus
import com.esiri.esiriplus.core.domain.repository.ConsultationRequestRepository
import com.esiri.esiriplus.core.network.model.toDomainResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConsultationRequestRepositoryImpl @Inject constructor(
    private val service: ConsultationRequestService,
) : ConsultationRequestRepository {

    override suspend fun createRequest(
        doctorId: String,
        serviceType: String,
        consultationType: String,
        chiefComplaint: String,
        symptoms: String?,
        patientAgeGroup: String?,
        patientSex: String?,
        patientBloodGroup: String?,
        patientAllergies: String?,
        patientChronicConditions: String?,
    ): Result<ConsultationRequest> {
        return service.createRequest(
            doctorId = doctorId,
            serviceType = serviceType,
            consultationType = consultationType,
            chiefComplaint = chiefComplaint,
            symptoms = symptoms,
            patientAgeGroup = patientAgeGroup,
            patientSex = patientSex,
            patientBloodGroup = patientBloodGroup,
            patientAllergies = patientAllergies,
            patientChronicConditions = patientChronicConditions,
        ).map { it.toDomain() }
            .toDomainResult()
    }

    override suspend fun acceptRequest(requestId: String): Result<ConsultationRequest> {
        return service.acceptRequest(requestId)
            .map { it.toDomain() }
            .toDomainResult()
    }

    override suspend fun rejectRequest(requestId: String): Result<ConsultationRequest> {
        return service.rejectRequest(requestId)
            .map { it.toDomain() }
            .toDomainResult()
    }

    override suspend fun expireRequest(requestId: String): Result<ConsultationRequest> {
        return service.expireRequest(requestId)
            .map { it.toDomain() }
            .toDomainResult()
    }

    override suspend fun checkRequestStatus(requestId: String): Result<ConsultationRequest> {
        return service.checkRequestStatus(requestId)
            .map { it.toDomain() }
            .toDomainResult()
    }
}

private fun ConsultationRequestRow.toDomain(): ConsultationRequest {
    return ConsultationRequest(
        requestId = requestId,
        patientSessionId = "", // Not returned by server; caller has it
        doctorId = "",         // Not returned by server; caller has it
        serviceType = "",      // Not returned by server; caller has it
        status = ConsultationRequestStatus.fromString(status),
        createdAt = parseTimestamp(createdAt),
        expiresAt = parseTimestamp(expiresAt),
        consultationId = consultationId,
    )
}

private fun parseTimestamp(value: String?): Long {
    if (value == null) return System.currentTimeMillis()
    return try {
        java.time.Instant.parse(value).toEpochMilli()
    } catch (_: Exception) {
        System.currentTimeMillis()
    }
}
