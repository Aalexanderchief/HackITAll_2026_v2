plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.library)
}

kotlin {
    jvmToolchain(17)

    androidTarget()
    jvm("desktop")

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            // Compose
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)

            // Navigation + ViewModel (JetBrains multiplatform forks)
            implementation(libs.navigation.compose)
            implementation(libs.lifecycle.viewmodel.compose)

            // Networking
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.client.content.neg)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.network)

            // Async + serialization
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)

            // Date/time
            implementation(libs.datetime)

            // MCP
            implementation(libs.mcp.kotlin.sdk)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.coroutines.android)

            // QR code scanning
            implementation(libs.camerax.camera2)
            implementation(libs.camerax.lifecycle)
            implementation(libs.camerax.view)
            implementation(libs.mlkit.barcode)
            implementation(libs.activity.compose)
            implementation(libs.lifecycle.runtime.compose)
        }

        val desktopMain by getting {
            dependencies {
                implementation(libs.ktor.client.cio)
                implementation(compose.desktop.currentOs)
                implementation(libs.coroutines.swing)
            }
        }
    }
}

android {
    namespace = "com.agentpilot.shared"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
}
