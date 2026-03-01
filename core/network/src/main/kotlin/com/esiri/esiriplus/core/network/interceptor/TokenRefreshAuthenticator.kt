package com.esiri.esiriplus.core.network.interceptor

import com.esiri.esiriplus.core.network.SessionInvalidator
import com.esiri.esiriplus.core.network.TokenManager
import dagger.Lazy
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject

class TokenRefreshAuthenticator @Inject constructor(
    private val tokenManager: TokenManager,
    private val tokenRefresher: TokenRefresher,
    private val sessionInvalidator: Lazy<SessionInvalidator>,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Patient custom JWTs cannot be refreshed via Supabase Auth.
        // Do NOT attempt refresh or invalidate — just let the request fail gracefully.
        val currentToken = tokenManager.getAccessTokenSync()
        if (currentToken != null && JwtUtils.isPatientToken(currentToken)) {
            return null
        }

        // Don't retry more than once
        if (responseCount(response) > 1) {
            sessionInvalidator.get().invalidate()
            return null
        }

        val requestToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")

        synchronized(this) {
            val latestToken = tokenManager.getAccessTokenSync()

            // Another thread already refreshed the token
            if (latestToken != null && latestToken != requestToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $latestToken")
                    .build()
            }

            val refreshToken = tokenManager.getRefreshTokenSync() ?: run {
                sessionInvalidator.get().invalidate()
                return null
            }

            val success = tokenRefresher.refreshToken(refreshToken)
            if (success) {
                val newToken = tokenManager.getAccessTokenSync() ?: return null
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
            }

            sessionInvalidator.get().invalidate()
            return null
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
