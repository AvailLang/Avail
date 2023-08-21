package org.availlang.artifact

import org.availlang.artifact.jar.AvailArtifactJar
import org.availlang.artifact.manifest.AvailArtifactManifest

/**
 * An enumeration of the type of the packaging used to create the
 * [AvailArtifact].
 *
 * @author Richard Arriaga
 */
enum class PackageType
{
	/**
	 * The [AvailArtifact] packaged as a JVM Jar file.
	 */
	JAR
	{
		override val currentVersion: Int
			get() = AvailArtifactJar.CURRENT_ARTIFACT_VERSION

		override val artifactDescriptor: ArtifactDescriptor
			get() = ArtifactDescriptor(
				this,
				currentVersion,
				AvailArtifactManifest.CURRENT_MANIFEST_VERSION)
	};

	/**
	 * The current [AvailArtifact] package version for this [PackageType].
	 */
	abstract val currentVersion: Int

	/**
	 * Provide the appropriate [ArtifactDescriptor] that represents all the
	 * current versions of this [PackageType].
	 */
	abstract val artifactDescriptor: ArtifactDescriptor

	/**
	 * Check to see if the provided version is valid for this [PackageType].
	 *
	 * @param proposedVersion
	 *   The proposed version to check.
	 * @return
	 *   `true` if the version is valid; `false` otherwise.
	 */
	fun isValidVersion (proposedVersion: Int): Boolean =
		proposedVersion in 0..currentVersion

	companion object
	{
		/**
		 * Check to see if the provided value is a valid ordinal for a variant
		 * in [PackageType].
		 *
		 * @param proposedOrdinal
		 *   The proposed ordinal to check.
		 * @return
		 *   `true` if the ordinal represents a valid variant of [PackageType];
		 *   `false` otherwise.
		 */
		fun checkOrdinal (proposedOrdinal: Int): Boolean =
			proposedOrdinal in 0 until PackageType.values().size
	}
}
