/*
 * AvailFile.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice, this
 *   list of conditions and the following disclaimer in the documentation
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
package com.avail.files

import com.avail.builder.ModuleRoot
import com.avail.io.AvailClient
import java.nio.charset.Charset
import java.util.UUID

/**
 * `AvailFile` is a specification for declaring behavior and state for a
 * file that has been opened from the hierarchy of an Avail [ModuleRoot].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property fileWrapper
 *   The [AbstractFileWrapper] that wraps this [AvailFile].
 *
 * @constructor
 * Construct an [AvailFile].
 *
 * @param charset
 *   The [Charset] of the file.
 * @param fileWrapper
 *   The [AbstractFileWrapper] that wraps this [AvailFile].
 */
abstract class AvailFile constructor(
	val fileWrapper: AbstractFileWrapper)
{
	/** The raw bytes of the file. */
	abstract val rawContent: ByteArray

	/** The mime type of this file. */
	val mimeType: String = fileWrapper.reference.mimeType

	/**
	 * The last time in milliseconds since the Unix epoch that the file was
	 * updated.
	 */
	var lastModified: Long = fileWrapper.reference.lastModified

	/**
	 * @return
	 *   The most recent file content eligible to be saved.
	 */
	open fun getSaveableContent (): ByteArray = rawContent

	/**
	 * Time in milliseconds since the unix epoch UTC when this file was lasted
	 * edited.
	 */
	var lastEdit = 0L

	/**
	 * `true` indicates the file has been edited but it has not been
	 * [saved][SaveAction]. `false` indicates the file is unchanged from disk.
	 */
	private var isDirty = false

	/**
	 * Indicate this file has been edited to be different than what is saved
	 * to disk.
	 */
	@Synchronized
	fun markDirty ()
	{
		lastEdit = System.currentTimeMillis()
		isDirty = true
	}

	/**
	 * Indicate this file has been edited to be different than what is saved
	 * to disk.
	 */
	@Synchronized
	fun conditionallyClearDirty(saveTimeStart: Long)
	{
		if (lastEdit < saveTimeStart)
		{
			isDirty = false
		}
	}

	/**
	 * `true` indicates the file is open; `false` indicates it is closed.
	 */
	val isOpen: Boolean get() = !fileWrapper.isClosed

	/**
	 * Insert the [ByteArray] data into the file at the specified location. This
	 * should remove existing data in the file in this range and replace it
	 * with the provided data. This should preserve all data outside of this
	 * range.
	 *
	 * @param data
	 *   The `ByteArray` data to add to this [AvailFile].
	 * @param start
	 *   The location in the file to inserting/overwriting the data, exclusive.
	 * @param end
	 *   The location in the file to stop overwriting. All data after this point
	 *   should be preserved.
	 * @param timestamp
	 *   The time in milliseconds since the Unix Epoch UTC the update occurred.
	 * @param originator
	 *   The [AvailClient.id] of the session that originated the change.
	 * @return
	 *   The [TracedAction] that preserves this edit and how to reverse it.
	 */
	abstract fun editRange (
		data: ByteArray,
		start: Int,
		end: Int,
		timestamp: Long = System.currentTimeMillis(),
		originator: UUID): TracedAction

	/**
	 * Replace the entire contents of the file with the provided byte array.
	 *
	 * @param data
	 *   The `ByteArray` data to add to this [AvailFile].
	 * @param timestamp
	 *   The time in milliseconds since the Unix Epoch UTC the update occurred.
	 * @param originator
	 *   The [AvailClient.id] of the session that originated the change.
	 * @return
	 *   The [TracedAction] that preserves this edit and how to reverse it.
	 */
	abstract fun replaceFile (
		data: ByteArray,
		timestamp: Long = System.currentTimeMillis(),
		originator: UUID): TracedAction

	companion object
	{
		/**
		 * A set of known text mime types.
		 */
		private val knownTextMimeTypes = setOf(
			"text/plain",
			"text/css",
			"text/csv",
			"text/html",
			"text/javascript",
			"application/json",
			"application/xml",
			"text/xml")

		/**
		 * The mime type of an Avail module file.
		 */
		val availMimeType = "text/avail"

		/**
		 * Indicate whether the provided mime type should be treated as an
		 * [AvailTextFile]?
		 *
		 * @param mimeType
		 *   The mime type to check.
		 * @return
		 *   `true` indicates it should be; `false` otherwise.
		 */
		fun isTextFile (mimeType: String) =
			knownTextMimeTypes.contains(mimeType)
	}
}
