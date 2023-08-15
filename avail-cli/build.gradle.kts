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
import avail.build.AvailSetupContext.distroLib
import avail.build.cleanupJars
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	id("java")
	kotlin("jvm")
	id("com.github.johnrengelman.shadow")
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
	// Avail.
	implementation(project(":avail"))
}
tasks {
	// Produce a fat JAR for the Avail CLI.
	jar {
		doFirst { cleanupJars() }
		manifest.attributes["Main-Class"] = "avail.tools.compiler.Compiler"
		manifest.attributes["Build"] = project.version
		duplicatesStrategy = DuplicatesStrategy.INCLUDE
	}

	// Copy the JAR into the distribution directory.
	val releaseAvailCLI by creating(Copy::class) {
		group = "release"
		from(shadowJar.get().outputs.files)
		into(file("${rootProject.projectDir}/$distroLib"))
		rename(".*", "avail-cli.jar")
		duplicatesStrategy = DuplicatesStrategy.INCLUDE
	}

	// Update the dependencies of "assemble".
	assemble { dependsOn(releaseAvailCLI) }
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
	languageVersion = "1.9"
	apiVersion = "1.9"
}
