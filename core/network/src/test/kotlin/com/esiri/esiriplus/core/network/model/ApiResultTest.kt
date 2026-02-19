package com.esiri.esiriplus.core.network.model

import com.esiri.esiriplus.core.common.result.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiResultTest {

    @Test
    fun `Success isSuccess returns true`() {
        val result: ApiResult<String> = ApiResult.Success("data")
        assertTrue(result.isSuccess)
        assertFalse(result.isError)
    }

    @Test
    fun `Error isError returns true`() {
        val result: ApiResult<String> = ApiResult.Error(400, "Bad Request")
        assertTrue(result.isError)
        assertFalse(result.isSuccess)
    }

    @Test
    fun `NetworkError isError returns true`() {
        val result: ApiResult<String> = ApiResult.NetworkError(Exception("fail"), "Network error")
        assertTrue(result.isError)
        assertFalse(result.isSuccess)
    }

    @Test
    fun `Unauthorized isError returns true`() {
        val result: ApiResult<String> = ApiResult.Unauthorized
        assertTrue(result.isError)
        assertFalse(result.isSuccess)
    }

    @Test
    fun `map transforms Success data`() {
        val result = ApiResult.Success(42)
        val mapped = result.map { it.toString() }
        assertEquals(ApiResult.Success("42"), mapped)
    }

    @Test
    fun `map passes through Error`() {
        val error = ApiResult.Error(500, "Server Error")
        val mapped = error.map { "ignored" }
        assertEquals(error, mapped)
    }

    @Test
    fun `map passes through NetworkError`() {
        val exception = Exception("network")
        val error = ApiResult.NetworkError(exception, "fail")
        val mapped = error.map { "ignored" }
        assertEquals(error, mapped)
    }

    @Test
    fun `map passes through Unauthorized`() {
        val mapped = ApiResult.Unauthorized.map { "ignored" }
        assertEquals(ApiResult.Unauthorized, mapped)
    }

    @Test
    fun `getOrNull returns data for Success`() {
        val result = ApiResult.Success("hello")
        assertEquals("hello", result.getOrNull())
    }

    @Test
    fun `getOrNull returns null for Error`() {
        val result: ApiResult<String> = ApiResult.Error(400, "Bad Request")
        assertNull(result.getOrNull())
    }

    @Test
    fun `getOrNull returns null for NetworkError`() {
        val result: ApiResult<String> = ApiResult.NetworkError(Exception(), "fail")
        assertNull(result.getOrNull())
    }

    @Test
    fun `getOrNull returns null for Unauthorized`() {
        val result: ApiResult<String> = ApiResult.Unauthorized
        assertNull(result.getOrNull())
    }

    @Test
    fun `getOrThrow returns data for Success`() {
        val result = ApiResult.Success("data")
        assertEquals("data", result.getOrThrow())
    }

    @Test(expected = ApiException::class)
    fun `getOrThrow throws ApiException for Error`() {
        val result: ApiResult<String> = ApiResult.Error(400, "Bad Request")
        result.getOrThrow()
    }

    @Test(expected = Exception::class)
    fun `getOrThrow throws original exception for NetworkError`() {
        val result: ApiResult<String> = ApiResult.NetworkError(RuntimeException("fail"), "msg")
        result.getOrThrow()
    }

    @Test(expected = ApiException::class)
    fun `getOrThrow throws ApiException for Unauthorized`() {
        val result: ApiResult<String> = ApiResult.Unauthorized
        result.getOrThrow()
    }

    @Test
    fun `toDomainResult converts Success`() {
        val apiResult = ApiResult.Success("data")
        val domainResult = apiResult.toDomainResult()
        assertTrue(domainResult is Result.Success)
        assertEquals("data", (domainResult as Result.Success).data)
    }

    @Test
    fun `toDomainResult converts Error`() {
        val apiResult: ApiResult<String> = ApiResult.Error(404, "Not Found", "details")
        val domainResult = apiResult.toDomainResult()
        assertTrue(domainResult is Result.Error)
        val error = domainResult as Result.Error
        assertTrue(error.exception is ApiException)
        assertEquals(404, (error.exception as ApiException).code)
    }

    @Test
    fun `toDomainResult converts NetworkError`() {
        val exception = RuntimeException("network fail")
        val apiResult: ApiResult<String> = ApiResult.NetworkError(exception, "Network error")
        val domainResult = apiResult.toDomainResult()
        assertTrue(domainResult is Result.Error)
        assertEquals(exception, (domainResult as Result.Error).exception)
    }

    @Test
    fun `toDomainResult converts Unauthorized`() {
        val apiResult: ApiResult<String> = ApiResult.Unauthorized
        val domainResult = apiResult.toDomainResult()
        assertTrue(domainResult is Result.Error)
        val error = domainResult as Result.Error
        assertTrue(error.exception is ApiException)
        assertEquals(401, (error.exception as ApiException).code)
    }

    @Test
    fun `ApiException has correct properties`() {
        val exception = ApiException(422, "Validation Error", "field: name")
        assertEquals(422, exception.code)
        assertEquals("Validation Error", exception.message)
        assertEquals("field: name", exception.details)
    }
}
