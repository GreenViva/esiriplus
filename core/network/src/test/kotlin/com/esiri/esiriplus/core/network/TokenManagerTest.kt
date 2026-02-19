package com.esiri.esiriplus.core.network

import com.esiri.esiriplus.core.network.security.EncryptedTokenStorage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TokenManagerTest {

    private lateinit var encryptedTokenStorage: EncryptedTokenStorage
    private lateinit var tokenManager: TokenManager

    @Before
    fun setUp() {
        encryptedTokenStorage = mockk(relaxed = true)
        every { encryptedTokenStorage.getAccessToken() } returns null
        every { encryptedTokenStorage.getRefreshToken() } returns null
        tokenManager = TokenManager(encryptedTokenStorage)
    }

    @Test
    fun `getAccessTokenSync delegates to storage`() {
        every { encryptedTokenStorage.getAccessToken() } returns "test-access"
        assertEquals("test-access", tokenManager.getAccessTokenSync())
    }

    @Test
    fun `getRefreshTokenSync delegates to storage`() {
        every { encryptedTokenStorage.getRefreshToken() } returns "test-refresh"
        assertEquals("test-refresh", tokenManager.getRefreshTokenSync())
    }

    @Test
    fun `getAccessTokenSync returns null when no token`() {
        every { encryptedTokenStorage.getAccessToken() } returns null
        assertNull(tokenManager.getAccessTokenSync())
    }

    @Test
    fun `saveTokens persists to storage and updates StateFlow`() {
        tokenManager.saveTokens("access", "refresh", 1000L)

        verify { encryptedTokenStorage.saveTokens("access", "refresh", 1000L) }
        assertEquals("access", tokenManager.accessToken.value)
        assertEquals("refresh", tokenManager.refreshToken.value)
    }

    @Test
    fun `clearTokens clears storage and resets StateFlow`() {
        tokenManager.saveTokens("access", "refresh", 1000L)
        tokenManager.clearTokens()

        verify { encryptedTokenStorage.clearTokens() }
        assertNull(tokenManager.accessToken.value)
        assertNull(tokenManager.refreshToken.value)
    }

    @Test
    fun `isTokenExpiringSoon delegates to storage`() {
        every { encryptedTokenStorage.isTokenExpiringSoon(5) } returns true
        assertTrue(tokenManager.isTokenExpiringSoon(5))
    }

    @Test
    fun `isTokenExpiringSoon returns false when token not expiring`() {
        every { encryptedTokenStorage.isTokenExpiringSoon(5) } returns false
        assertFalse(tokenManager.isTokenExpiringSoon(5))
    }

    @Test
    fun `initial StateFlow values match storage`() {
        every { encryptedTokenStorage.getAccessToken() } returns "initial-access"
        every { encryptedTokenStorage.getRefreshToken() } returns "initial-refresh"

        val manager = TokenManager(encryptedTokenStorage)
        assertEquals("initial-access", manager.accessToken.value)
        assertEquals("initial-refresh", manager.refreshToken.value)
    }
}
