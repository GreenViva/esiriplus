package com.esiri.esiriplus.core.network

import com.esiri.esiriplus.core.network.model.NetworkResult
import com.esiri.esiriplus.core.network.model.safeApiCall
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EdgeFunctionClient @Inject constructor(
    private val supabaseClientProvider: SupabaseClientProvider,
    private val tokenManager: TokenManager,
) {
    val client: SupabaseClient
        get() = supabaseClientProvider.client

    @Suppress("UnusedParameter", "UnusedPrivateProperty")
    suspend fun invoke(
        functionName: String,
        body: JsonObject? = null,
    ): NetworkResult<String> = safeApiCall {
        @Suppress("UNUSED_VARIABLE")
        val token = tokenManager.accessToken.firstOrNull()
        // TODO: Wire up Supabase Functions.invoke() with auth token injection
        // val functions = client.functions
        // functions.invoke(functionName, body, headers = ...)
        throw NotImplementedError(
            "Edge function invocation not yet wired — implement with supabase-kt Functions plugin",
        )
    }
}
