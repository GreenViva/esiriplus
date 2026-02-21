plugins {
    id("esiriplus.android.feature")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.esiri.esiriplus.feature.auth"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(libs.room.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.biometric)
    implementation(libs.security.crypto)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
