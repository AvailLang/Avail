/*
 * AvailTextFile.kt
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

package com.avail.files

import com.avail.descriptor.tuples.A_Tuple
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.UUID

/**
 * An `AvailTextFile` is an [AvailFile] that is a nondescript text file
 * contained in an Avail `ModuleRoot` hierarchy of files.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
internal class AvailTextFile : AbstractAvailTextFile
{
	/** The String content of the file. */
	private lateinit var content: String

	override val rawContent: ByteArray get() = content.toByteArray(charset)

	override fun getSaveableContent(): ByteArray = rawContent

	/**
	 * Construct an [AvailTextFile].
	 *
	 * @param fileWrapper
	 *   The [ManagedFileWrapper] that wraps this [AvailFile].
	 * @param charset
	 *   The [Charset] of the file.
	 */
	constructor(
		fileWrapper: AbstractFileWrapper,
		charset: Charset = Charsets.UTF_8): super(charset, fileWrapper)
	{
		fileWrapper.reference.readFile(true,
		{ bytes, _ ->
			val decoder = charset.newDecoder()
			decoder.onMalformedInput(CodingErrorAction.REPLACE)
			decoder.onUnmappableCharacter(CodingErrorAction.REPLACE)
			try
			{
				content = decoder.decode(ByteBuffer.wrap(bytes)).toString()
			}
			catch (e: CharacterCodingException)
			{
				System.err.println(
					"Attempted to decode bytes from supposed text file " +
						fileWrapper.reference.uri)
				e.printStackTrace()
				fileWrapper.notifyOpenFailure(FileErrorCode.DECODER_FAILURE, e)
				return@readFile
			}
			catch (e: Throwable)
			{
				System.err.println(
					"Attempted to decode bytes from supposed text file " +
						fileWrapper.reference.uri)
				e.printStackTrace()
				fileWrapper.notifyOpenFailure(FileErrorCode.UNSPECIFIED, e)
				return@readFile
			}
			fileWrapper.notifyReady()
		}) { code, ex ->
			System.err.println(
				"Received ErrorCode: $code while attempting read file: " +
					"${fileWrapper.reference.uri} with exception:\n")
			ex?.printStackTrace()
			fileWrapper.notifyOpenFailure(code, ex)
		}
	}

	/**
	 * Construct an [AvailTextFile].
	 *
	 * @param raw
	 *   The raw binary file contents.
	 * @param fileWrapper
	 *   The [ManagedFileWrapper] that wraps this [AvailFile].
	 * @param charset
	 *   The [Charset] of the file.
	 */
	constructor(
		raw : ByteArray,
		fileWrapper: AbstractFileWrapper,
		charset: Charset = Charsets.UTF_8): super(charset, fileWrapper)
	{
		val decoder = charset.newDecoder()
		decoder.onMalformedInput(CodingErrorAction.REPLACE)
		decoder.onUnmappableCharacter(CodingErrorAction.REPLACE)
		try
		{
			content = decoder.decode(ByteBuffer.wrap(raw)).toString()
		}

		catch (e: CharacterCodingException)
		{
			System.err.println(
				"Attempted to decode bytes from supposed text file " +
					fileWrapper.reference.uri)
			e.printStackTrace()
			fileWrapper.notifyOpenFailure(FileErrorCode.DECODER_FAILURE, e)
			return
		}
		catch (e: Throwable)
		{
			System.err.println(
				"Attempted to decode bytes from supposed text file " +
					fileWrapper.reference.uri)
			e.printStackTrace()
			fileWrapper.notifyOpenFailure(FileErrorCode.UNSPECIFIED, e)
			return
		}
		fileWrapper.notifyReady()
	}

	override fun replaceFile(
		data: ByteArray, timestamp: Long, originator: UUID): TracedAction =
			editRange(data, 0, content.length, originator = originator)

	/**
	 * Insert the [ByteArray] data into the file at the specified location. This
	 * should remove existing data in the file in this range and replace it
	 * with the provided data. This should preserve all data outside of this
	 * range.
	 *
	 * The client will use 0-based indexing in its request, so we must adjust by
	 * one. The file, in zero-based indexing must be prefixed before the
	 * requested `start`. Shifting to Avail's one-based indexing and utilizing
	 * the [A_Tuple.copyTupleFromToCanDestroy]'s inclusive range requires the
	 * remove being after the requested `start`. Because the `start` is shifted
	 * and correctly inclusive in the first half of the file, the 2nd half of
	 * the file, the `end` must begin at the index one place beyond `end`
	 * (`end + 1`). Thus the inserted text happens after the `start` position.
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
	 * @return The [TracedAction] that preserves this edit and how to reverse
	 *   it.
	 */
	override fun editRange(
		data: ByteArray,
		start: Int,
		end: Int,
		timestamp: Long,
		originator: UUID): TracedAction
	{
		// The text to insert in the file
		val text = String(data, charset)

		// The text to remove from the file. The offset for one-based indexing
		// requires we begin removing from 1 beyond start.
		val removed = content.slice(IntRange(start + 1, end))

		// The client will use 0-based indexing in its request, so we must
		// adjust by 1. Here using the start as inclusive is good enough to
		// offset by 1.
		val first = content.slice(IntRange(1, start))

		// As stated above, must add 1 to end to keep from preserving the last
		// character marked for removal.
		val third = content.slice(IntRange(end + 1, content.length))

		content = first + text + third

		markDirty()
		return TracedAction(
			timestamp,
			originator,
			EditRange(data, start, end),
			EditRange(
				removed.toByteArray(charset),
				start,
				start + text.length))
	}
}
