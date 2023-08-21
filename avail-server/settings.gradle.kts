pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "avail-server"
include("avail")
project(":avail").projectDir = File(settingsDir, "../avail")
