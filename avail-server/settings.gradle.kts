plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("1.0.0")
}

rootProject.name = "avail-server"
include("avail")
project(":avail").projectDir = File(settingsDir, "../avail")
include("avail-artifact")
project(":avail-artifact").projectDir =
    File(settingsDir, "../avail-artifact")
