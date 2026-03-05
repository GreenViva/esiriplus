package com.esiri.esiriplus.feature.doctor.data

import android.util.Log
import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.repository.VideoRepository
import com.esiri.esiriplus.core.domain.repository.VideoToken
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import com.esiri.esiriplus.core.network.dto.VideoTokenRequest
import com.esiri.esiriplus.core.network.dto.VideoTokenResponse
import com.esiri.esiriplus.core.network.model.ApiResult
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

    override suspend fun getVideoToken(consultationId: String, callType: String?, roomId: String?): Result<VideoToken> {
        Log.d(TAG, "Requesting video token: consultation=$consultationId, callType=$callType, roomId=$roomId")
        val request = VideoTokenRequest(consultationId = consultationId, callType = callType, roomId = roomId)
        val body = json.encodeToString(request).let { Json.parseToJsonElement(it).jsonObject }

        val apiResult = edgeFunctionClient.invokeAndDecode<VideoTokenResponse>(
            functionName = "videosdk-token",
            body = body,
        )

        when (apiResult) {
            is ApiResult.Success -> Log.d(TAG, "Got video token, roomId=${apiResult.data.roomId}")
            is ApiResult.Error -> Log.e(TAG, "Video token error: code=${apiResult.code}, msg=${apiResult.message}")
            is ApiResult.Unauthorized -> Log.e(TAG, "Video token unauthorized — session expired")
            is ApiResult.NetworkError -> Log.e(TAG, "Video token network error", apiResult.exception)
        }

        return apiResult.map { response ->
            VideoToken(
                token = response.token,
                roomId = response.roomId,
                permissions = response.permissions,
            )
        }.toDomainResult()
    }

    companion object {
        private const val TAG = "VideoRepo"
    }
}
