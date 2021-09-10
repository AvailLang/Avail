/*
 * build.gradle.kts
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
import com.avail.build.AvailSetupContext.distroLib

plugins {
	java
	id("maven-publish")
	publishing
}

repositories {
	mavenCentral()
}

dependencies {
	// Avail.
	implementation (rootProject)
}
tasks {
	processResources {
		exclude("**")
	}
	// Produce a JAR with the source of every module in the standard Avail library.
	val standardLibraryName =
		"$buildDir/avail-standard-library.jar"
	jar {
		description = "The Avail standard library"
		archiveFileName.set(standardLibraryName)
		exclude("stacks/**")
		exclude("com/")
		// Exclude all the java files that Gradle puts in by default.
		dependsOn(processResources)

		from("../distro/src/avail") {
			include("**/*.*")
		}
		val versionToPublish = Publish.versionToPublish
		duplicatesStrategy = DuplicatesStrategy.FAIL
		manifest.attributes["Implementation-Title"] = "Avail standard library"
		manifest.attributes["Implementation-Version"] = versionToPublish
		// Even though the jar only includes .avail source files, we need the
		// content to be available at runtime, so we use "" for the archive
		// classifier instead of "sources".
		archiveClassifier.set("")
	}

	// Copy the library into the distribution directory.
	val releaseStandardLibrary by creating(Copy::class) {
		group = "release"
		from(standardLibraryName)
		into("${rootProject.projectDir}/$distroLib")
		duplicatesStrategy = DuplicatesStrategy.INCLUDE

		dependsOn(jar)
	}

	// Update the dependencies of "assemble".
	assemble { dependsOn(releaseStandardLibrary) }
}
rootProject.tasks.assemble { dependsOn(tasks.getByName("releaseStandardLibrary")) }

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
		create<MavenPublication>("workbench") {
			val jar = tasks.jar
			artifact(jar)
		}
	}
}
