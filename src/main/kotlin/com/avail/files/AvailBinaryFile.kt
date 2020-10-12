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

package com.avail.files

import java.util.UUID

/**
 * `AvailBinaryFile` is an [AvailFile] that contains a strictly
 * binary file.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 * Construct an [AvailBinaryFile].
 *
 * @param fileWrapper
 *   The [AbstractFileWrapper] that wraps this [AvailFile].
 */
internal class AvailBinaryFile constructor(
		fileWrapper: AbstractFileWrapper)
	: AvailFile(fileWrapper)
{
	/** The String content of the file. */
	private var content = ByteArray(0)

	override val rawContent get() = content

	init
	{
		fileWrapper.reference.readFile(true,
			{ bytes, _ ->
				try
				{
					content = bytes
					fileWrapper.notifyReady()
				}
				catch (e: Throwable)
				{
					System.err.println(
						"Attempted to decode bytes from supposed text file " +
							fileWrapper.reference.uri)
					System.err.println(e)
				}
			}) { code, ex ->
			// TODO figure out what to do with these!!! Probably report them?
			System.err.println(
				"Received ErrorCode: $code while attempting read file: " +
					"${fileWrapper.reference.uri} with exception:\n")
			ex?.printStackTrace()
		}
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