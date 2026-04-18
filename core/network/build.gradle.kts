import java.util.Properties

plugins {
    id("esiriplus.android.library")
    id("esiriplus.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun localOrEnv(key: String): String =
    localProperties.getProperty(key)
        ?: System.getenv(key)
        ?: error("Missing $key in local.properties or environment")

android {
    namespace = "com.esiri.esiriplus.core.network"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "SUPABASE_URL", "\"${localOrEnv("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localOrEnv("SUPABASE_ANON_KEY")}\"")
    }
}

dependencies {
    api(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:database"))

    // Supabase
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.functions)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)

    // Ktor (OkHttp engine for Supabase)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)

    // OkHttp
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Retrofit (api because Response<T> is exposed in SupabaseApi and SafeApiCall)
    api(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)

    // Moshi
    implementation(libs.moshi)
    ksp(libs.moshi.kotlin.codegen)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // Security
    implementation(libs.security.crypto)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Location services (used by LocationResolver)
    implementation(libs.play.services.location)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
}
