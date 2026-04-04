plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(17)   // Android/ART bytecode ceiling — keep at 17 regardless of host JDK

    androidTarget()
    sourceSets {
        androidMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.preview)
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
