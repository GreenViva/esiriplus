package com.esiri.esiriplus.core.network.dto

import com.esiri.esiriplus.core.domain.model.SecurityQuestion
import kotlinx.serialization.Serializable

@Serializable
data class SecurityQuestionDto(val key: String, val label: String)

fun SecurityQuestionDto.toDomain(): SecurityQuestion = SecurityQuestion(
    key = key,
    label = label,
)
