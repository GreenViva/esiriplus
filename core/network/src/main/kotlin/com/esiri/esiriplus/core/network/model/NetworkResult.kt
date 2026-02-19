package com.esiri.esiriplus.core.network.model

@Deprecated("Use ApiResult instead", ReplaceWith("ApiResult"))
sealed interface NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>
    data class Error(
        val code: Int? = null,
        val message: String,
        val exception: Throwable? = null,
    ) : NetworkResult<Nothing>
}

@Deprecated("Use safeApiCall from SafeApiCall.kt instead")
suspend fun <T> safeNetworkCall(apiCall: suspend () -> T): NetworkResult<T> =
    try {
        NetworkResult.Success(apiCall())
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        NetworkResult.Error(message = e.message ?: "Unknown error", exception = e)
    }

fun <T> NetworkResult<T>.toApiResult(): ApiResult<T> = when (this) {
    is NetworkResult.Success -> ApiResult.Success(data)
    is NetworkResult.Error -> if (code != null) {
        ApiResult.Error(code = code, message = message, details = null)
    } else {
        ApiResult.NetworkError(
            exception = exception ?: Exception(message),
            message = message,
        )
    }
}
