/*
 * AvailPlugin.kt
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

import org.availlang.artifact.environment.AvailEnvironment
import org.availlang.artifact.environment.project.AvailProject
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.UnknownConfigurationException
import java.io.File

/**
 * `AvailPlugin` represents the Avail Gradle plugin.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class AvailPlugin : Plugin<Project>
{
	internal var latestAvailStdLib = ""
	internal var latestAvail = ""
	internal var hasAvailImport = false

	init
	{
		// Make sure .avail is set up in the user's home directory.
		AvailEnvironment.optionallyCreateAvailUserHome()
	}

	override fun apply(target: Project)
	{
		// Create Custom Project Configurations
		target.configurations.run {
			// Config for acquiring Avail library dependencies
			create(AVAIL_LIBRARY).apply {
				isTransitive = false
				isVisible = false
				description =
					"Config for acquiring published Avail library dependencies"
			}

			// Config for checking latest versions of Avail published libraries.
			create(CHECK_CONFIG).apply {
				isTransitive = false
				isVisible = false
				description =
					"Config for checking latest versions of Avail published " +
						"org.availlang libraries."
				dependencies.add(AvailStandardLibrary().dependency(target))
				dependencies.add(
					target.dependencies.create("$AVAIL_DEP_GRP:$AVAIL:+"))
			}
		}

		// Create AvailExtension and attach it to the target host Project
		val extension =
			target.extensions.create(
				AVAIL,
				AvailExtension::class.java,
				target,
				this)

		target.tasks.register("checkProject", DefaultTask::class.java)
		{
			group = AVAIL
			description = "This checks the configured Avail project and " +
				"displays warnings, errors, or recomendations"

			doLast {
				checkProject(target, extension)
			}
		}

		target.tasks.register("initializeAvail", DefaultTask::class.java)
		{
			group = AVAIL
			description = "Initialize the Avail Project. This sets up the " +
				"project according to the configuration in the `avail` " +
				"extension block. At the end of this task, all root " +
				"initializers (lambdas) that were added will be run."

			val availLibConfig =
				target.configurations.getByName(AVAIL_LIBRARY)
			extension.rootDependencies.forEach {
				val dependency = it.dependency(target)
				availLibConfig.dependencies.add(dependency)
			}

			// Putting the call into a `doLast` block forces this to only be
			// executed when explicitly run.
			doLast {
				project.mkdir(extension.rootsDirectory.fullPath)
				project.mkdir(extension.repositoryDirectory.fullPath)
				availLibConfig.resolvedConfiguration.resolvedArtifacts.forEach {
					val grpPath =
						it.moduleVersion.id.group.replace(".", File.separator)
					val targetDir =
						"${AvailEnvironment.availHomeLibs}${File.separator}$grpPath${File.separator}"
					File(targetDir).mkdirs()
					it.file.apply {
						copyTo(File("$targetDir$name"), true)
					}
				}
				extension.createRoots.values.forEach {
					it.create(extension.moduleHeaderCommentBody)
				}
				extension.roots.values.forEach { it.action(it) }
				checkProject(target, extension)
			}
		}

		target.tasks.register("printAvailConfig")
		{
			group = AVAIL
			description =
				"Print the Avail configuration collected from the `avail` " +
					"extension."
			dependsOn("initializeAvail")
			println(extension.printableConfig)
		}

		target.tasks.register(
			"availArtifactJar", PackageAvailArtifactTask::class.java)
		{
			dependsOn("checkProject")
			dependsOn("build")
		}

		target.tasks.register(
			"createProjectFile", CreateAvailProjectFileTask::class.java)
		{
			group = AVAIL
			description = "Creates a new AvailProject file based on the " +
				"configuration of the AvailExtension."
			dependsOn("initializeAvail")

			doLast {
				val availProject = extension.createProject()
				project.rootDir.resolve(AvailProject.CONFIG_FILE_NAME)
					.writeText(availProject.fileContent)
			}
		}
	}

	/**
	 * Check the Avail Project Configuration for any problems or
	 * recommendations.
	 *
	 * @param project
	 *   The [Project] to use.
	 */
	private fun checkProject(project: Project, extension: AvailExtension)
	{
		val checkConfig =
			project.configurations.getByName(CHECK_CONFIG)
		val resolvedConfig = checkConfig.resolvedConfiguration
		val resolvedDependencies =
			resolvedConfig.firstLevelModuleDependencies
		resolvedDependencies.forEach {
			if (it.moduleName == AVAIL)
			{
				latestAvail = it.moduleVersion
			}
			if (it.moduleName == AVAIL_STDLIB_DEP_ARTIFACT_NAME)
			{
				latestAvailStdLib = it.moduleVersion
			}
		}

		try
		{
			project.configurations.getByName("implementation")
				.dependencies.forEach { d ->
					if (d.group == AVAIL_DEP_GRP && d.name == AVAIL)
					{
						hasAvailImport = true
						d.version?.let {
							if (latestAvail.isNotEmpty())
							{
								val setV = AvailVersion(it)
								val latestV = AvailVersion(latestAvail)
								if (latestV > setV)
								{
									println(
										"A newer version of Avail is " +
											"available: " +
											"$AVAIL_DEP_GRP:$AVAIL:$latestAvail")
								}
							}
						}
					}
				}
		}
		catch (e: UnknownConfigurationException)
		{
			// Do nothing
		}
		try
		{
			project.configurations.getByName("api")
				.dependencies.forEach { d ->
					if (d.group == AVAIL_DEP_GRP && d.name == AVAIL)
					{
						hasAvailImport = true
						d.version?.let {
							if (latestAvail.isNotEmpty())
							{
								val setV = AvailVersion(it)
								val latestV = AvailVersion(latestAvail)
								if (latestV > setV)
								{
									println(
										"A newer version of Avail is " +
											"available: " +
											"$AVAIL_DEP_GRP:$AVAIL:$latestAvail")
								}
							}
						}
					}
				}
		}
		catch (e: UnknownConfigurationException)
		{
			// Do nothing
		}
		if (!hasAvailImport)
		{
			System.err.println()
			println(
				"WARNING: No Avail dependency. Consider adding " +
					"`implementation(" +
					"\"$AVAIL_DEP_GRP:$AVAIL:$latestAvail\")` to the " +
					"`dependencies` section of the build script.")
		}
		if (extension.usesStdLib)
		{
			val currentV = extension.availStandardLibrary.version
			if (latestAvailStdLib.isNotEmpty())
			{
				val setV = AvailStdLibVersion(currentV)
				val latestV = AvailStdLibVersion(latestAvailStdLib)
				if (latestV > setV)
				{
					println(
						"RECOMMENDATION: A newer version of the Avail Standard " +
							"Library is available: " +
							"$AVAIL_STDLIB_DEP:$latestAvailStdLib")
				}
			}
		}
	}

	companion object
	{
		/**
		 * The dependency group-artifact String dependency that points to the
		 * published Avail Standard Library Jar. This is absent the version.
		 */
		internal const val AVAIL_DEP_GRP: String = "org.availlang"

		/**
		 * The dependency group-artifact String dependency that points to the
		 * published Avail Standard Library Jar. This is absent the version.
		 */
		internal const val AVAIL_STDLIB_DEP_ARTIFACT_NAME: String =
			"avail-stdlib"

		/**
		 * The dependency group-artifact String dependency that points to the
		 * published Avail Standard Library Jar. This is absent the version.
		 */
		internal const val AVAIL_STDLIB_DEP: String =
			"$AVAIL_DEP_GRP:$AVAIL_STDLIB_DEP_ARTIFACT_NAME"

		/**
		 * The name of the custom [Project] [Configuration], `availLibrary`,
		 * where only the [AVAIL_STDLIB_DEP] is added.
		 */
		internal const val AVAIL_LIBRARY: String = "availLibrary"

		/**
		 * The string "avail" that is used to name the [AvailExtension] for the
		 * hosting [Project].
		 */
		internal const val AVAIL = "avail"

		/**
		 * The name of the configuration used to check the latest published
		 * Avail libraries.
		 */
		internal const val CHECK_CONFIG = "z017f99c2454b49bcbaedc98aa5cbb39b"
	}
}
