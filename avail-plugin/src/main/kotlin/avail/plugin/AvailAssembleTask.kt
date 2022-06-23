/*
 * AvailWorkbenchTask.kt
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

import avail.plugin.AvailPlugin.Companion.AVAIL
import org.availlang.artifact.AvailArtifactType
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.lang.IllegalStateException
import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.jar.Attributes
import java.util.jar.JarFile

/**
 * A task that assembles the entire Avail project into a fat jar.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
open class AvailAssembleTask: DefaultTask()
{
	/**
	 * The base name to give to the created jar.
	 */
	@Input
	var jarBaseName: String = name

	/**
	 * The base name to give to the created jar.
	 */
	@Input
	var mainClass: String = "avail.environment.AvailWorkbench"

	/**
	 * The base name to give to the created jar.
	 */
	@Input
	var version: String = ""

	/**
	 * The [AvailArtifactType] for this Avail artifact.
	 */
	@Input
	var artifactType: AvailArtifactType = AvailArtifactType.LIBRARY

	/**
	 * Get the active [AvailExtension] from the host [Project].
	 */
	private val availExtension: AvailExtension get() =
		(project.extensions.getByName(AVAIL) as AvailExtension)

	/**
	 * The directory location where the Avail roots repositories exist for this
	 * workbench instance. It defaults to what is set in the `avail` extension,
	 * [AvailExtension.repositoryDirectory].
	 */
	@Input
	var repositoryDirectory: String = availExtension.repositoryDirectory

	/**
	 * The current time as a String in the format `yyyy-MM-ddTHH:mm:ss.SSSZ`.
	 */
	private val formattedNow: String get()
	{
		val formatter = DateTimeFormatter
			.ofPattern("yyyy-MM-ddTHH:mm:ss.SSSZ")
			.withLocale(Locale.getDefault())
			.withZone(ZoneId.of("UTC"))
		return formatter.format(Instant.now())
	}

	/**
	 * The list of additional local jar dependencies to be bundled in with the
	 * fat jar of the Workbench. These strings should be absolute paths to an
	 * existing jar.
	 */
	private val localJarDependencies = mutableSetOf<String>()

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
	fun localJarDependency(dependency: String)
	{
		localJarDependencies.add(dependency)
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
	 * workbench when it is launched.
	 *
	 * There is no need to prefix the file scheme, `file://`, if it exists on
	 * the local file system; otherwise the scheme should be prefixed.
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
	 * Assembles the application into a fully self-contained runnable JAR.
	 *
	 * @param fullPathToFile
	 *   The location the jar will end up.
	 */
	private fun assemble (fullPathToFile: String)
	{
		val implementations = project.configurations.getByName("implementation")
		val api = project.configurations.getByName("api")

		println("Building $fullPathToFile")
		project.tasks.create("__avassm_$name", AvailJarPackager::class.java)
		{
			// jar {
			//		description = "The Avail standard library"
			//		manifest.attributes["Build-Version"] = project.extra.get("buildVersion")
			//		manifest.attributes["Implementation-Version"] = project.version
			//		archiveFileName.set(standardLibraryName)
			//		isZip64 = true
			//		dependsOn(createDigests)
			//		from(sourcesRoot) {
			//			include("**/*.*")
			//
			//			into(AvailArtifact.rootArtifactSourcesDir(availRootName))
			//		}
			//		from(digestsDirectory) {
			//			include(AvailArtifact.digestsFileName)
			//			into(availRootDigestsFilePath)
			//		}
			//		// Eventually we will add Avail-Compilations or something, to capture
			//		// serialized compiled modules, serialized phrases, manifest entries,
			//		// style information, and navigation indices.
			//		duplicatesStrategy = DuplicatesStrategy.FAIL
			//		manifest.attributes["Implementation-Title"] = "Avail standard library"
			//		manifest.attributes["Implementation-Version"] = project.version
			//		// Even though the jar only includes .avail source files, we need the
			//		// content to be available at runtime, so we use "" for the archive
			//		// classifier instead of "sources".
			//		archiveClassifier.set("")
			//	}

			group = AVAIL
			description =
				"Assemble a standalone Workbench fat jar and run it. " +
					"This task will require `initializeAvail` to be run " +
					"once to initialize the environment before building " +
					"the workbench.jar that will then be run using " +
					"configurations from the `avail` extension."
			manifest.attributes["Main-Class"] = mainClass
			manifest.attributes["Version"] = project.version
			manifest.attributes["Assembled-Timestamp-UTC"] = formattedNow
			val availArtifactDir = "${project.buildDir}/$AVAIL"
			project.mkdir(availArtifactDir)
			archiveBaseName.set(jarBaseName)
			archiveVersion.set(this@AvailAssembleTask.version)
			destinationDirectory.set(project.file(availArtifactDir))
			// Explicitly gather the dependencies, so that we end up with a JAR
			// including the complete "org.availlang:avail" plus any other
			// dependencies explicitly stated in the Avail extension.
			from(
				implementations.resolve().map {
					if (it.isDirectory) it else project.zipTree(it) } +
					api.resolve().map {
						if (it.isDirectory) it
						else project.zipTree(it) } +
					localJarDependencies.map {
						val f = project.file(it)
						JarFile(f).manifest
							.mainAttributes[Attributes.Name("Implementation-Version")] as? String

						if (f.isDirectory) f else project.zipTree(f)})
			duplicatesStrategy = DuplicatesStrategy.INCLUDE
		}.doCopy()
	}

	private fun createDigests (
		rootName: String,
		rootPath: String,
		project: Project)
	{
		val digestsDirectory = "${project.buildDir}/$AVAIL/$rootName/Avail-Digests"
		val baseFile = File(rootPath)
		val digestsString = buildString {
			inputs.files.files
				.filter(File::isFile)
				.forEach { file ->
					val digest = MessageDigest.getInstance("SHA-256")
					digest.update(file.readBytes())
					val hex = digest.digest()
						.joinToString("") { String.format("%02X", it) }
					append("${file.toRelativeString(baseFile)}:$hex\n")
				}
		}
		File("$digestsDirectory/all_digests.txt")
			.writeText(digestsString)
	}

	/**
	 * This is the core action that is performed.
	 */
	@TaskAction
	protected fun run ()
	{
		val v = if (version.isNotEmpty()) "-$version" else ""
		val fullPathToFile =
			"${project.buildDir}/$AVAIL/$jarBaseName$v.jar"
		assemble(fullPathToFile)
	}

	companion object
	{
		/**
		 * The dependency group-artifact String dependency that points to the
		 * published Avail code. This is absent the version.
		 */
		internal const val WORKBENCH_DEP: String = "org.availlang:avail"
	}
}
