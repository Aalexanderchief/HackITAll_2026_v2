plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.intellij.platform)
}

kotlin {
    jvmToolchain(21)  // IntelliJ Platform requires JVM 21
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3.1")
        bundledPlugin("com.intellij.java")
        instrumentationTools()
    }

    // Ktor server 3.0.3 — compiled with Kotlin 2.0.x, compatible with IntelliJ 2024.3.1's
    // bundled Kotlin 2.1.x stdlib. Do NOT use 3.4.x here: it requires Kotlin 2.3.x stdlib
    // which IntelliJ doesn't bundle, causing NoSuchMethodError on Duration internal APIs.
    val ktorServer = "3.0.3"
    implementation("io.ktor:ktor-server-netty:$ktorServer") {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
    }
    implementation("io.ktor:ktor-server-websockets:$ktorServer") {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
    }
    implementation("io.ktor:ktor-server-content-negotiation:$ktorServer") {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
    }
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorServer") {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
    }
    compileOnly(libs.coroutines.core)
    implementation(libs.serialization.json)
    implementation(libs.zxing.core)
    implementation(libs.zxing.javase)
}

intellijPlatform {
    pluginConfiguration {
        id = "com.agentpilot.plugin"
        name = "AgentPilot"
        version = "0.1.0"
    }
    buildSearchableOptions = false
}
