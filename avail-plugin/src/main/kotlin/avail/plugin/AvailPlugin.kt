/*
 * AvailPlugin.kt
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

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import java.io.File
import java.util.Properties

/**
 * `AvailPlugin` represents the Avail Gradle plugin.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class AvailPlugin : Plugin<Project>
{
	/**
	 * The release version of the Avail dependencies to target. This also will
	 * represent the version of the released plugin.
	 */
	internal var releaseVersion: String

	init
	{
		// Get the release version stripe for all Avail resources to be acquired
		// as dependencies.
		val propsFile = javaClass.getResourceAsStream(releaseVersionFile)
		val props = Properties()
		props.load(propsFile)
		this.releaseVersion = props.getProperty("releaseVersion")
	}

	override fun apply(target: Project)
	{
		// Create Custom Project Configurations
		target.configurations.run {
			create(AVAIL_LIBRARY)
			create(WORKBENCH_INTERNAL_CONFIG)
		}

		// Create AvailExtension and attach it to the target host Project
		val extension =
			target.extensions.create(
				AVAIL,
				AvailExtension::class.java,
				target,
				this)

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
					password =
						"gh" + "p_z45vpIzBYdnOol5Q" + "qRCr4x8FSnPaGb3v1y8n"
				}
			}
		}

		// Create Dependencies
		val workbenchDependency =
			target.dependencies.create("$WORKBENCH_DEP:$releaseVersion:all")
		val coreDependency: Dependency =
			target.dependencies.create("$AVAIL_CORE:$releaseVersion")
		val stdlibDependency =
			target.dependencies.create("$AVAIL_STDLIB_DEP:$releaseVersion")

		// Obtain Project Configurations
		val availLibConfig =
			target.configurations.getByName(AVAIL_LIBRARY)
		val workbenchConfig =
			target.configurations.getByName(WORKBENCH_INTERNAL_CONFIG)
		val implementationConfig =
			target.configurations.getByName(IMPLEMENTATION)
//		val apiConfig =
//			target.configurations.getByName(API)

		// Add Dependencies
		workbenchConfig.dependencies.add(workbenchDependency)
		implementationConfig.dependencies.add(coreDependency)

		target.tasks.register("initializeAvail", DefaultTask::class.java)
		{
			group = AVAIL
			description = "Initialize the Avail Project. This sets up the " +
				"project according to the configuration in the `avail` " +
				"extension block. At the end of this task, all root " +
				"initializers (lambdas) that were added will be run."

			if (extension.useAvailStdLib)
			{
				availLibConfig.dependencies.add(stdlibDependency)
				extension.root(extension.availStandardLibrary!!.root(
					extension.rootsDirectory))
			}
			// Putting the call into a `doLast` block forces this to only be
			// executed when explicitly run.
			doLast {
				project.mkdir(extension.rootsDirectory)
				project.mkdir(extension.repositoryDirectory)

				availLibConfig.resolve().forEach {
					val targetFile =
						if (extension.useAvailStdLib
							&& it.name.contains(AVAIL_STDLIB_BASE_JAR_NAME))
						{
							extension.availStandardLibrary!!.jar(
								extension.rootsDirectory)
						}
						else
						{
							File("${extension.rootsDirectory}/$it.name")
						}
					it.copyTo(targetFile, true)
				}
				extension.createRoots.values.forEach {
					it.create(project, extension)
				}
				extension.roots.values.forEach { it.action(it) }
			}
		}

		target.tasks.register("printAvailConfig")
		{
			group = AVAIL
			description =
				"Print the Avail configuration collected from the `avail` " +
					"extension."
			println(extension.printableConfig)
		}

		target.tasks.register(
			"assembleAndRunWorkbench", AvailWorkbenchTask::class.java)
		{
			group = AVAIL
			description = "My custom workbench build."
			workbenchJarBaseName = WORKBENCH
			extension.roots.forEach { (t, u) ->  root(t, u.uri)}
			maximumJavaHeap = "6g"
			vmOption("-ea")
			vmOption("-XX:+UseCompressedOops")
			vmOption("-DavailDeveloper=true")
		}

		target.tasks.register("packageRoots", DefaultTask::class.java)
		{
			group = AVAIL
			description = "Package the roots created in the Avail extension " +
				"that have been provided an AvailLibraryPackageContext"
			doLast {
				extension.createRoots.values.forEach {
					it.packageLibrary(target)
				}
			}
		}
	}
	companion object
	{
		/**
		 * The dependency group-artifact String dependency that points to the
		 * published Avail Workbench Jar. This is absent the version.
		 */
		private const val WORKBENCH_DEP: String =
			"avail:avail-workbench"

		/**
		 * The dependency group-artifact String dependency that points to the
		 * published Avail Standard Library Jar. This is absent the version.
		 */
		internal const val AVAIL_STDLIB_DEP: String =
			"avail:avail-stdlib"

		/**
		 * The dependency group-artifact String dependency that points to the
		 * published Avail Core Jar. This is absent the version.
		 */
		private const val AVAIL_CORE: String = "avail:avail-core"

		/**
		 * The name of the [Project] [Configuration] where `implementation`
		 * dependencies are added.
		 */
		private const val IMPLEMENTATION: String = "implementation"

		/**
		 * The name of the [Project] [Configuration] where `api`
		 * dependencies are added.
		 */
		private const val API: String = "api"

		/**
		 * The name of the custom [Project] [Configuration], `availLibrary`,
		 * where only the [AVAIL_STDLIB_DEP] is added.
		 */
		internal const val AVAIL_LIBRARY: String = "availLibrary"

		/**
		 * The rename that is applied to the [AVAIL_STDLIB_DEP] Jar if it is
		 * copied into the roots directory given [AvailExtension.useAvailStdLib]
		 * is `true`.
		 */
		internal const val AVAIL_STDLIB_BASE_JAR_NAME: String = "avail-stdlib"

		/**
		 * The string "avail" that is used to name the [AvailExtension] for the
		 * hosting [Project].
		 */
		internal const val AVAIL = "avail"

		/**
		 * The string, `workbench`, is the standard label for the avail
		 * workbench.
		 */
		internal const val WORKBENCH = "workbench"

		/**
		 * The name of the custom [Project] [Configuration],
		 * `__internalAvailWorkbench`, where the [WORKBENCH_DEP] is added.
		 */
		internal const val WORKBENCH_INTERNAL_CONFIG = "__internalAvailWorkbench"

		/**
		 * The location of the properties file that contains the last published
		 * release of the avail libraries.
		 */
		const val releaseVersionFile = "/releaseVersion.properties"
	}
}
