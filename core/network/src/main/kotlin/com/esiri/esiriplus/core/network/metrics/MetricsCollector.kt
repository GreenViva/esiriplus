package com.esiri.esiriplus.core.network.metrics

import android.util.Log
import com.esiri.esiriplus.core.common.di.IoDispatcher
import com.esiri.esiriplus.core.network.BuildConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class MetricEntry(
    @SerialName("metric_type") val metricType: String,
    val endpoint: String,
    val method: String? = null,
    @SerialName("status_code") val statusCode: Int? = null,
    @SerialName("latency_ms") val latencyMs: Int,
    val success: Boolean = true,
    @SerialName("error_type") val errorType: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
    val platform: String = "android",
)

/**
 * Collects performance metrics from [MetricsInterceptor] and periodically
 * flushes them to the Supabase `log-performance-metrics` edge function.
 *
 * Uses a dedicated bare [OkHttpClient] (no interceptors) to avoid circular
 * dependency with the authenticated client and to prevent metrics-of-metrics
 * recursion.
 */
@Singleton
class MetricsCollector @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val buffer = ConcurrentLinkedQueue<MetricEntry>()
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val json = Json { ignoreUnknownKeys = true }

    /** Bare client — no auth interceptors, no certificate pinning, short timeouts. */
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    init {
        scope.launch {
            while (true) {
                delay(FLUSH_INTERVAL_MS)
                flush()
            }
        }
    }

    /** Record a single metric entry. Triggers an early flush when the buffer is full. */
    fun record(entry: MetricEntry) {
        buffer.add(entry)
        if (buffer.size >= BATCH_THRESHOLD) {
            scope.launch { flush() }
        }
    }

    private suspend fun flush() {
        val batch = mutableListOf<MetricEntry>()
        while (batch.size < MAX_BATCH_SIZE) {
            buffer.poll()?.let { batch.add(it) } ?: break
        }
        if (batch.isEmpty()) return

        withContext(ioDispatcher) {
            try {
                val body = json.encodeToString(
                    ListSerializer(MetricEntry.serializer()),
                    batch,
                )
                val request = Request.Builder()
                    .url("${BuildConfig.SUPABASE_URL}/functions/v1/log-performance-metrics")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
                    .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .build()

                httpClient.newCall(request).execute().close()
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Log.w(TAG, "Failed to flush ${batch.size} metrics", e)
            }
        }
    }

    companion object {
        private const val TAG = "MetricsCollector"
        private const val FLUSH_INTERVAL_MS = 60_000L
        private const val BATCH_THRESHOLD = 50
        private const val MAX_BATCH_SIZE = 100
        private const val TIMEOUT_SECONDS = 10L
    }
}
