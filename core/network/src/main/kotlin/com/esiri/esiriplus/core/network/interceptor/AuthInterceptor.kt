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
            requestBuilder.header(HEADER_AUTHORIZATION, "Bearer $token")
        }

        return chain.proceed(requestBuilder.build())
    }

    companion object {
        private const val HEADER_AUTHORIZATION = "Authorization"
        private const val HEADER_API_KEY = "apikey"
    }
}
