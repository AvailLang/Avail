/*
 * AvailServerBinaryFile.kt
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
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.util.UUID

/**
 * `AvailServerBinaryFile` is an [AvailServerFile] that contains a strictly
 * binary file.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 * Construct an [AvailServerBinaryFile].
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
internal class AvailServerBinaryFile constructor(
		path: String,
		file: AsynchronousFileChannel,
		mimeType: String,
		serverFileWrapper: ServerFileWrapper)
	: AvailServerFile(path, file, mimeType, serverFileWrapper)
{
	/** The String content of the file. */
	private var content = ByteArray(0)

	override val rawContent get() = content

	init
	{
		var filePosition = 0L
		val input = ByteBuffer.allocateDirect(4096)

//		this.file.read<Any>(
//			input,
//			0L,
//			null,
			SimpleCompletionHandler<Int>(
				{
					try
					{
						var moreInput = true
						if (value == -1)
						{
							moreInput = false
						}
						else
						{
							filePosition += value.toLong()
						}
						input.flip()
						val data = ByteArray(input.limit())
						input.get(data)
						content += data
						// If more input remains, then queue another read.
						if (moreInput)
						{
							handler.guardedDo {
								file.read(input, filePosition, dummy, handler)
							}
						}
						// Otherwise, close the file channel and notify the
						// serverFileWrapper of completion. No reason to keep
						// it open as it cannot be edited.
						else
						{
							serverFileWrapper.notifyReady()
						}
					}
					catch (e: IOException)
					{
						serverFileWrapper.notifyOpenFailure(
							ServerErrorCode.IO_EXCEPTION, e)
					}
				},
				{
					serverFileWrapper.notifyOpenFailure(
						ServerErrorCode.IO_EXCEPTION, throwable)
				}).guardedDo { file.read(input, 0L, dummy, handler) }
	}

	override fun replaceFile(
		data: ByteArray, timestamp: Long, originator: UUID): TracedAction =
			editRange(data, 0, content.size, originator = originator)

	override fun editRange(
		data: ByteArray,
		start: Int,
		end: Int,
		timestamp: Long,
		originator: UUID): TracedAction
	{
		val removed = content.copyOfRange(start, end)
		content = content.copyOfRange(0, start) +
			data + content.copyOfRange(end, content.size)
		markDirty()
		return TracedAction(
			timestamp,
			originator,
			EditRange(data, start, end),
			EditRange(removed, start, start + data.size))
	}
}