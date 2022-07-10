/*
 * CreateDigestsFileTask.kt
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

package avail.plugins.gradle

import org.availlang.artifact.ArtifactDescriptor
import org.availlang.artifact.AvailArtifactType
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import org.availlang.artifact.manifest.AvailArtifactManifest
import org.availlang.artifact.manifest.AvailManifestRoot
import org.availlang.artifact.AvailArtifact
import org.availlang.artifact.jar.AvailLibraryJarBuilder
import org.availlang.artifact.jar.JvmComponent
import org.gradle.jvm.tasks.Jar
import java.io.File

/**
 * Perform all tasks necessary to package the Avail Standard Library as an
 * [AvailArtifact].
 *
 * This performs the following tasks:
 * 1. Creates the [ArtifactDescriptor] file.
 * 2. Creates the [AvailArtifactManifest] file.
 * 3. Creates source digests file.
 *
 * @author Richard Arriaga
 */
abstract class CreateAvailStandardLibraryJar : Jar()
{
	/**
	 * The algorithm to use to create the digests of the Avail Standard Library
	 * source.
	 */
	@Input
	var digestAlgorithm: String = "SHA-256"

	init
	{
		group = "build"
		description = "Creaate the Avail Standard Library jar."
	}

	@TaskAction
	fun createAvailStandardLibraryJar ()
	{
		File("${project.buildDir}").mkdirs()
		val location = "${project.rootDir}/distro/src/$availStdLibRootName"
		val jarBuilder = AvailLibraryJarBuilder(
			"${project.buildDir}/$availStandardLibraryJarFileName",
			project.version.toString(),
			"avail",
			AvailArtifactManifest.manifestFile(
				AvailArtifactType.LIBRARY,
				mapOf(
					availStdLibRootName to
					AvailManifestRoot(
						"avail",
						listOf(".avail"),
						listOf("!_"),
						"The singular module root of Avail standard library.")),
				"The official Avail standard library.",
				JvmComponent.NONE))
		jarBuilder.addRoot(location, availStdLibRootName, digestAlgorithm)
		jarBuilder.finish()
	}

	companion object
	{
		/**
		 * The name of the Avail Standard Library root.
		 */
		const val availStdLibRootName = "avail"

		/**
		 * The `buildDir` project relative directory of the Avail Standard
		 * Library source files.
		 */
		internal const val availSourceLocation =
			"../distro/src/$availStdLibRootName"

		/**
		 * Name of the Avail Standard Library artifact jar file.
		 */
		const val availStandardLibraryJarFileName =
			"avail-standard-library.jar"
	}
}
