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
			final int shortValue = object.extractShort();
			return array(
				IntegerDescriptor.fromInt((shortValue >> 8) & 0xFF),
				IntegerDescriptor.fromInt(shortValue & 0xFF));
		}

		@Override
		AvailObject compose (
			final @NotNull AvailObject[] subobjects,
			final @NotNull Deserializer deserializer)
		{
			final int intValue = (subobjects[0].extractByte() << 8)
				+ subobjects[1].extractByte();
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
				(subobjects[0].extractByte() << 16)
				+ (subobjects[1].extractByte() << 8)
				+ subobjects[2].extractByte());
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
		COMPRESSED_SHORT_CHARACTER_TUPLE.as("BMP string"))
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
	}
	;


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
