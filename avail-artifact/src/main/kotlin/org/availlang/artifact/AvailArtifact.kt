package org.availlang.artifact

import org.availlang.artifact.manifest.AvailArtifactManifest

/**
 * Represents a packaged Avail artifact.
 *
 * @author Richard Arriaga
 */
interface AvailArtifact
{
	/**
	 * The name of the Avail artifact.
	 */
	val name: String

	/**
	 * This [AvailArtifact]'s [AvailArtifactManifest].
	 */
	val manifest: AvailArtifactManifest

	/**
	 * The [ArtifactDescriptor] that describes this [AvailArtifact].
	 */
	val artifactDescriptor: ArtifactDescriptor

	/**
	 * @return
	 *   Extract and return the [AvailArtifactManifest] from this
	 *   [AvailArtifact].
	 * @throws AvailArtifactException
	 *   Should be thrown if there is trouble accessing the
	 *   [AvailArtifactManifest].
	 */
	fun extractManifest (): AvailArtifactManifest

	/**
	 * Extract the map of file digests keyed by the file name.
	 *
	 * @param rootName
	 *   The name of the root to extract digest map for.
	 * @return
	 *   The digest map keyed by the file name to its associated digest bytes.
	 * @throws AvailArtifactException
	 *   Should be thrown if there is trouble extracting the digest.
	 */
	fun extractDigestForRoot (
		rootName: String
	): Map<String, ByteArray>

	/**
	 * Extract the list of [AvailRootFileMetadata] for all the files in the
	 * Avail Root Module for the given root module name.
	 *
	 * @param rootName
	 *   The name of the root to extract metadata for.
	 * @return
	 *   The list of extracted [AvailRootFileMetadata].
	 * @throws AvailArtifactException
	 *   Should be thrown if there is trouble accessing the roots files.
	 */
	fun extractFileMetadataForRoot (
		rootName: String
	): List<AvailRootFileMetadata>

	companion object
	{
		/**
		 * The name of the root directory in which all the contents of the
		 * [AvailArtifact] is stored.
		 */
		const val artifactRootDirectory = "avail-artifact-contents"

		/**
		 * The prefix of paths of Avail Module Root *source* file names within
		 * the artifact.
		 */
		const val availSourcesPathInArtifact = "Avail-Sources"

		/**
		 * The prefix of paths of Avail Module Root *source* file names within
		 * the artifact.
		 */
		const val availDigestsPathInArtifact = "Avail-Digests"

		/**
		 * The name of the root digests file.
		 */
		const val digestsFileName = "all_digests.txt"

		/**
		 * The path within this artifact of the digests file. The file contains
		 * a series of entries of the form ```<path>:<digest>\n```.
		 */
		const val availDigestsFilePathInArtifact =
			"$availDigestsPathInArtifact/$digestsFileName"

		/**
		 * Create the base path for the directory where the Avail root sources
		 * will be stored.
		 *
		 * @param rootName
		 *   The name of the root to construct the path for.
		 * @return
		 *   The source files path in the artifact.
		 */
		fun rootArtifactSourcesDir (rootName: String) =
			"${artifactRootDirectory}/$rootName/$availSourcesPathInArtifact"

		/**
		 * Create the path for the digests directory in the artifact for the
		 * given root.
		 *
		 * @param rootName
		 *   The name of the root to construct the path for.
		 * @return
		 *   The digests file path in the artifact.
		 */
		fun rootArtifactDigestDirPath (rootName: String) =
			"${artifactRootDirectory}/$rootName"

		/**
		 * Create the file path for the digests file in the artifact for the
		 * given root.
		 *
		 * @param rootName
		 *   The name of the root to construct the path for.
		 * @return
		 *   The digests file path in the artifact.
		 */
		fun rootArtifactDigestFilePath (rootName: String) =
			"${rootArtifactDigestDirPath(rootName)}/$availDigestsFilePathInArtifact"
	}
}
