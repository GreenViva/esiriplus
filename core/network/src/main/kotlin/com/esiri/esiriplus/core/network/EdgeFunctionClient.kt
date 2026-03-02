package com.esiri.esiriplus.core.network

import com.esiri.esiriplus.core.network.di.AuthenticatedClient
import com.esiri.esiriplus.core.network.interceptor.JwtUtils
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
        anonymous: Boolean = false,
        patientAuth: Boolean = false,
    ): ApiResult<String> = safeApiCall {
        executeRequest(functionName, body, anonymous, patientAuth)
    }

    suspend inline fun <reified T> invokeAndDecode(
        functionName: String,
        body: JsonObject? = null,
        anonymous: Boolean = false,
        patientAuth: Boolean = false,
    ): ApiResult<T> = safeApiCall {
        val responseBody = executeRequest(functionName, body, anonymous, patientAuth)
        json.decodeFromString<T>(responseBody)
    }

    @PublishedApi
    internal suspend fun executeRequest(
        functionName: String,
        body: JsonObject?,
        anonymous: Boolean = false,
        patientAuth: Boolean = false,
    ): String =
        withContext(Dispatchers.IO) {
            val jsonBody = (body ?: buildJsonObject {}).toString()

            val requestBuilder = Request.Builder()
                .url("$baseUrl/$functionName")
                .post(jsonBody.toRequestBody(jsonMediaType))
                .header("Content-Type", "application/json")
                .header("apikey", BuildConfig.SUPABASE_ANON_KEY)

            when {
                anonymous -> {
                    // Use anon key only — tell AuthInterceptor to skip overriding
                    requestBuilder.header(
                        "Authorization",
                        "Bearer ${BuildConfig.SUPABASE_ANON_KEY}",
                    )
                    requestBuilder.header(HEADER_SKIP_AUTH, "true")
                }
                patientAuth -> {
                    // Bypass Supabase gateway JWT verification with anon key,
                    // but pass the actual patient JWT in a custom header for
                    // function-level auth via validateAuth().
                    val token = tokenManager.getAccessTokenSync()
                    requestBuilder.header(
                        "Authorization",
                        "Bearer ${BuildConfig.SUPABASE_ANON_KEY}",
                    )
                    requestBuilder.header(HEADER_SKIP_AUTH, "true")
                    if (token != null) {
                        requestBuilder.header(HEADER_PATIENT_TOKEN, token)
                    }
                }
                else -> {
                    val token =
                        tokenManager.getAccessTokenSync() ?: BuildConfig.SUPABASE_ANON_KEY
                    if (JwtUtils.isPatientToken(token)) {
                        // Patient JWT detected — bypass Supabase gateway JWT
                        // verification (which rejects custom-signed patient JWTs)
                        // and pass the token via a custom header instead.
                        requestBuilder.header(
                            "Authorization",
                            "Bearer ${BuildConfig.SUPABASE_ANON_KEY}",
                        )
                        requestBuilder.header(HEADER_SKIP_AUTH, "true")
                        requestBuilder.header(HEADER_PATIENT_TOKEN, token)
                    } else {
                        requestBuilder.header("Authorization", "Bearer $token")
                    }
                }
            }

            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    throw EdgeFunctionException(response.code, responseBody)
                }

                responseBody
            }
        }

    companion object {
        /** Marker header — AuthInterceptor will not override Authorization when present. */
        const val HEADER_SKIP_AUTH = "X-Skip-Auth"

        /** Custom header to pass patient JWT for function-level auth (bypasses gateway). */
        const val HEADER_PATIENT_TOKEN = "X-Patient-Token"
    }
}

class EdgeFunctionException(
    val code: Int,
    override val message: String,
) : Exception("Edge function error ($code): $message")
