package com.esiri.esiriplus.core.network.interceptor

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Circuit breaker interceptor that prevents cascading failures.
 *
 * When consecutive failures for an endpoint category exceed [FAILURE_THRESHOLD],
 * the circuit opens and requests fail fast for [OPEN_DURATION_MS].
 * After the cooldown, a single probe request is allowed (half-open state).
 * If it succeeds, the circuit closes; if it fails, it reopens.
 *
 * Placed after [RetryInterceptor] in the chain so it only sees
 * requests that have already exhausted their retries.
 */
@Singleton
class CircuitBreakerInterceptor @Inject constructor() : Interceptor {

    private val circuits = ConcurrentHashMap<String, CircuitState>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val category = categorize(request.url.encodedPath)
        val circuit = circuits.getOrPut(category) { CircuitState() }

        return when (circuit.state.get()) {
            State.OPEN -> {
                if (circuit.shouldAttemptProbe()) {
                    circuit.state.set(State.HALF_OPEN)
                    Log.d(TAG, "Circuit HALF_OPEN for '$category' — allowing probe request")
                    executeAndRecord(chain, circuit, category)
                } else {
                    Log.w(TAG, "Circuit OPEN for '$category' — failing fast")
                    failFast(category)
                }
            }
            State.HALF_OPEN -> {
                // Only one probe at a time; reject concurrent requests
                Log.d(TAG, "Circuit HALF_OPEN for '$category' — rejecting concurrent request")
                failFast(category)
            }
            State.CLOSED -> {
                executeAndRecord(chain, circuit, category)
            }
        }
    }

    private fun executeAndRecord(
        chain: Interceptor.Chain,
        circuit: CircuitState,
        category: String,
    ): Response {
        return try {
            val response = chain.proceed(chain.request())
            if (response.isSuccessful || response.code < 500) {
                circuit.recordSuccess()
                if (circuit.state.get() == State.HALF_OPEN) {
                    Log.d(TAG, "Probe succeeded — circuit CLOSED for '$category'")
                }
            } else {
                circuit.recordFailure()
                if (circuit.state.get() == State.OPEN) {
                    Log.w(TAG, "Circuit OPENED for '$category' after ${FAILURE_THRESHOLD} consecutive 5xx errors")
                }
            }
            response
        } catch (e: IOException) {
            circuit.recordFailure()
            if (circuit.state.get() == State.OPEN) {
                Log.w(TAG, "Circuit OPENED for '$category' after ${FAILURE_THRESHOLD} consecutive IO failures")
            }
            throw e
        }
    }

    private fun failFast(category: String): Response {
        return Response.Builder()
            .request(okhttp3.Request.Builder().url("https://circuit-breaker.local/$category").build())
            .protocol(Protocol.HTTP_1_1)
            .code(SERVICE_UNAVAILABLE)
            .message("Circuit breaker open for $category")
            .body("{\"error\":\"Service temporarily unavailable. Please try again later.\",\"code\":\"CIRCUIT_OPEN\"}".toResponseBody())
            .build()
    }

    private fun categorize(path: String): String = when {
        path.contains("/functions/v1/mpesa") ||
            path.contains("/functions/v1/service-access-payment") ||
            path.contains("/functions/v1/call-recharge-payment") -> "payment"
        path.contains("/functions/v1/videosdk-token") -> "video"
        path.contains("/functions/v1/handle-consultation-request") -> "consultation"
        path.contains("/functions/v1/handle-messages") -> "messages"
        path.contains("/rest/v1/") -> "rest"
        path.contains("/functions/v1/") -> "edge"
        else -> "default"
    }

    private enum class State { CLOSED, OPEN, HALF_OPEN }

    private class CircuitState {
        val state = AtomicReference(State.CLOSED)
        private val consecutiveFailures = AtomicInteger(0)
        private val openedAt = AtomicLong(0)

        fun recordSuccess() {
            consecutiveFailures.set(0)
            state.set(State.CLOSED)
        }

        fun recordFailure() {
            val failures = consecutiveFailures.incrementAndGet()
            if (failures >= FAILURE_THRESHOLD) {
                state.set(State.OPEN)
                openedAt.set(System.currentTimeMillis())
            }
        }

        fun shouldAttemptProbe(): Boolean {
            val elapsed = System.currentTimeMillis() - openedAt.get()
            return elapsed >= OPEN_DURATION_MS
        }
    }

    companion object {
        private const val TAG = "CircuitBreaker"
        private const val FAILURE_THRESHOLD = 5
        private const val OPEN_DURATION_MS = 30_000L // 30 seconds cooldown
        private const val SERVICE_UNAVAILABLE = 503
    }
}
