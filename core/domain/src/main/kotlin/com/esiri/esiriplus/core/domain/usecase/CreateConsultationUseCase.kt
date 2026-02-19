package com.esiri.esiriplus.core.domain.usecase

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.Consultation
import com.esiri.esiriplus.core.domain.model.ServiceType
import com.esiri.esiriplus.core.domain.repository.ConsultationRepository
import javax.inject.Inject

class CreateConsultationUseCase @Inject constructor(
    private val consultationRepository: ConsultationRepository,
) {
    suspend operator fun invoke(patientId: String, serviceType: ServiceType): Result<Consultation> =
        consultationRepository.createConsultation(patientId, serviceType)
}
