package com.esiri.esiriplus.core.network.api.model

import com.esiri.esiriplus.core.domain.model.Consultation
import com.esiri.esiriplus.core.domain.model.ConsultationStatus
import com.esiri.esiriplus.core.domain.model.Payment
import com.esiri.esiriplus.core.domain.model.PaymentStatus
import com.esiri.esiriplus.core.domain.model.ServiceType
import com.esiri.esiriplus.core.domain.model.User
import com.esiri.esiriplus.core.domain.model.UserRole
import java.time.Instant

fun ConsultationApiModel.toDomain(): Consultation = Consultation(
    id = id,
    patientId = patientId,
    doctorId = doctorId,
    serviceType = ServiceType.entries.find { it.name.equals(serviceType, ignoreCase = true) }
        ?: ServiceType.GENERAL_CONSULTATION,
    status = ConsultationStatus.entries.find { it.name.equals(status, ignoreCase = true) }
        ?: ConsultationStatus.PENDING,
    notes = notes,
    createdAt = Instant.parse(createdAt),
    updatedAt = updatedAt?.let { Instant.parse(it) },
)

fun PaymentApiModel.toDomain(): Payment = Payment(
    id = id,
    consultationId = consultationId,
    amount = amount,
    currency = currency,
    status = PaymentStatus.entries.find { it.name.equals(status, ignoreCase = true) }
        ?: PaymentStatus.PENDING,
    mpesaReceiptNumber = mpesaReceiptNumber,
    createdAt = Instant.parse(createdAt),
)

fun UserApiModel.toDomain(): User = User(
    id = id,
    fullName = fullName,
    phone = phone,
    email = email,
    role = UserRole.entries.find { it.name.equals(role, ignoreCase = true) }
        ?: UserRole.PATIENT,
    isVerified = isVerified,
)
