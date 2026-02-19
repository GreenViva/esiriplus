import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("esiriplus.android.library")
            pluginManager.apply("esiriplus.android.compose")
            pluginManager.apply("esiriplus.android.hilt")

            val catalog = versionCatalog
            dependencies {
                add("implementation", catalog.findLibrary("androidx-navigation-compose").get())
                add("implementation", catalog.findLibrary("hilt-navigation-compose").get())
                add("implementation", catalog.findLibrary("androidx-lifecycle-viewmodel-compose").get())
                add("implementation", catalog.findLibrary("androidx-lifecycle-runtime-compose").get())
                add("implementation", catalog.findLibrary("kotlinx-coroutines-android").get())
            }
        }
    }
}
