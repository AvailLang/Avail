/*
 * AvailExtension.kt
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

package com.avail.plugin

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import java.io.File
import java.net.URI

/**
 * `AvailExtension` is a Gradle extension for the [AvailPlugin] which is where
 * a user can configure Avail.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
open class AvailExtension
{
	/**
	 * The directory location where the Avail roots exist. The path to this
	 * location must be absolute.
	 */
	open var rootsDirectory: String = ""

	/**
	 * The directory location where the Avail roots repositories exist.
	 */
	open var repositoryDirectory: String = ""

	/**
	 * `true` indicates the Avail Standard Library (`avail-stdlib`) should be
	 * included in the [roots directory][rootsDirectory]; `false`
	 * otherwise.
	 */
	open var useAvailStdLib: Boolean = true

	/**
	 * The list of [AvailRoot]s that are statically known to be included in the
	 * [roots directory][rootsDirectory].
	 */
	private val roots: MutableSet<AvailRoot> = mutableSetOf()

	/**
	 * The host [Project].
	 */
	private lateinit var project: Project

	/**
	 * Add an Avail root with the provided name and [URI].
	 *
	 * There is no need to prefix the file scheme, `file://`, if it exists on
	 * the local file file system; otherwise the scheme should be prefixed.
	 *
	 * @param name
	 *   The name of the root to add.
	 * @param uri
	 *   The uri path to the root.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	fun root(name: String, uri: String)
	{
		roots.add(AvailRoot(name, uri))
	}

	/**
	 * Add an Avail root with the provided name and [URI]. This defaults the
	 * root URI to be located in the [rootsDirectory], using the root name as
	 * the top level folder of the root.
	 *
	 * @param name
	 *   The name of the root to add.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	fun root(name: String)
	{
		roots.add(AvailRoot(name, "$rootsDirectory/$name"))
	}

	/**
	 * The list of additional dependencies to be bundled in with the fat jar
	 * of the Workbench.
	 */
	val workBenchDependencies = mutableSetOf<String>()

	/**
	 * Add a dependency jar file to be included in the workbench jar.
	 *
	 * @param dependency
	 *   The String path to the dependency that must be added.
	 */
	@Suppress("unused")
	fun workBenchDependency(dependency: String)
	{
		workBenchDependencies.add(dependency)
	}

	/**
	 * The VM Options associated with running the workbench.
	 */
	internal val workbenchVmOptions: List<String> get() =
		standardWorkbenchVmOptions + buildString {
			roots.forEach { println(it.rootString) }
			if (roots.isNotEmpty())
			{
				append("-DavailRoots=")
				append(roots.joinToString(";") { it.rootString })
			}
		} + listOf("-Davail.repositories=$repositoryDirectory")

	/**
	 * Create a printable view of this entire [AvailExtension]'s current
	 * configuration state.
	 */
	internal val printableConfig: String get() =
		buildString {
			append("\n================== Avail Configuration ==================\n")
			append("\tAvail Version: ${AvailPlugin.AVAIL_STRIPE_RELEASE}\n")
			append("\tRepository Location: $repositoryDirectory\n")
			append("\tRoots Location: $rootsDirectory\n")
			append("\tIncluded Roots:")
			roots.forEach { append("\n\t\t-${it.name}: ${it.uri}") }
			append("\n\tIncluded Workbench Dependencies:")
			workBenchDependencies.forEach { append("\n\t\t$it") }
			append("\n=========================================================\n")
		}

	/**
	 * Initialize this [AvailExtension] with the host [Project].
	 *
	 * @param project
	 *   The host `Project`.
	 * @param availLibConfig
	 *   The `project` [Configuration] that contains the `avail-stdlib`
	 *   dependency. This is [optionally][useAvailStdLib] included in the
	 *   [Avail roots][rootsDirectory]
	 */
	internal fun init(project: Project, availLibConfig: Configuration)
	{
		this.project = project
		val rootsDir =
			rootsDirectory.ifEmpty {
				"${project.projectDir.absolutePath}/$defaultAvailRootsDirectory"
			}
		rootsDirectory = rootsDir
		project.mkdir(rootsDir)

		val reposDir =
			repositoryDirectory.ifEmpty {
				"${project.projectDir.absolutePath}/$defaultRepositoryDirectory"
			}
		repositoryDirectory = reposDir
		project.mkdir(reposDir)

		if (useAvailStdLib)
		{
			root(
				AvailPlugin.AVAIL,
				"jar:$rootsDir/${AvailPlugin.AVAIL_STDLIB_JAR_NAME}")
		}
		val stdlibJar = availLibConfig.singleFile
		val targetFile =
			File("${rootsDirectory}/${AvailPlugin.AVAIL_STDLIB_JAR_NAME}")
		stdlibJar.copyTo(targetFile, true)
	}

	companion object
	{
		/**
		 * The directory location where the Avail roots exist.
		 */
		private const val defaultAvailRootsDirectory: String = "avail/roots"

		/**
		 * The directory location where the Avail roots repositories exist.
		 */
		private const val defaultRepositoryDirectory: String =
			"avail/repositories"

		/**
		 * The static list of Workbench VM arguments.
		 */
		private val standardWorkbenchVmOptions = listOf(
			"-ea", "-XX:+UseCompressedOops", "-Xmx12g", "-DavailDeveloper=true")
	}
}
