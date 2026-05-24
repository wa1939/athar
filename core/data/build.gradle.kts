plugins {
    alias(libs.plugins.athar.android.library)
    alias(libs.plugins.athar.android.hilt)
    alias(libs.plugins.athar.android.room)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.athar.core.data"
}

dependencies {
    api(project(":core:domain"))
    implementation(project(":core:common"))

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bundles.coroutines)
    implementation(libs.timber)

    // SQLCipher: encryption-at-rest backed by Android Keystore-wrapped DB key (ADR-003).
    implementation(libs.sqlcipher.android)
}
