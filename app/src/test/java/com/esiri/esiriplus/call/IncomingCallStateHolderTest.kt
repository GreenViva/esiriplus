package com.esiri.esiriplus.call

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IncomingCallStateHolderTest {

    private val holder = IncomingCallStateHolder()

    @Test
    fun `initial state is null`() = runTest {
        assertNull(holder.incomingCall.first())
    }

    @Test
    fun `showIncomingCall emits the call`() = runTest {
        val call = IncomingCall(
            consultationId = "c-1",
            roomId = "r-1",
            callType = "VIDEO",
            callerRole = "doctor",
        )

        holder.showIncomingCall(call)

        val result = holder.incomingCall.first()
        assertNotNull(result)
        assertEquals("c-1", result!!.consultationId)
        assertEquals("r-1", result.roomId)
        assertEquals("VIDEO", result.callType)
        assertEquals("doctor", result.callerRole)
    }

    @Test
    fun `dismiss clears the call`() = runTest {
        holder.showIncomingCall(
            IncomingCall("c-1", "r-1", "AUDIO", "patient"),
        )

        holder.dismiss()

        assertNull(holder.incomingCall.first())
    }

    @Test
    fun `new call replaces previous call`() = runTest {
        holder.showIncomingCall(IncomingCall("c-1", "r-1", "VIDEO", "doctor"))
        holder.showIncomingCall(IncomingCall("c-2", "r-2", "AUDIO", "patient"))

        val result = holder.incomingCall.first()
        assertEquals("c-2", result!!.consultationId)
    }
}
