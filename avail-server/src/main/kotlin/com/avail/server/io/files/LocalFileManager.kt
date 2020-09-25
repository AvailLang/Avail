/*
 * LocalFileManager.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package com.avail.server.io.files

import com.avail.AvailRuntime
import com.avail.builder.ModuleRoot
import com.avail.builder.ModuleRoots
import com.avail.server.error.ServerErrorCode
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.CompletionHandler
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardOpenOption
import java.nio.file.StandardWatchEventKinds.*
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.nio.file.attribute.BasicFileAttributes
import java.util.EnumSet
import java.util.UUID

/**
 * `LocalFileManager` is a [FileManager] used to manage files when a local copy
 * of the AvailServer is running and is meant to interact with the host's local
 * file system.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
internal class LocalFileManager constructor(runtime: AvailRuntime)
	: FileManager(runtime)
{
	/**
	 * The [FileSystemWatcher] used to monitor the [ModuleRoots] for system
	 * changes.
	 */
	private val fileSystemWatcher = FileSystemWatcher()

	init
	{
		fileSystemWatcher.initialize(runtime.moduleRoots())
	}

	override fun close()
	{
		fileSystemWatcher.watchService.close()
	}

	override fun getResourceResolver(moduleRoot: ModuleRoot): ResourceResolver
	{
		TODO("Not yet implemented")
	}

	override fun watchRoot(root: ModuleRoot)
	{
		fileSystemWatcher.add(root)
	}

	override fun serverFileWrapper(
		id: UUID,
		path: String): ServerFileWrapper =
			ServerFileWrapper(
				id,
				path,
				this,
				runtime.ioSystem().openFile(
					Paths.get(path), fileOpenOptions))

	override fun readFile(
		path: String,
		consumer: (UUID, String, ByteArray)->Unit,
		failureHandler: (ServerErrorCode, Throwable?)->Unit): UUID
	{
		val uuid: UUID
		synchronized(pathToIdMap)
		{
			uuid = pathToIdMap.getOrPut(path) { UUID.randomUUID() }
			idToPathMap[uuid] = path
		}
		val value = fileCache[uuid]
		executeFileTask {
			value.value?.provide(consumer, failureHandler)
			?: failureHandler(ServerErrorCode.FILE_NOT_FOUND, null)
		}
		return uuid
	}

	override fun createFile(
		path: String,
		consumer: (UUID, String, ByteArray)->Unit,
		failureHandler: (ServerErrorCode, Throwable?)->Unit): UUID?
	{
		// TODO should the mime type be required?
		// TODO check to see if this is reasonable?
		try
		{
			val file = AsynchronousFileChannel.open(
				Paths.get(path), fileCreateOptions, fileExecutor)
			file.force(false)
			file.close()
			return readFile(path, consumer, failureHandler)
		}
		catch (e: FileAlreadyExistsException)
		{
			failureHandler(ServerErrorCode.FILE_ALREADY_EXISTS, e)
		}
		catch (e: IOException)
		{
			failureHandler(ServerErrorCode.IO_EXCEPTION, e)
		}
		return null
	}

	override fun saveFile(
		availServerFile: AvailServerFile,
		failureHandler: (ServerErrorCode, Throwable?)->Unit)
	{
		val content = availServerFile.getSaveContent()
		val data = ByteBuffer.wrap(content)
		val saveTimeStart = System.currentTimeMillis()
		val file = runtime.ioSystem().openFile(
			Paths.get(availServerFile.path),
			EnumSet.of(
				StandardOpenOption.TRUNCATE_EXISTING,
				StandardOpenOption.READ,
				StandardOpenOption.WRITE))
		file.write(
			data,
			0,
			null,
			object : CompletionHandler<Int, ServerErrorCode?>
			{
				override fun completed(
					result: Int?,
					attachment: ServerErrorCode?)
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
						availServerFile.conditionallyClearDirty(saveTimeStart)
					}
				}

				override fun failed(
					exc: Throwable?,
					attachment: ServerErrorCode?)
				{
					failureHandler(
						attachment ?: ServerErrorCode.UNSPECIFIED,
						exc)
				}
			})
	}

	/**
	 * Save the data to disk starting at the specified write location.
	 *
	 * @param data
	 *   The [ByteBuffer] to save to disk for this [AvailServerFile].
	 * @param writePosition
	 *   The position in the file to start writing to.
	 * @param failure
	 *   A function that accepts a [ServerErrorCode] that describes the nature
	 *   of the failure and an optional [Throwable].
	 */
	private fun save(
		file: AsynchronousFileChannel,
		data: ByteBuffer,
		writePosition: Long,
		failure: (ServerErrorCode, Throwable?)->Unit)
	{
		file.write(
			data,
			writePosition,
			null,
			object : CompletionHandler<Int, ServerErrorCode?>
			{
				override fun completed(
					result: Int?,
					attachment: ServerErrorCode?)
				{
					if (data.hasRemaining())
					{
						save(file, data, data.position().toLong(), failure)
					}
				}

				override fun failed(
					exc: Throwable?,
					attachment: ServerErrorCode?)
				{
					failure(attachment ?: ServerErrorCode.UNSPECIFIED, exc)
				}
			})
	}

	override fun delete(
		path: String,
		success: (UUID?)->Unit,
		failure: (ServerErrorCode, Throwable?)->Unit)
	{
		val removedId = pathToIdMap.remove(path)?.let { id ->
			fileCache.remove(id)
			idToPathMap.remove(id)
			id
		}
		if (!Files.deleteIfExists(Paths.get(path)))
		{
			failure(ServerErrorCode.FILE_NOT_FOUND, null)
		}
		else
		{
			success(removedId)
		}
	}

	companion object
	{
		/**
		 * The [EnumSet] of [StandardOpenOption]s used when opening files.
		 */
		private val fileOpenOptions =
			EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE)
	}

	/**
	 * `RootWatcher` is responsible for managing the watching of a [ModuleRoot]
	 * by a [FileSystemWatcher].
	 *
	 * @property root
	 *   The [ModuleRoot] being watched.
	 * @property fileSystemWatcher
	 *   The [FileSystemWatcher] watching the `root` directory.
	 *
	 * @constructor
	 * Construct a [RootWatcher].
	 *
	 * @param root
	 *   The [ModuleRoot] being watched.
	 * @param fileSystemWatcher
	 *   The [FileSystemWatcher] watching the `root` directory.
	 */
	inner class RootWatcher @Throws(IOException::class) constructor(
		val root: ModuleRoot,
		val fileSystemWatcher: FileSystemWatcher)
	{
		/**
		 * [Map] from a [WatchKey] to the [Path] of the directory that
		 * `WatchKey` is watching.
		 */
		val watchMap = mutableMapOf<WatchKey, Path>()

		init
		{
			Files.walkFileTree(Paths.get(root.sourceUri!!.absolutePath),
				object : SimpleFileVisitor<Path>()
				{
					@Throws(IOException::class)
					override fun preVisitDirectory(
						dir: Path, attrs: BasicFileAttributes): FileVisitResult
					{
						val watcher = dir.register(
							fileSystemWatcher.watchService,
							ENTRY_CREATE,
							ENTRY_DELETE,
							ENTRY_MODIFY)
						watchMap[watcher] = dir
						fileSystemWatcher.watchMap[watcher] = this@RootWatcher
						return FileVisitResult.CONTINUE
					}
				})
		}
	}

	/**
	 * `FileSystemWatcher` manages the [WatchService] responsible for watching
	 * the local file system directories of the [ModuleRoot]s loaded into the
	 * AvailRuntime.
	 */
	inner class FileSystemWatcher
	{
		/**
		 * The [WatchService] watching the [LocalFileManager] directories where
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
		 * Add a new [ModuleRoot] to be watched by this [FileSystemWatcher].
		 */
		fun add (moduleRoot: ModuleRoot)
		{
			RootWatcher(moduleRoot, this)
		}

		/**
		 * Shutdown this [FileSystemWatcher].
		 */
		fun close ()
		{
			watchService.close()
		}

		/**
		 * Initialize this [FileSystemWatcher] to watch the [ModuleRoot]s in the
		 * provided [ModuleRoots].
		 *
		 * @param moduleRoots
		 *   The [ModuleRoots] to watch.
		 */
		fun initialize(moduleRoots: ModuleRoots)
		{
			moduleRoots.roots.forEach {
				RootWatcher(it, this)
			}

			// Rename steps:
			// 1 - Parent directory Modify
			// 2 - "New" directory Create (new folder name)
			// 3 - Old name directory Delete

			this@LocalFileManager.executeFileTask {
				try
				{
					var key: WatchKey
					while (watchService.take().also { key = it } != null)
					{
						watchMap[key]?.let { rw ->
							rw.watchMap[key]?.let { path ->
								for (event: WatchEvent<*> in key.pollEvents())
								{
									// Mac stuff to ignore
									if (event
											.context()
											.toString() == ".DS_Store")
									{
										key.reset()
										continue
									}
									val file =
										File("$path/${event.context()}")
									val isDirectory = file.isDirectory
									if (isDirectory && (
										event.kind() == ENTRY_MODIFY
											|| event.kind() == ENTRY_CREATE))
									{
										key.reset()
										continue
									}
									when
									{
										event.kind() == ENTRY_DELETE ->
										{
											// TODO send delete notification
											println("$file deleted!")
											key.reset()
										}
										event.kind() == ENTRY_MODIFY ->
										{
											// TODO send file modify
											//  notification to sessions with
											//  file open
											println("$file modified!")
											key.reset()
										}
										event.kind() == ENTRY_CREATE ->
										{
											// TODO send file modify
											//  notification to sessions with
											//  file open
											println("$file created!")
											key.reset()
										}
									}

									println(
										"Event kind:" + event.kind()
											+ ". File affected: " + path + "/" + event.context() + ".")
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
	}
}