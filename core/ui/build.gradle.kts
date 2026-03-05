plugins {
    id("esiriplus.android.library")
    id("esiriplus.android.compose")
}

android {
    namespace = "com.esiri.esiriplus.core.ui"
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.androidx.appcompat)
}
