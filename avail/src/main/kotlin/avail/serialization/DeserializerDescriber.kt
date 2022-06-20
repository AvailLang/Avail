/*
 * DeserializerDescriber.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package avail.serialization

import avail.AvailRuntime
import avail.descriptor.representation.AvailObject
import org.availlang.persistence.MalformedSerialStreamException

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
	runtime: AvailRuntime) : AbstractDeserializer(
		input,
		runtime,
		{ throw Exception("DeserializerDescriber cannot be pumped") })
{
	/** The [StringBuilder] on which the description is being written. */
	private val builder = StringBuilder(1000)

	/**
	 * The [IndexCompressor] used to convert compressed indices into absolute
	 * indices into previously deserialized objects.  This must be of the same
	 * kind as the one used in [Serializer].
	 */
	private val compressor = FourStreamIndexCompressor()

	/**
	 * Describe a compressed index on the [builder], decompressing it to ensure
	 * the [compressor] is advanced to its next state correctly.
	 *
	 * Indices are shown as `[compressed:decompressed]`.
	 */
	fun printCompressedIndex(compressedIndex: Int)
	{
		val decompressed = compressor.decompress(compressedIndex)
		builder.append("[$compressedIndex:$decompressed]")
	}

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
			while (input.available() > 0)
			{
				append((compressor.currentIndex()).toString())
				append(": ")
				SerializerOperation.byOrdinal(readByte()).describe(this)
				append("\n")
				compressor.incrementIndex()
			}
		}
		catch (e: Exception)
		{
			throw MalformedSerialStreamException(e)
		}
		return builder.toString()
	}

	override fun fromCompressedObjectIndex(compressedIndex: Int) =
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

	/**
	 * Append the given character to my description.
	 *
	 * @param char
	 *   The [Char] to append.
	 */
	internal fun append(char: Char)
	{
		builder.append(char)
	}
}
