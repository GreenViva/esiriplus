plugins {
    id("esiriplus.android.library")
    id("esiriplus.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.esiri.esiriplus.core.network"

    defaultConfig {
        buildConfigField("String", "SUPABASE_URL", "\"https://nzzvphhqbcscoetzfzkd.supabase.co\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im56enZwaGhxYmNzY29ldHpmemtkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzEzMjI3OTYsImV4cCI6MjA4Njg5ODc5Nn0.31g9pCxm5AThy9xckctfWMHG7wrcmykIPepA_PMHDkQ\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "SUPABASE_URL", "\"https://nzzvphhqbcscoetzfzkd.supabase.co\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im56enZwaGhxYmNzY29ldHpmemtkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzEzMjI3OTYsImV4cCI6MjA4Njg5ODc5Nn0.31g9pCxm5AThy9xckctfWMHG7wrcmykIPepA_PMHDkQ\"")
        }
        create("staging") {
            buildConfigField("String", "SUPABASE_URL", "\"https://nzzvphhqbcscoetzfzkd.supabase.co\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im56enZwaGhxYmNzY29ldHpmemtkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzEzMjI3OTYsImV4cCI6MjA4Njg5ODc5Nn0.31g9pCxm5AThy9xckctfWMHG7wrcmykIPepA_PMHDkQ\"")
        }
        release {
            buildConfigField("String", "SUPABASE_URL", "\"https://nzzvphhqbcscoetzfzkd.supabase.co\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im56enZwaGhxYmNzY29ldHpmemtkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzEzMjI3OTYsImV4cCI6MjA4Njg5ODc5Nn0.31g9pCxm5AThy9xckctfWMHG7wrcmykIPepA_PMHDkQ\"")
        }
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

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
}
