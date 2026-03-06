package com.esiri.esiriplus.core.common.error

import androidx.annotation.StringRes
import com.esiri.esiriplus.core.common.R

/**
 * Typed error codes that map HTTP status codes and exception types
 * to Android string resource IDs for localized user-facing messages.
 *
 * The network layer produces an [ErrorCode] alongside the raw error.
 * UI layers resolve the string resource via Context to display
 * the correct localized message.
 */
enum class ErrorCode(@StringRes val messageResId: Int) {

    // HTTP errors
    BAD_REQUEST(R.string.error_bad_request),
    UNAUTHORIZED(R.string.error_unauthorized),
    FORBIDDEN(R.string.error_forbidden),
    NOT_FOUND(R.string.error_not_found),
    REQUEST_TIMEOUT(R.string.error_request_timeout),
    CONFLICT(R.string.error_conflict),
    UNPROCESSABLE_ENTITY(R.string.error_unprocessable_entity),
    RATE_LIMITED(R.string.error_rate_limited),
    SERVER_ERROR(R.string.error_server_error),
    UNKNOWN_HTTP(R.string.error_unknown_http),

    // Network / connectivity
    NO_INTERNET(R.string.error_no_internet),
    CONNECTION_TIMEOUT(R.string.error_connection_timeout),
    NETWORK_ERROR(R.string.error_network),
    UNEXPECTED(R.string.error_unexpected),
    ;

    companion object {
        fun fromHttpCode(code: Int): ErrorCode = when (code) {
            400 -> BAD_REQUEST
            401 -> UNAUTHORIZED
            403 -> FORBIDDEN
            404 -> NOT_FOUND
            408 -> REQUEST_TIMEOUT
            409 -> CONFLICT
            422 -> UNPROCESSABLE_ENTITY
            429 -> RATE_LIMITED
            in 500..599 -> SERVER_ERROR
            else -> UNKNOWN_HTTP
        }

        fun fromException(exception: Throwable): ErrorCode = when (exception) {
            is java.net.UnknownHostException -> NO_INTERNET
            is java.net.SocketTimeoutException -> CONNECTION_TIMEOUT
            is java.io.IOException -> NETWORK_ERROR
            else -> UNEXPECTED
        }
    }
}
