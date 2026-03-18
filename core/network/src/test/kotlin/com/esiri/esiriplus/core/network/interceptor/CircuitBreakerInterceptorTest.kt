package com.esiri.esiriplus.core.network.interceptor

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CircuitBreakerInterceptorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        client = OkHttpClient.Builder()
            .addInterceptor(CircuitBreakerInterceptor())
            .build()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `successful requests keep circuit closed`() {
        repeat(10) {
            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        }

        repeat(10) {
            val response = executeRequest()
            assertEquals(200, response.code)
        }
    }

    @Test
    fun `circuit opens after consecutive 5xx failures`() {
        // Enqueue 5 server errors to trigger circuit open
        repeat(5) {
            mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("error"))
        }

        // Execute 5 failing requests
        repeat(5) {
            val response = executeRequest()
            assertEquals(500, response.code)
        }

        // Next request should fail fast with 503 (circuit open)
        val response = executeRequest()
        assertEquals(503, response.code)
        val body = response.body?.string() ?: ""
        assertTrue(body.contains("CIRCUIT_OPEN"))
    }

    @Test
    fun `4xx errors do not trip the circuit`() {
        repeat(10) {
            mockWebServer.enqueue(MockResponse().setResponseCode(400).setBody("bad request"))
        }

        repeat(10) {
            val response = executeRequest()
            assertEquals(400, response.code)
        }

        // Circuit should still be closed — enqueue a success to verify
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        val response = executeRequest()
        assertEquals(200, response.code)
    }

    @Test
    fun `success resets failure count`() {
        // 4 failures (below threshold of 5)
        repeat(4) {
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
        }
        // Then 1 success resets counter
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        // Then 4 more failures (still below threshold)
        repeat(4) {
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
        }
        // Final success should work
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        // Execute all 10 requests
        repeat(4) { executeRequest() } // 4 failures
        assertEquals(200, executeRequest().code) // success resets
        repeat(4) { executeRequest() } // 4 more failures
        assertEquals(200, executeRequest().code) // still works - circuit never opened
    }

    @Test
    fun `circuit opens per category independently`() {
        // Only one server, but categories are based on path
        // All requests go to the same mock server, but circuit tracks by category
        repeat(5) {
            mockWebServer.enqueue(MockResponse().setResponseCode(500))
        }
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        // 5 failures on rest endpoint path
        repeat(5) {
            val request = Request.Builder()
                .url(mockWebServer.url("/rest/v1/test"))
                .build()
            client.newCall(request).execute()
        }

        // Different category (edge) should still work
        val request = Request.Builder()
            .url(mockWebServer.url("/functions/v1/some-function"))
            .build()
        val response = client.newCall(request).execute()
        assertEquals(200, response.code)
    }

    private fun executeRequest(): okhttp3.Response {
        val request = Request.Builder()
            .url(mockWebServer.url("/rest/v1/test"))
            .build()
        return client.newCall(request).execute()
    }
}
