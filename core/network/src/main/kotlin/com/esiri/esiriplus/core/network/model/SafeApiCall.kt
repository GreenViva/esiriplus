package com.esiri.esiriplus.core.network.model

import com.esiri.esiriplus.core.common.result.Result
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

private const val HTTP_UNAUTHORIZED = 401

suspend fun <T> safeApiCall(block: suspend () -> T): ApiResult<T> =
    try {
        ApiResult.Success(block())
    } catch (e: HttpException) {
        val code = e.code()
        if (code == HTTP_UNAUTHORIZED) {
            ApiResult.Unauthorized
        } else {
            ApiErrorMapper.fromHttpCode(code, e.response()?.errorBody()?.string())
        }
    } catch (e: IOException) {
        ApiErrorMapper.fromException(e)
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        ApiErrorMapper.fromException(e)
    }

fun <T> Response<T>.toApiResult(): ApiResult<T> {
    val body = body()
    return if (isSuccessful && body != null) {
        ApiResult.Success(body)
    } else if (code() == HTTP_UNAUTHORIZED) {
        ApiResult.Unauthorized
    } else {
        ApiErrorMapper.fromHttpCode(code(), errorBody()?.string())
    }
}

fun <T> ApiResult<T>.toDomainResult(): Result<T> = when (this) {
    is ApiResult.Success -> Result.Success(data)
    is ApiResult.Error -> Result.Error(ApiException(code, message, details))
    is ApiResult.NetworkError -> Result.Error(exception, message)
    is ApiResult.Unauthorized -> Result.Error(ApiException(HTTP_UNAUTHORIZED, "Unauthorized"))
}
