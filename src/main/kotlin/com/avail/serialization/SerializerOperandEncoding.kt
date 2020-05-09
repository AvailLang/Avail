/*
 * SerializerOperandEncoding.kt
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

import com.avail.descriptor.character.A_Character.Companion.codePoint
import com.avail.descriptor.character.CharacterDescriptor.Companion.fromCodePoint
import com.avail.descriptor.maps.MapDescriptor
import com.avail.descriptor.maps.MapDescriptor.Companion.emptyMap
import com.avail.descriptor.numbers.IntegerDescriptor
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.createUninitializedInteger
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromLong
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.intCount
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.tuples.ByteStringDescriptor.generateByteString
import com.avail.descriptor.tuples.ByteTupleDescriptor.generateByteTupleFrom
import com.avail.descriptor.tuples.NybbleTupleDescriptor.generateNybbleTupleFrom
import com.avail.descriptor.tuples.ObjectTupleDescriptor.generateObjectTupleFrom
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.tuples.TupleDescriptor.emptyTuple
import com.avail.descriptor.tuples.TupleDescriptor.tupleFromIntegerList
import com.avail.descriptor.tuples.TwoByteStringDescriptor.generateTwoByteString
import com.avail.utility.Strings.increaseIndentation
import java.io.OutputStream
import java.util.*

/**
 * A `SerializerOperandEncoding` is an encoding algorithm for part of a
 * [SerializerOperation].  It assists in the disassembly and reassembly of the
 * various kinds of objects encountered in Avail.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
internal enum class SerializerOperandEncoding
{
	/**
	 * This is an [AvailObject] that's always an [integer][IntegerDescriptor] in
	 * the range [0..255].  This is a particularly concise representation,
	 * useful for bit fields, enumerations, and other limited values.
	 */
	BYTE
	{
		override fun write(obj: AvailObject, serializer: Serializer) =
			serializer.writeByte(obj.extractUnsignedByte().toInt())

		override fun read(deserializer: AbstractDeserializer) =
			fromInt(deserializer.readByte())
	},

	/**
	 * This is an [AvailObject] that's always an [ ] in the range [0..65535].
	 * Some system limits fall within this range (e.g., number of arguments to a
	 * function), allowing this compact representation to be used.
	 *
	 * This operand uses the compressed representation below, which may not be
	 * effective for some uses, in which case [UNCOMPRESSED_SHORT] may be
	 * more appropriate.
	 *
	 *  * 0x0000..0x007F take one byte.
	 *  * 0x0080..0x7EFF take two bytes.
	 *  * 0x7F00..0xFFFF take three bytes.
	 */
	COMPRESSED_SHORT
	{
		override fun write(obj: AvailObject, serializer: Serializer)
		{
			val shortValue = obj.extractInt()
			assert(shortValue and 0xFFFF == shortValue)
			when
			{
				shortValue < 0x80 -> serializer.writeByte(shortValue)
				shortValue < 0x7F00 ->
				{
					serializer.writeByte((shortValue shr 8) + 128)
					serializer.writeByte(shortValue and 0xFF)
				}
				else ->
				{
					serializer.writeByte(255)
					serializer.writeShort(shortValue)
				}
			}
		}

		override fun read(deserializer: AbstractDeserializer): AvailObject
		{
			val firstByte = deserializer.readByte()
			val intValue: Int
			intValue = when
			{
				firstByte < 128 -> firstByte
				firstByte < 255 ->
					(firstByte - 128 shl 8) + deserializer.readByte()
				else -> deserializer.readShort()
			}
			return fromInt(intValue)
		}
	},

	/**
	 * This is an [AvailObject] that's always an [integer][IntegerDescriptor] in
	 * the range [0..65535].  This is a particularly concise representation,
	 * useful for bit fields, enumerations, and other limited values.
	 *
	 * In this case don't attempt to compress it smaller than two bytes, since
	 * we have knowledge that it probably won't be effective.
	 */
	UNCOMPRESSED_SHORT
	{
		override fun write(obj: AvailObject, serializer: Serializer) =
			serializer.writeShort(obj.extractUnsignedShort())

		override fun read(deserializer: AbstractDeserializer): AvailObject =
			fromInt(deserializer.readShort())
	},

	/**
	 * This is an [AvailObject] that's always an [integer][IntegerDescriptor] in
	 * the same range as Java's signed int, -2<sup>31</sup> through
	 * 2<sup>31</sup>-1.  Some system limits fall within this range, allowing
	 * this compact representation to be used.
	 */
	SIGNED_INT
	{
		override fun write(obj: AvailObject, serializer: Serializer)
		{
			val intValue = obj.extractInt()
			serializer.writeInt(intValue)
		}

		override fun read(deserializer: AbstractDeserializer) =
			fromInt(deserializer.readInt())
	},

	/**
	 * This is an [AvailObject] that's always a positive
	 * [integer][IntegerDescriptor] in the range 0 through 2<sup>32</sup>-1.
	 * Some system limits fall within this range, allowing this compact
	 * representation to be used.
	 */
	UNSIGNED_INT
	{
		override fun write(obj: AvailObject, serializer: Serializer)
		{
			val longValue = obj.extractLong()
			assert(longValue and 0xFFFFFFFFL == longValue)
			serializer.writeInt(longValue.toInt())
		}

		override fun read(deserializer: AbstractDeserializer): AvailObject
		{
			val intValue = deserializer.readInt()
			val longValue = intValue.toLong() and 0xFFFFFFFFL
			return fromLong(longValue)
		}
	},

	/**
	 * This is an [AvailObject] that occurred previously in the sequence of
	 * objects.  A variable-length integer is encoded in the stream to indicate
	 * the object's absolute subscript within the serialization stream.
	 */
	OBJECT_REFERENCE
	{
		override fun trace(obj: AvailObject, serializer: Serializer) =
			// Visit the object.
			serializer.traceOne(obj)

		override fun write(obj: AvailObject, serializer: Serializer)
		{
			val instruction = serializer.instructionForObject(obj)
			val index = instruction.index
			assert(index >= 0) {
				"Attempted to write reference to untraced object."
			}
			writeCompressedPositiveInt(index, serializer)
		}

		override fun read(deserializer: AbstractDeserializer): AvailObject
		{
			val index = readCompressedPositiveInt(deserializer)
			return deserializer.objectFromIndex(index)
		}

		override fun describe(describer: DeserializerDescriber)
		{
			val index = readCompressedPositiveInt(describer)
			describer.append("#")
			describer.append(index.toString())
		}
	},

	/**
	 * This is an [AvailObject] that's an [integer][IntegerDescriptor] of any
	 * size.  It writes a compressed int for the number of int slots, then the
	 * big-endian sequence of (also internally big-endian) uncompressed `int`s.
	 * Only the first int in that sequence is to be considered to have a sign.
	 */
	BIG_INTEGER_DATA
	{
		override fun write(obj: AvailObject, serializer: Serializer)
		{
			val slotsCount = intCount(obj)
			writeCompressedPositiveInt(slotsCount, serializer)
			for (i in slotsCount downTo 1)
			{
				serializer.writeInt(obj.rawSignedIntegerAt(i))
			}
		}

		override fun read(deserializer: AbstractDeserializer): AvailObject
		{
			val slotsCount = readCompressedPositiveInt(deserializer)
			val newInteger = createUninitializedInteger(slotsCount)
			for (i in slotsCount downTo 1)
			{
				newInteger.rawSignedIntegerAtPut(i, deserializer.readInt())
			}
			newInteger.makeImmutable()
			return newInteger
		}
	},

	/**
	 * This is a [tuple][TupleDescriptor] of arbitrary objects, written as a
	 * compressed size and a sequence of compressed object references.
	 */
	TUPLE_OF_OBJECTS
	{
		override fun trace(obj: AvailObject, serializer: Serializer)
		{
			// Visit the *elements* of the tuple.
			for (element in obj)
			{
				serializer.traceOne(element as AvailObject)
			}
		}

		override fun write(obj: AvailObject, serializer: Serializer)
		{
			val tupleSize = obj.tupleSize()
			writeCompressedPositiveInt(tupleSize, serializer)
			for (element in obj)
			{
				writeCompressedPositiveInt(
					serializer.indexOfExistingObject(element),
					serializer)
			}
		}

		override fun read(deserializer: AbstractDeserializer): AvailObject
		{
			val tupleSize = readCompressedPositiveInt(deserializer)
			val newTuple = generateObjectTupleFrom(tupleSize) {
				deserializer.objectFromIndex(
					readCompressedPositiveInt(deserializer))
			}
			newTuple.makeImmutable()
			return newTuple
		}

		override fun describe(describer: DeserializerDescriber)
		{
			val tupleSize = readCompressedPositiveInt(describer)
			describer.append("<")
			for (i in 1..tupleSize)
			{
				if (i > 1)
				{
					describer.append(", ")
				}
				val objectIndex = readCompressedPositiveInt(describer)
				describer.append("#")
				describer.append(objectIndex.toString())
			}
			describer.append(">")
		}
	},

	/**
	 * This is a [tuple][TupleDescriptor] of characters whose
	 * [code&#32;points][codePoint] are in the range 0..255. Write a
	 * compressed size and the sequence of raw bytes.
	 */
	BYTE_CHARACTER_TUPLE
	{
		override fun write(obj: AvailObject, serializer: Serializer)
		{
			val tupleSize = obj.tupleSize()
			writeCompressedPositiveInt(tupleSize, serializer)
			for (i in 1..tupleSize)
			{
				serializer.writeByte(obj.tupleCodePointAt(i))
			}
		}

		override fun read(deserializer: AbstractDeserializer): AvailObject
		{
			val tupleSize = readCompressedPositiveInt(deserializer)
			return generateByteString(tupleSize) { deserializer.readByte() }
		}
	},

	/**
	 * This is a [tuple][TupleDescriptor] of Unicode characters with code points
	 * in the range 0..65535.  Write the size of the tuple (not the number of
	 * bytes), then a sequence of compressed integers, one per character.
	 *
	 * This operand is limited to 16-bit code points to allow easy use of a
	 * two-byte string during deserialization.  Strings with code points outside
	 * this range use [COMPRESSED_ARBITRARY_CHARACTER_TUPLE] instead.
	 */
	COMPRESSED_SHORT_CHARACTER_TUPLE
	{
		override fun write(obj: AvailObject, serializer: Serializer)
		{
			val tupleSize = obj.tupleSize()
			writeCompressedPositiveInt(tupleSize, serializer)
			for (i in 1..tupleSize)
			{
				writeCompressedPositiveInt(
					obj.tupleCodePointAt(i), serializer)
			}
		}

		override fun read(deserializer: AbstractDeserializer): AvailObject
		{
			val tupleSize = readCompressedPositiveInt(deserializer)
			return generateTwoByteString(tupleSize) {
				val codePoint = readCompressedPositiveInt(deserializer)
				assert(codePoint and 0xFFFF == codePoint)
				codePoint
			}
		}
	},

	/**
	 * This is a [tuple][TupleDescriptor] of Unicode characters with arbitrary
	 * code points.  Write the size of the tuple (not the number of bytes), then
	 * a sequence of compressed integers, one per character.
	 *
	 * @see .COMPRESSED_SHORT_CHARACTER_TUPLE
	 */
	COMPRESSED_ARBITRARY_CHARACTER_TUPLE
	{
		override fun write(obj: AvailObject, serializer: Serializer)
		{
			val tupleSize = obj.tupleSize()
			writeCompressedPositiveInt(tupleSize, serializer)
			(1..tupleSize).forEach { i ->
				writeCompressedPositiveInt(obj.tupleCodePointAt(i), serializer)
			}
		}

		override fun read(deserializer: AbstractDeserializer): AvailObject
		{
			val tupleSize = readCompressedPositiveInt(deserializer)
			// Update this when we have efficient 21-bit strings, three
			// characters per 64-bit long.
			return generateObjectTupleFrom(tupleSize) {
				fromCodePoint(readCompressedPositiveInt(deserializer))
			}
		}
	},

	/**
	 * This is a [tuple][TupleDescriptor] of integers in the range [0..2^31-1],
	 * written as a compressed size and a sequence of compressed ints.
	 */
	COMPRESSED_INT_TUPLE
	{
		override fun write(obj: AvailObject, serializer: Serializer)
		{
			val tupleSize = obj.tupleSize()
			writeCompressedPositiveInt(tupleSize, serializer)
			for (element in obj)
			{
				writeCompressedPositiveInt(element.extractInt(), serializer)
			}
		}

		override fun read(deserializer: AbstractDeserializer): AvailObject
		{
			// Reconstruct into whatever tuple representation is most compact.
			val tupleSize = readCompressedPositiveInt(deserializer)
			val list = ArrayList<Int>(tupleSize)
			for (i in 0 until tupleSize)
			{
				list.add(readCompressedPositiveInt(deserializer))
			}
			return tupleFromIntegerList(list).makeImmutable()
		}
	},

	/**
	 * This is a [tuple][TupleDescriptor] of integers in the range 0..255.
	 * Write a compressed size and the sequence of raw bytes.
	 */
	UNCOMPRESSED_BYTE_TUPLE
	{
		override fun write(obj: AvailObject, serializer: Serializer)
		{
			val tupleSize = obj.tupleSize()
			writeCompressedPositiveInt(tupleSize, serializer)
			for (i in 1..tupleSize)
			{
				serializer.writeByte(obj.tupleIntAt(i))
			}
		}

		override fun read(deserializer: AbstractDeserializer): AvailObject
		{
			val tupleSize = readCompressedPositiveInt(deserializer)
			return generateByteTupleFrom(tupleSize) { deserializer.readByte() }
		}
	},

	/**
	 * This is a [tuple][TupleDescriptor] of integers in the range 0..15.  Write
	 * a compressed size and the sequence of big endian bytes containing two
	 * consecutive nybbles at a time.  The last nybble is considered to be
	 * followed by a zero nybble.
	 */
	UNCOMPRESSED_NYBBLE_TUPLE
	{
		override fun write(obj: AvailObject, serializer: Serializer)
		{
			val tupleSize = obj.tupleSize()
			writeCompressedPositiveInt(tupleSize, serializer)
			var i = 1
			while (i < tupleSize)
			{
				val first = obj.tupleIntAt(i)
				val second = obj.tupleIntAt(i + 1)
				val pair = (first shl 4) + second
				serializer.writeByte(pair)
				i += 2
			}
			if (tupleSize and 1 == 1)
			{
				serializer.writeByte(obj.tupleIntAt(tupleSize) shl 4)
			}
		}

		override fun read(deserializer: AbstractDeserializer): AvailObject
		{
			val tupleSize = readCompressedPositiveInt(deserializer)
			if (tupleSize == 0)
			{
				// Reasonably common case.
				return emptyTuple()
			}
			var twoNybbles = 0
			return generateNybbleTupleFrom(tupleSize) { index ->
				if (index and 1 != 0)
				{
					twoNybbles = deserializer.readByte()
					return@generateNybbleTupleFrom twoNybbles shr 4 and 0xF
				}
				twoNybbles and 0xF
			}
		}
	},

	/**
	 * This is a [map][MapDescriptor] whose keys and values are arbitrary
	 * objects.  Write a compressed map size and a sequence of alternating keys
	 * and associated values.
	 */
	GENERAL_MAP
	{
		override fun trace(obj: AvailObject, serializer: Serializer)
		{
			for (entry in obj.mapIterable())
			{
				serializer.traceOne(entry.key())
				serializer.traceOne(entry.value())
			}
		}

		override fun write(obj: AvailObject, serializer: Serializer)
		{
			writeCompressedPositiveInt(obj.mapSize(), serializer)
			for (entry in obj.mapIterable())
			{
				writeCompressedPositiveInt(
					serializer.indexOfExistingObject(entry.key()),
					serializer)
				writeCompressedPositiveInt(
					serializer.indexOfExistingObject(entry.value()),
					serializer)
			}
		}

		override fun read(deserializer: AbstractDeserializer): AvailObject
		{
			val mapSize = readCompressedPositiveInt(deserializer)
			var map = emptyMap()
			for (index in 1..mapSize)
			{
				map = map.mapAtPuttingCanDestroy(
					deserializer.objectFromIndex(
						readCompressedPositiveInt(deserializer)),
					deserializer.objectFromIndex(
						readCompressedPositiveInt(deserializer)),
					true)
			}
			return map as AvailObject
		}

		override fun describe(describer: DeserializerDescriber)
		{
			val mapSize = readCompressedPositiveInt(describer)
			describer.append("{")
			for (i in 1..mapSize)
			{
				if (i > 1)
				{
					describer.append(", ")
				}
				val keyIndex = readCompressedPositiveInt(describer)
				val valueIndex = readCompressedPositiveInt(describer)
				describer.append("#")
				describer.append(keyIndex.toString())
				describer.append(" → #")
				describer.append(valueIndex.toString())
			}
			describer.append("}")
		}
	};

	/**
	 * Visit an operand of some object prior to beginning to write a graph of
	 * objects to the [Serializer].
	 *
	 * @param obj
	 *   The [AvailObject] to trace.
	 * @param serializer
	 *   The [Serializer] with which to trace the object.
	 */
	internal open fun trace(obj: AvailObject, serializer: Serializer)
	{
		// do nothing
	}

	/**
	 * Write an operand with a suitable encoding to the [OutputStream].
	 *
	 * @param obj
	 *   The [AvailObject] to serialize.
	 * @param serializer
	 *   The [Serializer] on which to encode the object.
	 */
	internal abstract fun write(obj: AvailObject, serializer: Serializer)

	/**
	 * Read an operand of the appropriate kind from the [AbstractDeserializer].
	 *
	 * @param deserializer
	 *   The `AbstractDeserializer` from which to read.
	 * @return
	 *   An AvailObject suitable for this kind of operand.
	 */
	internal abstract fun read(deserializer: AbstractDeserializer): AvailObject

	/**
	 * Describe an operand of the appropriate kind from the
	 * [DeserializerDescriber].
	 *
	 * Specific enumeration values might override this to avoid constructing the
	 * actual complex objects.
	 *
	 * @param describer
	 *   The [DeserializerDescriber] on which to describe this.
	 */
	internal open fun describe(describer: DeserializerDescriber)
	{
		val value = read(describer)
		describer.append(increaseIndentation(value.toString(), 1))
	}

	/**
	 * Construct a [SerializerOperand] with an encoding based on the receiver
	 * and with the specified role with regard to the containing
	 * [operation][SerializerOperation].
	 *
	 * @param roleName
	 *   The purpose of this operand within its operation.
	 * @return
	 *   The new operand.
	 */
	fun named(roleName: String): SerializerOperand
	{
		return SerializerOperand(this, roleName)
	}

	companion object
	{
		/**
		 * Write an unsigned integer in the range 0..2<sup>31</sup>-1.  Use a form
		 * that uses less than 32 bits for small values.
		 *
		 * @param index The integer to write.
		 * @param serializer Where to write it.
		 */
		internal fun writeCompressedPositiveInt(
			index: Int,
			serializer: Serializer)
		{
			assert(index >= 0) { "Expected a positive int to write" }
			when
			{
				index < 128 ->
					// 0..127 are written as a single byte.
					serializer.writeByte(index)
				index < 64 shl 8 ->
				{
					// 128..16383 are written with six bits of the first byte
					// used for the high byte (first byte is 128..191).  The
					// second byte is the low byte.
					serializer.writeByte((index shr 8) + 128)
					serializer.writeByte(index and 0xFF)
				}
				index < 63 shl 16 ->
				{
					// The first byte is 192..254, or almost six bits (after
					// dealing with the 192 bias).  The middle and low bytes
					// follow.  That allows up to 0x003EFFFF to be written in
					// only three bytes. The middle and low bytes follow.
					serializer.writeByte((index shr 16) + 192)
					serializer.writeShort(index and 0xFFFF)
				}
				else ->
				{
					// All the way up to 2^31-1.
					serializer.writeByte(255)
					serializer.writeInt(index)
				}
			}
		}

		/**
		 * Read a compressed positive int in the range 0..2<sup>31</sup>-1.  The
		 * encoding supports:
		 *
		 *  * 0..127 in one byte
		 *  * 128..16383 in two bytes
		 *  * 16384..0x003effff in three bytes
		 *  * 0x003f0000..0x7fffffff in five bytes.
		 *
		 * @param deserializer
		 *   Where to read the integer from.
		 * @return
		 *   The integer that was read.
		 */
		fun readCompressedPositiveInt(deserializer: AbstractDeserializer): Int
		{
			val firstByte = deserializer.readByte()
			if (firstByte < 128)
			{
				// One byte, 0..127
				return firstByte
			}
			if (firstByte < 192)
			{
				// Two bytes, 128..16383
				return (firstByte - 128 shl 8) + deserializer.readByte()
			}
			return if (firstByte < 255)
			{
				// Three bytes, 16384..0x3EFFFF
				(firstByte - 192 shl 16) + deserializer.readShort()
			}
			// Five bytes, 0x3F0000..0x7FFFFFFF
			else deserializer.readInt()
		}
	}
}
