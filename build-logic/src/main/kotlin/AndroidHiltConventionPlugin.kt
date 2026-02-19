import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("com.google.dagger.hilt.android")

            val catalog = versionCatalog
            dependencies {
                add("implementation", catalog.findLibrary("hilt-android").get())
                add("ksp", catalog.findLibrary("hilt-compiler").get())
            }
        }
    }
}
