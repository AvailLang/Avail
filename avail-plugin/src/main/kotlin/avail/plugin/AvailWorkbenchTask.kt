/*
 * AvailWorkbenchTask.kt
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

import avail.plugin.AvailPlugin.Companion.WORKBENCH
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskAction
import java.lang.IllegalStateException
import java.net.URI

/**
 * `AvailWorkbenchTask` enables a custom run of the workbench.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
open class AvailWorkbenchTask: DefaultTask()
{
	/**
	 * The base name to give to the created jar.
	 */
	@Input
	var workbenchJarBaseName: String = name

	/**
	 * `true` indicates if as Workbench jar created by this task from a previous
	 * run exists, it will be deleted and rebuilt. `false` indicates any
	 * preexisting Workbench jar will be preserved and launched.
	 */
	@Input
	var rebuildWorkbenchJar: Boolean = false

	/**
	 * The name of the [Configuration] that the dependencies are added to.
	 */
	private val configName = "_configWb$name"

	/**
	 * The [Configuration] for adding [dependencies][Dependency] to be included
	 * in the workbench jar.
	 */
	private val localConfig: Configuration by lazy {
		project.configurations.create(configName)
	}

	/**
	 * Get the active [AvailExtension] from the host [Project].
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	private val availExtension: AvailExtension get() =
		(project.extensions.getByName(AvailPlugin.AVAIL) as AvailExtension)

	/**
	 * The directory location where the Avail roots repositories exist for this
	 * workbench instance. It defaults to what is set in the `avail` extension,
	 * [AvailExtension.repositoryDirectory].
	 */
	@Input
	var repositoryDirectory: String = availExtension.repositoryDirectory

	/**
	 * The list of additional local jar dependencies to be bundled in with the
	 * fat jar of the Workbench. These strings should be absolute paths to an
	 * existing jar.
	 */
	private val workbenchJarDependencies = mutableSetOf<String>()

	/**
	 * Add a local jar file to be included in the workbench jar.
	 *
	 * All jars added in this way will be bundled into the assembled workbench
	 * jar.
	 *
	 * @param dependency
	 *   The String path to the jar that must be added.
	 */
	@Suppress("unused")
	fun workbenchLocalJarDependency(dependency: String)
	{
		workbenchJarDependencies.add(dependency)
	}

	/**
	 * Add a dependency to be included in the jar.
	 *
	 * @param dependency
	 *   The string that identifies the dependency such as:
	 *   `org.package:myLibrary:2.3.1`.
	 */
	@Suppress("unused")
	fun dependency (dependency: String)
	{
		localConfig.dependencies.add(project.dependencies.create(dependency))
	}

	fun projectDependency (dependency: String)
	{
//		val dep = ProjectDependency
	}

	/**
	 * Add a dependency to be included in the jar.
	 *
	 * @param dependency
	 *   The [Dependency] to add.
	 */
	@Suppress("unused")
	fun dependency (dependency: Dependency)
	{
		localConfig.dependencies.add(dependency)
	}

	/**
	 * The set of VM Options that will be used when the Avail Workbench jar is
	 * run.
	 */
	private val vmOptions = mutableSetOf<String>()

	/**
	 * Add a VM Option that will be used when the Avail Workbench jar is run.
	 *
	 * All options added via this function will be used in the running of the
	 * Jar.
	 *
	 * @param option
	 *   The VM option to add.
	 */
	@Suppress("unused")
	fun vmOption(option: String)
	{
		vmOptions.add(option)
	}

	/**
	 * Clear all the set VM options used when launching the workbench.
	 */
	@Suppress("unused")
	fun clearVmOptions () { vmOptions.clear() }

	/**
	 * The maximum heap size that will be presented as a VM option upon running
	 * an Avail Workbench.
	 *
	 * `-Xmx` will be prefixed with the provided option. You must provide a
	 * value of the format:
	 * `<size>[g|G|m|M|k|K]`
	 *
	 * An example of a valid value is: `"6g"`.
	 */
	@Input
	var maximumJavaHeap: String = "6g"
		set(value)
		{
			val last = value.last()
			val sizeString = value.split(last).first()
			if (setOf('g', 'G', 'm', 'M', 'k', 'K').contains(value.last()))
			{
				val size = try
				{
					sizeString.toInt()
				}
				catch (e: NumberFormatException)
				{
					throw IllegalStateException(
						"`$value` is not a valid number for memory size " +
							"for running an Avail Workbench.")
				}
				if (size < 1)
				{
					throw IllegalStateException(
						"`$value` is not a valid number for memory size for " +
							"running an Avail Workbench.")
				}
				field = value
			}
			else
			{
				throw IllegalStateException(
					"`$value` does not have a valid memory size, `$last` for " +
						"running an Avail Workbench.")
			}
		}

	/**
	 * The maximum heap size VM option for running an Avail Workbench.
	 */
	private val heapSize: List<String> get() = listOf("-Xmx$maximumJavaHeap")

	/**
	 * The map of [AvailRoot.name] to [AvailRoot] that will be included in this
	 * VM option,
	 */
	private val roots = mutableMapOf<String, AvailRoot>()

	/**
	 * Add an Avail root with the provided name and [URI] to be added to the
	 * workbench when it is launched..
	 *
	 * There is no need to prefix the file scheme, `file://`, if it exists on
	 * the local file file system; otherwise the scheme should be prefixed.
	 *
	 * @param name
	 *   The name of the root to add.
	 * @param uri
	 *   The uri path to the root.
	 */
	@Suppress("Unused")
	fun root(name: String, uri: String)
	{
		roots[name] = AvailRoot(name, uri) { }
	}

	/**
	 * The list of VM options to be used when starting the workbench jar.
	 */
	private val assembledVmOptions: List<String> by lazy {
		vmOptions.toList() + heapSize + buildString {
			if (roots.isNotEmpty())
			{
				append("-DavailRoots=")
				append(roots.values.sorted()
					.joinToString(";") { it.rootString })
			}
		} + listOf("-Davail.repositories=$repositoryDirectory")
	}

	/**
	 * Assembles the custom workbench jar if it doesn't exist or
	 * [rebuildWorkbenchJar] is `true`.
	 */
	private fun assemble (fullPathToFile: String)
	{
		if (!rebuildWorkbenchJar)
		{
			if (project.file(fullPathToFile).exists()) { return }
		}
		println("Building $fullPathToFile")
		project.tasks.create("__wbassm_$name", AvailJarPackager::class.java)
		{
			group = AvailPlugin.AVAIL
			description =
				"Assemble a standalone Workbench fat jar and run it. " +
					"This task will require `initializeAvail` to be run " +
					"once to initialize the environment before building " +
					"the workbench.jar that will then be run using " +
					"configurations from the `avail` extension."
			manifest.attributes["Main-Class"] =
				"avail.environment.AvailWorkbench"
			project.mkdir("${project.buildDir}/$WORKBENCH")
			archiveBaseName.set(workbenchJarBaseName)
			archiveVersion.set("")
			into(project.file("${project.buildDir}/$WORKBENCH"))
			val workbenchConfig =
				project.configurations.getByName(
					AvailPlugin.WORKBENCH_INTERNAL_CONFIG)
			// Explicitly gather up the dependencies, so that we end up with
			// a JAR including the complete Avail workbench plus any workbench
			// dependencies explicitly stated in the Avail extension.
			// Additionally anything added to the "workbench" configuration in
			// the host project's dependencies section of the build script will
			// also be added.
			from(
				workbenchConfig.resolve().map {
					if (it.isDirectory) it else project.zipTree(it) } +
					localConfig.resolve().map {
						if (it.isDirectory) it
						else project.zipTree(it) } +
					workbenchJarDependencies.map {
						val f = project.file(it)
						if (f.isDirectory) f else project.zipTree(f)})
			duplicatesStrategy = DuplicatesStrategy.INCLUDE
		}.doCopy()
	}

	/**
	 * Launches the [assembled][assemble] workbench jar.
	 */
	private fun launch (fullPathToFile: String)
	{
		project.tasks.create("__wbrun_$name", JavaExec::class.java)
		{
			group = AvailPlugin.AVAIL
			description =
				"Run the Avail Workbench jar that was assembled " +
					"in the parent task, `assembleAndRunWorkbench`."
			workingDir = project.projectDir
			jvmArgs(assembledVmOptions)
			classpath = project.files(fullPathToFile)
			println(buildString {
				append("Launching: $fullPathToFile with VM options:")
				assembledVmOptions.forEach {
					append("\n\t • $it")
				}
			})
		}.exec()
	}

	/**
	 * This is the core action that is performed.
	 */
	@TaskAction
	protected fun run ()
	{
		val fullPathToFile =
			"${project.buildDir}/$WORKBENCH/$workbenchJarBaseName.jar"
		assemble(fullPathToFile)
		launch(fullPathToFile)
	}
}
