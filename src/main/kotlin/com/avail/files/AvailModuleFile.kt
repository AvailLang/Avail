/*
 * AvailModuleFile.kt
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

import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import com.avail.descriptor.tuples.StringDescriptor
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * An `AvailModuleFile` is an [AvailFile] that is a text file that represents
 * the source of an Avail module file.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
internal class AvailModuleFile : AbstractAvailTextFile
{
	/** The String content of the file. */
	lateinit var content: A_String
		private set

	override val rawContent: ByteArray get() =
		content.asNativeString().toByteArray(StandardCharsets.UTF_16BE)

	override fun getSaveableContent(): ByteArray =
		content.asNativeString().toByteArray(StandardCharsets.UTF_8)

	/**
	 * Construct an [AvailModuleFile].
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
				content = StringDescriptor.stringWithSurrogatesFrom(
					decoder.decode(ByteBuffer.wrap(bytes)).toString())
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

	/**
	 * Construct an [AvailModuleFile].
	 *
	 * @param raw
	 *   The raw binary contents of the file.
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
			content = StringDescriptor.stringWithSurrogatesFrom(
				decoder.decode(ByteBuffer.wrap(raw)).toString())
			fileWrapper.notifyReady()
		}
		catch (e: Throwable)
		{
			System.err.println(
				"Attempted to decode bytes from supposed text file " +
					fileWrapper.reference.uri)
			System.err.println(e)
		}
	}

	override fun replaceFile(
		data: ByteArray, timestamp: Long, originator: UUID): TracedAction =
			editRange(data, 0, content.tupleSize(), originator = originator)

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
		val text =
			StringDescriptor.stringWithSurrogatesFrom(
				String(data, Charsets.UTF_16BE))

		// The text to remove from the file. The offset for one-based indexing
		// requires we begin removing from 1 beyond start.
		val removed =
			content.copyStringFromToCanDestroy(start + 1, end, false)

		// The client will use 0-based indexing in its request, so we must
		// adjust by 1. Here using the start as inclusive is good enough to
		// offset by 1.
		val first = content.copyStringFromToCanDestroy(1, start, false)

		// As stated above, must add 1 to end to keep from preserving the last
		// character marked for removal.
		val third = content.copyStringFromToCanDestroy(
			end + 1, content.tupleSize(), false)

		content = A_Tuple.concatenate(
			A_Tuple.concatenate(first, text, false), third, false)

		markDirty()
		return TracedAction(
			timestamp,
			originator,
			EditRange(data, start, end),
			EditRange(
				removed.asNativeString().toByteArray(Charsets.UTF_16BE),
				start,
				start + text.tupleSize()))
	}
}