pluginManagement {
	repositories {
		mavenCentral()
		gradlePluginPortal()
	}
}

plugins {
	id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

rootProject.name = "avail-intellij-plugin"
include("avail")
project(":avail").projectDir = File(settingsDir, "../avail")
include("avail-artifact")
project(":avail-artifact").projectDir =
	File(settingsDir, "../avail-artifact")
include("avail-language-server")
project(":avail-language-server").projectDir =
	File(settingsDir, "../avail-language-server")
