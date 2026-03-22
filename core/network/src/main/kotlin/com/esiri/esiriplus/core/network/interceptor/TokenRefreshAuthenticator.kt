package com.esiri.esiriplus.core.network.interceptor

import android.util.Log
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

        // Don't retry more than once.
        // If the refresh succeeded (responseCount > 1 means we already refreshed and retried)
        // but the retry ALSO got 401, this is most likely a transient server-side failure
        // (e.g. supabase.auth.getUser() timing out inside the edge function) rather than a
        // genuinely invalid session.  Do NOT invalidate here — the tokens are still valid
        // and the next call will either succeed or fail at the refresh step (returning false)
        // which correctly triggers invalidation.
        if (responseCount(response) > 1) {
            Log.w(TAG, "Retry after token refresh also got 401 — transient server issue, not invalidating")
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

            // TokenRefresherImpl returns false ONLY for explicit HTTP 4xx from Supabase
            // (bad / rotated refresh token). It throws for network/IO failures so we can
            // distinguish the two cases here.
            val success = try {
                tokenRefresher.refreshToken(refreshToken)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Transient network failure (DNS not ready, socket timeout, etc.).
                // Do NOT invalidate — the session is still valid; the request simply
                // failed due to connectivity.  OkHttp will surface the original 401
                // to the caller, which can retry later.
                Log.w(TAG, "Token refresh threw (network issue); not invalidating session", e)
                return null
            }

            if (success) {
                val newToken = tokenManager.getAccessTokenSync() ?: return null
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
            }

            // success == false means Supabase explicitly rejected the refresh token
            // (HTTP 400/401).  The session is truly invalid — log the user out.
            Log.e(TAG, "Refresh token rejected by Supabase; invalidating session")
            sessionInvalidator.get().invalidate()
            return null
        }
    }

    companion object {
        private const val TAG = "TokenRefreshAuth"
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
