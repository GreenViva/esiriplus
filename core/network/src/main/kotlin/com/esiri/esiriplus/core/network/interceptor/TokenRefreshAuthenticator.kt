package com.esiri.esiriplus.core.network.interceptor

import android.util.Log
import com.esiri.esiriplus.core.network.BuildConfig
import com.esiri.esiriplus.core.network.EdgeFunctionClient.Companion.HEADER_DOCTOR_TOKEN
import com.esiri.esiriplus.core.network.EdgeFunctionClient.Companion.HEADER_PATIENT_TOKEN
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
        // Detect patient/anonymous requests by headers. Edge function requests set
        // X-Skip-Auth=true. If the request has NO X-Doctor-Token, it's either a
        // patient request or anonymous — never refresh or invalidate for those.
        // The X-Patient-Token header might be absent if the token was null at send time.
        val isSkipAuth = response.request.header("X-Skip-Auth") != null
        val hasDoctorToken = response.request.header(HEADER_DOCTOR_TOKEN) != null
        if (isSkipAuth && !hasDoctorToken) {
            Log.d(TAG, "Non-doctor edge function request got 401 — not invalidating")
            return null
        }

        // Don't retry more than once.
        if (responseCount(response) > 1) {
            Log.w(TAG, "Retry after token refresh also got 401 — transient server issue, not invalidating")
            return null
        }

        // Detect whether this was a doctor edge-function request.
        val requestToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")

        synchronized(this) {
            val latestToken = tokenManager.getAccessTokenSync()

            if (hasDoctorToken) {
                // Doctor edge-function path: Authorization carries anon key (not doctor JWT).
                // Check if the stored doctor token has already been refreshed by another thread.
                val doctorTokenOnRequest = response.request.header(HEADER_DOCTOR_TOKEN)
                if (latestToken != null && latestToken != doctorTokenOnRequest) {
                    return response.request.newBuilder()
                        .header(HEADER_DOCTOR_TOKEN, latestToken)
                        .build()
                }

                val refreshToken = tokenManager.getRefreshTokenSync() ?: run {
                    sessionInvalidator.get().invalidate()
                    return null
                }

                val success = try {
                    tokenRefresher.refreshToken(refreshToken)
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    Log.w(TAG, "Token refresh threw (network issue); not invalidating session", e)
                    return null
                }

                if (success) {
                    val newToken = tokenManager.getAccessTokenSync() ?: return null
                    return response.request.newBuilder()
                        .header(HEADER_DOCTOR_TOKEN, newToken)
                        .build()
                }

                Log.e(TAG, "Refresh token rejected by Supabase; invalidating session")
                sessionInvalidator.get().invalidate()
                return null
            }

            // Standard (non-edge-function) path: Authorization carries the doctor JWT.
            // Another thread already refreshed the token.
            if (latestToken != null && latestToken != requestToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $latestToken")
                    .build()
            }

            val refreshToken = tokenManager.getRefreshTokenSync() ?: run {
                sessionInvalidator.get().invalidate()
                return null
            }

            val success = try {
                tokenRefresher.refreshToken(refreshToken)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Log.w(TAG, "Token refresh threw (network issue); not invalidating session", e)
                return null
            }

            if (success) {
                val newToken = tokenManager.getAccessTokenSync() ?: return null
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
            }

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
