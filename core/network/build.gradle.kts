plugins {
    id("esiriplus.android.library")
    id("esiriplus.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.esiri.esiriplus.core.network"

    defaultConfig {
        buildConfigField("String", "SUPABASE_URL", "\"https://your-project-ref.supabase.co\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"your-anon-key\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "SUPABASE_URL", "\"https://your-project-ref.supabase.co\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"your-anon-key\"")
        }
        create("staging") {
            buildConfigField("String", "SUPABASE_URL", "\"https://your-staging-ref.supabase.co\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"your-staging-anon-key\"")
        }
        release {
            buildConfigField("String", "SUPABASE_URL", "\"https://your-prod-ref.supabase.co\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"your-prod-anon-key\"")
        }
    }
}

dependencies {
    api(project(":core:common"))

    // Supabase
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.functions)
    implementation(libs.supabase.postgrest)

    // Ktor
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // DataStore
    implementation(libs.datastore.preferences)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
