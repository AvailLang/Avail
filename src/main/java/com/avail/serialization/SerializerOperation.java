/*
 * SerializerOperation.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

import com.avail.AvailRuntime;
import com.avail.descriptor.*;
import com.avail.descriptor.AtomDescriptor.SpecialAtom;
import com.avail.descriptor.DeclarationPhraseDescriptor.DeclarationKind;
import com.avail.descriptor.MapDescriptor.Entry;
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind;
import com.avail.exceptions.AvailRuntimeException;
import com.avail.exceptions.MalformedMessageException;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.primitive.pojos.P_CreatePojoConstructorFunction;
import com.avail.interpreter.primitive.pojos.P_CreatePojoInstanceMethodFunction;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.avail.AvailRuntime.specialObject;
import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.AssignmentPhraseDescriptor.isInline;
import static com.avail.descriptor.AssignmentPhraseDescriptor.newAssignment;
import static com.avail.descriptor.AtomDescriptor.SpecialAtom.EXPLICIT_SUBCLASSING_KEY;
import static com.avail.descriptor.AtomDescriptor.SpecialAtom.HERITABLE_KEY;
import static com.avail.descriptor.AtomDescriptor.trueObject;
import static com.avail.descriptor.AtomWithPropertiesDescriptor.createAtomWithProperties;
import static com.avail.descriptor.BlockPhraseDescriptor.newBlockNode;
import static com.avail.descriptor.BottomPojoTypeDescriptor.pojoBottom;
import static com.avail.descriptor.CharacterDescriptor.fromCodePoint;
import static com.avail.descriptor.CommentTokenDescriptor.newCommentToken;
import static com.avail.descriptor.CompiledCodeDescriptor.newCompiledCode;
import static com.avail.descriptor.CompiledCodeTypeDescriptor.compiledCodeTypeForFunctionType;
import static com.avail.descriptor.ContinuationDescriptor.createContinuationWithFrame;
import static com.avail.descriptor.ContinuationTypeDescriptor.continuationTypeForFunctionType;
import static com.avail.descriptor.DeclarationPhraseDescriptor.newDeclaration;
import static com.avail.descriptor.DoubleDescriptor.fromDouble;
import static com.avail.descriptor.ExpressionAsStatementPhraseDescriptor.newExpressionAsStatement;
import static com.avail.descriptor.FiberTypeDescriptor.fiberType;
import static com.avail.descriptor.FirstOfSequencePhraseDescriptor.newFirstOfSequenceNode;
import static com.avail.descriptor.FloatDescriptor.fromFloat;
import static com.avail.descriptor.FunctionDescriptor.createFunction;
import static com.avail.descriptor.FunctionTypeDescriptor.functionTypeFromArgumentTupleType;
import static com.avail.descriptor.InstanceMetaDescriptor.instanceMeta;
import static com.avail.descriptor.InstanceTypeDescriptor.instanceType;
import static com.avail.descriptor.IntegerDescriptor.*;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.integerRangeType;
import static com.avail.descriptor.ListPhraseDescriptor.newListNode;
import static com.avail.descriptor.ListPhraseTypeDescriptor.createListNodeType;
import static com.avail.descriptor.LiteralPhraseDescriptor.literalNodeFromToken;
import static com.avail.descriptor.LiteralTokenDescriptor.literalToken;
import static com.avail.descriptor.LiteralTokenTypeDescriptor.literalTokenType;
import static com.avail.descriptor.MacroSubstitutionPhraseDescriptor.newMacroSubstitution;
import static com.avail.descriptor.MapDescriptor.emptyMap;
import static com.avail.descriptor.MapTypeDescriptor.mapTypeForSizesKeyTypeValueType;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.ObjectDescriptor.objectFromMap;
import static com.avail.descriptor.ObjectTupleDescriptor.*;
import static com.avail.descriptor.ObjectTypeDescriptor.objectTypeFromMap;
import static com.avail.descriptor.PermutedListPhraseDescriptor.newPermutedListNode;
import static com.avail.descriptor.PojoFieldDescriptor.pojoFieldVariableForInnerType;
import static com.avail.descriptor.PojoTypeDescriptor.*;
import static com.avail.descriptor.PrimitiveTypeDescriptor.extractOrdinal;
import static com.avail.descriptor.RawPojoDescriptor.equalityPojo;
import static com.avail.descriptor.RawPojoDescriptor.rawNullPojo;
import static com.avail.descriptor.ReferencePhraseDescriptor.referenceNodeFromUse;
import static com.avail.descriptor.SelfPojoTypeDescriptor.pojoFromSerializationProxy;
import static com.avail.descriptor.SelfPojoTypeDescriptor.pojoSerializationProxy;
import static com.avail.descriptor.SendPhraseDescriptor.newSendNode;
import static com.avail.descriptor.SequencePhraseDescriptor.newSequence;
import static com.avail.descriptor.SetTypeDescriptor.setTypeForSizesContentType;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.descriptor.SuperCastPhraseDescriptor.newSuperCastNode;
import static com.avail.descriptor.TokenDescriptor.TokenType.lookupTokenType;
import static com.avail.descriptor.TokenDescriptor.newToken;
import static com.avail.descriptor.TokenTypeDescriptor.tokenType;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleDescriptor.toList;
import static com.avail.descriptor.TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType;
import static com.avail.descriptor.VariableDescriptor.newVariableWithOuterType;
import static com.avail.descriptor.VariableTypeDescriptor.variableReadWriteType;
import static com.avail.descriptor.VariableTypeDescriptor.variableTypeFor;
import static com.avail.descriptor.VariableUsePhraseDescriptor.newUse;
import static com.avail.exceptions.AvailErrorCode.E_JAVA_METHOD_NOT_AVAILABLE;
import static com.avail.interpreter.Primitive.primitiveByName;
import static com.avail.interpreter.levelTwo.L2Chunk.ChunkEntryPoint.TO_RESTART;
import static com.avail.interpreter.levelTwo.L2Chunk.ChunkEntryPoint.TO_RETURN_INTO;
import static com.avail.interpreter.levelTwo.L2Chunk.unoptimizedChunk;
import static com.avail.serialization.SerializerOperandEncoding.*;
import static com.avail.utility.Nulls.stripNull;
import static java.lang.Double.doubleToRawLongBits;
import static java.lang.Double.longBitsToDouble;
import static java.lang.Float.floatToRawIntBits;
import static java.lang.Float.intBitsToFloat;
import static java.lang.String.format;

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
	ZERO_INTEGER(0)
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array();
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return zero();
		}
	},

	/**
	 * The Avail integer 1.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	ONE_INTEGER(1)
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array();
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return one();
		}
	},

	/**
	 * The Avail integer 2.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	TWO_INTEGER(2)
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array();
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return two();
		}
	},

	/**
	 * The Avail integer 3.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	THREE_INTEGER(3)
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array();
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return fromUnsignedByte((short) 3);
		}
	},

	/**
	 * The Avail integer 4.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	FOUR_INTEGER(4)
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array();
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return fromUnsignedByte((short) 4);
		}
	},

	/**
	 * The Avail integer 5.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	FIVE_INTEGER(5)
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array();
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return fromUnsignedByte((short) 5);
		}
	},

	/**
	 * The Avail integer 6.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	SIX_INTEGER(6)
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array();
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return fromUnsignedByte((short) 6);
		}
	},

	/**
	 * The Avail integer 7.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	SEVEN_INTEGER(7)
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array();
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return fromUnsignedByte((short) 7);
		}
	},

	/**
	 * The Avail integer 8.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	EIGHT_INTEGER(8)
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array();
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return fromUnsignedByte((short) 8);
		}
	},

	/**
	 * The Avail integer 9.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	NINE_INTEGER(9)
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array();
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return fromUnsignedByte((short) 9);
		}
	},

	/**
	 * The Avail integer 10.  Note that there are no operands, since the value
	 * is encoded in the choice of instruction itself.
	 */
	TEN_INTEGER(10)
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array();
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return fromUnsignedByte((short) 10);
		}
	},

	/**
	 * An Avail integer in the range 11..255.  Note that 0..10 have their own
	 * special cases already which require very little space.
	 */
	BYTE_INTEGER(11, BYTE.as("only byte"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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

		@Override
		void describe (
			final DeserializerDescriber describer)
		{
			describer.append(this.name());
			describer.append(" = ");
			describer.append(operands[0].read(describer).toString());
		}
	},

	/**
	 * An Avail integer in the range 256..65535.  Note that 0..255 have their
	 * own special cases already which require less space.  Don't try to
	 * compress the short value for this reason.
	 */
	SHORT_INTEGER(12, UNCOMPRESSED_SHORT.as("the unsigned short"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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

		@Override
		void describe (
			final DeserializerDescriber describer)
		{
			describer.append(this.name());
			describer.append(" = ");
			describer.append(operands[0].read(describer).toString());
		}
	},

	/**
	 * An Avail integer in the range -2<sup>31</sup> through 2<sup>31</sup>-1,
	 * except the range 0..65535 which have their own special cases already.
	 */
	INT_INTEGER(13, SIGNED_INT.as("int's value"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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

		@Override
		void describe (
			final DeserializerDescriber describer)
		{
			describer.append(this.name());
			describer.append(" = ");
			describer.append(operands[0].read(describer).toString());
		}
	},

	/**
	 * An Avail integer that cannot be represented as an {@code int}.
	 */
	BIG_INTEGER(14, BIG_INTEGER_DATA.as("constituent ints"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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

		@Override
		void describe (
			final DeserializerDescriber describer)
		{
			describer.append(this.name());
			describer.append(" = ");
			describer.append(operands[0].read(describer).toString());
		}
	},


	/**
	 * Produce the Avail {@linkplain NilDescriptor#nil nil} during
	 * deserialization.
	 */
	NIL(15)
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array();
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return nil;
		}
	},

	/**
	 * This special opcode causes a previously built object to be produced as an
	 * actual checkpoint output from the {@link Deserializer}.
	 */
	CHECKPOINT(16, OBJECT_REFERENCE.as("object to checkpoint"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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
	SPECIAL_OBJECT(17, COMPRESSED_SHORT.as("special object number"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array(
				fromInt(Serializer.indexOfSpecialObject(object)));
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return specialObject(subobjects[0].extractInt());
		}

		@Override
		void describe (
			final DeserializerDescriber describer)
		{
			describer.append(this.name());
			final A_Number specialNumber = operands[0].read(describer);
			final int specialIndex = specialNumber.extractInt();
			describer.append(" (");
			describer.append(Integer.toString(specialIndex));
			describer.append(") = ");
			describer.append(specialObject(specialIndex).toString());
		}
	},

	/**
	 * One of the special atoms that the {@link AvailRuntime} maintains.
	 */
	SPECIAL_ATOM(18, COMPRESSED_SHORT.as("special atom number"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array(
				fromInt(Serializer.indexOfSpecialAtom(object)));
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return Deserializer.specialAtom(subobjects[0].extractInt());
		}

		@Override
		void describe (
			final DeserializerDescriber describer)
		{
			describer.append(this.name());
			final A_Number specialNumber = operands[0].read(describer);
			final int specialIndex = specialNumber.extractInt();
			describer.append(" (");
			describer.append(Integer.toString(specialIndex));
			describer.append(") = ");
			describer.append(Deserializer.specialAtom(specialIndex).toString());
		}
	},

	/**
	 * A {@linkplain CharacterDescriptor character} whose code point fits in an
	 * unsigned byte (0..255).
	 */
	BYTE_CHARACTER(19, BYTE.as("Latin-1 code point"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array(
				fromInt(object.codePoint()));
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return fromCodePoint(
				subobjects[0].extractInt());
		}
	},

	/**
	 * A {@linkplain CharacterDescriptor character} whose code point requires an
	 * unsigned short (256..65535).
	 */
	SHORT_CHARACTER(20, UNCOMPRESSED_SHORT.as("BMP code point"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array(
				fromInt(object.codePoint()));
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return fromCodePoint(
				subobjects[0].extractInt());
		}
	},

	/**
	 * A {@linkplain CharacterDescriptor character} whose code point requires
	 * three bytes to represent (0..16777215, but technically only 0..1114111).
	 */
	LARGE_CHARACTER(
		21,
		BYTE.as("SMP codepoint high byte"),
		BYTE.as("SMP codepoint middle byte"),
		BYTE.as("SMP codepoint low byte"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			final int codePoint = object.codePoint();
			return array(
				fromInt((codePoint >> 16) & 0xFF),
				fromInt((codePoint >> 8) & 0xFF),
				fromInt(codePoint & 0xFF));
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return fromCodePoint(
				(subobjects[0].extractUnsignedByte() << 16)
				+ (subobjects[1].extractUnsignedByte() << 8)
				+ subobjects[2].extractUnsignedByte());
		}
	},

	/**
	 * A {@linkplain FloatDescriptor float}.  Convert the raw bits to an int
	 * for writing.
	 */
	FLOAT(22, SIGNED_INT.as("raw bits"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			final float floatValue = object.extractFloat();
			final int floatBits = floatToRawIntBits(floatValue);
			return array(
				fromInt(floatBits));
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final int floatBits = subobjects[0].extractInt();
			final float floatValue = intBitsToFloat(floatBits);
			return fromFloat(floatValue);
		}

		@Override
		void describe (
			final DeserializerDescriber describer)
		{
			describer.append(this.name());
			describer.append(" = ");
			final A_Number floatAsIntNumber = operands[0].read(describer);
			final int floatBits = floatAsIntNumber.extractInt();
			describer.append(Float.toString(intBitsToFloat(floatBits)));
		}
	},

	/**
	 * A {@linkplain DoubleDescriptor double}.  Convert the raw bits to a long
	 * and write it in big endian.
	 */
	DOUBLE(
		23,
		SIGNED_INT.as("upper raw bits"),
		SIGNED_INT.as("lower raw bits"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			final double doubleValue = object.extractDouble();
			final long doubleBits = doubleToRawLongBits(doubleValue);
			return array(
				fromInt((int) (doubleBits >> 32)),
				fromInt((int) doubleBits));
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final int highBits = subobjects[0].extractInt();
			final int lowBits = subobjects[1].extractInt();
			final long doubleBits =
				(((long) highBits) << 32) + (lowBits & 0xFFFFFFFFL);
			final double doubleValue = longBitsToDouble(doubleBits);
			return fromDouble(doubleValue);
		}

		@Override
		void describe (
			final DeserializerDescriber describer)
		{
			describer.append(this.name());
			describer.append(" = ");
			final A_Number highBitsAsNumber = operands[0].read(describer);
			final A_Number lowBitsAsNumber = operands[1].read(describer);
			final int highBits = highBitsAsNumber.extractInt();
			final int lowBits = lowBitsAsNumber.extractInt();
			final long doubleBits =
				(((long) highBits) << 32) + (lowBits & 0xFFFFFFFFL);
			final double theDouble = longBitsToDouble(doubleBits);
			describer.append(Double.toString(theDouble));
		}
	},

	/**
	 * A {@linkplain TupleDescriptor tuple} of arbitrary objects.  Write the
	 * size of the tuple then the elements as object identifiers.
	 */
	GENERAL_TUPLE(24, TUPLE_OF_OBJECTS.as("tuple elements"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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
	BYTE_STRING(25, BYTE_CHARACTER_TUPLE.as("Latin-1 string"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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
	SHORT_STRING(
		26,
		COMPRESSED_SHORT_CHARACTER_TUPLE.as("Basic Multilingual Plane string"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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
	ARBITRARY_STRING(
		27,
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.as("arbitrary string"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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
	 * fall in the range 0..2^31-1.
	 */
	INT_TUPLE(28, COMPRESSED_INT_TUPLE.as("tuple of ints"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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
	BYTE_TUPLE(29, UNCOMPRESSED_BYTE_TUPLE.as("tuple of bytes"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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
	NYBBLE_TUPLE(30, UNCOMPRESSED_NYBBLE_TUPLE.as("tuple of nybbles"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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
	MAP(31, GENERAL_MAP.as("map contents"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array(object.fieldMap());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return objectFromMap(subobjects[0]);
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
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array(object.fieldTypeMap());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return objectTypeFromMap(subobjects[0]);
		}
	},

	/**
	 * An {@linkplain AtomDescriptor atom}.  Output the atom name and the name
	 * of the module that issued it.  Look up the corresponding atom during
	 * reconstruction, recreating it if it's not present and supposed to have
	 * been issued by the current module.
	 */
	ATOM(
		34,
		OBJECT_REFERENCE.as("atom name"),
		OBJECT_REFERENCE.as("module name"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			serializer.checkAtom(object);
			assert object.getAtomProperty(HERITABLE_KEY.atom).equalsNil();
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
	 * An {@linkplain AtomDescriptor atom}.  Output the atom name and the name
	 * of the module that issued it.  Look up the corresponding atom during
	 * reconstruction, recreating it if it's not present and supposed to have
	 * been issued by the current module.
	 */
	HERITABLE_ATOM(
		35,
		OBJECT_REFERENCE.as("atom name"),
		OBJECT_REFERENCE.as("module name"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			serializer.checkAtom(object);
			assert object.getAtomProperty(HERITABLE_KEY.atom).equals(
				trueObject());
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
			atom.setAtomProperty(HERITABLE_KEY.atom, trueObject());
			return atom.makeShared();
		}
	},

	/**
	 * A {@linkplain CompiledCodeDescriptor compiled code object}.  Output any
	 * information needed to reconstruct the compiled code object.
	 */
	COMPILED_CODE(
		36,
		COMPRESSED_SHORT.as("Total number of frame slots"),
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.as("Primitive name"),
		OBJECT_REFERENCE.as("Function type"),
		UNCOMPRESSED_NYBBLE_TUPLE.as("Level one nybblecodes"),
		TUPLE_OF_OBJECTS.as("Regular literals"),
		TUPLE_OF_OBJECTS.as("Local types"),
		TUPLE_OF_OBJECTS.as("Constant types"),
		TUPLE_OF_OBJECTS.as("Outer types"),
		OBJECT_REFERENCE.as("Module name"),
		UNSIGNED_INT.as("Line number"),
		COMPRESSED_INT_TUPLE.as("Encoded line number deltas"),
		OBJECT_REFERENCE.as("Originating phrase"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			final int numLocals = object.numLocals();
			final int numConstants = object.numConstants();
			final int numOuters = object.numOuters();
			final int numRegularLiterals =
				object.numLiterals() - numConstants - numLocals - numOuters;
			final A_Tuple regularLiterals = generateObjectTupleFrom(
				numRegularLiterals, object::literalAt);
			final A_Tuple localTypes = generateObjectTupleFrom(
				numLocals, object::localTypeAt);
			final A_Tuple constantTypes = generateObjectTupleFrom(
				numConstants, object::constantTypeAt);
			final A_Tuple outerTypes = generateObjectTupleFrom(
				numOuters, object::outerTypeAt);
			final A_Module module = object.module();
			final A_String moduleName = module.equalsNil()
				? emptyTuple()
				: module.moduleName();
			final @Nullable Primitive primitive = object.primitive();
			final A_String primName;
			if (primitive == null)
			{
				primName = emptyTuple();
			}
			else
			{
				primName = stringFrom(primitive.name());
			}
			return array(
				fromInt(object.numSlots()),
				primName,
				object.functionType(),
				object.nybbles(),
				regularLiterals,
				localTypes,
				constantTypes,
				outerTypes,
				moduleName,
				fromInt(object.startingLineNumber()),
				object.lineNumberEncodedDeltas(),
				object.originatingPhrase());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final int numSlots = subobjects[0].extractInt();
			final A_String primitive = subobjects[1];
			final A_Type functionType = subobjects[2];
			final A_Tuple nybbles = subobjects[3];
			final A_Tuple regularLiterals = subobjects[4];
			final A_Tuple localTypes = subobjects[5];
			final A_Tuple constantTypes = subobjects[6];
			final A_Tuple outerTypes = subobjects[7];
			final A_String moduleName = subobjects[8];
			final A_Number lineNumberInteger = subobjects[9];
			final A_Tuple lineNumberEncodedDeltas = subobjects[10];
			final A_Phrase originatingPhrase = subobjects[11];

			final A_Type numArgsRange =
				functionType.argsTupleType().sizeRange();
			final int numArgs = numArgsRange.lowerBound().extractInt();
			assert numArgsRange.upperBound().extractInt() == numArgs;
			final int numLocals = localTypes.tupleSize();
			final int numConstants = constantTypes.tupleSize();

			final A_Module module = moduleName.tupleSize() == 0
				? nil
				: deserializer.moduleNamed(moduleName);
			return newCompiledCode(
				nybbles,
				numSlots - numConstants - numLocals - numArgs,
				functionType,
				primitiveByName(primitive.asNativeString()),
				regularLiterals,
				localTypes,
				constantTypes,
				outerTypes,
				module,
				lineNumberInteger.extractInt(),
				lineNumberEncodedDeltas,
				originatingPhrase);
		}
	},

	/**
	 * A {@linkplain FunctionDescriptor function} with no outer (lexically
	 * captured) variables.
	 */
	CLEAN_FUNCTION(37, OBJECT_REFERENCE.as("Compiled code"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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
			return createFunction(code, emptyTuple());
		}
	},

	/**
	 * A {@linkplain FunctionDescriptor function} with one or more outer
	 * (lexically captured) variables.
	 */
	GENERAL_FUNCTION(
		38,
		OBJECT_REFERENCE.as("Compiled code"),
		TUPLE_OF_OBJECTS.as("Outer values"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			final A_Tuple outers = generateObjectTupleFrom(
				object.numOuterVars(), object::outerVarAt);
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
			return createFunction(code, outers);
		}
	},

	/**
	 * A non-global {@linkplain VariableDescriptor variable}.  Deserialization
	 * reconstructs a new one, since there's no way to know where the original
	 * one came from.
	 */
	LOCAL_VARIABLE(39, OBJECT_REFERENCE.as("variable type"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			assert !object.isGlobal();
			return array(
				object.kind());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return newVariableWithOuterType(subobjects[0]);
		}

		@Override
		boolean isVariableCreation ()
		{
			// This was a local variable, so answer true, indicating that we
			// also need to serialize the content.  If the variable had instead
			// been looked up (as for a GLOBAL_VARIABLE), we would answer false
			// to indicate that the variable had already been initialized
			// elsewhere.
			return true;
		}
	},

	/**
	 * A global variable.  It's serialized as the type, the module that defined
	 * it, the variable's name within that module, and a flag byte.
	 *
	 * <p>The flag byte has these fields:</p>
	 * <ol>
	 *     <li value="1">Whether this is a write-once variable.</li>
	 *     <li value="2">Whether the write-once variable's value was computed
	 *     by a stable computation.</li>
	 * </ol>
	 *
	 * <p>A global variable is marked as such when constructed by the compiler.
	 * The write-once flag is also specified at that time as well.  To
	 * reconstruct a global variable, it's simply looked up by name in its
	 * defining module.  The variable must exist (or the serialization is
	 * malformed), so modules must ensure all their global variables and
	 * constants are reconstructed when loading from serialized form.</p>
	 */
	GLOBAL_VARIABLE(
		40,
		OBJECT_REFERENCE.as("variable type"),
		OBJECT_REFERENCE.as("module name"),
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.as("variable name"),
		BYTE.as("flags"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			assert object.isGlobal();
			final int flags =
				(object.isInitializedWriteOnceVariable() ? 1 : 0)
				+ (object.valueWasStablyComputed() ? 2 : 0);
			return array(
				object.kind(),
				object.globalModule(),
				object.globalName(),
				fromInt(flags));
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_Type varType = subobjects[0];
			final A_Module module = subobjects[1];
			final A_String varName = subobjects[2];
			final int flags = subobjects[3].extractInt();

			final boolean writeOnce = (flags & 1) != 0;
			final boolean stablyComputed = (flags & 2) != 0;
			final A_Variable variable =
				writeOnce
					? module.constantBindings().mapAt(varName)
					: module.variableBindings().mapAt(varName);
			if (stablyComputed != variable.valueWasStablyComputed())
			{
				throw new RuntimeException(
					"Disagreement about whether a module constant was stably"
						+ " computed.");
			}
			if (!varType.equals(variable.kind()))
			{
				throw new RuntimeException(
					"Disagreement about global variable's type.");
			}
			return variable;
		}
	},

	/**
	 * A {@linkplain SetDescriptor set}.  Convert it to a tuple and work with
	 * that, converting it back to a set when deserializing.
	 */
	SET(41, TUPLE_OF_OBJECTS.as("tuple of objects"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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
	 * A {@linkplain TokenDescriptor token}.
	 */
	TOKEN(
		42,
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.as("token string"),
		SIGNED_INT.as("start position"),
		SIGNED_INT.as("line number"),
		BYTE.as("token type code"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array(
				object.string(),
				fromInt(object.start()),
				fromInt(object.lineNumber()),
				fromInt(object.tokenType().ordinal()));
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_String string = subobjects[0];
			final int start = subobjects[1].extractInt();
			final int lineNumber = subobjects[2].extractInt();
			final int tokenTypeOrdinal = subobjects[3].extractInt();
			return newToken(
				string,
				start,
				lineNumber,
				lookupTokenType(tokenTypeOrdinal));
		}
	},

	/**
	 * A {@linkplain LiteralTokenDescriptor literal token}.
	 */
	LITERAL_TOKEN(
		43,
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.as("token string"),
		OBJECT_REFERENCE.as("literal value"),
		SIGNED_INT.as("start position"),
		SIGNED_INT.as("line number"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array(
				object.string(),
				object.literal(),
				fromInt(object.start()),
				fromInt(object.lineNumber()));
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_String string = subobjects[0];
			final AvailObject literal = subobjects[1];
			final int start = subobjects[2].extractInt();
			final int lineNumber = subobjects[3].extractInt();
			return literalToken(
				string,
				start,
				lineNumber,
				literal);
		}
	},

	/**
	 * A {@linkplain TokenDescriptor token}.
	 */
	COMMENT_TOKEN(
		44,
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.as("token string"),
		SIGNED_INT.as("start position"),
		SIGNED_INT.as("line number"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array(
				object.string(),
				fromInt(object.start()),
				fromInt(object.lineNumber()));
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_String string = subobjects[0];
			final int start = subobjects[1].extractInt();
			final int lineNumber = subobjects[2].extractInt();
			return newCommentToken(string, start, lineNumber);
		}
	},

	/**
	 * This special opcode causes a previously built variable to have a
	 * previously built value to be assigned to it at this point during
	 * deserialization.
	 */
	ASSIGN_TO_VARIABLE(
		45,
		OBJECT_REFERENCE.as("variable to assign"),
		OBJECT_REFERENCE.as("value to assign"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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
			return nil;
		}
	},

	/**
	 * The representation of a continuation, which is just its level one state.
	 */
	CONTINUATION(
		46,
		OBJECT_REFERENCE.as("calling continuation"),
		OBJECT_REFERENCE.as("continuation's function"),
		TUPLE_OF_OBJECTS.as("continuation frame slots"),
		COMPRESSED_SHORT.as("program counter"),
		COMPRESSED_SHORT.as("stack pointer"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			final int frameSlotCount = object.numSlots();
			final List<AvailObject> frameSlotsList =
				new ArrayList<>(frameSlotCount);
			for (int i = 1; i <= frameSlotCount; i++)
			{
				frameSlotsList.add(object.argOrLocalOrStackAt(i));
			}
			return array(
				object.caller(),
				object.function(),
				tupleFromList(frameSlotsList),
				fromInt(object.pc()),
				fromInt(object.stackp()));
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
			final A_Continuation continuation =
				createContinuationWithFrame(
					function,
					caller,
					pcInteger.extractInt(),
					stackpInteger.extractInt(),
					unoptimizedChunk,
					pcInteger.equalsInt(0)
						? TO_RESTART.offsetInDefaultChunk
						: TO_RETURN_INTO.offsetInDefaultChunk,
					toList(frameSlots),
					0);
			return continuation.makeImmutable();
		}
	},

	/**
	 * A reference to a {@linkplain MethodDescriptor method} that should be
	 * looked up during deserialization.  A method can have multiple {@linkplain
	 * MessageBundleDescriptor message bundles}, and <em>each</em> &lt;module
	 * name, atom name&gt; pair is recorded during serialization.  For system
	 * atoms we output nil for the module name.  During deserialization, the
	 * list is searched for a module that has been loaded, and if the
	 * corresponding name is an atom, and if that atom has a bundle associated
	 * with it, that bundle's method is used.
	 */
	METHOD(47, TUPLE_OF_OBJECTS.as("module name / atom name pairs"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			assert object.isInstanceOf(Types.METHOD.o());
			final List<A_Tuple> pairs = new ArrayList<>();
			for (final A_Bundle bundle : object.bundles())
			{
				final A_Atom atom = bundle.message();
				final A_Module module = atom.issuingModule();
				if (!module.equalsNil())
				{
					pairs.add(
						tuple(module.moduleName(), atom.atomName()));
				}
				else
				{
					pairs.add(tuple(nil, atom.atomName()));
				}
			}
			return array(tupleFromList(pairs));
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_Tuple pairs = subobjects[0];
			final AvailRuntime runtime = deserializer.runtime();
			for (final A_Tuple pair : pairs)
			{
				assert pair.tupleSize() == 2;
				final A_String moduleName = pair.tupleAt(1);
				final A_String atomName = pair.tupleAt(2);
				if (!moduleName.equalsNil()
					&& runtime.includesModuleNamed(moduleName))
				{
					final A_Atom atom =
						lookupAtom(atomName, moduleName, deserializer);
					final A_Bundle bundle = atom.bundleOrNil();
					if (!bundle.equalsNil())
					{
						return bundle.bundleMethod();
					}
				}
			}
			// Look it up as a special atom instead.
			for (final A_Tuple pair : pairs)
			{
				assert pair.tupleSize() == 2;
				final A_String moduleName = pair.tupleAt(1);
				final A_String atomName = pair.tupleAt(2);
				if (moduleName.equalsNil())
				{
					final @Nullable A_Atom specialAtom =
						Serializer.specialAtomsByName.get(atomName);
					if (specialAtom != null)
					{
						final A_Bundle bundle = specialAtom.bundleOrNil();
						if (!bundle.equalsNil())
						{
							return bundle.bundleMethod();
						}
					}
					throw new RuntimeException(
						"Method could not be found by name as a special atom "
							+ "bundle");
				}
			}
			throw new RuntimeException(
				"None of method's bundle-defining modules were loaded");
		}
	},

	/**
	 * A reference to a {@linkplain MethodDefinitionDescriptor method
	 * definition}, which should be reconstructed by looking it up.
	 */
	METHOD_DEFINITION(
		48,
		OBJECT_REFERENCE.as("method"),
		OBJECT_REFERENCE.as("signature"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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
	MACRO_DEFINITION(
		49,
		OBJECT_REFERENCE.as("method"),
		OBJECT_REFERENCE.as("signature"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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
	ABSTRACT_DEFINITION(
		50,
		OBJECT_REFERENCE.as("method"),
		OBJECT_REFERENCE.as("signature"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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
	FORWARD_DEFINITION(
		51,
		OBJECT_REFERENCE.as("method"),
		OBJECT_REFERENCE.as("signature"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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
	MESSAGE_BUNDLE(52, OBJECT_REFERENCE.as("message atom"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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
	 * A reference to a module, possibly the one being constructed.
	 */
	MODULE(53, OBJECT_REFERENCE.as("module name"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array(
				object.moduleName());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_String moduleName = subobjects[0];
			final A_Module currentModule = deserializer.currentModule();
			if (currentModule.moduleName().equals(moduleName))
			{
				return currentModule;
			}
			return deserializer.runtime().moduleAt(moduleName);
		}
	},

	/**
	 * An {@linkplain AtomDescriptor atom} which is used for creating explicit
	 * subclasses.  Output the atom name and the name of the module that issued
	 * it.  Look up the corresponding atom during reconstruction, recreating it
	 * if it's not present and supposed to have been issued by the current
	 * module.
	 *
	 * <P>This should be the same as {@link #ATOM}, other than adding the
	 * special {@link SpecialAtom#EXPLICIT_SUBCLASSING_KEY} property.</P>
	 */
	EXPLICIT_SUBCLASS_ATOM(
		54,
		OBJECT_REFERENCE.as("atom name"),
		OBJECT_REFERENCE.as("module name"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			serializer.checkAtom(object);
			assert object.getAtomProperty(HERITABLE_KEY.atom)
				.equalsNil();
			assert object.getAtomProperty(EXPLICIT_SUBCLASSING_KEY.atom)
				.equals(EXPLICIT_SUBCLASSING_KEY.atom);
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
			atom.setAtomProperty(
				EXPLICIT_SUBCLASSING_KEY.atom,
				EXPLICIT_SUBCLASSING_KEY.atom);
			return atom.makeShared();
		}
	},

	/**
	 * The {@linkplain RawPojoDescriptor raw pojo} for the Java {@code null}
	 * value.
	 */
	RAW_POJO_NULL(55)
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array();
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return rawNullPojo();
		}
	},

	/**
	 * The serialization of a raw (unparameterized) Java {@linkplain
	 * Class#isPrimitive() non-primitive} {@link Class}.
	 */
	RAW_NONPRIMITIVE_JAVA_CLASS(
		56,
		OBJECT_REFERENCE.as("class name"))
		{
			@Override
			A_BasicObject[] decompose (
				final AvailObject object,
				final Serializer serializer)
			{
				final Class<?> javaClass = object.javaObjectNotNull();
				return array(
					stringFrom(javaClass.getName()));
			}

			@Override
			A_BasicObject compose (
				final AvailObject[] subobjects,
				final Deserializer deserializer)
			{
				final A_String className = subobjects[0];
				final Class<?> javaClass =
					deserializer.runtime().lookupRawJavaClass(className);
				return equalityPojo(javaClass);
			}
		},

	/**
	 * An instance of {@link Method}, likely created as part of {@link
	 * P_CreatePojoInstanceMethodFunction}. The method may be an instance method
	 * or a static method.
	 */
	RAW_POJO_METHOD(
		57,
		OBJECT_REFERENCE.as("declaring class pojo"),
		OBJECT_REFERENCE.as("method name string"),
		OBJECT_REFERENCE.as("marshaled argument pojo types"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			final Method method = object.javaObjectNotNull();
			final Class<?> declaringClass = method.getDeclaringClass();
			final String methodName = method.getName();
			final Class<?>[] argumentTypes = method.getParameterTypes();

			return array(
				equalityPojo(declaringClass),
				stringFrom(methodName),
				generateObjectTupleFrom(
					argumentTypes.length,
					i -> equalityPojo(argumentTypes[i - 1])));
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_Type receiverType = subobjects[0];
			final A_String methodName = subobjects[1];
			final A_Tuple argumentTypes = subobjects[2];

			final Class<?> receiverClass = receiverType.javaObjectNotNull();
			final Class<?>[] argumentClasses = marshalTypes(argumentTypes);
			try
			{
				return equalityPojo(
					receiverClass.getMethod(
						methodName.asNativeString(), argumentClasses));
			}
			catch (final NoSuchMethodException e)
			{
				throw new AvailRuntimeException(E_JAVA_METHOD_NOT_AVAILABLE);
			}
		}
	},

	/**
	 * An instance of {@link Constructor}, likely created as part of {@link
	 * P_CreatePojoConstructorFunction}.
	 */
	RAW_POJO_CONSTRUCTOR(
		58,
		OBJECT_REFERENCE.as("declaring class pojo"),
		OBJECT_REFERENCE.as("marshaled argument pojo types"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			final Constructor<?> constructor = object.javaObjectNotNull();
			final Class<?> declaringClass = constructor.getDeclaringClass();
			final Class<?>[] argumentTypes = constructor.getParameterTypes();

			return array(
				equalityPojo(declaringClass),
				generateObjectTupleFrom(
					argumentTypes.length,
					i -> equalityPojo(argumentTypes[i - 1])));
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_Type receiverType = subobjects[0];
			final A_Tuple argumentTypes = subobjects[1];

			final Class<?> receiverClass = receiverType.javaObjectNotNull();
			final Class<?>[] argumentClasses = marshalTypes(argumentTypes);
			try
			{
				return equalityPojo(
					receiverClass.getConstructor(argumentClasses));
			}
			catch (final NoSuchMethodException e)
			{
				throw new AvailRuntimeException(E_JAVA_METHOD_NOT_AVAILABLE);
			}
		}
	},

	/**
	 * The serialization of a raw (unparameterized) Java {@linkplain
	 * Class#isPrimitive() primitive} {@link Class}.
	 */
	RAW_PRIMITIVE_JAVA_CLASS(
		59,
		OBJECT_REFERENCE.as("primitive class name"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			final Class<?> javaClass = object.javaObjectNotNull();
			return array(
				stringFrom(javaClass.getName()));
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_String className = subobjects[0];
			final Class<?> javaClass;
			switch (className.asNativeString())
			{
				case "void":
					javaClass = Void.TYPE;
					break;
				case "boolean":
					javaClass = Boolean.TYPE;
					break;
				case "byte":
					javaClass = Byte.TYPE;
					break;
				case "short":
					javaClass = Short.TYPE;
					break;
				case "int":
					javaClass = Integer.TYPE;
					break;
				case "long":
					javaClass = Long.TYPE;
					break;
				case "char":
					javaClass = Character.TYPE;
					break;
				case "float":
					javaClass = Float.TYPE;
					break;
				case "double":
					javaClass = Double.TYPE;
					break;
				default:
					assert false :
						"There are only nine primitive types (and "
						+ className
						+ " is not one of them)!";
					throw new RuntimeException();
			}
			return equalityPojo(javaClass);
		}
	},

	/**
	 * An {@link AssignmentPhraseDescriptor assignment phrase}.
	 */
	ASSIGNMENT_PHRASE(
		60,
		BYTE.as("flags"),
		OBJECT_REFERENCE.as("variable"),
		OBJECT_REFERENCE.as("expression"),
		OBJECT_REFERENCE.as("tokens"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			final boolean isInline = isInline(object);
			return array(
				fromInt(isInline ? 1 : 0),
				object.variable(),
				object.expression(),
				object.tokens());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_Number isInline = subobjects[0];
			final A_Phrase variableUse = subobjects[1];
			final A_Phrase expression = subobjects[2];
			final A_Tuple tokens = subobjects[3];
			return newAssignment(
				variableUse, expression, tokens, !isInline.equalsInt(0));
		}
	},

	/**
	 * A {@link BlockPhraseDescriptor block phrase}.
	 */
	BLOCK_PHRASE(
		61,
		TUPLE_OF_OBJECTS.as("arguments tuple"),
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.as("primitive name"),
		TUPLE_OF_OBJECTS.as("statements tuple"),
		OBJECT_REFERENCE.as("result type"),
		TUPLE_OF_OBJECTS.as("declared exceptions"),
		UNSIGNED_INT.as("starting line number"),
		TUPLE_OF_OBJECTS.as("tokens"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			final @Nullable Primitive primitive = object.primitive();
			final A_String primitiveName =
				primitive == null
					? emptyTuple()
					: stringFrom(primitive.name());
			return array(
				object.argumentsTuple(),
				primitiveName,
				object.statementsTuple(),
				object.resultType(),
				object.declaredExceptions().asTuple(),
				fromInt(object.startingLineNumber()),
				object.tokens());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_Tuple argumentsTuple = subobjects[0];
			final A_String primitiveName = subobjects[1];
			final A_Tuple statementsTuple = subobjects[2];
			final A_Type resultType = subobjects[3];
			final A_Tuple declaredExceptionsTuple = subobjects[4];
			final A_Number startingLineNumber = subobjects[5];
			final A_Tuple tokens = subobjects[6];
			final int primitiveNumber;
			if (primitiveName.tupleSize() == 0)
			{
				primitiveNumber = 0;
			}
			else
			{
				final Primitive primitive =
					stripNull(primitiveByName(primitiveName.asNativeString()));
				primitiveNumber = primitive.primitiveNumber;
			}
			return newBlockNode(
				argumentsTuple,
				primitiveNumber,
				statementsTuple,
				resultType,
				declaredExceptionsTuple.asSet(),
				startingLineNumber.extractInt(),
				tokens);
		}
	},

	/**
	 * A {@link DeclarationPhraseDescriptor declaration phrase}.
	 */
	DECLARATION_PHRASE(
		62,
		BYTE.as("declaration kind ordinal"),
		OBJECT_REFERENCE.as("token"),
		OBJECT_REFERENCE.as("declared type"),
		OBJECT_REFERENCE.as("type expression"),
		OBJECT_REFERENCE.as("initialization expression"),
		OBJECT_REFERENCE.as("literal object"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			final DeclarationKind kind = object.declarationKind();
			final A_Token token = object.token();
			final A_Type declaredType = object.declaredType();
			final A_Phrase typeExpression = object.typeExpression();
			final A_Phrase initializationExpression =
				object.initializationExpression();
			final AvailObject literalObject = object.literalObject();
			return array(
				fromInt(kind.ordinal()),
				token,
				declaredType,
				typeExpression,
				initializationExpression,
				literalObject);
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_Number declarationKindNumber = subobjects[0];
			final A_Token token = subobjects[1];
			final A_Type declaredType = subobjects[2];
			final A_Phrase typeExpression = subobjects[3];
			final A_Phrase initializationExpression = subobjects[4];
			final AvailObject literalObject = subobjects[5];

			final DeclarationKind declarationKind =
				DeclarationKind.lookup(declarationKindNumber.extractInt());
			return newDeclaration(
				declarationKind,
				token,
				declaredType,
				typeExpression,
				initializationExpression,
				literalObject);
		}
	},

	/**
	 * An {@link ExpressionAsStatementPhraseDescriptor expression-as-statement
	 * phrase}.
	 */
	EXPRESSION_AS_STATEMENT_PHRASE(63, OBJECT_REFERENCE.as("expression"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array(
				object.expression());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_Phrase expression = subobjects[0];
			return newExpressionAsStatement(expression);
		}
	},

	/**
	 * A {@link FirstOfSequencePhraseDescriptor first-of-sequence phrase}.
	 */
	FIRST_OF_SEQUENCE_PHRASE(64, TUPLE_OF_OBJECTS.as("statements"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array(
				object.statements());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_Tuple statements = subobjects[0];
			return newFirstOfSequenceNode(statements);
		}
	},

	/**
	 * A {@link ListPhraseDescriptor list phrase}.
	 */
	LIST_PHRASE(65, TUPLE_OF_OBJECTS.as("expressions"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array(
				object.expressionsTuple());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_Tuple expressionsTuple = subobjects[0];
			return newListNode(expressionsTuple);
		}
	},

	/**
	 * A {@link LiteralPhraseDescriptor literal phrase}.
	 */
	LITERAL_PHRASE(66, OBJECT_REFERENCE.as("literal token"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array(
				object.token());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_Token literalToken = subobjects[0];
			return literalNodeFromToken(literalToken);
		}
	},

	/**
	 * A {@link MacroSubstitutionPhraseDescriptor macro substitution phrase}.
	 */
	MACRO_SUBSTITUTION_PHRASE(
		67,
		OBJECT_REFERENCE.as("original phrase"),
		OBJECT_REFERENCE.as("output phrase"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array(
				object.macroOriginalSendNode(),
				object.outputPhrase());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_Phrase macroOriginalSendPhrase = subobjects[0];
			final A_Phrase outputPhrase = subobjects[1];
			return newMacroSubstitution(macroOriginalSendPhrase, outputPhrase);
		}
	},

	/**
	 * A {@link PermutedListPhraseDescriptor permuted list phrase}.
	 */
	PERMUTED_LIST_PHRASE(
		68,
		OBJECT_REFERENCE.as("list phrase"),
		TUPLE_OF_OBJECTS.as("permutation"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array(
				object.list(),
				object.permutation());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_Phrase list = subobjects[0];
			final A_Tuple permutation = subobjects[1];
			return newPermutedListNode(list, permutation);
		}
	},

	/**
	 * A {@link PermutedListPhraseDescriptor permuted list phrase}.
	 */
	REFERENCE_PHRASE(69, OBJECT_REFERENCE.as("variable use"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array(
				object.variable());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_Phrase variableUse = subobjects[0];
			return referenceNodeFromUse(variableUse);
		}
	},

	/**
	 * A {@link SendPhraseDescriptor send phrase}.
	 */
	SEND_PHRASE(
		70,
		OBJECT_REFERENCE.as("bundle"),
		OBJECT_REFERENCE.as("arguments list phrase"),
		OBJECT_REFERENCE.as("return type"),
		TUPLE_OF_OBJECTS.as("tokens"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array(
				object.bundle(),
				object.argumentsListNode(),
				object.expressionType(),
				object.tokens());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_Bundle bundle = subobjects[0];
			final A_Phrase argsListNode = subobjects[1];
			final A_Type returnType = subobjects[2];
			final A_Tuple tokens = subobjects[3];
			return newSendNode(tokens, bundle, argsListNode, returnType);
		}
	},

	/**
	 * A {@link SequencePhraseDescriptor sequence phrase}.
	 */
	SEQUENCE_PHRASE(71, TUPLE_OF_OBJECTS.as("statements"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array(
				object.statements());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_Tuple statements = subobjects[0];
			return newSequence(statements);
		}
	},

	/**
	 * A {@link SuperCastPhraseDescriptor super cast phrase}.
	 */
	SUPER_CAST_PHRASE(
		72,
		OBJECT_REFERENCE.as("expression"),
		OBJECT_REFERENCE.as("type for lookup"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array(
				object.expression(),
				object.superUnionType());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_Phrase expression = subobjects[0];
			final A_Type superUnionType = subobjects[1];
			return newSuperCastNode(expression, superUnionType);
		}
	},

	/**
	 * A {@link VariableUsePhraseDescriptor variable use phrase}.
	 */
	VARIABLE_USE_PHRASE(
		73,
		OBJECT_REFERENCE.as("use token"),
		OBJECT_REFERENCE.as("declaration"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array(
				object.token(),
				object.declaration());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_Token token = subobjects[0];
			final A_Phrase declaration = subobjects[1];
			return newUse(token, declaration);
		}
	},

	/**
	 * An arbitrary primitive type that is not already a special object. Exists
	 * primarily to support hidden types that are not exposed directly to an
	 * Avail programmer but which must still be visible to the serialization
	 * mechanism.
	 */
	ARBITRARY_PRIMITIVE_TYPE(
		74,
		BYTE.as("primitive type ordinal"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array(fromInt(extractOrdinal(object)));
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return Types.all()[subobjects[0].extractInt()].o();
		}
	},

	/**
	 * A {@link A_Variable variable} bound to a {@code static} Java field.
	 */
	STATIC_POJO_FIELD(
		75,
		OBJECT_REFERENCE.as("class name"),
		OBJECT_REFERENCE.as("field name"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			assert object.descriptor() instanceof PojoFinalFieldDescriptor;
			final Field field = object
				.slot(PojoFinalFieldDescriptor.ObjectSlots.FIELD)
				.javaObjectNotNull();
			final Class<?> definingClass = field.getDeclaringClass();
			final A_String className = stringFrom(definingClass.getName());
			final A_String fieldName = stringFrom(field.getName());
			return array(
				className,
				fieldName);
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			try
			{
				final ClassLoader classLoader =
					deserializer.runtime().classLoader();
				final Class<?> definingClass = Class.forName(
					subobjects[0].asNativeString(), true, classLoader);
				final Field field = definingClass.getField(
					subobjects[1].asNativeString());
				assert (field.getModifiers() & Modifier.STATIC) != 0;
				final A_Type fieldType = resolvePojoType(
					field.getGenericType(), emptyMap());
				return pojoFieldVariableForInnerType(
					equalityPojo(field), rawNullPojo(), fieldType);
			}
			catch (final ClassNotFoundException|NoSuchFieldException e)
			{
				throw new RuntimeException(e);
			}
		}
	},

	/**
	 * Reserved for future use.
	 */
	RESERVED_76(76)
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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
	RESERVED_77(77)
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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
	RESERVED_78(78)
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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
	RESERVED_79(79)
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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
	FIBER_TYPE(80, OBJECT_REFERENCE.as("Result type"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array(object.resultType());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final A_Type resultType = subobjects[0];
			return fiberType(resultType);
		}
	},

	/**
	 * A {@linkplain FunctionTypeDescriptor function type}.
	 */
	FUNCTION_TYPE(
		81,
		OBJECT_REFERENCE.as("Arguments tuple type"),
		OBJECT_REFERENCE.as("Return type"),
		TUPLE_OF_OBJECTS.as("Checked exceptions"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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
			return functionTypeFromArgumentTupleType(
				argsTupleType, returnType, checkedExceptionsTuple.asSet());
		}
	},

	/**
	 * A {@linkplain TupleTypeDescriptor tuple type}.
	 */
	TUPLE_TYPE(
		82,
		OBJECT_REFERENCE.as("Tuple sizes"),
		TUPLE_OF_OBJECTS.as("Leading types"),
		OBJECT_REFERENCE.as("Default type"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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
			return tupleTypeForSizesTypesDefaultType(
				sizeRange, typeTuple, defaultType);
		}
	},

	/**
	 * An {@linkplain IntegerRangeTypeDescriptor integer range type}.
	 */
	INTEGER_RANGE_TYPE(
		83,
		BYTE.as("Inclusive flags"),
		OBJECT_REFERENCE.as("Lower bound"),
		OBJECT_REFERENCE.as("Upper bound"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			final int flags = (object.lowerInclusive() ? 1 : 0)
				+ (object.upperInclusive() ? 2 : 0);
			return array(
				fromInt(flags),
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
			return integerRangeType(
				lowerBound, lowerInclusive, upperBound, upperInclusive);
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
	UNFUSED_POJO_TYPE(
		84,
		OBJECT_REFERENCE.as("class name"),
		TUPLE_OF_OBJECTS.as("class parameterization"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			assert object.isPojoType();
			assert !object.isPojoFusedType();
			final AvailObject rawPojoType = object.javaClass();
			final Class<?> baseClass = rawPojoType.javaObjectNotNull();
			final A_String className = stringFrom(baseClass.getName());
			final A_Map ancestorMap = object.javaAncestors();
			final A_Tuple myParameters = ancestorMap.mapAt(rawPojoType);
			final List<A_BasicObject> processedParameters =
				new ArrayList<>(myParameters.tupleSize());
			for (final A_Type parameter : myParameters)
			{
				assert !parameter.isTuple();
				if (parameter.isPojoSelfType())
				{
					processedParameters.add(pojoSerializationProxy(parameter));
				}
				else
				{
					processedParameters.add(parameter);
				}
			}
			return array(
				className,
				tupleFromList(processedParameters));
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final List<AvailObject> processedParameters =
				new ArrayList<>(subobjects[1].tupleSize());
			final ClassLoader classLoader =
				deserializer.runtime().classLoader();
			try
			{
				for (final AvailObject parameter : subobjects[1])
				{
					processedParameters.add(
						parameter.isTuple()
							? pojoFromSerializationProxy(parameter, classLoader)
							: parameter);
				}
				final Class<?> baseClass = Class.forName(
					subobjects[0].asNativeString(), true, classLoader);
				return pojoTypeForClassWithTypeArguments(
					baseClass, tupleFromList(processedParameters));
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
	FUSED_POJO_TYPE(85, GENERAL_MAP.as("ancestor parameterizations map"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			assert object.isPojoType();
			assert object.isPojoFusedType();
			A_Map symbolicMap = emptyMap();
			for (final Entry entry
				: object.javaAncestors().mapIterable())
			{
				final Class<?> baseClass = entry.key().javaObjectNotNull();
				final A_String className = stringFrom(baseClass.getName());
				final List<A_BasicObject> processedParameters =
					new ArrayList<>(entry.value().tupleSize());
				for (final AvailObject parameter : entry.value())
				{
					assert !parameter.isTuple();
					if (parameter.isPojoSelfType())
					{
						processedParameters.add(
							pojoSerializationProxy(parameter));
					}
					else
					{
						processedParameters.add(parameter);
					}
				}
				symbolicMap = symbolicMap.mapAtPuttingCanDestroy(
					className, tupleFromList(processedParameters), true);
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
			A_Map ancestorMap = emptyMap();
			try
			{
				for (final Entry entry : subobjects[0].mapIterable())
				{
					final Class<?> baseClass = Class.forName(
						entry.key().asNativeString(), true, classLoader);
					final AvailObject rawPojo = equalityPojo(baseClass);
					final List<AvailObject> processedParameters =
						new ArrayList<>(entry.value().tupleSize());
					for (final AvailObject parameter : entry.value())
					{
						if (parameter.isTuple())
						{
							processedParameters.add(
								pojoFromSerializationProxy(
									parameter, classLoader));
						}
						else
						{
							processedParameters.add(parameter);
						}
					}
					ancestorMap = ancestorMap.mapAtPuttingCanDestroy(
						rawPojo, tupleFromList(processedParameters), true);
				}
			}
			catch (final ClassNotFoundException e)
			{
				throw new RuntimeException(e);
			}
			return fusedTypeFromAncestorMap(ancestorMap);
		}
	},

	/**
	 * A {@linkplain PojoTypeDescriptor pojo type} representing a Java array
	 * type.  We can reconstruct this array type from the content type and the
	 * range of allowable sizes (a much stronger model than Java itself
	 * supports).
	 */
	ARRAY_POJO_TYPE(
		86,
		OBJECT_REFERENCE.as("content type"),
		OBJECT_REFERENCE.as("size range"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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
			return pojoArrayType(contentType, sizeRange);
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
	SELF_POJO_TYPE_REPRESENTATIVE(87, TUPLE_OF_OBJECTS.as("class names"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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
	BOTTOM_POJO_TYPE(88)
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array();
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return pojoBottom();
		}
	},

	/**
	 * The bottom {@linkplain PojoTypeDescriptor pojo type}, representing
	 * the most specific type of pojo.
	 */
	COMPILED_CODE_TYPE(89, OBJECT_REFERENCE.as("function type for code type"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array(object.functionType());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return compiledCodeTypeForFunctionType(subobjects[0]);
		}
	},

	/**
	 * The bottom {@linkplain PojoTypeDescriptor pojo type}, representing
	 * the most specific type of pojo.
	 */
	CONTINUATION_TYPE(
		90,
		OBJECT_REFERENCE.as("function type for continuation type"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array(object.functionType());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return continuationTypeForFunctionType(subobjects[0]);
		}
	},

	/**
	 * An Avail {@link EnumerationTypeDescriptor enumeration}, a type that has
	 * an explicit finite list of its instances.
	 */
	ENUMERATION_TYPE(91, TUPLE_OF_OBJECTS.as("set of instances"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array(object.instances().asTuple());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return
				enumerationWith(subobjects[0].asSet());
		}
	},

	/**
	 * An Avail {@link InstanceTypeDescriptor singular enumeration}, a type that
	 * has a single (non-type) instance.
	 */
	INSTANCE_TYPE(92, OBJECT_REFERENCE.as("type's instance"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array(object.instance());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return instanceType(subobjects[0]);
		}
	},

	/**
	 * An Avail {@link InstanceMetaDescriptor instance meta}, a type that
	 * has an instance i, which is itself a type.  Subtypes of type i are also
	 * considered instances of this instance meta.
	 */
	INSTANCE_META(93, OBJECT_REFERENCE.as("meta's instance"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array(object.instance());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return instanceMeta(subobjects[0]);
		}
	},

	/**
	 * A {@linkplain SetTypeDescriptor set type}.
	 */
	SET_TYPE(
		94,
		OBJECT_REFERENCE.as("size range"),
		OBJECT_REFERENCE.as("element type"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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
			return setTypeForSizesContentType(sizeRange, contentType);
		}
	},

	/**
	 * A {@linkplain MapTypeDescriptor map type}.
	 */
	MAP_TYPE(
		95,
		OBJECT_REFERENCE.as("size range"),
		OBJECT_REFERENCE.as("key type"),
		OBJECT_REFERENCE.as("value type"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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
			return mapTypeForSizesKeyTypeValueType(
				sizeRange, keyType, valueType);
		}
	},

	/**
	 * A {@linkplain TokenTypeDescriptor token type}.
	 */
	TOKEN_TYPE(96, OBJECT_REFERENCE.as("literal type"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array(
				fromInt(object.tokenType().ordinal()));
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return tokenType(lookupTokenType(subobjects[0].extractInt()));
		}
	},

	/**
	 * A {@linkplain LiteralTokenTypeDescriptor literal token type}.
	 */
	LITERAL_TOKEN_TYPE(97, OBJECT_REFERENCE.as("literal type"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array (
				object.literalType());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return literalTokenType(subobjects[0]);
		}
	},

	/**
	 * A {@linkplain PhraseTypeDescriptor parse phrase type}.
	 */
	PARSE_NODE_TYPE(
		98,
		BYTE.as("kind"),
		OBJECT_REFERENCE.as("expression type"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array (
				fromInt(object.phraseKind().ordinal()),
				object.expressionType());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final int phraseKindOrdinal = subobjects[0].extractInt();
			final AvailObject expressionType = subobjects[1];
			final PhraseKind phraseKind =
				PhraseKind.lookup(phraseKindOrdinal);
			return phraseKind.create(expressionType);
		}
	},

	/**
	 * A {@linkplain ListPhraseTypeDescriptor list phrase type}.
	 */
	LIST_NODE_TYPE(
		99,
		BYTE.as("list phrase kind"),
		OBJECT_REFERENCE.as("expression type"),
		OBJECT_REFERENCE.as("subexpressions tuple type"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array (
				fromInt(object.phraseKind().ordinal()),
				object.expressionType(),
				object.subexpressionsTupleType());
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			final int phraseKindOrdinal = subobjects[0].extractInt();
			final AvailObject expressionType = subobjects[1];
			final AvailObject subexpressionsTupleType = subobjects[2];
			final PhraseKind phraseKind =
				PhraseKind.lookup(phraseKindOrdinal);
			return createListNodeType(
				phraseKind, expressionType, subexpressionsTupleType);
		}
	},

	/**
	 * A {@linkplain VariableTypeDescriptor variable type} for which the read
	 * type and write type are equal.
	 */
	SIMPLE_VARIABLE_TYPE(100, OBJECT_REFERENCE.as("content type"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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
			return variableTypeFor(subobjects[0]);
		}
	},

	/**
	 * A {@linkplain ReadWriteVariableTypeDescriptor variable type} for which
	 * the read type and write type are (actually) unequal.
	 */
	READ_WRITE_VARIABLE_TYPE(
		101,
		OBJECT_REFERENCE.as("read type"),
		OBJECT_REFERENCE.as("write type"))
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
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
			return variableReadWriteType(
				subobjects[0],
				subobjects[1]);
		}
	},

	/**
	 * The {@linkplain BottomTypeDescriptor bottom type}, more specific than all
	 * other types.
	 */
	BOTTOM_TYPE(102)
	{
		@Override
		A_BasicObject[] decompose (
			final AvailObject object,
			final Serializer serializer)
		{
			return array();
		}

		@Override
		A_BasicObject compose (
			final AvailObject[] subobjects,
			final Deserializer deserializer)
		{
			return pojoBottom();
		}
	};

	/**
	 * The array of enumeration values.  Don't change it.
	 */
	private static final SerializerOperation[] all = values();

	/**
	 * Answer the enum value having the given ordinal.
	 *
	 * @param ordinal The ordinal to look up.
	 * @return The {@code SerializerOperation} having the given ordinal.
	 */
	static SerializerOperation byOrdinal (final int ordinal)
	{
		return all[ordinal];
	}

	/** The maximum number of operands of any SerializerOperation. */
	static final int maxSubobjects = Arrays.stream(all)
		.map(op -> op.operands().length)
		.reduce(0, Math::max);

	/**
	 * The operands that this operation expects to see encoded after the tag.
	 */
	protected final SerializerOperand[] operands;

	/**
	 * Answer my {@linkplain SerializerOperand operands}.
	 *
	 * @return My {@code SerializerOperand}s.
	 */
	@SuppressWarnings({
		"AssignmentOrReturnOfFieldWithMutableType",
		"SingleElementAnnotation"
	})
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
	boolean isVariableCreation ()
	{
		return false;
	}

	/**
	 * Construct a new {@code SerializerOperation}.
	 *
	 * @param ordinal
	 *            The ordinal of this enum value, supplied as a cross-check to
	 *            reduce the chance of accidental incompatibility due to the
	 *            addition of new categories of Avail objects.
	 * @param operands
	 *            The list of operands that describe the interpretation of a
	 *            stream of bytes written with this {@code SerializerOperation}.
	 */
	SerializerOperation (
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
	 * @param serializer
	 *            The serializer requesting decomposition.
	 * @return
	 *            An array of {@code AvailObject}s whose entries agree with this
	 *            {@code SerializerOperation}'s operands.
	 */
	abstract A_BasicObject[] decompose (
		final AvailObject object,
		final Serializer serializer);

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
	 * @param operandValues
	 *        The already extracted array of operand values.
	 * @param serializer
	 *        Where to serialize it.
	 */
	void writeObject (
		final A_BasicObject[] operandValues,
		final Serializer serializer)
	{
		serializer.writeByte(ordinal());
		assert operandValues.length == operands.length;
		for (int i = 0; i < operandValues.length; i++)
		{
			operands[i].write((AvailObject) operandValues[i], serializer);
		}
	}

	/**
	 * Describe this operation and its operands.
	 *
	 * @param describer
	 *        The {@link DeserializerDescriber} on which to describe this.
	 */
	void describe (
		final DeserializerDescriber describer)
	{
		describer.append(this.name());
		for (final SerializerOperand operand : operands)
		{
			describer.append("\n\t");
			operand.describe(describer);
		}
	}

	/**
	 * Find or create the atom with the given name in the module with the given
	 * name.
	 *
	 * @param atomName The name of the atom.
	 * @param moduleName The module that defines the atom.
	 * @param deserializer A deserializer with which to look up modules.
	 * @return The {@link A_Atom atom}.
	 */
	static A_Atom lookupAtom (
		final A_String atomName,
		final A_String moduleName,
		final Deserializer deserializer)
	{
		final A_Module currentModule = deserializer.currentModule();
		assert !currentModule.equalsNil();
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
			final A_Atom atom = createAtomWithProperties(
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
				return candidates.iterator().next();
			}
			if (candidates.setSize() > 1)
			{
				throw new RuntimeException(
					format(
						"Ambiguous atom \"%s\" in module %s",
						atomName,
						module));
			}
		}
		// This should probably fail more gracefully.
		throw new RuntimeException(
			format(
				"Unknown atom %s in module %s",
				atomName,
				module));
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
