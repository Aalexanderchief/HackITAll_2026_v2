pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AgentPilot"
include(":shared", ":androidApp", ":intellijPlugin")
// ":intellijPlugin" excluded — build separately in IntelliJ via ./gradlew :intellijPlugin:buildPlugin
