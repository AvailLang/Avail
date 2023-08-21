pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "avail-cli"
include("avail")
project(":avail").projectDir = File(settingsDir, "../avail")
