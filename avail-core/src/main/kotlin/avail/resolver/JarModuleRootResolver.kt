/*
 * JarModuleRootResolver.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package avail.resolver

import avail.builder.ModuleNameResolver.Companion.availExtension
import avail.builder.ModuleNameResolver.Companion.availExtensionWithSlash
import avail.builder.ModuleRoot
import avail.builder.ModuleRootErrorCode
import avail.error.ErrorCode
import avail.error.StandardErrorCode
import avail.files.FileErrorCode
import avail.files.FileManager
import avail.resolver.ResourceType.DIRECTORY
import avail.resolver.ResourceType.MODULE
import avail.resolver.ResourceType.PACKAGE
import avail.resolver.ResourceType.REPRESENTATIVE
import avail.resolver.ResourceType.RESOURCE
import avail.resolver.ResourceType.ROOT
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import java.util.jar.JarFile
import javax.annotation.concurrent.GuardedBy
import kotlin.concurrent.withLock

/**
 * `JarModuleRootResolver` is a [ModuleRootResolver] used for accessing
 * a [ModuleRoot] that is provided as a jar file.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property name
 *   The [module&#32;root][ModuleRoot] name.
 *
 * @constructor
 * Construct a [JarModuleRootResolver].
 *
 * @param name
 *   The name of the module root.
 * @param uri
 *   The [URI] that identifies the location of the jar file containing all
 *   source modules for a [ModuleRoot].
 * @param fileManager
 *   The [FileManager] used to manage the files accessed via this
 *   [JarModuleRootResolver].
 */
class JarModuleRootResolver
constructor(
	name: String,
	uri: URI,
	fileManager: FileManager
) : ModuleRootResolver(name, uri, fileManager)
{
	override val canSave: Boolean get() = false

	private val jarFileLock = ReentrantLock()

	/** The jar file containing Avail source files. */
	@GuardedBy("jarFileLock")
	private var jarFile: JarFile? = null

	override fun close() {
		jarFileLock.withLock {
			jarFile?.close()
			jarFile = null
		}
	}

	override fun resolvesToValidModuleRoot(): Boolean = File(uri.path).isFile

	override fun resolve(
		successHandler: (ResolverReference)->Unit,
		failureHandler: (ErrorCode, Throwable?)->Unit)
	{
		executeTask {
			val map = mutableMapOf<String, ResolverReference>()
			val rootPrefix = "/${moduleRoot.name}"
			try
			{
				val digests = mutableMapOf<String, ByteArray>()
				val entries = jarFileLock.withLock {
					jarFile = JarFile(uri.path)
					// First, fetch the digests file and populate the digestMap.
					val digestEntry =
						jarFile!!.getEntry(availDigestsPathInJar)!!
					val bytes = ByteArray(digestEntry.size.toInt())
					val stream = DataInputStream(
						BufferedInputStream(
							jarFile!!.getInputStream(digestEntry), 4096))
					stream.readFully(bytes)
					// Decode file as UTF-8.
					val text = String(bytes)
					text.lines()
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
					jarFile!!.entries()
				}
				for (entry in entries.iterator())
				{
					var name = entry.name
					if (!name.startsWith(availSourcesPathInJar)) continue
					name = name.removePrefix(availSourcesPathInJar)
					val type = when
					{
						entry.name.endsWith(availExtensionWithSlash) -> PACKAGE
						name.endsWith("/") -> DIRECTORY
						name.endsWith(availExtension) ->
						{
							assert(!entry.isDirectory)
							val parts = name.split("/")
							when
							{
								(parts.size >= 2
									&& parts.last() == parts[parts.size - 2]
								) -> REPRESENTATIVE
								else -> MODULE
							}
						}
						else -> RESOURCE
					}
					name = name.removeSuffix("/")
					val qualifiedName = name
						.split("/")
						.joinToString("/", prefix = "$rootPrefix/") {
							it.removeSuffix(availExtension)
						}
					val mimeType = when (type)
					{
						MODULE, REPRESENTATIVE -> "text/plain"
						else -> ""
					}
					val reference = ResolverReference(
						this,
						// exact relative path within jar
						URI(null, entry.name, null),
						qualifiedName,
						type,
						mimeType,
						entry.lastModifiedTime.toMillis(),
						entry.size,
						forcedDigest = digests[name])
					map[qualifiedName] = reference
				}
				// Add the root.
				map[rootPrefix] = ResolverReference(
					this,
					URI(rootPrefix),
					rootPrefix,
					ROOT,
					"",
					0,
					0,
					moduleRoot.name)
				// Connect parents to children, and register them all in the
				// referenceMap.
				map.forEach { (name, reference) ->
					referenceMap[reference.qualifiedName] = reference
					if (name.isNotEmpty())
					{
						val parentName = name.substringBeforeLast("/", "")
						// Ignore malformed entries in jar
						val parentNode = map[parentName] ?:
							return@forEach
						when (reference.isResource)
						{
							true -> parentNode.resources.add(reference)
							false -> parentNode.modules.add(reference)
						}
					}
				}
			}
			catch (e: Throwable)
			{
				failureHandler(
					ModuleRootErrorCode.MODULE_ROOT_RESOLUTION_FAILED, e)
				return@executeTask
			}
			val rootReference = map[rootPrefix]!!
			moduleRootTree = rootReference
			successHandler(rootReference)
		}
	}

	override fun rootManifest(
		forceRefresh: Boolean,
		withList: (List<ResolverReference>)->Unit,
		failureHandler: (ErrorCode, Throwable?) -> Unit)
	{
		if (referenceMap.isNotEmpty() && !forceRefresh)
		{
			withList(referenceMap.values.toList())
			return
		}
		executeTask {
			resolve(
				{ withList(referenceMap.values.toList()) },
				failureHandler)
		}
	}

	override fun refreshResolverMetaData(
		reference: ResolverReference,
		successHandler: (Long)->Unit,
		failureHandler: (ErrorCode, Throwable?)->Unit)
	{
		// Assume jars don't change while they're in use.
		executeTask {
			successHandler(reference.lastModified)
		}
	}

	override fun refreshResolverReferenceDigest (
		reference: ResolverReference,
		successHandler: (ByteArray, Long) -> Unit,
		failureHandler: (ErrorCode, Throwable?) -> Unit)
	{
		// Within a jar file, the digest file is the authoritative mechanism for
		// distinguishing versions.  There's nothing to refresh, since the
		// digest and timestamp were capture during jar construction.
		when (val digest = reference.forcedDigest)
		{
			null -> failureHandler(
				FileErrorCode.FILE_NOT_FOUND,
				NoSuchFileException(
					File(reference.qualifiedName),
					reason = "Avail file ${reference.qualifiedName} does not " +
						"occur in Jar"))
			else -> successHandler(digest, reference.lastModified)
		}
	}

	override fun createFile(
		qualifiedName: String,
		mimeType: String,
		completion: ()->Unit,
		failureHandler: (ErrorCode, Throwable?)->Unit)
	{
		// Jars are read-only.
		failureHandler(FileErrorCode.PERMISSIONS, null)
	}

	override fun createPackage(
		qualifiedName: String,
		completion: ()->Unit,
		failureHandler: (ErrorCode, Throwable?)->Unit)
	{
		// Jars are read-only.
		failureHandler(FileErrorCode.PERMISSIONS, null)
	}

	override fun createDirectory(
		qualifiedName: String,
		completion: ()->Unit,
		failureHandler: (ErrorCode, Throwable?)->Unit)
	{
		// Jars are read-only.
		failureHandler(FileErrorCode.PERMISSIONS, null)
	}

	override fun deleteResource(
		qualifiedName: String,
		completion: ()->Unit,
		failureHandler: (ErrorCode, Throwable?)->Unit)
	{
		// Jars are read-only.
		failureHandler(FileErrorCode.PERMISSIONS, null)
	}

	override fun saveFile(
		reference: ResolverReference,
		fileContents: ByteArray,
		successHandler: () -> Unit,
		failureHandler: (ErrorCode, Throwable?)->Unit)
	{
		// Jars are read-only.
		failureHandler(FileErrorCode.PERMISSIONS, null)
	}

	override fun readFile(
		bypassFileManager: Boolean,
		reference: ResolverReference,
		withContents: (ByteArray, UUID?)->Unit,
		failureHandler: (ErrorCode, Throwable?)->Unit)
	{
		require(!setOf(ROOT, DIRECTORY, PACKAGE).contains(reference.type)) {
			"${reference.qualifiedName} is not a file that can be read!"
		}
		if (!bypassFileManager)
		{
			val handled = fileManager.optionallyProvideExistingFile(
				reference,
				{ uuid, availFile ->
					reference.refresh(
						availFile.lastModified,
						availFile.rawContent.size.toLong())
					withContents(availFile.rawContent, uuid)
				},
				failureHandler)
			if (handled) return
		}
		val fileContent = try
		{
			jarFileLock.withLock {
				// We stashed the exact path within the jar inside the
				// schemaSpecificPart of the URI.
				val entry = jarFile!!.getEntry(reference.uri.schemeSpecificPart)
				assert(entry.size.toInt().toLong() == entry.size)
				val bytes = ByteArray(entry.size.toInt())
				val stream = DataInputStream(
					BufferedInputStream(jarFile!!.getInputStream(entry), 4096))
				stream.readFully(bytes)
				bytes
			}
		}
		catch (e: IOException)
		{
			failureHandler(StandardErrorCode.IO_EXCEPTION, e)
			return
		}
		withContents(fileContent, null)
	}

	companion object
	{
		/**
		 * The prefix of paths of Avail *source* file names within this jar
		 * file.
		 */
		const val availSourcesPathInJar = "Avail-Sources/"

		/**
		 * The path within this jar file of the digests file.  The file contains
		 * a series of entries of the form ```<path>:<digest>\n```.
		 */
		const val availDigestsPathInJar = "Avail-Digests/all_digests.txt"
	}
}
