/*
 * FileSystemModuleRootResolver.kt
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

import avail.AvailRuntime
import avail.builder.ModuleNameResolver.Companion.availExtension
import avail.builder.ModuleRoot
import avail.builder.ModuleRootErrorCode
import avail.builder.ModuleRoots
import avail.error.ErrorCode
import avail.error.StandardErrorCode
import avail.files.FileErrorCode
import avail.files.FileManager
import avail.io.SimpleCompletionHandler
import avail.resolver.ModuleRootResolver.WatchEventType.CREATE
import avail.resolver.ModuleRootResolver.WatchEventType.DELETE
import avail.resolver.ModuleRootResolver.WatchEventType.MODIFY
import avail.resolver.ResourceType.DIRECTORY
import avail.resolver.ResourceType.MODULE
import avail.resolver.ResourceType.PACKAGE
import avail.resolver.ResourceType.REPRESENTATIVE
import avail.resolver.ResourceType.RESOURCE
import avail.resolver.ResourceType.ROOT
import io.methvin.watcher.DirectoryChangeEvent
import io.methvin.watcher.DirectoryChangeEvent.EventType
import io.methvin.watcher.DirectoryWatcher
import io.methvin.watcher.hashing.FileHasher
import org.apache.tika.Tika
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.CompletionHandler
import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.nio.file.WatchService
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.EnumSet
import java.util.LinkedList
import java.util.UUID
import kotlin.concurrent.thread

/**
 * `FileSystemModuleRootResolver` is a [ModuleRootResolver] used for accessing
 * a [ModuleRoot] from the local file system.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property name
 *   The [module&#32;root][ModuleRoot] name.
 *
 * @constructor
 * Construct a [FileSystemModuleRootResolver].
 *
 * @param name
 *   The name of the module root.
 * @param uri
 *   The [URI] that identifies the location of the [ModuleRoot].
 * @param fileManager
 *   The [FileManager] used to manage the files accessed via this
 *   [FileSystemModuleRootResolver].
 */
@Suppress("RemoveRedundantQualifierName")
class FileSystemModuleRootResolver constructor(
	name: String,
	uri: URI,
	fileManager: FileManager
) : ModuleRootResolver(name, uri, fileManager)
{
	override fun close() = fileSystemWatcher.close()

	override fun resolvesToValidModuleRoot(): Boolean = File(uri).isDirectory

	override fun resolve(
		successHandler: (ResolverReference)->Unit,
		failureHandler: (ErrorCode, Throwable?)->Unit)
	{
		executeTask {
			val directory = Paths.get(uri).toFile()
			if (directory.isDirectory)
			{
				try
				{
					Files.walkFileTree(
						Paths.get(directory.absolutePath),
						EnumSet.of(FileVisitOption.FOLLOW_LINKS),
						Integer.MAX_VALUE,
						sourceModuleVisitor())
				}
				catch (e: IOException)
				{
					// This shouldn't happen, since we never raise any
					// exceptions in the visitor.
				}
				val tree = moduleRootTree
				if (tree !== null)
				{
					successHandler(tree)
					return@executeTask
				}
			}
			failureHandler(
				ModuleRootErrorCode.MODULE_ROOT_RESOLUTION_FAILED,
				null)
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
				{
					withList(referenceMap.values.toList())
				},
				failureHandler)
		}
	}

	/**
	 * Answer the [ResolverReference] for the given absolute [Path].
	 *
	 * @param path
	 *   The `Path` to the target.
	 * @param qualifiedName
	 *   The qualified name of the file.
	 * @param qualifiedName
	 *   The [ResolverReference.qualifiedName].
	 */
	fun resolverReference(
		path: Path,
		qualifiedName: String,
		type: ResourceType?): ResolverReference
	{
		val file = path.toFile()
		if (!file.exists())
		{
			throw NoSuchFileException(file, reason = "$path not found")
		}
		val resourceType = type ?: determineResourceType(file)
		val isPackage = file.isDirectory
		val lastModified = file.lastModified()
		val size = if (isPackage) 0 else file.length()
		val mimeType = when
		{
			isPackage -> ""
			file.extension == "avail" -> "text/plain"  // For performance.
			else -> tika.detect(path)
		}
		val qname = qualifiedName.replace(availExtension, "")
		return ResolverReference(
			this,
			path.toUri(),
			qname,
			resourceType,
			mimeType,
			lastModified,
			size)
	}

	/**
	 * Answer the appropriate [ResourceType] for the provided [File].
	 *
	 * @param file
	 *   The `File` to check.
	 * @return
	 *   The `ResourceType`.
	 */
	private fun determineResourceType (file: File): ResourceType
	{
		val fileName = file.absolutePath
		return if (fileName.endsWith(availExtension))
		{

			if (file.isDirectory) PACKAGE
			else
			{
				val components = fileName.split("/")
				val parent =
					if (components.size > 1)
					{
						components[components.size - 2].split(availExtension)[0]
					}
					else ""

				val localName = fileName.substring(
					0,
					fileName.length - availExtension.length)
				if (parent == localName) REPRESENTATIVE
				else MODULE
			}
		}
		else
		{
			if (file.isDirectory) DIRECTORY
			else RESOURCE
		}
	}

	override fun refreshResolverMetaData(
		reference: ResolverReference,
		successHandler: (Long)->Unit,
		failureHandler: (ErrorCode, Throwable?)->Unit)
	{
		executeTask {
			try
			{
				val file = Paths.get(reference.uri).toFile()
				val modified = file.lastModified()
				reference.refresh(modified, file.length())
				successHandler(modified)
			}
			catch (e: Throwable)
			{
				failureHandler(
					StandardErrorCode.IO_EXCEPTION,
					IOException(
						"Could not refresh file metadata for " +
							reference.qualifiedName,
						e))
			}
		}
	}

	override fun refreshResolverReferenceDigest (
		reference: ResolverReference,
		successHandler : (ByteArray, Long) -> Unit,
		failureHandler: (ErrorCode, Throwable?) -> Unit)
	{
		try
		{
			val f = Paths.get(reference.uri).toFile()
			val initialModified = f.lastModified()
			readFile(
				false,
				reference,
				{ bytes, _ ->
					val modified = f.lastModified()
					if (modified != initialModified)
					{
						System.err.println(
							"(${reference.qualifiedName}) File changed " +
								"during digest calculation: modified " +
								"timestamp at file read start " +
								"$initialModified, at finish $modified")
					}
					val hasher = MessageDigest.getInstance(
						ResolverReference.DIGEST_ALGORITHM)
					hasher.update(bytes, 0, bytes.size)
					val newDigest = hasher.digest()
					reference.refresh(modified, f.length())
					successHandler(newDigest, modified)
				},
				failureHandler)
		}
		catch (e: NoSuchFileException)
		{
			failureHandler(FileErrorCode.FILE_NOT_FOUND, e)
		}
		catch (e: SecurityException)
		{
			failureHandler(FileErrorCode.PERMISSIONS, e)
		}
	}

	override fun createFile(
		qualifiedName: String,
		mimeType: String,
		completion: ()->Unit,
		failureHandler: (ErrorCode, Throwable?)->Unit)
	{
		TODO("Not yet implemented")
		// TODO RAA add ResolverReference to referenceMap and reference tree
	}

	override fun createPackage(
		qualifiedName: String,
		completion: ()->Unit,
		failureHandler: (ErrorCode, Throwable?)->Unit)
	{
		TODO("Not yet implemented")
		// TODO RAA must create package and module representative
		// TODO add ResolverReferences to referenceMap and reference tree
	}

	override fun createDirectory(
		qualifiedName: String,
		completion: ()->Unit,
		failureHandler: (ErrorCode, Throwable?)->Unit)
	{
		TODO("Not yet implemented")
		// TODO RAA add ResolverReference to referenceMap and reference tree
	}

	override fun deleteResource(
		qualifiedName: String,
		completion: ()->Unit,
		failureHandler: (ErrorCode, Throwable?)->Unit)
	{
		TODO("Not yet implemented")
		// TODO delete all children, update reference map and reference tree.
	}

	/**
	 * Save the data to disk starting at the specified write location.
	 *
	 * @param data
	 *   The [ByteBuffer] to save containing the contents to save.
	 * @param writePosition
	 *   The position in the file to start writing to.
	 * @param failureHandler
	 *   A function that accepts a [ErrorCode] that describes the nature
	 *   of the failure and an optional [Throwable].
	 */
	private fun save(
		file: AsynchronousFileChannel,
		data: ByteBuffer,
		writePosition: Long,
		failureHandler: (ErrorCode, Throwable?)->Unit)
	{
		file.write(
			data,
			writePosition,
			null,
			object : CompletionHandler<Int, ErrorCode?>
			{
				override fun completed(
					result: Int?,
					attachment: ErrorCode?)
				{
					if (data.hasRemaining())
					{
						save(
							file,
							data,
							data.position().toLong(),
							failureHandler)
					}
				}

				override fun failed(
					exc: Throwable?,
					attachment: ErrorCode?)
				{
					failureHandler(attachment ?: FileErrorCode.UNSPECIFIED, exc)
				}
			})
	}

	override fun saveFile(
		reference: ResolverReference,
		fileContents: ByteArray,
		successHandler: () -> Unit,
		failureHandler: (ErrorCode, Throwable?)->Unit)
	{
		val data = ByteBuffer.wrap(fileContents)
		val file = fileManager.ioSystem.openFile(
			absolutePath(uri, reference.qualifiedName),
			EnumSet.of(
				StandardOpenOption.TRUNCATE_EXISTING,
				StandardOpenOption.WRITE))
		file.write(
			data,
			0,
			null,
			object : CompletionHandler<Int, ErrorCode?>
			{
				override fun completed(
					result: Int?,
					attachment: ErrorCode?)
				{
					if (data.hasRemaining())
					{
						save(
							file,
							data,
							data.position().toLong(),
							failureHandler)
					}
					else
					{
						reference.refresh(
							System.currentTimeMillis(),
							fileContents.size.toLong())
						successHandler()
					}
				}

				override fun failed(
					exc: Throwable?,
					attachment: ErrorCode?)
				{
					failureHandler(
						attachment ?: FileErrorCode.UNSPECIFIED,
						exc)
				}
			})
	}

	override fun readFile(
		bypassFileManager: Boolean,
		reference: ResolverReference,
		withContents: (ByteArray, UUID?)->Unit,
		failureHandler: (ErrorCode, Throwable?)->Unit)
	{
		if (setOf(ROOT, DIRECTORY, PACKAGE).contains(reference.type))
		{
			failureHandler(
				StandardErrorCode.IO_EXCEPTION,
				IOException(
					"${reference.qualifiedName} is a directory, not a file",
					null))
			return
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
		val file: AsynchronousFileChannel
		try
		{
			file = fileManager.ioSystem.openFile(
				Paths.get(reference.uri), EnumSet.of(StandardOpenOption.READ))
		}
		catch (e: IOException)
		{
			val ex = IOException("Failed to read source: ${reference.uri}", e)
			failureHandler(StandardErrorCode.IO_EXCEPTION, ex)
			return
		}

		var filePosition = 0L
		val buffer = ByteBuffer.allocateDirect(4096)
		var content = ByteArray(0)
		SimpleCompletionHandler<Int>(
			{
				try
				{
					var moreInput = true
					if (value == -1)
					{
						moreInput = false
					}
					else
					{
						val pos = value.toLong()
						filePosition += pos
					}
					// If more input remains, then queue another read.
					if (moreInput)
					{
						// Destructively acquire the data from the buffer.
						buffer.flip()
						val data = ByteArray(buffer.limit())
						buffer.get(data)
						content += data
						buffer.clear()
						handler.guardedDo {
							file.read(buffer, filePosition, Unit, handler)
						}
					}
					else
					{
						file.close()
						withContents(content, null)
					}
				}
				catch (e: IOException)
				{
					try
					{
						file.close()
					}
					catch (e: Throwable)
					{
						// Do nothing
					}
					failureHandler(StandardErrorCode.IO_EXCEPTION, e)
				}
			},
			{
				try
				{
					file.close()
				}
				catch (e: Throwable)
				{
					// Do nothing
				}
				val ex =
					IOException("Failed to read: ${reference.uri}", throwable)
				ex.printStackTrace()
				failureHandler(StandardErrorCode.IO_EXCEPTION, ex)
			}).guardedDo { file.read(buffer, 0L, Unit, handler) }
	}

	/**
	 * The [FileSystemWatcher] used to monitor the [ModuleRoots] for system
	 * changes.
	 */
	private val fileSystemWatcher = FileSystemWatcher()

	/**
	 * Answer a [visitor][FileVisitor] able to visit every source module
	 * beneath the [module root][moduleRoot].
	 *
	 * @return
	 *   A `FileVisitor`.
	 */
	private fun sourceModuleVisitor(): FileVisitor<Path>
	{
		var isRoot = true
		val stack = ArrayDeque<ResolverReference>()
		return object : FileVisitor<Path>
		{
			override fun preVisitDirectory(
				dir: Path,
				attrs: BasicFileAttributes
			): FileVisitResult
			{
				// If this directory is a root, then create its node now and
				// then recurse into it. Turn off the isRoot flag.
				if (isRoot)
				{
					var dirURI = dir.toUri()
					if (dirURI.scheme === null)
					{
						dirURI = URI("file://$dir")
					}
					isRoot = false
					val qualifiedName = "/${moduleRoot.name}"
					val reference = ResolverReference(
						this@FileSystemModuleRootResolver,
						dirURI,
						qualifiedName,
						ROOT,
						"",
						0,
						0,
						moduleRoot.name)

					referenceMap[qualifiedName] = reference
					moduleRootTree = reference
					stack.add(reference)
					return FileVisitResult.CONTINUE
				}
				val parent = stack.peekFirst()!!
				// The directory is not a root. If it has an Avail
				// extension, then it is a package.
				val fileName = dir.fileName.toString()
				if (fileName.endsWith(availExtension))
				{
					val localName = fileName.removeSuffix(availExtension)
					val qualifiedName = "${parent.qualifiedName}/$localName"
					var dirURI = dir.toUri()
					if (dirURI.scheme === null)
					{
						dirURI = URI("file://$dir")
					}
					val reference = ResolverReference(
						this@FileSystemModuleRootResolver,
						dirURI,
						qualifiedName,
						ResourceType.PACKAGE,
						"",
						0,
						0)
					referenceMap[qualifiedName] = reference
					stack.addFirst(reference)
					parent.modules.add(reference)
					return FileVisitResult.CONTINUE
				}
				// This is an ordinary directory.
				val qualifiedName = "${parent.qualifiedName}/$fileName"
				var dirURI = dir.toUri()
				if (dirURI.scheme === null)
				{
					dirURI = URI("file://$dir")
				}
				val reference = ResolverReference(
					this@FileSystemModuleRootResolver,
					dirURI,
					qualifiedName,
					ResourceType.DIRECTORY,
					"",
					0,
					0)
				referenceMap[qualifiedName] = reference
				stack.addFirst(reference)
				parent.resources.add(reference)
				return FileVisitResult.CONTINUE
			}

			override fun postVisitDirectory(
				dir: Path,
				e: IOException?
			): FileVisitResult
			{
				stack.removeFirst()
				return FileVisitResult.CONTINUE
			}

			override fun visitFile(
				file: Path,
				attrs: BasicFileAttributes
			): FileVisitResult
			{
				// The root should be a directory, not a file.
				if (isRoot)
				{
					throw IOException("alleged root is not a directory")
				}

				val fileName = file.fileName.toString()
				if (fileName.uppercase() == ".DS_STORE")
				{
					// Mac file to be ignored
					return FileVisitResult.CONTINUE
				}

				// A file with an Avail extension is an Avail module.
				val parent = stack.peekFirst()!!
				if (fileName.endsWith(availExtension))
				{
					val localName = fileName.substring(
						0, fileName.length - availExtension.length)
					val type =
						if (parent.isPackage && parent.localName == localName)
							ResourceType.REPRESENTATIVE
						else
							ResourceType.MODULE

					val qualifiedName = "${parent.qualifiedName}/$localName"
					val reference = resolverReference(file, qualifiedName, type)
					referenceMap[qualifiedName] = reference
					parent.modules.add(reference)
				}
				// Otherwise, it is a resource.
				else
				{
					val qualifiedName = "${parent.qualifiedName}/$fileName"
					val reference =
						resolverReference(
							file, qualifiedName, ResourceType.RESOURCE)
					referenceMap[qualifiedName] = reference
					parent.resources.add(reference)
				}
				return FileVisitResult.CONTINUE
			}

			override fun visitFileFailed(
				file: Path,
				e: IOException): FileVisitResult
			{
				return FileVisitResult.CONTINUE
			}
		}
	}

	companion object
	{
		/**
		 * Create the [Tika] mime detector once, lazily.
		 */
		val tika: Tika by lazy { Tika() }

		/**
		 * Answer a [Path] for a given [ModuleRootResolver.uri] and
		 * [ResolverReference.qualifiedName], transforming the package names to
		 * include the [availExtension].
		 *
		 * So, a module root at `file:///Users/Someone/foo` with a qualified
		 * name of an Avail module, 'Bar/Baz/Cat`, will result in the `Path`:
		 * `file:///Users/Someone/foo/Bar.avail/Baz.avail/Cat.avail`.
		 *
		 * @param rootURI
		 *   The target `ModuleRootResolver.uri`.
		 * @param qualifiedName
		 *   The target `ResolverReference.qualifiedName`.
		 * @return The resolved absolute `Path`.
		 */
		fun absolutePath (
			rootURI: URI,
			qualifiedName: String
		): Path = Paths.get(
			buildString {
				append(rootURI)
				for (part in qualifiedName.split("/"))
				{
					append('/')
					append(part)
					append(availExtension)
				}
			})
	}

	/**
	 * `FileSystemWatcher` manages the [WatchService] responsible for watching
	 * the local file system directories of the [ModuleRoot]s loaded into the
	 * AvailRuntime.
	 */
	inner class FileSystemWatcher
	{
		/**
		 * The [WatchService] watching the [FileManager] directories where
		 * the [AvailRuntime] loaded [ModuleRoot]s are stored.
		 */
		private val directoryWatcher = DirectoryWatcher.builder()
			.fileHasher(FileHasher.LAST_MODIFIED_TIME)
			.listener { e -> resolveEvent(e) }
			.path(Paths.get(moduleRoot.resolver.uri))
			.build()!!
			.apply {
				// Allocate a dedicated thread to observing changes to the
				// filesystem.
				thread (
					isDaemon = true,
					name = "file system observer"
				) {
					while (true)
					{
						try
						{
							watch()
							break
						}
						catch (t: Throwable)
						{
							// Try again.
						}
					}
				}
			}

		/**
		 * Shutdown this [FileSystemWatcher].
		 */
		fun close ()
		{
			directoryWatcher.close()
		}

		private fun resolveEvent (event: DirectoryChangeEvent)
		{
			// Mac stuff to ignore.
			val path = event.path()
			if (path.endsWith(".DS_Store"))
			{
				return
			}
			val base = moduleRoot.resolver.uri
			val uri = event.path()?.toUri() ?: return
			val file = File(base.resolve(uri))
			val isDirectory = file.isDirectory
			val eventType = event.eventType()
			if (isDirectory
				&& (eventType == EventType.MODIFY
					|| eventType == EventType.CREATE))
			{
				return
			}
			val qualifiedName = getQualifiedName(file.toString())
			when (eventType)
			{
				EventType.DELETE ->
				{
					val ref = referenceMap.remove(qualifiedName) ?: return
					val parent = referenceMap[ref.parentName]
					if (parent !== null)
					{
						val children = when (ref.isResource)
						{
							true -> parent.resources
							false -> parent.modules
						}
						children.remove(ref)
					}
					watchEventSubscriptions.values.forEach { subscriber ->
						subscriber(DELETE, ref)
					}
				}
				EventType.MODIFY ->
				{
					val ref = referenceMap[qualifiedName] ?: return
					this@FileSystemModuleRootResolver.refreshResolverMetaData(
						ref,
						{
							watchEventSubscriptions.values.forEach {
								subscriber -> subscriber(MODIFY, ref)
							}
						},
						{ _, _ -> })
				}
				EventType.CREATE ->
				{
					if (referenceMap[qualifiedName] != null)
					{
						// Already exists.
						return
					}
					val type = determineResourceType(file)
					val added = LinkedList<ResolverReference>()
					var ref = resolverReference(
						file.toPath(),
						qualifiedName,
						type)
					added.addFirst(ref)
					referenceMap[qualifiedName] = ref
					do
					{
						// When moving a directory into place, the directory
						// and its children may be notified in arbitrary order,
						// so take care to create entries for the missing
						// parents as needed.
						var parent = referenceMap[ref.parentName]
						val parentExisted = parent != null
						if (!parentExisted)
						{
							val parentFile = File(ref.uri).parentFile
							val parentType = determineResourceType(parentFile)
							parent = resolverReference(
								parentFile.toPath(),
								ref.parentName,
								parentType)
							added.addFirst(parent)
							referenceMap[parent.qualifiedName] = parent
						}
						// Assert that the parent is not null (because the
						// flow analyzer isn't quite powerful enough to prove
						// this).
						parent!!
						val children = when (ref.isResource)
						{
							true -> parent.resources
							false -> parent.modules
						}
						children.add(ref)
						ref = parent
					}
					while (!parentExisted)
					added.forEach { newRef ->
						watchEventSubscriptions.values.forEach { subscriber ->
							subscriber(CREATE, newRef)
						}
					}
				}
				else -> {}
			}
		}
	}
}
