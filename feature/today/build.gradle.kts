import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.athar.android.feature)
}

android {
    namespace = "com.athar.feature.today"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
