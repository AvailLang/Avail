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

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties
import java.io.FileInputStream
import java.io.FileOutputStream

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
	`maven-publish`
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
version = Versions.getReleaseVersion()

repositories {
	mavenCentral()
	maven {
		setUrl("https://maven.pkg.github.com/AvailLang/Avail")
		metadataSources {
			mavenPom()
			artifact()
		}
		credentials {
			username = "anonymous"
			// A public key read-only token for Avail downloads.
			password = "gh" + "p_z45vpIzBYdnOol5Q" + "qRCr4x8FSnPaGb3v1y8n"
		}
	}
}

dependencies {
	implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.kotlin}")
}

tasks {
	withType<JavaCompile> {
		options.encoding = "UTF-8"
		sourceCompatibility = Versions.jvmTarget
		targetCompatibility = Versions.jvmTarget
	}
	withType<KotlinCompile> {
		kotlinOptions {
			jvmTarget = Versions.jvmTarget
			freeCompilerArgs = listOf("-Xjvm-default=all-compatibility")
			languageVersion = Versions.kotlinLanguage
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

	register("updateVersion", DefaultTask::class)
	{
		if(project.hasProperty("versionStripe"))
		{
			val updatedVersion =
				project.property("versionStripe").toString()
			val propsFile = FileInputStream(Versions.releaseVersionFile)
			val props = Properties()
			props.load(propsFile)
			props.setProperty("releaseVersion", updatedVersion)
			props.store(FileOutputStream(Versions.releaseVersionFile), null)
		}
		else
		{
			System.err.println("`avail-plugin` `updateVersion` task run but " +
				"receive no updated version.")
		}
	}
}

publishing {
	repositories {
		maven {
			name = "localPluginRepository"
			url = uri("../local-plugin-repository")
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


