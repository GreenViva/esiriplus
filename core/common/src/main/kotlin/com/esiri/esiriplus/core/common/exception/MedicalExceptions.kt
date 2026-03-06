package com.esiri.esiriplus.core.common.exception

/**
 * Base exception for all eSIRI Plus domain-specific errors.
 * Subclasses provide typed context for each medical domain area.
 */
open class EsiriplusException(
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause)

class ConsultationException(
    message: String,
    cause: Throwable? = null,
    val consultationId: String? = null,
) : EsiriplusException(message, cause)

class PaymentException(
    message: String,
    cause: Throwable? = null,
    val paymentReference: String? = null,
    val amountTzs: Long? = null,
) : EsiriplusException(message, cause)

class SessionException(
    message: String,
    cause: Throwable? = null,
    val sessionId: String? = null,
) : EsiriplusException(message, cause)

class AuthenticationException(
    message: String,
    cause: Throwable? = null,
) : EsiriplusException(message, cause)

class DoctorProfileException(
    message: String,
    cause: Throwable? = null,
    val doctorId: String? = null,
) : EsiriplusException(message, cause)

class AppointmentException(
    message: String,
    cause: Throwable? = null,
    val appointmentId: String? = null,
) : EsiriplusException(message, cause)

class VideoCallException(
    message: String,
    cause: Throwable? = null,
    val meetingId: String? = null,
) : EsiriplusException(message, cause)

class FileUploadException(
    message: String,
    cause: Throwable? = null,
    val filePath: String? = null,
    val maxSizeMb: Int? = null,
) : EsiriplusException(message, cause)
