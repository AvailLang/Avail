/*
 * AvailSetup.kt
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

package com.avail.build

import com.avail.build.AvailSetupContext.distroSrc
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Copy
import com.avail.plugins.gradle.AvailRoot
import java.net.URI

/// Herein lies utility functions and state for use in gradle-related operations
/// specific to Avail setup and build functions.

object AvailSetupContext
{
	/** The root Avail distribution directory name. */
	const val distroDir = "distro"

	/** The relative path to the Avail distribution source directory. */
	const val distroSrc = "$distroDir/src"

	/** The relative path to the Avail distribution lib directory. */
	const val distroLib = "$distroDir/lib"

	/** The default list of [AvailRoot.name]s. */
	val availTestRootNames =
		listOf("avail", "builder-tests", "examples", "website")

	/**
	 * Avail [Project.getProjectDir] path to the Avail `Primitive` classes
	 * in the build directory.
	 */
	const val allPrimitivesPath =
		"${BuildContext.buildClassesPath}/com/avail/interpreter"

	/**
	 * The path to the `All_Primitives.txt` file that lists all the primitives.
	 */
	const val allPrimitivesText = "$allPrimitivesPath/All_Primitives.txt"

	/**
	 * The pattern that matches all the names of `Primitive` class files
	 * relative to [allPrimitivesPath].
	 */
	const val primitiveClassFileNamePattern = "**/P_*.class"

	/**
	 *
	 */
	const val bootstrapPackagePath = "com/avail/tools/bootstrap"

	/**
	 * The [Project.getProjectDir] relative path of the bootstrap package.
	 */
	const val relativePathBootstrap =
		"src/main/kotlin/$bootstrapPackagePath"

	/**
	 * The [Project.getProjectDir] relative path of the built bootstrap package.
	 */
	const val relativePathBootstrapClasses =
		"${BuildContext.buildClassesPath}/$bootstrapPackagePath"
}

/**
 * Answer an [AvailRoot] relative to this [Project.getProjectDir].
 *
 * @param name
 *   The [AvailRoot.name].
 * @return
 *   An [AvailRoot].
 */
fun Project.availRoot(name: String): AvailRoot
{
	val rootURI = "${rootProject.projectDir}/$distroSrc/$name"
	println("AvailRoot: $rootURI")
	return AvailRoot(
		name,
		URI(rootURI))
}

/**
 * Compute the Avail roots. This is needed to properly configure "test".
 *
 * @return
 *   The concatenated string of `root=root-path` separated by `;`.
 */
fun Project.computeAvailRootsForTest (): String =
	AvailSetupContext.availTestRootNames
		.joinToString(";") { availRoot(it).rootString }

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
		fileTree("${rootProject.projectDir}/${AvailSetupContext.relativePathBootstrap}")
	val movedPropertyFiles =
		file("$buildDir/classes/kotlin/main/com/avail/tools/bootstrap")
	val lang = System.getProperty("user.language")
	pathBootstrap.include("**/*_${lang}.properties")
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
fun Project.generateBootStrap(task: Copy)
{
	val source = "$projectDir/src/main/kotlin"
	val lang = System.getProperty("user.language")
	val pathBootstrap = fileTree(
		"$source/com/avail/tools/bootstrap/generated/$lang/Bootstrap.avail")
	task.inputs.files + pathBootstrap
	val distroBootstrap =
	file("$distroSrc/avail/Avail.avail/Foundation.avail/Bootstrap.avail")
	task.outputs.dir(distroBootstrap)

	group = "bootstrap"
	task.dependsOn(tasks.getByName("internalGenerateBootstrap"))

	task.from(pathBootstrap)
	task.into(distroBootstrap)

	task.duplicatesStrategy = DuplicatesStrategy.INCLUDE
}


