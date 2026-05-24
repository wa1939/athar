plugins {
    alias(libs.plugins.athar.android.library)
    alias(libs.plugins.athar.android.hilt)
}

android {
    namespace = "com.athar.ingestion.smslistener"
}

dependencies {
    api(project(":core:domain"))
    implementation(project(":ingestion:sms-parser"))
    implementation(libs.bundles.coroutines)
    implementation(libs.timber)
}
