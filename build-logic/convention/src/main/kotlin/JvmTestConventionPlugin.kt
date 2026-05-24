import athar.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

class JvmTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            testLogging {
                events("failed", "skipped")
                showStackTraces = true
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }

        dependencies {
            add("testImplementation", libs.findBundle("unit-test").get())
        }
    }
}
