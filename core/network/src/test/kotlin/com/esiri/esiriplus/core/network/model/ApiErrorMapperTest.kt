package com.esiri.esiriplus.core.network.model

import com.esiri.esiriplus.core.common.error.ErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ApiErrorMapperTest {

    @Test
    fun `maps 400 to invalid request message`() {
        val result = ApiErrorMapper.fromHttpCode(400)
        assertEquals(400, result.code)
        assertTrue(result.message.contains("Invalid request"))
        assertEquals(ErrorCode.BAD_REQUEST, result.errorCode)
    }

    @Test
    fun `maps 401 to authentication message`() {
        val result = ApiErrorMapper.fromHttpCode(401)
        assertEquals(401, result.code)
        assertTrue(result.message.contains("Authentication"))
        assertEquals(ErrorCode.UNAUTHORIZED, result.errorCode)
    }

    @Test
    fun `maps 403 to permission message`() {
        val result = ApiErrorMapper.fromHttpCode(403)
        assertEquals(403, result.code)
        assertTrue(result.message.contains("permission"))
        assertEquals(ErrorCode.FORBIDDEN, result.errorCode)
    }

    @Test
    fun `maps 404 to not found message`() {
        val result = ApiErrorMapper.fromHttpCode(404)
        assertEquals(404, result.code)
        assertTrue(result.message.contains("not found"))
        assertEquals(ErrorCode.NOT_FOUND, result.errorCode)
    }

    @Test
    fun `maps 408 to timeout message`() {
        val result = ApiErrorMapper.fromHttpCode(408)
        assertEquals(408, result.code)
        assertTrue(result.message.contains("timed out"))
        assertEquals(ErrorCode.REQUEST_TIMEOUT, result.errorCode)
    }

    @Test
    fun `maps 409 to conflict message`() {
        val result = ApiErrorMapper.fromHttpCode(409)
        assertEquals(409, result.code)
        assertTrue(result.message.contains("Conflict"))
        assertEquals(ErrorCode.CONFLICT, result.errorCode)
    }

    @Test
    fun `maps 422 to validation message`() {
        val result = ApiErrorMapper.fromHttpCode(422)
        assertEquals(422, result.code)
        assertTrue(result.message.contains("invalid"))
        assertEquals(ErrorCode.UNPROCESSABLE_ENTITY, result.errorCode)
    }

    @Test
    fun `maps 429 to rate limit message`() {
        val result = ApiErrorMapper.fromHttpCode(429)
        assertEquals(429, result.code)
        assertTrue(result.message.contains("Too many requests"))
        assertEquals(ErrorCode.RATE_LIMITED, result.errorCode)
    }

    @Test
    fun `maps 500 to server error message`() {
        val result = ApiErrorMapper.fromHttpCode(500)
        assertEquals(500, result.code)
        assertTrue(result.message.contains("Server error"))
        assertEquals(ErrorCode.SERVER_ERROR, result.errorCode)
    }

    @Test
    fun `maps 503 to server error message`() {
        val result = ApiErrorMapper.fromHttpCode(503)
        assertEquals(503, result.code)
        assertTrue(result.message.contains("Server error"))
        assertEquals(ErrorCode.SERVER_ERROR, result.errorCode)
    }

    @Test
    fun `maps unknown code to unexpected error`() {
        val result = ApiErrorMapper.fromHttpCode(418)
        assertEquals(418, result.code)
        assertTrue(result.message.contains("Unexpected"))
        assertEquals(ErrorCode.UNKNOWN_HTTP, result.errorCode)
    }

    @Test
    fun `includes response body as details`() {
        val result = ApiErrorMapper.fromHttpCode(400, "error details")
        assertEquals("error details", result.details)
    }

    @Test
    fun `maps UnknownHostException to network error`() {
        val exception = UnknownHostException("host not found")
        val result = ApiErrorMapper.fromException(exception)
        assertTrue(result is ApiResult.NetworkError)
        val networkError = result as ApiResult.NetworkError
        assertTrue(networkError.message.contains("internet"))
        assertEquals(ErrorCode.NO_INTERNET, networkError.errorCode)
    }

    @Test
    fun `maps SocketTimeoutException to timeout error`() {
        val exception = SocketTimeoutException("timeout")
        val result = ApiErrorMapper.fromException(exception)
        assertTrue(result is ApiResult.NetworkError)
        val networkError = result as ApiResult.NetworkError
        assertTrue(networkError.message.contains("timed out"))
        assertEquals(ErrorCode.CONNECTION_TIMEOUT, networkError.errorCode)
    }

    @Test
    fun `maps IOException to network error`() {
        val exception = IOException("connection reset")
        val result = ApiErrorMapper.fromException(exception)
        assertTrue(result is ApiResult.NetworkError)
        val networkError = result as ApiResult.NetworkError
        assertTrue(networkError.message.contains("Network error"))
        assertEquals(ErrorCode.NETWORK_ERROR, networkError.errorCode)
    }

    @Test
    fun `maps generic exception to unexpected error`() {
        val exception = IllegalStateException("something broke")
        val result = ApiErrorMapper.fromException(exception)
        assertTrue(result is ApiResult.NetworkError)
        val networkError = result as ApiResult.NetworkError
        assertEquals("something broke", networkError.message)
        assertEquals(ErrorCode.UNEXPECTED, networkError.errorCode)
    }

    @Test
    fun `maps exception with null message to default`() {
        val exception = RuntimeException()
        val result = ApiErrorMapper.fromException(exception)
        assertTrue(result is ApiResult.NetworkError)
        val networkError = result as ApiResult.NetworkError
        assertTrue(networkError.message.contains("unexpected"))
        assertEquals(ErrorCode.UNEXPECTED, networkError.errorCode)
    }

    @Test
    fun `errorCode fromHttpCode covers all standard codes`() {
        val expected = mapOf(
            400 to ErrorCode.BAD_REQUEST,
            401 to ErrorCode.UNAUTHORIZED,
            403 to ErrorCode.FORBIDDEN,
            404 to ErrorCode.NOT_FOUND,
            408 to ErrorCode.REQUEST_TIMEOUT,
            409 to ErrorCode.CONFLICT,
            422 to ErrorCode.UNPROCESSABLE_ENTITY,
            429 to ErrorCode.RATE_LIMITED,
            500 to ErrorCode.SERVER_ERROR,
            502 to ErrorCode.SERVER_ERROR,
            503 to ErrorCode.SERVER_ERROR,
        )
        expected.forEach { (code, errorCode) ->
            assertEquals("Code $code", errorCode, ErrorCode.fromHttpCode(code))
        }
    }

    @Test
    fun `errorCode fromException covers all exception types`() {
        assertEquals(ErrorCode.NO_INTERNET, ErrorCode.fromException(UnknownHostException()))
        assertEquals(ErrorCode.CONNECTION_TIMEOUT, ErrorCode.fromException(SocketTimeoutException()))
        assertEquals(ErrorCode.NETWORK_ERROR, ErrorCode.fromException(IOException()))
        assertEquals(ErrorCode.UNEXPECTED, ErrorCode.fromException(RuntimeException()))
    }
}
