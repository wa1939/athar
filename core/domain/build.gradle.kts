plugins {
    alias(libs.plugins.athar.jvm.library)
}

dependencies {
    api(project(":core:common"))
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.collections.immutable)
}
