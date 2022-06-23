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

/**
 * A task that assembles the entire Avail project into a fat jar.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
open class PackageAvailLibraryTask: DefaultTask()
{
	/**
	 * The base name to give to the created jar.
	 */
	@Input
	var libraryName: String = name

	/**
	 * The base name to give to the created jar.
	 */
	@Input
	var version: String = ""

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
	 * Assembles the application into a fully self-contained runnable JAR.
	 *
	 * @param fullPathToFile
	 *   The location the jar will end up.
	 */
	private fun assemble (fullPathToFile: String)
	{
		println("Building $fullPathToFile")
		project.tasks.create("__avassm_$name", AvailJarPackager::class.java)
		{
			group = AVAIL
			description =
				"Assemble an Avail Library."
			manifest.attributes["Version"] = project.version
			manifest.attributes["Assembled-Timestamp-UTC"] = formattedNow
			val availArtifactDir = "${project.buildDir}/$AVAIL"
			project.mkdir(availArtifactDir)
			archiveBaseName.set(libraryName)
			archiveVersion.set(this@PackageAvailLibraryTask.version)
			destinationDirectory.set(project.file(availArtifactDir))
			roots.forEach { (rootName, root) ->
				val rootDir = "$availArtifactDir/$rootName/Avail-Sources"
				project.mkdir(rootDir)
				val digestDirectory =
					"$availArtifactDir/$rootName/Avail-Digests/"
				project.mkdir(digestDirectory)
				val digest = createDigests(rootName, root.uri, project)
				File("$digestDirectory/all_digests.txt")
					.writeText(digest)
				from(root.uri)
				{
					include("**/*.*")
					into("$rootName/Avail-Sources")
				}
				from(digestDirectory)
				{
					include("**/*.*")
					into("$rootName/Avail-Sources")
				}
			}
			duplicatesStrategy = DuplicatesStrategy.INCLUDE
		}.doCopy()
	}

	private fun createDigests (
		rootName: String,
		rootPath: String,
		project: Project): String
	{
		val digestsDirectory = "${project.buildDir}/$AVAIL/$rootName/Avail-Digests"
		val baseFile = File(rootPath)
		return buildString {
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

	}

	/**
	 * This is the core action that is performed.
	 */
	@TaskAction
	protected fun run ()
	{
		val v = if (version.isNotEmpty()) "-$version" else ""
		val fullPathToFile =
			"${project.buildDir}/$AVAIL/$libraryName$v.jar"
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
