package com.esiri.esiriplus.feature.patient.data

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.Consultation
import com.esiri.esiriplus.core.domain.model.ServiceType
import com.esiri.esiriplus.core.domain.repository.ConsultationRepository
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("UnusedPrivateProperty")
@Singleton
class ConsultationRepositoryImpl @Inject constructor(
    private val edgeFunctionClient: EdgeFunctionClient,
) : ConsultationRepository {

    override fun getConsultationsForPatient(patientId: String): Flow<List<Consultation>> =
        flowOf(emptyList()) // TODO: Implement

    override fun getConsultationsForDoctor(doctorId: String): Flow<List<Consultation>> =
        flowOf(emptyList()) // TODO: Implement

    override suspend fun createConsultation(patientId: String, serviceType: ServiceType): Result<Consultation> =
        Result.Error(NotImplementedError("Not yet implemented"))

    override suspend fun updateConsultationStatus(consultationId: String, status: String): Result<Consultation> =
        Result.Error(NotImplementedError("Not yet implemented"))

    override suspend fun getConsultation(consultationId: String): Result<Consultation> =
        Result.Error(NotImplementedError("Not yet implemented"))
}
