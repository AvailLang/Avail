package org.availlang.artifact

import java.io.File
import java.security.MessageDigest

/**
 * Utility file for constructing Avail Root Module digests for a packaged Avail
 * artifact.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Richard Arriaga
 */
object DigestUtility
{
	/**
	 * Construct the contents of a single file that summarizes message digests
	 * of all files of an Avail Module root directory structure.  Each input
	 * file is read, digested with the specified [MessageDigest] algorithm
	 * (default is SHA-256), and written to the digests contents. The entries
	 * are the file name relative to the basePath, a colon, the hex
	 * representation of the digest of that file, and a linefeed.
	 *
	 * @param rootPath
	 *   The path to the Avail root module directory to create a digest for.
	 * @param digestAlgorithm
	 *   The [MessageDigest] algorithm to use to create the digest. This must be
	 *   a valid algorithm accessible from
	 *   [java.security.MessageDigest.getInstance].
	 * @return
	 *   The digest contents.
	 * @throws AvailArtifactException
	 *   If the provided `rootPath` is not a [directory][File.isDirectory].
	 */
	fun createDigest (
		rootPath: String,
		digestAlgorithm: String = "SHA-256"): String
	{
		val root = File(rootPath)
		if (!root.isDirectory)
		{
			throw AvailArtifactException(
				"Failed to create a module root digest; provided root path, " +
						"$rootPath, is not a directory")
		}
		return buildString {
			root.walk()
				.filter { it.isFile }
				.forEach { file ->
					val digest = MessageDigest.getInstance(digestAlgorithm)
					digest.update(file.readBytes())
					val hex = digest.digest()
						.joinToString("") { String.format("%02X", it) }
					append("${file.toRelativeString(root).replace("\\", "/")}:$hex\n")
				}
		}
	}

	/**
	 * Construct the contents of a single file that summarizes message digests
	 * of all files of an Avail Module root directory structure then write it to
	 * the provided target file.  Each input file is read, digested with the
	 * specified [MessageDigest] algorithm (default is SHA-256), and written to
	 * the digests contents. The entries are the file name relative to the
	 * basePath, a colon, the hex representation of the digest of that file, and
	 * a linefeed.
	 *
	 * @param rootPath
	 *   The path to the Avail root module directory to create a digest for.
	 * @param targetFile
	 *   The file to write the digest to.
	 * @param digestAlgorithm
	 *   The [MessageDigest] algorithm to use to create the digest.
	 * @return
	 *   The digest contents.
	 * @throws AvailArtifactException
	 *   If the provided `rootPath` is not a [directory][File.isDirectory].
	 */
	fun writeDigestFile (
		rootPath: String,
		targetFile: File,
		digestAlgorithm: String = "SHA-256")
	{
		targetFile.writeText(createDigest(rootPath, digestAlgorithm))
	}

	/**
	 * Extract the digest's contents into a map keyed by the file name.
	 *
	 * @param digestFileContent
	 *   The contents of the digest file to parse.
	 * @return
	 *   A map from file name to the associated digest bytes.
	 */
	fun parseDigest (digestFileContent: String): Map<String, ByteArray>
	{
		val digests = mutableMapOf<String, ByteArray>()
		digestFileContent.lines()
			.filter(String::isNotEmpty)
			.forEach { line ->
				val (innerFileName, digestString) = line.split(":")
				val digestBytes =
					ByteArray(digestString.length ushr 1) { i ->
						digestString
							.substring(i shl 1, (i shl 1) + 2)
							.toInt(16)
							.toByte()
					}
				digests[innerFileName] = digestBytes
			}
		return digests
	}
}
