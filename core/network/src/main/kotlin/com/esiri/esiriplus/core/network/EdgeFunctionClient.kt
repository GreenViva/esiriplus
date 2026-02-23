package com.esiri.esiriplus.core.network

import com.esiri.esiriplus.core.network.di.AuthenticatedClient
import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.network.model.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EdgeFunctionClient @Inject constructor(
    @AuthenticatedClient private val okHttpClient: OkHttpClient,
    private val tokenManager: TokenManager,
) {
    private val baseUrl = BuildConfig.SUPABASE_URL + "/functions/v1"
    private val jsonMediaType = "application/json".toMediaType()

    @PublishedApi
    internal val json: Json = Json { ignoreUnknownKeys = true }

    suspend fun invoke(
        functionName: String,
        body: JsonObject? = null,
    ): ApiResult<String> = safeApiCall {
        executeRequest(functionName, body)
    }

    suspend inline fun <reified T> invokeAndDecode(
        functionName: String,
        body: JsonObject? = null,
    ): ApiResult<T> = safeApiCall {
        val responseBody = executeRequest(functionName, body)
        json.decodeFromString<T>(responseBody)
    }

    @PublishedApi
    internal suspend fun executeRequest(functionName: String, body: JsonObject?): String =
        withContext(Dispatchers.IO) {
            val jsonBody = (body ?: buildJsonObject {}).toString()

            val requestBuilder = Request.Builder()
                .url("$baseUrl/$functionName")
                .post(jsonBody.toRequestBody(jsonMediaType))
                .header("Content-Type", "application/json")
                .header("apikey", BuildConfig.SUPABASE_ANON_KEY)

            tokenManager.getAccessTokenSync()?.let { token ->
                requestBuilder.header("Authorization", "Bearer $token")
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                throw EdgeFunctionException(response.code, responseBody)
            }

            responseBody
        }
}

class EdgeFunctionException(
    val code: Int,
    override val message: String,
) : Exception("Edge function error ($code): $message")
