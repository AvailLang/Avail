package org.availlang.artifact.manifest

import org.availlang.artifact.AvailArtifactException
import org.availlang.artifact.AvailArtifactType
import org.availlang.artifact.formattedNow
import org.availlang.json.JSONObject
import org.availlang.json.JSONWriter

/**
 * Version 1 of the [AvailArtifactManifest].
 *
 * @author Richard Arriaga
 *
 * @constructor
 * Construct an [AvailArtifactManifestV1].
 *
 * @param artifactType
 *   The [AvailArtifactType] that describes the nature of the Avail artifact.
 * @param constructed
 *   The UTC timestamp that represents when the artifact was constructed. Must
 *   be created using [formattedNow] when newly constructing an artifact.
 * @param roots
 *   The map of the [AvailRootManifest]s keyed by [AvailRootManifest.name]
 *   that are present in the artifact.
 * @param description
 *   A description of the artifact.
 */
class AvailArtifactManifestV1 constructor (
	override val artifactType: AvailArtifactType,
	override val constructed: String,
	override val roots: Map<String, AvailRootManifest>,
	override val description: String = ""
): AvailArtifactManifest
{
	override val artifactVersion: Int = 1

	override fun toString(): String  = "$artifactType"

	override fun writeTo(writer: JSONWriter)
	{
		writer.writeObject {
			at(::artifactVersion.name) { write(artifactVersion) }
			at(::artifactType.name) { write(artifactType.name) }
			at(::constructed.name) { write(constructed) }
			at(::description.name) { write(description) }
			at(::roots.name) { writeArray(roots.values) }
		}
	}

	companion object
	{
		/**
		 * Answer an [AvailArtifactManifest] from the provided [JSONObject].
		 *
		 * @param obj
		 *   The [JSONObject] to extract the [AvailArtifactManifest] from.
		 * @return
		 *   The extracted [AvailArtifactManifest].
		 * @throws AvailArtifactException
		 *   If there is any problem extracting the [AvailArtifactManifest].
		 */
		internal fun fromJSON (obj: JSONObject): AvailArtifactManifest
		{
			val typeName =
				try
				{
					obj.getString(AvailArtifactManifest::artifactType.name)
				}
				catch (e: Throwable)
				{
					throw AvailArtifactException(
						"Problem accessing Avail Artifact Manifest Type.", e)
				}
			val type =
				try
				{
					AvailArtifactType.valueOf(typeName)
				}
				catch (e: IllegalArgumentException)
				{
					throw AvailArtifactException(
						"Invalid Avail Artifact Type: $typeName")
				}
			val constructed =
				try
				{
					obj.getString(AvailArtifactManifest::constructed.name)
				}
				catch (e: Throwable)
				{
					throw AvailArtifactException(
						"Problem accessing Avail Artifact Manifest " +
								"construction timestamp.",
						e)
				}
			val description =
				try
				{
					obj.getString(AvailArtifactManifest::description.name)
				}
				catch (e: Throwable)
				{
					throw AvailArtifactException(
						"Problem accessing Avail Artifact Manifest " +
							"description.",
						e)
				}
			val roots =
				try
				{
					obj.getArray(AvailArtifactManifest::roots.name)
						.map { AvailRootManifest.from(it as JSONObject) }
						.associateBy { it.name }
				}
				catch (e: Throwable)
				{
					throw AvailArtifactException(
						"Problem accessing Avail Artifact Manifest Roots.", e)
				}
			return AvailArtifactManifestV1(
				type, constructed, roots, description)
		}
	}
}
