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
import java.io.File
import java.net.URI

/**
 * `AvailExtension` is a Gradle extension for the [AvailPlugin] which is where
 * a user can configure Avail.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
open class AvailExtension constructor(
	internal val plugin: AvailPlugin,
	internal val project: Project)
{
	/**
	 * The directory location where the Avail roots exist. The path to this
	 * location must be absolute.
	 */
	open var rootsDirectory: String = ""

	/**
	 * Answer the roots directory; uses default if [rootsDirectory] is not
	 * set in this extension in the build script.
	 */
	internal val rootsDir get() =
		rootsDirectory.ifEmpty {
			"${project.projectDir.absolutePath}/$defaultAvailRootsDirectory"
		}

	/**
	 * The directory location where the Avail roots repositories exist.
	 */
	open var repositoryDirectory: String = ""

	/**
	 * Provide the repos directory; uses default if [repositoryDirectory] is not
	 * set in this extension in the build script.
	 */
	val reposDir: String get() =
		repositoryDirectory.ifEmpty {
			"${project.projectDir.absolutePath}/$defaultRepositoryDirectory"
		}

	/**
	 * `true` indicates the Avail Standard Library (`avail-stdlib`) should be
	 * included in the [roots directory][rootsDirectory]; `false`
	 * otherwise.
	 */
	open var useAvailStdLib: Boolean = true

	/**
	 * Raw copyright file header. Will be wrapped in comment along with file
	 * name. If copyright is empty (*default*), will only provide the file name
	 * in the header.
	 */
	open var copyrightBody: String = ""

	/**
	 * The absolute path to a file containing the text for the [copyrightBody].
	 * This will be ignored if [copyrightBodyFile] is not empty.
	 */
	open var copyrightBodyFile: String = ""

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
	 * @param initializer
	 *   A lambda that accepts the created [AvailRoot] and is executed after
	 *   [AvailExtension.initExtension] is run. Does nothing by default.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	fun root(name: String, uri: String, initializer: (AvailRoot) -> Unit = {})
	{
		plugin.roots.add(AvailRoot(name, uri, initializer))
	}

	/**
	 * Add an Avail root with the provided name and [URI]. This defaults the
	 * root URI to be located in the [rootsDirectory], using the root name as
	 * the top level folder of the root.
	 *
	 * @param name
	 *   The name of the root to add.
	 * @param initializer
	 *   A lambda that accepts the created [AvailRoot] and is executed after
	 *   [AvailExtension.initExtension] is run.
	 */
	@Suppress("Unused")
	fun root(name: String, initializer: (AvailRoot) -> Unit = {})
	{
		plugin.roots.add(
			AvailRoot(name, "${plugin.internalRootsDir}/$name", initializer))
	}

	/**
	 * Add a [CreateAvailRoot] to be created in the [rootsDirectory].
	 *
	 * @param name
	 *   The [CreateAvailRoot.name].
	 * @param rootInitializer
	 *   A lambda that accepts the created `CreateAvailRoot` so that it may be
	 *   initialized.
	 */
	@Suppress("Unused")
	fun createRoot (name: String, rootInitializer: (CreateAvailRoot) -> Unit)
	{
		val createRoot =
			CreateAvailRoot(name, "$rootsDir/$name")
		rootInitializer(createRoot)
		plugin.createRoots.add(createRoot)
		plugin.roots.add(createRoot)
	}

	/**
	 * Add a dependency jar file to be included in the workbench jar.
	 *
	 * @param dependency
	 *   The String path to the dependency that must be added.
	 */
	@Suppress("unused")
	fun workBenchDependency(dependency: String)
	{
		plugin.workBenchDependencies.add(dependency)
	}

	/**
	 * Initialize this [AvailExtension] with the host [Project].
	 *
	 * @param project
	 *   The host `Project`.
	 */
	internal fun initExtension(project: Project)
	{
		plugin.internalRootsDir = rootsDir
		project.mkdir(rootsDir)
		plugin.internalReposDir = reposDir
		project.mkdir(reposDir)

		plugin.internalCopyrightBody = copyrightBody
		plugin.internalCopyrightBodyFile = copyrightBodyFile
		if (useAvailStdLib)
		{
			val availLibConfig =
				project.configurations.getByName(AvailPlugin.AVAIL_LIBRARY)
			val stdlibJar = availLibConfig.singleFile
			val targetFile =
				File("$rootsDir/${AvailPlugin.AVAIL_STDLIB_JAR_NAME}")
			stdlibJar.copyTo(targetFile, true)
		}
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
	}
}
