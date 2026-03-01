package com.esiri.esiriplus.core.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class RetryInterceptor(
    private val maxRetries: Int = MAX_RETRIES,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var lastException: IOException? = null

        for (attempt in 0..maxRetries) {
            try {
                val response = chain.proceed(request)

                if (response.isSuccessful || !isRetryableStatusCode(response.code)) {
                    return response
                }

                if (attempt < maxRetries) {
                    response.close()
                    sleepWithBackoff(attempt)
                } else {
                    return response
                }
            } catch (e: IOException) {
                lastException = e
                if (attempt < maxRetries) {
                    sleepWithBackoff(attempt)
                }
            }
        }

        throw lastException ?: IOException("Request failed after $maxRetries retries")
    }

    private fun isRetryableStatusCode(code: Int): Boolean =
        code == HTTP_REQUEST_TIMEOUT ||
            code in HTTP_SERVER_ERROR_RANGE

    private fun sleepWithBackoff(attempt: Int) {
        val delayMillis = INITIAL_BACKOFF_MS * (1L shl attempt)
        Thread.sleep(delayMillis)
    }

    companion object {
        const val MAX_RETRIES = 3
        const val INITIAL_BACKOFF_MS = 1000L
        private const val HTTP_REQUEST_TIMEOUT = 408
        private val HTTP_SERVER_ERROR_RANGE = 500..599
    }
}
