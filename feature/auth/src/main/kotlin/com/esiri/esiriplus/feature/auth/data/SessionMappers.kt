package com.esiri.esiriplus.feature.auth.data

import com.esiri.esiriplus.core.database.entity.SessionEntity
import com.esiri.esiriplus.core.database.entity.UserEntity
import com.esiri.esiriplus.core.domain.model.Session
import com.esiri.esiriplus.core.domain.model.User
import com.esiri.esiriplus.core.domain.model.UserRole
import java.time.Instant

fun UserEntity.toDomain(): User = User(
    id = id,
    fullName = fullName,
    phone = phone,
    email = email,
    role = UserRole.entries.find { it.name.equals(role, ignoreCase = true) }
        ?: UserRole.PATIENT,
    isVerified = isVerified,
)

fun User.toEntity(): UserEntity = UserEntity(
    id = id,
    fullName = fullName,
    phone = phone,
    email = email,
    role = role.name,
    isVerified = isVerified,
)

fun SessionEntity.toDomain(user: User): Session = Session(
    accessToken = accessToken,
    refreshToken = refreshToken,
    expiresAt = expiresAt,
    user = user,
    createdAt = createdAt,
)

fun Session.toEntity(): SessionEntity = SessionEntity(
    accessToken = accessToken,
    refreshToken = refreshToken,
    expiresAt = expiresAt,
    userId = user.id,
    createdAt = createdAt,
)
