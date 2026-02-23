package com.esiri.esiriplus.feature.patient.data

import com.esiri.esiriplus.core.database.dao.VideoCallDao
import com.esiri.esiriplus.core.database.entity.VideoCallEntity
import com.esiri.esiriplus.core.domain.model.CallQuality
import com.esiri.esiriplus.core.domain.model.VideoCall
import com.esiri.esiriplus.core.domain.repository.VideoCallRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoCallRepositoryImpl @Inject constructor(
    private val videoCallDao: VideoCallDao,
) : VideoCallRepository {

    override suspend fun getVideoCallById(callId: String): VideoCall? {
        return videoCallDao.getById(callId)?.toDomain()
    }

    override fun getVideoCallsByConsultation(consultationId: String): Flow<List<VideoCall>> {
        return videoCallDao.getByConsultationId(consultationId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun saveVideoCall(videoCall: VideoCall) {
        videoCallDao.insert(videoCall.toEntity())
    }

    override suspend fun deleteVideoCall(videoCall: VideoCall) {
        videoCallDao.delete(videoCall.toEntity())
    }

    override suspend fun clearAll() {
        videoCallDao.clearAll()
    }
}

private fun VideoCallEntity.toDomain(): VideoCall = VideoCall(
    callId = callId,
    consultationId = consultationId,
    startedAt = startedAt,
    endedAt = endedAt,
    durationSeconds = durationSeconds,
    callQuality = CallQuality.valueOf(callQuality),
    createdAt = createdAt,
)

private fun VideoCall.toEntity(): VideoCallEntity = VideoCallEntity(
    callId = callId,
    consultationId = consultationId,
    startedAt = startedAt,
    endedAt = endedAt,
    durationSeconds = durationSeconds,
    callQuality = callQuality.name,
    createdAt = createdAt,
)
