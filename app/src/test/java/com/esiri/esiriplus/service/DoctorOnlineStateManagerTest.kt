package com.esiri.esiriplus.service

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

class DoctorOnlineStateManagerTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var context: Context
    private lateinit var stateManager: DoctorOnlineStateManager

    @Before
    fun setup() {
        editor = mockk(relaxed = true)
        prefs = mockk {
            every { edit() } returns editor
            every { getBoolean("is_online", false) } returns false
            every { getString("doctor_id", null) } returns null
        }
        context = mockk {
            every { getSharedPreferences("doctor_online_state", Context.MODE_PRIVATE) } returns prefs
        }
        stateManager = DoctorOnlineStateManager(context)
    }

    @Test
    fun `isOnline returns false by default`() {
        assertFalse(stateManager.isOnline)
    }

    @Test
    fun `isOnline returns persisted value`() {
        every { prefs.getBoolean("is_online", false) } returns true
        assertTrue(stateManager.isOnline)
    }

    @Test
    fun `setting isOnline persists via SharedPreferences`() {
        every { editor.putBoolean("is_online", true) } returns editor

        stateManager.isOnline = true

        verify { editor.putBoolean("is_online", true) }
        verify { editor.apply() }
    }

    @Test
    fun `doctorId returns null by default`() {
        assertNull(stateManager.doctorId)
    }

    @Test
    fun `doctorId returns persisted value`() {
        every { prefs.getString("doctor_id", null) } returns "doc-123"
        assertEquals("doc-123", stateManager.doctorId)
    }

    @Test
    fun `setting doctorId persists via SharedPreferences`() {
        every { editor.putString("doctor_id", "doc-456") } returns editor

        stateManager.doctorId = "doc-456"

        verify { editor.putString("doctor_id", "doc-456") }
        verify { editor.apply() }
    }

    @Test
    fun `clear removes all preferences`() {
        every { editor.clear() } returns editor

        stateManager.clear()

        verify { editor.clear() }
        verify { editor.apply() }
    }
}
