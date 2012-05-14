/**
 * SerializerOperation.java
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

import static com.avail.serialization.SerializerOperandEncoding.*;
import com.avail.AvailRuntime;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.descriptor.TypeDescriptor.Types;

/**
 * A {@code SerializerOpcode} describes how to disassemble and assemble the
 * various kinds of objects encountered in Avail.
 *
 * <p>
 * The ordinal is passed in the constructor as a cross-check, to increase the
 * difficulty of (accidentally) changing the serialized representation without.
 * </p>
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public enum SerializerOperation
{
	/**
	 * The Avail integer 0.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	ZERO_INTEGER (0)
	{
		@Override
		AvailObject[] decompose (
			final @NotNull AvailObject object)
		{
			return array();
		}

		@Override
		AvailObject compose (
			final @NotNull AvailObject[] subobjects,
			final @NotNull Deserializer deserializer)
		{
			return IntegerDescriptor.zero();
		}
	},

	/**
	 * The Avail integer 1.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	ONE_INTEGER (1)
	{
		@Override
		AvailObject[] decompose (
			final @NotNull AvailObject object)
		{
			return array();
		}

		@Override
		AvailObject compose (
			final @NotNull AvailObject[] subobjects,
			final @NotNull Deserializer deserializer)
		{
			return IntegerDescriptor.one();
		}
	},

	/**
	 * The Avail integer 2.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	TWO_INTEGER (2)
	{
		@Override
		AvailObject[] decompose (
			final @NotNull AvailObject object)
		{
			return array();
		}

		@Override
		AvailObject compose (
			final @NotNull AvailObject[] subobjects,
			final @NotNull Deserializer deserializer)
		{
			return IntegerDescriptor.fromInt(2);
		}
	},

	/**
	 * The Avail integer 3.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	THREE_INTEGER (3)
	{
		@Override
		AvailObject[] decompose (
			final @NotNull AvailObject object)
		{
			return array();
		}

		@Override
		AvailObject compose (
			final @NotNull AvailObject[] subobjects,
			final @NotNull Deserializer deserializer)
		{
			return IntegerDescriptor.fromInt(3);
		}
	},

	/**
	 * The Avail integer 4.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	FOUR_INTEGER (4)
	{
		@Override
		AvailObject[] decompose (
			final @NotNull AvailObject object)
		{
			return array();
		}

		@Override
		AvailObject compose (
			final @NotNull AvailObject[] subobjects,
			final @NotNull Deserializer deserializer)
		{
			return IntegerDescriptor.fromInt(4);
		}
	},

	/**
	 * The Avail integer 5.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	FIVE_INTEGER (5)
	{
		@Override
		AvailObject[] decompose (
			final @NotNull AvailObject object)
		{
			return array();
		}

		@Override
		AvailObject compose (
			final @NotNull AvailObject[] subobjects,
			final @NotNull Deserializer deserializer)
		{
			return IntegerDescriptor.fromInt(5);
		}
	},

	/**
	 * The Avail integer 6.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	SIX_INTEGER (6)
	{
		@Override
		AvailObject[] decompose (
			final @NotNull AvailObject object)
		{
			return array();
		}

		@Override
		AvailObject compose (
			final @NotNull AvailObject[] subobjects,
			final @NotNull Deserializer deserializer)
		{
			return IntegerDescriptor.fromInt(6);
		}
	},

	/**
	 * The Avail integer 7.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	SEVEN_INTEGER (7)
	{
		@Override
		AvailObject[] decompose (
			final @NotNull AvailObject object)
		{
			return array();
		}

		@Override
		AvailObject compose (
			final @NotNull AvailObject[] subobjects,
			final @NotNull Deserializer deserializer)
		{
			return IntegerDescriptor.fromInt(7);
		}
	},

	/**
	 * The Avail integer 8.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	EIGHT_INTEGER (8)
	{
		@Override
		AvailObject[] decompose (
			final @NotNull AvailObject object)
		{
			return array();
		}

		@Override
		AvailObject compose (
			final @NotNull AvailObject[] subobjects,
			final @NotNull Deserializer deserializer)
		{
			return IntegerDescriptor.fromInt(8);
		}
	},

	/**
	 * The Avail integer 9.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	NINE_INTEGER (9)
	{
		@Override
		AvailObject[] decompose (
			final @NotNull AvailObject object)
		{
			return array();
		}

		@Override
		AvailObject compose (
			final @NotNull AvailObject[] subobjects,
			final @NotNull Deserializer deserializer)
		{
			return IntegerDescriptor.fromInt(9);
		}
	},

	/**
	 * The Avail integer 10.  Note that there are no operands, since the value
	 * is encoded in the choice of instruction itself.
	 */
	TEN_INTEGER (10)
	{
		@Override
		AvailObject[] decompose (
			final @NotNull AvailObject object)
		{
			return array();
		}

		@Override
		AvailObject compose (
			final @NotNull AvailObject[] subobjects,
			final @NotNull Deserializer deserializer)
		{
			return IntegerDescriptor.fromInt(10);
		}
	},

	/**
	 * An Avail integer in the range 11..255.  Note that 0..10 have their own
	 * special cases already which require very little space.
	 */
	BYTE_INTEGER (11, BYTE.as("only byte"))
	{
		@Override
		AvailObject[] decompose (
			final @NotNull AvailObject object)
		{
			return array(object);
		}

		@Override
		AvailObject compose (
			final @NotNull AvailObject[] subobjects,
			final @NotNull Deserializer deserializer)
		{
			return subobjects[0];
		}
	},

	/**
	 * An Avail integer in the range 256..65535.  Note that 0..255 have their
	 * own special cases already which require less space.  Don't try to
	 * compress the short value for this reason.
	 *
	 * <p>
	 * Separated into two {@code BYTE}s instead of one {@code
	 * UNCOMPRESSED_SHORT} <em>just</em> so that the intermediate objects can be
	 * the Avail {@linkplain IntegerDescriptor integers} that efficiently fit in
	 * a byte.
	 * </p>
	 */
	SHORT_INTEGER (12, BYTE.as("high byte"), BYTE.as("low byte"))
	{
		@Override
		AvailObject[] decompose (
			final @NotNull AvailObject object)
		{
			final int shortValue = object.extractUnsignedShort();
			return array(
				IntegerDescriptor.fromInt((shortValue >> 8) & 0xFF),
				IntegerDescriptor.fromInt(shortValue & 0xFF));
		}

		@Override
		AvailObject compose (
			final @NotNull AvailObject[] subobjects,
			final @NotNull Deserializer deserializer)
		{
			final int intValue = (subobjects[0].extractUnsignedByte() << 8)
				+ subobjects[1].extractUnsignedByte();
			return IntegerDescriptor.fromInt(intValue);
		}
	},

	/**
	 * An Avail integer in the range -2<sup>31</sup> through 2<sup>31</sup>-1,
	 * except the range 0..65535 which have their own special cases already.
	 */
	INT_INTEGER (13, SIGNED_INT.as("int's value"))
	{
		@Override
		AvailObject[] decompose (
			final @NotNull AvailObject object)
		{
			return array(object);
		}

		@Override
		AvailObject compose (
			final @NotNull AvailObject[] subobjects,
			final @NotNull Deserializer deserializer)
		{
			return subobjects[0];
		}
	},

	/**
	 * An Avail integer that cannot be represented as an {@code int}.
	 */
	BIG_INTEGER (14, BIG_INTEGER_DATA.as("constituent ints"))
	{
		@Override
		AvailObject[] decompose (
			final @NotNull AvailObject object)
		{
			return array(object);
		}

		@Override
		AvailObject compose (
			final @NotNull AvailObject[] subobjects,
			final @NotNull Deserializer deserializer)
		{
			return subobjects[0];
		}
	},


	/**
	 * Produce the Avail {@linkplain NullDescriptor#nullObject() null object}
	 * during deserialization.
	 */
	NULL_OBJECT (15)
	{
		@Override
		@NotNull AvailObject[] decompose (final @NotNull AvailObject object)
		{
			return array();
		}

		@Override
		AvailObject compose (
			final @NotNull AvailObject[] subobjects,
			final @NotNull Deserializer deserializer)
		{
			return NullDescriptor.nullObject();
		}
	},

	/**
	 * This special opcode causes a previously built object to be produced as an
	 * actual checkpoint output from the {@link Deserializer}.
	 */
	CHECKPOINT (16, OBJECT_REFERENCE.as("object to checkpoint"))
	{
		@Override
		@NotNull AvailObject[] decompose (final @NotNull AvailObject object)
		{
			// Make sure the function actually gets written out.
			return array(object);
		}

		@Override
		AvailObject compose (
			final @NotNull AvailObject[] subobjects,
			final @NotNull Deserializer deserializer)
		{
			final AvailObject subobject = subobjects[0];
			deserializer.recordProducedObject(subobject);
			return subobject;
		}
	},

	/**
	 * One of the special objects that the {@link AvailRuntime} maintains.
	 */
	SPECIAL_OBJECT (17, COMPRESSED_SHORT.as("special object number"))
	{
		@Override
		@NotNull AvailObject[] decompose (
			final @NotNull AvailObject object)
		{
			return array(
				IntegerDescriptor.fromInt(
					Serializer.indexOfSpecialObject(object)));
		}

		@Override
		AvailObject compose (
			final @NotNull AvailObject[] subobjects,
			final @NotNull Deserializer deserializer)
		{
			return deserializer.specialObject(subobjects[0].extractInt());
		}
	},

	/**
	 * A {@linkplain CharacterDescriptor character} whose code point fits in an
	 * unsigned byte (0..255).
	 */
	BYTE_CHARACTER (18, BYTE.as("Latin-1 code point"))
	{
		@Override
		@NotNull AvailObject[] decompose (
			final @NotNull AvailObject object)
		{
			return array(
				IntegerDescriptor.fromInt(object.codePoint()));
		}

		@Override
		AvailObject compose (
			final @NotNull AvailObject[] subobjects,
			final @NotNull Deserializer deserializer)
		{
			return CharacterDescriptor.fromCodePoint(
				subobjects[0].extractInt());
		}
	},

	/**
	 * A {@linkplain CharacterDescriptor character} whose code point requires an
	 * unsigned short (256..65535).
	 */
	SHORT_CHARACTER (19,
		UNCOMPRESSED_SHORT.as("BMP code point"))
	{
		@Override
		@NotNull AvailObject[] decompose (
			final @NotNull AvailObject object)
		{
			return array(
				IntegerDescriptor.fromInt(object.codePoint()));
		}

		@Override
		AvailObject compose (
			final @NotNull AvailObject[] subobjects,
			final @NotNull Deserializer deserializer)
		{
			return CharacterDescriptor.fromCodePoint(
				subobjects[0].extractInt());
		}
	},

	/**
	 * A {@linkplain CharacterDescriptor character} whose code point requires
	 * three bytes to represent (0..16777215, but technically only 0..1114111).
	 */
	LARGE_CHARACTER (20,
		BYTE.as("SMP codepoint high byte"),
		BYTE.as("SMP codepoint middle byte"),
		BYTE.as("SMP codepoint low byte"))
	{
		@Override
		@NotNull AvailObject[] decompose (
			final @NotNull AvailObject object)
		{
			final int codePoint = object.codePoint();
			return array(
				IntegerDescriptor.fromInt((codePoint >> 16) & 0xFF),
				IntegerDescriptor.fromInt((codePoint >> 8) & 0xFF),
				IntegerDescriptor.fromInt(codePoint & 0xFF));
		}

		@Override
		AvailObject compose (
			final @NotNull AvailObject[] subobjects,
			final @NotNull Deserializer deserializer)
		{
			return CharacterDescriptor.fromCodePoint(
				(subobjects[0].extractUnsignedByte() << 16)
				+ (subobjects[1].extractUnsignedByte() << 8)
				+ subobjects[2].extractUnsignedByte());
		}
	},

	/**
	 * A {@linkplain CharacterDescriptor character} whose code point requires
	 * three bytes to represent (0..16777215, but technically only 0..1114111).
	 */
	FLOAT (21, SIGNED_INT.as("raw bits"))
	{
		@Override
		@NotNull AvailObject[] decompose (
			final @NotNull AvailObject object)
		{
			final float floatValue = object.extractFloat();
			final int floatBits = Float.floatToRawIntBits(floatValue);
			return array(
				IntegerDescriptor.fromInt(floatBits));
		}

		@Override
		AvailObject compose (
			final @NotNull AvailObject[] subobjects,
			final @NotNull Deserializer deserializer)
		{
			final int floatBits = subobjects[0].extractInt();
			final float floatValue = Float.intBitsToFloat(floatBits);
			return FloatDescriptor.fromFloat(floatValue);
		}
	},

	/**
	 * A {@linkplain DoubleDescriptor double}.  Convert the raw bits to a long
	 * and write it in big endian.
	 */
	DOUBLE (22,
		SIGNED_INT.as("upper raw bits"),
		UNSIGNED_INT.as("lower raw bits"))
	{
		@Override
		@NotNull AvailObject[] decompose (
			final @NotNull AvailObject object)
		{
			final double doubleValue = object.extractDouble();
			final long doubleBits = Double.doubleToRawLongBits(doubleValue);
			return array(
				IntegerDescriptor.fromInt((int)(doubleBits >> 32)),
				IntegerDescriptor.fromInt((int)doubleBits));
		}

		@Override
		AvailObject compose (
			final @NotNull AvailObject[] subobjects,
			final @NotNull Deserializer deserializer)
		{
			final int highBits = subobjects[0].extractInt();
			final int lowBits = subobjects[1].extractInt();
			final long doubleBits =
				(((long)highBits) << 32)
				+ (lowBits & 0xFFFFFFFFL);
			final double doubleValue = Double.longBitsToDouble(doubleBits);
			return DoubleDescriptor.fromDouble(doubleValue);
		}
	},

	/**
	 * A {@linkplain TupleDescriptor tuple} of arbitrary objects.  Write the
	 * size of the tuple then the elements as object identifiers.
	 */
	GENERAL_TUPLE (23, TUPLE_OF_OBJECTS.as("tuple elements"))
	{
		@Override
		@NotNull AvailObject[] decompose (
			final @NotNull AvailObject object)
		{
			return array(object);
		}

		@Override
		AvailObject compose (
			final @NotNull AvailObject[] subobjects,
			final @NotNull Deserializer deserializer)
		{
			return subobjects[0];
		}
	},

	/**
	 * A {@linkplain StringDescriptor tuple of characters with code points in
	 * the range 0..255}.  Write the size of the tuple then the sequence of
	 * character bytes.
	 */
	BYTE_STRING(24,
		BYTE_CHARACTER_TUPLE.as("Latin-1 string"))
	{
		@Override
		@NotNull AvailObject[] decompose (
			final @NotNull AvailObject object)
		{
			return array(object);
		}

		@Override
		AvailObject compose (
			final @NotNull AvailObject[] subobjects,
			final @NotNull Deserializer deserializer)
		{
			return subobjects[0];
		}
	},

	/**
	 * A {@linkplain StringDescriptor tuple of characters} whose code points all
	 * fall in the range 0..65535.  Write the compressed number of characters
	 * then each compressed character.
	 */
	SHORT_STRING(25,
		COMPRESSED_SHORT_CHARACTER_TUPLE.as("Basic Multilingual Plane string"))
	{
		@Override
		@NotNull AvailObject[] decompose (
			final @NotNull AvailObject object)
		{
			return array(object);
		}

		@Override
		AvailObject compose (
			final @NotNull AvailObject[] subobjects,
			final @NotNull Deserializer deserializer)
		{
			return subobjects[0];
		}
	},

	/**
	 * A {@linkplain StringDescriptor tuple of characters} with arbitrary code
	 * points.  Write the compressed number of characters then each compressed
	 * character.
	 */
	ARBITRARY_STRING(26,
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.as("arbitrary string"))
	{
		@Override
		@NotNull AvailObject[] decompose (
			final @NotNull AvailObject object)
		{
			return array(object);
		}

		@Override
		AvailObject compose (
			final @NotNull AvailObject[] subobjects,
			final @NotNull Deserializer deserializer)
		{
			return subobjects[0];
		}
	},

	/**
	 * A {@linkplain StringDescriptor tuple of characters} whose code points all
	 * fall in the range 0..65535.  Write the compressed number of characters
	 * then each compressed character.
	 */
	BYTE_TUPLE(27, UNCOMPRESSED_BYTE_TUPLE.as("tuple of bytes"))
	{
		@Override
		@NotNull AvailObject[] decompose (
			final @NotNull AvailObject object)
		{
			return array(object);
		}

		@Override
		AvailObject compose (
			final @NotNull AvailObject[] subobjects,
			final @NotNull Deserializer deserializer)
		{
			return subobjects[0];
		}
	},

	/**
	 * A {@linkplain StringDescriptor tuple of characters} whose code points all
	 * fall in the range 0..65535.  Write the compressed number of characters
	 * then each compressed character.
	 */
	NYBBLE_TUPLE(28, UNCOMPRESSED_NYBBLE_TUPLE.as("tuple of nybbles"))
	{
		@Override
		@NotNull AvailObject[] decompose (
			final @NotNull AvailObject object)
		{
			return array(object);
		}

		@Override
		AvailObject compose (
			final @NotNull AvailObject[] subobjects,
			final @NotNull Deserializer deserializer)
		{
			return subobjects[0];
		}
	},

	/**
	 * A {@linkplain SetDescriptor set}.  Convert it to a tuple and work with
	 * that, converting it back to a set when deserializing.
	 */
	SET(29, TUPLE_OF_OBJECTS.as("tuple of objects"))
	{
		@Override
		AvailObject[] decompose (final AvailObject object)
		{
			return array(object.asTuple());
		}

		@Override
		AvailObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return subobjects[0].asSet();
		}
	},

	/**
	 * A {@linkplain MapDescriptor map}.  Convert it to a tuple (key1, value1,
	 * ... key[N], value[N]) and work with that, converting it back to a map
	 * when deserializing.
	 */
	MAP(30, GENERAL_MAP.as("map contents"))
	{
		@Override
		AvailObject[] decompose (final AvailObject object)
		{
			return array(object);
		}

		@Override
		AvailObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return subobjects[0];
		}
	},

	/**
	 * A {@linkplain MapDescriptor map}.  Convert it to a tuple (key1, value1,
	 * ... key[N], value[N]) and work with that, converting it back to a map
	 * when deserializing.
	 */
	OBJECT(31, GENERAL_MAP.as("field map"))
	{
		@Override
		AvailObject[] decompose (final AvailObject object)
		{
			return array(object.fieldMap());
		}

		@Override
		AvailObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return ObjectDescriptor.objectFromMap(subobjects[0]);
		}
	},

	/**
	 * A {@linkplain MapDescriptor map}.  Convert it to a tuple (key1, value1,
	 * ... key[N], value[N]) and work with that, converting it back to a map
	 * when deserializing.
	 */
	OBJECT_TYPE(32, GENERAL_MAP.as("field type map"))
	{
		@Override
		AvailObject[] decompose (final AvailObject object)
		{
			return array(object.fieldTypeMap());
		}

		@Override
		AvailObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return ObjectTypeDescriptor.objectTypeFromMap(subobjects[0]);
		}
	},

	/**
	 * An {@linkplain AtomDescriptor atom}.  Output the atom name and the name
	 * of the module that issued it.  Look up the corresponding atom during
	 * reconstruction, recreating it if it's not present and supposed to have
	 * been issued by the current module.
	 */
	ATOM(33,
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.as("atom name"),
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.as("module name"))
	{
		@Override
		AvailObject[] decompose (final AvailObject object)
		{
			final AvailObject module = object.issuingModule();
			if (module.equalsNull())
			{
				throw new RuntimeException("Atom has no issuing module");
			}
			return array(object.name(), module.name());
		}

		@Override
		@NotNull AvailObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final AvailObject atomName = subobjects[0];
			final AvailObject moduleName = subobjects[1];
			return lookupAtom(atomName, moduleName, deserializer);
		}
	},

	/**
	 * A {@linkplain CompiledCodeDescriptor compiled code object}.  Output any
	 * information needed to reconstruct the compiled code object.
	 */
	COMPILED_CODE (34,
		COMPRESSED_SHORT.as("Total number of frame slots"),
		COMPRESSED_SHORT.as("Primitive number"),
		OBJECT_REFERENCE.as("Function type"),
		UNCOMPRESSED_NYBBLE_TUPLE.as("Level one nybblecodes"),
		TUPLE_OF_OBJECTS.as("Regular literals"),
		TUPLE_OF_OBJECTS.as("Local types"),
		TUPLE_OF_OBJECTS.as("Outer types"))
	{

		@Override
		AvailObject[] decompose (final AvailObject object)
		{
			final int numLocals = object.numLocals();
			final int numOuters = object.numOuters();
			final int numRegularLiterals =
				object.numLiterals() - numLocals - numOuters;
			final AvailObject regularLiterals =
				ObjectTupleDescriptor.mutable().create(numRegularLiterals);
			for (int i = 1; i <= numRegularLiterals; i++)
			{
				regularLiterals.tupleAtPut(i, object.literalAt(i));
			}
			final AvailObject localTypes =
				ObjectTupleDescriptor.mutable().create(numLocals);
			for (int i = 1; i <= numLocals; i++)
			{
				localTypes.tupleAtPut(i, object.localTypeAt(i));
			}
			final AvailObject outerTypes =
				ObjectTupleDescriptor.mutable().create(numOuters);
			for (int i = 1; i <= numOuters; i++)
			{
				outerTypes.tupleAtPut(i, object.outerTypeAt(i));
			}
			return array(
				IntegerDescriptor.fromInt(object.numArgsAndLocalsAndStack()),
				IntegerDescriptor.fromInt(object.primitiveNumber()),
				object.functionType(),
				object.nybbles(),
				regularLiterals,
				localTypes,
				outerTypes);
		}

		@Override
		AvailObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final int numArgsAndLocalsAndStack = subobjects[0].extractInt();
			final int primitive = subobjects[1].extractInt();
			final AvailObject functionType = subobjects[2];
			final AvailObject nybbles = subobjects[3];
			final AvailObject regularLiterals = subobjects[4];
			final AvailObject localTypes = subobjects[5];
			final AvailObject outerTypes = subobjects[6];

			final AvailObject numArgsRange =
				functionType.argsTupleType().sizeRange();
			final int numArgs = numArgsRange.lowerBound().extractInt();
			final int numLocals = localTypes.tupleSize();

			return CompiledCodeDescriptor.create(
				nybbles,
				localTypes.tupleSize(),
				numArgsAndLocalsAndStack - numLocals - numArgs,
				functionType,
				primitive,
				regularLiterals,
				localTypes,
				outerTypes);
		}
	},

	/**
	 * A {@linkplain FunctionDescriptor function} with no outer (lexically
	 * captured) variables.
	 */
	CLEAN_FUNCTION (35,
		OBJECT_REFERENCE.as("Compiled code"))
	{
		@Override
		AvailObject[] decompose (final AvailObject object)
		{
			return array(
				object.code());
		}

		@Override
		AvailObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final AvailObject code = subobjects[0];
			return FunctionDescriptor.create(code, TupleDescriptor.empty());
		}
	},

	/**
	 * A {@linkplain FunctionDescriptor function} with one or more outer
	 * (lexically captured) variables.
	 */
	GENERAL_FUNCTION (36,
		OBJECT_REFERENCE.as("Compiled code"),
		TUPLE_OF_OBJECTS.as("Outer values"))
	{
		@Override
		AvailObject[] decompose (final AvailObject object)
		{
			final int numOuters = object.numOuterVars();
			final AvailObject outers = ObjectTupleDescriptor.mutable().create(
				numOuters);
			for (int i = 1; i <= numOuters; i++)
			{
				outers.tupleAtPut(i, object.outerVarAt(i));
			}
			return array(
				object.code(),
				outers);
		}

		@Override
		AvailObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final AvailObject code = subobjects[0];
			final AvailObject outers = subobjects[1];
			return FunctionDescriptor.create(
				code,
				outers);
		}
	},

	/**
	 * A {@linkplain FunctionTypeDescriptor function type}.
	 */
	FUNCTION_TYPE (37,
		OBJECT_REFERENCE.as("Arguments tuple type"),
		OBJECT_REFERENCE.as("Return type"),
		TUPLE_OF_OBJECTS.as("Checked exceptions"))
	{
		@Override
		AvailObject[] decompose (final AvailObject object)
		{
			return array(
				object.argsTupleType(),
				object.returnType(),
				object.declaredExceptions().asTuple());
		}

		@Override
		AvailObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final AvailObject argsTupleType = subobjects[0];
			final AvailObject returnType = subobjects[1];
			final AvailObject checkedExceptionsTuple = subobjects[2];
			return FunctionTypeDescriptor.createWithArgumentTupleType(
				argsTupleType,
				returnType,
				checkedExceptionsTuple.asSet());
		}
	},

	/**
	 * A {@linkplain TupleTypeDescriptor tuple type}.
	 */
	TUPLE_TYPE (38,
		OBJECT_REFERENCE.as("Tuple sizes"),
		TUPLE_OF_OBJECTS.as("Leading types"),
		OBJECT_REFERENCE.as("Default type"))
	{
		@Override
		AvailObject[] decompose (final AvailObject object)
		{
			return array (
				object.sizeRange(),
				object.typeTuple(),
				object.defaultType());
		}

		@Override
		AvailObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final AvailObject sizeRange = subobjects[0];
			final AvailObject typeTuple = subobjects[1];
			final AvailObject defaultType = subobjects[2];
			return TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				sizeRange,
				typeTuple,
				defaultType);
		}
	},

	/**
	 * An {@linkplain IntegerRangeTypeDescriptor integer range type}.
	 */
	INTEGER_RANGE_TYPE (39,
		BYTE.as("Inclusive flags"),
		OBJECT_REFERENCE.as("Lower bound"),
		OBJECT_REFERENCE.as("Upper bound"))
	{
		@Override
		AvailObject[] decompose (final AvailObject object)
		{
			final int flags = (object.lowerInclusive() ? 1 : 0)
				+ (object.upperInclusive() ? 2 : 0);
			return array(
				IntegerDescriptor.fromInt(flags),
				object.lowerBound(),
				object.upperBound());
		}

		@Override
		AvailObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final int flags = subobjects[0].extractUnsignedByte();
			final AvailObject lowerBound = subobjects[1];
			final AvailObject upperBound = subobjects[2];
			final boolean lowerInclusive = (flags & 1) != 0;
			final boolean upperInclusive = (flags & 2) != 0;
			return IntegerRangeTypeDescriptor.create(
				lowerBound,
				lowerInclusive,
				upperBound,
				upperInclusive);
		}
	},

	/**
	 * A reference to a {@linkplain MethodDescriptor method} that should be
	 * looked up by name during reconstruction.
	 */
	METHOD (40,
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.as("method's atom's name"),
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.as("method's atom's module name"))
	{
		@Override
		AvailObject[] decompose (final AvailObject object)
		{
			assert object.isInstanceOf(Types.METHOD.o());
			final AvailObject methodNameAtom = object.name();
			final AvailObject module = methodNameAtom.issuingModule();
			if (module.equalsNull())
			{
				throw new RuntimeException("Atom has no issuing module");
			}
			return array(methodNameAtom.name(), module.name());
		}

		@Override
		@NotNull AvailObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final AvailObject atomName = subobjects[0];
			final AvailObject moduleName = subobjects[1];
			final AvailObject atom = lookupAtom(
				atomName,
				moduleName,
				deserializer);
			return deserializer.runtime().methodFor(atom);
		}
	},

	/**
	 * A {@linkplain PojoTypeDescriptor pojo type} for which {@linkplain
	 * AvailObject#isPojoFusedType()} is false.  This indicates a representation
	 * with a juicy class filling, which allows a particularly compact
	 * representation involving the class name and its parameter types.
	 */
	UNFUSED_POJO_TYPE (41,
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.as("class name"),
		TUPLE_OF_OBJECTS.as("class parameterization"))
	{
		@Override
		AvailObject[] decompose (final AvailObject object)
		{
			assert object.isPojoType();
			assert !object.isPojoFusedType();
			final AvailObject rawPojoType = object.javaClass();
			final Class<?> baseClass =
				(Class<?>)rawPojoType.javaObject();
			final AvailObject className =
				StringDescriptor.from(baseClass.getName());
			final AvailObject ancestorMap = object.javaAncestors();
			final AvailObject myParameters = ancestorMap.mapAt(rawPojoType);
			return array(className, myParameters);
		}

		@Override
		@NotNull AvailObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final ClassLoader classLoader =
				deserializer.runtime().classLoader();
			Class<?> baseClass;
			try
			{
				baseClass = Class.forName(
					subobjects[0].asNativeString(),
					true,
					classLoader);
			}
			catch (final ClassNotFoundException e)
			{
				throw new RuntimeException(e);
			}
			return PojoTypeDescriptor.forClassWithTypeArguments(
				baseClass,
				subobjects[1]);
		}
	},

	/**
	 * A {@linkplain PojoTypeDescriptor pojo type} for which {@linkplain
	 * AvailObject#isPojoFusedType()} is true.  This indicates a representation
	 * without the juicy class filling, so we have to say how each ancestor is
	 * parameterized.
	 */
	FUSED_POJO_TYPE (42,
		GENERAL_MAP.as("ancestor parameterizations map"))
	{
		@Override
		AvailObject[] decompose (final AvailObject object)
		{
			assert object.isPojoType();
			assert object.isPojoFusedType();
			AvailObject symbolicMap = MapDescriptor.empty();
			for (final MapDescriptor.Entry entry
				: object.javaAncestors().mapIterable())
			{
				final Class<?> baseClass =
					(Class<?>)entry.key.javaObject();
				final AvailObject className =
					StringDescriptor.from(baseClass.getName());
				symbolicMap = symbolicMap.mapAtPuttingCanDestroy(
					className,
					entry.value,
					true);
			}
			return array(symbolicMap);
		}

		@Override
		@NotNull AvailObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final ClassLoader classLoader =
				deserializer.runtime().classLoader();
			AvailObject ancestorMap = MapDescriptor.empty();
			try
			{
				for (final MapDescriptor.Entry entry
					: subobjects[0].mapIterable())
				{
					final Class<?> baseClass = Class.forName(
						entry.key.asNativeString(),
						true,
						classLoader);
					final AvailObject rawPojo =
						RawPojoDescriptor.equalityWrap(baseClass);
					ancestorMap = ancestorMap.mapAtPuttingCanDestroy(
						rawPojo,
						entry.value,
						true);
				}
			}
			catch (final ClassNotFoundException e)
			{
				throw new RuntimeException(e);
			}
			return PojoTypeDescriptor.fusedTypeFromAncestorMap(
				ancestorMap);
		}
	},

	/**
	 * A {@linkplain PojoTypeDescriptor pojo type} representing a Java array
	 * type.  We can reconstruct this array type from the content type and the
	 * range of allowable sizes (a much stronger model than Java itself
	 * supports).
	 */
	ARRAY_POJO_TYPE (43,
		OBJECT_REFERENCE.as("content type"),
		OBJECT_REFERENCE.as("size range"))
	{
		@Override
		AvailObject[] decompose (final AvailObject object)
		{
			assert object.isPojoArrayType();
			final AvailObject contentType = object.contentType();
			final AvailObject sizeRange = object.sizeRange();
			return array(contentType, sizeRange);
		}

		@Override
		@NotNull AvailObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final AvailObject contentType = subobjects[0];
			final AvailObject sizeRange = subobjects[1];
			return PojoTypeDescriptor.forArrayTypeWithSizeRange(
				contentType,
				sizeRange);
		}
	},

	/**
	 * A {@linkplain PojoTypeDescriptor pojo type} representing a "self type"
	 * for parameterizing a Java class by itself.  For example, in the
	 * parametric type {@code Enum<E extends Enum<E>>}, we parameterize the
	 * class {@code Enum} with such a self type.  To reconstruct a self type all
	 * we need is a way to get to the raw Java class, so we serialize its name.
	 */
	SELF_POJO_TYPE (44,
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.as("class name"))
	{
		@Override
		AvailObject[] decompose (final AvailObject object)
		{
			assert object.isPojoSelfType();
			final AvailObject rawPojoType = object.javaClass();
			final Class<?> selfClass =
				(Class<?>)rawPojoType.javaObject();
			final AvailObject className =
				StringDescriptor.from(selfClass.getName());
			return array(className);
		}

		@Override
		@NotNull AvailObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final String className = subobjects[0].asNativeString();
			final ClassLoader classLoader =
				deserializer.runtime().classLoader();
			Class<?> baseClass;
			try
			{
				baseClass = Class.forName(
					className,
					true,
					classLoader);
			}
			catch (final ClassNotFoundException e)
			{
				throw new RuntimeException(e);
			}
			return PojoTypeDescriptor.selfTypeForClass(baseClass);
		}
	};

	/**
	 * The operands that this operation expects to see encoded after the tag.
	 */
	private final SerializerOperand[] operands;

	/**
	 * Answer my {@linkplain SerializerOperand operands}.
	 *
	 * @return My {@code SerializerOperand}s.
	 */
	@NotNull SerializerOperand[] operands ()
	{
		return operands;
	}

	/**
	 * Construct a new {@link SerializerOperation}.
	 *
	 * @param ordinal
	 *            The ordinal of this enum value, supplied as a cross-check to
	 *            reduce the chance of accidental incompatibility due to the
	 *            addition of new categories of Avail objects.
	 * @param operands
	 *            The list of operands that describe the interpretation of a
	 *            stream of bytes written with this {@code SerializerOperation}.
	 */
	private SerializerOperation (
		final int ordinal,
		final SerializerOperand... operands)
	{
		assert (ordinal & 255) == ordinal;
		assert ordinal() == ordinal;
		this.operands = operands;
	}

	/**
	 * Decompose the given {@link AvailObject} into an array of {@code
	 * AvailObject}s that correspond with my {@link #operands}.
	 *
	 * @param object
	 *            The object to decompose.
	 * @return
	 *            An array of {@code AvailObject}s whose entries agree with this
	 *            {@link SerializerOperation}'s operands.
	 */
	abstract @NotNull AvailObject[] decompose (
		final @NotNull AvailObject object);

	/**
	 * Reconstruct the given {@link AvailObject} from an array of {@code
	 * AvailObject}s that correspond with my {@link #operands}.
	 *
	 * @param subobjects
	 *            The array of {@code AvailObject}s to assemble into a new
	 *            object.
	 * @param deserializer
	 *            The {@link Deserializer} for those instructions that do
	 *            more than simply assemble an object.
	 * @return
	 *            The new {@code AvailObject}.
	 */
	abstract @NotNull AvailObject compose (
		final @NotNull AvailObject[] subobjects,
		final @NotNull Deserializer deserializer);

	/**
	 * Write the given {@link AvailObject} to the {@link Serializer}.  It
	 * must have already been fully traced.
	 *
	 * @param object The already traced {@code AvailObject} to serialize.
	 * @param serializer Where to serialize it.
	 */
	void writeObject (
		final @NotNull AvailObject object,
		final @NotNull Serializer serializer)
	{
		serializer.writeByte(ordinal());
		final @NotNull AvailObject[] decomposed = decompose(object);
		assert decomposed.length == operands.length;
		for (int i = 0; i < decomposed.length; i++)
		{
			operands[i].write(decomposed[i], serializer);
		}
	}

	/**
	 * @param atomName
	 * @param moduleName
	 * @param deserializer
	 * @return
	 */
	AvailObject lookupAtom (
		final AvailObject atomName,
		final AvailObject moduleName,
		final Deserializer deserializer)
	{
		final AvailObject currentModule = deserializer.currentModule();
		if (moduleName.equals(currentModule.name()))
		{
			// An atom in the current module.  Create it if necessary.
			// Check if it's already defined somewhere...
			final AvailObject localMatches;
			if (currentModule.privateNames().hasKey(atomName))
			{
				localMatches = currentModule.privateNames().mapAt(atomName);
			}
			else
			{
				localMatches = SetDescriptor.empty();
			}

			if (localMatches.setSize() == 1)
			{
				return localMatches.asTuple().tupleAt(1);
			}
			else if (localMatches.setSize() == 0)
			{
				final AvailObject atom = AtomDescriptor.create(
					atomName,
					currentModule);
				currentModule.atPrivateNameAdd(atomName, atom);
				return atom;
			}
			else
			{
				throw new RuntimeException(
					"Ambiguous local atom name: \""
					+ atomName.toString() + "\"");
			}
		}
		// An atom in an imported module.
		final AvailObject module = deserializer.moduleNamed(moduleName);
		if (module.privateNames().hasKey(atomName))
		{
			final AvailObject atoms = module.privateNames().mapAt(atomName);
			if (atoms.setSize() == 1)
			{
				return atoms.asTuple().tupleAt(1);
			}
			if (atoms.setSize() > 1)
			{
				throw new RuntimeException(
					"Ambiguous imported atom \""
					+ atomName.toString()
					+ "\" in module \""
					+ moduleName.toString()
					+ "\"");
			}
		}
		throw new RuntimeException(
			"No such atom \""
			+ atomName.toString()
			+ "\" in module \""
			+ moduleName.toString()
			+ "\"");
	}

	/**
	 * Read an {@link AvailObject} from the {@link Deserializer}.  Its
	 * predecessors must already have been fully assembled.
	 *
	 * @param deserializer
	 *            The {@code Deserializer} from which to read an object.
	 */
	static void readObject (
		final @NotNull Deserializer deserializer)
	{
		final int ordinal = deserializer.readByte();
		final SerializerOperation operation = values()[ordinal];
		final SerializerOperand[] operands = operation.operands();
		final AvailObject[] subobjects = new AvailObject[operands.length];
		for (int i = 0; i < operands.length; i++)
		{
			subobjects[i] = operands[i].read(deserializer);
		}
		final AvailObject newObject =
			operation.compose(subobjects, deserializer);
		newObject.makeImmutable();
		deserializer.addObject(newObject);
	}

	/**
	 * This helper function takes a variable number of arguments as an array,
	 * and conveniently returns that array.  This is syntactically <em>much</em>
	 * cleaner than any built-in array building syntax.
	 *
	 * @param objects The {@link AvailObject}s.
	 * @return The same array of {@code AvailObject}s.
	 */
	static AvailObject[] array (
		final @NotNull AvailObject... objects)
	{
		return objects;
	}
}
