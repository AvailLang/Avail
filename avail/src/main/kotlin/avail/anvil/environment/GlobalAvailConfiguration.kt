/*
 * GlobalAvailConfiguration.kt
 * Copyright © 1993-2023, The Avail Foundation, LLC.
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

package avail.anvil.environment

import avail.anvil.manager.AvailProjectManager
import avail.anvil.settings.KeyboardShortcutOverride
import avail.anvil.projects.KnownAvailProject
import avail.anvil.settings.ShortcutSettings
import avail.anvil.shortcuts.KeyboardShortcut
import org.availlang.artifact.environment.AvailEnvironment
import org.availlang.artifact.environment.AvailEnvironment.availHome
import org.availlang.artifact.environment.project.AvailProject
import org.availlang.json.JSONFriendly
import org.availlang.json.jsonObject
import org.availlang.json.jsonPrettyPrintWriter
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/**
 * The interface that defines the state expected of the configuration for the
 * Avail environment on a specific machine. This data is stored in the
 * [AvailEnvironment.availHome] directory in the file
 * `avail-global-config.json`.
 *
 * @author Richard Arriaga
 */
sealed interface GlobalAvailConfiguration: JSONFriendly
{
	/**
	 * The serialization version of this [GlobalAvailConfiguration] which
	 * represents the structure of the [JSONFriendly]-based configuration file
	 * that represents this [GlobalAvailConfiguration].
	 */
	val serializationVersion: Int

	/**
	 * The [KnownAvailProject.id] of the project marked to be opened
	 * automatically at launch bypassing the [AvailProjectManager] or
	 * `null` if only the [AvailProjectManager] should be opened.
	 */
	var favorite: String?

	/**
	 * The list of character positions to draw an editor guide line.
	 */
	val editorGuideLines: MutableList<Int>

	/** The set of [KnownAvailProject]s. */
	val knownProjects: MutableSet<KnownAvailProject>

	/**
	 * The map of [KeyboardShortcutOverride.actionMapKey] to [KeyboardShortcut]
	 * that override the default key combinations for the listed
	 * [KeyboardShortcut]s.
	 */
	val keyboardShortcutOverrides: MutableMap<String, KeyboardShortcutOverride>
		get() = shortcutSettings.keyboardShortcutOverrides

	/**
	 * The [ShortcutSettings] sourced from [keyboardShortcutOverrides].
	 */
	val shortcutSettings: ShortcutSettings

	/**
	 * The [knownProjects] sorted by [KnownAvailProject.name] in ascending
	 * alphabetical order.
	 */
	val knownProjectsByAlphaAscending get() =
		knownProjects.toList().sortedBy { it.name }

	/**
	 * The [knownProjects] sorted by [KnownAvailProject.name] in descending
	 * alphabetical order.
	 */
	val knownProjectsByAlphaDescending get() =
		knownProjects.toList().sortedByDescending { it.name }

	/**
	 * The [knownProjects] sorted by [KnownAvailProject.lastOpened] in ascending
	 * order.
	 */
	val knownProjectsByLastOpenedAscending get() =
		knownProjects.toList().sortedBy { it.lastOpened }

	/**
	 * The [knownProjects] sorted by [KnownAvailProject.lastOpened] in
	 * descending order.
	 */
	val knownProjectsByLastOpenedDescending get() =
		knownProjects.toList().sortedByDescending { it.lastOpened }

	/**
	 * Add the provided [AvailProject] to this [GlobalAvailConfiguration] as a
	 * [KnownAvailProject].
	 *
	 * @param project
	 *   The [AvailProject] to add.
	 * @param path
	 *   The path to the [AvailProject] file.
	 */
	fun add (project: AvailProject, path: String)
	{
		knownProjects.firstOrNull { it.id == project.id }?.let {
			it.name = project.name
			it.projectConfigFile = path
			it.lastOpened = System.currentTimeMillis()
		} ?: knownProjects.add(
			KnownAvailProject(
				project.name, project.id, path, System.currentTimeMillis()))
		saveToDisk()
	}

	/**
	 * The [favorite] [KnownAvailProject] or `null` if [favorite] is `null`.
	 */
	val favoriteKnownProject: KnownAvailProject? get() =
		favorite?.let { fav ->
			knownProjects.firstOrNull { it.id == fav }
		}

	/**
	 * Remove the [KnownAvailProject] with the given [KnownAvailProject.id].
	 *
	 * @param id
	 *   The [KnownAvailProject.id] of the [KnownAvailProject] to remove.
	 */
	fun removeProject (id: String)
	{
		if(knownProjects.removeIf { it.id == id })
		{
			saveToDisk()
		}
	}

	/**
	 * The String contents of this [GlobalAvailConfiguration] that can be
	 * written to disk.
	 */
	@Suppress("unused")
	val fileContent: String get() =
		jsonPrettyPrintWriter {
			this@GlobalAvailConfiguration.writeTo(this)
		}.toString()

	/**
	 * Write this [GlobalAvailConfiguration] to disk.
	 */
	fun saveToDisk ()
	{
		// First create a backup of the current file.
		Files.copy(
			Paths.get(globalConfigFile),
			Paths.get("$globalConfigFile.bak"),
			StandardCopyOption.REPLACE_EXISTING)
		File(globalConfigFile).writeText(fileContent)
	}

	/**
	 * Reset the [keyboardShortcutOverrides] to the defaults. This clears all
	 * current [keyboardShortcutOverrides].
	 */
	fun resetToDefaultShortcuts ()
	{
		shortcutSettings.keyboardShortcutOverrides.clear()
		shortcutSettings.save()
	}

	companion object
	{
		/**
		 * The version that represents the current structure under which Avail
		 * libs are packaged in the artifact.
		 */
		private const val CURRENT_PROJECT_VERSION = 1

		/**
		 * The Avail configuration file name.
		 */
		const val CONFIG_FILE_NAME = "avail-global-config.json"

		/**
		 * The path to the [GlobalAvailConfiguration] file on disk.
		 */
		private val configFilePath get() =
			"$availHome${File.separator}$CONFIG_FILE_NAME"

		val emptyConfig: GlobalAvailConfiguration
			get() = GlobalAvailConfigurationV1()

		/**
		 * @return
		 *   The [GlobalAvailConfiguration] read from the
		 *   [AvailEnvironment.availHome] or the [emptyConfig] if not found or
		 *   malformed.
		 */
		fun getGlobalConfig (): GlobalAvailConfiguration
		{
			val file = File(globalConfigFile)
			if (!file.exists())
			{
				val config = emptyConfig
				config.saveToDisk()
				return config
			}
			val obj =
				try
				{
					val contents = file.readText()
					jsonObject(contents)
				}
				catch (e: Throwable)
				{
					System.err.println(
						"Malformed Global Avail Configuration File: " +
							"$configFilePath\n. Delete file and restart avail")
					e.printStackTrace()
					throw e
				}
			val version = obj.getNumber(
				GlobalAvailConfiguration::serializationVersion.name).int

			return when (version)
			{
				1 -> GlobalAvailConfigurationV1.from(obj)
				else ->
					throw IllegalStateException("Invalid Global Avail " +
						"Configuration File: Version $version is not in the " +
						"valid range of known global config file versions," +
						" [1, $CURRENT_PROJECT_VERSION].")
			}
		}
	}
}
