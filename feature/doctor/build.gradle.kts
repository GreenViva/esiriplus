plugins {
    id("esiriplus.android.feature")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.esiri.esiriplus.feature.doctor"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}
