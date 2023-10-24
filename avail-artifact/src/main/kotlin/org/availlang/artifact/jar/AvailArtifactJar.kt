package org.availlang.artifact.jar

import org.availlang.artifact.ArtifactDescriptor
import org.availlang.artifact.AvailArtifact
import org.availlang.artifact.AvailArtifactException
import org.availlang.artifact.AvailRootFileMetadata
import org.availlang.artifact.DigestUtility
import org.availlang.artifact.ResourceTypeManager
import org.availlang.artifact.manifest.AvailArtifactManifest
import org.availlang.json.jsonObject
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.net.URI
import java.util.Enumeration
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarFile

/**
 * An [AvailArtifact] packaged as a [JarFile].
 *
 * @author Richard Arriaga
 *
 * @property uri
 *   The [URI] that points to the JAR file.
 *
 * @constructor
 * Construct an [AvailArtifactJar].
 *
 * @param uri
 *   The [URI] to the jar file.
 * @throws AvailArtifactException
 *   If there is a problem accessing the [JarFile] at the given [URI].
 */
@Suppress("unused")
class AvailArtifactJar constructor(
	private val uri: URI
): AvailArtifact
{
	/**
	 * The [JarFile] that is the [AvailArtifactJar].
	 */
	private val jarFile: JarFile

	override val artifactDescriptor: ArtifactDescriptor

	/**
	 * An enumeration of the jar file entries.
	 *
	 * @throws IllegalStateException
	 *   If the jar file has been closed.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	val jarFileEntries: Enumeration<JarEntry> get() = jarFile.entries()

	/**
	 * Extracts the [Attributes.Name.IMPLEMENTATION_VERSION] from the manifest.
	 *
	 * @return
	 *   The implementation version or `null` if not present in the [jarFile]
	 *   [manifest][JarFile.getManifest].
	 */
	fun getImplementationVersion(): String? =
		jarFile.manifest
			.mainAttributes[Attributes.Name.IMPLEMENTATION_VERSION] as? String

	override val name: String

	override val manifest by lazy { extractManifest() }

	init
	{
		try
		{
			jarFile = JarFile(uri.path)
			name = jarFile.name
			artifactDescriptor =
				ArtifactDescriptor.from(
					extractFile(ArtifactDescriptor.artifactDescriptorFilePath))
		}
		catch (e: Throwable)
		{
			throw AvailArtifactException(
				"Problem accessing Avail Artifact Jar File: $uri.",
				e)
		}
	}

	/**
	 * Close the backing [JarFile].
	 */
	fun close ()
	{
		jarFile.close()
	}

	/**
	 * Extract the targeted file from the backed [jarFile].
	 *
	 * This task happens synchronously to simplify the complexity of working
	 * with the artifact as it is not deemed necessary for this task to be
	 * overly performant.
	 *
	 * @param filePath
	 *   The file to extract.
	 * @return
	 *   The file contents as a raw [ByteArray].
	 * @throws AvailArtifactException
	 *   If the target file is not retrievable.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	fun extractFile (filePath: String): ByteArray
	{
		val digestEntry = jarFile.getEntry(filePath) ?:
			throw AvailArtifactException(
				"Could not locate $filePath in the JAR file $uri")
		val bytes = ByteArray(digestEntry.size.toInt())
		val stream = DataInputStream(
			BufferedInputStream(jarFile.getInputStream(digestEntry), 4096))
		stream.readFully(bytes)
		return bytes
	}

	/**
	 * Extract the targeted file from a specific root from the backed [jarFile].
	 *
	 * This task happens synchronously to simplify the complexity of working
	 * with the artifact as it is not deemed necessary for this task to be
	 * overly performant.
	 *
	 * @param rootName
	 *   The name of the root to extract the file from.
	 * @param filePath
	 *   The root-relative file to extract.
	 * @return
	 *   The file contents as a raw [ByteArray].
	 * @throws AvailArtifactException
	 *   If the target file is not retrievable.
	 */
	@Suppress("unused")
	fun extractRootFile (rootName: String, filePath: String): ByteArray =
		extractFile("${AvailArtifact.artifactRootDirectory}/$rootName/" +
			"${AvailArtifact.availSourcesPathInArtifact}/$filePath")

	override fun extractManifest(): AvailArtifactManifest
	{
		val rawManifest =
			extractFile(AvailArtifactManifest.availArtifactManifestFile)
		val text = String(rawManifest)
		val json =
			try
			{
				jsonObject(text)
			}
			catch (e: Throwable)
			{
				throw AvailArtifactException(
					"Failure in parsing Avail Manifest, " +
						"${AvailArtifactManifest.availArtifactManifestFile} in " +
							"the JAR file $uri",
					e)
			}
		return AvailArtifactManifest.from(json)
	}

	override fun extractDigestForRoot(rootName: String): Map<String, ByteArray>
	{
		val digestPath = AvailArtifact.rootArtifactDigestFilePath(rootName)
		val digestEntry = jarFile.getEntry(digestPath) ?:
			throw AvailArtifactException(
				"Could not locate digest, $digestPath, for root, $rootName")
		val bytes = ByteArray(digestEntry.size.toInt())
		val stream = DataInputStream(
			BufferedInputStream(
				jarFile.getInputStream(digestEntry), 4096))
		stream.readFully(bytes)
		return DigestUtility.parseDigest(String(bytes))
	}

	override fun extractFileMetadataForRoot(
		rootName: String,
		resourceTypeManager: ResourceTypeManager
	): List<AvailRootFileMetadata>
	{
		val digests = extractDigestForRoot(rootName)
		val entries = jarFile.entries()
		return extractFileMetadataForRoot(
			rootName, uri.fragment, resourceTypeManager, entries, digests)
	}

	/**
	 * Extract the list of [AvailRootFileMetadata] for all the files in the
	 * Avail Root Module for the given root module name.
	 *
	 * @param rootName
	 *   The name of the root to extract metadata for.
	 * @param rootNameInJar
	 *   The name of the root to extract as it exists in the jar.
	 * @param entries
	 *   The [jarFile] [entries][JarFile.entries].
	 * @param digests
	 *   The digest map keyed by the file name to its associated digest bytes.
	 * @return
	 *   The list of extracted [AvailRootFileMetadata].
	 * @throws AvailArtifactException
	 *   Should be thrown if there is trouble accessing the roots files.
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	fun extractFileMetadataForRoot(
		rootName: String,
		rootNameInJar: String,
		resourceTypeManager: ResourceTypeManager,
		entries: Enumeration<JarEntry>,
		digests: Map<String, ByteArray>
	): List<AvailRootFileMetadata> =
		resourceTypeManager.getAvailRootFilesMetadata(
			rootName,
			rootNameInJar,
			entries,
			digests
		).values.toList()

	override fun toString(): String = "$uri"

	companion object
	{
		/**
		 * The version that represents the current structure under which Avail
		 * libs are packaged in the artifact.
		 */
		internal const val CURRENT_ARTIFACT_VERSION = 1
	}
}
