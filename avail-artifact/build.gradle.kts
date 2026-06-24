import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File

plugins {
	kotlin("jvm") version "2.3.10"
	`maven-publish`
	publishing
	signing
	id("org.jetbrains.dokka") version "2.1.0"
}

group = "org.availlang"
version = "2.0.0.alpha22"  // For the next publication.

repositories {
	mavenLocal()
	mavenCentral()
}

val targetJvm = JvmTarget.JVM_25

val kotlinVersion = KotlinVersion.KOTLIN_2_3

java {
	toolchain {
        languageVersion = JavaLanguageVersion.of(targetJvm.target)
	}
}

kotlin {
	jvmToolchain {
		languageVersion.set(JavaLanguageVersion.of(targetJvm.target))
	}
}

dependencies {
	api("org.availlang:avail-json:2.1.2")
	testImplementation(kotlin("test"))
}

val isReleaseVersion =
	!version.toString().endsWith("SNAPSHOT", ignoreCase = true)

///////////////////////////////////////////////////////////////////////////////
//                       Publish Utilities
///////////////////////////////////////////////////////////////////////////////
val ossrhUsername: String get() =
	System.getenv("OSSRH_USER") ?: ""
val ossrhPassword: String get() =
	System.getenv("OSSRH_PASSWORD") ?: ""

private val credentialsWarning =
	"Missing OSSRH credentials.  To publish, you'll need to create an OSSRH " +
			"JIRA account. Then ensure the user name, and password are available " +
			"as the environment variables: 'OSSRH_USER' and 'OSSRH_PASSWORD'"

/**
 * Check that the `publish` task has access to the necessary credentials.
 */
fun checkCredentials ()
{
	if (ossrhUsername.isEmpty() || ossrhPassword.isEmpty())
	{
		System.err.println(credentialsWarning)
	}
}

tasks {
	withType<JavaCompile> {
		options.encoding = "UTF-8"
		sourceCompatibility = targetJvm.target
		targetCompatibility = targetJvm.target
	}

	withType<KotlinCompile> {
		compilerOptions {
			jvmTarget = targetJvm
			freeCompilerArgs = listOf()
			languageVersion = kotlinVersion
		}
	}

	// Access JavaToolchainService at project level for configuration cache compatibility
	val javaToolchains = extensions.getByType(JavaToolchainService::class)
	withType<Test> {
		javaLauncher =
			javaToolchains.launcherFor {
				languageVersion = JavaLanguageVersion.of(targetJvm.target)
			}
		testLogging {
			events = setOf(TestLogEvent.FAILED)
			exceptionFormat = TestExceptionFormat.FULL
			showExceptions = true
			showCauses = true
			showStackTraces = true
		}
	}

	val sourceJar = register<Jar>("sourceJar") {
		description = "Creates sources JAR."
		dependsOn(JavaPlugin.CLASSES_TASK_NAME)
		archiveClassifier.set("sources")
		from(sourceSets["main"].allSource)
	}

	dokka {
		moduleName.set("Avail-artifact")
		dokkaPublications.html {
			suppressInheritedMembers.set(true)
			failOnWarning.set(true)
		}
		dokkaSourceSets.main {
			sourceLink {
				localDirectory.set(file("src/main/kotlin"))
				remoteUrl("https://github.com/AvailLang/Avail/blob/main/src/main/kotlin")
				remoteLineSuffix.set("#L")
			}
		}
		pluginsConfiguration.html {
			//customStyleSheets.from("styles.css")
			//customAssets.from("logo.png")
			footerMessage.set("(c) The Avail Foundation")
		}
	}

	val javadocJar = register<Jar>("javadocJar")
	{
		// Use Dokka 2 task name for generating the html publication
		dependsOn("dokkaGeneratePublicationHtml")
		description = "Creates Javadoc JAR."
		dependsOn(JavaPlugin.CLASSES_TASK_NAME)
		archiveClassifier.set("javadoc")
	}

	publish {
		checkCredentials()
		dependsOn(build)
		dependsOn(sourceJar)
		dependsOn(javadocJar)
	}
}

// Configure jar task outside tasks {} block for configuration cache compatibility
tasks.named<Jar>("jar") {
	manifest.attributes["Implementation-Version"] = version.toString()
	// Capture path as String at configuration time for configuration cache compatibility
	val libsDirPath = layout.buildDirectory.dir("libs").get().asFile.absolutePath
	doFirst {
		// Use Java file operations to avoid project access at execution time
		File(libsDirPath).listFiles()?.forEach { file ->
			if (file.isFile && file.name.endsWith(".jar") && !file.name.endsWith("-all.jar")) {
				file.delete()
			}
		}
	}
}

signing {
	useGpgCmd()
	sign(the<PublishingExtension>().publications)
}

publishing {
	repositories {
		maven {
			url = if (isReleaseVersion)
			{
				// Release version
				uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
			}
			else
			{
				// Snapshot
				uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
			}
			println("Publishing snapshot: $isReleaseVersion")
			println("Publishing URL: $url")
			credentials {
				username = ossrhUsername
				password = ossrhPassword
			}
		}
	}

	publications {

		create<MavenPublication>("avail-artifact") {
			pom {
				groupId = group.toString()
				name.set("Avail Artifact")
				packaging = "jar"
				description.set(
					"This module provides utilities for working with "
					+ "Avail artifacts.")
				url.set("https://www.availlang.org/")
				licenses {
					license {
						name.set("BSD 3-Clause \"New\" or \"Revised\" License")
						url.set("https://github.com/AvailLang/avail-storage/blob/main/LICENSE")
					}
				}
				scm {
					connection.set("TODO")
					developerConnection.set("TODO")
					url.set("TODO")
				}
				developers {
					developer {
						id.set("richATAvail")
						name.set("Richard Arriaga")
					}
				}
			}
			val sourceJar = tasks.getByName("sourceJar") as Jar
			val javadocJar = tasks.getByName("javadocJar") as Jar
			from(components["java"])
			artifact(sourceJar)
			artifact(javadocJar)
		}
	}
}
