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

import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import avail.build.BuildContext

/**
 * The build version string of the form: "yyyyMMdd.HHmmss", representing the
 * time of the build.
 *
 * It simply represents the time the build was completed and the base [version]
 * of said build.
 */
val builtTime by lazy {
	BuildContext.built
}

plugins {
	java
	`java-library`
	kotlin("jvm") version Versions.kotlin
	id("com.github.johnrengelman.shadow") version Versions.shadow apply false
	`maven-publish`
	publishing
	id("org.jetbrains.dokka") version "1.7.10" apply false
	id("org.availlang.avail-plugin") version "2.0.0.alpha19" apply false
}

java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(Versions.jvmTarget))
	}
}

kotlin {
	jvmToolchain {
		languageVersion.set(JavaLanguageVersion.of(Versions.jvmTargetString))
	}
}

allprojects {
	group = "org.availlang"
	version = "2.0.0.alpha22"

	// Define versions in a single place
	extra.apply{
		set("builtTime", builtTime)
	}
	repositories {
		mavenLocal()
		mavenCentral()
	}

	tasks {
		withType<JavaCompile> {
			options.encoding = "UTF-8"
			sourceCompatibility = Versions.jvmTargetString
			targetCompatibility = Versions.jvmTargetString
		}

		withType<KotlinCompile> {
			kotlinOptions {
				jvmTarget = Versions.jvmTargetString
				freeCompilerArgs = listOf("-Xjvm-default=all-compatibility")
				languageVersion = Versions.kotlinLanguage
			}
		}
		withType<Test> {
			val toolChains =
				project.extensions.getByType(JavaToolchainService::class)
			javaLauncher.set(
				toolChains.launcherFor {
					languageVersion.set(JavaLanguageVersion.of(
						Versions.jvmTarget))
				})
			testLogging {
				events = setOf(FAILED)
				exceptionFormat = TestExceptionFormat.FULL
				showExceptions = true
				showCauses = true
				showStackTraces = true
			}
		}
	}
}

val buildFailMessage =
	file("buildSrc/buildFailureMessage.txt")
		.readText()
		.replace("###projectDir###", projectDir.absolutePath)

// Show the release banner upon final completion of the recursive build.
gradle.buildFinished {
	if (("build" in gradle!!.startParameter.taskNames)
		&& failure != null)
	{
		println(buildFailMessage)
	}
}
