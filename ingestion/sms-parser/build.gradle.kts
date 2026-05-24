plugins {
    alias(libs.plugins.athar.jvm.library)
}

dependencies {
    api(project(":core:domain"))
    implementation(project(":core:common"))
}
