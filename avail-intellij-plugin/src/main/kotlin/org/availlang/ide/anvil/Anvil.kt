/*
 * Anvil.kt
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

package org.availlang.ide.anvil

import org.availlang.ide.anvil.models.Project
import org.availlang.ide.anvil.models.ProjectDescriptor
import org.availlang.ide.anvil.utilities.Defaults
import org.availlang.json.JSONFriendly
import org.availlang.json.JSONObject
import org.availlang.json.JSONReader
import org.availlang.json.JSONWriter
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * `Anvil` is the application context for the running Anvil application as an
 * IntelliJ plugin. It statically maintains all the model state of Anvil that
 * exists while this project is open in IntelliJ.
 *
 * // TODO figure out how to locate the .idea folder to place config file!
 * @author Richard Arriaga
 */
object Anvil: JSONFriendly
{
	/**
	 * These are all the [default][Defaults] settings for Anvil.
	 */
	val defaults = Defaults()

	/**
	 * The [ProjectDescriptor] that describes the project.
	 */
	var projectDescriptor: ProjectDescriptor = ProjectDescriptor.EMPTY_PROJECT

	/**
	 * Close the [open project][openProjects] with the given project id.
	 *
	 * @param id
	 *   The [Project.id] to remove.
	 */
	fun closeProject (id: String)
	{
		saveConfigToDisk()
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
	private val configFile: File
		get() =
		File("${anvilHome}/$CONFIG_FILE")

	/**
	 * Read in the Anvil configuration file and use its contents to populate
	 * this [Anvil].
	 */
	private fun readConfig (configFile: File)
	{
		val reader = JSONReader(configFile.bufferedReader())
		val obj = reader.read() as? JSONObject
			?: error("Malformed Anvil config file: ${configFile.absolutePath}")

		projectDescriptor = ProjectDescriptor.from(obj)
	}

	override fun writeTo(writer: JSONWriter)
	{
		writer.startObject()
		projectDescriptor.writeTo(writer)
		writer.endObject()
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
	const val CONFIG_FILE = "anvil.json"

	/**
	 * The Anvil home directory located in the `user.home` directory.
	 */
	private const val ANVIL_HOME = ".avail/anvil"

	/**
	 * The default repositories directory located in the `user.home` directory.
	 */
	internal const val REPOS_DEFAULT = ".avail/repositories"

	/**
	 * The key used to track serialization version of the Anvil config file.
	 */
	internal const val VERSION = "version"

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
