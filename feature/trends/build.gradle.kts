plugins {
    alias(libs.plugins.athar.android.feature)
}

android {
    namespace = "com.athar.feature.trends"
}

dependencies {
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)
}
