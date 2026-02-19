plugins {
    id("esiriplus.android.library")
    id("esiriplus.android.hilt")
}

android {
    namespace = "com.esiri.esiriplus.core.domain"
}

dependencies {
    api(project(":core:common"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
