plugins {
	id("java")
	kotlin("jvm") version "1.9.0"
	id("org.jetbrains.intellij") version "1.15.0"
}

group = "org.availlang"
version = "1.0.0-SNAPSHOT"

repositories {
	mavenCentral()
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
	version.set("LATEST-EAP-SNAPSHOT")
	type.set("IU") // Target IDE Platform

	plugins.set(listOf(/* Plugin Dependencies */))
}

dependencies {
	implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
	implementation(project(":avail-language-server"))
}

tasks {
	// Set the JVM compatibility versions
	withType<JavaCompile> {
		sourceCompatibility = "17"
		targetCompatibility = "17"
	}
	withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
		kotlinOptions.jvmTarget = "17"
	}

	patchPluginXml {
		sinceBuild.set("222")
		untilBuild.set("233.*")
	}

	signPlugin {
		certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
		privateKey.set(System.getenv("PRIVATE_KEY"))
		password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
	}

	publishPlugin {
		token.set(System.getenv("PUBLISH_TOKEN"))
	}
}
