package com.esiri.esiriplus.feature.patient.data

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.common.util.IdempotencyKeyGenerator
import com.esiri.esiriplus.core.domain.model.Consultation
import com.esiri.esiriplus.core.domain.model.ServiceType
import com.esiri.esiriplus.core.domain.repository.ConsultationRepository
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.api.SupabaseApi
import com.esiri.esiriplus.core.network.api.model.UpdateConsultationStatusBody
import com.esiri.esiriplus.core.network.api.model.toDomain
import com.esiri.esiriplus.core.network.dto.ConsultationResponse
import com.esiri.esiriplus.core.network.dto.CreateConsultationRequest
import com.esiri.esiriplus.core.network.model.safeApiCall
import com.esiri.esiriplus.core.network.model.toDomainResult
import com.esiri.esiriplus.core.network.model.toApiResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConsultationRepositoryImpl @Inject constructor(
    private val supabaseApi: SupabaseApi,
    private val edgeFunctionClient: EdgeFunctionClient,
) : ConsultationRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun getConsultationsForPatient(patientId: String): Flow<List<Consultation>> = flow {
        val response = supabaseApi.getConsultationsForPatient(
            patientIdFilter = "eq.$patientId",
        )
        val result = response.toApiResult()
        emit(result.getOrNull()?.map { it.toDomain() } ?: emptyList())
    }

    override fun getConsultationsForDoctor(doctorId: String): Flow<List<Consultation>> = flow {
        val response = supabaseApi.getConsultationsForDoctor(
            doctorIdFilter = "eq.$doctorId",
        )
        val result = response.toApiResult()
        emit(result.getOrNull()?.map { it.toDomain() } ?: emptyList())
    }

    override suspend fun createConsultation(
        patientId: String,
        serviceType: ServiceType,
    ): Result<Consultation> {
        val request = CreateConsultationRequest(
            patientId = patientId,
            serviceType = serviceType.name,
            idempotencyKey = IdempotencyKeyGenerator.generate("consultation"),
        )
        val body = json.encodeToString(request).let { Json.parseToJsonElement(it).jsonObject }

        val apiResult = edgeFunctionClient.invokeAndDecode<ConsultationResponse>(
            functionName = "create-consultation",
            body = body,
        )

        return apiResult.map { response ->
            Consultation(
                id = response.id,
                patientId = response.patientId,
                doctorId = response.doctorId,
                serviceType = ServiceType.entries.find { it.name.equals(response.serviceType, ignoreCase = true) }
                    ?: ServiceType.GENERAL_CONSULTATION,
                status = com.esiri.esiriplus.core.domain.model.ConsultationStatus.entries
                    .find { it.name.equals(response.status, ignoreCase = true) }
                    ?: com.esiri.esiriplus.core.domain.model.ConsultationStatus.PENDING,
                notes = response.notes,
                createdAt = Instant.parse(response.createdAt),
                updatedAt = response.updatedAt?.let { Instant.parse(it) },
            )
        }.toDomainResult()
    }

    override suspend fun updateConsultationStatus(
        consultationId: String,
        status: String,
    ): Result<Consultation> {
        val apiResult = safeApiCall {
            val response = supabaseApi.updateConsultationStatus(
                idFilter = "eq.$consultationId",
                body = UpdateConsultationStatusBody(status = status),
            )
            response.toApiResult().getOrThrow().toDomain()
        }
        return apiResult.toDomainResult()
    }

    override suspend fun getConsultation(consultationId: String): Result<Consultation> {
        val apiResult = safeApiCall {
            val response = supabaseApi.getConsultation(idFilter = "eq.$consultationId")
            response.toApiResult().getOrThrow().toDomain()
        }
        return apiResult.toDomainResult()
    }
}
