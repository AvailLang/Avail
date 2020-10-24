/*
 * BuildInputStream.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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

package com.avail.environment.streams

import com.avail.environment.AvailWorkbench
import com.avail.utility.javaNotifyAll
import com.avail.utility.javaWait
import java.io.ByteArrayInputStream
import kotlin.math.max
import kotlin.math.min

/**
 * [BuildInputStream] satisfies reads from the UI's input field.  It blocks
 * reads unless some data is available.
 *
 * @constructor
 * Construct a new `BuildInputStream`.
 */
class BuildInputStream(
	private val workbench: AvailWorkbench
) : ByteArrayInputStream(ByteArray(1024), 0, 0)
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

	/**
	 * Update the content of the stream with data from the
	 * [input&#32;field][AvailWorkbench.inputField].
	 */
	@Synchronized
	fun update()
	{
		val text = workbench.inputField.text + "\n"
		val bytes = text.toByteArray()
		if (pos + bytes.size >= buf.size)
		{
			val newSize = max(
				buf.size shl 1, bytes.size + buf.size)
			val newBuf = ByteArray(newSize)
			System.arraycopy(buf, 0, newBuf, 0, buf.size)
			buf = newBuf
		}
		System.arraycopy(bytes, 0, buf, count, bytes.size)
		count += bytes.size
		workbench.writeText(text, StreamStyle.IN_ECHO)
		workbench.inputField.text = ""
		javaNotifyAll()
	}

	/**
	 * The specified command string was just entered.  Present it in the
	 * [StreamStyle.COMMAND] style.  Force an extra leading new line
	 * to keep the text area from looking stupid.  Also end with a new line.
	 * The passed command should not itself have a new line included.
	 *
	 * @param commandText
	 * The command that was entered, with no leading or trailing line
	 * breaks.
	 */
	@Synchronized
	fun feedbackForCommand(
		commandText: String)
	{
		val textToInsert = "\n" + commandText + "\n"
		workbench.writeText(textToInsert,
			StreamStyle.COMMAND)
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