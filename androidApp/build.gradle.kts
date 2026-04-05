plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(17)

    androidTarget()
    sourceSets {
        androidMain.dependencies {
            implementation(project(":shared"))

            // Compose
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.uiTooling)
            implementation(compose.preview)

            // Lifecycle
            implementation(libs.lifecycle.runtime.compose)

            // Coroutines
            implementation(libs.coroutines.android)
        }
    }
}

android {
    namespace = "com.agentpilot.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.agentpilot.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { compose = true }
}
