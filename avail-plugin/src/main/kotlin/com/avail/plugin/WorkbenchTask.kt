package com.avail.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input

/**
 * `WorkbenchTask` is a task that assembles a Workbench fatjar and runs it.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
open class WorkbenchTask: DefaultTask()
{
	/**
	 * `true` indicates if as Workbench jar created by this task from a previous
	 * run exists, it will be deleted and rebuilt. `false` indicates any
	 * preexisting Workbench jar will be preserved and launched.
	 */
	@Input
	var rebuildWorkbenchJar: Boolean = false

	/**
	 * The list of additional dependencies to be bundled in with the fat jar
	 * of the Workbench. These strings should be absolute paths to an existing
	 * jar.
	 */
	private val workBenchDependencies = mutableSetOf<String>()

	/**
	 * Add a dependency jar file to be included in the workbench jar.
	 *
	 * All jars added in this way will be bundled into the assembled workbench
	 * jar.
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
	 * The map of [AvailRoot.name] to [AvailRoot] that will be included in this
	 * VM option,
	 */
	private val roots = mutableMapOf<String, AvailRoot>()

	companion object
	{
		/**
		 * The static list of Workbench VM arguments.
		 */
		private val standardWorkbenchVmOptions = listOf(
			"-ea", "-XX:+UseCompressedOops", "-Xmx12g", "-DavailDeveloper=true")

		/**
		 * The VM Option `-DavailDeveloper=true`
		 */
		const val vmOptionAvailDeveloper = "-DavailDeveloper=true"

		/** The VM option `-ea`. */
		const val vmOptionEA = "-ea"

		/** The VM option `-XX:+UseCompressedOops`. */
		const val vmOptionUseCompressedOops = "-XX:+UseCompressedOops"
	}
}
