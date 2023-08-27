/*
 * build.gradle.kts
 * Copyright © 1993-2022, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

import avail.plugin.AvailExtension
import org.availlang.artifact.AvailArtifactType.LIBRARY
import org.availlang.artifact.environment.location.ProjectHome
import org.availlang.artifact.environment.location.Scheme.FILE
import org.availlang.artifact.environment.project.AvailProject.Companion.CONFIG_FILE_NAME
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toUpperCaseAsciiOnly

plugins {
	id("java")
	`maven-publish`
	publishing
	signing
	id("org.jetbrains.dokka") version "1.8.20"
	id("org.availlang.avail-plugin") version "2.0.0.alpha20"
}

repositories {
	mavenLocal()
	mavenCentral()
}

group = "org.availlang"
version = "2.0.0.alpha23-1.6.1.alpha14"

avail {
	projectDescription = "The Avail Standard Library"
	rootsDirectory = ProjectHome(
		"../avail/distro/src",
		FILE,
		project.rootDir.absolutePath,
		rootNameInJar = "avail")
	projectRoot(
		"avail",
		entryPoints = mutableListOf("!_"),
		description = "The Avail Standard Library primary module root" )
	artifact {
		artifactType = LIBRARY
		implementationTitle = "Avail Standard Library"
		projectFileLocation =
			ProjectHome(
				"./../$CONFIG_FILE_NAME",
				FILE,
				project.projectDir.absolutePath,
				null)
	}
}

val availExtension get() = project.extensions
	.findByType(AvailExtension::class.java)!!

/**
 * Copy the generated library jar to distro/lib to enable testing of workbench
 * with jar file library.
 */
fun copyArtifactToDistroLib ()
{
	val availExtension = project.extensions
		.findByType(AvailExtension::class.java)!!
	File(availExtension.targetOutputJar).apply {
		copyTo(File("../avail/distro/lib/${name}"), true)
	}
}

tasks {
	jar {
		doLast {
			// This re-creates the JAR, deleting the present JAR first. This
			// is done due to the publishing sanity check introduced in Gradle
			// 6.3 that does an internal check to confirm that the jar was
			// effectively constructed by the standard JAR task in some
			// predetermined internal order. This problem manifests with this
			// error message:
			// `Artifact <TARGET JAR>.jar wasn't produced by this build.`
			// At the time of writing this was the only solution identified so
			// far that overcame the issue.
			availExtension.createArtifact()
		}
	}

	createProjectFile {
		outputLocation = ProjectHome(
			"",
			FILE,
			rootDir.absolutePath,
			rootNameInJar = null)
	}

	// Copy the library into the distribution directory. This is used by the
	// workbench configuration that uses the standard library jar to start the
	// workbench with the Avail Standard Library.
	val copyToDistroLib by creating(DefaultTask::class) {
		dependsOn(availArtifactJar)
		doLast { copyArtifactToDistroLib() }
	}


	val sourceJar by creating(Jar::class) {
		description = "Creates sources JAR."
		dependsOn(JavaPlugin.CLASSES_TASK_NAME)
		archiveClassifier.set("sources")
		from(sourceSets["main"].allSource)
	}

	val dokkaHtml by getting(org.jetbrains.dokka.gradle.DokkaTask::class)

	val javadocJar by creating(Jar::class)
	{
		dependsOn(dokkaHtml)
		description = "Creates Javadoc JAR."
		dependsOn(JavaPlugin.CLASSES_TASK_NAME)
		archiveClassifier.set("javadoc")
		from(dokkaHtml.outputDirectory)
	}

	artifacts {
		add("archives", sourceJar)
		add("archives", javadocJar)
	}
	publish {
		PublishingUtility.checkCredentials()
		doLast { copyArtifactToDistroLib() }
	}
	publishToMavenLocal {
		doLast { copyArtifactToDistroLib() }
	}
}

val isReleaseVersion =
	!version.toString().toUpperCaseAsciiOnly().endsWith("SNAPSHOT")

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
				username = PublishingUtility.ossrhUsername
				password = PublishingUtility.ossrhPassword
			}
		}
	}

	publications {

		create<MavenPublication>("avail-stdlib") {
			pom {
				groupId = project.group.toString()
				name.set("Avail Standard Library")
				packaging = "jar"
				description.set("This module provides the entire Avail standard library.")
				url.set("https://www.availlang.org/")
				licenses {
					license {
						name.set("BSD 3-Clause \"New\" or \"Revised\" License")
						url.set("https://github.com/AvailLang/avail-storage/blob/main/LICENSE")
					}
				}
				scm {
					connection.set("scm:git:git@github.com:AvailLang/Avail.git")
					developerConnection.set("scm:git:git@github.com:AvailLang/Avail.git")
					url.set("https://github.com/AvailLang/Avail")
				}
				developers {
					developer {
						id.set("toddATAvail")
						name.set("Todd Smith")
					}
					developer {
						id.set("markATAvail")
						name.set("Mark van Gulik")
					}
					developer {
						id.set("richATAvail")
						name.set("Richard Arriaga")
					}
					developer {
						id.set("leslieATAvail")
						name.set("Leslie Schultz")
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

/**
 * A utility object for publishing.
 *
 * @author Richard Arriaga
 */
object PublishingUtility
{
	/**
	 * The Sonatype username used for publishing.
	 */
	val ossrhUsername: String get() =
		System.getenv("OSSRH_USER") ?: ""

	/**
	 * The Sonatype password used for publishing.
	 */
	val ossrhPassword: String get() =
		System.getenv("OSSRH_PASSWORD") ?: ""

	/**
	 * The warning that indicates the system does not have environment variables
	 * for publishing credentials.
	 */
	private const val credentialsWarning =
		"Missing OSSRH credentials.  To publish, you'll need to create an OSSRH " +
			"JIRA account. Then ensure the user name, and password are available " +
			"as the environment variables: 'OSSRH_USER' and 'OSSRH_PASSWORD'"

	/**
	 * Check that the publisher has access to the necessary credentials.
	 */
	fun checkCredentials ()
	{
		if (ossrhUsername.isEmpty() || ossrhPassword.isEmpty())
		{
			System.err.println(credentialsWarning)
		}
	}
}
