package org.availlang.artifact.environment.project

import org.availlang.artifact.AvailArtifact
import org.availlang.artifact.environment.location.ProjectConfig
import org.availlang.artifact.manifest.AvailArtifactManifest
import org.availlang.artifact.manifest.AvailRootManifest
import org.availlang.json.JSONFriendly
import org.availlang.json.JSONObject
import org.availlang.json.JSONWriter
import org.availlang.json.jsonObject
import java.io.File

/**
 * The expansion text of a template.
 *
 * @author Richard Arriaga
 *
 * @property expansion
 *   The full template expansion string.
 */
data class TemplateExpansion constructor(var expansion: String): JSONFriendly
{
	/**
	 * `true` indicates this [TemplateExpansion] should be included in an
	 * [AvailArtifact]'s [AvailArtifactManifest]'s
	 * [AvailRootManifest.templates]s; `false` otherwise.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	var markedForArtifactInclusion: Boolean = true

	/**
	 * An optional description of this [TemplateExpansion] or `null` if not
	 * used.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	var description: String? = null

	override fun writeTo(writer: JSONWriter)
	{
		writer.writeObject {
			at(::expansion.name) { write(expansion) }
			at(::markedForArtifactInclusion.name)
			{
				write(markedForArtifactInclusion)
			}
			description?.let {
				at(::description.name) { write(description) }
			}
		}
	}

	/**
	 * Construct a [TemplateExpansion] from the provided [JSONObject].
	 *
	 * @param obj
	 *   The [JSONObject] to extract the [TemplateExpansion] from.
	 */
	constructor(
		obj: JSONObject
	): this(obj.getString(TemplateExpansion::expansion.name))
	{
		markedForArtifactInclusion =
			obj.getBoolean(::markedForArtifactInclusion.name)
		description = obj.getStringOrNull(::description.name)
	}
}

/**
 * A group of associated [TemplateExpansion]s.
 *
 * @author Richard Arriaga
 */
class TemplateGroup constructor(
	map: MutableMap<String, TemplateExpansion> = mutableMapOf()
): JSONFriendly
{
	/**
	 * The [TemplateExpansion]s that should be available when editing Avail
	 * source module in the workbench.
 	 */
	val templates = map

	/**
	 * Answer the [TemplateExpansion] associated with the provided name.
	 *
	 * @param name
	 *   The name of the [TemplateExpansion] to retrieve.
	 * @return
	 *   The associated [TemplateExpansion] or `null` if not found.
	 */
	operator fun get (name: String): TemplateExpansion? = templates[name]

	/**
	 * Apply this [TemplateGroup] onto the provided [TemplateGroup].
	 *
	 * @param group
	 *   The [TemplateGroup] to merge onto.
	 * @return
	 *   A new merged [TemplateGroup].
	 */
	@Suppress("unused")
	fun mergeOnto (group: TemplateGroup): TemplateGroup =
		TemplateGroup(group.templates.toMutableMap().apply {
			putAll(templates)
		})

	/**
	 * Answer a [TemplateGroup] that contains all the [TemplateExpansion]s from
	 * this [TemplateGroup] that have been marked as
	 * [TemplateExpansion.markedForArtifactInclusion].
	 */
	val markedForInclusion get() =
		TemplateGroup(
			templates.filter { (_, v) ->
				v.markedForArtifactInclusion
			}.toMutableMap())

	override fun writeTo(writer: JSONWriter)
	{
		writer.writeObject {
			templates.forEach { (name, expansion) ->
				at(name) { write(expansion) }
			}
		}
	}

	/**
	 * Save this [TemplateGroup] to the file indicated by the provided path.
	 *
	 * @param path
	 *   The path to the file where this [TemplateGroup] is to be saved.
	 */
	@Suppress("unused")
	fun saveToDisk (path: String)
	{
		File(path).apply {
			if (!isDirectory)
			{
				writeText(jsonPrettyPrintedFormattedString)
			}
		}
	}

	/**
	 * Construct a [TemplateGroup].
	 *
	 * @param obj
	 *   The [JSONObject] to extract the [TemplateGroup] from.
	 */
	constructor(obj: JSONObject): this()
	{
		obj.associateTo(templates)
		{ (name, expansion) ->
			name to TemplateExpansion(expansion as JSONObject)
		}
	}

	companion object
	{
		/**
		 * Extract the [TemplateGroup] from the configuration file at the
		 * provided [ProjectConfig] location.
		 *
		 * @param config
		 *   The [ProjectConfig] location of the file that contains the
		 *   [TemplateGroup].
		 */
		fun from (config: ProjectConfig): TemplateGroup =
			TemplateGroup(jsonObject(File(config.fullPathNoPrefix).readText()))
	}
}
