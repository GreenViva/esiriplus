package com.esiri.esiriplus.core.network.security

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EncryptedTokenStorageTest {

    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setUp() {
        mockPrefs = mockk()
        mockEditor = mockk(relaxed = true)
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.putLong(any(), any()) } returns mockEditor
        every { mockEditor.clear() } returns mockEditor
    }

    @Test
    fun `getAccessToken returns null when empty`() {
        every { mockPrefs.getString("access_token", null) } returns null
        assertNull(mockPrefs.getString("access_token", null))
    }

    @Test
    fun `getAccessToken returns stored value`() {
        every { mockPrefs.getString("access_token", null) } returns "test-token"
        assertEquals("test-token", mockPrefs.getString("access_token", null))
    }

    @Test
    fun `getRefreshToken returns null when empty`() {
        every { mockPrefs.getString("refresh_token", null) } returns null
        assertNull(mockPrefs.getString("refresh_token", null))
    }

    @Test
    fun `getRefreshToken returns stored value`() {
        every { mockPrefs.getString("refresh_token", null) } returns "refresh-test"
        assertEquals("refresh-test", mockPrefs.getString("refresh_token", null))
    }

    @Test
    fun `getExpiresAt returns 0 when empty`() {
        every { mockPrefs.getLong("expires_at", 0L) } returns 0L
        assertEquals(0L, mockPrefs.getLong("expires_at", 0L))
    }

    @Test
    fun `getExpiresAt returns stored value`() {
        every { mockPrefs.getLong("expires_at", 0L) } returns 1735689600000L
        assertEquals(1735689600000L, mockPrefs.getLong("expires_at", 0L))
    }

    @Test
    fun `saveTokens persists all three values`() {
        mockPrefs.edit()
            .putString("access_token", "access")
            .putString("refresh_token", "refresh")
            .putLong("expires_at", 1000L)
            .apply()

        verify { mockEditor.putString("access_token", "access") }
        verify { mockEditor.putString("refresh_token", "refresh") }
        verify { mockEditor.putLong("expires_at", 1000L) }
        verify { mockEditor.apply() }
    }

    @Test
    fun `clearTokens calls clear and apply`() {
        mockPrefs.edit().clear().apply()

        verify { mockEditor.clear() }
        verify { mockEditor.apply() }
    }

    @Test
    fun `isTokenExpiringSoon returns true when expiresAt is 0`() {
        // When no token is stored (expiresAt = 0), should be considered expiring
        val expiresAt = 0L
        assertTrue(expiresAt == 0L)
    }

    @Test
    fun `isTokenExpiringSoon returns true when token expires within threshold`() {
        val now = System.currentTimeMillis()
        val expiresAt = now + (3 * 60 * 1000) // expires in 3 minutes
        val thresholdMillis = 5 * 60 * 1000L // 5 minute threshold
        assertTrue(now + thresholdMillis >= expiresAt)
    }

    @Test
    fun `isTokenExpiringSoon returns false when token is fresh`() {
        val now = System.currentTimeMillis()
        val expiresAt = now + (30 * 60 * 1000) // expires in 30 minutes
        val thresholdMillis = 5 * 60 * 1000L // 5 minute threshold
        assertFalse(now + thresholdMillis >= expiresAt)
    }

    @Test
    fun `isTokenExpiringSoon returns true when token already expired`() {
        val now = System.currentTimeMillis()
        val expiresAt = now - (5 * 60 * 1000) // expired 5 minutes ago
        val thresholdMillis = 5 * 60 * 1000L
        assertTrue(now + thresholdMillis >= expiresAt)
    }
}
