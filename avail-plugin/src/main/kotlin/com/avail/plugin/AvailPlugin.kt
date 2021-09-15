package com.avail.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.JavaExec
import org.gradle.jvm.tasks.Jar

/**
 * `AvailPlugin` represents the Avail Gradle plugin.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class AvailPlugin : Plugin<Project>
{
	/**
	 * The directory location where the Avail roots exist. This will be set
	 * during [AvailExtension.initExtension]. If [AvailExtension.rootsDirectory]
	 * is explicitly set by a user `internalRootsDir` will use that value
	 * otherwise it will be set to the default value. This is done as there is
	 * no guarantee of having an initialized value for `rootsDirectory` when
	 * used due to Gradle's initialization mechanism.
	 */
	internal var internalRootsDir: String = ""

	/**
	 * The directory location where the Avail roots exist. This will be set
	 * during [AvailExtension.initExtension]. If
	 * [AvailExtension.repositoryDirectory] is explicitly set by a user `v` will
	 * use that value otherwise it will be set to the default value. This is
	 * done as there is no guarantee of having an initialized value for
	 * `repositoryDirectory` when used due to Gradle's initialization mechanism.
	 */
	internal var internalReposDir: String = ""

	/**
	 * The list of [AvailRoot]s that are statically known to be included in the
	 * [roots directory][internalRootsDir].
	 */
	internal val roots: MutableSet<AvailRoot> = mutableSetOf()

	/**
	 * The set of [CreateAvailRoot]s that will be created when the
	 * `initializeAvail` task is run.
	 */
	internal val createRoots: MutableSet<CreateAvailRoot> = mutableSetOf()

	/**
	 * The list of additional dependencies to be bundled in with the fat jar
	 * of the Workbench.
	 */
	internal val workBenchDependencies = mutableSetOf<String>()

	/**
	 * Raw copyright file header. Will be wrapped in comment along with file
	 * name. If copyright is empty (*default*), will only provide the file name
	 * in the header.
	 */
	internal var internalCopyrightBody: String = ""

	/**
	 * The absolute path to a file containing the text for the
	 * [AvailExtension.copyrightBody]. This will be ignored if
	 * [AvailExtension.copyrightBodyFile] is not empty.
	 */
	internal var internalCopyrightBodyFile: String = ""

	/**
	 * Create a printable view of this entire [AvailExtension]'s current
	 * configuration state.
	 */
	private fun printableConfig(ext: AvailExtension): String =
		buildString {
			append("\n================== Avail Configuration ==================\n")
			append("\tAvail Version: ${AVAIL_STRIPE_RELEASE}\n")
			append("\tRepository Location: ${ext.reposDir}\n")
			append("\tRoots Location: ${ext.rootsDir}\n")
			append("\tIncluded Roots:")
			roots.forEach { append("\n\t\t-${it.name}: ${it.uri}") }
			append("\n\tIncluded Workbench Dependencies:")
			workBenchDependencies.forEach { append("\n\t\t$it") }
			append("\tWorkbench VM Options:")
			workbenchVmOptions(ext).forEach { append("\n\t\t-$it") }
			append("\n=========================================================\n")
		}

	/**
	 * The VM Options associated with running the workbench.
	 */
	private fun workbenchVmOptions(ext: AvailExtension): List<String> =
		standardWorkbenchVmOptions + buildString {
			if (ext.useAvailStdLib)
			{
				roots.add(AvailRoot(
					AVAIL,
					"jar:${ext.rootsDir}/$AVAIL_STDLIB_JAR_NAME"))
			}
			if (roots.isNotEmpty())
			{
				append("-DavailRoots=")
				append(roots.joinToString(";") { it.rootString })
			}
		} + listOf("-Davail.repositories=${ext.reposDir}")

	override fun apply(target: Project)
	{
		// Create Custom Project Configurations
		target.configurations.run {
			create(AVAIL_LIBRARY)
			create(WORKBENCH)
		}

		// Create AvailExtension and attach it to the target host Project
		val extension =
			target.extensions.create(AVAIL, AvailExtension::class.java, this, target)

		// Set up repositories
		target.repositories.run {
			mavenCentral()
			maven {
				setUrl("https://maven.pkg.github.com/AvailLang/Avail")
				metadataSources {
					mavenPom()
					artifact()
				}
				credentials {
					username = "anonymous"
					// A public key read-only token for Avail downloads.
					password = "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"
				}
			}
		}

		// Create Dependencies
		val workbenchDependency =
			target.dependencies.create("$WORKBENCH_DEP:$AVAIL_STRIPE_RELEASE:all")
		val coreDependency: Dependency =
			target.dependencies.create("$AVAIL_CORE:$AVAIL_STRIPE_RELEASE")
		val stdlibDependency =
			target.dependencies.create("$AVAIL_STDLIB_DEP:$AVAIL_STRIPE_RELEASE")

		// Obtain Project Configurations
		val availLibConfig =
			target.configurations.getByName(AVAIL_LIBRARY)
		val workbenchConfig =
			target.configurations.getByName(WORKBENCH)
		val implementationConfig =
			target.configurations.getByName(IMPLEMENTATION)

		// Add Dependencies
		availLibConfig.dependencies.add(stdlibDependency)
		workbenchConfig.dependencies.add(workbenchDependency)
		implementationConfig.dependencies.add(coreDependency)

		val basicSetup =
			target.tasks.register("_basicAvailSetup")
			{
				group = "avail"
				description = "An internal task that performs basic Avail " +
					"setup actions after the `avail` extension is " +
					"initialized/populated by gradle. This task is meant to " +
					"be run by a user of this plugin."
				extension.initExtension(project)
			}

		target.tasks.register("initializeAvail", DefaultTask::class.java)
		{
			group = AVAIL
			description = "Initialize the Avail Project. This sets up the " +
				"project according to the configuration in the `avail` " +
				"extension block. At the end of this task, all root " +
				"initializers (lambdas) that were added will be run."

			// Putting the call into a `doLast` block forces this to only be
			// calculated when
			dependsOn(basicSetup)
			doLast {
				if (internalCopyrightBody.isEmpty()
					&& internalCopyrightBodyFile.isNotEmpty())
				{
					internalCopyrightBody =
						project.file(internalCopyrightBodyFile).readText()
				}
				createRoots.forEach {
					it.create(project, this@AvailPlugin)
				}
				roots.forEach { it.action(it) }
			}
		}

		target.tasks.register("printAvailConfig")
		{
			group = AVAIL
			description = "Print the `avail` configuration. Depends on " +
				"`_basicAvailSetup`."
			dependsOn(basicSetup)
			println(printableConfig(extension))
		}

		val assembleWorkbench =
			target.tasks.register("assembleWorkbench", Jar::class.java)
			{
				group = AVAIL
				description = "Assemble a standalone Workbench fat jar. " +
					"This task will be run before `runWorkbench` to build the " +
					"workbench.jar that will be run. Depends on " +
					"`_basicAvailSetup`."
				dependsOn(basicSetup)
				manifest.attributes["Main-Class"] =
					"com.avail.environment.AvailWorkbench"
				archiveBaseName.set(WORKBENCH)
				archiveVersion.set("")
				// Explicitly gather up the dependencies, so that we end up with
				// a JAR including the complete Avail workbench plus
				from(
					workbenchConfig.resolve().map {
							if (it.isDirectory) it else target.zipTree(it) } +
						workBenchDependencies.map {
							val f = project.file(it)
							if (f.isDirectory) f else target.zipTree(f)})
				duplicatesStrategy = DuplicatesStrategy.INCLUDE
			}
		target.tasks.register("runWorkbench", JavaExec::class.java)
		{
			group = AVAIL
			description =
				"Run the Avail Workbench defaulting to include the project's " +
					"Avail roots. Depends on `assembleWorkbench`."
			dependsOn(assembleWorkbench)
			workingDir = target.projectDir
			jvmArgs(workbenchVmOptions(extension))
			classpath =
				target.files("${target.buildDir}/libs/$WORKBENCH_JAR")
		}
	}
	companion object
	{
		/**
		 * The dependency group-artifact String dependency that points to the
		 * published Avail Workbench Jar. This is absent the version.
		 */
		private const val WORKBENCH_DEP: String =
			"org.availlang:avail-workbench"

		/**
		 * The dependency group-artifact String dependency that points to the
		 * published Avail Standard Library Jar. This is absent the version.
		 */
		private const val AVAIL_STDLIB_DEP: String =
			"org.availlang:avail-stdlib"

		/**
		 * The dependency group-artifact String dependency that points to the
		 * published Avail Core Jar. This is absent the version.
		 */
		private const val AVAIL_CORE: String = "org.availlang:avail-core"

		/**
		 * The name of the [Project] [Configuration] where `implementation`
		 * dependencies are added.
		 */
		private const val IMPLEMENTATION: String = "implementation"

		/**
		 * The name of the custom [Project] [Configuration], `availLibrary`,
		 * where only the [AVAIL_STDLIB_DEP] is added.
		 */
		internal const val AVAIL_LIBRARY: String = "availLibrary"

		/**
		 * The rename that is applied to the [AVAIL_STDLIB_DEP] Jar if it is
		 * copied into the roots directory given
		 * [AvailExtension.useAvailStdLib] is `true`.
		 */
		internal const val AVAIL_STDLIB_JAR_NAME: String = "avail-stdlib.jar"

		/**
		 * The string "avail" that is used to name the [AvailExtension] for the
		 * hosting [Project].
		 */
		internal const val AVAIL = "avail"

		/**
		 * The name of the custom [Project] [Configuration], `workbench`,
		 * where only the [WORKBENCH_DEP] is added.
		 */
		internal const val WORKBENCH = "workbench"

		/**
		 * The name to apply to the built workbench jar.
		 */
		internal const val WORKBENCH_JAR = "workbench.jar"

		/**
		 * The stripe release version of avail jars:
		 *  * `avail-core`
		 *  * `avail-workbench`
		 *  * `avail-stdlib`
		 *
		 *  This represents the version of this plugin.
		 */
		internal const val AVAIL_STRIPE_RELEASE = "1.6.0.20210910.181950"

		/**
		 * The static list of Workbench VM arguments.
		 */
		private val standardWorkbenchVmOptions = listOf(
			"-ea", "-XX:+UseCompressedOops", "-Xmx12g", "-DavailDeveloper=true")
	}
}
