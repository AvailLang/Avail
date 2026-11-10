/*
 * build.gradle.kts
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent.*

buildscript {
	/** The version of the Kotlin Gradle plugin to depend on. */
	val kotlin = "2.4.21"
	extensions.add("kotlin_version", kotlin)
}

plugins {
	kotlin("jvm") version "2.4.21"
	`kotlin-dsl`
	id("org.jetbrains.dokka") version "2.3.0"
	`maven-publish`
	id("com.gradle.plugin-publish") version "1.3.0"
}

group = "org.availlang"
version = "2.0.0.alpha20"


/**
 * The kotlin version set in the project extension
 */
val kotlinVersion get() = project.extensions.getByName("kotlin_version")

/** The JVM target version for Kotlin. */
val jvmTarget = 26

/** The JVM target version for Kotlin. */
val jvmTargetString = jvmTarget.toString()

/** The Kotlin language and API version. */
val kotlinLanguage = KotlinVersion.KOTLIN_2_4

/**
 * The location of the properties file that contains the last published
 * release of the avail libraries.
 */
@Suppress("MemberVisibilityCanBePrivate")
val releaseVersionFile =
	"src/main/resources/releaseVersion.properties"

repositories {
	mavenLocal {
		// Only Avail's own locally published artifacts.  Anything else in
		// ~/.m2 may be a Maven download without Gradle module metadata, which
		// Gradle cannot reliably combine with metadata-published modules.
		content { includeGroupByRegex("org\\.availlang(\\..*)?") }
	}
	mavenCentral()
}

java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(jvmTarget))
	}
}

kotlin {
	jvmToolchain {
		this.languageVersion.set(JavaLanguageVersion.of(jvmTargetString))
	}
}

dependencies {
	implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
	api("org.availlang:avail-artifact:2.0.0.alpha22")
}

tasks {

	withType<JavaCompile> {
		options.encoding = "UTF-8"
		sourceCompatibility = jvmTargetString
		targetCompatibility = jvmTargetString
	}

	withType<KotlinCompile> {
		compilerOptions {
			jvmTarget = JvmTarget.fromTarget(jvmTargetString)
			freeCompilerArgs = listOf("-Xjvm-default=all-compatibility")
			languageVersion = kotlinLanguage
			apiVersion = kotlinLanguage
		}
	}
	withType<Test> {
		val toolChains =
			project.extensions.getByType(JavaToolchainService::class)
		javaLauncher.set(
			toolChains.launcherFor {
				languageVersion.set(JavaLanguageVersion.of(jvmTarget))
			})
		testLogging {
			events = setOf(FAILED)
			exceptionFormat = TestExceptionFormat.FULL
			showExceptions = true
			showCauses = true
			showStackTraces = true
		}
	}

	val sourceJar by creating(Jar::class) {
		group = "build"
		description = "Creates sources JAR."
		dependsOn(JavaPlugin.CLASSES_TASK_NAME)
		archiveClassifier.set("sources")
		manifest.attributes["Implementation-Version"] =
			project.version
		from(sourceSets["main"].allSource)
	}

	val javadocJar by creating(Jar::class)
	{
		dependsOn("dokkaGeneratePublicationHtml")
		description = "Creates Javadoc JAR."
		dependsOn(JavaPlugin.CLASSES_TASK_NAME)
		archiveClassifier.set("javadoc")
		from(layout.buildDirectory.dir("dokka/html"))
	}
	jar {
		doFirst {
			delete(fileTree("$buildDir/libs").matching {
				include("**/*.jar")
			})
		}
		manifest.attributes["Implementation-Version"] =
			project.version
		duplicatesStrategy = DuplicatesStrategy.INCLUDE
		from("src/main/kotlin") {
			include("src/main/resources/*.*")
		}
	}
	withType<GenerateModuleMetadata> {
		dependsOn(sourceJar)
	}
	artifacts {
		add("archives", sourceJar)
		add("archives", javadocJar)
	}
}

gradlePlugin {
	@Suppress("UnstableApiUsage")
	website.set("https://www.availlang.org/")
	@Suppress("UnstableApiUsage")
	vcsUrl.set("https://github.com/AvailLang/gradle-plugin")
	plugins {
		create("avail") {
			id = "org.availlang.avail-plugin"
			displayName = "Avail Gradle Plugin"
			implementationClass = "avail.plugin.AvailPlugin"
			description = "This plugin assists in Avail project setup"
			@Suppress("UnstableApiUsage")
			tags.set(listOf("avail", "language"))
		}
	}
}
