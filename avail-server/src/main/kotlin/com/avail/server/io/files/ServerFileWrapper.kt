/*
 * ServerFileWrapper.kt
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

import com.avail.server.error.ServerErrorCode
import org.apache.tika.Tika
import java.io.IOException
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * A `ServerFileWrapper` holds an [AvailServerFile]. The purpose of this is to
 * enable the Avail server [FileManager] cache to delay establishing the type
 * of `AvailServerFile` until the file type can be read without delaying adding
 * a file object to the `FileManager` cache.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property id
 *   The [FileManager.fileCache] key.
 * @property path
 *   The String path to the target file on disk.
 *
 * @constructor
 * Construct a [ServerFileWrapper].
 *
 * @param id
 *   The [FileManager.fileCache] key.
 * @param path
 *   The String path to the target file on disk.
 * @param fileChannel
 *   The [AsynchronousFileChannel] used to access the file.
 */
internal class ServerFileWrapper constructor(
	val id: UUID, val path: String, fileChannel: AsynchronousFileChannel)
{
	/**
	 * The [AvailServerFile] wrapped by this [ServerFileWrapper].
	 */
	lateinit var file: AvailServerFile
		private set

	/**
	 * `true` indicates the file is being loaded; `false` indicates the file
	 * has been loaded.
	 */
	@Volatile
	var isLoadingFile: Boolean = true
		private set

	/**
	 * The [queue][ConcurrentLinkedQueue] of [FileRequestHandler]s that are
	 * requesting receipt of the [file]'s
	 * [raw bytes][AvailServerFile.rawContent].
	 */
	private val handlerQueue = ConcurrentLinkedQueue<FileRequestHandler>()

	/**
	 * The [Stack] of [TracedAction] that tracks the [FileAction]s applied in
	 * Stack order. To revert, pop the `TracedAction`s and apply
	 * [TracedAction.reverseAction] to the [AvailServerFile]. This represents
	 * the _undo_ stack.
	 */
	private val tracedActionStack = Stack<TracedAction>()

	/**
	 * The next position in the [tracedActionStack] to begin saving to disk the
	 * next time the [file]'s local history is saved.
	 */
	private var tracedActionStackSavePointer: Int = 0

	/**
	 * Save the [tracedActionStack] to local history starting from the position
	 * at [tracedActionStackSavePointer].
	 */
	fun conditionallySaveToLocalHistory ()
	{
		val currentSize = tracedActionStack.size
		if (tracedActionStackSavePointer < currentSize)
		{
			val start = tracedActionStackSavePointer
			for (i in start until  currentSize)
			{
				// TODO write this somewhere!
				tracedActionStack[i]
			}
			tracedActionStackSavePointer = currentSize
		}
	}

	/**
	 * The [queue][ConcurrentLinkedQueue] of [FileAction]s that are waiting to
	 * be executed for the contained [AvailServerFile].
	 */
	private val actionQueue = ConcurrentLinkedQueue<FileAction>()

	/**
	 * Is the [file] being available for [FileAction]a from the [actionQueue]?
	 * `true` indicates it is; `false` otherwise.
	 */
	private val isReady = AtomicBoolean(true)

	/**
	 * Remove all the [FileAction]s from the [actionQueue] and perform them
	 * in FIFO order if this [ServerFileWrapper] [is ready][isReady]
	 * to update.
	 */
	private fun executeActions ()
	{
		if (isReady.getAndSet(false))
		{
			var action = actionQueue.poll()

			// Check if an action is available and that the file hasn't been
			// marked as being not ready before proceeding
			while (action != null && isReady.get())
			{
				val tracedAction =
					action.execute(file, System.currentTimeMillis())

				if (undoStackDepth > 0)
				{
					// Must remove all TracedActions above the undoStackDepth
					// pointer as they are no longer valid.
					do
					{
						val ta = tracedActionStack.pop()
						// TODO remove ta from the project cache?
					}
					while (--undoStackDepth > 0)
				}
				tracedActionStack.push(tracedAction)
				action = actionQueue.poll()
			}
			// TODO is this safe
			synchronized(actionQueue)
			{
				isReady.set(true)
			}
			// TODO Save file
		}
	}

	/**
	 * [Update][FileAction.execute] the wrapped [AvailServerFile] with the
	 * provided [FileAction].
	 *
	 * @param fileAction
	 *   The `FileAction` to perform.
	 */
	fun execute (fileAction: FileAction)
	{
		synchronized(actionQueue)
		{
			actionQueue.add(fileAction)
		}
		executeActions()
	}

	/**
	 * The number of places from the top of the [tracedActionStack] that have
	 * been undone.
	 */
	var undoStackDepth = 0

	/**
	 * [Undo][TracedAction.undo] the [FileAction] performed on the [file] from
	 * the [TracedAction] that is [undoStackDepth] + 1 from the top of the
	 * stack.
	 */
	fun undo ()
	{
		if (tracedActionStack.size >= undoStackDepth + 1)
		{
			// We are not at the bottom of the stack; there are TracedActions
			// eligible to be reverted.
			tracedActionStack[tracedActionStack.size + ++undoStackDepth].undo(file)
		}
	}

	/**
	 * If an [undo] resulted in a [revert][TracedAction.reverseAction] on the
	 * [file] and no other [FileAction] has occurred since,
	 * [redo][TracedAction.redo] the previously reverted action.
	 */
	// TODO make thread safe?
	fun redo ()
	{
		if (undoStackDepth == 0)
		{
			// There have been no TracedActions undone.
			return
		}
		val index = tracedActionStack.size - undoStackDepth
		// TODO validate in range
		tracedActionStack[index].redo(file)
		undoStackDepth--
	}

	/**
	 * Delete the [file] from disk.
	 *
	 * @param id
	 *   The [FileManager] cache id for this file.
	 * @param failure
	 *   A function that accepts a [ServerErrorCode] that describes the nature
	 *   of the failure and an optional [Throwable]. TODO refine error handling
	 */
	fun delete(
		id: UUID,
		success: () -> Unit,
		failure: (ServerErrorCode, Throwable?) -> Unit)
	{
		isReady.set(false)
		try
		{
			if (file.isOpen)
			{
				try
				{
					file.close()
				}
				catch (e: IOException)
				{
					// Do nothing
				}
			}
			if (!Files.deleteIfExists(Paths.get(path)))
			{
				failure(ServerErrorCode.FILE_NOT_FOUND, null)
			}
			else
			{
				FileManager.remove(id)
				success()
			}
		}
		catch (e: IOException)
		{
			failure(ServerErrorCode.IO_EXCEPTION, e)
		}
	}

	init
	{
		FileManager.executeFileTask (Runnable {
			val p = Paths.get(path)
			val mimeType = Tika().detect(p)
			file = createFile(path, fileChannel, mimeType, this)
		})
	}

	/**
	 * The number of clients that actively have this file open.
	 */
	val interestCount = AtomicInteger(0)

	/** Close this [ServerFileWrapper]. */
	fun close () { file.close() }

	/**
	 * Provide the [raw bytes][AvailServerFile.rawContent] of the enclosed
	 * [AvailServerFile] to the requesting consumer.
	 *
	 * @param consumer
	 *   A function that accepts the [FileManager.fileCache] [UUID] that
	 *   uniquely identifies the file, the String mime type, and the
	 *   [raw bytes][AvailServerFile.rawContent] of an [AvailServerFile].
	 * @param failureHandler
	 *   A function that accepts a [ServerErrorCode] that describes the nature
	 *   of the failure and an optional [Throwable]. TODO refine error handling
	 */
	fun provide(
		consumer: (UUID, String, ByteArray) -> Unit,
		failureHandler: (ServerErrorCode, Throwable?) -> Unit)
	{
		// TODO should be run on a separate thread or is that handled inside the
		//  consumer?
		synchronized(this)
		{
			interestCount.incrementAndGet()
			if (!isLoadingFile)
			{
				consumer(id, file.mimeType, file.rawContent)
				return
			}
			else
			{
				handlerQueue.add(FileRequestHandler(
					id, consumer, failureHandler))
			}
		}
	}

	/**
	 * Notify this [ServerFileWrapper] that the [AvailServerFile] has been fully
	 * read.
	 */
	fun notifyReady ()
	{
		synchronized(this)
		{
			isLoadingFile = false
		}
		// TODO should each be run on a separate thread or is that handled
		//  inside the consumer?
		val fileBytes = file.rawContent
		handlerQueue.forEach {
			it.requestConsumer(it.id, file.mimeType, fileBytes)
		}
	}

	/**
	 * Notify the [FileRequestHandler]s in [ServerFileWrapper.handlerQueue] that
	 *
	 */
	fun notifyFailure (errorCode: ServerErrorCode, e: Throwable?)
	{
		// TODO fix this up
		handlerQueue.forEach {
			it.failureHandler(errorCode, e)

		}
	}

	companion object
	{
		/**
		 * Answer a new [AvailServerFile] that will be associated with the
		 * [ServerFileWrapper].
		 *
		 * @param path
		 *   The String path to the target file on disk.
		 * @param file
		 *   The [AsynchronousFileChannel] used to access the file.
		 * @param mimeType
		 *   The MIME type of the file.
		 * @param serverFileWrapper
		 *   The owning `ServerFileWrapper`.
		 */
		fun createFile (
			path: String,
			file: AsynchronousFileChannel,
			mimeType: String,
			serverFileWrapper: ServerFileWrapper): AvailServerFile
		{
			return when (mimeType)
			{
				"text/avail", "text/plain", "text/json" ->
					AvailServerTextFile(path, file, mimeType, serverFileWrapper)
				else ->
					AvailServerBinaryFile(path, file, mimeType, serverFileWrapper)
			}
		}
	}
}

/**
 * A `FileRequestHandler` handles success and failure cases for requesting a
 * file's raw binary data.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property id
 *   The [FileManager.fileCache] [UUID] that uniquely identifies the file.
 * @property requestConsumer
 *   A function that accepts the [FileManager.fileCache] [UUID] that
 *   uniquely identifies the file and the
 *   [raw bytes][AvailServerFile.rawContent] of an [AvailServerFile].
 * @property failureHandler
 *   A function that accepts a [ServerErrorCode] that describes the nature of
 *   the failure and an optional [Throwable]. TODO refine error handling.
 *
 * @constructor
 * Construct a [FileRequestHandler].
 *
 * @param id
 *   The [FileManager.fileCache] [UUID] that uniquely identifies the file.
 * @param requestConsumer
 *   A function that accepts the [FileManager.fileCache] [UUID] that
 *   uniquely identifies the file, the String mime type, and the
 *   [raw bytes][AvailServerFile.rawContent] of an [AvailServerFile].
 * @param failureHandler
 *   A function that accepts a [ServerErrorCode] that describes the nature of
 *   the failure and an optional [Throwable]. TODO refine error handling.
 */
internal class FileRequestHandler constructor(
	val id: UUID,
	val requestConsumer: (UUID, String, ByteArray) -> Unit,
	val failureHandler: (ServerErrorCode, Throwable?) -> Unit)