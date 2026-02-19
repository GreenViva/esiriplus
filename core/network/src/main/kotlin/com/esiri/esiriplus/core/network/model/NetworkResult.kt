package com.esiri.esiriplus.core.network.model

sealed interface NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>
    data class Error(
        val code: Int? = null,
        val message: String,
        val exception: Throwable? = null,
    ) : NetworkResult<Nothing>
}

suspend fun <T> safeApiCall(apiCall: suspend () -> T): NetworkResult<T> =
    try {
        NetworkResult.Success(apiCall())
    } catch (e: Exception) {
        NetworkResult.Error(message = e.message ?: "Unknown error", exception = e)
    }
