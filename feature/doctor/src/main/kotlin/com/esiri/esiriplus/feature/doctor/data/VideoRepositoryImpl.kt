package com.esiri.esiriplus.feature.doctor.data

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.repository.VideoRepository
import com.esiri.esiriplus.core.domain.repository.VideoToken
import com.esiri.esiriplus.core.network.EdgeFunctionClient
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("UnusedPrivateProperty")
@Singleton
class VideoRepositoryImpl @Inject constructor(
    private val edgeFunctionClient: EdgeFunctionClient,
) : VideoRepository {

    override suspend fun getVideoToken(consultationId: String): Result<VideoToken> =
        Result.Error(NotImplementedError("Not yet implemented"))
}
