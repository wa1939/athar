import athar.configureAndroidCommon
import athar.libs
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("com.android.library")
            apply("org.jetbrains.kotlin.android")
        }

        extensions.configure<LibraryExtension> {
            configureAndroidCommon(this)
            defaultConfig {
                // Not shipping as standalone libraries — no consumerProguardFiles entries.
                targetSdk = libs.findVersion("targetSdk").get().requiredVersion.toInt()
            }
        }

        dependencies {
            add("testImplementation", libs.findBundle("unit-test").get())
        }
    }
}
