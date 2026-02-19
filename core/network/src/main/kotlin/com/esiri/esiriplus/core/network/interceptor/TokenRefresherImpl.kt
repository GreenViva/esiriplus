package com.esiri.esiriplus.core.network.interceptor

import com.esiri.esiriplus.core.network.BuildConfig
import com.esiri.esiriplus.core.network.TokenManager
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

class TokenRefresherImpl @Inject constructor(
    private val tokenManager: TokenManager,
    private val moshi: Moshi,
) : TokenRefresher {

    private val bareClient = OkHttpClient.Builder().build()

    override fun refreshToken(currentRefreshToken: String): Boolean {
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
            val response = bareClient.newCall(request).execute()
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
                true
            } else {
                false
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            false
        }
    }
}

@JsonClass(generateAdapter = true)
internal data class TokenRefreshResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "refresh_token") val refreshToken: String,
    @Json(name = "expires_in") val expiresIn: Long,
)
