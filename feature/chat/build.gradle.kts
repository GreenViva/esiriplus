plugins {
    id("esiriplus.android.feature")
}

android {
    namespace = "com.esiri.esiriplus.feature.chat"
}

dependencies {
    implementation(project(":core:domain"))
}
