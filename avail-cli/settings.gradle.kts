pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.8.0")
}

rootProject.name = "avail-cli"
include("avail")
project(":avail").projectDir = File(settingsDir, "../avail")
include("avail-artifact")
project(":avail-artifact").projectDir =
    File(settingsDir, "../avail-artifact")
