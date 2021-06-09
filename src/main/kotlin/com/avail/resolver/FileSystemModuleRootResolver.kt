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

package com.avail.resolver

import com.avail.AvailRuntime
import com.avail.builder.ModuleName
import com.avail.builder.ModuleNameResolver
import com.avail.builder.ModuleRoot
import com.avail.builder.ModuleRootErrorCode
import com.avail.builder.ModuleRoots
import com.avail.builder.ResolvedModuleName
import com.avail.builder.UnresolvedModuleException
import com.avail.error.ErrorCode
import com.avail.error.StandardErrorCode
import com.avail.files.AvailFile
import com.avail.files.FileErrorCode
import com.avail.files.FileManager
import com.avail.io.SimpleCompletionHandler
import com.avail.persistence.IndexedFileException
import com.avail.persistence.cache.Repository
import com.avail.resolver.ModuleRootResolver.WatchEventType
import com.avail.resolver.ModuleRootResolver.WatchEventType.*
import org.apache.tika.Tika
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID
import com.avail.resolver.ResourceType.*
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.CompletionHandler
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.Collections
import java.util.Deque
import java.util.EnumSet
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicBoolean

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
 * @throws IndexedFileException
 *   If the indexed repository could not be opened.
 */
class FileSystemModuleRootResolver constructor(
		val name: String,
		override val uri: URI,
		override val fileManager: FileManager)
	: ModuleRootResolver
{
	override var accessException: Throwable? = null

	override val watchEventSubscriptions:
		MutableMap<UUID, (WatchEventType, ResolverReference) -> Unit> =
			Collections.synchronizedMap(mutableMapOf())

	/**
	 * The map from the [ModuleName.qualifiedName] to the respective
	 * [ResolverReference].
	 */
	private val referenceMap = mutableMapOf<String, ResolverReference>()

	/**
	 * The full [ModuleRoot] tree if available; or `null` if not yet set.
	 */
	private var moduleRootTree: ResolverReference? = null

	override val moduleRoot: ModuleRoot = ModuleRoot(name, this)

	override fun provideModuleRootTree(
		successHandler: (ResolverReference)->Unit,
		failureHandler: (ErrorCode, Throwable?)->Unit)
	{
		executeTask {
			moduleRootTree?.let { successHandler(it) } ?:
				resolve(successHandler, failureHandler)
		}
	}

	override fun executeTask(task: ()->Unit) =
		fileManager.executeFileTask(task)

	override fun close()
	{
		fileSystemWatcher.watchService.close()
	}

	override fun resolvesToValidModuleRoot(): Boolean =
		File(uri).isDirectory

	override fun resolve(
		successHandler: (ResolverReference)->Unit,
		failureHandler: (ErrorCode, Throwable?)->Unit)
	{
		executeTask {
			val directory = Paths.get(uri).toFile()
			if (!directory.isDirectory)
			{
				failureHandler(
					ModuleRootErrorCode.MODULE_ROOT_RESOLUTION_FAILED,
					null)
				return@executeTask
			}
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
			if (tree === null)
			{
				failureHandler(
					ModuleRootErrorCode.MODULE_ROOT_RESOLUTION_FAILED,
					null)
				return@executeTask
			}
			successHandler(tree)
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
	 * Create the [Tika] mime detector once, lazily.
	 */
	private val tika: Tika by lazy { Tika() }

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
	private fun resolverReference(
		path: Path,
		qualifiedName: String,
		type: ResourceType? = null): ResolverReference
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
		val mimeType = if (isPackage) "" else tika.detect(path)
		val qname = qualifiedName.replace(ModuleNameResolver.availExtension, "")
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
		return if (fileName.endsWith(ModuleNameResolver.availExtension))
		{

			if (file.isDirectory) PACKAGE
			else
			{
				val components = fileName.split("/")
				val parent =
					if (components.size > 1)
					{
						components[components.size - 2].split(
							ModuleNameResolver.availExtension)[0]
					}
					else ""

				val localName = fileName.substring(
					0,
					fileName.length - ModuleNameResolver.availExtension.length)
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

	override fun getResolverReference(qualifiedName: String)
		: ResolverReference? = referenceMap[qualifiedName]

	override fun provideResolverReference(
		qualifiedName: String,
		withReference: (ResolverReference)->Unit,
		failureHandler: (ErrorCode, Throwable?) -> Unit)
	{
		referenceMap[qualifiedName]?.let { withReference(it) } ?: run {
			try
			{
				withReference(
					resolverReference(
						absolutePath(
							uri,
							qualifiedName
						),
						qualifiedName
					)
				)
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
							"(${reference.qualifiedName}) File changed during " +
								"digest calculation: modified timestamp at " +
								"file read start $initialModified, at finish " +
								modified)
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
						save(file, data, data.position().toLong(), failureHandler)
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
		byPassFileManager: Boolean,
		reference: ResolverReference,
		withContents: (ByteArray, UUID?)->Unit,
		failureHandler: (ErrorCode, Throwable?)->Unit)
	{
		require(!setOf(ROOT, DIRECTORY, PACKAGE).contains(reference.type)) {
			"${reference.qualifiedName} is not a file that can be read!"
		}
		if (!byPassFileManager)
		{
			val isBeingRead =
				fileManager.optionallyProvideExistingFile(
					reference,
					{ uuid, availFile ->
						reference.refresh(
							availFile.lastModified,
							availFile.rawContent.size.toLong())
						withContents(availFile.rawContent, uuid)
					},
					failureHandler)
			if (isBeingRead) { return }
		}
		val file: AsynchronousFileChannel
		try
		{
			file = fileManager.ioSystem.openFile(
				Paths.get(reference.uri), EnumSet.of(StandardOpenOption.READ))
		}
		catch (e: IOException)
		{
			val ex =
				IOException("Failed to read source: ${reference.uri}", e)
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
							file.read(buffer, filePosition, dummy, handler)
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
			}).guardedDo { file.read(buffer, 0L, dummy, handler) }
	}

	override fun watchRoot()
	{
		fileSystemWatcher.add()
	}

	override fun find (
		qualifiedName: ModuleName,
		initialCanonicalName: ModuleName,
		moduleNameResolver: ModuleNameResolver)
			: ModuleNameResolver.ModuleNameResolutionResult?
	{
		var canonicalName = initialCanonicalName
		val components =
			canonicalName.packageName.split("/")
		assert(components.size > 1)
		assert(components[0].isEmpty())

		val nameStack = LinkedList<String>()
		nameStack.addLast("/${moduleRoot.name}")
		val pathStack: Deque<File> = LinkedList()

		// If the source directory is available, then build a search stack of
		// trials at ascending tiers of enclosing packages.
		val sourceDirectory = Paths.get(uri).toFile()
		pathStack.addLast(sourceDirectory)
		for (index in 2 until components.size)
		{
			assert(components[index].isNotEmpty())
			nameStack.addLast(String.format(
				"%s/%s",
				nameStack.peekLast(),
				components[index]))
			pathStack.addLast(File(
				pathStack.peekLast(),
				components[index] + ModuleNameResolver.availExtension))
		}

		// If the source directory is available, then search the file system.
		val checkedPaths = mutableListOf<ModuleName>()
		var repository: Repository? = null
		var sourceFile: File? = null
		assert(!pathStack.isEmpty())
		// Explore the search stack from most enclosing package to least
		// enclosing.
		while (!pathStack.isEmpty())
		{
			canonicalName = ModuleName(
				nameStack.removeLast(),
				canonicalName.localName,
				canonicalName.isRename)
			checkedPaths.add(canonicalName)
			val trial = File(
				ModuleNameResolver.filenameFor(
					pathStack.removeLast().path,
					canonicalName.localName))
			if (trial.exists())
			{
				repository = moduleRoot.repository
				sourceFile = trial
				break
			}
		}

		// We found a candidate.
		if (repository !== null)
		{
			// If the candidate is a package, then substitute
			// the package representative.
			if (sourceFile!!.isDirectory)
			{
				sourceFile = File(
					sourceFile,
					canonicalName.localName + ModuleNameResolver.availExtension)
				canonicalName = ModuleName(
					canonicalName.qualifiedName,
					canonicalName.localName,
					canonicalName.isRename)
				if (!sourceFile.isFile)
				{
					// Alas, the package representative did not exist.
					return ModuleNameResolver.ModuleNameResolutionResult(
						UnresolvedModuleException(
							null,
							qualifiedName.localName,
							this))
				}
			}
			val ref =
				referenceMap[canonicalName.qualifiedName] ?:
					return ModuleNameResolver.ModuleNameResolutionResult(
						UnresolvedModuleException(
							null,
							qualifiedName.localName,
							this))
			return ModuleNameResolver.ModuleNameResolutionResult(
				ResolvedModuleName(
					canonicalName,
					moduleNameResolver.moduleRoots,
					ref,
					canonicalName.isRename))
		}

		// Resolution failed.
		return null
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
		val extension = ModuleNameResolver.availExtension
		var isRoot = true
		val stack = ArrayDeque<ResolverReference>()
		return object : FileVisitor<Path>
		{
			override fun preVisitDirectory(
				dir: Path,
				attrs: BasicFileAttributes): FileVisitResult
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
				if (fileName.endsWith(extension))
				{
					val localName = fileName.substring(
						0, fileName.length - extension.length)
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
				e: IOException?): FileVisitResult
			{
				stack.removeFirst()
				return FileVisitResult.CONTINUE
			}

			override fun visitFile(
				file: Path,
				attrs: BasicFileAttributes): FileVisitResult
			{
				// The root should be a directory, not a file.
				if (isRoot)
				{
					throw IOException("alleged root is not a directory")
				}

				val fileName = file.fileName.toString()
				if (fileName.toUpperCase() == ".DS_STORE")
				{
					// Mac file to be ignored
					return FileVisitResult.CONTINUE
				}

				// A file with an Avail extension is an Avail module.
				val parent = stack.peekFirst()!!
				if (fileName.endsWith(extension))
				{
					val localName = fileName.substring(
						0, fileName.length - extension.length)
					val type =
						if (parent.isPackage && parent.localName == localName)
							ResourceType.REPRESENTATIVE
						else
							ResourceType.MODULE

					val qualifiedName = "${parent.qualifiedName}/$localName"
					val reference =
						resolverReference(file, qualifiedName, type)
					referenceMap[qualifiedName] = reference
					parent.modules.add(reference)
				}
				// Otherwise, it is a resource.
				else
				{
					val qualifiedName = "${parent.qualifiedName}/$fileName"
					val reference =
						resolverReference(
							file,
							qualifiedName,
							ResourceType.RESOURCE)
					referenceMap[qualifiedName] = reference
					parent.resources.add(reference)
				}
				return FileVisitResult.CONTINUE
			}

			override fun visitFileFailed(
				file: Path,
				e: IOException): FileVisitResult
			{
				val parent = stack.peekFirst()!!
				val isDirectory = file.toFile().isDirectory
				val fileName = file.fileName.toString()
				if (fileName.endsWith(extension))
				{
					val localName = fileName.substring(
						0, fileName.length - extension.length)
					val qualifiedName = "${parent.qualifiedName}/$localName"
					val mime: String
					val type =
						if (isDirectory)
						{
							mime = ""
							ResourceType.PACKAGE
						}
						else
						{
							mime = AvailFile.availMimeType
							ResourceType.MODULE
						}
					var fileURI = file.toUri()
					if (fileURI.scheme === null)
					{
						fileURI = URI("file://$file")
					}
					val reference = ResolverReference(
						this@FileSystemModuleRootResolver,
						fileURI,
						qualifiedName,
						type,
						mime,
						0,
						0)
					reference.accessException = e
					referenceMap[qualifiedName] = reference
					parent.modules.add(reference)
				}
				else
				{
					val qualifiedName = "${parent.qualifiedName}/$fileName"
					var fileURI = file.toUri()
					if (fileURI.scheme === null)
					{
						fileURI = URI("file://$file")
					}
					val reference = ResolverReference(
						this@FileSystemModuleRootResolver,
						fileURI,
						qualifiedName,
						if (isDirectory)
							ResourceType.DIRECTORY
						else ResourceType.RESOURCE,
						"",
						0,
						0)
					reference.accessException = e
					referenceMap[qualifiedName] = reference
					parent.modules.add(reference)
				}
				return FileVisitResult.CONTINUE
			}
		}
	}

	companion object
	{
		/**
		 * Answer a [Path] for a given [ModuleRootResolver.uri] and
		 * [ResolverReference.qualifiedName], transforming the package names to
		 * include the [ModuleNameResolver.availExtension].
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
			qualifiedName: String): Path =
				Paths.get(buildString {
					append(rootURI)
					for (part in qualifiedName.split("/"))
					{
						append('/')
						append(part)
						append(ModuleNameResolver.availExtension)
					}
				})
	}

	/**
	 * `FileSystemWatcher` manages the [WatchService] responsible for watching
	 * the local file system directories of the [ModuleRoot]s loaded into the
	 * AvailRuntime.
	 */
	inner class FileSystemWatcher constructor()
	{
		private val initialized = AtomicBoolean(false)

		/**
		 * The [WatchService] watching the [FileManager] directories where
		 * the [AvailRuntime] loaded [ModuleRoot]s are stored.
		 */
		val watchService: WatchService =
			FileSystems.getDefault().newWatchService()

		/**
		 * A [Map] from a [WatchKey] to the [RootWatcher] for the [ModuleRoot]
		 * hierarchy where the `WatchKey` is used.
		 */
		val watchMap = mutableMapOf<WatchKey, RootWatcher>()

		/**
		 * Add the [FileSystemModuleRootResolver.moduleRoot] to be watched by
		 * this [FileSystemWatcher] and initialize it.
		 */
		fun add ()
		{
			if (!initialized.getAndSet(true))
			{
				RootWatcher(this)
				initialize()
			}
		}

		/**
		 * Shutdown this [FileSystemWatcher].
		 */
		fun close ()
		{
			watchService.close()
		}

		/**
		 * Initialize this [FileSystemWatcher].
		 */
		fun initialize()
		{
			// Rename steps:
			// 1 - Parent directory Modify
			// 2 - "New" directory Create (new folder name)
			// 3 - Old name directory Delete

			this@FileSystemModuleRootResolver.executeTask {
				try
				{
					var key: WatchKey
					while (watchService.take().also { key = it } != null)
					{
						watchMap[key]?.let { rw ->
							rw.watchMap[key]?.let { path ->
								for (event: WatchEvent<*> in key.pollEvents())
								{
									resolveEvent(key, path, event)
								}
							}
						}
						key.reset()
					}
				}
				catch (e: ClosedWatchServiceException)
				{
					// The watch service is closing and the thread is currently
					// blocked in the take or poll methods waiting for a key to
					// be queued. This ensures an immediate stop to this
					// service. Nothing else to do here.
				}
			}
		}

		private fun resolveEvent (
			key: WatchKey,
			path: Path,
			event: WatchEvent<*>)
		{
			// Mac stuff to ignore
			if (event.context().toString() == ".DS_Store")
			{
				key.reset()
				return
			}
			val file = File("$path/${event.context()}")
			val isDirectory = file.isDirectory
			if (isDirectory
				&& (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY
					|| event.kind() == StandardWatchEventKinds.ENTRY_CREATE))
			{
				key.reset()
				return
			}
			when
			{
				event.kind() == StandardWatchEventKinds.ENTRY_DELETE ->
				{
					val uriPath = "$path/${event.context()}"
					val qualifiedName = getQualifiedName(uriPath)
					val ref = referenceMap.remove(qualifiedName) ?: return
					// TODO remove from reference tree
					watchEventSubscriptions.values.forEach {
						it(DELETE, ref)
					}
					key.reset()
				}
				event.kind() == StandardWatchEventKinds.ENTRY_MODIFY ->
				{
					val uriPath = "$path/${event.context()}"
					val qualifiedName = getQualifiedName(uriPath)
					val ref = referenceMap[qualifiedName] ?: return
					this@FileSystemModuleRootResolver.refreshResolverMetaData(
						ref,
						{ _ ->
							watchEventSubscriptions.values.forEach {
								it(MODIFY, ref)
							}
						}) { _, _ ->
							// Nothing to be done here...
						}
					key.reset()
				}
				event.kind() == StandardWatchEventKinds.ENTRY_CREATE ->
				{
					val uriPath = "$path/${event.context()}"
					val qualifiedName = getQualifiedName(uriPath)
					val type = determineResourceType(file)
					val ref =
						resolverReference(
							path,
							qualifiedName,
							type)
					referenceMap[ref.qualifiedName] = ref
					// TODO insert into reference tree
					watchEventSubscriptions.values.forEach {
						it(CREATE, ref)
					}
					key.reset()
				}
			}
		}
	}

	/**
	 * `RootWatcher` is responsible for managing the watching of a [ModuleRoot]
	 * by a [FileSystemWatcher].
	 *
	 * @property fileSystemWatcher
	 *   The [FileSystemWatcher] watching the `root` directory.
	 *
	 * @constructor
	 * Construct a [RootWatcher].
	 *
	 * @param fileSystemWatcher
	 *   The [FileSystemWatcher] watching the `root` directory.
	 */
	inner class RootWatcher @Throws(IOException::class) constructor(
		val fileSystemWatcher: FileSystemWatcher)
	{
		/**
		 * [Map] from a [WatchKey] to the [Path] of the directory that
		 * `WatchKey` is watching.
		 */
		val watchMap = mutableMapOf<WatchKey, Path>()

		init
		{
			Files.walkFileTree(Paths.get(uri),
				object : SimpleFileVisitor<Path>()
				{
					@Throws(IOException::class)
					override fun preVisitDirectory(
						dir: Path, attrs: BasicFileAttributes): FileVisitResult
					{
						val watcher = dir.register(
							fileSystemWatcher.watchService,
							StandardWatchEventKinds.ENTRY_CREATE,
							StandardWatchEventKinds.ENTRY_DELETE,
							StandardWatchEventKinds.ENTRY_MODIFY)
						watchMap[watcher] = dir
						fileSystemWatcher.watchMap[watcher] = this@RootWatcher
						return FileVisitResult.CONTINUE
					}
				})
		}
	}
}
