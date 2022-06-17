/*
 * AvailPlugin.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
import java.util.jar.Attributes
import java.util.jar.JarFile

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
			create(AvailWorkbenchTask.WORKBENCH_INTERNAL_CONFIG)
		}

		// Create AvailExtension and attach it to the target host Project
		val extension =
			target.extensions.create(
				AVAIL,
				AvailExtension::class.java,
				target,
				this)

		target.tasks.register("initializeAvail", DefaultTask::class.java)
		{
			group = AVAIL
			description = "Initialize the Avail Project. This sets up the " +
				"project according to the configuration in the `avail` " +
				"extension block. At the end of this task, all root " +
				"initializers (lambdas) that were added will be run."

			val availLibConfig =
				target.configurations.getByName(AVAIL_LIBRARY)
			if (extension.useAvailStdLib)
			{
				val stdlibVer = extension.availStandardLibrary!!.stdlibVersion
				val stdlibDependency = target.dependencies
					.create("$AVAIL_STDLIB_DEP:$stdlibVer")
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
							val vv = getImplementationVersion(it)
							println("Adding Avail Standard Library: $vv")
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
			dependsOn("initializeAvail")
			println(extension.printableConfig)
		}

		target.tasks.register(
			"assembleAndRunWorkbench", AvailWorkbenchTask::class.java)
		{
			// Create Dependencies
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

	private fun getImplementationVersion(jar: File): String? =
		JarFile(jar).manifest
			.mainAttributes[Attributes.Name("Implementation-Version")] as? String

	companion object
	{
		/**
		 * The dependency group-artifact String dependency that points to the
		 * published Avail Workbench Jar. This is absent the version.
		 */
		internal const val WORKBENCH_DEP: String =
			"avail:avail-workbench"

		/**
		 * The dependency group-artifact String dependency that points to the
		 * published Avail Standard Library Jar. This is absent the version.
		 */
		internal const val AVAIL_STDLIB_DEP: String =
			"avail:avail-stdlib"

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
	}
}
