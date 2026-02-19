package com.esiri.esiriplus.core.network.interceptor

import com.esiri.esiriplus.core.network.TokenManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ProactiveTokenRefreshInterceptorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var tokenManager: TokenManager
    private lateinit var tokenRefresher: TokenRefresher

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        tokenManager = mockk(relaxed = true)
        tokenRefresher = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `triggers refresh when token is expiring soon`() {
        every { tokenManager.getAccessTokenSync() } returns "valid-token"
        every { tokenManager.isTokenExpiringSoon() } returns true
        every { tokenManager.getRefreshTokenSync() } returns "refresh-token"
        every { tokenRefresher.refreshToken("refresh-token") } returns true

        val client = createClient()
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        client.newCall(Request.Builder().url(mockWebServer.url("/")).build()).execute()

        verify { tokenRefresher.refreshToken("refresh-token") }
    }

    @Test
    fun `does not refresh when token is not expiring soon`() {
        every { tokenManager.getAccessTokenSync() } returns "valid-token"
        every { tokenManager.isTokenExpiringSoon() } returns false

        val client = createClient()
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        client.newCall(Request.Builder().url(mockWebServer.url("/")).build()).execute()

        verify(exactly = 0) { tokenRefresher.refreshToken(any()) }
    }

    @Test
    fun `does not refresh when no access token`() {
        every { tokenManager.getAccessTokenSync() } returns null

        val client = createClient()
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        client.newCall(Request.Builder().url(mockWebServer.url("/")).build()).execute()

        verify(exactly = 0) { tokenRefresher.refreshToken(any()) }
    }

    @Test
    fun `does not refresh when no refresh token available`() {
        every { tokenManager.getAccessTokenSync() } returns "valid-token"
        every { tokenManager.isTokenExpiringSoon() } returns true
        every { tokenManager.getRefreshTokenSync() } returns null

        val client = createClient()
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        client.newCall(Request.Builder().url(mockWebServer.url("/")).build()).execute()

        verify(exactly = 0) { tokenRefresher.refreshToken(any()) }
    }

    @Test
    fun `still proceeds with request even if refresh fails`() {
        every { tokenManager.getAccessTokenSync() } returns "valid-token"
        every { tokenManager.isTokenExpiringSoon() } returns true
        every { tokenManager.getRefreshTokenSync() } returns "refresh-token"
        every { tokenRefresher.refreshToken("refresh-token") } returns false

        val client = createClient()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        val response = client.newCall(
            Request.Builder().url(mockWebServer.url("/")).build(),
        ).execute()

        assertEquals(200, response.code)
    }

    private fun createClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(ProactiveTokenRefreshInterceptor(tokenManager, tokenRefresher))
            .build()
}
