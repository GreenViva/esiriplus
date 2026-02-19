plugins {
    id("esiriplus.android.library")
    id("esiriplus.android.hilt")
    id("esiriplus.android.room")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.esiri.esiriplus.core.database"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    api(project(":core:common"))

    // SQLCipher encryption
    implementation(libs.sqlcipher.android)

    // Security (Keystore-based key derivation)
    implementation(libs.security.crypto)

    // Serialization (for JSON type converters)
    implementation(libs.kotlinx.serialization.json)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.room.testing)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
