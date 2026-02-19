package com.esiri.esiriplus.core.network.interceptor

import com.esiri.esiriplus.core.network.TokenManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class ProactiveTokenRefreshInterceptor @Inject constructor(
    private val tokenManager: TokenManager,
    private val tokenRefresher: TokenRefresher,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (tokenManager.getAccessTokenSync() != null && tokenManager.isTokenExpiringSoon()) {
            val refreshToken = tokenManager.getRefreshTokenSync()
            if (refreshToken != null) {
                tokenRefresher.refreshToken(refreshToken)
            }
        }
        return chain.proceed(chain.request())
    }
}
