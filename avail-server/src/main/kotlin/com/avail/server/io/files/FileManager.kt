/*
 * FileManager.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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
import com.avail.AvailThread
import com.avail.server.AvailServer
import com.avail.server.error.ServerErrorCode
import com.avail.server.error.ServerErrorCode.*
import com.avail.utility.LRUCache
import com.avail.utility.MutableOrNull
import java.io.IOException
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.ThreadPoolExecutor

/**
 * `FileManager` manages the opened files of the Avail Server. It provides an
 * LRU caching mechanism by which files can be added and removed as needed to
 * control the number of open files in memory.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property runtime
 *   The [AvailRuntime] that is associated with the running [AvailServer].
 *
 * @constructor
 * Construct a [FileManager].
 *
 * @param runtime
 *   The [AvailRuntime] that is associated with the running [AvailServer].
 */
internal class FileManager constructor(private val runtime: AvailRuntime)
{
	/**
	 * The [EnumSet] of [StandardOpenOption]s used when opening files.
	 */
	private val fileOpenOptions =
		EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE)

	/**
	 * The [EnumSet] of [StandardOpenOption]s used when creating files.
	 */
	private val fileCreateOptions =
		EnumSet.of(
			StandardOpenOption.READ,
			StandardOpenOption.WRITE,
			StandardOpenOption.CREATE_NEW)

	/**
	 * The [thread pool executor][ThreadPoolExecutor] for asynchronous file
	 * operations performed on behalf of this [FileManager].
	 */
	private val fileExecutor = runtime.ioSystem().fileExecutor

	/**
	 * Maintain an [LRUCache] of [AvailServerFile]s opened by the Avail server.
	 *
	 * This cache works in conjunction with [pathToIdMap] to maintain links
	 * between
	 */
	// TODO make the softCapacity and strongCapacity configurable, not magic numbers
	private val fileCache = LRUCache<UUID, MutableOrNull<ServerFileWrapper>>(
		10000,
		10,
		{
			var path = ""
			pathToIdMap.forEach { (k, v) ->
				if (v == it)
				{
					path = k
					return@forEach
				}
			}
			MutableOrNull(
				if (path.isEmpty())
				{
					null
				}
				else
				{
					ServerFileWrapper(
						it,
						path,
						this,
						runtime.ioSystem().openFile(
							Paths.get(path), fileOpenOptions))
				})

		},
		{ _, value ->
			try
			{
				value.value?.close()
			}
			catch (e: IOException)
			{
				// Do nothing
			}
		})

	/**
	 * Fully remove the file associated with the provided [fileCache] id. This
	 * also removes it from [pathToIdMap].
	 *
	 * @param id
	 *   The [UUID] that uniquely identifies the target file in the cache.
	 */
	fun remove (id: UUID)
	{
		fileCache[id].value?.let {
			fileCache.remove(id)
			pathToIdMap.remove(it.path)
			idToPathMap.remove(id)
		} ?: idToPathMap.remove(id)?.let { pathToIdMap.remove(it) }
	}

	/**
	 * Deregister interest in the file associated with the provided [fileCache]
	 * id. If the resulting [interest count][ServerFileWrapper.interestCount]
	 * is 0, the file is closed and fully removed from the [fileCache].
	 *
	 * @param id
	 *   The [UUID] that uniquely identifies the target file in the cache.
	 */
	fun deregisterInterest (id: UUID)
	{
		fileCache[id].value?.let {
			if (it.interestCount.decrementAndGet() == 0)
			{
				remove(id)
				it.close()
			}
		}
	}

	/**
	 * Delete the file at the provided path.
	 *
	 * @param path
	 *   The String path to the file to be deleted.
	 * @param failure
	 *   A function that accepts a [ServerErrorCode] that describes the nature
	 *   of the failure and an optional [Throwable]. TODO refine error handling
	 */
	fun delete (
		path: String,
		success: () -> Unit,
		failure: (ServerErrorCode, Throwable?) -> Unit)
	{
		pathToIdMap[path]?.let {id ->
			fileCache[id].value?.delete(id, success, failure)
			idToPathMap.remove(id)
		} ?: {
			if (!Files.deleteIfExists(Paths.get(path)))
			{
				failure(FILE_NOT_FOUND, null)
			}
			else
			{
				success()
			}
		}.invoke()
	}

	/**
	 * A [Map] from the String [Path] location of a [file][AvailServerFile] to
	 * the [UUID] that uniquely identifies that file in the [fileCache].
	 *
	 * This map will never be cleared of values as cached files that have been
	 * removed from the `fileCache` must maintain association with the
	 * server-assigned [UUID] that identifies the file for all interested
	 * clients. If a client requests a file action with a given UUID and it is
	 * not found in the `fileCache`, this map will be used to retrieve the
	 * associated file from disk and placed back in the `fileCache`.
	 */
	private val pathToIdMap = mutableMapOf<String, UUID>()

	/**
	 * A [Map] from the file cache [id][UUID] that uniquely identifies that file
	 * in the [fileCache] to the String [Path] location of a
	 * [file][AvailServerFile].
	 *
	 * This map will never be cleared of values as cached files that have been
	 * removed from the `fileCache` must maintain association with the
	 * server-assigned [UUID] that identifies the file for all interested
	 * clients. If a client requests a file action with a given UUID and it is
	 * not found in the `fileCache`, this map will be used to retrieve the
	 * associated file from disk and placed back in the `fileCache`.
	 */
	private val idToPathMap = mutableMapOf<UUID, String>()

	/**
	 * Retrieve the [ServerFileWrapper] and provide it with a request to obtain
	 * the [raw file bytes][AvailServerFile.rawContent].
	 *
	 * @param path
	 *   The String path location of the file.
	 * @param consumer
	 *   A function that accepts the [FileManager.fileCache] [UUID] that
	 *   uniquely identifies the file, the String mime type, and the
	 *   [raw bytes][AvailServerFile.rawContent] of an [AvailServerFile].
	 * @param failureHandler
	 *   A function that accepts a [ServerErrorCode] that describes the nature
	 *   of the failure and an optional [Throwable].
	 * @return
	 *   The [FileManager] file id for the file.
	 */
	fun readFile (
		path: String,
		consumer: (UUID, String, ByteArray) -> Unit,
		failureHandler: (ServerErrorCode, Throwable?) -> Unit): UUID
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
			?: failureHandler(FILE_NOT_FOUND, null)
		}
		return uuid
	}

	/**
	 * Schedule the specified task for eventual execution
	 * by the [thread pool executor][ThreadPoolExecutor] for
	 * asynchronous file operations. The implementation is free to run the task
	 * immediately or delay its execution arbitrarily. The task will not execute
	 * on an [Avail thread][AvailThread].
	 *
	 * @param task
	 *   A task.
	 */
	fun executeFileTask(task: () -> Unit)
	{
		fileExecutor.execute(task)
	}

	/**
	 * Retrieve the [ServerFileWrapper] and provide it with a request to obtain
	 * the [raw file bytes][AvailServerFile.rawContent].
	 *
	 * @param id
	 *   The [ServerFileWrapper] cache id of the file to act upon.
	 * @param fileAction
	 *   The [FileAction] to execute.
	 * @param failureHandler
	 *   A function that accepts a [ServerErrorCode] that describes the nature of
	 *   the failure and an optional [Throwable]. TODO refine error handling.
	 */
	fun executeAction (
		id: UUID,
		fileAction: FileAction,
		failureHandler: (ServerErrorCode, Throwable?) -> Unit)
	{
		fileCache[id].value?.execute(fileAction)
			?: failureHandler(BAD_FILE_ID, null)
		// TODO do some better error reporting?
	}

	/**
	 * Create a [ServerFileWrapper] and provide it with a request to obtain
	 * the [raw file bytes][AvailServerFile.rawContent].
	 *
	 * @param path
	 *   The String path location of the file.
	 * @param consumer
	 *   A function that accepts the [FileManager.fileCache] [UUID] that
	 *   uniquely identifies the file, the String mime type, and the
	 *   [raw bytes][AvailServerFile.rawContent] of an [AvailServerFile].
	 * @param failureHandler
	 *   A function that accepts a [ServerErrorCode] that describes the failure
	 *   and an optional [Throwable]. TODO refine error reporting
	 * @return
	 *   The [FileManager] file id for the file.
	 */
	fun createFile (
		path: String,
		consumer: (UUID, String, ByteArray) -> Unit,
		failureHandler: (ServerErrorCode, Throwable?) -> Unit): UUID?
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
			failureHandler(FILE_ALREADY_EXISTS, e)
		}
		catch (e: IOException)
		{
			failureHandler(IO_EXCEPTION, e)
		}
		return null
	}
}