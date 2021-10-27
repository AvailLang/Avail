/*
 * BuildInputStream.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

package avail.anvil.streams

import androidx.compose.ui.text.AnnotatedString
import avail.utility.javaWait
import java.io.ByteArrayInputStream
import kotlin.math.min

/**
 * [AnvilInputStream] is an [ByteArrayInputStream] that satisfies reads of:
 *  * Saved entry point transcripts
 *  * Saved build transcripts
 */
class AnvilInputStream constructor(
	bytes: ByteArray = ByteArray(1024),
	offset: Int = 0,
	length: Int = 0
): ByteArrayInputStream(bytes, offset, length)
{
	/**
	 * Clear the input stream. All pending data is discarded and the stream
	 * position is reset to zero (`0`).
	 */
	@Synchronized
	fun clear()
	{
		count = 0
		pos = 0
	}

	fun append (data: ByteArray)
	{

	}

	/**
	 * The next [StyledStreamEntry] or `null` if fully read.
	 */
	internal val nextStyledStreamEntry: StyledStreamEntry? get() =
		if (pos == count) { null }
		else { StyledStreamEntry.read(this) }

	/**
	 * Answer an [AnnotatedString] of the contents of this [AnvilInputStream].
	 *
	 * @param isDark
	 *   `true` if [StreamStyle.dark] is to be used; `false` indicates
	 *   [StreamStyle.light] is to be used.
	 * @return
	 *   The [AnnotatedString].
	 */
	fun toAnnotatedString (isDark: Boolean): AnnotatedString
	{
		val builder = AnnotatedString.Builder()
		var next = nextStyledStreamEntry
		while (next != null)
		{
			builder.append(next.annotatedString(isDark))
			next = nextStyledStreamEntry
		}
		return builder.toAnnotatedString()
	}

	override fun markSupported(): Boolean
	{
		return false
	}

	override fun mark(readAheadLimit: Int)
	{
		throw UnsupportedOperationException()
	}

	@Synchronized
	override fun reset()
	{
		throw UnsupportedOperationException()
	}

	@Synchronized
	override fun read(): Int
	{
		// Block until data is available.
		try
		{
			while (pos == count)
			{
				javaWait()
			}
		}
		catch (e: InterruptedException)
		{
			return -1
		}
		return buf[pos++].toInt() and 0xFF
	}

	@Synchronized
	override fun read(
		readBuffer: ByteArray?, start: Int, requestSize: Int): Int
	{
		assert(readBuffer !== null)
		if (requestSize <= 0)
		{
			return 0
		}
		// Block until data is available.
		try
		{
			while (pos == count)
			{
				javaWait()
			}
		}
		catch (e: InterruptedException)
		{
			return -1
		}

		val bytesToTransfer = min(requestSize, count - pos)
		System.arraycopy(buf, pos, readBuffer!!, start, bytesToTransfer)
		pos += bytesToTransfer
		return bytesToTransfer
	}
}
