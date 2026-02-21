package com.esiri.esiriplus.core.network

import com.esiri.esiriplus.core.network.model.ApiResult
import com.esiri.esiriplus.core.network.model.safeApiCall
import io.github.jan.supabase.functions.Functions
import io.ktor.client.call.body
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EdgeFunctionClient @Inject constructor(
    supabaseClientProvider: SupabaseClientProvider,
) {
    @PublishedApi
    internal val functions: Functions =
        supabaseClientProvider.client.pluginManager.getPlugin(Functions)

    suspend fun invoke(
        functionName: String,
        body: JsonObject? = null,
    ): ApiResult<String> = safeApiCall {
        val response = functions(
            function = functionName,
            body = body ?: buildJsonObject {},
            headers = Headers.build {
                append(HttpHeaders.ContentType, "application/json")
            },
        )
        response.body<String>()
    }

    suspend inline fun <reified T> invokeAndDecode(
        functionName: String,
        body: JsonObject? = null,
    ): ApiResult<T> = safeApiCall {
        val response = functions(
            function = functionName,
            body = body ?: buildJsonObject {},
            headers = Headers.build {
                append(HttpHeaders.ContentType, "application/json")
            },
        )
        response.body<T>()
    }
}
