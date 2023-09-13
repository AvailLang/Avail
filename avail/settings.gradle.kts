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

include("avail-stdlib")
project(":avail-stdlib").projectDir =
    File(settingsDir, "../avail-stdlib")
