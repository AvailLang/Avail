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

val versionToPublish by lazy {
	Publish.versionToPublish
}

// Define versions in a single place
extra.apply{
	set("versionToPublish", versionToPublish)
}

plugins {
	id("java")
	id("org.jetbrains.kotlin.jvm") version Versions.kotlin
	id("com.github.johnrengelman.shadow") version Versions.shadow apply false
	id("maven-publish")
	id("publishing")
	id("org.jetbrains.compose") version Versions.compose apply false
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
