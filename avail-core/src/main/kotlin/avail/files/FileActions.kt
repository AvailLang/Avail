/*
 * FileActions.kt
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
import avail.files.RedoAction.execute
import avail.files.UndoAction.execute
import java.util.UUID

/**
 * `FileActionType` is an enum that describes the types of actions that can be
 * requested occur when interacting with an [AvailFile].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
enum class FileActionType
{
	/** Represents the canonical non-action when nothing is to be done. */
	NO_ACTION,

	/** Save the [AvailFile] to disk. */
	SAVE,

	/**
	 * Effectively, it removes data from the insertion start position until the
	 * insertion end point. Then it splits the file at insertion start point,
	 * inserting the data and appending the remainder of the file after the
	 * inserted data.
	 */
	EDIT_RANGE,

	/**
	 * This is an [EDIT_RANGE] but overwrites the entire file.
	 */
	REPLACE_CONTENTS,

	/**
	 * Undo the most recently performed [EDIT_RANGE].
	 */
	UNDO,

	/** Redo the most recently [undone][UNDO] [FileAction]. */
	REDO
}

/**
 * `FileAction` declares the methods and states for a performing a
 * [FileActionType] on an [AvailFile].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
interface FileAction
{
	/**
	 * Executes the action on the provided [AvailFile] and answer the
	 * [TracedAction] required to reverse this `FileAction` update.
	 *
	 * @param file
	 *   The `AvailServerFile` to update.
	 * @param timestamp
	 *   The time when this [FileAction] request was received.
	 * @param originator
	 *   The [Session.id] of the session that originated the change.
	 * @return
	 *   The [TracedAction], when applied, will reverse this `FileAction`.
	 */
	fun execute (
		file: AvailFile,
		timestamp: Long,
		originator: UUID
	): TracedAction

	/**
	 * The [FileActionType] that represents this [FileAction].
	 */
	val type: FileActionType

	/**
	 * `true` indicates this [FileAction] is traced in a [TracedAction]; `false`
	 * otherwise.
	 */
	val isTraced: Boolean get() = false
}

/**
 * `EditRange` is a [FileAction] that effectively first removes data from the
 * stated range (with an exclusive upper bound), then splits the file and
 * inserts the new data at the position where the first element was removed.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property data
 *   The [ByteArray] that is to be inserted in the file.
 * @property start
 *   The location in the file to inserting/overwriting the data.
 * @property end
 *   The location in the file to stop overwriting, exclusive. All data from
 *   this point should be preserved.
 *
 * @constructor
 * Construct an [EditRange].
 *
 * @param data
 *   The [ByteArray] that is to be inserted in the file.
 * @param start
 *   The location in the file to insert/overwrite the data.
 * @param end
 *   The location in the file to stop overwriting, exclusive. All data from
 *   this point should be preserved.
 */
class EditRange constructor(
	val data: ByteArray,
	private val start: Int,
	private val end: Int): FileAction
{
	override fun execute(
		file: AvailFile,
		timestamp: Long,
		originator: UUID): TracedAction =
		file.editRange(data, start, end, timestamp, originator)

	override val type: FileActionType = FileActionType.EDIT_RANGE

	override val isTraced: Boolean = true
}

/**
 * `ReplaceContents` is a [FileAction] that replaces the entire contents of a
 * file.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property data
 *   The [ByteArray] that is to be inserted in the file.
 *
 * @constructor
 * Construct an [EditRange].
 *
 * @param data
 *   The [ByteArray] that is to be inserted in the file.
 */
class ReplaceContents constructor(val data: ByteArray): FileAction
{
	override fun execute(
		file: AvailFile,
		timestamp: Long,
		originator: UUID): TracedAction =
		file.replaceFile(data, timestamp, originator)

	override val type: FileActionType = FileActionType.REPLACE_CONTENTS

	override val isTraced: Boolean = true
}

/**
 * `NoAction` is a [FileAction] indicates no action should/could be taken.
 *
 * Some `FileAction`s have an inverse action. `NoAction` is used as the inverse
 * action to `FileAction`s that have no meaningful inverse.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object NoAction: FileAction
{
	/** The canonical non-session id. */
	private val NON_SESSION_ID =
		UUID.nameUUIDFromBytes("NON_SESSION".toByteArray())

	val tracedAction = TracedAction(0,  NON_SESSION_ID, NoAction, NoAction)

	override fun execute(
		file: AvailFile,
		timestamp: Long,
		originator: UUID): TracedAction =
		tracedAction

	override val type: FileActionType = FileActionType.NO_ACTION
}


/**
 * `UndoAction` is a [FileAction] that [executes][execute] the
 * [inverse][TracedAction.reverseAction] for a `FileAction` that is traced as a
 * [TracedAction].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object UndoAction: FileAction
{
	override fun execute(
		file: AvailFile,
		timestamp: Long,
		originator: UUID): TracedAction
	{
		file.fileWrapper.undo(originator)
		return NoAction.tracedAction
	}

	override val type: FileActionType = FileActionType.UNDO
}

/**
 * `RedoAction` is a [FileAction] that [re-executes][execute] a recently
 * [undone][UndoAction] [traced][TracedAction] `FileAction`.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object RedoAction: FileAction
{
	override fun execute(
		file: AvailFile,
		timestamp: Long,
		originator: UUID): TracedAction
	{
		file.fileWrapper.redo(originator)
		return NoAction.tracedAction
	}

	override val type: FileActionType = FileActionType.REDO
}

/**
 * `SaveAction` is a [FileAction] that forces a save of an [AvailFile] to
 * disk outside of the normal save mechanism.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property failureHandler
 *   A function that accepts a [ErrorCode] that describes the nature
 *   of the failure and an optional [Throwable]. TODO refine error handling
 *
 * @constructor
 * Construct a [SaveAction].
 *
 * @param failureHandler
 *   A function that accepts a [ErrorCode] that describes the nature
 *   of the failure and an optional [Throwable].
 */
class SaveAction constructor(
	private val fileManager: FileManager,
	private val failureHandler: (ErrorCode, Throwable?) -> Unit): FileAction
{
	override fun execute(
		file: AvailFile,
		timestamp: Long,
		originator: UUID): TracedAction
	{
		fileManager.saveFile(file, failureHandler)
		return NoAction.tracedAction
	}

	override val type: FileActionType = FileActionType.SAVE
}

/**
 * A `TracedAction` records a [FileAction] that was performed on a file and
 * the `FileAction`s required to undo the initial `FileAction`.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property timestamp
 *   The time when this [FileAction] request was received.
 * @property originator
 *   The [Session.id] of the session that originator of the change.
 * @property forwardAction
 *   The originally requested [FileAction] that was made to a file.
 * @property reverseAction
 *   The [FileAction] that reverses the [forwardAction].
 *
 * @constructor
 * Construct a [TracedAction].
 *
 * @param timestamp
 *   The time when this [FileAction] request was performed.
 * @param originator
 *   The [Session.id] of the session that originator of the change.
 * @param forwardAction
 *   The originally requested [FileAction] that was made to a file.
 * @param reverseAction
 *   The [FileAction] that reverses the `forwardAction`.
 */
class TracedAction constructor(
	private val timestamp: Long,
	private val originator: UUID,
	private val forwardAction: FileAction,
	private val reverseAction: FileAction)
{
	/**
	 * Answer whether or not this [TracedAction] is traceable on the traced
	 * action stack.
	 *
	 * @return `true` indicates it is; `false` otherwise.
	 */
	fun isTraced () : Boolean = forwardAction.isTraced && reverseAction.isTraced

	/**
	 * Run the [reverseAction] on the provided [AvailFile].
	 *
	 * @param file
	 *   The [AvailFile] to reverse.
	 */
	fun undo (file: AvailFile)
	{
		reverseAction.execute(file, System.currentTimeMillis(), originator)
	}

	/**
	 * Run the [forwardAction] on the provided [AvailFile].
	 *
	 * @param file
	 *   The [AvailFile] to redo.
	 */
	fun redo (file: AvailFile)
	{
		forwardAction.execute(file, System.currentTimeMillis(), originator)
	}
}
