package com.esiri.esiriplus.core.network.interceptor

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ClientRateLimiterInterceptorTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder()
            .addInterceptor(ClientRateLimiterInterceptor())
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `requests within limit proceed normally`() {
        repeat(5) { server.enqueue(MockResponse().setBody("ok")) }

        repeat(5) {
            val request = Request.Builder()
                .url(server.url("/rest/v1/doctors"))
                .build()
            client.newCall(request).execute().use { response ->
                assertEquals(200, response.code)
            }
        }

        assertEquals(5, server.requestCount)
    }

    @Test
    fun `different categories are tracked independently`() {
        repeat(2) { server.enqueue(MockResponse().setBody("ok")) }

        val req1 = Request.Builder()
            .url(server.url("/functions/v1/mpesa-stk-push"))
            .build()
        client.newCall(req1).execute().close()

        val req2 = Request.Builder()
            .url(server.url("/functions/v1/list-doctors"))
            .build()
        client.newCall(req2).execute().close()

        assertEquals(2, server.requestCount)
    }
}
