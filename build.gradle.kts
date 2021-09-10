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

import com.avail.build.Utility.formattedNow
import com.avail.build.cleanupAllJars
import com.avail.build.cleanupJars
import com.avail.build.computeAvailRootsForTest
import com.avail.build.generateBuildTime
import com.avail.build.modules.RootProject
import com.avail.build.releaseAvail
import com.avail.build.scrubReleases
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val versionToPublish by lazy {
	"1.6.0.$formattedNow"
}

// Define versions in a single place
extra.apply{
	set("versionToPublish", versionToPublish)
}

plugins {
	id("java")
	id("org.jetbrains.kotlin.jvm") version Versions.kotlin
	id("com.github.johnrengelman.shadow") version Versions.shadow
	id("maven-publish")
	id("publishing")
	id("org.jetbrains.compose") version "1.0.0-alpha3" apply false
}
allprojects {
	group = "org.availlang"
	version = versionToPublish

	tasks {
		withType<JavaCompile>().configureEach {
			options.encoding = "UTF-8"
			options.compilerArgs = listOf("-Dfile.encoding=UTF-8")
			sourceCompatibility = Versions.jvmTarget
			targetCompatibility = Versions.jvmTarget
		}
		withType<KotlinCompile>().configureEach {
			kotlinOptions {
				jvmTarget = Versions.jvmTarget
				freeCompilerArgs = listOf("-Xjvm-default=compatibility")
				languageVersion = Versions.kotlinLanguage
			}
		}
	}
}

repositories {
	mavenCentral()
}

dependencies {
	testApi(project(":avail-test-utility"))
	RootProject.addDependencies(this)
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

	// Generate the list of all primitives, which a running Avail system uses during
	// setup to reflectively identify the complete catalog of primitives.
	val generatePrimitivesList by creating {
		// Un-Windows the path, if necessary.
		val pathAvailBuildMain =
			"$buildDir/classes/kotlin/main".replace("\\\\", "/")
		val allPrimitives = fileTree("$pathAvailBuildMain/com/avail/interpreter")
		val pathToPrimitivesList =
			file("$pathAvailBuildMain/com/avail/interpreter/All_Primitives.txt")
		allPrimitives.include("**/P_*.class")
		allPrimitives.exclude("**/*$*.class")
		allPrimitives.builtBy("compileKotlin")
		inputs.files + allPrimitives
		outputs.files + pathToPrimitivesList

		doLast {
			val allPrimitiveNames = allPrimitives.map {
				it.absolutePath
					.replace("\\\\", "/")
					.replaceFirst("$pathAvailBuildMain/", "")
					.replaceFirst(".class", "")
					.replace("/", ".")
			}.sorted().joinToString("\n")

			pathToPrimitivesList.writeText(allPrimitiveNames)
		}
	}

	// Update the dependencies of "classes".
	classes {
		dependsOn(getByName("generatePrimitivesList"))
		doLast {
			generateBuildTime(this)
		}
	}

	jar { doFirst { cleanupJars() } }
	shadowJar { doFirst { cleanupAllJars() } }

	// Copy the JAR into the distribution directory.
	val releaseAvail by creating(Copy::class) {
		releaseAvail(this, shadowJar.get().outputs.files)
	}

	// Update the dependencies of "assemble".
	assemble { dependsOn(releaseAvail) }

	// Remove released libraries.
	val scrubReleases by creating(Delete::class) {
		scrubReleases(this)
	}

	// Update the dependencies of "clean".
	clean { dependsOn(scrubReleases) }

	val sourceJar by creating (Jar::class) {
		description = "Creates sources JAR."
		archiveClassifier.set("sources")
		from(sourceSets.getByName("main").allSource)
		duplicatesStrategy = DuplicatesStrategy.INCLUDE
	}
}

val buildFailMessage =
	file("buildSrc/buildFailureMessage.txt")
		.readText()
		.replace("###projectDir###", projectDir.absolutePath)

// Show the release banner upon final completion of the recursive build.
gradle.buildFinished {
	if (("build" in gradle!!.startParameter.taskNames) // TODO revisit me
		&& failure == null)
	{
		println(buildFailMessage)
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
		create<MavenPublication>("avail") {
			val sourceJar = tasks.getByName("sourceJar") as Jar
			from(components["java"])
			artifact(sourceJar)
		}
	}
}

// Remove libraries from the staging directory after successful publication.
// Otherwise they'll gradually build up in there.
tasks {

	val cleanUpStagingDirectory by creating(Delete::class) {
		delete(fileTree ("$projectDir/build/libs").matching {
			include("**/*.jar")
		})
		// publishAvailPublicationToGitHubRepository is generated by the
		// maven-publish plugin.
		dependsOn(getByName("publishAvailPublicationToGitHubRepository"))
	}

	publish {
		dependsOn(cleanUpStagingDirectory)
		doFirst {
			Publish.checkCredentials()
		}
	}
}
