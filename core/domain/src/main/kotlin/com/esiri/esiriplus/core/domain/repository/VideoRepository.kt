package com.esiri.esiriplus.core.domain.repository

import com.esiri.esiriplus.core.common.result.Result

interface VideoRepository {
    suspend fun getVideoToken(consultationId: String): Result<VideoToken>
}

data class VideoToken(
    val token: String,
    val roomId: String,
)
