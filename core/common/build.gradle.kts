plugins {
    id("esiriplus.android.library")
    id("esiriplus.android.hilt")
}

android {
    namespace = "com.esiri.esiriplus.core.common"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.appcompat)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
