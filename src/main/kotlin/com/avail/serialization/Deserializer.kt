/*
 * Deserializer.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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
import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import java.io.InputStream

/**
 * A [Deserializer] takes a stream of bytes and reconstructs objects that had
 * been previously [serialized][Serializer.serialize] with a [Serializer].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `Deserializer`.
 *
 * @param input
 *   An [InputStream] from which to reconstruct objects.
 * @param runtime
 *   The [AvailRuntime] from which to locate well-known objects during
 *   deserialization.
 */
class Deserializer constructor(
	input: InputStream,
	runtime: AvailRuntime) : AbstractDeserializer(input, runtime)
{
	/** The objects that have been assembled so far. */
	private val assembledObjects = mutableListOf<AvailObject>()

	/** The most recent object produced by deserialization. */
	private var producedObject: AvailObject? = null

	/** A reusable buffer of operand objects.  */
	private val subobjectsBuffer =
		Array(SerializerOperation.maxSubobjects) { nil }

	/**
	 * The [IndexCompressor] used to convert compressed indices into absolute
	 * indices into previously deserialized objects.  This must be of the same
	 * kind as the one used in [Serializer].
	 */
	private val compressor = FourStreamIndexCompressor()

	/**
	 * Record a newly reconstituted object.
	 *
	 * @param newObject
	 *   The object that should be recorded.
	 */
	private fun addObject(newObject: AvailObject)
	{
		assembledObjects.add(newObject.makeImmutable())
		compressor.incrementIndex()
	}

	/**
	 * Deserialize an object from the [input] and return it.  If there are no
	 * more objects in the input then answer null.  If the stream is malformed
	 * throw a MalformedSerialStreamException.
	 *
	 * @return
	 *   A fully deserialized object or `null`.
	 * @throws MalformedSerialStreamException
	 *   If the stream is malformed.
	 */
	@Throws(MalformedSerialStreamException::class)
	fun deserialize(): AvailObject?
	{
		assert(producedObject === null)
		try
		{
			if (input.available() == 0)
			{
				return null
			}
			while (producedObject === null)
			{
				val before = System.nanoTime()
				val ordinal = readByte()
				val operation = SerializerOperation.byOrdinal(ordinal)
				val operands = operation.operands
				for (i in operands.indices) {
					subobjectsBuffer[i] = operands[i].read(this)
				}
				val newObject = operation.compose(subobjectsBuffer, this)
				newObject.makeImmutable()
				addObject(newObject as AvailObject)
				operation.deserializeStat.record(System.nanoTime() - before)
			}
			val temp = producedObject
			producedObject = null
			return temp
		}
		catch (e: Exception)
		{
			throw MalformedSerialStreamException(e)
		}
	}

	override fun fromCompressedObjectIndex(compressedIndex: Int): AvailObject =
		assembledObjects[compressor.decompress(compressedIndex)]

	/**
	 * Record the provided object as an end product of deserialization.
	 *
	 * @param obj
	 *   The object that was produced.
	 */
	override fun recordProducedObject(
		obj: AvailObject)
	{
		assert(producedObject === null)
		producedObject = obj
	}

	companion object
	{
		/**
		 * Look up the [special&#32;atom][AvailRuntime.specialAtoms].
		 *
		 * @param index
		 *   The special atom's ordinal.
		 * @return
		 *   The special atom known to the virtual machine's runtime.
		 */
		internal fun specialAtom(index: Int): A_Atom =
			AvailRuntime.specialAtoms[index]
	}
}
