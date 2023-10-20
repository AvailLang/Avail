/*
 * ManagedFileWrapper.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package avail.files

import avail.error.ErrorCode
import avail.resolver.ResolverReference
import org.availlang.artifact.ResourceType
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * A `AbstractFileWrapper` is an abstraction for holding an [AvailFile]. The
 * purpose of this is to enable the Avail server  [FileManager] cache to delay
 * establishing the type of `AvailFile` until the file type can be read without
 * delaying adding a file object to the `FileManager` cache.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property id
 *   The [FileManager.fileCache] key.
 * @property reference
 *   The [ResolverReference] of the target file on disk.
 * @property fileManager
 *   The [FileManager] this [ManagedFileWrapper] belongs to.
 *
 * @constructor
 * Construct a [AbstractFileWrapper].
 *
 * @param id
 *   The [FileManager.fileCache] key.
 * @param reference
 *   The [ResolverReference] of the target file.
 * @param fileManager
 *   The [FileManager] this [ManagedFileWrapper] belongs to.
 */
abstract class AbstractFileWrapper constructor(
	val id: UUID,
	val reference: ResolverReference,
	protected val fileManager: FileManager)
{
	/**
	 * `true` indicates that there was an error; `false` otherwise.
	 */
	open val isError get() = false

	/**
	 * The associated [ErrorCode] if [error] is not `null`; `null`
	 * otherwise
	 */
	open val errorCode: ErrorCode? = null

	/**
	 * The [Throwable] if one was encountered when opening the file, `null`
	 * otherwise.
	 */
	open val error: Throwable? = null

	/** The [AvailFile] wrapped by this [ManagedFileWrapper]. */
	abstract val file: AvailFile

	/**
	 * `true` indicates that [file] is fully populated with the content of the
	 * file located at the [reference]; `false` indicates the file has
	 * not been loaded into memory.
	 */
	private val isAvailable = AtomicBoolean(false)

	/**
	 * The [queue][ConcurrentLinkedQueue] of [FileRequestHandler]s that are
	 * requesting receipt of the [file]'s
	 * [raw bytes][AvailFile.rawContent].
	 */
	private val fileRequestQueue = ConcurrentLinkedQueue<FileRequestHandler>()

	/**
	 * The [Stack] of [TracedAction] that tracks the [FileAction]s applied in
	 * Stack order. To revert, pop the `TracedAction`s and apply
	 * [TracedAction.reverseAction] to the [AvailFile]. This represents
	 * the _undo_ stack.
	 */
	private val tracedActionStack = Stack<TracedAction>()

	/**
	 * The next position in the [tracedActionStack] to begin saving to disk the
	 * next time the [file]'s local history is saved.
	 */
	private var tracedActionStackSavePointer: Int = 0

	/**
	 * Delete the wrapped file from its storage location.
	 *
	 * @param success
	 *   Accepts the [FileManager] file id if remove successful. Maybe `null`
	 *   if file not present in `FileManager`.
	 * @param failure
	 *   A function that accepts a [ErrorCode] that describes the nature
	 *   of the failure and an optional [Throwable].
	 */
	abstract fun delete (
		success: (UUID?) -> Unit,
		failure: (ErrorCode, Throwable?) -> Unit)

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
	 * [Update][FileAction.execute] the wrapped [AvailFile] with the provided
	 * [FileAction].
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
	 *   The id of the entity that originated the change.
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
					@Suppress("UNUSED_VARIABLE")
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
	 *   The [UUID] of the session that originated the undo.
	 */
	fun undo(@Suppress("UNUSED_PARAMETER") originator: UUID)
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
	 *   The [UUID] of the session that originated the undo.
	 */
	fun redo(@Suppress("UNUSED_PARAMETER") originator: UUID)
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

	/**
	 * Is the file closed? `true` indicates it is; `false` otherwise.
	 */
	private val closed = AtomicBoolean(false)

	/**
	 * Is the file closed? `true` indicates it is; `false` otherwise.
	 */
	val isClosed: Boolean get() = closed.get()

	/** Close this [ManagedFileWrapper]. */
	fun close ()
	{
		closed.set(true)
	}

	/**
	 * Provide the [raw bytes][AvailFile.rawContent] of the enclosed
	 * [AvailFile] to the requesting consumer.
	 *
	 * @param registerInterest
	 *   `true` if the intent is to let this [AbstractFileWrapper] know that
	 *   the caller is interested in the file being kept in memory; `false`
	 *   otherwise.
	 * @param successHandler
	 *   A function that accepts the [FileManager.fileCache] [UUID] that
	 *   uniquely identifies the file, the String mime type, and the
	 *   [AvailFile].
	 * @param failureHandler
	 *   A function that accepts a [ErrorCode] that describes the nature
	 *   of the failure and an optional [Throwable].
	 */
	fun provide(
		registerInterest: Boolean,
		successHandler: (UUID, String, AvailFile) -> Unit,
		failureHandler: (ErrorCode, Throwable?) -> Unit)
	{
		if (isError)
		{
			failureHandler(errorCode!!, error)
			return
		}
		if (closed.get())
		{
			// This shouldn't really happen, but it gives an opportunity for
			// the requester to potentially re-request the file.
			failureHandler(FileErrorCode.FILE_CLOSED, null)
		}
		if (registerInterest)
		{
			interestCount.incrementAndGet()
		}
		if (isAvailable.get())
		{
			successHandler(id, reference.mimeType, file)
			return
		}
		else
		{
			fileRequestQueue.add(FileRequestHandler(
				id, successHandler, failureHandler))
		}
	}

	/**
	 * Notify this [ManagedFileWrapper] that the [AvailFile] has been fully
	 * read.
	 */
	fun notifyReady ()
	{
		// We only want to notify that this file is ready once.
		if (!isAvailable.getAndSet(true))
		{
			fileRequestQueue.forEach {
				fileManager.executeFileTask {
					it.requestConsumer(it.id, reference.mimeType, file)
				}
			}
			fileManager.checkInterest(id)
		}
	}

	/**
	 * Notify the [FileRequestHandler]s in [ManagedFileWrapper.fileRequestQueue] that
	 * the file action encountered a failure while opening.
	 *
	 * @param errorCode
	 *   The [ErrorCode] describing the failure.
	 * @param e
	 *   The optional [Throwable] related to the failure if one exists; `null`
	 *   otherwise.
	 */
	fun notifyOpenFailure (errorCode: ErrorCode, e: Throwable? = null)
	{
		// TODO fix this up
		fileRequestQueue.forEach {
			it.failureHandler(errorCode, e)

		}
	}
}

/**
 * A `ManagedFileWrapper` is an [AbstractFileWrapper] used by the [FileManager]
 * to hold an [AvailFile] that is on the file system of the machine Avail is
 * running. The purpose of this is to enable the [FileManager] cache to delay
 * establishing the type of the `AvailFile` until the file type can be read
 * without delaying adding a file object to the `FileManager` cache.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 * Construct a [ManagedFileWrapper].
 *
 * @param id
 *   The [FileManager.fileCache] key.
 * @param resolverReference
 *   The [reference] of the target file.
 * @param fileManager
 *   The [FileManager] this [ManagedFileWrapper] belongs to.
 */
class ManagedFileWrapper constructor(
	id: UUID,
	resolverReference: ResolverReference,
	fileManager: FileManager
) : AbstractFileWrapper(id, resolverReference, fileManager)
{
	/** The [AvailFile] wrapped by this [ManagedFileWrapper]. */
	override val file: AvailFile by lazy {
		when
		{
			resolverReference.type == ResourceType.Module
				|| resolverReference.mimeType == AvailFile.availMimeType ->
					AvailModuleFile(this)
			AvailFile.isTextFile(resolverReference.mimeType) ->
				AvailTextFile(this)
			else -> AvailBinaryFile(this)
		}
	}

	override fun delete(
		success: (UUID?)->Unit,
		failure: (ErrorCode, Throwable?)->Unit)
	{
		if (!Files.deleteIfExists(Paths.get(reference.uri)))
		{
			failure(FileErrorCode.FILE_NOT_FOUND, null)
		}
		else
		{
			success(id)
		}
	}

	companion object
	{
		/**
		 * Answer a new [AvailFile] that will be associated with the
		 * [ManagedFileWrapper].
		 *
		 * @param mimeType
		 *   The MIME type of the file.
		 * @param fileWrapper
		 *   The owning `ServerFileWrapper`.
		 */
		fun createFile (
			mimeType: String,
			fileWrapper: ManagedFileWrapper
		): AvailFile = when
		{
			mimeType == "text/avail" -> AvailModuleFile(fileWrapper)
			AvailFile.isTextFile(mimeType) -> AvailTextFile(fileWrapper)
			else -> AvailBinaryFile(fileWrapper)
		}
	}
}

/**
 * A `NullFileWrapper` is an [AbstractFileWrapper] not used by the [FileManager]
 * to hold an [AvailFile] that is on the file system of the machine Avail is
 * running. The purpose of this is to enable access outside of the [FileManager].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 * Construct a [NullFileWrapper].
 *
 * @param resolverReference
 *   The [reference] of the target file.
 * @param fileManager
 *   The [FileManager] this [ManagedFileWrapper] belongs to.
 */
class NullFileWrapper constructor(
	raw: ByteArray,
	resolverReference: ResolverReference,
	fileManager: FileManager
) : AbstractFileWrapper(nullUUID, resolverReference, fileManager)
{
	/** The [AvailFile] wrapped by this [NullFileWrapper]. */
	override val file: AvailFile by lazy {
		when
		{
			resolverReference.type == ResourceType.Module
				|| resolverReference.type == ResourceType.Representative
				|| resolverReference.mimeType == AvailFile.availMimeType ->
				AvailModuleFile(this)
			AvailFile.isTextFile(resolverReference.mimeType) ->
				AvailTextFile(raw,this)
			else -> AvailBinaryFile(this)
		}
	}

	override fun delete(
		success: (UUID?)->Unit,
		failure: (ErrorCode, Throwable?)->Unit)
	{
		if (!Files.deleteIfExists(Paths.get(reference.uri)))
		{
			failure(FileErrorCode.FILE_NOT_FOUND, null)
		}
		else
		{
			success(id)
		}
	}

	companion object
	{
		/**
		 * UUID always used for [AbstractFileWrapper] that is not in the file
		 * cache nor will be added to the file cache.
		 */
		private val nullUUID = UUID.nameUUIDFromBytes("Null UUID".toByteArray())
	}
}

/**
 * A `ErrorServerFileWrapper` is a [AbstractFileWrapper] that encountered
 * an error when accessing the underlying file.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 * Construct a [ManagedFileWrapper].
 *
 * @param id
 *   The [FileManager.fileCache] key.
 * @param resolverReference
 *   The [reference] of the target file.
 * @param fileManager
 *   The [FileManager] this [ManagedFileWrapper] belongs to.
 */
class ErrorFileWrapper constructor(
	id: UUID,
	resolverReference: ResolverReference,
	fileManager: FileManager,
	override val error: Throwable,
	override val errorCode: ErrorCode
) : AbstractFileWrapper(id, resolverReference, fileManager)
{
	/** The [AvailFile] wrapped by this [ManagedFileWrapper]. */
	override val file: AvailFile
		get() = throw UnsupportedOperationException("File is not available")

	override val isError get() = true

	override fun delete(
		success: (UUID?)->Unit,
		failure: (ErrorCode, Throwable?)->Unit)
	{
		throw UnsupportedOperationException("Not meaningful to support delete")
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
 *   [raw bytes][AvailFile.rawContent] of an [AvailFile].
 * @property failureHandler
 *   A function that accepts a [ErrorCode] that describes the nature of
 *   the failure and an optional [Throwable]. TODO refine error handling.
 *
 * @constructor
 * Construct a [FileRequestHandler].
 *
 * @param id
 *   The [FileManager.fileCache] [UUID] that uniquely identifies the file.
 * @param requestConsumer
 *   A function that accepts the [FileManager.fileCache] [UUID] that
 *   uniquely identifies the file, the String mime type, and the [AvailFile].
 * @param failureHandler
 *   A function that accepts a [ErrorCode] that describes the nature of
 *   the failure and an optional [Throwable]. TODO refine error handling.
 */
internal class FileRequestHandler constructor(
	val id: UUID,
	val requestConsumer: (UUID, String, AvailFile) -> Unit,
	val failureHandler: (ErrorCode, Throwable?) -> Unit)
