/*
 * BuildOutputStream.kt
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

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.nio.charset.StandardCharsets

/**
 * [AnvilOutputStream] intercepts writes of [StyledStreamEntry] data.
 */
class AnvilOutputStream: ByteArrayOutputStream(1)
{
	/**
	 * Transfer any data in my buffer into the updateQueue, starting up a UI
	 * task to transfer them to the document as needed.
	 */
	private fun queueForTranscript()
	{
		assert(Thread.holdsLock(this))
		val text: String
		try
		{
			text = toString(StandardCharsets.UTF_8.name())
		}
		catch (e: UnsupportedEncodingException)
		{
			assert(false) { "Somehow Java doesn't support characters" }
			throw RuntimeException(e)
		}

		if (text.isEmpty())
		{
			// Nothing new to display.
			return
		}
		reset()
	}

	/**
	 * Answer an [AnvilInputStream] from this [AnvilOutputStream].
	 *
	 * @return
	 *   A new [AnvilInputStream] containing the data from this
	 *   [AnvilOutputStream].
	 */
	fun toAnvilInputStream (): AnvilInputStream
	{
		val copyBuf = ByteArray(count)
		System.arraycopy(buf, 0, copyBuf, 0, count)
		return AnvilInputStream(copyBuf)
	}

	/**
	 * Answer a [ByteArray] copy starting from the indicated position to the
	 * size of this [AnvilOutputStream].
	 */
	fun copyFromPosition (nextReadPosition: Int): ByteArray
	{
		val bufSize = count - nextReadPosition
		val copyBuf = ByteArray(bufSize)
		System.arraycopy(buf, nextReadPosition, copyBuf, 0, bufSize)
		return copyBuf
	}

	@Synchronized
	override fun write(b: Int)
	{
		super.write(b)
		queueForTranscript()
	}

	@Synchronized
	@Throws(IOException::class)
	override fun write(b: ByteArray?)
	{
		assert(b !== null)
		super.write(b!!)
		queueForTranscript()
	}

	@Synchronized
	override fun write(
		b: ByteArray?,
		off: Int,
		len: Int)
	{
		assert(b !== null)
		super.write(b!!, off, len)
		queueForTranscript()
	}

	/**
	 * Write the provided [StyledStreamEntry] to this [AnvilOutputStream].
	 *
	 * @param streamEntry
	 *   The entry to write.
	 */
	internal fun write (streamEntry: StyledStreamEntry)
	{
		streamEntry.serializeTo(this)
	}
}
