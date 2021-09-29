/*
 * App.kt
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

package avail.anvil

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import avail.anvil.models.Project
import avail.anvil.models.ProjectDescriptor
import avail.anvil.utilities.Defaults
import com.avail.utility.json.JSONFriendly
import com.avail.utility.json.JSONObject
import com.avail.utility.json.JSONReader
import com.avail.utility.json.JSONValue
import com.avail.utility.json.JSONWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.coroutines.CoroutineContext

/**
 * `Anvil` is the application context for the running Anvil application. It
 * statically maintains all the model state of Anvil that exists outside of
 * of the [Composable] views of the application.
 *
 * @author Richard Arriaga
 */
object Anvil: JSONFriendly
{
	/**
	 * This is the [CoroutineScope] used for running coroutines that are
	 * independent of any individual screen.
	 */
	val applicationScope: CoroutineScope  =
		CoroutineScope(SupervisorJob() + Dispatchers.Default)

	/**
	 * This is the [CoroutineScope] used for running IO coroutines that are
	 * independent of any individual screen.
	 */
	val applicationIoScope =
		CoroutineScope(SupervisorJob() + Dispatchers.IO)

	/**
	 * These are all the [default][Defaults] settings for Anvil.
	 */
	val defaults = Defaults()

	/**
	 *
	 */
	var isSufficientlyLoadedFromDisk = mutableStateOf(false)

	/**
	 * The [Project]s that Anvil presently knows exists.
	 */
	private val knownProjectMap =
		mutableMapOf<String, ProjectDescriptor>()

	/**
	 * The list of [ProjectDescriptor]s of projects that Anvil presently knows
	 * exists.
	 */
	val knownProjects: List<ProjectDescriptor> get() =
		knownProjectMap.values.toList().sorted()

	/**
	 * Answer the [Project] for the given [ProjectDescriptor.id].
	 *
	 * @param id
	 *   The that uniquely identifies the project.
	 * @return
	 *   The corresponding project or `null` if no project with that name found.
	 */
	fun project (id: String): ProjectDescriptor? =
		knownProjectMap[id]

	/**
	 * Close the [open project][openProjects] with the given project id.
	 *
	 * @param id
	 *   The [Project.id] to remove.
	 */
	fun closeProject (id: String)
	{
		openProjects.remove(id)
		saveConfigToDisk()
	}

	/**
	 * Open the [Project].
	 *
	 * @param project
	 *   The `project` to add to [openProjects].
	 */
	fun openProject (project: Project)
	{
		openProjects[project.id] = project
		saveConfigToDisk()
	}

	/**
	 * The [Project]s that Anvil presently has open.
	 */
	val openProjects = mutableStateMapOf<String, Project>()

	/**
	 * Add the [project] to [knownProjectMap].
	 */
	fun addKnownProject (project: ProjectDescriptor)
	{
		knownProjectMap[project.id] = project
	}

	/**
	 * Add the [project] to [knownProjectMap].
	 */
	fun addKnownProject (project: Project)
	{
		knownProjectMap[project.id] = project.descriptor
	}

	internal val userHome: String get() = System.getProperty("user.home")

	/**
	 * The path to the Anvil home directory.
	 */
	internal val anvilHome get() = run {
		File("$userHome/$ANVIL_HOME").apply {
			mkdirs()
		}.absolutePath
	}

	/**
	 * The Anvil configuration [File].
	 */
	private val configFile: File get() =
		File("${anvilHome}/$CONFIG_FILE")

	/**
	 * Read in the Anvil configuration file and use its contents to populate
	 * this [Anvil].
	 */
	private fun readConfig (configFile: File)
	{
		val reader = JSONReader(configFile.bufferedReader())
		val obj = reader.read()  as? JSONObject
			?: error("Malformed Anvil config file: ${configFile.absolutePath}")
		obj.getArray(KNOWN_PROJECTS).forEach {
			val projObj = it as? JSONObject ?:
				error("Malformed Anvil config file: " +
					"${configFile.absolutePath}: malformed Project object in " +
					"`knownProjects`.")
			val descriptor = ProjectDescriptor.from(projObj)
			knownProjectMap[descriptor.id] = descriptor
		}
		val opens = obj.getArray(OPEN_PROJECTS)
		if (opens.size() == 0)
		{
			isSufficientlyLoadedFromDisk.value = true
		}
		else
		{
			opens.forEach { data ->
				(data as? JSONValue)?.string?.let { key ->
					knownProjectMap[key]?.let { descriptor ->
						descriptor.project { project ->
							openProjects[project.id] = project
							isSufficientlyLoadedFromDisk.value = true
						}
					}
				}
			}
		}
	}

	override fun writeTo(writer: JSONWriter)
	{
		writer.at(KNOWN_PROJECTS) {
			startArray()
			knownProjectMap.values.forEach {
				startObject()
				it.writeTo(this)
				endObject()
			}
			endArray()
		}
		writer.at(OPEN_PROJECTS) {
			startArray()
			openProjects.values.toList().sorted().forEach { p ->
				write(p.id)
			}
			endArray()
		}
	}

	/**
	 * Save the current Anvil configuration to disk.
	 */
	fun saveConfigToDisk ()
	{
		val writer = JSONWriter()
		writer.startObject()
		writeTo(writer)
		writer.endObject()
		try
		{
			Files.newBufferedWriter(configFile.toPath()).use {
				it.write(writer.toString())
			}
		}
		catch (e: IOException)
		{
			throw IOException(
				"Save Anvil config to file failed: ${configFile.absolutePath}",
				e)
		}
	}

	/**
	 * Initialize the Anvil environment.
	 */
	fun initialize()
	{
		val config = configFile
		if (config.exists())
		{
			readConfig(config)
		}
		else
		{
			saveConfigToDisk()
		}
	}

	/**
	 * The file that contains all of Anvil's stored configrations.
	 */
	private const val CONFIG_FILE = "anvil.json"

	/**
	 * The Anvil home directory located in the `user.home` directory.
	 */
	private const val ANVIL_HOME = ".avail/anvil"

	/**
	 * The default repositories directory located in the `user.home` directory.
	 */
	internal const val REPOS_DEFAULT = ".avail/repositories"

	///////////////////////////////////////////////////////////////////////////
	//                         JSON CONFIG FILE KEYS                         //
	///////////////////////////////////////////////////////////////////////////
	/**
	 * [knownProjectMap] config file key.
	 */
	private const val KNOWN_PROJECTS = "knownProjects"

	/**
	 * [openProjects] config file key.
	 */
	private const val OPEN_PROJECTS = "openProjects"
}
