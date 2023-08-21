package org.availlang.artifact.environment.project

import org.availlang.json.JSONFriendly
import org.availlang.json.JSONObject
import org.availlang.json.JSONWriter
import org.availlang.json.jsonObject
import java.io.File

/**
 * The local settings for an [AvailProject].
 *
 * @author Richard Arriaga
 *
 * @property configDir
 *   The configuration directory the [LocalSettings] should be stored in.
 * @property palette
 *   The name of the [Palette] to use for styling.
 * @property loadModulesOnStartup
 *   The Avail modules that should be loaded on environment set up.
 */
data class LocalSettings constructor (
	val configDir: String,
	var palette: String = "",
	val loadModulesOnStartup: MutableSet<String> = mutableSetOf()
): JSONFriendly
{
	override fun writeTo(writer: JSONWriter)
	{
		writer.writeObject {
			at(::palette.name) { write(palette) }
			at(::loadModulesOnStartup.name)
			{
				writeStrings(loadModulesOnStartup)
			}
		}
	}

	/** Save this [LocalSettings] to disk. */
	fun save ()
	{
		File(File(configDir), LOCAL_SETTINGS_FILE).writeText(
			jsonPrettyPrintedFormattedString)
	}

	/**
	 * Construct a [LocalSettings].
	 *
	 * @param configDir
	 *   The configuration directory the [LocalSettings] should be stored
	 *   in.
	 * @param obj
	 *   The [JSONObject] that contains the [LocalSettings] data.
	 */
	constructor(configDir: String, obj: JSONObject): this(configDir)
	{
		obj.getStringOrNull(::palette.name)?.let {
			palette = it
		}
		obj.getArrayOrNull(::loadModulesOnStartup.name)?.strings?.let {
			loadModulesOnStartup.addAll(it)
		}
	}

	companion object
	{
		/**
		 * The name of the local settings file.
		 */
		const val LOCAL_SETTINGS_FILE = "settings-local.json"

		/**
		 * Answer a [LocalSettings] for the provided configuration directory.
		 *
		 * @param configDir
		 *   The configuration directory the [LocalSettings] should be stored
		 *   in.
		 */
		fun from (configDir: File): LocalSettings =
			configDir.let {
				if (!it.exists())
				{
					return@let LocalSettings(configDir.absolutePath)
				}
				val text = File(it, LOCAL_SETTINGS_FILE).readText()
				LocalSettings(
					configDir.absolutePath,
					jsonObject(text))
			}
	}
}
