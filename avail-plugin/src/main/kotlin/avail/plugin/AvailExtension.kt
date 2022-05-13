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
	 * The version of Avail to use. This will be the version that is used for
	 * `avail-core`, `avail-workbench`, `avail-stdlib`. By default it is set to
	 * `+` which indicates the latest version in the repository should be used.
	 */
	var availVersion = "+"

	/**
	 * The directory location where the Avail roots exist. The path to this
	 * location must be absolute.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	var rootsDirectory: String =
			"${project.projectDir.absolutePath}/$defaultAvailRootsDirectory"
		set(value)
		{
			availStandardLibrary?.root(value)?.let { root(it) }
			field = value
		}

	/**
	 * The directory location where the Avail roots repositories exist.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	var repositoryDirectory: String =
		"${project.projectDir.absolutePath}/$defaultRepositoryDirectory"

	/**
	 * The [AvailStandardLibrary] if it is being used by this project.
	 */
	internal var availStandardLibrary: AvailStandardLibrary? = null

	/**
	 * The map of [AvailRoot.name]s to be [AvailRoot]s be included in the Avail
	 * project.
	 */
	internal val roots: MutableMap<String, AvailRoot> = mutableMapOf()

	/**
	 * This function informs the plugin to include the Avail Standard Library
	 * as a root.
	 *
	 * @param configure
	 *   The lambda that allows for configuring the [AvailStandardLibrary].
	 */
	@Suppress("Unused")
	fun useStdAvailLib (configure: AvailStandardLibrary.() -> Unit)
	{
		val asl = AvailStandardLibrary(
			"$AVAIL_STDLIB_BASE_JAR_NAME")
		configure(asl)
		this.availStandardLibrary = asl
		root(asl.root(rootsDirectory))
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
	internal val useAvailStdLib: Boolean get()  = availStandardLibrary != null

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
	 * Add the provided [AvailRoot].
	 *
	 * @param root
	 *   The name of the root to add.
	 */
	@Suppress("Unused")
	fun root(root: AvailRoot)
	{
		roots[root.name] = root
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
	 *   all roots have been added.
	 */
	@Suppress("Unused")
	fun root(
		name: String,
		uri: String = "$rootsDirectory/$name",
		initializer: (AvailRoot) -> Unit = {})
	{
		root(AvailRoot(name, uri, initializer))
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
	 * Create a printable view of this entire [AvailExtension]'s current
	 * configuration state.
	 */
	val printableConfig: String get() =
		buildString {
			append("\n========================= Avail Configuration")
			append(" =========================")
			append("\n\tRepository Location: $repositoryDirectory")
			append("\n\tVM Arguments to include for Avail Runtime:")
			append(roots.values
				.sorted()
				.joinToString(";", "\n\t\t• -DavailRoots=") { it.rootString })
			append("\n\t\t• -Davail.repositories=$repositoryDirectory")
			append("\n\tRoots Location: $rootsDirectory")
			append("\n\tIncluded Roots:")
			roots.values.sorted().forEach {
				append("\n\t\t• ${it.name}: ${it.uri}")
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
		private const val defaultAvailRootsDirectory: String = ".avail/roots"

		/**
		 * The directory location where the Avail roots repositories exist.
		 */
		private const val defaultRepositoryDirectory: String =
			".avail/repositories"

		/**
		 * The static list of Workbench VM arguments.
		 */
		private val standardWorkbenchVmOptions = listOf(
			"-ea", "-XX:+UseCompressedOops", "-Xmx6g", "-DavailDeveloper=true")
	}
}
