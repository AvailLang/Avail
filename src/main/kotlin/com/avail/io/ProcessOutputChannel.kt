/*
 * ProcessOutputChannel.kt
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

package com.avail.io

import com.avail.AvailRuntime.Companion.currentRuntime
import java.io.BufferedWriter
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.PrintStream
import java.io.Writer
import java.nio.CharBuffer
import java.nio.channels.CompletionHandler
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * A `ProcessInputChannel` provides a faux
 * [asynchronous&#32;interface][TextOutputChannel] to a synchronous
 * [output&#32;stream][PrintStream].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct a new `ProcessOutputChannel` that wraps the specified
 * [output&#32;stream][PrintStream].
 *
 * @param stream
 *   An output stream. This should generally be either [System.out] or
 *   [System.err].
 */
class ProcessOutputChannel constructor(stream: PrintStream) : TextOutputChannel
{
	/** The wrapped [writer][Writer].  */
	internal val out: Writer

	init
	{
		val encoder = StandardCharsets.UTF_8.newEncoder()
		encoder.onMalformedInput(CodingErrorAction.REPLACE)
		encoder.onUnmappableCharacter(CodingErrorAction.REPLACE)
		out = BufferedWriter(OutputStreamWriter(stream, encoder))
	}

	// The standard output stream is always open; we do not permit it to be
	// closed, at any rate.
	override fun isOpen(): Boolean = false

	override fun <A> write(
		buffer: CharBuffer, attachment: A?, handler: CompletionHandler<Int, A>)
	{
		val runtime = currentRuntime()
		runtime.ioSystem().executeFileTask (Runnable {
			try
			{
				out.write(buffer.toString())
				out.flush()
			}
			catch (e: IOException)
			{
				handler.failed(e, attachment)
				return@Runnable
			}

			handler.completed(buffer.limit(), attachment)
		})
	}

	override fun <A> write(
		data: String, attachment: A?, handler: CompletionHandler<Int, A>)
	{
		val runtime = currentRuntime()
		runtime.ioSystem().executeFileTask (Runnable {
			try
			{
				out.write(data)
				out.flush()
			}
			catch (e: IOException)
			{
				handler.failed(e, attachment)
				return@Runnable
			}

			handler.completed(data.length, attachment)
		})
	}

	override fun close()
	{
		// Do nothing. Definitely don't close the underlying output stream.
	}
}
