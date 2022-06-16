/*
 * build.gradle.kts
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
import avail.build.modules.AvailCoreModule
import avail.build.releaseAvail
import avail.build.scrubReleases
import avail.plugins.gradle.GenerateFileManifestTask

plugins {
	java
	kotlin("jvm")
	id("com.github.johnrengelman.shadow")
	`maven-publish`
	publishing
}

java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(Versions.jvmTarget))
	}
}

kotlin {
	jvmToolchain {
		(this as JavaToolchainSpec).languageVersion.set(
			JavaLanguageVersion.of(Versions.jvmTargetString))
	}
}

dependencies {
	api("org.availlang:avail-json:${Versions.availJsonVersion}")
	api("org.availlang:avail-storage:${Versions.availStorageVersion}")
	AvailCoreModule.addDependencies(this)
}

// Compute the Avail roots. This is needed to properly configure "test".
val availRoots: String by lazy { computeAvailRootsForTest() }
tasks {
	val generated = layout.buildDirectory.dir("generated-resources")
	// Generate the list of all primitives, which a running Avail system uses
	// during setup to reflectively identify the complete catalog of primitives.
	val generatePrimitivesList by creating(GenerateFileManifestTask::class) {
		basePath = layout.projectDirectory.dir("src/main/kotlin").asFile.path
		inputs.files(
			fileTree(basePath) {
				include("avail/interpreter/primitive/**/P_*.kt")
			})
		outputs.dir(generated)
		outputFilePath = "avail/interpreter/All_Primitives.txt"
		fileNameTransformer = {
			// Transform from a relative path to a fully qualified class name.
			replaceFirst(".kt", "").replace("/", ".")
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
		manifest.attributes["Build-Version"] = project.extra.get("buildVersion")
		// The All_Primitives.txt file must be added to the build resources
		// directory before we can build the jar.
	}

	// Copy the JAR into the distribution directory.
	val releaseAvail by creating(Copy::class) {
		releaseAvail(this, shadowJar.get().outputs.files)
	}

	// Update the dependencies of "assemble".
	assemble {
		dependsOn(releaseAvail)
		dependsOn(":avail-stdlib:releaseStandardLibrary")
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

	// This is looked up by name in the publishing the scope but cannot be
	// referred to directly as this is created in a different scope.
	@Suppress("UNUSED_VARIABLE")
	val sourceJar by creating (Jar::class) {
		description = "Creates sources JAR."
		archiveClassifier.set("sources")
		from(sourceSets.getByName("main").allSource)
		duplicatesStrategy = DuplicatesStrategy.INCLUDE
	}
}

publishing {
	repositories {
		maven {
			name = "GitHub"
			url = uri("https://maven.pkg.github.com/AvailLang/Avail")
			credentials {
				username = Publish.githubUsername
				password = Publish.githubPassword
			}
		}
	}

	publications {
		create<MavenPublication>("avail-core") {
			val sourceJar = tasks.getByName("sourceJar") as Jar
			from(components["java"])
			artifact(sourceJar)
		}
	}
}

tasks {
	/**
	 * Remove libraries from the staging directory after successful publication.
	 * Otherwise they'll gradually build up in there.
	 */
	val cleanUpStagingDirectory by creating(Delete::class) {
		description =
			"Remove libraries from the staging directory after successful " +
				"publication. Otherwise they'll gradually build up in there."
		delete(fileTree ("$projectDir/build/libs").matching {
			include("**/*.jar")
		})
	}

	publish {
		dependsOn(cleanUpStagingDirectory)
		doFirst {
			Publish.checkCredentials()
		}
	}
}
