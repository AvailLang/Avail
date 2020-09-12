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
import com.avail.server.error.ServerErrorCode
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.CompletionHandler
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.*

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
	override fun serverFileWrapper(
		id: UUID,
		path: String): ServerFileWrapper =
			ServerFileWrapper(
				id,
				path,
				this,
				runtime.ioSystem().openFile(
					Paths.get(path), fileOpenOptions))

	override fun readFile (
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
			?: failureHandler(ServerErrorCode.FILE_NOT_FOUND, null)
		}
		return uuid
	}

	override fun createFile (
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
			failureHandler(ServerErrorCode.FILE_ALREADY_EXISTS, e)
		}
		catch (e: IOException)
		{
			failureHandler(ServerErrorCode.IO_EXCEPTION, e)
		}
		return null
	}

	override fun saveFile (
		availServerFile: AvailServerFile,
		failureHandler: (ServerErrorCode, Throwable?) -> Unit)
	{
		val content = availServerFile.rawContent
		val data = ByteBuffer.wrap(content)
		val saveTimeStart = System.currentTimeMillis()
		val file = runtime.ioSystem().openFile(
			Paths.get(availServerFile.path), fileOpenOptions)
		file.write(
			data,
			0,
			null,
//			SimpleCompletionHandler<ServerErrorCode>(
//				{
//					if (data.hasRemaining())
//					{
//						save(file, data, data.position().toLong(), failure)
//					}
//					else
//					{
//						availServerFile.conditionallyClearDirty(saveTimeStart)
//					}
//				})
//			{ e, code ->
//				failure(code ?: ServerErrorCode.UNSPECIFIED, e)
//			})
			object : CompletionHandler<Int, ServerErrorCode?> {
				override fun completed(result: Int?, attachment: ServerErrorCode?)
				{
					if (data.hasRemaining())
					{
						save(file, data, data.position().toLong(), failureHandler)
					}
					else
					{
						availServerFile.conditionallyClearDirty(saveTimeStart)
					}
				}

				override fun failed(exc: Throwable?, attachment: ServerErrorCode?)
				{
					failureHandler(attachment ?: ServerErrorCode.UNSPECIFIED, exc)
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
	private fun save (
		file: AsynchronousFileChannel,
		data: ByteBuffer,
		writePosition: Long,
		failure: (ServerErrorCode, Throwable?) -> Unit)
	{
		file.write(
			data,
			writePosition,
			null,
//			SimpleCompletionHandler<Int, ServerErrorCode?>(
//				{ result, _, _ ->
//					if (data.hasRemaining())
//					{
//						save(file, data, data.position().toLong(), failure)
//					}
//				})
//			{ e, code, _ ->
//				failure(code ?: ServerErrorCode.UNSPECIFIED, e)
//			})
			object : CompletionHandler<Int, ServerErrorCode?> {
				override fun completed(result: Int?, attachment: ServerErrorCode?)
				{
					if (data.hasRemaining())
					{
						save(file, data, data.position().toLong(), failure)
					}
				}

				override fun failed(exc: Throwable?, attachment: ServerErrorCode?)
				{
					failure(attachment ?: ServerErrorCode.UNSPECIFIED, exc)
				}
			})
	}

	override fun delete (
		path: String,
		success: () -> Unit,
		failure: (ServerErrorCode, Throwable?) -> Unit)
	{
		pathToIdMap.remove(path)?.let {id ->
			// TODO send notifications file deleted?
			//  might need to track more session-file interest info
			fileCache.remove(id)
			idToPathMap.remove(id)
		}
		if (!Files.deleteIfExists(Paths.get(path)))
		{
			failure(ServerErrorCode.FILE_NOT_FOUND, null)
		}
		else
		{
			success()
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
}