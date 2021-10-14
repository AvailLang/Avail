/*
 * ConsoleInputChannel.kt
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

package avail.io

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.CharBuffer
import java.nio.channels.CompletionHandler
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * A `ConsoleInputChannel` provides a faux
 * [asynchronous&#32;interface][TextInputChannel] to a synchronous
 * [input&#32;stream][InputStream]. The reader must supply
 * [UTF-8][StandardCharsets.UTF_8] encoded characters.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 *
 * @constructor
 *   Construct a new [ConsoleInputChannel] that wraps the specified
 *   [stream][InputStream].
 *
 * @param stream
 *   An input stream. This should generally be [System. in].
 */
class ConsoleInputChannel constructor(stream: InputStream) : TextInputChannel
{
	/** The wrapped [reader][Reader]. */
	private val input: Reader

	init
	{
		val decoder = StandardCharsets.UTF_8.newDecoder()
		decoder.onMalformedInput(CodingErrorAction.REPLACE)
		decoder.onUnmappableCharacter(CodingErrorAction.REPLACE)
		input = BufferedReader(InputStreamReader(stream, decoder))
	}

	// The standard input stream is always open; we do not permit it to be
	// closed, at any rate.
	override fun isOpen(): Boolean = true

	override fun <A> read(
		buffer: CharBuffer, attachment: A?, handler: CompletionHandler<Int, A>)
	{
		val charsRead: Int
		try
		{
			charsRead = input.read(buffer)
			if (charsRead == -1)
			{
				throw IOException("end of stream")
			}
		}
		catch (e: IOException)
		{
			handler.failed(e, attachment)
			return
		}

		handler.completed(charsRead, attachment)
	}

	@Throws(IOException::class)
	override fun mark(readAhead: Int)
	{
		input.mark(readAhead)
	}

	@Throws(IOException::class)
	override fun reset()
	{
		input.reset()
	}

	override fun close()
	{
		// Do nothing. Definitely don't close the underlying input stream.
	}
}
