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
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File

plugins {
	id("java")
	kotlin("jvm") version "1.9.0"
	id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
	mavenLocal()
	mavenCentral()
}

/** The language level version of Kotlin. */
val kotlinLanguage = "1.9"

/** The `com.google.code.findbugs:jsr305` version. */
val jsrVersion = "3.0.2"

dependencies {
	implementation("org.availlang:avail:2.0.0.alpha21")
	implementation("com.google.code.findbugs:jsr305:$jsrVersion")
}

/**
 * Construct a operating system-specific file path using [File.separator].
 *
 * @param path
 *   The locations to join using the system separator.
 * @return
 *   The constructed String path.
 */
fun systemPath(vararg path: String): String =
	path.toList().joinToString(File.separator)

/**
 *
 */
val bootstrapPackagePath =
	systemPath("avail", "tools", "bootstrap")

/**
 * The [Project.getProjectDir] relative path of the bootstrap package.
 */
val relativePathBootstrap =
	systemPath("src", "main", "kotlin", bootstrapPackagePath)

/**
 * Copy the generated bootstrap property files into the build directory, so that
 * the executable tools can find them as resources..
 *
 * @param task
 *   The [Copy] task in which this code is executed.
 */
fun Project.relocateGeneratedPropertyFiles (task: Copy)
{
	val pathBootstrap =
		fileTree(systemPath(
			"${rootProject.projectDir}",
			relativePathBootstrap))
	val movedPropertyFiles = file(systemPath(
		"$buildDir",
		"classes",
		"kotlin",
		"main",
		"avail",
		"tools",
		"bootstrap"))
	val lang = System.getProperty("user.language")
	pathBootstrap.include(systemPath("**", "*_${lang}.properties"))
	// This is a lie, but it ensures that this rule will not run until after the
	// Kotlin source is compiled.
	pathBootstrap.builtBy("compileKotlin")
	// TODO finish
	task.inputs.files + pathBootstrap
	task.outputs.dir(movedPropertyFiles)
	task.from(pathBootstrap)
	task.into(movedPropertyFiles)
	task.duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

/**
 * Generate the new bootstrap Avail modules for the current locale and copy them
 * to the appropriate location for distribution.
 *
 * @param task
 *   The [Copy] task in which this code is executed.
 */
fun Project.projectGenerateBootStrap(task: Copy)
{
	val source = systemPath("$projectDir", "src", "main", "resources")
	val lang = System.getProperty("user.language")
	val pathBootstrap = fileTree(systemPath(
		source,
		"avail",
		"tools",
		"bootstrap",
		"generated",
		lang,
		"Bootstrap.avail"))
	task.inputs.files + pathBootstrap
	val distroBootstrap =
		file(systemPath(
			"${rootProject.projectDir}",
			distroSrc,
			"avail",
			"Avail.avail",
			"Foundation.avail",
			"Bootstrap.avail"))
	task.outputs.dir(distroBootstrap)

	group = "bootstrap"
	task.dependsOn(tasks.getByName("internalGenerateBootstrap"))

	task.from(pathBootstrap)
	task.into(distroBootstrap)

	task.duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks {
	// Don't build any JAR files, since these are bootstrap tools only.
	jar { enabled = false }

	/**
	 * Copy the generated bootstrap property files into the build directory, so
	 * that the executable tools can find them as resources.
	 *
	 * See [AvailBootstrapModule.relocateGeneratedPropertyFiles].
	 */
	val relocateGeneratedPropertyFiles by creating(Copy::class) {
		description =
			"Copy the generated bootstrap property files into the build " +
				"directory, that the executable tools can find them as " +
				"resources. See " +
				"`AvailBootstrapModule.relocateGeneratedPropertyFiles`."
		group = "bootstrap"
		relocateGeneratedPropertyFiles(this)
	}

	// Update the dependencies of "classes".
	classes { dependsOn(relocateGeneratedPropertyFiles) }

	/** Bootstrap Primitive_<lang>.properties for the current locale. */
	val generatePrimitiveNames by creating(JavaExec::class) {
		description =
			"Bootstrap Primitive_<lang>.properties for the current locale."
		group = "bootstrap"
		mainClass.set("avail.tools.bootstrap.PrimitiveNamesGenerator")
		classpath = sourceSets.main.get().runtimeClasspath
		dependsOn(classes)
	}

	/** Bootstrap ErrorCodeNames_<lang>.properties for the current locale. */
	val generateErrorCodeNames by creating(JavaExec::class) {
		description =
			"Bootstrap ErrorCodeNames_<lang>.properties for the current locale."
		group = "bootstrap"
		mainClass.set("avail.tools.bootstrap.ErrorCodeNamesGenerator")
		classpath = sourceSets.main.get().runtimeClasspath
		dependsOn(classes)
	}

	/** Bootstrap ErrorCodeNames_<lang>.properties for the current locale. */
	val generateSpecialObjectNames by creating(JavaExec::class) {
		description =
			"Bootstrap ErrorCodeNames_<lang>.properties for the current locale."
		group = "bootstrap"
		mainClass.set("avail.tools.bootstrap.SpecialObjectNamesGenerator")
		classpath = sourceSets.main.get().runtimeClasspath
		dependsOn(classes)
	}

	/**
	 * Gradle task to generate all bootstrap `.properties` files for the current
	 * locale.
	 */
	val generateAllNames by creating {
		description =
			"Gradle task to generate all bootstrap `.properties` files for " +
				"the current locale."
		group = "bootstrap"
		dependsOn(generatePrimitiveNames)
		dependsOn(generateErrorCodeNames)
		dependsOn(generateSpecialObjectNames)
	}

	/**
	 * Generate the new bootstrap Avail modules for the current locale.
	 *
	 * This is used in [Project.generateBootStrap].
	 */
	val internalGenerateBootstrap by creating(JavaExec::class) {
		description =
			"Generate the new bootstrap Avail modules for the current locale." +
				"\n\tThis is used in Project.generateBootStrap."
		group = "internal"
		mainClass.set("avail.tools.bootstrap.BootstrapGenerator")
		classpath = sourceSets.main.get().runtimeClasspath
		dependsOn(classes)
	}

	/**
	 * Gradle task to generate the new bootstrap Avail modules for the current
	 * locale and copy them to the appropriate location for distribution.
	 */
	val generateBootstrap by creating(Copy::class) {
		description =
			"Gradle task to generate the new bootstrap Avail modules for the " +
				"current locale and copy them to the appropriate location " +
				"for distribution."
		group = "bootstrap"
		projectGenerateBootStrap(this)
	}
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
	languageVersion = kotlinLanguage
	apiVersion = kotlinLanguage
}

/** The root Avail distribution directory name. */
val distroDir = "distro"

/** The relative path to the Avail distribution source directory. */
val distroSrc = systemPath(distroDir, "src")

/**
 * Generate the new bootstrap Avail modules for the current locale and copy them
 * to the appropriate location for distribution.
 *
 * @param task
 *   The [Copy] task in which this code is executed.
 */
fun Project.generateBootStrap(task: Copy)
{
	val source = systemPath("$projectDir", "src", "main", "resources")
	val lang = System.getProperty("user.language")
	val pathBootstrap = fileTree(
		systemPath(
		source,
		"avail",
		"tools",
		"bootstrap",
		"generated",
		lang,
		"Bootstrap.avail"))
	task.inputs.files + pathBootstrap
	val distroBootstrap =
		file(
			systemPath(
			"${rootProject.projectDir}",
			distroSrc,
			"avail",
			"Avail.avail",
			"Foundation.avail",
			"Bootstrap.avail"))
	task.outputs.dir(distroBootstrap)

	group = "bootstrap"
	task.dependsOn(tasks.getByName("internalGenerateBootstrap"))

	task.from(pathBootstrap)
	task.into(distroBootstrap)

	task.duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
