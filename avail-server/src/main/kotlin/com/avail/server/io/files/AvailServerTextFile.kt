/*
 * AvailServerTextFile.kt
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
import com.avail.utility.MutableLong
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.charset.Charset

/**
 * An `AvailServerTextFile` is an [AvailServerFile] that is specific to textual
 * files.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property charset
 *   The [Charset] of the file.
 * @constructor
 * Construct an [AvailServerTextFile].
 *
 * @param path
 *   The on-disk absolute location of the file.
 * @param file
 *   The [AsynchronousFileChannel] used to access the file.
 * @param mimeType
 *   The MIME type of the file.
 * @param serverFileWrapper
 *   The [ServerFileWrapper] that wraps this [AvailServerFile].
 * @param charset
 *   The [Charset] of the file.
 */
internal class AvailServerTextFile constructor(
		path: String,
		file: AsynchronousFileChannel,
		mimeType: String,
		serverFileWrapper: ServerFileWrapper,
		private val charset: Charset = Charsets.UTF_8)
	: AvailServerFile(path, file, mimeType, serverFileWrapper)
{
	/** The String content of the file. */
	private lateinit var content: String

	override val rawContent: ByteArray get() = content.toByteArray(charset)

	// TODO add log of local history to account for undo's and a way of keeping
	//  historical versions of the files.

	init
	{
		val sourceBuilder = StringBuilder(4096)
		val filePosition = MutableLong(0L)
		val input = ByteBuffer.allocateDirect(4096)
		val decoder = charset.newDecoder()
		val output = CharBuffer.allocate(4096)
		this.file.read<Any>(
			input,
			0L,
			null,
			SimpleCompletionHandler<Int, Any?>(
				{ bytesRead, _, handler ->
					try
					{
						var moreInput = true
						if (bytesRead == -1)
						{
							moreInput = false
						}
						else
						{
							filePosition.value += bytesRead.toLong()
						}
						input.flip()

						// TODO not sure if we should care about result
						val result = decoder.decode(
							input, output, !moreInput)
						// If the decoder didn't consume all of the bytes,
						// then preserve the unconsumed bytes in the next
						// buffer (for decoding).
						if (input.hasRemaining())
						{
							input.compact()
						}
						else
						{
							input.clear()
						}
						output.flip()
						sourceBuilder.append(output)
						// If more input remains, then queue another read.
						if (moreInput)
						{
							output.clear()
							this.file.read<Any>(
								input,
								filePosition.value,
								null,
								handler)
						}
						// Otherwise, notify the serverFileWrapper of completion
						else
						{
							decoder.flush(output)
							sourceBuilder.append(output)
							content = sourceBuilder.toString()
							serverFileWrapper.notifyReady()
						}
					}
					catch (e: IOException)
					{
						TODO("Handle AvailServerTextFile read decode bytes fail")
					}
				},
				{ e, _, _ ->
					TODO("Handle AvailServerTextFile read fail")
				}))
	}

	override fun insert (
		data: ByteArray, position: Int, timestamp: Long): List<EditAction>
	{
		val text = String(data, charset)
		content = content.substring(0, position) +
		          text + content.substring(position, content.length)
		return listOf(RemoveRange(timestamp, position, text.length))
	}

	override fun removeRange(
		start: Int, end: Int, timestamp: Long): List<EditAction>
	{
		val data = content.substring(start, end)
		content = content.substring(0, start + 1) +
			content.substring(end, content.length)
		return listOf(Insert(timestamp, data.toByteArray(charset), start))
	}
}