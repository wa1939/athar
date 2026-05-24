plugins {
    alias(libs.plugins.athar.android.library)
}

android {
    namespace = "com.athar.ml.models"

    androidResources {
        @Suppress("UnstableApiUsage")
        noCompress += "tflite"
    }
}

dependencies {
    // Model assets only — no Kotlin sources here yet. The TFLite runtime + Categorizer impl
    // that binds these assets to the MerchantClassifier interface lands with ticket S-10.
    implementation(libs.tflite)
    // tflite-support uses a separate 0.4.x version line — wire it in when the classifier
    // adapter actually needs it (S-10), pinned to a verified Maven coord at that time.
}
