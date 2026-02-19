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

class TokenRefreshAuthenticatorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var tokenManager: TokenManager
    private lateinit var tokenRefresher: TokenRefresher

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        tokenManager = mockk(relaxed = true)
        tokenRefresher = mockk()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `refreshes token on 401 response`() {
        every { tokenManager.getAccessTokenSync() } returns "old-token" andThen "new-token"
        every { tokenManager.getRefreshTokenSync() } returns "refresh-token"
        every { tokenRefresher.refreshToken("refresh-token") } returns true

        val client = createClient()

        // First response: 401, second: 200 (after refresh)
        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        val response = client.newCall(
            Request.Builder()
                .url(mockWebServer.url("/"))
                .header("Authorization", "Bearer old-token")
                .build(),
        ).execute()

        assertEquals(200, response.code)
        verify { tokenRefresher.refreshToken("refresh-token") }
    }

    @Test
    fun `clears tokens when refresh fails`() {
        every { tokenManager.getAccessTokenSync() } returns "old-token"
        every { tokenManager.getRefreshTokenSync() } returns "refresh-token"
        every { tokenRefresher.refreshToken("refresh-token") } returns false

        val client = createClient()

        mockWebServer.enqueue(MockResponse().setResponseCode(401))

        val response = client.newCall(
            Request.Builder()
                .url(mockWebServer.url("/"))
                .header("Authorization", "Bearer old-token")
                .build(),
        ).execute()

        assertEquals(401, response.code)
        verify { tokenManager.clearTokens() }
    }

    @Test
    fun `clears tokens when no refresh token available`() {
        every { tokenManager.getAccessTokenSync() } returns "old-token"
        every { tokenManager.getRefreshTokenSync() } returns null

        val client = createClient()
        mockWebServer.enqueue(MockResponse().setResponseCode(401))

        val response = client.newCall(
            Request.Builder()
                .url(mockWebServer.url("/"))
                .header("Authorization", "Bearer old-token")
                .build(),
        ).execute()

        assertEquals(401, response.code)
        verify { tokenManager.clearTokens() }
    }

    @Test
    fun `skips refresh if another thread already refreshed`() {
        // Simulate another thread having already refreshed: current token differs from request token
        every { tokenManager.getAccessTokenSync() } returns "already-refreshed-token"
        every { tokenManager.getRefreshTokenSync() } returns "refresh-token"

        val client = createClient()

        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        val response = client.newCall(
            Request.Builder()
                .url(mockWebServer.url("/"))
                .header("Authorization", "Bearer old-token")
                .build(),
        ).execute()

        assertEquals(200, response.code)
        verify(exactly = 0) { tokenRefresher.refreshToken(any()) }
    }

    private fun createClient(): OkHttpClient =
        OkHttpClient.Builder()
            .authenticator(TokenRefreshAuthenticator(tokenManager, tokenRefresher))
            .build()
}
