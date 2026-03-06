import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal fun Project.configureAndroidApplication(
    extension: ApplicationExtension,
) {
    extension.apply {
        compileSdk = 36
        defaultConfig {
            minSdk = 24
            targetSdk = 36
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
        @Suppress("UnstableApiUsage")
        testOptions.unitTests.isReturnDefaultValues = true
        @Suppress("UnstableApiUsage")
        testOptions.unitTests.isIncludeAndroidResources = true
    }
    configureKotlinJvmTarget()
}

internal fun Project.configureAndroidLibrary(
    extension: LibraryExtension,
) {
    extension.apply {
        compileSdk = 36
        defaultConfig {
            minSdk = 24
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
        @Suppress("UnstableApiUsage")
        testOptions.unitTests.isReturnDefaultValues = true
        @Suppress("UnstableApiUsage")
        testOptions.unitTests.isIncludeAndroidResources = true
    }
    configureKotlinJvmTarget()
}

private fun Project.configureKotlinJvmTarget() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
}
