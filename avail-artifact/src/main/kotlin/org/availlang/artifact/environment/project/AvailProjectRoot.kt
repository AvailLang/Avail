@file:Suppress("MemberVisibilityCanBePrivate", "DuplicatedCode")

package org.availlang.artifact.environment.project

import org.availlang.artifact.environment.AvailEnvironment
import org.availlang.artifact.environment.location.AvailLocation
import org.availlang.artifact.environment.project.AvailProject.Companion.STYLE_FILE_NAME
import org.availlang.artifact.environment.project.AvailProject.Companion.TEMPLATE_FILE_NAME
import org.availlang.artifact.manifest.AvailRootManifest
import org.availlang.artifact.roots.AvailRoot
import org.availlang.json.JSONFriendly
import org.availlang.json.JSONObject
import org.availlang.json.JSONWriter
import org.availlang.json.jsonObject
import java.io.File
import java.security.MessageDigest
import java.util.*

/**
 * Represents an Avail module root in a [AvailProject].
 *
 * @author Richard Arriaga
 *
 * @property rootConfigDirectory
 *   The path to the configuration directory where project configuration files
 *   specific to this [AvailRoot] is stored.
 * @property projectDirectory
 *   The root directory of the [AvailProject].
 * @property name
 *   The name of the module root.
 * @property location
 *   The [AvailLocation] of this root.
 * @property localSettings
 *   The [LocalSettings] for the [AvailProjectRoot].
 * @property styles
 *   The [StylingGroup] for this [AvailProjectRoot].
 * @property templateGroup
 *   The [TemplateGroup] for this [AvailProjectRoot].
 * @property availModuleExtensions
 *   The file extensions that signify files that should be treated as Avail
 *   modules.
 * @property editable
 *   `true` indicates this root is editable by the project; `false` otherwise.
 * @property id
 *   The immutable id that uniquely identifies this [AvailProjectRoot].
 * @property visible
 *   `true` indicates the root is intended to be displayed; `false` indicates
 *   the root should not be visible by default.
 */
class AvailProjectRoot constructor(
	val rootConfigDirectory: String,
	val projectDirectory: String,
	var name: String,
	var location: AvailLocation,
	var localSettings: LocalSettings,
	var styles: StylingGroup = StylingGroup(),
	var templateGroup: TemplateGroup = TemplateGroup(),
	val availModuleExtensions: MutableList<String> = mutableListOf("avail"),
	var editable: Boolean = location.editable,
	val id: String = UUID.randomUUID().toString(),
	var visible: Boolean = true
): JSONFriendly
{
	/**
	 * An optional description of this [AvailProjectRoot].
	 */
	var description: String = ""

	/**
	 * The [StylingSelection] used for styling this [AvailProjectRoot].
	 */
	@Suppress("unused")
	val stylingSelection: StylingSelection get() =
		localSettings.palette.let { p ->
			StylingSelection(
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
		}

	/**
	 * The list of [ModuleHeaderFileMetadata]s that can be used to prepend to
	 * the start of new modules.
	 */
	val moduleHeaders: MutableSet<ModuleHeaderFileMetadata> = mutableSetOf()

	/**
	 * The Avail module descriptor path. It takes the form:
	 *
	 * `"$name=$uri"`
	 */
	@Suppress("unused")
	val modulePath: String = "$name=${location.fullPath}"

	/**
	 * Refresh the [templateGroup] from disk.
	 *
	 * @param rootConfigDir
	 *   The path to the root's configuration directory.
	 */
	@Suppress("unused")
	fun refreshTemplates (rootConfigDir: String)
	{
		templateGroup = TemplateGroup(
			jsonObject(
				File(rootConfigDir, TEMPLATE_FILE_NAME).readText()))
	}

	/**
	 * Refresh the [styles] from disk.
	 *
	 * @param rootConfigDir
	 *   The path to the root's configuration directory.
	 */
	@Suppress("unused")
	fun refreshStyles (rootConfigDir: String)
	{
		styles = StylingGroup(
			jsonObject(
				File(rootConfigDir, STYLE_FILE_NAME).readText()))
	}

	/**
	 * Refresh the [localSettings] from disk.
	 *
	 * @param rootConfigDir
	 *   The path to the root's configuration directory.
	 */
	@Suppress("unused")
	fun refreshLocalSettings (rootConfigDir: String)
	{
		localSettings = LocalSettings.from(File(rootConfigDir))
	}

	/**
	 * Write the [templateGroup] to its configuration file in
	 * [rootConfigDirectory].
	 */
	@Suppress("unused")
	fun saveTemplatesToDisk ()
	{
		templateGroup.saveToDisk(
			"$rootConfigDirectory${File.separator}$TEMPLATE_FILE_NAME")
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
	 * Write the [styles] to its configuration file in
	 * [rootConfigDirectory].
	 */
	@Suppress("unused")
	fun saveStylesToDisk ()
	{
		styles.saveToDisk(
			"$rootConfigDirectory${File.separator}$STYLE_FILE_NAME")
	}

	/**
	 * Create an [AvailRootManifest] from this [AvailProjectRoot].
	 *
	 * @param digestAlgorithm
	 *   The [MessageDigest] algorithm to use to create the digests for all the
	 *   root's contents. This must be a valid algorithm accessible from
	 *   [java.security.MessageDigest.getInstance].
	 * @param entryPoints
	 *   The Avail entry points exposed by this root.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	fun manifest(
		digestAlgorithm: String = "SHA-256",
		entryPoints: MutableList<String> = mutableListOf()
	): AvailRootManifest =
		AvailRootManifest(
			name,
			availModuleExtensions,
			entryPoints,
			templateGroup.markedForInclusion,
			styles,
			description,
			digestAlgorithm)

	/**
	 * The [AvailRoot] sourced from this [AvailProjectRoot].
	 */
	@Suppress("unused")
	val availRoot: AvailRoot get() =
		AvailRoot(
			name = name,
			location = location,
			availModuleExtensions = availModuleExtensions,
			templateGroup = templateGroup,
			styles = styles,
			description = description)

	override fun writeTo(writer: JSONWriter)
	{
		writer.writeObject {
			at(::id.name) { write(id) }
			at(::name.name) { write(name) }
			at(::editable.name) { write(editable) }
			at(::visible.name) { write(visible) }
			at(::description.name) { write(description) }
			at(::location.name) { location.writeTo(this@writeObject) }
			if (availModuleExtensions != listOf("avail"))
			{
				at(::availModuleExtensions.name) {
					writeStrings(availModuleExtensions)
				}
			}
			at(::moduleHeaders.name)
			{
				ModuleHeaderFileMetadata.writeTo(this, moduleHeaders)
			}
		}
	}

	override fun toString(): String = "$name=${location.fullPath}"

	companion object
	{
		/**
		 * Extract and build an [AvailProjectRoot] from the provided
		 * [JSONObject].
		 *
		 * @param projectFileName
		 *   The name of the project file without the path.
		 * @param projectDirectory
		 *   The root directory of this project.
		 * @param obj
		 *   The [JSONObject] that contains the [AvailProjectRoot] data.
		 * @param serializationVersion
		 *   The [Int] identifying the version of the [AvailProject] within
		 *   which this is a root.
		 * @return
		 *   The extracted `ProjectRoot`.
		 */
		fun from(
			project: AvailProject,
			projectFileName: String,
			projectDirectory: String,
			obj: JSONObject,
			@Suppress("UNUSED_PARAMETER") serializationVersion: Int
		): AvailProjectRoot
		{
			val rootName = obj.getString(AvailProjectRoot::name.name)
			val rootConfigPath = AvailEnvironment.projectRootConfigPath(
				projectFileName.removeSuffix(".json"),
				rootName,
				projectDirectory)
			val configDir =
				project.optionallyInitializeConfigDirectory(rootConfigPath)

			val localSettings = LocalSettings.from(configDir)
			val styles = StylingGroup(jsonObject(
				File(configDir, STYLE_FILE_NAME).readText()))
			val templateGroup = TemplateGroup(jsonObject(
				File(configDir, TEMPLATE_FILE_NAME).readText()))
			return AvailProjectRoot(
				rootConfigPath,
				projectDirectory = projectDirectory,
				name = obj.getString(AvailProjectRoot::name.name),
				location = AvailLocation.from(
					projectDirectory,
					obj.getObject(AvailProjectRoot::location.name)
				),
				localSettings = localSettings,
				styles = styles,
				templateGroup = templateGroup,
				availModuleExtensions = obj.getArrayOrNull(
					AvailProjectRoot::availModuleExtensions.name
				)?.strings?.toMutableList() ?: mutableListOf("avail"),
				editable = obj.getBoolean(
					AvailProjectRoot::editable.name
				) { false },
				id = obj.getString(AvailProjectRoot::id.name),
				visible = obj.getBoolean(AvailProjectRoot::visible.name) { true },
			).apply {
				obj.getArrayOrNull(AvailProjectRoot::moduleHeaders.name)?.let {
					moduleHeaders.addAll(
						ModuleHeaderFileMetadata.from(
							configDir.absolutePath, it))
				}
				description = obj.getStringOrNull(::description.name) ?: ""
			}
		}
	}
}
