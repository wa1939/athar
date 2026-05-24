plugins {
    alias(libs.plugins.athar.jvm.library)
}

dependencies {
    api(project(":core:common"))
    api(project(":core:domain"))
    api(libs.bundles.unit.test)
    api(libs.kotlinx.coroutines.test)
}
