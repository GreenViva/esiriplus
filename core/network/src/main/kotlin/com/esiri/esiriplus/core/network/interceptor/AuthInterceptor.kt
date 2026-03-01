package com.esiri.esiriplus.core.network.interceptor

import com.esiri.esiriplus.core.network.BuildConfig
import com.esiri.esiriplus.core.network.TokenManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()
            .header(HEADER_API_KEY, BuildConfig.SUPABASE_ANON_KEY)

        val token = tokenManager.getAccessTokenSync()

        if (token != null) {
            val isEdgeFunction = originalRequest.url.encodedPath.contains("/functions/v1/")

            if (isEdgeFunction) {
                // Edge Functions accept any JWT (custom patient JWTs + doctor Supabase JWTs)
                requestBuilder.header(HEADER_AUTHORIZATION, "Bearer $token")
            } else if (!JwtUtils.isPatientToken(token)) {
                // Doctor tokens (role="authenticated") work with PostgREST/Realtime/Storage.
                // Patient tokens (role="patient") are NOT valid Postgres roles and cause 401.
                requestBuilder.header(HEADER_AUTHORIZATION, "Bearer $token")
            }
            // For patient tokens on non-edge-function URLs: don't set Authorization.
            // PostgREST will use the apikey (anon role) which has public RLS access.
        } else {
            // No token — use anon key for edge functions (they require Authorization header)
            val isEdgeFunction = originalRequest.url.encodedPath.contains("/functions/v1/")
            if (isEdgeFunction) {
                requestBuilder.header(HEADER_AUTHORIZATION, "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            }
        }

        return chain.proceed(requestBuilder.build())
    }

    companion object {
        private const val HEADER_AUTHORIZATION = "Authorization"
        private const val HEADER_API_KEY = "apikey"
    }
}
