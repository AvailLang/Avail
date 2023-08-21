pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "avail-server"
include("avail")
project(":avail").projectDir = File(settingsDir, "../avail")
include("avail-artifact")
project(":avail-artifact").projectDir =
    File(settingsDir, "../avail-artifact")
