plugins {
    id("esiriplus.android.library")
}

android {
    namespace = "com.esiri.esiriplus.core.common"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
