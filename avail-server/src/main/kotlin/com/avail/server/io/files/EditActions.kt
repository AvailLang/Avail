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
	REMOVE_RANGE
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
	 * @return The [List] of `EditActions`, when applied in order, will reverse
	 *   this `EditAction`.
	 */
	fun update (file: AvailServerFile): List<EditAction>

	/**
	 * The [EditActionType] that represents this [EditAction].
	 */
	val type: EditActionType

	/**
	 * The time when this [EditAction] request was received.
	 */
	val timestamp: Long
}

/**
 * `Insert` is an [EditAction]
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property timestamp
 *   The time when this [EditAction] request was received.
 * @property data
 *   The [ByteArray] that is to be inserted in the file.
 * @property position
 *   The location in the file to insert the data.
 *
 * @constructor
 * Construct an [Insert].
 *
 * @param timestamp
 *   The time when this [EditAction] request was received.
 * @param data
 *   The [ByteArray] that is to be inserted in the file.
 * @param position
 *   The location in the file to insert the data.
 */
internal class Insert constructor(
	override val timestamp: Long,
	val data: ByteArray,
	private val position: Int) : EditAction
{
	override fun update(file: AvailServerFile): List<EditAction> =
		file.insert(data, position)

	override val type: EditActionType = EditActionType.INSERT
}

/**
 * `RemoveRange` is an [EditAction] that removes data from file for a range with
 * an exclusive upper bound.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property timestamp
 *   The time when this [EditAction] request was received.
 * @property start
 *   The location in the file to inserting/overwriting the data.
 * @property end
 *   The location in the file to stop overwriting, exclusive. All data from
 *   this point should be preserved.
 *
 * @constructor
 * Construct a [RemoveRange].
 *
 * @param timestamp
 *   The time when this [EditAction] request was received.
 * @param start
 *   The location in the file to inserting/overwriting the data.
 * @param end
 *   The location in the file to stop overwriting, exclusive. All data from
 *   this point should be preserved.
 */
internal class RemoveRange constructor(
	override val timestamp: Long,
	private val start: Int,
	private val end: Int) : EditAction
{
	override fun update(file: AvailServerFile): List<EditAction> =
		file.removeRange(start, end)

	override val type: EditActionType = EditActionType.REMOVE_RANGE
}

/**
 * `InsertRange` is an [EditAction] that first removes data with from the range
 * with an exclusive upper bound, then inserts the new data at the position
 * where the first element was removed.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property timestamp
 *   The time when this [EditAction] request was received.
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
 * @param timestamp
 *   The time when this [EditAction] request was received.
 * @param data
 *   The [ByteArray] that is to be inserted in the file.
 * @param start
 *   The location in the file to inserting/overwriting the data.
 * @param end
 *   The location in the file to stop overwriting, exclusive. All data from
 *   this point should be preserved.
 */
internal class InsertRange constructor(
	override val timestamp: Long,
	val data: ByteArray,
	private val start: Int,
	private val end: Int): EditAction
{
	override fun update(file: AvailServerFile): List<EditAction> =
		file.insertRange(data, start, end)

	override val type: EditActionType = EditActionType.INSERT_RANGE
}


