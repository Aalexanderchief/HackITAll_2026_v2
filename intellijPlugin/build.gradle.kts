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

    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.neg)
    implementation(libs.ktor.serialization.json)
    implementation(libs.coroutines.core)
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
