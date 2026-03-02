plugins {
    id("esiriplus.android.feature")
}

android {
    namespace = "com.esiri.esiriplus.feature.chat"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:network"))
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.compose.material.icons.extended)
    api(libs.videosdk.rtc)
}
