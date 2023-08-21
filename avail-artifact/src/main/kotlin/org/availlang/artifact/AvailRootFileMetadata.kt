package org.availlang.artifact

/**
 * Metadata describing a file/directory found inside an Avail Module Root
 * directory inside an [AvailArtifact] including the root directory itself.
 *
 * @author Richard Arriaga
 *
 * @property path
 *   The relative path of the file inside the [AvailArtifact].
 * @property type
 *   The [ResourceType] that identifies the type of the file.
 * @property qualifiedName
 *   The fully-qualified name of the module or resource.
 * @property mimeType
 *   The file MIME type of the associated resource.
 * @property lastModified
 *   The time in millis since the unix epoch, preferably UTC, when this was last
 *   modified. This value will be zero for directories.
 * @property size
 *   The size in bytes of the file or 0 if a directory.
 * @property digest
 *   The digest associated with the resource.
 */
class AvailRootFileMetadata constructor(
	val path: String,
	val type: ResourceType,
	val qualifiedName: String,
	val mimeType: String,
	val lastModified: Long,
	val size: Long,
	val digest: ByteArray?)
{

	override fun toString(): String = "[${type.name}] $qualifiedName"

	companion object
	{
		/**
		 * The standard extension for Avail module source files.
		 */
		const val availExtension = ".avail"
	}
}
