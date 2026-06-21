pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("1.0.0")
}

rootProject.name = "avail"

include("avail-artifact")
project(":avail-artifact").projectDir =
    File(settingsDir, "../avail-artifact")

include("avail-stdlib")
project(":avail-stdlib").projectDir =
    File(settingsDir, "../avail-stdlib")
