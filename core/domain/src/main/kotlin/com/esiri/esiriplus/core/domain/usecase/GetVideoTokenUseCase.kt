package com.esiri.esiriplus.core.domain.usecase

import com.esiri.esiriplus.core.common.result.Result
import com.esiri.esiriplus.core.domain.repository.VideoRepository
import com.esiri.esiriplus.core.domain.repository.VideoToken
import javax.inject.Inject

class GetVideoTokenUseCase @Inject constructor(
    private val videoRepository: VideoRepository,
) {
    suspend operator fun invoke(consultationId: String): Result<VideoToken> =
        videoRepository.getVideoToken(consultationId)
}
