pluginManagement {
	repositories {
		mavenLocal {
			url = file("local-plugin-repository/").toURI()
		}
		mavenLocal()
		// Adds the gradle plugin portal back to the plugin repositories as
		// this is removed (overridden) by adding any repository here.
		gradlePluginPortal()
	}
}
rootProject.name = "avail-intellij-plugin"

