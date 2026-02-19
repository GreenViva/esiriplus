package com.esiri.esiriplus.core.network.interceptor

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

class RetryInterceptorTest {

    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `does not retry on successful response`() {
        val client = createClient()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        val response = client.newCall(
            Request.Builder().url(mockWebServer.url("/")).build(),
        ).execute()

        assertEquals(200, response.code)
        assertEquals(1, mockWebServer.requestCount)
    }

    @Test
    fun `does not retry on 400 client error`() {
        val client = createClient()
        mockWebServer.enqueue(MockResponse().setResponseCode(400))

        val response = client.newCall(
            Request.Builder().url(mockWebServer.url("/")).build(),
        ).execute()

        assertEquals(400, response.code)
        assertEquals(1, mockWebServer.requestCount)
    }

    @Test
    fun `does not retry on 403 forbidden`() {
        val client = createClient()
        mockWebServer.enqueue(MockResponse().setResponseCode(403))

        val response = client.newCall(
            Request.Builder().url(mockWebServer.url("/")).build(),
        ).execute()

        assertEquals(403, response.code)
        assertEquals(1, mockWebServer.requestCount)
    }

    @Test
    fun `does not retry on 404 not found`() {
        val client = createClient()
        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        val response = client.newCall(
            Request.Builder().url(mockWebServer.url("/")).build(),
        ).execute()

        assertEquals(404, response.code)
        assertEquals(1, mockWebServer.requestCount)
    }

    @Test
    fun `retries on 500 server error`() {
        val client = createClient(maxRetries = 2)

        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        val response = client.newCall(
            Request.Builder().url(mockWebServer.url("/")).build(),
        ).execute()

        assertEquals(200, response.code)
        assertEquals(3, mockWebServer.requestCount)
    }

    @Test
    fun `retries on 503 service unavailable`() {
        val client = createClient(maxRetries = 1)

        mockWebServer.enqueue(MockResponse().setResponseCode(503))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        val response = client.newCall(
            Request.Builder().url(mockWebServer.url("/")).build(),
        ).execute()

        assertEquals(200, response.code)
        assertEquals(2, mockWebServer.requestCount)
    }

    @Test
    fun `retries on 429 too many requests`() {
        val client = createClient(maxRetries = 1)

        mockWebServer.enqueue(MockResponse().setResponseCode(429))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        val response = client.newCall(
            Request.Builder().url(mockWebServer.url("/")).build(),
        ).execute()

        assertEquals(200, response.code)
        assertEquals(2, mockWebServer.requestCount)
    }

    @Test
    fun `retries on 408 request timeout`() {
        val client = createClient(maxRetries = 1)

        mockWebServer.enqueue(MockResponse().setResponseCode(408))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        val response = client.newCall(
            Request.Builder().url(mockWebServer.url("/")).build(),
        ).execute()

        assertEquals(200, response.code)
        assertEquals(2, mockWebServer.requestCount)
    }

    @Test
    fun `returns last error response after max retries`() {
        val client = createClient(maxRetries = 2)

        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val response = client.newCall(
            Request.Builder().url(mockWebServer.url("/")).build(),
        ).execute()

        assertEquals(500, response.code)
        assertEquals(3, mockWebServer.requestCount)
    }

    @Test(expected = IOException::class)
    fun `throws IOException after max retries on network failure`() {
        val client = createClient(maxRetries = 0)

        // Shut down server to cause IOException
        mockWebServer.shutdown()

        client.newCall(
            Request.Builder().url(mockWebServer.url("/")).build(),
        ).execute()
    }

    private fun createClient(maxRetries: Int = RetryInterceptor.MAX_RETRIES): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(RetryInterceptor(maxRetries))
            .build()
}
