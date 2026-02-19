package com.esiri.esiriplus.core.network.interceptor

import com.esiri.esiriplus.core.network.TokenManager
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject

class TokenRefreshAuthenticator @Inject constructor(
    private val tokenManager: TokenManager,
    private val tokenRefresher: TokenRefresher,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Don't retry more than once
        if (responseCount(response) > 1) {
            tokenManager.clearTokens()
            return null
        }

        val requestToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")

        synchronized(this) {
            val currentToken = tokenManager.getAccessTokenSync()

            // Another thread already refreshed the token
            if (currentToken != null && currentToken != requestToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }

            val refreshToken = tokenManager.getRefreshTokenSync() ?: run {
                tokenManager.clearTokens()
                return null
            }

            val success = tokenRefresher.refreshToken(refreshToken)
            if (success) {
                val newToken = tokenManager.getAccessTokenSync() ?: return null
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
            }

            tokenManager.clearTokens()
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
