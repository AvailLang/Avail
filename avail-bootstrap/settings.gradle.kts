pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "avail-bootstrap"
include("avail")
project(":avail").projectDir = File(settingsDir, "../avail")
