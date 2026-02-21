plugins {
    id("esiriplus.android.application")
    id("esiriplus.android.compose")
    id("esiriplus.android.hilt")
    alias(libs.plugins.google.services)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.esiri.esiriplus"

    defaultConfig {
        applicationId = "com.esiri.esiriplus"
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // applicationIdSuffix = ".debug" // Enable after adding debug app to Firebase
            resValue("string", "app_name", "eSIRI+ Debug")
        }
        create("staging") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".staging"
            resValue("string", "app_name", "eSIRI+ Staging")
            matchingFallbacks += listOf("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            resValue("string", "app_name", "eSIRI+")
        }
    }
}

dependencies {
    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)

    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    // Serialization (for type-safe navigation routes)
    implementation(libs.kotlinx.serialization.json)

    // Project modules
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:patient"))
    implementation(project(":feature:doctor"))
    implementation(project(":feature:admin"))

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
