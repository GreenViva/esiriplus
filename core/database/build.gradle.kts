plugins {
    id("esiriplus.android.library")
    id("esiriplus.android.hilt")
    id("esiriplus.android.room")
}

android {
    namespace = "com.esiri.esiriplus.core.database"
}

dependencies {
    api(project(":core:common"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
