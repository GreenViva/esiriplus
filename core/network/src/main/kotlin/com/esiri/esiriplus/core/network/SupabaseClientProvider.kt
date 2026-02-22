package com.esiri.esiriplus.core.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.okhttp.OkHttp
import kotlin.time.Duration.Companion.seconds
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseClientProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            requestTimeout = REQUEST_TIMEOUT_SECONDS.seconds

            httpEngine = OkHttp.create {
                preconfigured = okHttpClient
            }

            install(Auth)
            install(Functions)
            install(Postgrest)
            install(Realtime)
            install(Storage)
        }
    }

    private companion object {
        const val REQUEST_TIMEOUT_SECONDS = 60
    }
}
