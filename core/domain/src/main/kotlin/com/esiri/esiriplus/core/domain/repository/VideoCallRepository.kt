package com.esiri.esiriplus.core.domain.repository

import com.esiri.esiriplus.core.domain.model.VideoCall
import kotlinx.coroutines.flow.Flow

interface VideoCallRepository {
    suspend fun getVideoCallById(callId: String): VideoCall?
    fun getVideoCallsByConsultation(consultationId: String): Flow<List<VideoCall>>
    suspend fun saveVideoCall(videoCall: VideoCall)
    suspend fun deleteVideoCall(videoCall: VideoCall)
    suspend fun clearAll()
}
