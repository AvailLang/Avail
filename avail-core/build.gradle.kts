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

plugins {
	java
	kotlin("jvm")
	id("com.github.johnrengelman.shadow")
	`maven-publish`
	publishing
}

repositories {
	mavenCentral()
}

dependencies {
	api(project(":avail-json"))
	api(project(":avail-storage"))
	testApi(project(":avail-test-utility"))
	AvailCoreModule.addDependencies(this)
}

// Compute the Avail roots. This is needed to properly configure "test".
val availRoots: String by lazy { computeAvailRootsForTest() }
tasks {
	test {
		useJUnitPlatform()
		minHeapSize = "4g"
		maxHeapSize = "6g"
		enableAssertions = true
		systemProperty("availRoots", availRoots)
	}

	// Generate the list of all primitives, which a running Avail system uses
	// during setup to reflectively identify the complete catalog of primitives.
	val generatePrimitivesList by creating {
		val sourcesPath = layout.projectDirectory.dir("src/main/kotlin")
		val primitivesSourceTree = fileTree(sourcesPath)
		{
			include("avail/interpreter/primitive/**/P_*.kt")
		}
		inputs.files(primitivesSourceTree)
		outputs.file(layout.buildDirectory.file("All_Primitives.txt"))

		doLast {
			val baseSourcePath = primitivesSourceTree.dir.path + "/"
			val allPrimitiveNames = primitivesSourceTree.map {
				it.absolutePath
					.replace("\\\\", "/")
					.replaceFirst(baseSourcePath, "")
					.replaceFirst(".kt", "")
					.replace("/", ".")
			}.sorted().joinToString("\n")
			val outFile = outputs.files.singleFile
			if (!outFile.exists() || outFile.readText() != allPrimitiveNames)
			{
				outFile.parentFile.mkdirs()
				outFile.writeText(allPrimitiveNames)
			}
		}
	}

	jar {
		manifest.attributes["Implementation-Version"] = project.version
		// Include the output from generatePrimitivesList (All_Primitives.txt)
		// in the jar, placing it in /avail/interpreter within the jar.
		from(generatePrimitivesList) {
			into("avail/interpreter/")
		}
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
		// Copy updated version to `avail-plugin`. This only provides
		// `avail-plugin` with the new version, it does not publish
		// `avail-plugin`.
		dependsOn(rootProject.tasks.getByName("updatePluginPublishVersion"))
		doFirst {
			Publish.checkCredentials()
		}
	}

//	publishToMavenLocal {
//		// Copy updated version to `avail-plugin`. This only provides
//		// `avail-plugin` with the new version, it does not publish
//		// `avail-plugin`.
//		dependsOn(rootProject.tasks.getByName("updatePluginPublishVersion"))
//	}
}
