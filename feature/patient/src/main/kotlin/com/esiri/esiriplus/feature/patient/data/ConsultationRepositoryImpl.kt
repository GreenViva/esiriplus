package com.esiri.esiriplus.feature.patient.data

import android.util.Log
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.common.util.IdempotencyKeyGenerator
import com.esiri.esiriplus.core.database.dao.ConsultationDao
import com.esiri.esiriplus.core.database.entity.ConsultationEntity
import com.esiri.esiriplus.core.domain.model.Consultation
import com.esiri.esiriplus.core.domain.model.ConsultationStatus
import com.esiri.esiriplus.core.domain.model.ServiceType
import com.esiri.esiriplus.core.domain.repository.ConsultationRepository
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.api.SupabaseApi
import com.esiri.esiriplus.core.network.api.model.ConsultationApiModel
import com.esiri.esiriplus.core.network.api.model.UpdateConsultationStatusBody
import com.esiri.esiriplus.core.network.api.model.toDomain
import com.esiri.esiriplus.core.network.dto.ConsultationResponse
import com.esiri.esiriplus.core.network.dto.CreateConsultationRequest
import com.esiri.esiriplus.core.network.model.safeApiCall
import com.esiri.esiriplus.core.network.model.toDomainResult
import com.esiri.esiriplus.core.network.model.toApiResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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
    private val consultationDao: ConsultationDao,
) : ConsultationRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun getConsultationsForPatient(patientId: String): Flow<List<Consultation>> = flow {
        // Try fetching from network and caching locally
        try {
            val response = supabaseApi.getConsultationsForPatient(
                patientIdFilter = "eq.$patientId",
            )
            val apiModels = response.toApiResult().getOrNull()
            if (apiModels != null) {
                val entities = apiModels.map { it.toEntity(patientId) }
                if (entities.isNotEmpty()) {
                    consultationDao.insertAll(entities)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network fetch failed, falling back to cached data", e)
        }

        // Always emit from local DB (reactive — updates as DB changes)
        emitAll(
            consultationDao.getByPatientSessionId(patientId).map { entities ->
                entities.map { it.toDomain() }
            },
        )
    }

    override fun getConsultationsForDoctor(doctorId: String): Flow<List<Consultation>> = flow {
        try {
            val response = supabaseApi.getConsultationsForDoctor(
                doctorIdFilter = "eq.$doctorId",
            )
            val apiModels = response.toApiResult().getOrNull()
            if (apiModels != null) {
                val entities = apiModels.map { it.toEntity(it.patientId) }
                if (entities.isNotEmpty()) {
                    consultationDao.insertAll(entities)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network fetch failed, falling back to cached data", e)
        }

        emitAll(
            consultationDao.getByPatientSessionId(doctorId).map { entities ->
                entities.map { it.toDomain() }
            },
        )
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

        val domainResult = apiResult.map { response ->
            Consultation(
                id = response.id,
                patientId = response.patientId,
                doctorId = response.doctorId,
                serviceType = ServiceType.entries.find { it.name.equals(response.serviceType, ignoreCase = true) }
                    ?: ServiceType.GENERAL_CONSULTATION,
                status = ConsultationStatus.entries
                    .find { it.name.equals(response.status, ignoreCase = true) }
                    ?: ConsultationStatus.PENDING,
                notes = response.notes,
                createdAt = Instant.parse(response.createdAt),
                updatedAt = response.updatedAt?.let { Instant.parse(it) },
            )
        }.toDomainResult()
        // Cache newly created consultation locally
        if (domainResult is Result.Success) {
            consultationDao.insert(domainResult.data.toEntity())
        }
        return domainResult
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
        val domainResult = apiResult.toDomainResult()
        // Update local cache
        if (domainResult is Result.Success) {
            consultationDao.updateStatus(consultationId, status)
        }
        return domainResult
    }

    override suspend fun getConsultation(consultationId: String): Result<Consultation> {
        val apiResult = safeApiCall {
            val response = supabaseApi.getConsultation(idFilter = "eq.$consultationId")
            response.toApiResult().getOrThrow().toDomain()
        }
        return apiResult.toDomainResult()
    }

    companion object {
        private const val TAG = "ConsultationRepo"
    }
}

private fun ConsultationApiModel.toEntity(patientSessionId: String): ConsultationEntity {
    val createdInstant = try { Instant.parse(createdAt) } catch (_: Exception) { Instant.now() }
    val updatedInstant = try { updatedAt?.let { Instant.parse(it) } } catch (_: Exception) { null }

    return ConsultationEntity(
        consultationId = id,
        patientSessionId = patientSessionId,
        doctorId = doctorId ?: "",
        status = status,
        serviceType = serviceType,
        consultationFee = 0,
        requestExpiresAt = createdInstant.plusSeconds(86400).toEpochMilli(),
        createdAt = createdInstant.toEpochMilli(),
        updatedAt = (updatedInstant ?: createdInstant).toEpochMilli(),
    )
}

private fun ConsultationEntity.toDomain(): Consultation = Consultation(
    id = consultationId,
    patientId = patientSessionId,
    doctorId = doctorId.ifBlank { null },
    serviceType = ServiceType.entries.find { it.name.equals(serviceType, ignoreCase = true) }
        ?: ServiceType.GENERAL_CONSULTATION,
    status = ConsultationStatus.entries.find { it.name.equals(status, ignoreCase = true) }
        ?: ConsultationStatus.PENDING,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

private fun Consultation.toEntity(): ConsultationEntity = ConsultationEntity(
    consultationId = id,
    patientSessionId = patientId,
    doctorId = doctorId ?: "",
    status = status.name,
    serviceType = serviceType.name,
    consultationFee = 0,
    requestExpiresAt = createdAt.plusSeconds(86400).toEpochMilli(),
    createdAt = createdAt.toEpochMilli(),
    updatedAt = (updatedAt ?: createdAt).toEpochMilli(),
)
