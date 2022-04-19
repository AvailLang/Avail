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

import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent.*

val versionToPublish by lazy {
	Publish.versionToPublish
}

// Define versions in a single place
extra.apply{
	set("versionToPublish", versionToPublish)
}

plugins {
	java
	`java-library`
	kotlin("jvm") version Versions.kotlin
	id("com.github.johnrengelman.shadow") version Versions.shadow apply false
	`maven-publish`
	publishing
	id("org.jetbrains.compose") version Versions.compose apply false
	id("org.jetbrains.intellij") version Versions.intellij apply false
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

allprojects {
	group = "avail"
	version = versionToPublish

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

repositories {
	mavenCentral()
}

tasks {
	// Associated with the publishing of `avail-core`. Publishing `avail-core`
	// will cause this task to trigger as a dependency.
	register("updatePluginPublishVersion", Exec::class) {
		group = "publish"
		description = "This pushes the most recently created " +
			"`versionToPublish` build version to `avail-plugin` so that it " +
			"can be updated in that project."
		workingDir = project.file("${project.projectDir}/avail-plugin")
		commandLine = listOf(
			"./gradlew", "updateVersion", "-PversionStripe=$versionToPublish")
		println("Sending `avail-plugin` (${project.projectDir}/avail-plugin) " +
			"gradle command, `updateVersion` with version, $versionToPublish")
	}

	publishToMavenLocal {
		// Copy updated version to `avail-plugin`. This only provides
		// `avail-plugin` with the new version, it does not publish
		// `avail-plugin`.
		finalizedBy(rootProject.tasks.getByName("updatePluginPublishVersion"))
	}
	publish {
		// Copy updated version to `avail-plugin`. This only provides
		// `avail-plugin` with the new version, it does not publish
		// `avail-plugin`.
		finalizedBy(rootProject.tasks.getByName("updatePluginPublishVersion"))
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
