package com.esiri.esiriplus.core.domain.model

data class DoctorRegistration(
    val email: String,
    val password: String,
    val fullName: String,
    val countryCode: String,
    val phone: String,
    val specialty: String,
    val customSpecialty: String,
    val country: String,
    val languages: List<String>,
    val licenseNumber: String,
    val yearsExperience: Int,
    val bio: String,
    val services: List<String>,
    val profilePhotoUri: String?,
    val licenseDocumentUri: String?,
    val certificatesUri: String?,
)
