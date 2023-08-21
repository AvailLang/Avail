package org.availlang.artifact.environment.project

import org.availlang.json.JSONArray
import org.availlang.json.JSONFriendly
import org.availlang.json.JSONObject
import org.availlang.json.JSONWriter
import java.io.File

/**
 * Represents a Module header template file in a project configuration
 * directory.
 *
 * @author Richard Arriaga
 *
 * @property configDirectory
 *   The absolute path to the directory where the associated file is stored.
 * @property name
 *   The name of this [ModuleHeaderFileMetadata].
 * @property fileName
 *   The [File.getName] of the [File] in the configuration that contains the
 *   module header text.
 * @property description
 *   A description of the module header.
 */
data class ModuleHeaderFileMetadata constructor(
	var configDirectory: String,
	var name: String = "",
	var fileName: String = "",
	var description: String = ""
): JSONFriendly
{
	override fun writeTo(writer: JSONWriter)
	{
		writer.writeObject {
			at(::name.name) { write(name) }
			at(::fileName.name) { write(fileName) }
			at(::description.name) { write(description) }
		}
	}

	/**
	 * The module header contents from the file on disk.
	 */
	@Suppress("unused")
	val fileContents: String get() = File(File(configDirectory), fileName).let {
		if (it.exists())
		{
			it.readText()
		}
		else
		{
			""
		}
	}

	override fun toString(): String = name

	/**
	 * Construct a [ModuleHeaderFileMetadata].
	 *
	 * @param configDirectory
	 *   The absolute path to the directory where the associated file is stored.
	 * @param obj
	 *   The [JSONObject] to extract the [ModuleHeaderFileMetadata] from.
	 */
	constructor(configDirectory: String, obj: JSONObject): this (configDirectory)
	{
		name = obj.getStringOrNull(::name.name) ?: ""
		fileName = obj.getStringOrNull(::fileName.name) ?: ""
		description = obj.getStringOrNull(::description.name) ?: ""
	}

	companion object
	{
		/**
		 * Extract a list of [ModuleHeaderFileMetadata]s from the provided
		 * [JSONArray].
		 *
		 * @param configDirectory
		 *   The absolute path to the directory where the associated file is
		 *   stored.
		 * @param arr
		 *   The [JSONArray] to extract [ModuleHeaderFileMetadata]s from.
		 */
		fun from (
			configDirectory: String,
			arr: JSONArray
		): List<ModuleHeaderFileMetadata> =
			arr.map {
				ModuleHeaderFileMetadata(configDirectory, it as JSONObject)
			}.toMutableList()

		/**
		 * Write the list of [ModuleHeaderFileMetadata]s to the provided
		 * [JSONWriter].
		 *
		 * @param writer
		 *   The [JSONWriter] to write.
		 * @param headers
		 *   The [List] of [ModuleHeaderFileMetadata]s to write.
		 */
		fun writeTo(writer: JSONWriter, headers: Set<ModuleHeaderFileMetadata>)
		{
			writer.writeArray {
				headers.forEach { write(it) }
			}
		}
	}
}
