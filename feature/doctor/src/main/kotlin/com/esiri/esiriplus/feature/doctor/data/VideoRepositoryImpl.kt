package com.esiri.esiriplus.feature.doctor.data

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.repository.VideoRepository
import com.esiri.esiriplus.core.domain.repository.VideoToken
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.dto.VideoTokenRequest
import com.esiri.esiriplus.core.network.dto.VideoTokenResponse
import com.esiri.esiriplus.core.network.model.toDomainResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepositoryImpl @Inject constructor(
    private val edgeFunctionClient: EdgeFunctionClient,
) : VideoRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getVideoToken(consultationId: String): Result<VideoToken> {
        val request = VideoTokenRequest(consultationId = consultationId)
        val body = json.encodeToString(request).let { Json.parseToJsonElement(it).jsonObject }

        val apiResult = edgeFunctionClient.invokeAndDecode<VideoTokenResponse>(
            functionName = "get-video-token",
            body = body,
        )

        return apiResult.map { response ->
            VideoToken(
                token = response.token,
                roomId = response.roomId,
            )
        }.toDomainResult()
    }
}
