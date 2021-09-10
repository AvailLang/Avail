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
import com.avail.build.modules.AvailBootstrapModule

plugins {
	java
	kotlin("jvm")
	id("com.github.johnrengelman.shadow")
}

repositories {
	mavenCentral()
}

dependencies {
	implementation (rootProject)
	AvailBootstrapModule.addDependencies(this)
}

tasks {
	// Don't build any JAR files, since these are bootstrap tools only.
	jar { enabled = false }

	// Copy the generated bootstrap property files into the build directory, so
	// that the executable tools can find them as resources.
	val relocateGeneratedPropertyFiles by creating(Copy::class) {
		AvailBootstrapModule.relocateGeneratedPropertyFiles(project, this)
	}

	// Update the dependencies of "classes".
	classes { dependsOn(relocateGeneratedPropertyFiles) }

	// Bootstrap Primitive_<lang>.properties for the current locale.
	val generatePrimitiveNames by creating(JavaExec::class) {
		group = "bootstrap"
		mainClass.set("com.avail.tools.bootstrap.PrimitiveNamesGenerator")
		classpath = sourceSets.main.get().runtimeClasspath
		dependsOn(classes)
	}

	// Bootstrap ErrorCodeNames_<lang>.properties for the current locale.
	val generateErrorCodeNames by creating(JavaExec::class) {
		group = "bootstrap"
		mainClass.set("com.avail.tools.bootstrap.ErrorCodeNamesGenerator")
		classpath = sourceSets.main.get().runtimeClasspath
		dependsOn(classes)
	}

	// Bootstrap ErrorCodeNames_<lang>.properties for the current locale.
	val generateSpecialObjectNames by creating(JavaExec::class) {
		group = "bootstrap"
		mainClass.set("com.avail.tools.bootstrap.SpecialObjectNamesGenerator")
		classpath = sourceSets.main.get().runtimeClasspath
		dependsOn(classes)
	}

	// Generate all bootstrap .properties files for the current locale.
	val generateAllNames by creating {
		group = "bootstrap"
		dependsOn(getByName("generatePrimitiveNames"))
		dependsOn(getByName("generateErrorCodeNames"))
		dependsOn(getByName("generateSpecialObjectNames"))
	}

	// Generate the new bootstrap Avail modules for the current locale.
	val internalGenerateBootstrap by creating(JavaExec::class) {
		group = "internal"
		mainClass.set("com.avail.tools.bootstrap.BootstrapGenerator")
		classpath = sourceSets.main.get().runtimeClasspath
		dependsOn(classes)
	}

	// Generate the new bootstrap Avail modules for the current locale and copy
	// them to the appropriate location for distribution.
	val generateBootstrap by creating(Copy::class) {
		AvailBootstrapModule.generateBootStrap(project, this)
	}
}
