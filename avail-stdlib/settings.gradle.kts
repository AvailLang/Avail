pluginManagement {
    repositories {
        mavenCentral()
        mavenLocal {
            // Only Avail's own locally published plugin.  Anything else in
            // ~/.m2 may be a Maven download without Gradle module metadata,
            // which Gradle cannot reliably combine with metadata-published
            // modules.
            content { includeGroupByRegex("org\\.availlang(\\..*)?") }
        }
        gradlePluginPortal()
    }
}

rootProject.name = "avail-stdlib"
