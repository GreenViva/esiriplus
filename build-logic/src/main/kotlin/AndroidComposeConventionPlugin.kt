import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            val catalog = versionCatalog
            val bom = catalog.findLibrary("androidx-compose-bom").get()
            dependencies {
                add("implementation", platform(bom))
                add("implementation", catalog.findLibrary("androidx-compose-ui").get())
                add("implementation", catalog.findLibrary("androidx-compose-ui-graphics").get())
                add("implementation", catalog.findLibrary("androidx-compose-ui-tooling-preview").get())
                add("implementation", catalog.findLibrary("androidx-compose-material3").get())
                add("debugImplementation", catalog.findLibrary("androidx-compose-ui-tooling").get())
                add("debugImplementation", catalog.findLibrary("androidx-compose-ui-test-manifest").get())
            }
        }
    }
}
