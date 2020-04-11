/*
 * DeserializerDescriber.kt
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

package com.avail.serialization

import com.avail.AvailRuntime
import com.avail.descriptor.representation.AvailObject

import java.io.InputStream

/**
 * A [DeserializerDescriber] takes a stream of bytes and outputs a
 * description of what would be reconstructed by a [Deserializer].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `DeserializerDescriber`.
 *
 * @param input
 *   An [InputStream] from which to reconstruct objects.
 * @param runtime
 *   The [AvailRuntime] from which to locate well-known objects during
 *   deserialization.
 */
class DeserializerDescriber constructor(
	input: InputStream,
	runtime: AvailRuntime) : AbstractDeserializer(input, runtime)
{
	/** The [StringBuilder] on which the description is being written.  */
	private val builder = StringBuilder(1000)

	/**
	 * Decode all of the deserialization steps, and return the resulting
	 * [String].
	 *
	 * @return
	 *   The descriptive [String].
	 * @throws MalformedSerialStreamException
	 *   If the stream is malformed.
	 */
	@Throws(MalformedSerialStreamException::class)
	fun describe(): String
	{
		try
		{
			var objectNumber = 0
			while (input.available() > 0)
			{
				append((objectNumber++).toString())
				append(": ")
				SerializerOperation.byOrdinal(readByte()).describe(this)
				append("\n")
			}
		}
		catch (e: Exception)
		{
			throw MalformedSerialStreamException(e)
		}
		return builder.toString()
	}

	override fun objectFromIndex(index: Int) =
		throw UnsupportedOperationException()

	override fun recordProducedObject(obj: AvailObject) =
		throw UnsupportedOperationException()

	/**
	 * Append the given string to my description.
	 *
	 * @param string
	 *   The [String] to append.
	 */
	internal fun append(string: String)
	{
		builder.append(string)
	}
}
