package com.esiri.esiriplus.core.network.metrics

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject

/**
 * OkHttp interceptor that measures request latency and records it
 * via [MetricsCollector]. Placed first in the interceptor chain to
 * capture the full user-facing wall-clock time (including retries,
 * auth refresh, etc.).
 */
class MetricsInterceptor @Inject constructor(
    private val metricsCollector: MetricsCollector,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startMs = System.currentTimeMillis()

        return try {
            val response = chain.proceed(request)
            val latency = (System.currentTimeMillis() - startMs).toInt()

            metricsCollector.record(
                MetricEntry(
                    metricType = categorizeType(request.url),
                    endpoint = sanitizeEndpoint(request.url),
                    method = request.method,
                    statusCode = response.code,
                    latencyMs = latency,
                    success = response.isSuccessful,
                    errorType = if (response.isSuccessful) null else "http_${response.code}",
                ),
            )
            response
        } catch (e: Exception) {
            val latency = (System.currentTimeMillis() - startMs).toInt()

            metricsCollector.record(
                MetricEntry(
                    metricType = categorizeType(request.url),
                    endpoint = sanitizeEndpoint(request.url),
                    method = request.method,
                    statusCode = null,
                    latencyMs = latency,
                    success = false,
                    errorType = classifyError(e),
                ),
            )
            throw e
        }
    }

    private fun categorizeType(url: HttpUrl): String {
        val path = url.encodedPath
        return when {
            path.contains("/functions/v1/") -> "edge_function"
            else -> "api_response"
        }
    }

    private fun sanitizeEndpoint(url: HttpUrl): String {
        val path = url.encodedPath
        return when {
            path.contains("/functions/v1/") -> path.substringAfter("/functions/v1/")
            path.contains("/rest/v1/") -> "rest/${path.substringAfter("/rest/v1/").substringBefore("?")}"
            path.contains("/auth/v1/") -> "auth/${path.substringAfter("/auth/v1/").substringBefore("?")}"
            path.contains("/storage/v1/") -> "storage/${path.substringAfter("/storage/v1/object/").substringBefore("/")}"
            else -> path.take(MAX_ENDPOINT_LENGTH)
        }
    }

    private fun classifyError(e: Exception): String = when (e) {
        is SocketTimeoutException -> "timeout"
        is UnknownHostException -> "dns"
        is IOException -> "network"
        else -> "unknown"
    }

    companion object {
        private const val MAX_ENDPOINT_LENGTH = 100
    }
}
