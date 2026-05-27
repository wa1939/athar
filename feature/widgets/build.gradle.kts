plugins {
    alias(libs.plugins.athar.android.feature)
}

android {
    namespace = "com.athar.feature.widgets"
}

dependencies {
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
}
