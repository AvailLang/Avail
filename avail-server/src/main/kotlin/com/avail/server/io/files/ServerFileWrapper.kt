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
import com.avail.server.session.Session
import org.apache.tika.Tika
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * A `AbstractServerFileWrapper` is an abstraction for holding an
 * [AvailServerFile]. The purpose of this is to enable the Avail server
 * [FileManager] cache to delay establishing the type of `AvailServerFile`
 * until the file type can be read without delaying adding a file object to the
 * `FileManager` cache.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property id
 *   The [FileManager.fileCache] key.
 * @property path
 *   The String path to the target file on disk.
 * @property fileManager
 *   The [FileManager] this [ServerFileWrapper] belongs to.
 *
 * @constructor
 * Construct a [AbstractServerFileWrapper].
 *
 * @param id
 *   The [FileManager.fileCache] key.
 * @param path
 *   The String path to the target file on disk.
 * @param fileManager
 *   The [FileManager] this [ServerFileWrapper] belongs to.
 */
abstract class AbstractServerFileWrapper constructor(
	val id: UUID,
	val path: String,
	protected val fileManager: FileManager)
{
	/**
	 * `true` indicates that there was an error; `false` otherwise.
	 */
	open val isError = false

	/**
	 * The associated [ServerErrorCode] if [error] is not `null`; `null`
	 * otherwise
	 */
	open val errorCode: ServerErrorCode? = null

	/**
	 * The [Throwable] if one was encountered when opening the file, `null`
	 * otherwise.
	 */
	open val error: Throwable? = null

	/** The [AvailServerFile] wrapped by this [ServerFileWrapper]. */
	abstract var file: AvailServerFile
		protected set

	/**
	 * `true` indicates that [file] is fully populated with the content of the
	 * file located at the [path]; `false` indicates the file has not been
	 * loaded into memory.
	 */
	private val isAvailable = AtomicBoolean(false)

	/**
	 * The [queue][ConcurrentLinkedQueue] of [FileRequestHandler]s that are
	 * requesting receipt of the [file]'s
	 * [raw bytes][AvailServerFile.rawContent].
	 */
	private val fileRequestQueue = ConcurrentLinkedQueue<FileRequestHandler>()

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
			for (i in start until currentSize)
			{
				// TODO write this somewhere!
				tracedActionStack[i]
			}
			tracedActionStackSavePointer = currentSize
		}
	}

	/**
	 * [Update][FileAction.execute] the wrapped [AvailServerFile] with the
	 * provided [FileAction].
	 *
	 * This method is [Synchronized] to enforce performing actions
	 * synchronously. All single-client edits are performed synchronously in
	 * client sent order via the client connection I/O message handling.
	 *
	 * Concurrent edits by multiple clients cannot be synchronized other than
	 * preventing concurrent edits. It is impossible for the server to reason
	 * out the appropriate order of edits from different concurrently editing
	 * clients.
	 *
	 * @param fileAction
	 *   The `FileAction` to perform.
	 * @param originator
	 *   The [Session.id] of the session that originated the change.
	 * @param continuation
	 *   What to do when sufficient processing has occurred.
	 */
	@Synchronized
	fun execute (
		fileAction: FileAction,
		originator: UUID,
		continuation: ()->Unit)
	{
		val tracedAction =
			fileAction.execute(file, System.currentTimeMillis(), originator)

		if (tracedAction.isTraced())
		{
			val stackSize = tracedActionStack.size
			val undoPointer = stackSize - undoStackDepth.get()
			if (undoPointer < stackSize)
			{
				// Must remove all TracedActions above the undoStackDepth
				// pointer as they are no longer valid.
				do
				{
					val ta = tracedActionStack.pop()
					// TODO remove ta from the project cache?
				}
				while (undoStackDepth.decrementAndGet() > 0)
			}
			tracedActionStack.push(tracedAction)
		}

		continuation()
	}

	/**
	 * The number of places from the top of the [tracedActionStack] that have
	 * been undone.
	 */
	private val undoStackDepth = AtomicInteger(0)

	/**
	 * [Undo][TracedAction.undo] the [FileAction] performed on the [file] from
	 * the [TracedAction] that is [undoStackDepth] + 1 from the top of the
	 * stack.
	 *
	 * This should only ever be called from [UndoAction.execute] to ensure it
	 * is performed through the synchronized [execution][execute] path.
	 *
	 * @param originator
	 *   The [Session.id] of the session that originated the undo.
	 */
	fun undo(originator: UUID)
	{
		if (tracedActionStack.size >= undoStackDepth.get() + 1)
		{
			// We are not at the bottom of the stack; there are TracedActions
			// eligible to be reverted.
			val tracedActionIndex =
				tracedActionStack.size - undoStackDepth.incrementAndGet()
			tracedActionStack[tracedActionIndex].undo(file)
		}
	}

	/**
	 * If an [undo] resulted in a [revert][TracedAction.reverseAction] on the
	 * [file] and no other [FileAction] has occurred since,
	 * [redo][TracedAction.redo] the previously reverted action.
	 *
	 * This should only ever be called from [RedoAction.execute] to ensure it
	 * is performed through the synchronized [execution][execute] path.
	 *
	 * @param originator
	 *   The [Session.id] of the session that originated the undo.
	 */
	fun redo(originator: UUID)
	{
		if (undoStackDepth.get() == 0)
		{
			// There have been no TracedActions undone.
			return
		}
		val index = tracedActionStack.size - undoStackDepth.get()
		// TODO validate in range
		tracedActionStack[index].redo(file)
		undoStackDepth.decrementAndGet()
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
		if (isError)
		{
			failureHandler(errorCode!!, error)
			return
		}
		interestCount.incrementAndGet()
		if (isAvailable.get())
		{
			consumer(id, file.mimeType, file.rawContent)
			return
		}
		else
		{
			fileRequestQueue.add(FileRequestHandler(
				id, consumer, failureHandler))
		}
	}

	/**
	 * Notify this [ServerFileWrapper] that the [AvailServerFile] has been fully
	 * read.
	 */
	fun notifyReady ()
	{
		// We only want to notify that this file is ready once.
		if (!isAvailable.getAndSet(true))
		{
			val fileBytes = file.rawContent
			fileRequestQueue.forEach {
				fileManager.executeFileTask {
					it.requestConsumer(it.id, file.mimeType, fileBytes)
				}
			}
		}
	}

	/**
	 * Notify the [FileRequestHandler]s in [ServerFileWrapper.fileRequestQueue] that
	 * the file action encountered a failure while opening.
	 *
	 * @param errorCode
	 *   The [ServerErrorCode] describing the failure.
	 * @param e
	 *   The optional [Throwable] related to the failure if one exists; `null`
	 *   otherwise.
	 */
	fun notifyOpenFailure (errorCode: ServerErrorCode, e: Throwable? = null)
	{
		// TODO fix this up
		fileRequestQueue.forEach {
			it.failureHandler(errorCode, e)

		}
	}
}

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
 * @property fileManager
 *   The [FileManager] this [ServerFileWrapper] belongs to.
 *
 * @constructor
 * Construct a [ServerFileWrapper].
 *
 * @param id
 *   The [FileManager.fileCache] key.
 * @param path
 *   The String path to the target file on disk.
 * @param fileManager
 *   The [FileManager] this [ServerFileWrapper] belongs to.
 * @param fileChannel
 *   The [AsynchronousFileChannel] used to access the file.
 */
class ServerFileWrapper constructor(
	id: UUID,
	path: String,
	fileManager: FileManager,
	fileChannel: AsynchronousFileChannel)
		: AbstractServerFileWrapper(id, path, fileManager)
{
	/** The [AvailServerFile] wrapped by this [ServerFileWrapper]. */
	override lateinit var file: AvailServerFile

	init
	{
		// TODO handle this externally
		fileManager.executeFileTask {
			val p = Paths.get(path)
			val mimeType = Tika().detect(p)
			file = createFile(path, fileChannel, mimeType, this)
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
			serverFileWrapper: ServerFileWrapper): AvailServerFile =
			when (mimeType)
			{
				"text/avail", "text/plain", "text/json" ->
					AvailServerTextFile(
						path, file, mimeType, serverFileWrapper)
				else ->
					AvailServerBinaryFile(
						path, file, mimeType, serverFileWrapper)
			}
	}
}

/**
 * A `ErrorServerFileWrapper` is a [AbstractServerFileWrapper] that encountered
 * an error when accessing the underlying file.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property id
 *   The [FileManager.fileCache] key.
 * @property path
 *   The String path to the target file on disk.
 * @property fileManager
 *   The [FileManager] this [ServerFileWrapper] belongs to.
 *
 * @constructor
 * Construct a [ServerFileWrapper].
 *
 * @param id
 *   The [FileManager.fileCache] key.
 * @param path
 *   The String path to the target file on disk.
 * @param fileManager
 *   The [FileManager] this [ServerFileWrapper] belongs to.
 * @param e
 *   The [Throwable] that was encountered.
 */
class ErrorServerFileWrapper constructor(
	id: UUID,
	path: String,
	fileManager: FileManager,
	e: Throwable,
	errorCode: ServerErrorCode)
	: AbstractServerFileWrapper(id, path, fileManager)
{
	/** The [AvailServerFile] wrapped by this [ServerFileWrapper]. */
	override lateinit var file: AvailServerFile

	override val isError = true
	override val error: Throwable? = e
	override val errorCode: ServerErrorCode? = errorCode
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