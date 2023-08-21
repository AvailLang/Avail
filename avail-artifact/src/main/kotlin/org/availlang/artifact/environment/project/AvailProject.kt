/*
 * AvailProject.kt
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

package org.availlang.artifact.environment.project

import org.availlang.artifact.AvailArtifact
import org.availlang.artifact.AvailArtifactBuildPlan
import org.availlang.artifact.AvailArtifactException
import org.availlang.artifact.environment.AvailEnvironment
import org.availlang.artifact.environment.AvailEnvironment.projectConfigDirectory
import org.availlang.artifact.environment.location.AvailLocation
import org.availlang.artifact.manifest.AvailArtifactManifest
import org.availlang.json.JSONFriendly
import org.availlang.json.JSONObject
import org.availlang.json.jsonObject
import org.availlang.json.jsonPrettyPrintWriter
import java.io.File

/**
 * Describes the makeup of an Avail project.
 *
 * This also implements a [JSONFriendly] interface for writing this as a
 * configuration file used for starting up Avail project environments.
 *
 * @author Richard Arriaga
 */
interface AvailProject: JSONFriendly
{
	/**
	 * The name of the Avail project.
	 */
	val name: String

	/**
	 * The serialization version of this [AvailProject] which represents the
	 * structure of the [JSONFriendly]-based configuration file that represents
	 * this [AvailProject].
	 */
	val serializationVersion: Int

	/**
	 * The [AvailLocation] for the Avail repository where a persistent Avail
	 * indexed file of compiled modules are stored.
	 */
	val repositoryLocation: AvailLocation

	/**
	 * The id that uniquely identifies the project.
	 */
	val id: String

	/**
	 * `true` indicates use of Avail Workbench's dark mode; `false` for light
	 *  mode.
	 */
	val darkMode: Boolean

	/**
	 * The map of [AvailProjectRoot.name] to [AvailProjectRoot].
	 */
	val roots: MutableMap<String, AvailProjectRoot>

	/**
	 * The map from [AvailArtifact] file name to its [AvailArtifactManifest].
	 */
	val manifestMap: MutableMap<String, AvailArtifactManifest>

	/**
	 *  The [LocalSettings] for this [AvailProject].
	 */
	var localSettings: LocalSettings

	/**
	 * The [StylingGroup] for this [AvailProject].
	 */
	var styles: StylingGroup

	/**
	 * The [TemplateGroup] for this [AvailProject].
	 */
	var templateGroup: TemplateGroup

	/**
	 * The [StylingSelection] used for styling this [AvailProject].
	 */
	@Suppress("unused")
	val stylingSelection: StylingSelection get() =
		localSettings.palette.let { p ->
			val projSelection = StylingSelection(
				when
				{
					styles.palettes.isEmpty() -> Palette.empty
					p.isNotEmpty() ->
						(styles.palettes[p] ?: styles.palettes.values.first())
							.let { Palette(it.lightColors, it.darkColors) }
					p.isEmpty() ->
						styles.palettes.values.first()
							.let { Palette(it.lightColors, it.darkColors) }
					else ->
					{
						Palette.empty
					}
				},
				styles.stylesheet.toMutableMap())
			val merged = roots.values.toMutableList()
				.apply { sortedBy { it.name } }
				.fold(StylingSelection())
				{ merged, r ->
					r.stylingSelection.mergeOnto(merged)
				}
			projSelection.mergeOnto(merged)
		}

	/**
	 * The map of [Palette] names to [Palette]s available for this root.
	 */
	@Suppress("unused")
	val palettes get() = styles.palettes

	/** The project-specific stylesheet, overriding any root stylesheets. */
	@Suppress("unused")
	val stylesheet: Map<String, StyleAttributes> get() = styles.stylesheet

	/**
	 * The set of Strings that describes arbitrary things to disallow in an
	 * Avail project.
	 */
	val disallow: MutableSet<String>

	/**
	 * The set of [ModuleHeaderFileMetadata]s that can be used to prepend to
	 * the start of new modules.
	 */
	val moduleHeaders: MutableSet<ModuleHeaderFileMetadata>

	/**
	 * The list of [AvailArtifactBuildPlan]s used to build an [AvailArtifact]
	 * from this [AvailProject].
	 */
	val artifactBuildPlans: MutableList<AvailArtifactBuildPlan>

	/**
	 * The list of [AvailProjectRoot]s in this [AvailProject].
	 */
	val availProjectRoots: List<AvailProjectRoot> get() =
		roots.values.toList().sortedBy { it.name }

	/**
	 * Optionally create the files expected in the [AvailProjectRoot]'s
	 * [configuration directory][AvailEnvironment.projectRootConfigPath].
	 *
	 * @param configPath
	 *   The [configuration directory][AvailEnvironment.projectRootConfigPath]
	 *   for the target [AvailProjectRoot].
	 * @return
	 *   The root configuration directory [File].
	 */
	fun optionallyInitializeConfigDirectory (
		configPath: String
	): File

	/**
	 * Add the [AvailProjectRoot] to this [AvailProject].
	 *
	 * @param availProjectRoot
	 *   The `AvailProjectRoot` to add.
	 */
	@Suppress("unused")
	fun addRoot (availProjectRoot: AvailProjectRoot)
	{
		roots[availProjectRoot.id] = availProjectRoot
	}

	/**
	 * Remove the [AvailProjectRoot] from this [AvailProject].
	 *
	 * @param projectRootName
	 *   The [AvailProjectRoot.name] to remove.
	 * @return
	 *   The `AvailProjectRoot` removed or `null` if not found.
	 */
	@Suppress("unused")
	fun removeRoot (projectRootName: String): AvailProjectRoot? =
		roots.remove(projectRootName)

	/**
	 * The String file contents of this [AvailArtifactManifest].
	 */
	@Suppress("unused")
	val fileContent: String get() =
		jsonPrettyPrintWriter {
			this@AvailProject.writeTo(this)
		}.toString()

	/**
	 * Refresh the [templateGroup] from disk.
	 *
	 * @param projectConfigDir
	 *   The path to the project configuration directory.
	 */
	@Suppress("unused")
	fun refreshTemplates (projectConfigDir: String)
	{
		templateGroup = TemplateGroup(
			jsonObject(
				File(projectConfigDir, TEMPLATE_FILE_NAME).readText()))
	}

	/**
	 * Refresh the [styles] from disk.
	 *
	 * @param projectConfigDir
	 *   The path to the project configuration directory.
	 */
	@Suppress("unused")
	fun refreshStyles (projectConfigDir: String)
	{
		styles = StylingGroup(
			jsonObject(
				File(projectConfigDir, STYLE_FILE_NAME).readText()))
	}

	/**
	 * Refresh the [localSettings] from disk.
	 *
	 * @param projectConfigDir
	 *   The path to the project configuration directory.
	 */
	@Suppress("unused")
	fun refreshLocalSettings (projectConfigDir: String)
	{
		localSettings = LocalSettings.from(File(projectConfigDir))
	}

	/**
	 * Refresh the [artifactBuildPlans] from disk.
	 *
	 * @param projectFileName
	 *   The name of the project.
	 * @param projectPath
	 *   The absolute path to the [AvailProject] directory.
	 */
	@Suppress("unused")
	fun refreshArtifactBuildPlans (
		projectFileName: String,
		projectPath: String)
	{
		val copy = artifactBuildPlans.toMutableList()
		artifactBuildPlans.clear()
		try
		{
			artifactBuildPlans.addAll(
				AvailArtifactBuildPlan.readPlans(projectFileName, projectPath))
		}
		catch (e: Throwable)
		{
			artifactBuildPlans.addAll(copy)
		}
	}

	/**
	 * Write the [templateGroup] to its configuration file in the project config
	 * directory.
	 *
	 * @param projectConfigDir
	 *   The project configuration directory.
	 */
	@Suppress("unused")
	fun saveTemplatesToDisk (projectConfigDir: String)
	{
		templateGroup.saveToDisk(
			"$projectConfigDir${File.separator}$TEMPLATE_FILE_NAME")
	}

	/**
	 * Write the [styles] to its configuration file in the project config
	 * directory.
	 *
	 * @param projectConfigDir
	 *   The project configuration directory.
	 */
	@Suppress("unused")
	fun saveStylesToDisk (projectConfigDir: String)
	{
		styles.saveToDisk(
			"$projectConfigDir${File.separator}$STYLE_FILE_NAME")
	}

	/**
	 * Write the [localSettings] to its configuration file in the project config
	 * directory.
	 */
	@Suppress("unused")
	fun saveLocalSettingsToDisk ()
	{
		localSettings.save()
	}

	/**
	 * Write the [templateGroup] to its configuration file in the project config
	 * directory.
	 *
	 * @param projectConfigDir
	 *   The project configuration directory.
	 */
	@Suppress("unused")
	fun saveArtifactBuildPlansToDisk (projectConfigDir: String)
	{
		jsonPrettyPrintWriter {
			writeArray {
				artifactBuildPlans.forEach { write(it) }
			}
		}
	}

	/**
	 * Answer the [AvailProjectRoot] associated with the given root
	 * configuration directory.
	 *
	 * @param path
	 *   The path to the root's configuration directory.
	 * @return
	 *   The associated [AvailProjectRoot] or `null` if no match was found.
	 */
	@Suppress("unused")
	fun rootFromConfigDirPath (path: String): AvailProjectRoot? =
		roots[File(path).name]

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
		@Suppress("unused")
		const val CONFIG_FILE_NAME = "avail-config.json"

		/**
		 * The name of the directory where the roots are stored for an
		 * [AvailProject].
		 */
		@Suppress("unused")
		const val ROOTS_DIR = "roots"

		/**
		 * The name of the [TemplateGroup] file in the root configuration
		 * directory.
		 */
		const val TEMPLATE_FILE_NAME = "templates.json"

		/**
		 * The name of the [TemplateGroup] file in the root configuration
		 * directory.
		 */
		const val STYLE_FILE_NAME = "styles.json"

		/**
		 * Extract and build a [AvailProject] from the provided [JSONObject].
		 *
		 * @param projectFileName
		 *   The name of the project file without the path.
		 * @param projectDirectory
		 *   The root directory of the project.
		 * @param obj
		 *   The `JSONObject` that contains the [AvailProject] data.
		 * @return
		 *   The extracted `AvailProject`.
		 */
		fun from (
			projectFileName: String,
			projectDirectory: String,
			obj: JSONObject
		): AvailProject
		{
			val version =
				obj.getNumber(AvailProject::serializationVersion.name).int

			return when (version)
			{
				1 -> AvailProjectV1.from(projectFileName, projectDirectory, obj)
				else ->
					throw AvailArtifactException("Invalid Avail Project: " +
						"Version $version is not in the valid range of " +
						"known project versions," +
						" [1, $CURRENT_PROJECT_VERSION].")
			}
		}

		/**
		 * Extract and build a [AvailProject] from the provided [JSONObject].
		 * Initializes the project configuration directories if not already
		 * created.
		 *
		 * @param projectFilePath
		 *   The path to the project file.
		 * @return
		 *   The extracted `AvailProject`.
		 */
		fun from (projectFilePath: String): AvailProject
		{
			val file = File(projectFilePath)
			val directory = file.parent
			val projectConfigDirectory = File(directory, projectConfigDirectory)
			if(!projectConfigDirectory.exists())
			{
				projectConfigDirectory.mkdirs()
				File(projectConfigDirectory, ".gitignore").writeText(
					"/**/*local-state*\n/**/*.backup")
			}
			return from(
				file.name,
				directory,
				jsonObject(file.readText(Charsets.UTF_8)))
		}

		/**
		 * Optionally create the files expected in the configuration directory.
		 *
		 * @param configPath
		 *   The path to the configuration directory.
		 * @return
		 *   The configuration directory [File].
		 */
		fun optionallyInitializeConfigDirectory(
			configPath: String,
			forRoot: Boolean = true
		): File =
			File(configPath).apply {
				if (!exists()) mkdirs()
				File(this, TEMPLATE_FILE_NAME).let {
					if (!it.exists())
					{
						it.writeText(TemplateGroup().jsonFormattedString)
					}
				}
				File(this, STYLE_FILE_NAME).let {
					if (!it.exists())
					{
						it.writeText(StylingGroup().jsonPrettyPrintedFormattedString)
					}
				}
				File(this, LocalSettings.LOCAL_SETTINGS_FILE).let {
					if (!it.exists())
					{
						it.writeText(LocalSettings(it.absolutePath)
							.jsonPrettyPrintedFormattedString)
					}
				}

				if (!forRoot)
				{
					File(this, AvailArtifactBuildPlan.ARTIFACT_PLANS_FILE).let {
						if (!it.exists())
						{
							it.writeText("[]")
						}
					}
				}
			}
	}
}
