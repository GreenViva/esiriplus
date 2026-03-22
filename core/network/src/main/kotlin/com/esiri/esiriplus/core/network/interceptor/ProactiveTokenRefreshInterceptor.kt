package com.esiri.esiriplus.core.network.interceptor

import com.esiri.esiriplus.core.network.TokenManager
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class ProactiveTokenRefreshInterceptor @Inject constructor(
    private val tokenManager: TokenManager,
    private val tokenRefresher: TokenRefresher,
) : Interceptor {

    private val refreshing = AtomicBoolean(false)

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenManager.getAccessTokenSync()
        // Only refresh Supabase Auth tokens (doctors). Patient custom JWTs
        // cannot be refreshed via Supabase Auth.
        if (token != null && !JwtUtils.isPatientToken(token) && tokenManager.isTokenExpiringSoon()) {
            if (refreshing.compareAndSet(false, true)) {
                try {
                    val refreshToken = tokenManager.getRefreshTokenSync()
                    if (refreshToken != null) {
                        tokenRefresher.refreshToken(refreshToken)
                    }
                } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
                    // Swallow proactive-refresh failures — the interceptor must never
                    // crash the OkHttp chain.  If the refresh fails here (network error
                    // or bad token), the subsequent 401 will be handled by
                    // TokenRefreshAuthenticator with proper error classification.
                } finally {
                    refreshing.set(false)
                }
            }
        }
        return chain.proceed(chain.request())
    }
}
