package com.esiri.esiriplus.core.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client-side rate limiter that prevents the app from hammering the server.
 * Uses a sliding-window counter per endpoint category.
 *
 * When the limit is hit, the interceptor delays the request (back-pressure)
 * rather than dropping it, so the user experience degrades gracefully.
 */
@Singleton
class ClientRateLimiterInterceptor @Inject constructor() : Interceptor {

    private val windows = ConcurrentHashMap<String, RateWindow>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val category = categorize(request.url.encodedPath)
        val limit = LIMITS[category] ?: DEFAULT_LIMIT

        val window = windows.getOrPut(category) { RateWindow() }
        window.waitIfNeeded(limit)

        return chain.proceed(request)
    }

    private fun categorize(path: String): String = when {
        path.contains("/functions/v1/mpesa") -> "payment"
        path.contains("/functions/v1/service-access-payment") -> "payment"
        path.contains("/functions/v1/call-recharge-payment") -> "payment"
        path.contains("/functions/v1/videosdk-token") -> "video"
        path.contains("/functions/v1/handle-consultation-request") -> "consultation"
        path.contains("/functions/v1/handle-messages") -> "messages"
        path.contains("/functions/v1/list-doctors") -> "read"
        path.contains("/functions/v1/list-all-doctors") -> "read"
        path.contains("/functions/v1/get-") -> "read"
        path.contains("/rest/v1/") -> "rest"
        path.contains("/functions/v1/") -> "edge"
        else -> "default"
    }

    /**
     * Sliding window: tracks request count in the current window.
     * If limit is exceeded, sleeps until the window resets.
     */
    private class RateWindow {
        private val count = AtomicInteger(0)
        private val windowStart = AtomicLong(System.currentTimeMillis())

        fun waitIfNeeded(maxPerWindow: Int) {
            val now = System.currentTimeMillis()
            val start = windowStart.get()

            // Reset window if expired
            if (now - start >= WINDOW_MS) {
                if (windowStart.compareAndSet(start, now)) {
                    count.set(0)
                }
            }

            val current = count.incrementAndGet()
            if (current > maxPerWindow) {
                // Back-pressure: wait until next window
                val sleepMs = WINDOW_MS - (System.currentTimeMillis() - windowStart.get())
                if (sleepMs > 0) {
                    Thread.sleep(sleepMs.coerceAtMost(MAX_WAIT_MS))
                }
                // Reset after waiting
                windowStart.set(System.currentTimeMillis())
                count.set(1)
            }
        }
    }

    companion object {
        private const val WINDOW_MS = 60_000L // 1 minute
        private const val MAX_WAIT_MS = 5_000L // Never block more than 5s

        // Requests per minute per category
        private val LIMITS = mapOf(
            "payment" to 8,
            "video" to 20,
            "consultation" to 15,
            "messages" to 40,
            "read" to 30,
            "rest" to 50,
            "edge" to 30,
            "default" to 60,
        )
        private const val DEFAULT_LIMIT = 60
    }
}
