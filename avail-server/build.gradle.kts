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
import avail.build.cleanupAllJars
import avail.build.cleanupJars
import avail.build.modules.AvailServerModule

plugins {
	java
	kotlin("jvm")
	id("com.github.johnrengelman.shadow")
}

repositories {
	mavenCentral()
}

dependencies {
	// Avail.
	implementation(project(":avail-json"))
	implementation(project(":avail-storage"))
	implementation(project(":avail-core"))
	testImplementation(project(":avail-test-utility"))
	AvailServerModule.addDependencies(this)
}

tasks {
	shadowJar {
		doFirst { cleanupAllJars() }
	}

	// Produce a fat JAR for the Avail server.
	jar {
		doFirst { cleanupJars() }
		manifest.attributes["Main-Class"] = "avail.server.AvailServer"
		duplicatesStrategy = DuplicatesStrategy.INCLUDE
	}

	test {
		useJUnitPlatform()
		minHeapSize = "4g"
		maxHeapSize = "6g"
		enableAssertions = true
	}

	// Copy the JAR into the distribution directory.
	val releaseAvailServer by creating(Copy::class) {
		group = "release"
		from(shadowJar.get().outputs.files)
		into(file("${rootProject.projectDir}/distro/lib"))
		rename(".*", "avail-server.jar")
		duplicatesStrategy = DuplicatesStrategy.INCLUDE
	}

	// Update the dependencies of "assemble".
	assemble { dependsOn(releaseAvailServer) }
}
