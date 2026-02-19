package com.esiri.esiriplus.core.network.interceptor

import com.esiri.esiriplus.core.network.TokenManager
import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var tokenManager: TokenManager

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        tokenManager = mockk()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `adds authorization header when token is available`() {
        every { tokenManager.getAccessTokenSync() } returns "test-token"

        val client = createClient()
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        client.newCall(Request.Builder().url(mockWebServer.url("/")).build()).execute()

        val request = mockWebServer.takeRequest()
        assertEquals("Bearer test-token", request.getHeader("Authorization"))
    }

    @Test
    fun `skips authorization header when no token`() {
        every { tokenManager.getAccessTokenSync() } returns null

        val client = createClient()
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        client.newCall(Request.Builder().url(mockWebServer.url("/")).build()).execute()

        val request = mockWebServer.takeRequest()
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun `always adds apikey header`() {
        every { tokenManager.getAccessTokenSync() } returns null

        val client = createClient()
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        client.newCall(Request.Builder().url(mockWebServer.url("/")).build()).execute()

        val request = mockWebServer.takeRequest()
        assertNotNull(request.getHeader("apikey"))
    }

    @Test
    fun `adds both apikey and authorization headers when token present`() {
        every { tokenManager.getAccessTokenSync() } returns "my-token"

        val client = createClient()
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        client.newCall(Request.Builder().url(mockWebServer.url("/")).build()).execute()

        val request = mockWebServer.takeRequest()
        assertNotNull(request.getHeader("apikey"))
        assertEquals("Bearer my-token", request.getHeader("Authorization"))
    }

    private fun createClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenManager))
            .build()
}
