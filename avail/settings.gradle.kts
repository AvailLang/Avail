pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "avail"
include("avail-artifact")
project(":avail-artifact").projectDir =
    File(settingsDir, "../avail-artifact")
