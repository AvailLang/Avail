/*
 * EditActions.kt
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

/**
 * `EditActionType` is an enum that describes the types of edits that can occur
 * on an [AvailServerFile].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
internal enum class EditActionType
{
	/**
	 * Insertion of text at a position causing the file to grow. This preserves
	 * the file data before and after the insertion.
	 */
	INSERT,

	/**
	 * A combination of [REMOVE_RANGE] and [Insert]. It removes data equal to
	 * the size of the insert data starting at the start point. Then it inserts
	 * the data at the removed range start point.
	 */
	INSERT_RANGE,

	/**
	 * Remove the data in the specified range from the file. The file size is
	 * decreased as a result of this action.
	 */
	REMOVE_RANGE,

	/**
	 * Undo the most recently performed [INSERT], [INSERT_RANGE], or
	 * [REMOVE_RANGE].
	 */
	UNDO,

	/**
	 * Redo the most recently [undone][UNDO] [EditAction].
	 */
	REDO
}

/**
 * `EditAction` declares the methods and states for a editing an
 * [AvailServerFile] according to one of the [EditActionType]s.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
internal interface EditAction
{
	/**
	 * Updates the provided [AvailServerFile] and answer the ordered list of
	 * [EditAction]s required to reverse this `EditAction` update.
	 *
	 * @param file
	 *   The `AvailServerFile` to update.
	 * @param timestamp
	 *   The time when this [EditAction] request was received.
	 * @return The [List] of `EditActions`, when applied in order, will reverse
	 *   this `EditAction`.
	 */
	fun update (file: AvailServerFile, timestamp: Long): TracedAction

	/**
	 * The [EditActionType] that represents this [EditAction].
	 */
	val type: EditActionType
}

/**
 * `Insert` is an [EditAction]
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property data
 *   The [ByteArray] that is to be inserted in the file.
 * @property position
 *   The location in the file to insert the data.
 *
 * @constructor
 * Construct an [Insert].
 *
 * @param data
 *   The [ByteArray] that is to be inserted in the file.
 * @param position
 *   The location in the file to insert the data.
 */
internal class Insert constructor(
	val data: ByteArray,
	private val position: Int) : EditAction
{
	override fun update(file: AvailServerFile, timestamp: Long): TracedAction =
		file.insert(data, position, timestamp)

	override val type: EditActionType = EditActionType.INSERT
}

/**
 * `RemoveRange` is an [EditAction] that removes data from file for a range with
 * an exclusive upper bound.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property start
 *   The location in the file to inserting/overwriting the data.
 * @property end
 *   The location in the file to stop overwriting, exclusive. All data from
 *   this point should be preserved.
 *
 * @constructor
 * Construct a [RemoveRange].
 *
 * @param start
 *   The location in the file to inserting/overwriting the data.
 * @param end
 *   The location in the file to stop overwriting, exclusive. All data from
 *   this point should be preserved.
 */
internal class RemoveRange constructor(
	private val start: Int,
	private val end: Int) : EditAction
{
	override fun update(file: AvailServerFile, timestamp: Long): TracedAction =
		file.removeRange(start, end, timestamp)

	override val type: EditActionType = EditActionType.REMOVE_RANGE
}

/**
 * `InsertRange` is an [EditAction] that first removes data with from the range
 * with an exclusive upper bound, then inserts the new data at the position
 * where the first element was removed.
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
 * Construct an [InsertRange].
 *
 * @param data
 *   The [ByteArray] that is to be inserted in the file.
 * @param start
 *   The location in the file to inserting/overwriting the data.
 * @param end
 *   The location in the file to stop overwriting, exclusive. All data from
 *   this point should be preserved.
 */
internal class InsertRange constructor(
	val data: ByteArray,
	private val start: Int,
	private val end: Int): EditAction
{
	override fun update(file: AvailServerFile, timestamp: Long): TracedAction =
		file.insertRange(data, start, end, timestamp)

	override val type: EditActionType = EditActionType.INSERT_RANGE
}

/**
 * A `TracedAction` records an [EditAction] that was performed on a file and
 * the `EditAction`s required to undo the initial `EditAction`.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property timestamp
 *   The time when this [EditAction] request was received.
 * @property forwardAction
 *   The originally requested [EditAction] that was made to a file.
 * @property reverseAction
 *   The [EditAction] that reverses the [forwardAction].
 *
 * @constructor
 * Construct a [TracedAction].
 *
 * @param timestamp
 *   The time when this [EditAction] request was performed.
 * @property forwardAction
 *   The originally requested [EditAction] that was made to a file.
 * @property reverseAction
 *   The [EditAction] that reverses the `forwardAction`.
 */
internal class TracedAction constructor(
	private val timestamp: Long,
	private val forwardAction: EditAction,
	private val reverseAction: EditAction)
{
	/**
	 * Run the [reverseAction] on the provided [AvailServerFile].
	 *
	 * @param file
	 *   The [AvailServerFile] to reverse.
	 */
	fun revert (file: AvailServerFile)
	{
		reverseAction.update(file, timestamp)
	}

	/**
	 * Run the [forwardAction] on the provided [AvailServerFile].
	 *
	 * @param file
	 *   The [AvailServerFile] to redo.
	 */
	fun redo (file: AvailServerFile)
	{
		forwardAction.update(file, timestamp)
	}
}