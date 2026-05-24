plugins {
    alias(libs.plugins.athar.android.library)
    alias(libs.plugins.athar.android.compose)
}

android {
    namespace = "com.athar.core.designsystem"
}

dependencies {
    api(project(":core:common"))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.kotlinx.collections.immutable)
}
