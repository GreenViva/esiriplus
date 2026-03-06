package com.esiri.esiriplus.core.common.error

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ErrorCodeTest {

    @Test
    fun `fromHttpCode maps standard HTTP codes`() {
        assertEquals(ErrorCode.BAD_REQUEST, ErrorCode.fromHttpCode(400))
        assertEquals(ErrorCode.UNAUTHORIZED, ErrorCode.fromHttpCode(401))
        assertEquals(ErrorCode.FORBIDDEN, ErrorCode.fromHttpCode(403))
        assertEquals(ErrorCode.NOT_FOUND, ErrorCode.fromHttpCode(404))
        assertEquals(ErrorCode.REQUEST_TIMEOUT, ErrorCode.fromHttpCode(408))
        assertEquals(ErrorCode.CONFLICT, ErrorCode.fromHttpCode(409))
        assertEquals(ErrorCode.UNPROCESSABLE_ENTITY, ErrorCode.fromHttpCode(422))
        assertEquals(ErrorCode.RATE_LIMITED, ErrorCode.fromHttpCode(429))
    }

    @Test
    fun `fromHttpCode maps 5xx range to SERVER_ERROR`() {
        assertEquals(ErrorCode.SERVER_ERROR, ErrorCode.fromHttpCode(500))
        assertEquals(ErrorCode.SERVER_ERROR, ErrorCode.fromHttpCode(502))
        assertEquals(ErrorCode.SERVER_ERROR, ErrorCode.fromHttpCode(503))
        assertEquals(ErrorCode.SERVER_ERROR, ErrorCode.fromHttpCode(599))
    }

    @Test
    fun `fromHttpCode maps unknown to UNKNOWN_HTTP`() {
        assertEquals(ErrorCode.UNKNOWN_HTTP, ErrorCode.fromHttpCode(418))
        assertEquals(ErrorCode.UNKNOWN_HTTP, ErrorCode.fromHttpCode(302))
    }

    @Test
    fun `fromException maps network exceptions`() {
        assertEquals(ErrorCode.NO_INTERNET, ErrorCode.fromException(UnknownHostException()))
        assertEquals(ErrorCode.CONNECTION_TIMEOUT, ErrorCode.fromException(SocketTimeoutException()))
        assertEquals(ErrorCode.NETWORK_ERROR, ErrorCode.fromException(IOException()))
    }

    @Test
    fun `fromException maps unknown to UNEXPECTED`() {
        assertEquals(ErrorCode.UNEXPECTED, ErrorCode.fromException(RuntimeException()))
        assertEquals(ErrorCode.UNEXPECTED, ErrorCode.fromException(IllegalStateException()))
    }

}
