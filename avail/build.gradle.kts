/*
 * build.gradle.kts
 * Copyright © 1993-2022, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
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

import avail.build.computeAvailRootsForTest
import avail.build.modules.AvailModule
import avail.build.scrubReleases
import avail.tasks.GenerateFileManifestTask
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toUpperCaseAsciiOnly

plugins {
	java
	kotlin("jvm")
	`maven-publish`
	publishing
	signing
	id("org.jetbrains.dokka")
	id("com.github.johnrengelman.shadow")
}

val isReleaseVersion =
	!version.toString().toUpperCaseAsciiOnly().endsWith("SNAPSHOT")

dependencies {
	api("org.availlang:avail-json:1.1.1")
	api("org.availlang:avail-storage:1.1.1")
	implementation("org.availlang:avail-artifact:2.0.0.alpha04")
	AvailModule.addDependencies(this)
}

// Compute the Avail roots. This is needed to properly configure "test".
val availRoots: String by lazy { computeAvailRootsForTest() }
tasks {
	val generated = layout.buildDirectory.dir("generated-resources")

	// Generate the list of all primitives, which a running Avail system uses
	// during setup to reflectively identify the complete catalog of primitives.
	val generatePrimitivesList by creating(GenerateFileManifestTask::class) {
		basePath = layout.projectDirectory.dir("src/main/kotlin").asFile.path
		val prefix = "$basePath${File.separator}"
		println("========basePath: $basePath")
		inputs.files(
			fileTree(basePath) {
				include("avail/interpreter/primitive/**/P_*.kt")
			})
		outputs.dir(generated)
		outputFilePath = "avail/interpreter/All_Primitives.txt"
		fileNameTransformer = {
			// Transform from a relative path to a fully qualified class name.
			replaceFirst(".kt", "")
				.replace(prefix, "")
				.replace(File.separator, ".")
		}
	}

	sourceSets.main {
		resources {
			srcDir(generatePrimitivesList)
		}
	}

	test {
		useJUnitPlatform()
		println("Java version for tests: $javaVersion")
		minHeapSize = "4g"
		maxHeapSize = "6g"
		enableAssertions = true
		systemProperty("availRoots", availRoots)
	}

	jar {
		manifest.attributes["Implementation-Version"] = project.version
		manifest.attributes["Build-Time"] = project.extra.get("builtTime")
		manifest.attributes["SplashScreen-Image"] =
			"resources/resources/workbench/AvailWBSplash.png"
		// The All_Primitives.txt file must be added to the build resources
		// directory before we can build the jar.
	}

	/**
	 * Remove released libraries.
	 *
	 * See [scrubReleases] in `Build.kt`.
	 */
	val scrubReleases by creating(Delete::class) {
		description =
			"Removes released libraries. See `scrubReleases` in `Build.kt`."
		scrubReleases(this)
	}

	// Update the dependencies of "clean".
	clean { dependsOn(scrubReleases) }

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
		dependsOn(build)
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
				username = PublishingUtility.ossrhUsername
				password = PublishingUtility.ossrhPassword
			}
		}
	}

	publications {

		create<MavenPublication>("avail") {
			pom {
				groupId = project.group.toString()
				name.set("Avail Programming Language")
				packaging = "jar"
				description.set("This module provides the entire Avail programming language.")
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
						id.set("markATAvail")
						name.set("Mark van Gulik")
					}
					developer {
						id.set("toddATAvail")
						name.set("Todd Smith")
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
