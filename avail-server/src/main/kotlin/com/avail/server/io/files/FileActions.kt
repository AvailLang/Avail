/*
 * FileActions.kt
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
import com.avail.server.io.files.RedoAction.execute
import com.avail.server.io.files.UndoAction.execute

/**
 * `FileActionType` is an enum that describes the types of actions that can be
 * requested occur when interacting with an [AvailServerFile].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
enum class FileActionType
{
	/** Represents the canonical non-action when nothing is to be done. */
	NO_ACTION,

	/** Save the [AvailServerFile] to disk. */
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
 * [FileActionType] on an [AvailServerFile].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
interface FileAction
{
	/**
	 * Executes the action on the provided [AvailServerFile] and answer the
	 * [TracedAction] required to reverse this `FileAction` update.
	 *
	 * @param file
	 *   The `AvailServerFile` to update.
	 * @param timestamp
	 *   The time when this [FileAction] request was received.
	 * @return
	 *   The [TracedAction], when applied, will reverse this `FileAction`.
	 */
	fun execute (file: AvailServerFile, timestamp: Long): TracedAction

	/**
	 * The [FileActionType] that represents this [FileAction].
	 */
	val type: FileActionType

	/**
	 * `true` indicates this [FileAction] is traced in a [TracedAction]; `false`
	 * otherwise.
	 */
	val isTraced: Boolean  get() = false
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
internal class EditRange constructor(
	val data: ByteArray,
	private val start: Int,
	private val end: Int): FileAction
{
	override fun execute(file: AvailServerFile, timestamp: Long): TracedAction =
		file.editRange(data, start, end, timestamp)

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
internal class ReplaceContents constructor(
	val data: ByteArray,
	private val start: Int,
	private val end: Int): FileAction
{
	override fun execute(file: AvailServerFile, timestamp: Long): TracedAction =
		file.editRange(data, start, end, timestamp)

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
internal object NoAction: FileAction
{
	val tracedAction = TracedAction(0, NoAction, NoAction)

	override fun execute(file: AvailServerFile, timestamp: Long): TracedAction =
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
internal object UndoAction: FileAction
{
	override fun execute(file: AvailServerFile, timestamp: Long): TracedAction
	{
		file.serverFileWrapper.undo()
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
internal object RedoAction: FileAction
{
	override fun execute(file: AvailServerFile, timestamp: Long): TracedAction
	{
		file.serverFileWrapper.redo()
		return NoAction.tracedAction
	}

	override val type: FileActionType = FileActionType.REDO
}

/**
 * `SaveAction` is a [FileAction] that forces a save of an [AvailServerFile] to
 * disk outside of the normal save mechanism.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property failureHandler
 *   A function that accepts a [ServerErrorCode] that describes the nature
 *   of the failure and an optional [Throwable]. TODO refine error handling
 *
 * @constructor
 * Construct a [SaveAction].
 *
 * @param failureHandler
 *   A function that accepts a [ServerErrorCode] that describes the nature
 *   of the failure and an optional [Throwable].
 */
internal class SaveAction constructor(
	private val fileManager: FileManager,
	private val failureHandler: (ServerErrorCode, Throwable?) -> Unit): FileAction
{
	override fun execute(file: AvailServerFile, timestamp: Long): TracedAction
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
 * @property forwardAction
 *   The originally requested [FileAction] that was made to a file.
 * @property reverseAction
 *   The [FileAction] that reverses the `forwardAction`.
 */
class TracedAction constructor(
	val timestamp: Long,
	private val forwardAction: FileAction,
	private val reverseAction: FileAction)
{
	/**
	 * Run the [reverseAction] on the provided [AvailServerFile].
	 *
	 * @param file
	 *   The [AvailServerFile] to reverse.
	 */
	fun undo (file: AvailServerFile)
	{
		reverseAction.execute(file, System.currentTimeMillis())
	}

	/**
	 * Run the [forwardAction] on the provided [AvailServerFile].
	 *
	 * @param file
	 *   The [AvailServerFile] to redo.
	 */
	fun redo (file: AvailServerFile)
	{
		forwardAction.execute(file, System.currentTimeMillis())
	}
}