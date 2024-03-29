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

import org.availlang.artifact.AvailArtifactBuildPlan
import org.availlang.artifact.AvailArtifactMetadata
import org.availlang.artifact.environment.AvailEnvironment
import org.availlang.artifact.environment.location.AvailLibraries
import org.availlang.artifact.environment.location.AvailLocation
import org.availlang.artifact.environment.location.Scheme
import org.availlang.artifact.jar.AvailArtifactJar
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact
import java.io.File

/**
 * `AvailPlugin` represents the Avail Gradle plugin.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class AvailPlugin : Plugin<Project>
{
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

			create(AVAIL).apply {
				isTransitive = false
				isVisible = false
				description =
					"Config for acquiring Avail libraries from maven"
			}
			create(AvailAnvilTask.ANVIL_INTERNAL_CONFIG)
		}

		// Create AvailExtension and attach it to the target host Project
		val extension =
			target.extensions.create(
				AVAIL,
				AvailExtension::class.java,
				target,
				this)

		target.tasks.register("downloadAvailLibraries", DefaultTask::class.java)
		{
			description = "Downloads all `avail` libraries in the " +
				"`dependencies` block to `~/.avail/libraries` making them " +
				"available for Avail project use. This is automatically run " +
				"on Gradle refresh. Libraries are re-downloaded and " +
				"overwritten with each run."

			println(buildString {
				target.configurations.getByName(AVAIL)
					.resolvedConfiguration.resolvedArtifacts.forEach {
						downloadLib(it).let { metadata ->
							append("Downloaded: ")
							append(metadata.location.path)
							metadata.manifest.roots.values.forEach { m ->
								append("\n\tRoot name in jar: '")
								append(m.name)
								append("'\n\t\t")
								append(m.description)
							}
						}
					}
			})
		}

		target.tasks.register("setupProject", DefaultTask::class.java)
		{
			group = AVAIL
			description = "Initialize the Avail Project. This sets up the " +
				"project according to the configuration in the `avail` " +
				"extension block. At the end of this task, all root " +
				"initializers (lambdas) that were added will be run."
			dependsOn("downloadAvailLibraries")
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
					downloadLib(it)
				}
				extension.createRoots.values.forEach {
					it.create(extension.moduleHeaderCommentBody)
				}
				extension.roots.values.forEach { it.action(it) }
				val availProject = extension.createProject()
				project.rootDir.resolve("${extension.name}.json")
					.writeText(availProject.fileContent)
				val configDir = project.rootDir.resolve(
					AvailEnvironment.projectConfigDirectory)
				val projectConfigDir = configDir.resolve(availProject.name)
				projectConfigDir.mkdirs()
				projectConfigDir
					.resolve(".gitignore")
					.writeText("/**/settings-local.json")
				val buildPlan = extension.buildPlan
				projectConfigDir
					.resolve(AvailArtifactBuildPlan.ARTIFACT_PLANS_FILE)
					.writeText(AvailArtifactBuildPlan
						.fileContent(listOf(buildPlan)))
			}
		}

		target.tasks.register(
			"anvil", AvailAnvilTask::class.java)
		{
			// Create Dependencies
			group = AVAIL
			description =
				"Assembles an Avail VM fat jar in `build/anvil/anvil.jar` and " +
					"uses it to run Anvil."
			maximumJavaHeap = "6g"
			vmOption("-ea")
			vmOption("-XX:+UseCompressedOops")
			vmOption("-DavailDeveloper=true")
		}

		target.tasks.register("printAvailConfig")
		{
			group = AVAIL
			description =
				"Print the Avail configuration collected from the `avail` " +
					"extension."

			println(extension.printableConfig(
				target.configurations.getByName(AVAIL_LIBRARY)
					.resolvedConfiguration.resolvedArtifacts.map {
						downloadLib(it)
					}.toList()))
		}

		target.tasks.register(
			"availArtifactJar", PackageAvailArtifactTask::class.java)
		{
			dependsOn("checkProject")
			dependsOn("build")
		}
	}

	/**
	 * Download the provided [ResolvedArtifact] to the
	 * [AvailEnvironment.availHomeLibs] directory and answer the
	 * [AvailArtifactMetadata] for the downloaded artifact.
	 */
	private fun downloadLib(
		artifact: ResolvedArtifact
	): AvailArtifactMetadata
	{
		val grpPath = artifact.moduleVersion.id.group
			.replace(".", File.separator)
		val targetDir = AvailEnvironment.availHomeLibs +
			"${File.separator}$grpPath${File.separator}"
		File(targetDir).mkdirs()
		val jarFile = File("$targetDir${artifact.file.name}")
		artifact.file.apply {
			copyTo(jarFile, true)
		}
		return AvailArtifactMetadata.fromJar(
			jarFile.toURI(),
			AvailLibraries(
				"$grpPath${File.separator}${artifact.file.name}",
				Scheme.JAR,
				null))
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
		 * The name of the custom [Project] [Configuration], `availLibrary`,
		 * where avail libraries are added.
		 */
		internal const val AVAIL_LIBRARY: String = "availLibrary"

		/**
		 * The string "avail" that is used to name the [AvailExtension] for the
		 * hosting [Project].
		 */
		internal const val AVAIL = "avail"
	}
}
