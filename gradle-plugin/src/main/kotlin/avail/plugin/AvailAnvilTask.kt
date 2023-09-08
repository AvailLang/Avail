package avail.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskAction
import java.lang.IllegalStateException

/**
 * Builds a fat jar for Anvil and launches it.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
open class AvailAnvilTask: DefaultTask()
{
	/**
	 * The version of the Avail, `org.availlang:avail` to use.
	 */
	@Input
	var version = ""

	/**
	 * The name of the [Configuration] that the dependencies are added to.
	 */
	private val configName = "_configWb$name"

	/**
	 * The [Configuration] for adding [dependencies][Dependency] to be included
	 * in the anvil jar.
	 */
	private val localConfig: Configuration by lazy {
		project.configurations.create(configName)
	}

	/**
	 * Get the active [AvailExtension] from the host [Project].
	 */
	private val availExtension: AvailExtension get() =
		(project.extensions.getByName(AvailPlugin.AVAIL) as AvailExtension)

	/**
	 * The set of VM Options that will be used when the Avail Anvil jar is
	 * run.
	 */
	private val vmOptions = mutableSetOf<String>()

	/**
	 * Add a VM Option that will be used when the Avail Anvil jar is run.
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
	 * Clear all the set VM options used when launching the anvil.
	 */
	@Suppress("unused")
	fun clearVmOptions () { vmOptions.clear() }

	/**
	 * The maximum heap size that will be presented as a VM option upon running
	 * an Avail Anvil.
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
							"for running an Avail Anvil.")
				}
				if (size < 1)
				{
					throw IllegalStateException(
						"`$value` is not a valid number for memory size for " +
							"running an Avail Anvil.")
				}
				field = value
			}
			else
			{
				throw IllegalStateException(
					"`$value` does not have a valid memory size, `$last` for " +
						"running an Avail Anvil.")
			}
		}

	/**
	 * The maximum heap size VM option for running an Avail Anvil.
	 */
	private val heapSize: List<String> get() = listOf("-Xmx$maximumJavaHeap")

	/**
	 * The list of VM options to be used when starting the anvil jar.
	 */
	private val assembledVmOptions: List<String> by lazy {
		vmOptions.toList() + heapSize
	}

	/**
	 * Assembles the fat anvil jar
	 */
	private fun assemble (fullPathToFile: String)
	{
		if (project.file(fullPathToFile).exists()) { return }

		if (version.isEmpty())
		{
			version = availExtension.availVersion
			if (version.isEmpty())
			{
				throw RuntimeException(
					"No version set for Avail in Anvil run task")
			}
		}
		val anvilConfig =
			project.configurations.getByName(ANVIL_INTERNAL_CONFIG)
		val anvilDependency =
			project.dependencies.create(
				"${AvailPlugin.AVAIL_DEP_GRP}:${AvailPlugin.AVAIL}:$version")
		anvilConfig.dependencies.add(anvilDependency)
		println("Building $fullPathToFile")
		project.tasks.create("__wbassm_$name", AvailJarPackager::class.java)
		{
			group = AvailPlugin.AVAIL
			description =
				"Assemble a standalone Anvil jar and run it."
			manifest.attributes["Main-Class"] =
				"avail.project.AvailProjectManagerRunner"
			manifest.attributes["SplashScreen-Image"] =
				"workbench/AvailWBSplash.png"
			val anvilDir = "${project.buildDir}/anvil"
			project.mkdir(anvilDir)
			archiveBaseName.set("anvil")
			archiveVersion.set("")
			destinationDirectory.set(project.file(anvilDir))
			from(
				anvilConfig.resolve().map {
					if (it.isDirectory) it else project.zipTree(it) } +
					localConfig.resolve().map {
						if (it.isDirectory) it
						else project.zipTree(it) })
			duplicatesStrategy = DuplicatesStrategy.INCLUDE
		}.doCopy()
	}

	/**
	 * Launches the [assembled][assemble] anvil jar.
	 */
	private fun launch (fullPathToFile: String)
	{
		project.tasks.create("_345jsjlfa_anvil", JavaExec::class.java)
		{
			group = AvailPlugin.AVAIL
			description =
				"Run the Avail Anvil jar that was assembled " +
					"in the parent task, `assembleAndRunAnvil`."
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
			"${project.buildDir}/anvil/anvil.jar"
		assemble(fullPathToFile)
		launch(fullPathToFile)
	}

	companion object
	{
		/**
		 * The name of the custom [Project] [Configuration] to add the Avail
		 * vm.
		 */
		internal const val ANVIL_INTERNAL_CONFIG = "__internalAvailAnvil"
	}
}
