package com.esiri.esiriplus.core.network.model

import org.junit.Assert.assertEquals
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
    }

    @Test
    fun `maps 401 to authentication message`() {
        val result = ApiErrorMapper.fromHttpCode(401)
        assertEquals(401, result.code)
        assertTrue(result.message.contains("Authentication"))
    }

    @Test
    fun `maps 403 to permission message`() {
        val result = ApiErrorMapper.fromHttpCode(403)
        assertEquals(403, result.code)
        assertTrue(result.message.contains("permission"))
    }

    @Test
    fun `maps 404 to not found message`() {
        val result = ApiErrorMapper.fromHttpCode(404)
        assertEquals(404, result.code)
        assertTrue(result.message.contains("not found"))
    }

    @Test
    fun `maps 408 to timeout message`() {
        val result = ApiErrorMapper.fromHttpCode(408)
        assertEquals(408, result.code)
        assertTrue(result.message.contains("timed out"))
    }

    @Test
    fun `maps 409 to conflict message`() {
        val result = ApiErrorMapper.fromHttpCode(409)
        assertEquals(409, result.code)
        assertTrue(result.message.contains("Conflict"))
    }

    @Test
    fun `maps 422 to validation message`() {
        val result = ApiErrorMapper.fromHttpCode(422)
        assertEquals(422, result.code)
        assertTrue(result.message.contains("invalid"))
    }

    @Test
    fun `maps 429 to rate limit message`() {
        val result = ApiErrorMapper.fromHttpCode(429)
        assertEquals(429, result.code)
        assertTrue(result.message.contains("Too many requests"))
    }

    @Test
    fun `maps 500 to server error message`() {
        val result = ApiErrorMapper.fromHttpCode(500)
        assertEquals(500, result.code)
        assertTrue(result.message.contains("Server error"))
    }

    @Test
    fun `maps 503 to server error message`() {
        val result = ApiErrorMapper.fromHttpCode(503)
        assertEquals(503, result.code)
        assertTrue(result.message.contains("Server error"))
    }

    @Test
    fun `maps unknown code to unexpected error`() {
        val result = ApiErrorMapper.fromHttpCode(418)
        assertEquals(418, result.code)
        assertTrue(result.message.contains("Unexpected"))
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
    }

    @Test
    fun `maps SocketTimeoutException to timeout error`() {
        val exception = SocketTimeoutException("timeout")
        val result = ApiErrorMapper.fromException(exception)
        assertTrue(result is ApiResult.NetworkError)
        val networkError = result as ApiResult.NetworkError
        assertTrue(networkError.message.contains("timed out"))
    }

    @Test
    fun `maps IOException to network error`() {
        val exception = IOException("connection reset")
        val result = ApiErrorMapper.fromException(exception)
        assertTrue(result is ApiResult.NetworkError)
        val networkError = result as ApiResult.NetworkError
        assertTrue(networkError.message.contains("Network error"))
    }

    @Test
    fun `maps generic exception to unexpected error`() {
        val exception = IllegalStateException("something broke")
        val result = ApiErrorMapper.fromException(exception)
        assertTrue(result is ApiResult.NetworkError)
        val networkError = result as ApiResult.NetworkError
        assertEquals("something broke", networkError.message)
    }

    @Test
    fun `maps exception with null message to default`() {
        val exception = RuntimeException()
        val result = ApiErrorMapper.fromException(exception)
        assertTrue(result is ApiResult.NetworkError)
        val networkError = result as ApiResult.NetworkError
        assertTrue(networkError.message.contains("unexpected"))
    }
}
