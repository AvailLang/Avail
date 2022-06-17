/*
 * Build.kt
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

package avail.build

import avail.build.AvailSetupContext.distroLib
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete

/// Herein lies utility functions and state for use in `build.gradle.kts` files.

object BuildContext
{
	/**
	 * [Project.getProjectDir]-relative path to the generated JVM classes.
	 */
	const val buildClassesPath = "classes/kotlin/main"

	/**
	 * The build version string of the form:
	 * [Versions.avail]-"yyyyMMdd.HHmmss", representing the time of the
	 * build.
	 */
	val buildVersion: String get() = "${Versions.avail}-${Utility.formattedNow}"
}

/**
 * The platform neutral (looking at you Windows...) build directory path for
 * this [Project].
 */
val Project.platformNeutralBuildDir get() =
	"$buildDir".replace('\\', '/')

/**
 * Copy the JAR into the distribution directory.
 *
 * @param task
 *   The [Copy] task that is responsible for performing this operation.
 * @param files
 *   The [files][FileCollection] to copy.
 */
fun Project.releaseAvail (task: Copy, files: FileCollection)
{
	task.run {
		group = "release"
		from(files)
		into(file("${rootProject.projectDir}/$distroLib"))
		rename(".*", "avail.jar")
		duplicatesStrategy = DuplicatesStrategy.INCLUDE
	}
}

/**
 * Copy the JAR into the distribution directory.
 *
 * @param task
 *   The [Copy] task that is responsible for performing this operation.
 * @param rename
 *   The name to apply to the copied jar.
 * @param files
 *   The [files][FileCollection] to copy.
 */
fun Project.releaseSubproject (
	task: Copy,
	rename:String,
	files: FileCollection)
{
	task.run {
		group = "release"
		from(files)
		into(file(
			"${rootProject.projectDir}/$distroLib"))
		rename(".*", rename)
		duplicatesStrategy = DuplicatesStrategy.INCLUDE
	}
}

/**
 * Remove released libraries; both the contents of
 * [AvailSetupContext.distroLib] and the publication staging directory,
 * build/libs.
 *
 * @param task
 *   The [Delete] task responsible for performing the operation.
 */
fun Project.scrubReleases (task: Delete)
{
	task.run {
		// distro/lib
		delete(fileTree("${rootProject.projectDir}/$distroLib")
			.matching { include("**/*.jar") })
		// And the publication staging directory, build/libs
		delete(fileTree("$projectDir/build/libs").matching {
			include("**/*.jar")
		})
	}
}

/**
 * Remove all the jars, excluding "*-all.jar" from the
 * [build directory][Project.getBuildDir].
 */
fun Project.cleanupJars ()
{
	delete(fileTree("$buildDir/libs").matching {
		include("**/*.jar")
		exclude("**/*-all.jar")
	})
}

/**
 * Remove all the "*-all.jar" jars from the
 * [build directory][Project.getBuildDir].
 */
fun Project.cleanupAllJars ()
{
	delete(fileTree("$buildDir/libs").matching {
		include("**/*-all.jar")
	})
}
