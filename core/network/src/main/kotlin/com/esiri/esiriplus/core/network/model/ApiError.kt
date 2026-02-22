package com.esiri.esiriplus.core.network.model

import org.json.JSONObject
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ApiErrorMapper {

    fun fromHttpCode(code: Int, responseBody: String? = null): ApiResult.Error {
        val parsedMessage = tryParseJsonError(responseBody)
        val message = parsedMessage ?: when (code) {
            400 -> "Invalid request. Please check your input."
            401 -> "Authentication required. Please log in again."
            403 -> "You don't have permission to perform this action."
            404 -> "The requested resource was not found."
            408 -> "Request timed out. Please try again."
            409 -> "Conflict. The resource has been modified."
            422 -> "The provided data is invalid."
            429 -> "Too many requests. Please wait and try again."
            in 500..599 -> "Server error. Please try again later."
            else -> "Unexpected error (HTTP $code)."
        }
        return ApiResult.Error(code = code, message = message, details = responseBody)
    }

    fun fromException(exception: Throwable): ApiResult<Nothing> = when (exception) {
        is UnknownHostException -> ApiResult.NetworkError(
            exception = exception,
            message = "No internet connection. Please check your network.",
        )
        is SocketTimeoutException -> ApiResult.NetworkError(
            exception = exception,
            message = "Connection timed out. Please try again.",
        )
        is java.io.IOException -> ApiResult.NetworkError(
            exception = exception,
            message = "Network error. Please check your connection.",
        )
        else -> ApiResult.NetworkError(
            exception = exception,
            message = "An unexpected error occurred.",
        )
    }

    /**
     * Attempts to extract the "error" field from a JSON response body.
     * Edge functions return `{"error":"...", "code":"..."}`.
     */
    private fun tryParseJsonError(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            val json = JSONObject(body)
            val error = if (json.has("error")) json.getString("error") else null
            if (!error.isNullOrBlank()) error else null
        } catch (_: Exception) {
            null
        }
    }
}
