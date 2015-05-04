/**
 * SerializerOperation.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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
import java.util.*;
import com.avail.AvailRuntime;
import com.avail.descriptor.*;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.descriptor.TokenDescriptor.TokenType;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.exceptions.MalformedMessageException;
import com.avail.interpreter.levelTwo.L2Chunk;

/**
 * A {@code SerializerOpcode} describes how to disassemble and assemble the
 * various kinds of objects encountered in Avail.
 *
 * <p>
 * The ordinal is passed in the constructor as a cross-check, to increase the
 * difficulty of (accidentally) changing the serialized representation without
 * due care for migration of existing serialized data.
 * </p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
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
		A_BasicObject[] decompose (
			final AvailObject object)
		{
			return array();
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
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
		A_BasicObject[] decompose (
			final AvailObject object)
		{
			return array();
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
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
		A_BasicObject[] decompose (
			final AvailObject object)
		{
			return array();
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return IntegerDescriptor.two();
		}
	},

	/**
	 * The Avail integer 3.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	THREE_INTEGER (3)
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object)
		{
			return array();
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return IntegerDescriptor.fromUnsignedByte((short)3);
		}
	},

	/**
	 * The Avail integer 4.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	FOUR_INTEGER (4)
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object)
		{
			return array();
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return IntegerDescriptor.fromUnsignedByte((short)4);
		}
	},

	/**
	 * The Avail integer 5.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	FIVE_INTEGER (5)
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object)
		{
			return array();
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return IntegerDescriptor.fromUnsignedByte((short)5);
		}
	},

	/**
	 * The Avail integer 6.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	SIX_INTEGER (6)
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object)
		{
			return array();
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return IntegerDescriptor.fromUnsignedByte((short)6);
		}
	},

	/**
	 * The Avail integer 7.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	SEVEN_INTEGER (7)
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object)
		{
			return array();
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return IntegerDescriptor.fromUnsignedByte((short)7);
		}
	},

	/**
	 * The Avail integer 8.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	EIGHT_INTEGER (8)
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object)
		{
			return array();
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return IntegerDescriptor.fromUnsignedByte((short)8);
		}
	},

	/**
	 * The Avail integer 9.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	NINE_INTEGER (9)
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object)
		{
			return array();
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return IntegerDescriptor.fromUnsignedByte((short)9);
		}
	},

	/**
	 * The Avail integer 10.  Note that there are no operands, since the value
	 * is encoded in the choice of instruction itself.
	 */
	TEN_INTEGER (10)
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object)
		{
			return array();
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return IntegerDescriptor.fromUnsignedByte((short)10);
		}
	},

	/**
	 * An Avail integer in the range 11..255.  Note that 0..10 have their own
	 * special cases already which require very little space.
	 */
	BYTE_INTEGER (11, BYTE.as("only byte"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object)
		{
			return array(object);
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return subobjects[0];
		}
	},

	/**
	 * An Avail integer in the range 256..65535.  Note that 0..255 have their
	 * own special cases already which require less space.  Don't try to
	 * compress the short value for this reason.
	 */
	SHORT_INTEGER (12, UNCOMPRESSED_SHORT.as("the unsigned short"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object)
		{
			return array(object);
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return subobjects[0];
		}
	},

	/**
	 * An Avail integer in the range -2<sup>31</sup> through 2<sup>31</sup>-1,
	 * except the range 0..65535 which have their own special cases already.
	 */
	INT_INTEGER (13, SIGNED_INT.as("int's value"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object)
		{
			return array(object);
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
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
		A_BasicObject[] decompose (
			final AvailObject object)
		{
			return array(object);
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return subobjects[0];
		}
	},


	/**
	 * Produce the Avail {@linkplain NilDescriptor#nil() nil} during
	 * deserialization.
	 */
	NIL (15)
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			return array();
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return NilDescriptor.nil();
		}
	},

	/**
	 * This special opcode causes a previously built object to be produced as an
	 * actual checkpoint output from the {@link Deserializer}.
	 */
	CHECKPOINT (16, OBJECT_REFERENCE.as("object to checkpoint"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			// Make sure the function actually gets written out.
			return array(object);
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
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
		A_BasicObject[] decompose (
			final AvailObject object)
		{
			return array(
				IntegerDescriptor.fromInt(
					Serializer.indexOfSpecialObject(object)));
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return deserializer.specialObject(subobjects[0].extractInt());
		}
	},

	/**
	 * One of the special atoms that the {@link AvailRuntime} maintains.
	 */
	SPECIAL_ATOM (18, COMPRESSED_SHORT.as("special atom number"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object)
		{
			return array(
				IntegerDescriptor.fromInt(
					Serializer.indexOfSpecialAtom(object)));
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return deserializer.specialAtom(subobjects[0].extractInt());
		}
	},

	/**
	 * A {@linkplain CharacterDescriptor character} whose code point fits in an
	 * unsigned byte (0..255).
	 */
	BYTE_CHARACTER (19, BYTE.as("Latin-1 code point"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object)
		{
			return array(
				IntegerDescriptor.fromInt(object.codePoint()));
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return CharacterDescriptor.fromCodePoint(
				subobjects[0].extractInt());
		}
	},

	/**
	 * A {@linkplain CharacterDescriptor character} whose code point requires an
	 * unsigned short (256..65535).
	 */
	SHORT_CHARACTER (20,
		UNCOMPRESSED_SHORT.as("BMP code point"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object)
		{
			return array(
				IntegerDescriptor.fromInt(object.codePoint()));
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return CharacterDescriptor.fromCodePoint(
				subobjects[0].extractInt());
		}
	},

	/**
	 * A {@linkplain CharacterDescriptor character} whose code point requires
	 * three bytes to represent (0..16777215, but technically only 0..1114111).
	 */
	LARGE_CHARACTER (21,
		BYTE.as("SMP codepoint high byte"),
		BYTE.as("SMP codepoint middle byte"),
		BYTE.as("SMP codepoint low byte"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object)
		{
			final int codePoint = object.codePoint();
			return array(
				IntegerDescriptor.fromInt((codePoint >> 16) & 0xFF),
				IntegerDescriptor.fromInt((codePoint >> 8) & 0xFF),
				IntegerDescriptor.fromInt(codePoint & 0xFF));
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return CharacterDescriptor.fromCodePoint(
				(subobjects[0].extractUnsignedByte() << 16)
				+ (subobjects[1].extractUnsignedByte() << 8)
				+ subobjects[2].extractUnsignedByte());
		}
	},

	/**
	 * A {@linkplain FloatDescriptor float}.  Convert the raw bits to an int
	 * for writing.
	 */
	FLOAT (22, SIGNED_INT.as("raw bits"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object)
		{
			final float floatValue = object.extractFloat();
			final int floatBits = Float.floatToRawIntBits(floatValue);
			return array(
				IntegerDescriptor.fromInt(floatBits));
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
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
	DOUBLE (23,
		SIGNED_INT.as("upper raw bits"),
		SIGNED_INT.as("lower raw bits"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object)
		{
			final double doubleValue = object.extractDouble();
			final long doubleBits = Double.doubleToRawLongBits(doubleValue);
			return array(
				IntegerDescriptor.fromInt((int)(doubleBits >> 32)),
				IntegerDescriptor.fromInt((int)doubleBits));
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
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
	GENERAL_TUPLE (24, TUPLE_OF_OBJECTS.as("tuple elements"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object)
		{
			return array(object);
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return subobjects[0];
		}
	},

	/**
	 * A {@linkplain StringDescriptor tuple of characters with code points in
	 * the range 0..255}.  Write the size of the tuple then the sequence of
	 * character bytes.
	 */
	BYTE_STRING(25,
		BYTE_CHARACTER_TUPLE.as("Latin-1 string"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object)
		{
			return array(object);
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return subobjects[0];
		}
	},

	/**
	 * A {@linkplain StringDescriptor tuple of characters} whose code points all
	 * fall in the range 0..65535.  Write the compressed number of characters
	 * then each compressed character.
	 */
	SHORT_STRING(26,
		COMPRESSED_SHORT_CHARACTER_TUPLE.as("Basic Multilingual Plane string"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object)
		{
			return array(object);
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return subobjects[0];
		}
	},

	/**
	 * A {@linkplain StringDescriptor tuple of characters} with arbitrary code
	 * points.  Write the compressed number of characters then each compressed
	 * character.
	 */
	ARBITRARY_STRING(27,
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.as("arbitrary string"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object)
		{
			return array(object);
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return subobjects[0];
		}
	},

	/**
	 * A {@linkplain TupleDescriptor tuple of integers} whose values all
	 * fall in the range 0..255.
	 */
	BYTE_TUPLE(28, UNCOMPRESSED_BYTE_TUPLE.as("tuple of bytes"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object)
		{
			return array(object);
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return subobjects[0];
		}
	},

	/**
	 * A {@linkplain TupleDescriptor tuple of integers} whose values fall in
	 * the range 0..15.
	 */
	NYBBLE_TUPLE(29, UNCOMPRESSED_NYBBLE_TUPLE.as("tuple of nybbles"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object)
		{
			return array(object);
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return subobjects[0];
		}
	},

	/**
	 * A {@linkplain SetDescriptor set}.  Convert it to a tuple and work with
	 * that, converting it back to a set when deserializing.
	 */
	SET(30, TUPLE_OF_OBJECTS.as("tuple of objects"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			return array(object.asTuple());
		}

		@Override
		A_BasicObject compose (
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
	MAP(31, GENERAL_MAP.as("map contents"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			return array(object);
		}

		@Override
		A_BasicObject compose (
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
	OBJECT(32, GENERAL_MAP.as("field map"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			return array(object.fieldMap());
		}

		@Override
		A_BasicObject compose (
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
	OBJECT_TYPE(33, GENERAL_MAP.as("field type map"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			return array(object.fieldTypeMap());
		}

		@Override
		A_BasicObject compose (
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
	ATOM(34,
		OBJECT_REFERENCE.as("atom name"),
		OBJECT_REFERENCE.as("module name"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			final A_Module module = object.issuingModule();
			if (module.equalsNil())
			{
				throw new RuntimeException("Atom has no issuing module");
			}
			return array(object.atomName(), module.moduleName());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final AvailObject atomName = subobjects[0];
			final AvailObject moduleName = subobjects[1];
			final A_Atom atom = lookupAtom(atomName, moduleName, deserializer);
			return atom.makeShared();
		}
	},

	/**
	 * A {@linkplain CompiledCodeDescriptor compiled code object}.  Output any
	 * information needed to reconstruct the compiled code object.
	 */
	COMPILED_CODE (35,
		COMPRESSED_SHORT.as("Total number of frame slots"),
		COMPRESSED_SHORT.as("Primitive number"),
		OBJECT_REFERENCE.as("Function type"),
		UNCOMPRESSED_NYBBLE_TUPLE.as("Level one nybblecodes"),
		TUPLE_OF_OBJECTS.as("Regular literals"),
		TUPLE_OF_OBJECTS.as("Local types"),
		TUPLE_OF_OBJECTS.as("Outer types"),
		OBJECT_REFERENCE.as("Module name"),
		UNSIGNED_INT.as("Line number"))
	{

		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			final int numLocals = object.numLocals();
			final int numOuters = object.numOuters();
			final int numRegularLiterals =
				object.numLiterals() - numLocals - numOuters;
			final A_Tuple regularLiterals =
				ObjectTupleDescriptor.createUninitialized(numRegularLiterals);
			for (int i = 1; i <= numRegularLiterals; i++)
			{
				regularLiterals.objectTupleAtPut(i, object.literalAt(i));
			}
			final A_Tuple localTypes =
				ObjectTupleDescriptor.createUninitialized(numLocals);
			for (int i = 1; i <= numLocals; i++)
			{
				localTypes.objectTupleAtPut(i, object.localTypeAt(i));
			}
			final A_Tuple outerTypes =
				ObjectTupleDescriptor.createUninitialized(numOuters);
			for (int i = 1; i <= numOuters; i++)
			{
				outerTypes.objectTupleAtPut(i, object.outerTypeAt(i));
			}
			final A_Module module = object.module();
			final A_String moduleName = module.equalsNil()
				? TupleDescriptor.empty()
				: module.moduleName();
			return array(
				IntegerDescriptor.fromInt(object.numArgsAndLocalsAndStack()),
				IntegerDescriptor.fromInt(object.primitiveNumber()),
				object.functionType(),
				object.nybbles(),
				regularLiterals,
				localTypes,
				outerTypes,
				moduleName,
				IntegerDescriptor.fromInt(object.startingLineNumber()));
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final int numArgsAndLocalsAndStack = subobjects[0].extractInt();
			final int primitive = subobjects[1].extractInt();
			final A_Type functionType = subobjects[2];
			final A_Tuple nybbles = subobjects[3];
			final A_Tuple regularLiterals = subobjects[4];
			final A_Tuple localTypes = subobjects[5];
			final A_Tuple outerTypes = subobjects[6];
			final A_String moduleName = subobjects[7];
			final A_Number lineNumberInteger = subobjects[8];

			final A_Type numArgsRange =
				functionType.argsTupleType().sizeRange();
			final int numArgs = numArgsRange.lowerBound().extractInt();
			final int numLocals = localTypes.tupleSize();

			final A_Module module = moduleName.tupleSize() == 0
				? NilDescriptor.nil()
				: deserializer.moduleNamed(moduleName);
			return CompiledCodeDescriptor.create(
				nybbles,
				localTypes.tupleSize(),
				numArgsAndLocalsAndStack - numLocals - numArgs,
				functionType,
				primitive,
				regularLiterals,
				localTypes,
				outerTypes,
				module,
				lineNumberInteger.extractInt());
		}
	},

	/**
	 * A {@linkplain FunctionDescriptor function} with no outer (lexically
	 * captured) variables.
	 */
	CLEAN_FUNCTION (36,
		OBJECT_REFERENCE.as("Compiled code"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			assert object.numOuterVars() == 0;
			return array(
				object.code());
		}

		@Override
		A_BasicObject compose (
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
	GENERAL_FUNCTION (37,
		OBJECT_REFERENCE.as("Compiled code"),
		TUPLE_OF_OBJECTS.as("Outer values"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			final int numOuters = object.numOuterVars();
			final A_Tuple outers =
				ObjectTupleDescriptor.createUninitialized(numOuters);
			for (int i = 1; i <= numOuters; i++)
			{
				outers.objectTupleAtPut(i, object.outerVarAt(i));
			}
			return array(
				object.code(),
				outers);
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final AvailObject code = subobjects[0];
			final A_Tuple outers = subobjects[1];
			return FunctionDescriptor.create(
				code,
				outers);
		}
	},

	/**
	 * A {@linkplain VariableDescriptor variable}.  Always reconstructed, since
	 * there is no mechanism for determining to which existing variable it might
	 * be referring.  The variable is reconstructed in an unassigned state.
	 */
	VARIABLE (38,
		OBJECT_REFERENCE.as("variable type"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			return array(
				object.kind());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return VariableDescriptor.forVariableType(subobjects[0]);
		}

		@Override
		boolean isVariable ()
		{
			return true;
		}
	},

	/**
	 * A {@linkplain VariableSharedWriteOnceDescriptor write-once variable}.
	 * Always reconstructed, since there is no mechanism for determining to
	 * which existing variable it might be referring.  The variable is
	 * reconstructed in an unassigned state, and is expected to be initialized
	 * exactly once at some future time.
	 */
	WRITE_ONCE_VARIABLE (39,
		OBJECT_REFERENCE.as("variable type"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			return array(
				object.kind());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return VariableSharedWriteOnceDescriptor.forVariableType(
				subobjects[0]);
		}

		@Override
		boolean isVariable ()
		{
			return true;
		}
	},

	/**
	 * A {@linkplain TokenDescriptor token}.
	 */
	TOKEN (40,
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.as("token string"),
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.as("leading whitespace"),
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.as("trailing whitespace"),
		SIGNED_INT.as("start position"),
		SIGNED_INT.as("line number"),
		SIGNED_INT.as("token index"),
		BYTE.as("token type code"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			return array(
				object.string(),
				object.leadingWhitespace(),
				object.trailingWhitespace(),
				IntegerDescriptor.fromInt(object.start()),
				IntegerDescriptor.fromInt(object.lineNumber()),
				IntegerDescriptor.fromInt(object.tokenIndex()),
				IntegerDescriptor.fromInt(object.tokenType().ordinal()));
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_String string = subobjects[0];
			final A_String leadingWhitespace = subobjects[1];
			final A_String trailingWhitespace = subobjects[2];
			final int start = subobjects[3].extractInt();
			final int lineNumber = subobjects[4].extractInt();
			final int tokenIndex = subobjects[5].extractInt();
			final int tokenTypeOrdinal = subobjects[6].extractInt();
			return TokenDescriptor.create(
				string,
				leadingWhitespace,
				trailingWhitespace,
				start,
				lineNumber,
				tokenIndex,
				TokenType.all()[tokenTypeOrdinal]);
		}
	},

	/**
	 * A {@linkplain LiteralTokenDescriptor literal token}.
	 */
	LITERAL_TOKEN (41,
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.as("token string"),
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.as("leading whitespace"),
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.as("trailing whitespace"),
		OBJECT_REFERENCE.as("literal value"),
		SIGNED_INT.as("start position"),
		SIGNED_INT.as("line number"),
		SIGNED_INT.as("token index"),
		BYTE.as("token type code"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			return array(
				object.string(),
				object.leadingWhitespace(),
				object.trailingWhitespace(),
				object.literal(),
				IntegerDescriptor.fromInt(object.start()),
				IntegerDescriptor.fromInt(object.lineNumber()),
				IntegerDescriptor.fromInt(object.tokenIndex()),
				IntegerDescriptor.fromInt(object.tokenType().ordinal()));
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_String string = subobjects[0];
			final A_String leadingWhitespace = subobjects[1];
			final A_String trailingWhitespace = subobjects[2];
			final AvailObject literal = subobjects[3];
			final int start = subobjects[4].extractInt();
			final int lineNumber = subobjects[5].extractInt();
			final int tokenIndex = subobjects[6].extractInt();
			final int tokenTypeOrdinal = subobjects[7].extractInt();
			return LiteralTokenDescriptor.create(
				string,
				leadingWhitespace,
				trailingWhitespace,
				start,
				lineNumber,
				tokenIndex,
				TokenType.all()[tokenTypeOrdinal],
				literal);
		}
	},

	/**
	 * A {@linkplain TokenDescriptor token}.
	 */
	COMMENT_TOKEN (42,
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.as("token string"),
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.as("leading whitespace"),
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.as("trailing whitespace"),
		SIGNED_INT.as("start position"),
		SIGNED_INT.as("line number"),
		SIGNED_INT.as("token index"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			return array(
				object.string(),
				object.leadingWhitespace(),
				object.trailingWhitespace(),
				IntegerDescriptor.fromInt(object.start()),
				IntegerDescriptor.fromInt(object.lineNumber()),
				IntegerDescriptor.fromInt(object.tokenIndex()));
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_String string = subobjects[0];
			final A_String leading = subobjects[1];
			final A_String trailing = subobjects[2];
			final int start = subobjects[3].extractInt();
			final int lineNumber = subobjects[4].extractInt();
			final int tokenIndex = subobjects[5].extractInt();
			return CommentTokenDescriptor.create(
				string,
				leading,
				trailing,
				start,
				lineNumber,
				tokenIndex);
		}
	},

	/**
	 * This special opcode causes a previously built variable to have a
	 * previously built value to be assigned to it at this point during
	 * deserialization.
	 */
	ASSIGN_TO_VARIABLE (43,
		OBJECT_REFERENCE.as("variable to assign"),
		OBJECT_REFERENCE.as("value to assign"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			return array(
				object,
				object.value());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_Variable variable = subobjects[0];
			final AvailObject value = subobjects[1];
			variable.setValue(value);
			return NilDescriptor.nil();
		}
	},

	/**
	 * The representation of a continuation, which is just its level one state.
	 */
	CONTINUATION (44,
		OBJECT_REFERENCE.as("calling continuation"),
		OBJECT_REFERENCE.as("continuation's function"),
		TUPLE_OF_OBJECTS.as("continuation frame slots"),
		COMPRESSED_SHORT.as("program counter"),
		COMPRESSED_SHORT.as("stack pointer"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			final int frameSlotCount = object.numArgsAndLocalsAndStack();
			final List<AvailObject> frameSlotsList =
				new ArrayList<>(frameSlotCount);
			for (int i = 1; i <= frameSlotCount; i++)
			{
				frameSlotsList.add(object.argOrLocalOrStackAt(i));
			}
			return array(
				object.caller(),
				object.function(),
				TupleDescriptor.fromList(frameSlotsList),
				IntegerDescriptor.fromInt(object.pc()),
				IntegerDescriptor.fromInt(object.stackp()));
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final AvailObject caller = subobjects[0];
			final A_Function function = subobjects[1];
			final A_Tuple frameSlots = subobjects[2];
			final A_Number pcInteger = subobjects[3];
			final A_Number stackpInteger = subobjects[4];
			final int frameSlotCount = frameSlots.tupleSize();
			final A_Continuation continuation =
				ContinuationDescriptor.createExceptFrame(
					function,
					caller,
					pcInteger.extractInt(),
					stackpInteger.extractInt(),
					false,
					L2Chunk.unoptimizedChunk(),
					L2Chunk.offsetToContinueUnoptimizedChunk());
			for (int i = 1; i <= frameSlotCount; i++)
			{
				continuation.argOrLocalOrStackAtPut(i, frameSlots.tupleAt(i));
			}
			continuation.makeImmutable();
			return continuation;
		}
	},

	/**
	 * A reference to a {@linkplain MethodDescriptor method} that should be
	 * looked up during deserialization.  A method can have multiple {@linkplain
	 * MessageBundleDescriptor message bundles}, only one of which is output
	 * during serialization, chosen arbitrarily.  During deserialization, the
	 * message bundle is looked up, and its method is extracted.
	 */
	METHOD (45, OBJECT_REFERENCE.as("arbitrary method bundle"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			assert object.isInstanceOf(Types.METHOD.o());
			return array(object.bundles().iterator().next());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_Bundle bundle = subobjects[0];
			return bundle.bundleMethod();
		}
	},

	/**
	 * A reference to a {@linkplain MethodDefinitionDescriptor method
	 * definition}, which should be reconstructed by looking it up.
	 */
	METHOD_DEFINITION (46,
		OBJECT_REFERENCE.as("method"),
		OBJECT_REFERENCE.as("signature"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			assert object.isMethodDefinition();
			return array(
				object.definitionMethod(),
				object.bodySignature());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_Method definitionMethod = subobjects[0];
			final A_Type signature = subobjects[1];
			final List<AvailObject> definitions = new ArrayList<>(1);
			for (final AvailObject eachDefinition
				: definitionMethod.definitionsTuple())
			{
				if (eachDefinition.bodySignature().equals(signature))
				{
					definitions.add(eachDefinition);
				}
			}
			assert definitions.size() == 1;
			final AvailObject definition = definitions.get(0);
			assert definition.isMethodDefinition();
			return definition;
		}
	},

	/**
	 * A reference to a {@linkplain MacroDefinitionDescriptor macro
	 * definition}, which should be reconstructed by looking it up.
	 */
	MACRO_DEFINITION (47,
		OBJECT_REFERENCE.as("method"),
		OBJECT_REFERENCE.as("signature"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			assert object.isMacroDefinition();
			return array(
				object.definitionMethod(),
				object.bodySignature());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_Method definitionMethod = subobjects[0];
			final A_Type signature = subobjects[1];
			final List<AvailObject> definitions = new ArrayList<>(1);
			for (final AvailObject eachDefinition
				: definitionMethod.definitionsTuple())
			{
				if (eachDefinition.bodySignature().equals(signature))
				{
					definitions.add(eachDefinition);
				}
			}
			assert definitions.size() == 1;
			final AvailObject definition = definitions.get(0);
			assert definition.isMacroDefinition();
			return definition;
		}
	},

	/**
	 * A reference to an {@linkplain AbstractDefinitionDescriptor abstract
	 * declaration}, which should be reconstructed by looking it up.
	 */
	ABSTRACT_DEFINITION (48,
		OBJECT_REFERENCE.as("method"),
		OBJECT_REFERENCE.as("signature"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			assert object.isAbstractDefinition();
			return array(
				object.definitionMethod(),
				object.bodySignature());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_Method definitionMethod = subobjects[0];
			final A_Type signature = subobjects[1];
			final List<AvailObject> definitions = new ArrayList<>(1);
			for (final AvailObject eachDefinition
				: definitionMethod.definitionsTuple())
			{
				if (eachDefinition.bodySignature().equals(signature))
				{
					definitions.add(eachDefinition);
				}
			}
			assert definitions.size() == 1;
			final AvailObject definition = definitions.get(0);
			assert definition.isAbstractDefinition();
			return definition;
		}
	},

	/**
	 * A reference to a {@linkplain ForwardDefinitionDescriptor forward
	 * declaration}, which should be reconstructed by looking it up.
	 */
	FORWARD_DEFINITION (49,
		OBJECT_REFERENCE.as("method"),
		OBJECT_REFERENCE.as("signature"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			assert object.isForwardDefinition();
			return array(
				object.definitionMethod(),
				object.bodySignature());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_Method definitionMethod = subobjects[0];
			final A_Type signature = subobjects[1];
			final List<AvailObject> definitions = new ArrayList<>(1);
			for (final AvailObject eachDefinition
				: definitionMethod.definitionsTuple())
			{
				if (eachDefinition.bodySignature().equals(signature))
				{
					definitions.add(eachDefinition);
				}
			}
			assert definitions.size() == 1;
			final AvailObject definition = definitions.get(0);
			assert definition.isForwardDefinition();
			return definition;
		}
	},

	/**
	 * A reference to a {@linkplain MessageBundleDescriptor message bundle},
	 * which should be reconstructed by looking it up.
	 */
	MESSAGE_BUNDLE (50,
		OBJECT_REFERENCE.as("message atom"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			return array(
				object.message());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_Atom atom = subobjects[0];
			try
			{
				return atom.bundleOrCreate();
			}
			catch (final MalformedMessageException e)
			{
				throw new RuntimeException(
					"Bundle should not have been serialized with malformed "
					+ "message");
			}
		}
	},

	/**
	 * Reserved for future use.
	 */
	RESERVED_51 (51)
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			throw new RuntimeException("Reserved serializer operation");
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			throw new RuntimeException("Reserved serializer operation");
		}
	},

	/**
	 * Reserved for future use.
	 */
	RESERVED_52 (52)
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			throw new RuntimeException("Reserved serializer operation");
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			throw new RuntimeException("Reserved serializer operation");
		}
	},

	/**
	 * Reserved for future use.
	 */
	RESERVED_53 (53)
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			throw new RuntimeException("Reserved serializer operation");
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			throw new RuntimeException("Reserved serializer operation");
		}
	},

	/**
	 * Reserved for future use.
	 */
	RESERVED_54 (54)
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			throw new RuntimeException("Reserved serializer operation");
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			throw new RuntimeException("Reserved serializer operation");
		}
	},

	/**
	 * Reserved for future use.
	 */
	RESERVED_55 (55)
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			throw new RuntimeException("Reserved serializer operation");
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			throw new RuntimeException("Reserved serializer operation");
		}
	},

	/**
	 * Reserved for future use.
	 */
	RESERVED_56 (56)
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			throw new RuntimeException("Reserved serializer operation");
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			throw new RuntimeException("Reserved serializer operation");
		}
	},

	/**
	 * Reserved for future use.
	 */
	RESERVED_57 (57)
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			throw new RuntimeException("Reserved serializer operation");
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			throw new RuntimeException("Reserved serializer operation");
		}
	},

	/**
	 * Reserved for future use.
	 */
	RESERVED_58 (58)
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			throw new RuntimeException("Reserved serializer operation");
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			throw new RuntimeException("Reserved serializer operation");
		}
	},

	/**
	 * A {@linkplain FiberTypeDescriptor fiber type}.
	 */
	FIBER_TYPE (59,
		OBJECT_REFERENCE.as("Result type"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			return array(object.resultType());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_Type resultType = subobjects[0];
			return FiberTypeDescriptor.forResultType(resultType);
		}
	},

	/**
	 * A {@linkplain FunctionTypeDescriptor function type}.
	 */
	FUNCTION_TYPE (60,
		OBJECT_REFERENCE.as("Arguments tuple type"),
		OBJECT_REFERENCE.as("Return type"),
		TUPLE_OF_OBJECTS.as("Checked exceptions"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			return array(
				object.argsTupleType(),
				object.returnType(),
				object.declaredExceptions().asTuple());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_Type argsTupleType = subobjects[0];
			final A_Type returnType = subobjects[1];
			final A_Tuple checkedExceptionsTuple = subobjects[2];
			return FunctionTypeDescriptor.createWithArgumentTupleType(
				argsTupleType,
				returnType,
				checkedExceptionsTuple.asSet());
		}
	},

	/**
	 * A {@linkplain TupleTypeDescriptor tuple type}.
	 */
	TUPLE_TYPE (61,
		OBJECT_REFERENCE.as("Tuple sizes"),
		TUPLE_OF_OBJECTS.as("Leading types"),
		OBJECT_REFERENCE.as("Default type"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			return array (
				object.sizeRange(),
				object.typeTuple(),
				object.defaultType());
		}

		@Override
		A_BasicObject compose (
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
	INTEGER_RANGE_TYPE (62,
		BYTE.as("Inclusive flags"),
		OBJECT_REFERENCE.as("Lower bound"),
		OBJECT_REFERENCE.as("Upper bound"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			final int flags = (object.lowerInclusive() ? 1 : 0)
				+ (object.upperInclusive() ? 2 : 0);
			return array(
				IntegerDescriptor.fromInt(flags),
				object.lowerBound(),
				object.upperBound());
		}

		@Override
		A_BasicObject compose (
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
	 * Reserved for future use.
	 */
	RESERVED_63 (63)
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			throw new RuntimeException("Reserved serializer operation");
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			throw new RuntimeException("Reserved serializer operation");
		}
	},

	/**
	 * A {@linkplain PojoTypeDescriptor pojo type} for which {@linkplain
	 * AvailObject#isPojoFusedType()} is false.  This indicates a representation
	 * with a juicy class filling, which allows a particularly compact
	 * representation involving the class name and its parameter types.
	 *
	 * <p>
	 * A self pojo type may appear in the parameterization of this class.
	 * Convert such a self type into a 1-tuple containing the self type's class
	 * name.  We can't rely on a self pojo type being able to create a proxy for
	 * itself during serialization, because it is required to be equal to the
	 * (non-self) type which it parameterizes, leading to problems when
	 * encountering the self type during tracing.
	 * </p>
	 */
	UNFUSED_POJO_TYPE (64,
		OBJECT_REFERENCE.as("class name"),
		TUPLE_OF_OBJECTS.as("class parameterization"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			assert object.isPojoType();
			assert !object.isPojoFusedType();
			final AvailObject rawPojoType = object.javaClass();
			final Class<?> baseClass =
				(Class<?>)rawPojoType.javaObjectNotNull();
			final A_String className =
				StringDescriptor.from(baseClass.getName());
			final A_Map ancestorMap = object.javaAncestors();
			final A_Tuple myParameters = ancestorMap.mapAt(rawPojoType);
			final List<A_BasicObject> processedParameters =
				new ArrayList<>(myParameters.tupleSize());
			for (final A_Type parameter : myParameters)
			{
				assert !parameter.isTuple();
				if (parameter.isPojoSelfType())
				{
					processedParameters.add(
						SelfPojoTypeDescriptor.toSerializationProxy(parameter));
				}
				else
				{
					processedParameters.add(parameter);
				}
			}
			return array(
				className,
				TupleDescriptor.fromList(processedParameters));
		}

		@Override
		A_BasicObject compose (
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
				final List<AvailObject> processedParameters =
					new ArrayList<>(subobjects[1].tupleSize());
				for (final AvailObject parameter : subobjects[1])
				{
					if (parameter.isTuple())
					{
						processedParameters.add(
							SelfPojoTypeDescriptor.fromSerializationProxy(
								parameter,
								classLoader));
					}
					else
					{
						processedParameters.add(parameter);
					}
				}
				return PojoTypeDescriptor.forClassWithTypeArguments(
					baseClass,
					TupleDescriptor.fromList(processedParameters));
			}
			catch (final ClassNotFoundException e)
			{
				throw new RuntimeException(e);
			}
		}
	},

	/**
	 * A {@linkplain PojoTypeDescriptor pojo type} for which {@linkplain
	 * AvailObject#isPojoFusedType()} is true.  This indicates a representation
	 * without the juicy class filling, so we have to say how each ancestor is
	 * parameterized.
	 *
	 * <p>
	 * We have to pre-convert self pojo types in the parameterizations map,
	 * otherwise one might be encountered during traversal.  This is bad because
	 * the self pojo type can be equal to another (non-self) pojo type, and in
	 * fact almost certainly will be equal to a previously encountered object
	 * (a pojo type that it's embedded in), so the serializer will think this is
	 * a cyclic structure.  To avoid this, we convert any occurrence of a self
	 * type into a tuple of size one, containing the name of the java class or
	 * interface name.  This is enough to reconstruct the self pojo type.
	 * </p>
	 */
	FUSED_POJO_TYPE (65,
		GENERAL_MAP.as("ancestor parameterizations map"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			assert object.isPojoType();
			assert object.isPojoFusedType();
			A_Map symbolicMap = MapDescriptor.empty();
			for (final MapDescriptor.Entry entry
				: object.javaAncestors().mapIterable())
			{
				final Class<?> baseClass =
					(Class<?>)entry.key().javaObjectNotNull();
				final A_String className =
					StringDescriptor.from(baseClass.getName());
				final List<A_BasicObject> processedParameters =
					new ArrayList<>(entry.value().tupleSize());
				for (final AvailObject parameter : entry.value())
				{
					assert !parameter.isTuple();
					if (parameter.isPojoSelfType())
					{
						processedParameters.add(
							SelfPojoTypeDescriptor.toSerializationProxy(
								parameter));
					}
					else
					{
						processedParameters.add(parameter);
					}
				}
				symbolicMap = symbolicMap.mapAtPuttingCanDestroy(
					className,
					TupleDescriptor.fromList(processedParameters),
					true);
			}
			return array(symbolicMap);
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final ClassLoader classLoader =
				deserializer.runtime().classLoader();
			A_Map ancestorMap = MapDescriptor.empty();
			try
			{
				for (final MapDescriptor.Entry entry
					: subobjects[0].mapIterable())
				{
					final Class<?> baseClass = Class.forName(
						entry.key().asNativeString(),
						true,
						classLoader);
					final AvailObject rawPojo =
						RawPojoDescriptor.equalityWrap(baseClass);
					final List<AvailObject> processedParameters =
						new ArrayList<>(entry.value().tupleSize());
					for (final AvailObject parameter : entry.value())
					{
						if (parameter.isTuple())
						{
							processedParameters.add(
								SelfPojoTypeDescriptor.fromSerializationProxy(
									parameter,
									classLoader));
						}
						else
						{
							processedParameters.add(parameter);
						}
					}
					ancestorMap = ancestorMap.mapAtPuttingCanDestroy(
						rawPojo,
						TupleDescriptor.fromList(processedParameters),
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
	ARRAY_POJO_TYPE (66,
		OBJECT_REFERENCE.as("content type"),
		OBJECT_REFERENCE.as("size range"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			assert object.isPojoArrayType();
			final A_Type contentType = object.contentType();
			final A_Type sizeRange = object.sizeRange();
			return array(contentType, sizeRange);
		}

		@Override
		A_BasicObject compose (
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
	 * A {@linkplain SetDescriptor set} of {@linkplain StringDescriptor class
	 * names} standing in for a {@linkplain PojoTypeDescriptor pojo type}
	 * representing a "self type".  A self type is used for for parameterizing a
	 * Java class by itself.  For example, in the parametric type {@code
	 * Enum<E extends Enum<E>>}, we parameterize the class {@code Enum} with
	 * such a self type.  To reconstruct a self type all we need is a way to get
	 * to the raw Java classes involved, so we serialize their names.
	 */
	SELF_POJO_TYPE_REPRESENTATIVE (67,
		TUPLE_OF_OBJECTS.as("class names"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			throw new RuntimeException(
				"Can't serialize a self pojo type directly");
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			throw new RuntimeException(
				"Can't serialize a self pojo type directly");
		}
	},

	/**
	 * The bottom {@linkplain PojoTypeDescriptor pojo type}, representing
	 * the most specific type of pojo.
	 */
	BOTTOM_POJO_TYPE (68)
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			return array();
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return BottomPojoTypeDescriptor.pojoBottom();
		}
	},

	/**
	 * The bottom {@linkplain PojoTypeDescriptor pojo type}, representing
	 * the most specific type of pojo.
	 */
	COMPILED_CODE_TYPE (69,
		OBJECT_REFERENCE.as("function type for code type"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			return array(object.functionType());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return CompiledCodeTypeDescriptor.forFunctionType(subobjects[0]);
		}
	},

	/**
	 * The bottom {@linkplain PojoTypeDescriptor pojo type}, representing
	 * the most specific type of pojo.
	 */
	CONTINUATION_TYPE (70,
		OBJECT_REFERENCE.as("function type for continuation type"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			return array(object.functionType());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return ContinuationTypeDescriptor.forFunctionType(subobjects[0]);
		}
	},

	/**
	 * An Avail {@link EnumerationTypeDescriptor enumeration}, a type that has
	 * an explicit finite list of its instances.
	 */
	ENUMERATION_TYPE (71,
		TUPLE_OF_OBJECTS.as("set of instances"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			return array(object.instances().asTuple());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return AbstractEnumerationTypeDescriptor.withInstances(
				subobjects[0].asSet());
		}
	},

	/**
	 * An Avail {@link InstanceTypeDescriptor singular enumeration}, a type that
	 * has a single (non-type) instance.
	 */
	INSTANCE_TYPE (72,
		OBJECT_REFERENCE.as("type's instance"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			return array(object.instance());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return InstanceTypeDescriptor.on(subobjects[0]);
		}
	},

	/**
	 * An Avail {@link InstanceMetaDescriptor instance meta}, a type that
	 * has an instance i, which is itself a type.  Subtypes of type i are also
	 * considered instances of this instance meta.
	 */
	INSTANCE_META (73,
		OBJECT_REFERENCE.as("meta's instance"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			return array(object.instance());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return InstanceMetaDescriptor.on(subobjects[0]);
		}
	},

	/**
	 * A {@linkplain SetTypeDescriptor set type}.
	 */
	SET_TYPE (74,
		OBJECT_REFERENCE.as("size range"),
		OBJECT_REFERENCE.as("element type"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			return array (
				object.sizeRange(),
				object.contentType());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final AvailObject sizeRange = subobjects[0];
			final AvailObject contentType = subobjects[1];
			return SetTypeDescriptor.setTypeForSizesContentType(
				sizeRange,
				contentType);
		}
	},

	/**
	 * A {@linkplain MapTypeDescriptor map type}.
	 */
	MAP_TYPE (75,
		OBJECT_REFERENCE.as("size range"),
		OBJECT_REFERENCE.as("key type"),
		OBJECT_REFERENCE.as("value type"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			return array (
				object.sizeRange(),
				object.keyType(),
				object.valueType());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final AvailObject sizeRange = subobjects[0];
			final AvailObject keyType = subobjects[1];
			final AvailObject valueType = subobjects[2];
			return MapTypeDescriptor.mapTypeForSizesKeyTypeValueType(
				sizeRange,
				keyType,
				valueType);
		}
	},

	/**
	 * A {@linkplain LiteralTokenTypeDescriptor literal token type}.
	 */
	LITERAL_TOKEN_TYPE (76,
		OBJECT_REFERENCE.as("literal type"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			return array (
				object.literalType());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return LiteralTokenTypeDescriptor.create(subobjects[0]);
		}
	},

	/**
	 * A {@linkplain ParseNodeTypeDescriptor parse node type}.
	 */
	PARSE_NODE_TYPE (77,
		BYTE.as("kind"),
		OBJECT_REFERENCE.as("expression type"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			return array (
				IntegerDescriptor.fromInt(object.parseNodeKind().ordinal()),
				object.expressionType());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final int parseNodeKindOrdinal = subobjects[0].extractInt();
			final AvailObject expressionType = subobjects[1];
			final ParseNodeKind parseNodeKind =
				ParseNodeKind.all()[parseNodeKindOrdinal];
			return parseNodeKind.create(expressionType);
		}
	},

	/**
	 * A {@linkplain VariableTypeDescriptor variable type} for which the read
	 * type and write type are equal.
	 */
	SIMPLE_VARIABLE_TYPE (78,
		OBJECT_REFERENCE.as("content type"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			final A_Type readType = object.readType();
			assert readType.equals(object.writeType());
			return array (
				readType);
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return VariableTypeDescriptor.wrapInnerType(subobjects[0]);
		}
	},

	/**
	 * A {@linkplain ReadWriteVariableTypeDescriptor variable type} for which
	 * the read type and write type are (actually) unequal.
	 */
	READ_WRITE_VARIABLE_TYPE (79,
		OBJECT_REFERENCE.as("read type"),
		OBJECT_REFERENCE.as("write type"))
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			final A_Type readType = object.readType();
			final A_Type writeType = object.writeType();
			assert !readType.equals(writeType);
			return array (
				readType,
				writeType);
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return VariableTypeDescriptor.fromReadAndWriteTypes(
				subobjects[0],
				subobjects[1]);
		}
	},

	/**
	 * The {@linkplain BottomTypeDescriptor bottom type}, more specific than all
	 * other types.
	 */
	BOTTOM_TYPE (80)
	{
		@Override
		A_BasicObject[] decompose (final AvailObject object)
		{
			return array();
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return BottomPojoTypeDescriptor.pojoBottom();
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
	SerializerOperand[] operands ()
	{
		return operands;
	}

	/**
	 * Answer whether this operation is the serialization of a {@linkplain
	 * VariableDescriptor variable}.
	 *
	 * @return false (true in the relevant enumeration values).
	 */
	boolean isVariable ()
	{
		return false;
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
	abstract A_BasicObject[] decompose (
		final AvailObject object);

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
	abstract A_BasicObject compose (
		final AvailObject[] subobjects,
		final Deserializer deserializer);

	/**
	 * Write the given {@link AvailObject} to the {@link Serializer}.  It
	 * must have already been fully traced.
	 *
	 * @param object The already traced {@code AvailObject} to serialize.
	 * @param serializer Where to serialize it.
	 */
	void writeObject (
		final AvailObject object,
		final Serializer serializer)
	{
		serializer.writeByte(ordinal());
		final A_BasicObject[] decomposed = decompose(object);
		assert decomposed.length == operands.length;
		for (int i = 0; i < decomposed.length; i++)
		{
			operands[i].write((AvailObject)decomposed[i], serializer);
		}
	}

	/**
	 * @param atomName
	 * @param moduleName
	 * @param deserializer
	 * @return
	 */
	A_Atom lookupAtom (
		final A_String atomName,
		final A_String moduleName,
		final Deserializer deserializer)
	{
		final A_Module currentModule = deserializer.currentModule();
		assert currentModule != null;
		if (moduleName.equals(currentModule.moduleName()))
		{
			// An atom in the current module.  Create it if necessary.
			// Check if it's already defined somewhere...
			final A_Set trueNames =
				currentModule.trueNamesForStringName(atomName);
			if (trueNames.setSize() == 1)
			{
				return trueNames.asTuple().tupleAt(1);
			}
			final A_Atom atom = AtomWithPropertiesDescriptor.create(
				atomName, currentModule);
			atom.makeImmutable();
			currentModule.addPrivateName(atom);
			return atom;
		}
		// An atom in an imported module.
		final A_Module module = deserializer.moduleNamed(moduleName);
		final A_Map newNames = module.newNames();
		if (newNames.hasKey(atomName))
		{
			return newNames.mapAt(atomName);
		}
		final A_Map privateNames = module.privateNames();
		if (privateNames.hasKey(atomName))
		{
			final A_Set candidates = privateNames.mapAt(atomName);
			if (candidates.setSize() == 1)
			{
				return candidates.asTuple().tupleAt(1);
			}
			if (candidates.setSize() > 1)
			{
				throw new RuntimeException(
					String.format(
						"Ambiguous atom \"%s\" in module %s",
						atomName,
						module));
			}
		}
		// This should probably fail more gracefully.
		throw new RuntimeException(
			String.format(
				"Unknown atom %s in module %s",
				atomName,
				module));
	}

	/**
	 * Read an {@link AvailObject} from the {@link Deserializer}.  Its
	 * predecessors must already have been fully assembled.
	 *
	 * @param deserializer
	 *            The {@code Deserializer} from which to read an object.
	 */
	static void readObject (final Deserializer deserializer)
	{
		final int ordinal = deserializer.readByte();
		final SerializerOperation operation = values()[ordinal];
		final SerializerOperand[] operands = operation.operands();
		final AvailObject[] subobjects = new AvailObject[operands.length];
		for (int i = 0; i < operands.length; i++)
		{
			subobjects[i] = operands[i].read(deserializer);
		}
		final A_BasicObject newObject =
			operation.compose(subobjects, deserializer);
		newObject.makeImmutable();
		deserializer.addObject((AvailObject)newObject);
	}

	/**
	 * This helper function takes a variable number of arguments as an array,
	 * and conveniently returns that array.  This is syntactically <em>much</em>
	 * cleaner than any built-in array building syntax.
	 *
	 * @param objects The {@link AvailObject}s.
	 * @return The same array of {@code AvailObject}s.
	 */
	static A_BasicObject[] array (
		final A_BasicObject... objects)
	{
		return objects;
	}
}
