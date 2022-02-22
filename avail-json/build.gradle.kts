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
import avail.build.cleanupJars
import avail.build.generateBuildTime
import avail.build.modules.AvailJsonModule
import avail.build.releaseAvail

plugins {
	java
	kotlin("jvm")
	`maven-publish`
	publishing
}

group = "org.availlang"
version = "1.0.3"
description = "A flexible JSON building and reading utility"

repositories {
	mavenCentral()
}

dependencies {
	implementation(kotlin("stdlib"))
	AvailJsonModule.addDependencies(this)
}

tasks {
	val sourceJar by creating(Jar::class) {
		description = "Creates sources JAR."
		dependsOn(JavaPlugin.CLASSES_TASK_NAME)
		archiveClassifier.set("sources")
		from(sourceSets["main"].allSource)
	}

	// Update the dependencies of "classes".
	classes {
		doLast {
			generateBuildTime(this)
		}
	}

	jar {
		manifest.attributes["Implementation-Version"] =
			project.version
		doFirst { cleanupJars() }
	}

	// Copy the JAR into the distribution directory.
	val releaseAvail by creating(Copy::class) {
		releaseAvail(this, jar.get().outputs.files)
	}

	// Update the dependencies of "assemble".
	assemble { dependsOn(releaseAvail) }

	artifacts { add("archives", sourceJar) }
	publish { Publish.checkCredentials() }
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
		create<MavenPublication>("avail-json") {
			val sourceJar = tasks.getByName("sourceJar") as Jar
			from(components["java"])
			artifact(sourceJar)
		}
	}
}
