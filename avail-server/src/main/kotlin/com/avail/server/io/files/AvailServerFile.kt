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

import com.avail.io.SimpleCompletionHandler
import com.avail.server.error.ServerErrorCode
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel

/**
 * `AvailSeverFile` is an interface for declaring behavior and state for a file
 * that has been opened by the Avail Server.
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
	protected val path: String,
	protected val file: AsynchronousFileChannel,
	protected val mimeType: String,
	val serverFileWrapper: ServerFileWrapper)
{
	/**
     * The raw bytes of the file.
	 */
	abstract val rawContent: ByteArray

	/** Close the backing channel [file]. */
	fun close () = file.close()

	/**
	 * `true` indicates the [file] is open; `false` indicates it is closed.
	 */
	val isOpen: Boolean get() = file.isOpen

	/**
	 * Save the data to disk starting at the specified write location.
	 *
	 * @param data
	 *   The [ByteBuffer] to save to disk for this [AvailServerFile].
	 * @param writePosition
	 *   The position in the file to start writing to.
	 * @param failure
	 *   A function that accepts a [ServerErrorCode] that describes the nature
	 *   of the failure and an optional [Throwable]. TODO refine error handling
	 */
	private fun save (
		data: ByteBuffer,
		writePosition: Long,
		failure: (ServerErrorCode, Throwable?) -> Unit)
	{
		file.write(
			data,
			writePosition,
			null,
			SimpleCompletionHandler<Int, ServerErrorCode?>(
				{ result, _, _ ->
					if (data.hasRemaining())
					{
						save(data, data.position().toLong(), failure)
					}
				})
			{ e, code, _ ->
				failure(code ?: ServerErrorCode.UNSPECIFIED, e)
			})
	}

	/**
	 * Save the [rawContent] to disk.
	 *
	 * @param failure
	 *   A function that accepts a [ServerErrorCode] that describes the nature
	 *   of the failure and an optional [Throwable]. TODO refine error handling
	 */
	fun save(failure: (ServerErrorCode, Throwable?) -> Unit)
	{
		val content = rawContent
		val data = ByteBuffer.wrap(content)
		file.write(
			data,
			0,
			null,
			SimpleCompletionHandler<Int, ServerErrorCode?>(
				{ _, _, _ ->
					if (data.hasRemaining())
					{
						save(data, data.position().toLong(), failure)
					}
				})
			{ e, code, _ ->
				failure(code ?: ServerErrorCode.UNSPECIFIED, e)
			})
	}

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
	 * @return The [TracedAction] that preserves this edit and how to reverse
	 *   it.
	 */
	abstract fun insertRange (
		data: ByteArray,
		start: Int,
		end: Int,
		timestamp: Long = System.currentTimeMillis()): TracedAction
}
