package com.esiri.esiriplus.core.domain.repository

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.model.ConsultationRequest

interface ConsultationRequestRepository {
    /** Patient sends a consultation request to a specific doctor. */
    suspend fun createRequest(
        doctorId: String,
        serviceType: String,
        consultationType: String,
        chiefComplaint: String,
    ): Result<ConsultationRequest>

    /** Doctor accepts a pending request. Returns request with consultationId. */
    suspend fun acceptRequest(requestId: String): Result<ConsultationRequest>

    /** Doctor rejects a pending request. */
    suspend fun rejectRequest(requestId: String): Result<ConsultationRequest>

    /** Mark a request as expired (client-side fallback; server validates timestamp). */
    suspend fun expireRequest(requestId: String): Result<ConsultationRequest>
}
