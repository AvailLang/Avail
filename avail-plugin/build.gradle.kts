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
			id = "com.avail.plugin"
			implementationClass = "com.avail.plugin.AvailPlugin"
		}
	}
}

group = "com.avail.plugin"
version = Versions.availStripeVersion

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

tasks.test {
	useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		jvmTarget = Versions.jvmTarget
		languageVersion = Versions.kotlinLanguage
	}
}
