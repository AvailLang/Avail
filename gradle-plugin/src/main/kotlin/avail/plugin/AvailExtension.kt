/*
 * AvailExtension.kt
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
package avail.plugin

import org.availlang.artifact.AvailArtifactBuildPlan
import org.availlang.artifact.environment.AvailEnvironment
import org.availlang.artifact.environment.location.*
import org.availlang.artifact.environment.project.AvailProject
import org.availlang.artifact.environment.project.AvailProjectV1
import org.availlang.artifact.environment.project.LocalSettings
import org.availlang.artifact.environment.project.StylingGroup
import org.availlang.artifact.environment.project.TemplateGroup
import org.availlang.artifact.roots.AvailRoot
import org.availlang.artifact.roots.CreateAvailRoot
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
	 * The [PackageAvailArtifact] used to configure Avail artifact construction.
	 */
	private val packageAvailArtifact by lazy {
		PackageAvailArtifact(project, this)
	}

	/**
	 * `true` indicates the standard library is imported from Maven and used as
	 * an Avail module root in the project; `false` otherwise.
	 */
	internal var usesStdLib = false

	/**
	 * The name of the Avail project. Defaults to [Project.getName].
	 */
	var name: String = project.name

	/**
	 * The absolute path to the jar file that will be created.
	 *
	 * It is set to the following by default:
	 * ```
	 * "$outputDirectory$artifactName-$version.jar"
	 * ```
	 */
	@Suppress("unused")
	val targetOutputJar: String get() = packageAvailArtifact.targetOutputJar

	/**
	 * The description of the project that is included in the Avail artifact.
	 */
	var projectDescription: String = ""

	/**
	 * The [AvailLocation] directory where the project's Avail roots exist, not
	 * imported libraries. By default, this is in [AvailProject.ROOTS_DIR] at
	 * the top level of the project.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	var rootsDirectory: AvailLocation = ProjectHome(
		AvailProject.ROOTS_DIR,
		Scheme.FILE,
		project.projectDir.absolutePath,
		null)

	/**
	 * The [AvailLocation] directory where the Avail roots repositories exist.
	 *
	 * This is set to [AvailEnvironment.availHomeRepos] by default.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	var repositoryDirectory: AvailLocation = AvailRepositories(
		rootNameInJar = null)

	/**
	 * The [AvailStandardLibrary] if it is being used by this project.
	 */
	internal var availStandardLibrary: AvailStandardLibrary =
		AvailStandardLibrary()

	/**
	 * The map of [AvailRoot.name]s to be [AvailRoot]s be included in the Avail
	 * project.
	 */
	internal val roots: MutableMap<String, AvailRoot> = mutableMapOf()

	/**
	 * The list of external [AvailLibraryDependency]s to be included from Maven
	 * repositories.
	 */
	internal val rootDependencies = mutableListOf<AvailLibraryDependency>()

	@Suppress("MemberVisibilityCanBePrivate")
	var availVersion: String = ""

	/**
	 * This function informs the plugin to include the Avail Standard Library
	 * as a root from a Maven repository.
	 *
	 * @param configure
	 *   The lambda that allows for configuring the [AvailStandardLibrary].
	 */
	@Suppress("Unused")
	fun includeStdAvailLibDependency (configure: AvailStandardLibrary.() -> Unit)
	{
		usesStdLib = true
		configure(availStandardLibrary)
		rootDependencies.add(availStandardLibrary)

		projectRoot(availStandardLibrary.root(
			availStandardLibrary.group.replace(".", "/")))
	}

	/**
	 * Add an Avail library as a root from a Maven repository for the provided
	 * dependency string.
	 *
	 * @param name
	 *   The name of the root as it will be used by Avail.
	 * @param dependency
	 *   The target library's dependency string of the form
	 *   ```
	 *   "group:artifactName:version"
	 *   ```
	 */
	@Suppress("Unused")
	fun includeAvailLibDependency (name: String, dependency: String)
	{
		AvailLibraryDependency(name, dependency).apply {
			this@AvailExtension.rootDependencies.add(this)
			this@AvailExtension.projectRoot(this.root(group.replace(".", "/")))
		}
	}

	/**
	 * Configures the [PackageAvailArtifact] which is used to create the Avail
	 * artifact.
	 *
	 * @param configure
	 *   The lambda that allows for configuring the [PackageAvailArtifact].
	 */
	fun artifact (configure: PackageAvailArtifact.() -> Unit)
	{
		configure(packageAvailArtifact)
	}

	/**
	 * Create an [AvailArtifactBuildPlan] from the [packageAvailArtifact].
	 */
	val buildPlan: AvailArtifactBuildPlan get() =
		packageAvailArtifact.buildPlan

	/**
	 * Create the Avail Artifact configured by [artifact].
	 */
	fun createArtifact ()
	{
		packageAvailArtifact.create()
	}

	/**
	 * The map of [CreateAvailRoot.name]s to [CreateAvailRoot]s that will be
	 * created when the `initializeAvail` task is run.
	 */
	internal val createRoots: MutableMap<String, CreateAvailRoot> =
		mutableMapOf()

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
	private fun projectRoot(root: AvailRoot)
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
	 * @param availModuleExtensions
	 *   The file extensions that signify files that should be treated as Avail
	 *   modules.
	 * @param entryPoints
	 *   The Avail entry points exposed by this root.
	 * @param description
	 *   An optional description of this root.
	 * @param initializer
	 *   A lambda that accepts the created [AvailRoot] and is executed after
	 *   all roots have been added.
	 */
	@Suppress("Unused")
	fun projectRoot(
		name: String,
		availModuleExtensions: MutableList<String> = mutableListOf("avail"),
		entryPoints: MutableList<String> = mutableListOf(),
		description: String = "",
		initializer: (AvailRoot) -> Unit = {})
	{
		projectRoot(
			AvailRoot(
				name,
				rootsDirectory.relativeLocation(
					name, Scheme.FILE, rootsDirectory.locationType),
				packageAvailArtifact.artifactDigestAlgorithm,
				availModuleExtensions,
				entryPoints,
				TemplateGroup(),
				StylingGroup(),
				description,
				initializer))
	}

	/**
	 * Add an Avail root with the provided name and [URI].
	 *
	 * There is no need to prefix the file scheme, `file://`, if it exists on
	 * the local file system; otherwise the scheme should be prefixed.
	 *
	 * @param name
	 *   The name of the root to add.
	 * @param jarFileName
	 *   The jar's [File.getName].
	 * @param availModuleExtensions
	 *   The file extensions that signify files that should be treated as Avail
	 *   modules.
	 * @param entryPoints
	 *   The Avail entry points exposed by this root.
	 * @param description
	 *   An optional description of this root.
	 * @param initializer
	 *   A lambda that accepts the created [AvailRoot] and is executed after
	 *   all roots have been added.
	 */
	@Suppress("Unused")
	fun rootJar(
		name: String,
		jarFileName: String,
		availModuleExtensions: MutableList<String> = mutableListOf("avail"),
		entryPoints: MutableList<String> = mutableListOf(),
		description: String = "",
		initializer: (AvailRoot) -> Unit = {})
	{
		projectRoot(
			AvailRoot(
				name,
				rootsDirectory.relativeLocation(
					jarFileName, Scheme.JAR, rootsDirectory.locationType),
				packageAvailArtifact.artifactDigestAlgorithm,
				availModuleExtensions,
				entryPoints,
				TemplateGroup(),
				StylingGroup(),
				description,
				initializer))
	}

	/**
	 * Add a [CreateAvailRoot] to be created in the [rootsDirectory].
	 *
	 * @param name
	 *   The [CreateAvailRoot.name].
	 * @param availModuleExtensions
	 *   The file extensions that signify files that should be treated as Avail
	 *   modules.
	 * @param entryPoints
	 *   The Avail entry points exposed by this root.
	 * @param description
	 *   An optional description of this root.
	 * @return
	 *   The created Root.
	 */
	@Suppress("Unused")
	fun createProjectRoot (
		name: String,
		availModuleExtensions: MutableList<String> = mutableListOf("avail"),
		entryPoints: MutableList<String> = mutableListOf(),
		description: String = ""): CreateAvailRoot =
		CreateAvailRoot(
			name,
			rootsDirectory.relativeLocation(
				name, Scheme.FILE, rootsDirectory.locationType),
			packageAvailArtifact.artifactDigestAlgorithm,
			availModuleExtensions,
			entryPoints,
			TemplateGroup(),
			StylingGroup(),
			description
		).apply {
			createRoots[name] = this
			roots[name] = this
		}

	/**
	 * Create a new [AvailProject].
	 *
	 * @return
	 *   An [AvailProject] from this [AvailExtension]'s configuration.
	 */
	fun createProject (): AvailProject =
		AvailProjectV1(
			name,
			true,
			repositoryDirectory,
			LocalSettings(project.rootDir.absolutePath),
			roots = roots.mapValues {
				it.value.createProjectRoot(
					project.name,
					project.rootDir.absolutePath)
			}.toMutableMap())

	/**
	 * Create a printable view of this entire [AvailExtension]'s current
	 * configuration state.
	 */
	val printableConfig: String get() =
		buildString {
			append("\n========================= Avail Configuration")
			append(" =========================")
			rootDependencies.forEach {
				append("\n\tIncluded Library Dependency: \"")
				append(it.name)
				append("\", \"")
				append(it.dependencyString)
				append('"')
			}
			append("\n\tRepository Location: ${repositoryDirectory.fullPathNoPrefix}")
			append("\n\tRoots Location: $rootsDirectory")
			append("\n\tIncluded Roots:")
			roots.values.sorted().forEach {
				append("\n\t\t• ${it.name}: ${it.absolutePath}")
			}
			append("\n\tCreated Roots:")
			createRoots.values.sorted().forEach {
				append(it.configString)
			}
			append("\n====================================")
			append("====================================\n")
		}
}
