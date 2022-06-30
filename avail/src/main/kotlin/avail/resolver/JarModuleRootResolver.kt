/*
 * JarModuleRootResolver.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

import avail.builder.ModuleRoot
import avail.builder.ModuleRootErrorCode
import avail.error.ErrorCode
import avail.error.StandardErrorCode
import avail.files.FileErrorCode
import avail.files.FileManager
import org.availlang.artifact.ResourceType.DIRECTORY
import org.availlang.artifact.ResourceType.PACKAGE
import org.availlang.artifact.ResourceType.ROOT
import org.availlang.artifact.jar.AvailArtifactJar
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
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

	/**
	 * The [AvailArtifactJar] that wraps the target jar.
	 */
	@GuardedBy("jarFileLock")
	private var artifactJar = AvailArtifactJar(uri)

	override fun close() {
		jarFileLock.withLock {
			artifactJar.close()
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
				val digests: Map<String, ByteArray>
				val entries = jarFileLock.withLock {
					digests = artifactJar.extractDigestForRoot(name)
					artifactJar.jarFileEntries
				}
				artifactJar.extractFileMetadataForRoot(name, entries, digests)
					.forEach {
						val reference = ResolverReference(
							this,
							// exact relative path within jar
							URI(null, it.path, null),
							it.qualifiedName,
							it.type,
							it.mimeType,
							it.lastModified,
							it.size,
							forcedDigest = digests[it.path])
						map[it.qualifiedName] = reference
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
				artifactJar.extractFile(reference.uri.schemeSpecificPart)
			}
		}
		catch (e: IOException)
		{
			failureHandler(StandardErrorCode.IO_EXCEPTION, e)
			return
		}
		withContents(fileContent, null)
	}
}
