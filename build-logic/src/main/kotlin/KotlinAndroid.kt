import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project

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
    }
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
    }
}
