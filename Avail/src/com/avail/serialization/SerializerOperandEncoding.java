/**
 * SerializerOperandEncoding.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

package com.avail.serialization;

import com.avail.descriptor.*;
import com.avail.utility.*;
import java.io.*;

/**
 * A {@code SerializerOperandEncoding} is an encoding algorithm for part of a
 * {@link SerializerOperation}.  It assists in the disassembly and reassembly
 * of the various kinds of objects encountered in Avail.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
enum SerializerOperandEncoding
{
	/**
	 * This is an {@link AvailObject} that's always an {@linkplain
	 * IntegerDescriptor integer} in the range [0..255].  This is a particularly
	 * concise representation, useful for bit fields, enumerations, and other
	 * limited values.
	 */
	BYTE
	{
		@Override
		void write (
			final AvailObject object,
			final Serializer serializer)
		{
			serializer.writeByte(object.extractUnsignedByte());
		}

		@Override
		A_BasicObject read (
			final Deserializer deserializer)
		{
			return IntegerDescriptor.fromInt(deserializer.readByte());
		}
	},

	/**
	 * This is an {@link AvailObject} that's always an {@linkplain
	 * IntegerDescriptor integer} in the range [0..65535].  Some system limits
	 * fall within this range (e.g., number of arguments to a function),
	 * allowing this compact representation to be used.
	 *
	 * <p>
	 * This operand uses the compressed representation below, which may not be
	 * effective for some uses, in which case {@link #UNCOMPRESSED_SHORT} may be
	 * more appropriate.
	 *
	 * <ul>
	 * <li>0x0000..0x007F take one byte.</li>
	 * <li>0x0080..0x7EFF take two bytes.</li>
	 * <li>0x7F00..0xFFFF take three bytes.</li>
	 * </ul>
	 */
	COMPRESSED_SHORT
	{
		@Override
		void write (
			final AvailObject object,
			final Serializer serializer)
		{
			final int shortValue = object.extractInt();
			assert (shortValue & 0xFFFF) == shortValue;
			if (shortValue < 128)
			{
				serializer.writeByte(shortValue);
			}
			else if (shortValue < 0x7EFF)
			{
				serializer.writeByte((shortValue >> 8) + 128);
				serializer.writeByte(shortValue & 0xFF);
			}
			else
			{
				serializer.writeByte(255);
				serializer.writeShort(shortValue);
			}
		}

		@Override
		A_BasicObject read (
			final Deserializer deserializer)
		{
			final int firstByte = deserializer.readByte();
			final int intValue;
			if (firstByte < 128)
			{
				intValue = firstByte;
			}
			else if (firstByte < 255)
			{
				intValue = ((firstByte - 128) << 8)
					+ deserializer.readByte();
			}
			else
			{
				intValue = deserializer.readShort();
			}
			return IntegerDescriptor.fromInt(intValue);
		}
	},

	/**
	 * This is an {@link AvailObject} that's always an {@linkplain
	 * IntegerDescriptor integer} in the range [0..65535].  This is a
	 * particularly concise representation, useful for bit fields, enumerations,
	 * and other limited values.
	 *
	 * <p>
	 * In this case don't attempt to compress it smaller than two bytes, since
	 * we have knowledge that it probably won't be effective.
	 * </p>
	 */
	UNCOMPRESSED_SHORT
	{
		@Override
		void write (
			final AvailObject object,
			final Serializer serializer)
		{
			serializer.writeShort(object.extractUnsignedShort());
		}

		@Override
		A_BasicObject read (
			final Deserializer deserializer)
		{
			return IntegerDescriptor.fromInt(deserializer.readShort());
		}
	},


	/**
	 * This is an {@link AvailObject} that's always an {@linkplain
	 * IntegerDescriptor integer} in the same range as Java's signed int,
	 * -2<sup>31</sup> through 2<sup>31</sup>-1.  Some system limits fall within
	 * this range, allowing this compact representation to be used.
	 */
	SIGNED_INT
	{
		@Override
		void write (
			final AvailObject object,
			final Serializer serializer)
		{
			final int intValue = object.extractInt();
			serializer.writeInt(intValue);
		}

		@Override
		A_BasicObject read (
			final Deserializer deserializer)
		{
			return IntegerDescriptor.fromInt(deserializer.readInt());
		}
	},

	/**
	 * This is an {@link AvailObject} that's always a positive {@linkplain
	 * IntegerDescriptor integer} in the range 0 through 2<sup>32</sup>-1.
	 * Some system limits fall within this range, allowing this compact
	 * representation to be used.
	 */
	UNSIGNED_INT
	{
		@Override
		void write (
			final AvailObject object,
			final Serializer serializer)
		{
			final long longValue = object.extractLong();
			assert (longValue & 0xFFFFFFFFL) == longValue;
			serializer.writeInt((int)longValue);
		}

		@Override
		A_BasicObject read (
			final Deserializer deserializer)
		{
			final int intValue = deserializer.readInt();
			final long longValue = intValue & 0xFFFFFFFFL;
			return IntegerDescriptor.fromLong(longValue);
		}
	},


	/**
	 * This is an {@link AvailObject} that occurred previously in the sequence
	 * of objects.  A variable-length integer is encoded in the stream to
	 * indicate the object's absolute subscript within the serialization stream.
	 */
	OBJECT_REFERENCE
	{
		@Override
		void trace (
			final AvailObject object,
			final Serializer serializer)
		{
			// Visit the object.
			serializer.traceOne(object);
		}

		@Override
		void write (
			final AvailObject object,
			final Serializer serializer)
		{
			final SerializerInstruction instruction =
				serializer.instructionForObject(object);
			final int index = instruction.index();
			assert index >= 0
				: "Attempted to write reference to untraced object.";
			writeCompressedPositiveInt(index, serializer);
		}

		@Override
		A_BasicObject read (
			final Deserializer deserializer)
		{
			final int index = readCompressedPositiveInt(deserializer);
			return deserializer.objectFromIndex(index);
		}
	},

	/**
	 * This is an {@link AvailObject} that's an {@linkplain IntegerDescriptor
	 * integer} of any size.  It writes a compressed int for the number of int
	 * slots, then the big-endian sequence of (also internally big-endian)
	 * uncompressed {@code int}s.  Only the first int in that sequence is to be
	 * considered to have a sign.
	 */
	BIG_INTEGER_DATA
	{
		@Override
		void write (
			final AvailObject object,
			final Serializer serializer)
		{
			final int slotsCount = object.integerSlotsCount();
			writeCompressedPositiveInt(slotsCount, serializer);
			for (int i = slotsCount; i >= 1; i--)
			{
				serializer.writeInt(object.rawSignedIntegerAt(i));
			}
		}

		@Override
		final A_BasicObject read (final Deserializer deserializer)
		{
			final int slotsCount = readCompressedPositiveInt(deserializer);
			final A_Number newInteger =
				IntegerDescriptor.createUninitialized(slotsCount);
			for (int i = slotsCount; i >= 1; i--)
			{
				newInteger.rawSignedIntegerAtPut(i, deserializer.readInt());
			}
			newInteger.makeImmutable();
			return newInteger;
		}
	},

	/**
	 * This is a {@linkplain TupleDescriptor tuple} of arbitrary objects,
	 * written as a compressed size and a sequence of compressed object
	 * references.
	 */
	TUPLE_OF_OBJECTS
	{
		@Override
		void trace (
			final AvailObject object,
			final Serializer serializer)
		{
			// Visit the *elements* of the tuple.
			final int tupleSize = object.tupleSize();
			for (int i = 1; i <= tupleSize; i++)
			{
				serializer.traceOne(object.tupleAt(i));
			}
		}

		@Override
		void write (
			final AvailObject object,
			final Serializer serializer)
		{
			final int tupleSize = object.tupleSize();
			writeCompressedPositiveInt(tupleSize, serializer);
			for (int i = 1; i <= tupleSize; i++)
			{
				writeCompressedPositiveInt(
					serializer.indexOfExistingObject(object.tupleAt(i)),
					serializer);
			}
		}

		@Override
		final A_BasicObject read (final Deserializer deserializer)
		{
			final int tupleSize = readCompressedPositiveInt(deserializer);
			final AvailObject newTuple =
				ObjectTupleDescriptor.createUninitialized(tupleSize);
			for (int i = 1; i <= tupleSize; i++)
			{
				final int objectIndex = readCompressedPositiveInt(deserializer);
				newTuple.tupleAtPut(
					i,
					deserializer.objectFromIndex(objectIndex));
			}
			newTuple.makeImmutable();
			return newTuple;
		}
	},

	/**
	 * This is a {@linkplain TupleDescriptor tuple} of characters whose
	 * {@link AvailObject#codePoint() code points} are in the range 0..255.
	 * Write a compressed size and the sequence of raw bytes.
	 */
	BYTE_CHARACTER_TUPLE
	{
		@Override
		void write (
			final AvailObject object,
			final Serializer serializer)
		{
			final int tupleSize = object.tupleSize();
			writeCompressedPositiveInt(tupleSize, serializer);
			for (int i = 1; i <= tupleSize; i++)
			{
				serializer.writeByte(object.tupleAt(i).codePoint());
			}
		}

		@Override
		final A_BasicObject read (final Deserializer deserializer)
		{
			final int tupleSize = readCompressedPositiveInt(deserializer);
			return StringDescriptor.mutableByteStringFromGenerator(
				tupleSize,
				new Generator<Integer>()
				{
					@Override
					public Integer value ()
					{
						return deserializer.readByte();
					}
				});
		}
	},

	/**
	 * This is a {@linkplain TupleDescriptor tuple} of Unicode characters with
	 * code points in the range 0..65535.  Write the size of the tuple (not the
	 * number of bytes), then a sequence of compressed integers, one per
	 * character.
	 *
	 * <p>
	 * This operand is limited to 16-bit code points to allow easy use of a
	 * two-byte string during deserialization.  Strings with code points outside
	 * this range use {@link #COMPRESSED_ARBITRARY_CHARACTER_TUPLE} instead.
	 * </p>
	 */
	COMPRESSED_SHORT_CHARACTER_TUPLE
	{
		@Override
		void write (
			final AvailObject object,
			final Serializer serializer)
		{
			final int tupleSize = object.tupleSize();
			writeCompressedPositiveInt(tupleSize, serializer);
			for (int i = 1; i <= tupleSize; i++)
			{
				writeCompressedPositiveInt(
					object.tupleAt(i).codePoint(),
					serializer);
			}
		}

		@Override
		final A_BasicObject read (final Deserializer deserializer)
		{
			final int tupleSize = readCompressedPositiveInt(deserializer);
			return StringDescriptor.mutableTwoByteStringFromGenerator(
				tupleSize,
				new Generator<Integer>()
				{
					@Override
					public Integer value ()
					{
						return readCompressedPositiveInt(deserializer);
					}
				});
		}
	},

	/**
	 * This is a {@linkplain TupleDescriptor tuple} of Unicode characters with
	 * arbitrary code points.  Write the size of the tuple (not the number of
	 * bytes), then a sequence of compressed integers, one per character.
	 *
	 * @see #COMPRESSED_SHORT_CHARACTER_TUPLE
	 */
	COMPRESSED_ARBITRARY_CHARACTER_TUPLE
	{
		@Override
		void write (
			final AvailObject object,
			final Serializer serializer)
		{
			final int tupleSize = object.tupleSize();
			writeCompressedPositiveInt(tupleSize, serializer);
			for (int i = 1; i <= tupleSize; i++)
			{
				writeCompressedPositiveInt(
					object.tupleAt(i).codePoint(),
					serializer);
			}
		}

		@Override
		final A_BasicObject read (final Deserializer deserializer)
		{
			final int tupleSize = readCompressedPositiveInt(deserializer);
			final AvailObject[] elementsArray = new AvailObject[tupleSize];
			for (int i = 0; i < tupleSize; i++)
			{
				final int codePoint = readCompressedPositiveInt(deserializer);
				elementsArray[i] = CharacterDescriptor.fromCodePoint(codePoint);
			}
			return TupleDescriptor.from(elementsArray);
		}
	},

	/**
	 * This is a {@linkplain TupleDescriptor tuple} of integers in the range
	 * 0..255.  Write a compressed size and the sequence of raw bytes.
	 */
	UNCOMPRESSED_BYTE_TUPLE
	{
		@Override
		void write (
			final AvailObject object,
			final Serializer serializer)
		{
			final int tupleSize = object.tupleSize();
			writeCompressedPositiveInt(tupleSize, serializer);
			for (int i = 1; i <= tupleSize; i++)
			{
				serializer.writeByte(object.tupleIntAt(i));
			}
		}

		@Override
		final A_BasicObject read (final Deserializer deserializer)
		{
			final int tupleSize = readCompressedPositiveInt(deserializer);
			final AvailObject tuple =
				ByteTupleDescriptor.mutableObjectOfSize(tupleSize);
			for (int i = 1; i <= tupleSize; i++)
			{
				tuple.rawByteAtPut(i, (short)deserializer.readByte());
			}
			return tuple;
		}
	},

	/**
	 * This is a {@linkplain TupleDescriptor tuple} of integers in the range
	 * 0..15.  Write a compressed size and the sequence of big endian bytes
	 * containing two consecutive nybbles at a time.  The last nybble is
	 * considered to be followed by a zero nybble.
	 */
	UNCOMPRESSED_NYBBLE_TUPLE
	{
		@Override
		void write (
			final AvailObject object,
			final Serializer serializer)
		{
			final int tupleSize = object.tupleSize();
			writeCompressedPositiveInt(tupleSize, serializer);
			for (int i = 1; i < tupleSize; i+=2)
			{
				final int first = object.tupleIntAt(i);
				final int second = object.tupleIntAt(i + 1);
				final int pair = (first << 4) + second;
				serializer.writeByte(pair);
			}
			if ((tupleSize & 1) == 1)
			{
				serializer.writeByte(object.tupleIntAt(tupleSize) << 4);
			}
		}

		@Override
		final A_BasicObject read (final Deserializer deserializer)
		{
			final int tupleSize = readCompressedPositiveInt(deserializer);
			final AvailObject tuple =
				NybbleTupleDescriptor.mutableObjectOfSize(tupleSize);
			for (int i = 1; i < tupleSize; i+=2)
			{
				final int pair = deserializer.readByte();
				tuple.rawNybbleAtPut(i, (byte)(pair >> 4));
				tuple.rawNybbleAtPut(i + 1, (byte)(pair & 0xF));
			}
			if ((tupleSize & 1) == 1)
			{
				final int lastByte = deserializer.readByte();
				tuple.rawNybbleAtPut(tupleSize, (byte)(lastByte >> 4));
			}
			return tuple;
		}
	},

	/**
	 * This is a {@linkplain MapDescriptor map} whose keys and values are
	 * arbitrary objects.  Write a compressed map size and a sequence of
	 * alternating keys and associated values.
	 */
	GENERAL_MAP
	{
		@Override
		void trace (final AvailObject object, final Serializer serializer)
		{
			for (final MapDescriptor.Entry entry : object.mapIterable())
			{
				serializer.traceOne(entry.key());
				serializer.traceOne(entry.value());
			}
		}

		@Override
		void write (final AvailObject object, final Serializer serializer)
		{
			writeCompressedPositiveInt(object.mapSize(), serializer);
			for (final MapDescriptor.Entry entry : object.mapIterable())
			{
				writeCompressedPositiveInt(
					serializer.indexOfExistingObject(entry.key()),
					serializer);
				writeCompressedPositiveInt(
					serializer.indexOfExistingObject(entry.value()),
					serializer);
			}
		}

		@Override
		A_BasicObject read (final Deserializer deserializer)
		{
			final int mapSize = readCompressedPositiveInt(deserializer);
			A_Map map = MapDescriptor.empty();
			for (int index = 1; index <= mapSize; index++)
			{
				map = map.mapAtPuttingCanDestroy(
					deserializer.objectFromIndex(
						readCompressedPositiveInt(deserializer)),
					deserializer.objectFromIndex(
						readCompressedPositiveInt(deserializer)),
					true);
			}
			return map;
		}
	}
	;

	/**
	 * Visit an operand of some object prior to beginning to write a graph of
	 * objects to the {@link Serializer}.
	 *
	 * @param object
	 *            The {@link AvailObject} to trace.
	 * @param serializer
	 *            The {@link Serializer} with which to trace the object.
	 */
	void trace (
		final AvailObject object,
		final Serializer serializer)
	{
		// do nothing
	}

	/**
	 * Write an operand with a suitable encoding to the {@link OutputStream}.
	 *
	 * @param object
	 *            The {@link AvailObject} to serialize.
	 * @param serializer
	 *            The {@link Serializer} on which to encode the object.
	 */
	abstract void write (
		final AvailObject object,
		final Serializer serializer);

	/**
	 * Read an operand of the appropriate kind from the {@link
	 * Deserializer}.
	 *
	 * @param deserializer  The {@code Deserializer} from which to read.
	 * @return An AvailObject suitable for this kind of operand.
	 */
	abstract A_BasicObject read (
		final Deserializer deserializer);

	/**
	 * Write an unsigned integer in the range 0..2<sup>31</sup>-1.  Use a form
	 * that uses less than 32 bits for small values.
	 *
	 * @param index
	 * @param serializer
	 */
	void writeCompressedPositiveInt (
		final int index,
		final Serializer serializer)
	{
		assert index >= 0 : "Expected a positive int to write";
		if (index < 128)
		{
			// 0..127 are written as a single byte.
			serializer.writeByte(index);
		}
		else if (index < (64 << 8))
		{
			// 128..16383 are written with six bits of the first byte used
			// for the high byte (first byte is 128..191).  The second byte
			// is the low byte.
			serializer.writeByte((index >> 8) + 128);
			serializer.writeByte(index & 0xFF);
		}
		else if (index < (63 << 16))
		{
			// The first byte is 192..254, or almost six bits (after dealing
			// with the 192 bias).  The middle and low bytes follow.  That
			// allows up to 0x003EFFFF to be  written in only three bytes.
			// The middle and low bytes follow.
			serializer.writeByte((index >> 16) + 192);
			serializer.writeShort(index & 0xFFFF);
		}
		else
		{
			// All the way up to 2^31-1.
			serializer.writeByte(255);
			serializer.writeInt(index);
		}

	}

	/**
	 * Read a compressed positive int in the range 0..2<sup>31</sup>-1.  The
	 * encoding supports:
	 * <ul>
	 * <li>0..127 in one byte</li>
	 * <li>128..16383 in two bytes</li>
	 * <li>16384..0x00feffff in three bytes</li>
	 * <li>0x00ff0000..0x7fffffff in five bytes</li>.
	 * </ul>
	 *
	 * @param deserializer
	 * @return
	 */
	int readCompressedPositiveInt (
		final Deserializer deserializer)
	{
		final int firstByte = deserializer.readByte();
		if (firstByte < 128)
		{
			// One byte, 0..127
			return firstByte;
		}
		if (firstByte < 192)
		{
			// Two bytes, 128..16383
			return ((firstByte - 128) << 8) + deserializer.readByte();
		}
		if (firstByte < 255)
		{
			// Three bytes, 16384..0x3EFFFF
			return ((firstByte - 192) << 16) + deserializer.readShort();
		}
		// Five bytes, 0x3F0000..0x7FFFFFFF
		return deserializer.readInt();
	}

	/**
	 * Construct a {@link SerializerOperand} with an encoding based on the
	 * receiver and with the specified role with regard to the containing
	 * {@linkplain SerializerOperation operation}.
	 *
	 * @param roleName The purpose of this operand within its operation.
	 * @return The new operand.
	 */
	SerializerOperand as (
		final String roleName)
	{
		return new SerializerOperand(this, roleName);
	}
}
