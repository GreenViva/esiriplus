package com.esiri.esiriplus.feature.auth.biometric

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricAuthManagerTest {

    private val preferenceStorage = mockk<BiometricPreferenceStorage>(relaxed = true)

    @Test
    fun `isEnabled delegates to preference storage`() {
        every { preferenceStorage.isBiometricEnabled() } returns true

        val manager = createManager()

        assertTrue(manager.isEnabled())
        verify { preferenceStorage.isBiometricEnabled() }
    }

    @Test
    fun `isEnabled returns false when preference is false`() {
        every { preferenceStorage.isBiometricEnabled() } returns false

        val manager = createManager()

        assertFalse(manager.isEnabled())
    }

    @Test
    fun `setEnabled delegates to preference storage`() {
        val manager = createManager()

        manager.setEnabled(true)

        verify { preferenceStorage.setBiometricEnabled(true) }
    }

    private fun createManager(): BiometricAuthManager {
        // We can't fully test BiometricManager.from(context) in unit tests,
        // so we test the preference delegation
        return BiometricAuthManager(
            context = mockk(relaxed = true),
            preferenceStorage = preferenceStorage,
        )
    }
}
