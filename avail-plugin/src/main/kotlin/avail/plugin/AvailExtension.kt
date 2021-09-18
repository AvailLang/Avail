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
package avail.plugin

import avail.plugin.AvailPlugin.Companion.AVAIL_STDLIB_BASE_JAR_NAME
import org.gradle.api.Project
import java.io.File
import java.net.URI

/**
 * `AvailExtension` is a Gradle extension for the [AvailPlugin] which is where
 * a user can configure Avail.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property project
 *   The host [Project].
 * @property plugin
 *   The hosting [AvailPlugin] instance.
 */
open class AvailExtension constructor(
	private val project: Project,
	private val plugin: AvailPlugin)
{
	/**
	 * The name of the standard
	 */
	private val stdLibName =
		"$AVAIL_STDLIB_BASE_JAR_NAME-${plugin.releaseVersion}.jar"

	/**
	 * The directory location where the Avail roots exist. The path to this
	 * location must be absolute.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	var rootsDirectory: String =
			"${project.projectDir.absolutePath}/$defaultAvailRootsDirectory"
		set(value)
		{
			if (useAvailStdLib)
			{
				addStdLib(value)
			}
			field = value
		}

	/**
	 * The directory location where the Avail roots repositories exist.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	var repositoryDirectory: String =
		"${project.projectDir.absolutePath}/$defaultRepositoryDirectory"

	/**
	 * The map of [AvailRoot.name]s to [AvailRoot]s be included in the Avail
	 * project.
	 */
	internal val roots: MutableMap<String, AvailRoot> = mutableMapOf()

	/**
	 * Add the `avail-stdlib` to the [roots].
	 */
	private fun addStdLib (rootDir: String)
	{
		roots[AvailPlugin.AVAIL] = AvailRoot(
			AvailPlugin.AVAIL, "jar:$rootDir/$stdLibName")
	}

	/**
	 * The map of [CreateAvailRoot.name]s to [CreateAvailRoot]s that will be
	 * created when the `initializeAvail` task is run.
	 */
	internal val createRoots: MutableMap<String, CreateAvailRoot> =
		mutableMapOf()

	/**
	 * `true` indicates the Avail Standard Library (`avail-stdlib`) should be
	 * included in the [roots directory][rootsDirectory]; `false`
	 * otherwise.
	 */
	var useAvailStdLib: Boolean = true
		set(value)
		{
			if (value)
			{
				addStdLib(rootsDirectory)
			}
			field = value
		}

	/**
	 * Raw module header comment. This is typically for a copyright. Will be
	 * wrapped in comment along with file name. If comment body is empty
	 * (*default*), will only provide the file name in the header comment.
	 */
	var moduleHeaderCommentBody: String = ""

	/**
	 * The absolute path to a file containing the text for the
	 * [moduleHeaderCommentBody]. This will be ignored if
	 * [moduleHeaderCommentBodyFile] is not empty.
	 */
	@Suppress("Unused")
	var moduleHeaderCommentBodyFile: String = ""
		set(value)
		{
			if (moduleHeaderCommentBody.isEmpty())
			{
				moduleHeaderCommentBody = project.file(value).readText()
			}
			field = value
		}

	/**
	 * Add an Avail root with the provided name and [URI].
	 *
	 * There is no need to prefix the file scheme, `file://`, if it exists on
	 * the local file system; otherwise the scheme should be prefixed.
	 *
	 * @param name
	 *   The name of the root to add.
	 * @param uri
	 *   The uri path to the root. This defaults to the root URI to be located
	 *   in the [rootsDirectory]
	 * @param initializer
	 *   A lambda that accepts the created [AvailRoot] and is executed after
	 *   [AvailExtension.initExtension] is run. Does nothing by default.
	 */
	@Suppress("Unused")
	fun root(
		name: String,
		uri: String = "$rootsDirectory/$name",
		initializer: (AvailRoot) -> Unit = {})
	{
		roots[name] = AvailRoot(name, uri, initializer)
	}

	/**
	 * Add a [CreateAvailRoot] to be created in the [rootsDirectory].
	 *
	 * @param name
	 *   The [CreateAvailRoot.name].
	 * @return
	 *   The created Root.
	 */
	@Suppress("Unused")
	fun createRoot (name: String): CreateAvailRoot =
		CreateAvailRoot(name, "$rootsDirectory/$name").apply {
			createRoots[name] = this
			roots[name] = this
		}

	/**
	 * Initialize this [AvailExtension] with the host [Project].
	 *
	 * @param project
	 *   The host `Project`.
	 */
	internal fun initExtension(project: Project)
	{
		project.mkdir(rootsDirectory)
		project.mkdir(repositoryDirectory)

		if (useAvailStdLib)
		{
			val availLibConfig =
				project.configurations.getByName(AvailPlugin.AVAIL_LIBRARY)
			val stdlibJar = availLibConfig.singleFile
			val targetFile =
				File("$rootsDirectory/$stdLibName")
			stdlibJar.copyTo(targetFile, true)
		}
	}

	/**
	 * Create a printable view of this entire [AvailExtension]'s current
	 * configuration state.
	 */
	val printableConfig: String get() =
		buildString {
			append("\n========================= Avail Configuration")
			append(" =========================\n")
			append("\tAvail Version: ${plugin.releaseVersion}\n")
			append("\tRepository Location: $repositoryDirectory\n")
			append("\tRoots Location: $rootsDirectory\n")
			append("\tIncluded Roots:")
			roots.values.sorted().forEach {
				append("\n\t\t-${it.name}: ${it.uri}")
			}
			append("\n\tCreated Roots:")
			createRoots.values.sorted().forEach {
				append(it.configString)
			}
			append("\n====================================")
			append("====================================\n")
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
