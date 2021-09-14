package com.avail.plugin

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
	override fun apply(target: Project)
	{
		// Create Custom Project Configurations
		target.configurations.run {
			create(AVAIL_LIBRARY)
			create(WORKBENCH)
		}

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
					// A public key read-only token for Avail downloads.
					password = "gh" + "p_z45vpIzBYdnOol5Q" + "qRCr4x8FSnPaGb3v1y8n"
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

		// Declare Extensions
		val extension =
			target.extensions
				.create(AVAIL, AvailExtension::class.java)
				.apply { init(target, availLibConfig) }

		target.tasks.register("printAvailConfig")
		{
			group = "avail"
			description = "Print the `avail` configuration."
			println(extension.printableConfig)
		}

		target.tasks.register("assembleWorkbench", Jar::class.java)
		{
			group = "avail"
			description = "Assemble a standalone Workbench fat jar."
			manifest.attributes["Main-Class"] =
				"com.avail.environment.AvailWorkbench"
			archiveBaseName.set(WORKBENCH)
			archiveVersion.set("")
			// Explicitly gather up the complete classpath, so that we end
			// up with a JAR including the complete Avail workbench plus... TODO
			from(
				workbenchConfig.resolve().map {
						if (it.isDirectory) it else target.zipTree(it) } +
					extension.workBenchDependencies.map {
						val f = project.file(it)
						if (f.isDirectory) f else target.zipTree(f)})
			duplicatesStrategy = DuplicatesStrategy.INCLUDE
		}
		target.tasks.register("runWorkbench", JavaExec::class.java)
		{
			group = "avail"
			description =
				"Run the Avail Workbench defaulting to include the project's " +
					"Avail roots"
			dependsOn(target.tasks.getByName("assembleWorkbench"))
			workingDir = target.projectDir
			jvmArgs(extension.workbenchVmOptions)
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
		private const val AVAIL_LIBRARY: String = "availLibrary"

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
		const val AVAIL_STRIPE_RELEASE = "1.6.0.20210910.181950"
	}
}
