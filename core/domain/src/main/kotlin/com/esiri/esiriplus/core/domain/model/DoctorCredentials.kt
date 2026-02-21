package com.esiri.esiriplus.core.domain.model

data class DoctorCredentials(
    val credentialId: String,
    val doctorId: String,
    val documentUrl: String,
    val documentType: CredentialType,
    val verifiedAt: Long? = null,
)
