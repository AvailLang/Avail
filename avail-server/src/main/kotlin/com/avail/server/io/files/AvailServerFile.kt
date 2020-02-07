/*
 * AvailServerFile.kt
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

import com.avail.server.AvailServer
import java.io.IOException
import java.nio.channels.AsynchronousFileChannel
import java.util.logging.Level

/**
 * `AvailSeverFile` is a specification for declaring behavior and state for a
 * file that has been opened by the Avail Server.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property path
 *   The on-disk absolute location of the file.
 * @property file
 *   The [AsynchronousFileChannel] used to access the file.
 * @property mimeType
 *   The MIME type of the file.
 * @property serverFileWrapper
 *   The [ServerFileWrapper] that wraps this [AvailServerFile].
 *
 * @constructor
 * Construct an [AvailServerFile].
 *
 * @param path
 *   The on-disk absolute location of the file.
 * @param file
 *   The [AsynchronousFileChannel] used to access the file.
 * @param mimeType
 *   The MIME type of the file.
 * @param serverFileWrapper
 *   The [ServerFileWrapper] that wraps this [AvailServerFile].
 */
internal abstract class AvailServerFile constructor(
	val path: String,
	protected val file: AsynchronousFileChannel,
	val mimeType: String,
	val serverFileWrapper: ServerFileWrapper)
{
	/** The raw bytes of the file. */
	abstract val rawContent: ByteArray

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

	/** Close the backing channel [file]. */
	fun close () =
		try
		{
			file.close()
		}
		catch (e: IOException)
		{
			AvailServer.logger.log(Level.WARNING, e) {
				"Could not close file, $path" }
		}

	/**
	 * `true` indicates the [file] is open; `false` indicates it is closed.
	 */
	val isOpen: Boolean get() = file.isOpen

	/**
	 * Insert the [ByteArray] data into the file at the specified location. This
	 * should remove existing data in the file in this range and replace it
	 * with the provided data. This should preserve all data outside of this
	 * range.
	 *
	 * @param data
	 *   The `ByteArray` data to add to this [AvailServerFile].
	 * @param start
	 *   The location in the file to inserting/overwriting the data, exclusive.
	 * @param end
	 *   The location in the file to stop overwriting. All data after this point
	 *   should be preserved.
	 * @param timestamp
	 *   The time in milliseconds since the Unix Epoch UTC the update occurred.
	 * @return
	 *   The [TracedAction] that preserves this edit and how to reverse it.
	 */
	abstract fun editRange (
		data: ByteArray,
		start: Int,
		end: Int,
		timestamp: Long = System.currentTimeMillis()): TracedAction
}
