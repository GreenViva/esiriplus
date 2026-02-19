package com.esiri.esiriplus.core.network.model

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val code: Int, val message: String, val details: String? = null) : ApiResult<Nothing>()
    data class NetworkError(val exception: Throwable, val message: String) : ApiResult<Nothing>()
    data object Unauthorized : ApiResult<Nothing>()

    fun <R> map(transform: (T) -> R): ApiResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        is NetworkError -> this
        is Unauthorized -> this
    }

    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw ApiException(code, message, details)
        is NetworkError -> throw exception
        is Unauthorized -> throw ApiException(HTTP_UNAUTHORIZED, "Unauthorized")
    }

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this !is Success

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
    }
}

class ApiException(
    val code: Int,
    override val message: String,
    val details: String? = null,
) : Exception(message)
