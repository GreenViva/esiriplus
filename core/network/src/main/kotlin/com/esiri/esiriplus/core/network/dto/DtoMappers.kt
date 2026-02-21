package com.esiri.esiriplus.core.network.dto

import com.esiri.esiriplus.core.domain.model.Session
import com.esiri.esiriplus.core.domain.model.User
import com.esiri.esiriplus.core.domain.model.UserRole
import java.time.Instant

fun PatientSessionResponse.toDomain(): Session = Session(
    accessToken = accessToken,
    refreshToken = refreshToken,
    expiresAt = Instant.parse(expiresAt),
    user = User(
        id = patientId,
        fullName = "",
        phone = "",
        role = UserRole.PATIENT,
        isVerified = false,
    ),
)

fun SessionResponse.toDomain(): Session = Session(
    accessToken = accessToken,
    refreshToken = refreshToken,
    expiresAt = Instant.ofEpochSecond(expiresAt),
    user = user.toDomain(),
)

fun UserDto.toDomain(): User = User(
    id = id,
    fullName = fullName,
    phone = phone,
    email = email,
    role = UserRole.entries.find { it.name.equals(role, ignoreCase = true) }
        ?: UserRole.PATIENT,
    isVerified = isVerified,
)
