plugins {
    alias(libs.plugins.athar.android.feature)
}

android {
    namespace = "com.athar.feature.widgets"
}

dependencies {
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler.androidx)
}
