plugins {
    alias(libs.plugins.athar.android.library)
    alias(libs.plugins.athar.android.hilt)
}

android {
    namespace = "com.athar.ingestion.notificationlistener"
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.bundles.coroutines)
    implementation(libs.timber)
}
