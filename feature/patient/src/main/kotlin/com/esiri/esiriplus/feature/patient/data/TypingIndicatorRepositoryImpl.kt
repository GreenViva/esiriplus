package com.esiri.esiriplus.feature.patient.data

import com.esiri.esiriplus.core.database.dao.TypingIndicatorDao
import com.esiri.esiriplus.core.database.entity.TypingIndicatorEntity
import com.esiri.esiriplus.core.domain.model.TypingIndicator
import com.esiri.esiriplus.core.domain.repository.TypingIndicatorRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TypingIndicatorRepositoryImpl @Inject constructor(
    private val typingIndicatorDao: TypingIndicatorDao,
) : TypingIndicatorRepository {

    override fun getTypingIndicators(consultationId: String): Flow<List<TypingIndicator>> {
        return typingIndicatorDao.getByConsultationId(consultationId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun updateTypingStatus(indicator: TypingIndicator) {
        typingIndicatorDao.upsert(indicator.toEntity())
    }

    override suspend fun cleanupOldIndicators() {
        val threshold = System.currentTimeMillis() - 10_000L
        typingIndicatorDao.deleteOld(threshold)
    }

    override suspend fun clearAll() {
        typingIndicatorDao.clearAll()
    }
}

private fun TypingIndicatorEntity.toDomain(): TypingIndicator = TypingIndicator(
    consultationId = consultationId,
    userId = userId,
    isTyping = isTyping,
    updatedAt = updatedAt,
)

private fun TypingIndicator.toEntity(): TypingIndicatorEntity = TypingIndicatorEntity(
    consultationId = consultationId,
    userId = userId,
    isTyping = isTyping,
    updatedAt = updatedAt,
)
