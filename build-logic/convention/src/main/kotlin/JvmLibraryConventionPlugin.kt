import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * Convention for pure JVM Kotlin modules — domain, common, parsers, categorizer.
 *
 * Per the modularization skill (rule 10: "prefer the lightest correct module type"),
 * any module that does not need Android resources/manifest must use kotlin("jvm"),
 * not com.android.library. This keeps tests on the JVM, build times fast, and the
 * dependency graph honest.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        pluginManager.apply("athar.jvm.test")

        extensions.configure(JavaPluginExtension::class.java) {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        extensions.configure(KotlinJvmProjectExtension::class.java) {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
                freeCompilerArgs.addAll(
                    "-opt-in=kotlin.RequiresOptIn",
                )
            }
        }
    }
}
