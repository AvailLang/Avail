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

import org.jetbrains.dokka.gradle.DokkaTask
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent.*

buildscript {
	extensions.add("kotlin_version", Versions.kotlin)
	dependencies {
		classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.kotlin}")
	}
}

plugins {
	kotlin("jvm") version Versions.kotlin
	`java-gradle-plugin`
	`kotlin-dsl`
	id("org.jetbrains.dokka") version "1.6.21"
	id("java-gradle-plugin")
	`maven-publish`
	id("com.gradle.plugin-publish") version "0.18.0"
}

gradlePlugin {
	plugins {
		create("avail-plugin") {
			id = "avail.avail-plugin"
			implementationClass = "avail.plugin.AvailPlugin"
		}
	}
}

group = "avail"
version = "1.6.1.rc1-SNAPSHOT"// Versions.getReleaseVersion()

repositories {
	mavenLocal()
	mavenCentral()
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
	implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.kotlin}")
	implementation("org.availlang:avail-artifact:2.0.0-SNAPSHOT")
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

	val sourceJar by creating(Jar::class) {
		group = "build"
		description = "Creates sources JAR."
		dependsOn(JavaPlugin.CLASSES_TASK_NAME)
		archiveClassifier.set("sources")
		manifest.attributes["Implementation-Version"] =
			project.version
		from(sourceSets["main"].allSource)
	}

	val dokkaHtml by getting(DokkaTask::class)

	val javadocJar by creating(Jar::class)
	{
		dependsOn(dokkaHtml)
		description = "Creates Javadoc JAR."
		dependsOn(JavaPlugin.CLASSES_TASK_NAME)
		archiveClassifier.set("javadoc")
		from(dokkaHtml.outputDirectory)
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

	artifacts {
		add("archives", sourceJar)
	}
}

pluginBundle {
	website = "https://www.availlang.org/"
	vcsUrl = "<uri to project source repository>"
	tags = listOf("avail")
}

gradlePlugin {
	plugins {
		create("avail") {
			id = "avail"
			displayName = "Avail Gradle Plugin"
			description = "This plugin assists in Avail project setup "
			implementationClass = "avail.plugin.AvailPlugin"
		}
	}
}


publishing {
	repositories {
		maven {
			this.
			name = "localPluginRepository"
			url = uri("${rootProject.projectDir}/local-plugin-repository")
		}
	}

	// You can pull in the locally published maven into another project by adding
	// this to your settings.gradle.kts file of the project you want to pull the
	// dependency into:
	// ```
	// pluginManagement {
	//	repositories {
	//		mavenLocal()
	//      // Adds the gradle plugin portal back to the plugin repositories as
	//      // this is removed (overridden) by adding any repository here.
	//		gradlePluginPortal()
	//	}
	// }
	// rootProject.name = "plugin-test"
	//```
	publications {
		create<MavenPublication>("avail-plugin") {
			val sourceJar = tasks.getByName("sourceJar") as Jar
			from(components["java"])
			artifact(sourceJar)
		}
	}
}


