package com.esiri.esiriplus.core.network.interceptor

import android.util.Log
import com.esiri.esiriplus.core.network.BuildConfig
import com.esiri.esiriplus.core.network.TokenManager
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.esiri.esiriplus.core.network.security.CertificatePinning
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class TokenRefresherImpl @Inject constructor(
    private val tokenManager: TokenManager,
    private val moshi: Moshi,
) : TokenRefresher {

    private val bareClient = OkHttpClient.Builder()
        .certificatePinner(CertificatePinning.createCertificatePinner())
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "TokenRefresher"
    }

    override fun refreshToken(currentRefreshToken: String): Boolean {
        // Serialize all refresh attempts so that concurrent calls (e.g. from
        // ProactiveTokenRefreshInterceptor on request A and TokenRefreshAuthenticator
        // on request B's 401) never send the same refresh token twice.  Supabase
        // rotates refresh tokens on every use, so a double-submission always causes
        // the second call to fail and triggers a spurious session-invalidation.
        synchronized(this) {
            // Double-check: another thread may have already refreshed while we waited.
            val latestRefreshToken = tokenManager.getRefreshTokenSync()
            if (latestRefreshToken != null && latestRefreshToken != currentRefreshToken) {
                Log.d(TAG, "Token already refreshed by another thread, skipping duplicate refresh")
                return true
            }

            val url = "${BuildConfig.SUPABASE_URL}/auth/v1/token?grant_type=refresh_token"
            val bodyJson = """{"refresh_token":"$currentRefreshToken"}"""
            val requestBody = bodyJson.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .header("Content-Type", "application/json")
                .build()

            return try {
                bareClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return false
                        val adapter = moshi.adapter(TokenRefreshResponse::class.java)
                        val tokenResponse = adapter.fromJson(body) ?: return false

                        val expiresAtMillis = System.currentTimeMillis() + (tokenResponse.expiresIn * 1000L)
                        tokenManager.saveTokens(
                            accessToken = tokenResponse.accessToken,
                            refreshToken = tokenResponse.refreshToken,
                            expiresAtMillis = expiresAtMillis,
                        )
                        Log.d(TAG, "Token refreshed successfully, expires in ${tokenResponse.expiresIn}s")
                        true
                    } else {
                        Log.e(TAG, "Token refresh failed: HTTP ${response.code} - ${response.body?.string()}")
                        false
                    }
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Re-throw network/IO exceptions so callers can distinguish a transient
                // connectivity failure (e.g. DNS not ready after device wake-up) from an
                // explicit auth rejection (bad refresh token → HTTP 400/401 → returns false).
                // TokenRefreshAuthenticator must NOT invalidate the session for transient errors.
                Log.e(TAG, "Token refresh network exception (not invalidating session)", e)
                throw e
            }
        }
    }
}

@JsonClass(generateAdapter = true)
internal data class TokenRefreshResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "refresh_token") val refreshToken: String,
    @Json(name = "expires_in") val expiresIn: Long,
)
