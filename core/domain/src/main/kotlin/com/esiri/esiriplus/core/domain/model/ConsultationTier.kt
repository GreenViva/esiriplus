package com.esiri.esiriplus.core.domain.model

enum class ConsultationTier {
    ROYAL,
    ECONOMY;

    companion object {
        fun fromString(value: String): ConsultationTier =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: ECONOMY
    }
}
