package org.availlang.artifact.manifest

import org.availlang.artifact.AvailArtifact
import org.availlang.artifact.AvailArtifactException
import org.availlang.artifact.environment.project.AvailProjectRoot
import org.availlang.artifact.environment.project.StylingGroup
import org.availlang.artifact.environment.project.TemplateGroup
import org.availlang.json.JSONFriendly
import org.availlang.json.JSONObject
import org.availlang.json.JSONWriter
import java.security.MessageDigest

/**
 * Contains information about an Avail Module Root inside an [AvailArtifact].
 *
 * @author Richard Arriaga
 *
 * @property name
 *   The name of the Avail root.
 * @property availModuleExtensions
 *   The file extensions that signify files that should be treated as Avail
 *   modules.
 * @property entryPoints
 *   The Avail entry points exposed by this root.
 * @property templates
 *   The [TemplateGroup] originating from the associated [AvailProjectRoot].
 * @property styles
 *   The [StylingGroup] originating from the associated [AvailProjectRoot].
 * @property description
 *   A description of the root.
 * @property digestAlgorithm
 *   The [MessageDigest] algorithm to use to create the digests for all the
 *   root's contents. This must be a valid algorithm accessible from
 *   [java.security.MessageDigest.getInstance].
 */
data class AvailRootManifest constructor(
	val name: String,
	val availModuleExtensions: MutableList<String>,
	val entryPoints: MutableList<String> = mutableListOf(),
	val templates: TemplateGroup = TemplateGroup(),
	val styles: StylingGroup = StylingGroup(),
	val description: String = "",
	val digestAlgorithm: String = "SHA-256"
): JSONFriendly
{
	override fun writeTo(writer: JSONWriter)
	{
		writer.writeObject {
			at(::name.name) { write(name) }
			at(::description.name) { write(description) }
			at(::digestAlgorithm.name) { write(digestAlgorithm) }
			at(::availModuleExtensions.name) {
				writeStrings(availModuleExtensions)
			}
			at(::entryPoints.name) { writeStrings(entryPoints) }
			at(::styles.name) { write(styles) }
			at(::templates.name) { write(templates) }
		}
	}

	companion object
	{
		/**
		 * Extract an [AvailRootManifest] from the given [JSONObject].
		 *
		 * @param obj
		 *   The [JSONObject] to extract the data from.
		 * @return
		 *   The extracted [AvailRootManifest].
		 * @throws AvailArtifactException
		 *   If there is an issue with extracting the [AvailRootManifest].
		 */
		fun from (obj: JSONObject): AvailRootManifest
		{
			val name =
				try
				{
					obj.getString(AvailRootManifest::name.name)
				}
				catch (e: Throwable)
				{
					throw AvailArtifactException(
						"Problem extracting Avail Manifest Root name.", e)
				}

			return AvailRootManifest(
				name = name,
				availModuleExtensions =
					obj.getArrayOrNull(
						AvailRootManifest::availModuleExtensions.name
					)?.strings?.toMutableList() ?: mutableListOf(),
				entryPoints =
					obj.getArrayOrNull(
						AvailRootManifest::entryPoints.name
					)?.strings?.toMutableList() ?: mutableListOf(),
				templates = obj.getObjectOrNull(
					AvailRootManifest::templates.name)?.let {
						TemplateGroup(it)
					} ?: TemplateGroup(),
				styles = obj.getObjectOrNull(
					AvailProjectRoot::styles.name
				)?.let {
					StylingGroup(it)
				} ?: StylingGroup(),
				description = obj.getStringOrNull(
					AvailRootManifest::description.name) ?: "",
				digestAlgorithm = obj.getStringOrNull(
					AvailRootManifest::digestAlgorithm.name) ?: "256")
		}
	}
}
