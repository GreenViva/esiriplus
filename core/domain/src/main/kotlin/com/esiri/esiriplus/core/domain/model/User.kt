package com.esiri.esiriplus.core.domain.model

data class User(
    val id: String,
    val fullName: String,
    val phone: String,
    val email: String? = null,
    val role: UserRole,
    val isVerified: Boolean = false,
)
