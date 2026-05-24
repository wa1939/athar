import athar.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Convention for `feature:*` modules.
 *
 * A feature module is a Compose UI screen + its ViewModel + its state/event sealed classes.
 * Features depend on core:domain, core:design-system, core:common — never on a sibling feature.
 * App composes features via Compose Navigation.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("athar.android.library")
            apply("athar.android.compose")
            apply("athar.android.hilt")
        }

        dependencies {
            add("implementation", project(":core:design-system"))
            add("implementation", project(":core:common"))
            add("implementation", project(":core:domain"))

            add("implementation", libs.findLibrary("androidx-lifecycle-runtime-ktx").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
            add("implementation", libs.findLibrary("androidx-navigation-compose").get())
            add("implementation", libs.findLibrary("hilt-navigation-compose").get())
            add("implementation", libs.findLibrary("kotlinx-collections-immutable").get())
            add("implementation", libs.findBundle("coroutines").get())

            add("testImplementation", project(":core:testing"))
        }
    }
}
