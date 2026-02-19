import androidx.room.gradle.RoomExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("androidx.room")

            extensions.configure<RoomExtension> {
                schemaDirectory("$projectDir/schemas")
            }

            val catalog = versionCatalog
            dependencies {
                add("implementation", catalog.findLibrary("room-runtime").get())
                add("implementation", catalog.findLibrary("room-ktx").get())
                add("ksp", catalog.findLibrary("room-compiler").get())
            }
        }
    }
}
