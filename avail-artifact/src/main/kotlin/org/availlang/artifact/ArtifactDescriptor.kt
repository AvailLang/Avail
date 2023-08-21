package org.availlang.artifact

import org.availlang.artifact.manifest.AvailArtifactManifest
import java.io.File
import java.nio.ByteBuffer

/**
 * Provides the packaged [AvailArtifact]'s
 * 1. [PackageType]
 * 2. Packaging version indicating the contents of the artifact as well as how
 *    it is packaged.
 * 3. The [AvailArtifactManifest]'s version that indicates the expected
 *    structure of the manifest file.
 *
 * @author Richard Arriaga
 *
 * @property packageType
 *   The [PackageType] of the represented [AvailArtifact].
 * @property version
 *   The packaging version indicating the contents of the artifact as well as
 *   how the [AvailArtifact] is packaged.
 * @property manifestVersion
 *   The [AvailArtifactManifest]'s version that indicates the expected structure
 *   of the manifest file.
 */
data class ArtifactDescriptor constructor(
	val packageType: PackageType,
	val version: Int,
	val manifestVersion: Int)
{
	/**
	 * This [ArtifactDescriptor] as a serialized [ByteArray] that is the
	 * contents of the [artifact descriptor file][artifactDescriptorFilePath].
	 */
	val serializedFileContent: ByteArray get() =
		ByteBuffer.allocate(12).apply {
			putInt(packageType.ordinal)
			putInt(version)
			putInt(manifestVersion)
		}.array()

	/**
	 * Write this [ArtifactDescriptor] to the provided [File].
	 *
	 * @param targetFile
	 *   The file to write to.
	 */
	fun writeFile (targetFile: File)
	{
		targetFile.writeBytes(serializedFileContent)
	}

	companion object
	{
		/**
		 * The name of the [ArtifactDescriptor] file.
		 */
		const val artifactDescriptorFileName = "artifact-descriptor"

		/**
		 * The path of the [ArtifactDescriptor] file.
		 */
		const val artifactDescriptorFilePath =
			"${AvailArtifact.artifactRootDirectory}/$artifactDescriptorFileName"

		/**
		 * Answer an [ArtifactDescriptor] from the provided bytes from an
		 * [artifact descriptor file][artifactDescriptorFilePath].
		 *
		 * @param bytes
		 *   The raw contents of the descriptor file.
		 * @return
		 *   The [ArtifactDescriptor] extracted from the given bytes.
		 * @throws AvailArtifactException
		 *   If the content has malformed or invalid contents.
		 */
		fun from (bytes: ByteArray): ArtifactDescriptor
		{
			if (bytes.size != 12)
			{
				throw AvailArtifactException(
					"Expected the `artifact-descriptor` file to have exactly " +
						"12 bytes: 4-byte int for package type ordinal, " +
						"4-byte int for artifact version, 4-byte int for " +
						"manifest file version.")
			}
			val descriptorBytes = ByteBuffer.wrap(bytes)
			val typeOrdinal = descriptorBytes.int
			if (!PackageType.checkOrdinal(typeOrdinal))
			{
				throw AvailArtifactException(
					"Expected the `artifact-descriptor` file to have a valid " +
						"package type ordinal value: received $typeOrdinal " +
						"but a valid ordianl is in the range [0, " +
						"${PackageType.values().size})")
			}
			val packageType = PackageType.values()[typeOrdinal]
			val version = descriptorBytes.int
			if (!packageType.isValidVersion(version))
			{
				throw AvailArtifactException(
					"Expected the `artifact-descriptor` file to have a valid " +
						"package version: received $version but a valid " +
						"version for a $packageType is in the range [0, " +
						"${packageType.currentVersion}]")
			}
			val manifestVersion = descriptorBytes.int
			if (!AvailArtifactManifest.isValidVersion(manifestVersion))
			{
				throw AvailArtifactException(
					"Expected the `artifact-descriptor` file to have a valid " +
						"manifest version: received $manifestVersion but a " +
						"valid version for is in the range [0, " +
						"${AvailArtifactManifest.CURRENT_MANIFEST_VERSION}]")
			}
			return ArtifactDescriptor(packageType, version, manifestVersion)
		}
	}
}
