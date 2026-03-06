package com.esiri.esiriplus.core.common.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkMonitorTest {

    @Test
    fun `emits initial online state`() = runTest {
        val monitor = FakeNetworkMonitor(initiallyOnline = true)
        assertTrue(monitor.isOnline.first())
    }

    @Test
    fun `emits initial offline state`() = runTest {
        val monitor = FakeNetworkMonitor(initiallyOnline = false)
        assertFalse(monitor.isOnline.first())
    }

    @Test
    fun `reflects connectivity change`() = runTest {
        val monitor = FakeNetworkMonitor(initiallyOnline = true)
        assertTrue(monitor.isOnline.first())

        monitor.setOnline(false)
        assertFalse(monitor.isOnline.first())

        monitor.setOnline(true)
        assertTrue(monitor.isOnline.first())
    }
}

/** Test double for NetworkMonitor. */
class FakeNetworkMonitor(initiallyOnline: Boolean = true) : NetworkMonitor {
    private val _isOnline = MutableStateFlow(initiallyOnline)
    override val isOnline = _isOnline

    fun setOnline(online: Boolean) {
        _isOnline.value = online
    }
}
