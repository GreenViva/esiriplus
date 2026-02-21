package com.esiri.esiriplus.core.domain.repository

import com.esiri.esiriplus.core.domain.model.TypingIndicator
import kotlinx.coroutines.flow.Flow

interface TypingIndicatorRepository {
    fun getTypingIndicators(consultationId: String): Flow<List<TypingIndicator>>
    suspend fun updateTypingStatus(indicator: TypingIndicator)
    suspend fun cleanupOldIndicators()
    suspend fun clearAll()
}
