/*
 * SerializerOperation.kt
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
import com.avail.AvailRuntime.specialObject
import com.avail.descriptor.*
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.AssignmentPhraseDescriptor.isInline
import com.avail.descriptor.AssignmentPhraseDescriptor.newAssignment
import com.avail.descriptor.AtomDescriptor.SpecialAtom
import com.avail.descriptor.AtomDescriptor.SpecialAtom.EXPLICIT_SUBCLASSING_KEY
import com.avail.descriptor.AtomDescriptor.SpecialAtom.HERITABLE_KEY
import com.avail.descriptor.AtomDescriptor.trueObject
import com.avail.descriptor.AtomWithPropertiesDescriptor.createAtomWithProperties
import com.avail.descriptor.BlockPhraseDescriptor.newBlockNode
import com.avail.descriptor.BottomPojoTypeDescriptor.pojoBottom
import com.avail.descriptor.CharacterDescriptor.fromCodePoint
import com.avail.descriptor.CommentTokenDescriptor.newCommentToken
import com.avail.descriptor.CompiledCodeDescriptor.newCompiledCode
import com.avail.descriptor.CompiledCodeTypeDescriptor.compiledCodeTypeForFunctionType
import com.avail.descriptor.ContinuationDescriptor.createContinuationWithFrame
import com.avail.descriptor.ContinuationTypeDescriptor.continuationTypeForFunctionType
import com.avail.descriptor.DeclarationPhraseDescriptor.DeclarationKind
import com.avail.descriptor.DeclarationPhraseDescriptor.newDeclaration
import com.avail.descriptor.DoubleDescriptor.fromDouble
import com.avail.descriptor.ExpressionAsStatementPhraseDescriptor.newExpressionAsStatement
import com.avail.descriptor.FiberTypeDescriptor.fiberType
import com.avail.descriptor.FirstOfSequencePhraseDescriptor.newFirstOfSequenceNode
import com.avail.descriptor.FloatDescriptor.fromFloat
import com.avail.descriptor.FunctionDescriptor.createFunction
import com.avail.descriptor.FunctionTypeDescriptor.functionTypeFromArgumentTupleType
import com.avail.descriptor.InstanceMetaDescriptor.instanceMeta
import com.avail.descriptor.InstanceTypeDescriptor.instanceType
import com.avail.descriptor.IntegerDescriptor.*
import com.avail.descriptor.IntegerRangeTypeDescriptor.integerRangeType
import com.avail.descriptor.ListPhraseDescriptor.newListNode
import com.avail.descriptor.ListPhraseTypeDescriptor.createListNodeType
import com.avail.descriptor.LiteralPhraseDescriptor.literalNodeFromToken
import com.avail.descriptor.LiteralTokenDescriptor.literalToken
import com.avail.descriptor.LiteralTokenTypeDescriptor.literalTokenType
import com.avail.descriptor.MacroSubstitutionPhraseDescriptor.newMacroSubstitution
import com.avail.descriptor.MapDescriptor.emptyMap
import com.avail.descriptor.MapTypeDescriptor.mapTypeForSizesKeyTypeValueType
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.ObjectDescriptor.objectFromMap
import com.avail.descriptor.ObjectTupleDescriptor.*
import com.avail.descriptor.ObjectTypeDescriptor.objectTypeFromMap
import com.avail.descriptor.PermutedListPhraseDescriptor.newPermutedListNode
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind
import com.avail.descriptor.PojoFieldDescriptor.pojoFieldVariableForInnerType
import com.avail.descriptor.PojoTypeDescriptor.*
import com.avail.descriptor.PrimitiveTypeDescriptor.extractOrdinal
import com.avail.descriptor.RawPojoDescriptor.equalityPojo
import com.avail.descriptor.RawPojoDescriptor.rawNullPojo
import com.avail.descriptor.ReferencePhraseDescriptor.referenceNodeFromUse
import com.avail.descriptor.SelfPojoTypeDescriptor.pojoFromSerializationProxy
import com.avail.descriptor.SelfPojoTypeDescriptor.pojoSerializationProxy
import com.avail.descriptor.SendPhraseDescriptor.newSendNode
import com.avail.descriptor.SequencePhraseDescriptor.newSequence
import com.avail.descriptor.SetTypeDescriptor.setTypeForSizesContentType
import com.avail.descriptor.StringDescriptor.stringFrom
import com.avail.descriptor.SuperCastPhraseDescriptor.newSuperCastNode
import com.avail.descriptor.TokenDescriptor.TokenType.lookupTokenType
import com.avail.descriptor.TokenDescriptor.newToken
import com.avail.descriptor.TokenTypeDescriptor.tokenType
import com.avail.descriptor.TupleDescriptor.emptyTuple
import com.avail.descriptor.TupleDescriptor.toList
import com.avail.descriptor.TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType
import com.avail.descriptor.VariableDescriptor.newVariableWithOuterType
import com.avail.descriptor.VariableTypeDescriptor.variableReadWriteType
import com.avail.descriptor.VariableTypeDescriptor.variableTypeFor
import com.avail.descriptor.VariableUsePhraseDescriptor.newUse
import com.avail.exceptions.AvailErrorCode.E_JAVA_METHOD_NOT_AVAILABLE
import com.avail.exceptions.AvailRuntimeException
import com.avail.exceptions.MalformedMessageException
import com.avail.interpreter.Primitive.primitiveByName
import com.avail.interpreter.levelTwo.L2Chunk.ChunkEntryPoint.TO_RESTART
import com.avail.interpreter.levelTwo.L2Chunk.ChunkEntryPoint.TO_RETURN_INTO
import com.avail.interpreter.levelTwo.L2Chunk.unoptimizedChunk
import com.avail.interpreter.primitive.pojos.P_CreatePojoConstructorFunction
import com.avail.interpreter.primitive.pojos.P_CreatePojoInstanceMethodFunction
import com.avail.serialization.SerializerOperandEncoding.*
import com.avail.utility.Nulls.stripNull
import java.lang.Double.doubleToRawLongBits
import java.lang.Double.longBitsToDouble
import java.lang.Float.floatToRawIntBits
import java.lang.Float.intBitsToFloat
import java.lang.String.format
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.*
import kotlin.math.max

/**
 * A `SerializerOpcode` describes how to disassemble and assemble the various
 * kinds of objects encountered in Avail.
 *
 * The ordinal is passed in the constructor as a cross-check, to increase the
 * difficulty of (accidentally) changing the serialized representation without
 * due care for migration of existing serialized data.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `SerializerOperation`.
 *
 * @param ordinal
 *   The ordinal of this enum value, supplied as a cross-check to reduce the
 *   chance of accidental incompatibility due to the addition of new categories
 *   of Avail objects.
 * @param operands
 *   The list of operands that describe the interpretation of a stream of bytes
 *   written with this `SerializerOperation`.
 */
enum class SerializerOperation constructor(
	ordinal: Int,
	vararg operands: SerializerOperand)
{
	/**
	 * The Avail integer 0.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	ZERO_INTEGER(0)
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array()
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return zero()
		}
	},

	/**
	 * The Avail integer 1.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	ONE_INTEGER(1)
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array()
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return one()
		}
	},

	/**
	 * The Avail integer 2.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	TWO_INTEGER(2)
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array()
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return two()
		}
	},

	/**
	 * The Avail integer 3.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	THREE_INTEGER(3)
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array()
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return fromUnsignedByte(3.toShort())
		}
	},

	/**
	 * The Avail integer 4.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	FOUR_INTEGER(4)
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array()
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return fromUnsignedByte(4.toShort())
		}
	},

	/**
	 * The Avail integer 5.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	FIVE_INTEGER(5)
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array()
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return fromUnsignedByte(5.toShort())
		}
	},

	/**
	 * The Avail integer 6.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	SIX_INTEGER(6)
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array()
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return fromUnsignedByte(6.toShort())
		}
	},

	/**
	 * The Avail integer 7.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	SEVEN_INTEGER(7)
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array()
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return fromUnsignedByte(7.toShort())
		}
	},

	/**
	 * The Avail integer 8.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	EIGHT_INTEGER(8)
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array()
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return fromUnsignedByte(8.toShort())
		}
	},

	/**
	 * The Avail integer 9.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	NINE_INTEGER(9)
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array()
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return fromUnsignedByte(9.toShort())
		}
	},

	/**
	 * The Avail integer 10.  Note that there are no operands, since the value
	 * is encoded in the choice of instruction itself.
	 */
	TEN_INTEGER(10)
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array()
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return fromUnsignedByte(10.toShort())
		}
	},

	/**
	 * An Avail integer in the range 11..255.  Note that 0..10 have their own
	 * special cases already which require very little space.
	 */
	BYTE_INTEGER(11, BYTE.`as`("only byte"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(`object`)
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return subobjects[0]
		}

		override fun describe(describer: DeserializerDescriber)
		{
			describer.append(this.name)
			describer.append(" = ")
			describer.append(operands[0].read(describer).toString())
		}
	},

	/**
	 * An Avail integer in the range 256..65535.  Note that 0..255 have their
	 * own special cases already which require less space.  Don't try to
	 * compress the short value for this reason.
	 */
	SHORT_INTEGER(12, UNCOMPRESSED_SHORT.`as`("the unsigned short"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(`object`)
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return subobjects[0]
		}

		override fun describe(describer: DeserializerDescriber)
		{
			describer.append(this.name)
			describer.append(" = ")
			describer.append(operands[0].read(describer).toString())
		}
	},

	/**
	 * An Avail integer in the range -2<sup>31</sup> through 2<sup>31</sup>-1,
	 * except the range 0..65535 which have their own special cases already.
	 */
	INT_INTEGER(13, SIGNED_INT.`as`("int's value"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(`object`)
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return subobjects[0]
		}

		override fun describe(describer: DeserializerDescriber)
		{
			describer.append(this.name)
			describer.append(" = ")
			describer.append(operands[0].read(describer).toString())
		}
	},

	/**
	 * An Avail integer that cannot be represented as an `int`.
	 */
	BIG_INTEGER(14, BIG_INTEGER_DATA.`as`("constituent ints"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(`object`)
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return subobjects[0]
		}

		override fun describe(describer: DeserializerDescriber)
		{
			describer.append(this.name)
			describer.append(" = ")
			describer.append(operands[0].read(describer).toString())
		}
	},

	/**
	 * Produce the Avail [nil][NilDescriptor.nil] during deserialization.
	 */
	NIL(15)
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array()
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return nil
		}
	},

	/**
	 * This special opcode causes a previously built object to be produced as an
	 * actual checkpoint output from the [Deserializer].
	 */
	CHECKPOINT(16, OBJECT_REFERENCE.`as`("object to checkpoint"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			// Make sure the function actually gets written out.
			return array(`object`)
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val subobject = subobjects[0]
			deserializer.recordProducedObject(subobject)
			return subobject
		}
	},

	/**
	 * One of the special objects that the [AvailRuntime] maintains.
	 */
	SPECIAL_OBJECT(17, COMPRESSED_SHORT.`as`("special object number"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				fromInt(Serializer.indexOfSpecialObject(`object`)))
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return specialObject(subobjects[0].extractInt())
		}

		override fun describe(describer: DeserializerDescriber)
		{
			describer.append(this.name)
			val specialNumber = operands[0].read(describer)
			val specialIndex = specialNumber.extractInt()
			describer.append(" (")
			describer.append(specialIndex.toString())
			describer.append(") = ")
			describer.append(specialObject(specialIndex).toString())
		}
	},

	/**
	 * One of the special atoms that the [AvailRuntime] maintains.
	 */
	SPECIAL_ATOM(18, COMPRESSED_SHORT.`as`("special atom number"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				fromInt(Serializer.indexOfSpecialAtom(`object`)))
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return Deserializer.specialAtom(subobjects[0].extractInt())
		}

		override fun describe(describer: DeserializerDescriber)
		{
			describer.append(this.name)
			val specialNumber = operands[0].read(describer)
			val specialIndex = specialNumber.extractInt()
			describer.append(" (")
			describer.append(specialIndex.toString())
			describer.append(") = ")
			describer.append(Deserializer.specialAtom(specialIndex).toString())
		}
	},

	/**
	 * A [character][CharacterDescriptor] whose code point fits in an
	 * unsigned byte (0..255).
	 */
	BYTE_CHARACTER(19, BYTE.`as`("Latin-1 code point"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(fromInt(`object`.codePoint()))
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return fromCodePoint(subobjects[0].extractInt())
		}
	},

	/**
	 * A [character][CharacterDescriptor] whose code point requires an
	 * unsigned short (256..65535).
	 */
	SHORT_CHARACTER(20, UNCOMPRESSED_SHORT.`as`("BMP code point"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(fromInt(`object`.codePoint()))
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return fromCodePoint(subobjects[0].extractInt())
		}
	},

	/**
	 * A [character][CharacterDescriptor] whose code point requires three bytes
	 * to represent (0..16777215, but technically only 0..1114111).
	 */
	LARGE_CHARACTER(
		21,
		BYTE.`as`("SMP codepoint high byte"),
		BYTE.`as`("SMP codepoint middle byte"),
		BYTE.`as`("SMP codepoint low byte"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			val codePoint = `object`.codePoint()
			return array(
				fromInt(codePoint shr 16 and 0xFF),
				fromInt(codePoint shr 8 and 0xFF),
				fromInt(codePoint and 0xFF))
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return fromCodePoint(
				(subobjects[0].extractUnsignedByte().toInt() shl 16)
					+ (subobjects[1].extractUnsignedByte().toInt() shl 8)
					+ subobjects[2].extractUnsignedByte().toInt())
		}
	},

	/**
	 * A [float][FloatDescriptor].  Convert the raw bits to an int for writing.
	 */
	FLOAT(22, SIGNED_INT.`as`("raw bits"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			val floatValue = `object`.extractFloat()
			val floatBits = floatToRawIntBits(floatValue)
			return array(fromInt(floatBits))
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val floatBits = subobjects[0].extractInt()
			val floatValue = intBitsToFloat(floatBits)
			return fromFloat(floatValue)
		}

		override fun describe(describer: DeserializerDescriber)
		{
			describer.append(this.name)
			describer.append(" = ")
			val floatAsIntNumber = operands[0].read(describer)
			val floatBits = floatAsIntNumber.extractInt()
			describer.append(intBitsToFloat(floatBits).toString())
		}
	},

	/**
	 * A [double][DoubleDescriptor].  Convert the raw bits to a long and write
	 * it in big endian.
	 */
	DOUBLE(
		23,
		SIGNED_INT.`as`("upper raw bits"),
		SIGNED_INT.`as`("lower raw bits"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			val doubleValue = `object`.extractDouble()
			val doubleBits = doubleToRawLongBits(doubleValue)
			return array(
				fromInt((doubleBits shr 32).toInt()),
				fromInt(doubleBits.toInt()))
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val highBits = subobjects[0].extractInt()
			val lowBits = subobjects[1].extractInt()
			val doubleBits =
				(highBits.toLong() shl 32) + (lowBits.toLong() and 0xFFFFFFFFL)
			val doubleValue = longBitsToDouble(doubleBits)
			return fromDouble(doubleValue)
		}

		override fun describe(describer: DeserializerDescriber)
		{
			describer.append(this.name)
			describer.append(" = ")
			val highBitsAsNumber = operands[0].read(describer)
			val lowBitsAsNumber = operands[1].read(describer)
			val highBits = highBitsAsNumber.extractInt()
			val lowBits = lowBitsAsNumber.extractInt()
			val doubleBits =
				(highBits.toLong() shl 32) + (lowBits.toLong() and 0xFFFFFFFFL)
			val theDouble = longBitsToDouble(doubleBits)
			describer.append(theDouble.toString())
		}
	},

	/**
	 * A [tuple][TupleDescriptor] of arbitrary objects.  Write the size of the
	 * tuple then the elements as object identifiers.
	 */
	GENERAL_TUPLE(24, TUPLE_OF_OBJECTS.`as`("tuple elements"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(`object`)
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return subobjects[0]
		}
	},

	/**
	 * A [tuple of characters with code points in][StringDescriptor].  Write the
	 * size of the tuple then the sequence of character bytes.
	 */
	BYTE_STRING(25, BYTE_CHARACTER_TUPLE.`as`("Latin-1 string"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(`object`)
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return subobjects[0]
		}
	},

	/**
	 * A [tuple of characters][StringDescriptor] whose code points all fall in
	 * the range 0..65535.  Write the compressed number of characters then each
	 * compressed character.
	 */
	SHORT_STRING(
		26,
		COMPRESSED_SHORT_CHARACTER_TUPLE.`as`(
			"Basic Multilingual Plane string"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(`object`)
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return subobjects[0]
		}
	},

	/**
	 * A [tuple of characters][StringDescriptor] with arbitrary code points.
	 * Write the compressed number of characters then each compressed character.
	 */
	ARBITRARY_STRING(
		27,
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.`as`("arbitrary string"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(`object`)
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return subobjects[0]
		}
	},

	/**
	 * A [tuple of integers][TupleDescriptor] whose values all fall in the range
	 * 0..2^31-1.
	 */
	INT_TUPLE(28, COMPRESSED_INT_TUPLE.`as`("tuple of ints"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(`object`)
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return subobjects[0]
		}
	},

	/**
	 * A [tuple of integers][TupleDescriptor] whose values all fall in the range
	 * 0..255.
	 */
	BYTE_TUPLE(29, UNCOMPRESSED_BYTE_TUPLE.`as`("tuple of bytes"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(`object`)
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return subobjects[0]
		}
	},

	/**
	 * A [tuple of integers][TupleDescriptor] whose values fall in the range
	 * 0..15.
	 */
	NYBBLE_TUPLE(30, UNCOMPRESSED_NYBBLE_TUPLE.`as`("tuple of nybbles"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(`object`)
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return subobjects[0]
		}
	},

	/**
	 * A [map][MapDescriptor].  Convert it to a tuple (key1, value1, ...
	 * key```[N]```, value```[N]```) and work with that, converting it back to a
	 * map when deserializing.
	 */
	MAP(31, GENERAL_MAP.`as`("map contents"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(`object`)
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return subobjects[0]
		}
	},

	/**
	 * A [map][MapDescriptor].  Convert it to a tuple (key1, value1, ...
	 * key```[N]```, value```[N]```) and work with that, converting it back to a
	 * map when deserializing.
	 */
	OBJECT(32, GENERAL_MAP.`as`("field map"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(`object`.fieldMap())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return objectFromMap(subobjects[0])
		}
	},

	/**
	 * A [map][MapDescriptor].  Convert it to a tuple (key1, value1, ...
	 * key```[N]```, value```[N]```) and work with that, converting it back to a
	 * map when deserializing.
	 */
	OBJECT_TYPE(33, GENERAL_MAP.`as`("field type map"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(`object`.fieldTypeMap())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return objectTypeFromMap(subobjects[0])
		}
	},

	/**
	 * An [atom][AtomDescriptor].  Output the atom name and the name of the
	 * module that issued it.  Look up the corresponding atom during
	 * reconstruction, recreating it if it's not present and supposed to have
	 * been issued by the current module.
	 */
	ATOM(
		34,
		OBJECT_REFERENCE.`as`("atom name"),
		OBJECT_REFERENCE.`as`("module name"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			serializer.checkAtom(`object`)
			assert(`object`.getAtomProperty(HERITABLE_KEY.atom).equalsNil())
			val module = `object`.issuingModule()
			if (module.equalsNil())
			{
				throw RuntimeException("Atom has no issuing module")
			}
			return array(`object`.atomName(), module.moduleName())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val atomName = subobjects[0]
			val moduleName = subobjects[1]
			val atom = lookupAtom(atomName, moduleName, deserializer)
			return atom.makeShared()
		}
	},

	/**
	 * An [atom][AtomDescriptor].  Output the atom name and the name of the
	 * module that issued it.  Look up the corresponding atom during
	 * reconstruction, recreating it if it's not present and supposed to have
	 * been issued by the current module.
	 */
	HERITABLE_ATOM(
		35,
		OBJECT_REFERENCE.`as`("atom name"),
		OBJECT_REFERENCE.`as`("module name"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			serializer.checkAtom(`object`)
			assert(
				`object`.getAtomProperty(HERITABLE_KEY.atom).equals(
					trueObject()))
			val module = `object`.issuingModule()
			if (module.equalsNil())
			{
				throw RuntimeException("Atom has no issuing module")
			}
			return array(`object`.atomName(), module.moduleName())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val atomName = subobjects[0]
			val moduleName = subobjects[1]
			val atom = lookupAtom(atomName, moduleName, deserializer)
			atom.setAtomProperty(HERITABLE_KEY.atom, trueObject())
			return atom.makeShared()
		}
	},

	/**
	 * A [compiled code object][CompiledCodeDescriptor].  Output any information
	 * needed to reconstruct the compiled code object.
	 */
	COMPILED_CODE(
		36,
		COMPRESSED_SHORT.`as`("Total number of frame slots"),
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.`as`("Primitive name"),
		OBJECT_REFERENCE.`as`("Function type"),
		UNCOMPRESSED_NYBBLE_TUPLE.`as`("Level one nybblecodes"),
		TUPLE_OF_OBJECTS.`as`("Regular literals"),
		TUPLE_OF_OBJECTS.`as`("Local types"),
		TUPLE_OF_OBJECTS.`as`("Constant types"),
		TUPLE_OF_OBJECTS.`as`("Outer types"),
		OBJECT_REFERENCE.`as`("Module name"),
		UNSIGNED_INT.`as`("Line number"),
		COMPRESSED_INT_TUPLE.`as`("Encoded line number deltas"),
		OBJECT_REFERENCE.`as`("Originating phrase"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			val numLocals = `object`.numLocals()
			val numConstants = `object`.numConstants()
			val numOuters = `object`.numOuters()
			val numRegularLiterals =
				`object`.numLiterals() - numConstants - numLocals - numOuters
			val regularLiterals = generateObjectTupleFrom(numRegularLiterals) {
				`object`.literalAt(it)
			}
			val localTypes = generateObjectTupleFrom(numLocals) {
				`object`.localTypeAt(it)
			}
			val constantTypes = generateObjectTupleFrom(numConstants) {
				`object`.constantTypeAt(it)
			}
			val outerTypes = generateObjectTupleFrom(numOuters) {
				`object`.outerTypeAt(it)
			}
			val module = `object`.module()
			val moduleName = if (module.equalsNil())
				emptyTuple()
			else
				module.moduleName()
			val primitive = `object`.primitive()
			val primName: A_String
			primName =
				if (primitive == null)
				{
					emptyTuple()
				}
				else
				{
					stringFrom(primitive.name())
				}
			return array(
				fromInt(`object`.numSlots()),
				primName,
				`object`.functionType(),
				`object`.nybbles(),
				regularLiterals,
				localTypes,
				constantTypes,
				outerTypes,
				moduleName,
				fromInt(`object`.startingLineNumber()),
				`object`.lineNumberEncodedDeltas(),
				`object`.originatingPhrase())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val numSlots = subobjects[0].extractInt()
			val primitive = subobjects[1]
			val functionType = subobjects[2]
			val nybbles = subobjects[3]
			val regularLiterals = subobjects[4]
			val localTypes = subobjects[5]
			val constantTypes = subobjects[6]
			val outerTypes = subobjects[7]
			val moduleName = subobjects[8]
			val lineNumberInteger = subobjects[9]
			val lineNumberEncodedDeltas = subobjects[10]
			val originatingPhrase = subobjects[11]

			val numArgsRange = functionType.argsTupleType().sizeRange()
			val numArgs = numArgsRange.lowerBound().extractInt()
			assert(numArgsRange.upperBound().extractInt() == numArgs)
			val numLocals = localTypes.tupleSize()
			val numConstants = constantTypes.tupleSize()

			val module = if (moduleName.tupleSize() == 0)
				nil
			else
				deserializer.moduleNamed(moduleName)
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
				originatingPhrase)
		}
	},

	/**
	 * A [function][FunctionDescriptor] with no outer (lexically captured)
	 * variables.
	 */
	CLEAN_FUNCTION(37, OBJECT_REFERENCE.`as`("Compiled code"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			assert(`object`.numOuterVars() == 0)
			return array(
				`object`.code())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val code = subobjects[0]
			return createFunction(code, emptyTuple())
		}
	},

	/**
	 * A [function][FunctionDescriptor] with one or more outer (lexically
	 * captured) variables.
	 */
	GENERAL_FUNCTION(
		38,
		OBJECT_REFERENCE.`as`("Compiled code"),
		TUPLE_OF_OBJECTS.`as`("Outer values"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			val outers = generateObjectTupleFrom(`object`.numOuterVars()) {
				`object`.outerVarAt(it)
			}
			return array(
				`object`.code(),
				outers)
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val code = subobjects[0]
			val outers = subobjects[1]
			return createFunction(code, outers)
		}
	},

	/**
	 * A non-global [variable][VariableDescriptor].  Deserialization
	 * reconstructs a new one, since there's no way to know where the original
	 * one came from.
	 */
	LOCAL_VARIABLE(39, OBJECT_REFERENCE.`as`("variable type"))
	{
		override val isVariableCreation: Boolean
			// This was a local variable, so answer true, indicating that we
			// also need to serialize the content.  If the variable had instead
			// been looked up (as for a GLOBAL_VARIABLE), we would answer false
			// to indicate that the variable had already been initialized
			// elsewhere.
			get() = true

		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			assert(!`object`.isGlobal)
			return array(`object`.kind())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return newVariableWithOuterType(subobjects[0])
		}
	},

	/**
	 * A global variable.  It's serialized as the type, the module that defined
	 * it, the variable's name within that module, and a flag byte.
	 *
	 * The flag byte has these fields:
	 *
	 *  1. Whether this is a write-once variable.
	 *  1. Whether the write-once variable's value was computed by a stable
	 *     computation.
	 *
	 * A global variable is marked as such when constructed by the compiler. The
	 * write-once flag is also specified at that time as well.  To reconstruct a
	 * global variable, it's simply looked up by name in its defining module.
	 * The variable must exist (or the serialization is malformed), so modules
	 * must ensure all their global variables and constants are reconstructed
	 * when loading from serialized form.
	 */
	GLOBAL_VARIABLE(
		40,
		OBJECT_REFERENCE.`as`("variable type"),
		OBJECT_REFERENCE.`as`("module name"),
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.`as`("variable name"),
		BYTE.`as`("flags"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			assert(`object`.isGlobal)
			val flags =
				(if (`object`.isInitializedWriteOnceVariable) 1 else 0) +
					if (`object`.valueWasStablyComputed()) 2 else 0
			return array(
				`object`.kind(),
				`object`.globalModule(),
				`object`.globalName(),
				fromInt(flags))
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val varType = subobjects[0]
			val module = subobjects[1]
			val varName = subobjects[2]
			val flags = subobjects[3].extractInt()

			val writeOnce = flags and 1 != 0
			val stablyComputed = flags and 2 != 0
			val variable =
				if (writeOnce)
					module.constantBindings().mapAt(varName)
				else
					module.variableBindings().mapAt(varName)
			if (stablyComputed != variable.valueWasStablyComputed())
			{
				throw RuntimeException(
					"Disagreement about whether a module constant was stably" +
						" computed.")
			}
			if (!varType.equals(variable.kind()))
			{
				throw RuntimeException(
					"Disagreement about global variable's type.")
			}
			return variable
		}
	},

	/**
	 * A [set][SetDescriptor].  Convert it to a tuple and work with that,
	 * converting it back to a set when deserializing.
	 */
	SET(41, TUPLE_OF_OBJECTS.`as`("tuple of objects"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(`object`.asTuple())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return subobjects[0].asSet()
		}
	},

	/**
	 * A [token][TokenDescriptor].
	 */
	TOKEN(
		42,
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.`as`("token string"),
		SIGNED_INT.`as`("start position"),
		SIGNED_INT.`as`("line number"),
		BYTE.`as`("token type code"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				`object`.string(),
				fromInt(`object`.start()),
				fromInt(`object`.lineNumber()),
				fromInt(`object`.tokenType().ordinal))
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val string = subobjects[0]
			val start = subobjects[1].extractInt()
			val lineNumber = subobjects[2].extractInt()
			val tokenTypeOrdinal = subobjects[3].extractInt()
			return newToken(
				string,
				start,
				lineNumber,
				lookupTokenType(tokenTypeOrdinal))
		}
	},

	/**
	 * A [literal token][LiteralTokenDescriptor].
	 */
	LITERAL_TOKEN(
		43,
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.`as`("token string"),
		OBJECT_REFERENCE.`as`("literal value"),
		SIGNED_INT.`as`("start position"),
		SIGNED_INT.`as`("line number"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				`object`.string(),
				`object`.literal(),
				fromInt(`object`.start()),
				fromInt(`object`.lineNumber()))
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val string = subobjects[0]
			val literal = subobjects[1]
			val start = subobjects[2].extractInt()
			val lineNumber = subobjects[3].extractInt()
			return literalToken(
				string,
				start,
				lineNumber,
				literal)
		}
	},

	/**
	 * A [token][TokenDescriptor].
	 */
	COMMENT_TOKEN(
		44,
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.`as`("token string"),
		SIGNED_INT.`as`("start position"),
		SIGNED_INT.`as`("line number"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				`object`.string(),
				fromInt(`object`.start()),
				fromInt(`object`.lineNumber()))
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val string = subobjects[0]
			val start = subobjects[1].extractInt()
			val lineNumber = subobjects[2].extractInt()
			return newCommentToken(string, start, lineNumber)
		}
	},

	/**
	 * This special opcode causes a previously built variable to have a
	 * previously built value to be assigned to it at this point during
	 * deserialization.
	 */
	ASSIGN_TO_VARIABLE(
		45,
		OBJECT_REFERENCE.`as`("variable to assign"),
		OBJECT_REFERENCE.`as`("value to assign"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				`object`,
				`object`.value())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val variable = subobjects[0]
			val value = subobjects[1]
			variable.setValue(value)
			return nil
		}
	},

	/**
	 * The representation of a continuation, which is just its level one state.
	 */
	CONTINUATION(
		46,
		OBJECT_REFERENCE.`as`("calling continuation"),
		OBJECT_REFERENCE.`as`("continuation's function"),
		TUPLE_OF_OBJECTS.`as`("continuation frame slots"),
		COMPRESSED_SHORT.`as`("program counter"),
		COMPRESSED_SHORT.`as`("stack pointer"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			val frameSlotCount = `object`.numSlots()
			val frameSlotsList = ArrayList<AvailObject>(frameSlotCount)
			for (i in 1..frameSlotCount)
			{
				frameSlotsList.add(`object`.argOrLocalOrStackAt(i))
			}
			return array(
				`object`.caller(),
				`object`.function(),
				tupleFromList(frameSlotsList),
				fromInt(`object`.pc()),
				fromInt(`object`.stackp()))
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val caller = subobjects[0]
			val function = subobjects[1]
			val frameSlots = subobjects[2]
			val pcInteger = subobjects[3]
			val stackpInteger = subobjects[4]
			val continuation = createContinuationWithFrame(
				function,
				caller,
				pcInteger.extractInt(),
				stackpInteger.extractInt(),
				unoptimizedChunk,
				if (pcInteger.equalsInt(0))
					TO_RESTART.offsetInDefaultChunk
				else
					TO_RETURN_INTO.offsetInDefaultChunk,
				toList(frameSlots),
				0)
			return continuation.makeImmutable()
		}
	},

	/**
	 * A reference to a [method][MethodDescriptor] that should be looked up
	 * during deserialization.  A method can have multiple [message
	 * bundles][MessageBundleDescriptor], and *each* <module name, atom name>
	 * pair is recorded during serialization. For system atoms we output nil for
	 * the module name.  During deserialization, the list is searched for a
	 * module that has been loaded, and if the corresponding name is an atom,
	 * and if that atom has a bundle associated with it, that bundle's method is
	 * used.
	 */
	METHOD(47, TUPLE_OF_OBJECTS.`as`("module name / atom name pairs"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			assert(`object`.isInstanceOf(TypeDescriptor.Types.METHOD.o()))
			val pairs = ArrayList<A_Tuple>()
			for (bundle in `object`.bundles())
			{
				val atom = bundle.message()
				val module = atom.issuingModule()
				if (!module.equalsNil())
				{
					pairs.add(
						tuple(module.moduleName(), atom.atomName()))
				}
				else
				{
					pairs.add(tuple(nil, atom.atomName()))
				}
			}
			return array(tupleFromList(pairs))
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val pairs = subobjects[0]
			val runtime = deserializer.runtime
			for (pair in pairs)
			{
				assert(pair.tupleSize() == 2)
				val moduleName = pair.tupleAt(1)
				val atomName = pair.tupleAt(2)
				if (!moduleName.equalsNil() && runtime.includesModuleNamed(
						moduleName))
				{
					val atom = lookupAtom(atomName, moduleName, deserializer)
					val bundle = atom.bundleOrNil()
					if (!bundle.equalsNil())
					{
						return bundle.bundleMethod()
					}
				}
			}
			// Look it up as a special atom instead.
			for (pair in pairs)
			{
				assert(pair.tupleSize() == 2)
				val moduleName = pair.tupleAt(1)
				val atomName = pair.tupleAt(2)
				if (moduleName.equalsNil())
				{
					val specialAtom = Serializer.specialAtomsByName[atomName]
					if (specialAtom != null)
					{
						val bundle = specialAtom.bundleOrNil()
						if (!bundle.equalsNil())
						{
							return bundle.bundleMethod()
						}
					}
					throw RuntimeException(
						"Method could not be found by name as a special atom " +
							"bundle")
				}
			}
			throw RuntimeException(
				"None of method's bundle-defining modules were loaded")
		}
	},

	/**
	 * A reference to a [method][MethodDefinitionDescriptor], which should be
	 * reconstructed by looking it up.
	 */
	METHOD_DEFINITION(
		48,
		OBJECT_REFERENCE.`as`("method"),
		OBJECT_REFERENCE.`as`("signature"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			assert(`object`.isMethodDefinition)
			return array(
				`object`.definitionMethod(),
				`object`.bodySignature())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_Definition
		{
			val definitionMethod = subobjects[0]
			val signature = subobjects[1]
			val definitions = ArrayList<AvailObject>(1)
			for (eachDefinition in definitionMethod.definitionsTuple())
			{
				if (eachDefinition.bodySignature().equals(signature))
				{
					definitions.add(eachDefinition)
				}
			}
			assert(definitions.size == 1)
			val definition = definitions[0]
			assert(definition.isMethodDefinition)
			return definition
		}
	},

	/**
	 * A reference to a [macro][MacroDefinitionDescriptor], which should be
	 * reconstructed by looking it up.
	 */
	MACRO_DEFINITION(
		49,
		OBJECT_REFERENCE.`as`("method"),
		OBJECT_REFERENCE.`as`("signature"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			assert(`object`.isMacroDefinition)
			return array(
				`object`.definitionMethod(),
				`object`.bodySignature())
		}

		@Suppress("RemoveRedundantQualifierName")
		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val definitionMethod = subobjects[0]
			val signature = subobjects[1]
			val definitions = ArrayList<AvailObject>(1)
			for (eachDefinition in definitionMethod.definitionsTuple())
			{
				if (eachDefinition.bodySignature().equals(signature))
				{
					definitions.add(eachDefinition)
				}
			}
			assert(definitions.size == 1)
			val definition = definitions[0]
			assert(definition.isMacroDefinition)
			return definition
		}
	},

	/**
	 * A reference to an [abstract][AbstractDefinitionDescriptor], which should be reconstructed by looking it up.
	 */
	ABSTRACT_DEFINITION(
		50,
		OBJECT_REFERENCE.`as`("method"),
		OBJECT_REFERENCE.`as`("signature"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			assert(`object`.isAbstractDefinition)
			return array(
				`object`.definitionMethod(),
				`object`.bodySignature())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val definitionMethod = subobjects[0]
			val signature = subobjects[1]
			val definitions = ArrayList<AvailObject>(1)
			for (eachDefinition in definitionMethod.definitionsTuple())
			{
				if (eachDefinition.bodySignature().equals(signature))
				{
					definitions.add(eachDefinition)
				}
			}
			assert(definitions.size == 1)
			val definition = definitions[0]
			assert(definition.isAbstractDefinition)
			return definition
		}
	},

	/**
	 * A reference to a [forward][ForwardDefinitionDescriptor], which should be reconstructed by looking it up.
	 */
	FORWARD_DEFINITION(
		51,
		OBJECT_REFERENCE.`as`("method"),
		OBJECT_REFERENCE.`as`("signature"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			assert(`object`.isForwardDefinition)
			return array(
				`object`.definitionMethod(),
				`object`.bodySignature())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val definitionMethod = subobjects[0]
			val signature = subobjects[1]
			val definitions = ArrayList<AvailObject>(1)
			for (eachDefinition in definitionMethod.definitionsTuple())
			{
				if (eachDefinition.bodySignature().equals(signature))
				{
					definitions.add(eachDefinition)
				}
			}
			assert(definitions.size == 1)
			val definition = definitions[0]
			assert(definition.isForwardDefinition)
			return definition
		}
	},

	/**
	 * A reference to a [message bundle][MessageBundleDescriptor],
	 * which should be reconstructed by looking it up.
	 */
	MESSAGE_BUNDLE(52, OBJECT_REFERENCE.`as`("message atom"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				`object`.message())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val atom = subobjects[0]
			try
			{
				return atom.bundleOrCreate()
			}
			catch (e: MalformedMessageException)
			{
				throw RuntimeException(
					"Bundle should not have been serialized with malformed " +
						"message")
			}

		}
	},

	/**
	 * A reference to a module, possibly the one being constructed.
	 */
	MODULE(53, OBJECT_REFERENCE.`as`("module name"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(`object`.moduleName())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val moduleName = subobjects[0]
			val currentModule = deserializer.currentModule
			return if (currentModule.moduleName().equals(moduleName))
			{
				currentModule
			}
			else deserializer.runtime.moduleAt(moduleName)
		}
	},

	/**
	 * An [atom][AtomDescriptor] which is used for creating explicit subclasses.
	 * Output the atom name and the name of the module that issued it.  Look up
	 * the corresponding atom during reconstruction, recreating it if it's not
	 * present and supposed to have been issued by the current module.
	 *
	 * This should be the same as [ATOM], other than adding the special
	 * [SpecialAtom.EXPLICIT_SUBCLASSING_KEY] property.
	 */
	EXPLICIT_SUBCLASS_ATOM(
		54,
		OBJECT_REFERENCE.`as`("atom name"),
		OBJECT_REFERENCE.`as`("module name"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			serializer.checkAtom(`object`)
			assert(
				`object`.getAtomProperty(HERITABLE_KEY.atom)
					.equalsNil())
			assert(
				`object`.getAtomProperty(EXPLICIT_SUBCLASSING_KEY.atom)
					.equals(EXPLICIT_SUBCLASSING_KEY.atom))
			val module = `object`.issuingModule()
			if (module.equalsNil())
			{
				throw RuntimeException("Atom has no issuing module")
			}
			return array(`object`.atomName(), module.moduleName())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val atomName = subobjects[0]
			val moduleName = subobjects[1]
			val atom = lookupAtom(atomName, moduleName, deserializer)
			atom.setAtomProperty(
				EXPLICIT_SUBCLASSING_KEY.atom,
				EXPLICIT_SUBCLASSING_KEY.atom)
			return atom.makeShared()
		}
	},

	/**
	 * The [raw pojo][RawPojoDescriptor] for the Java `null`
	 * value.
	 */
	RAW_POJO_NULL(55)
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array()
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return rawNullPojo()
		}
	},

	/**
	 * The serialization of a raw (unparameterized) Java
	 * [primitive][Class.isPrimitive] [Class].
	 */
	RAW_NONPRIMITIVE_JAVA_CLASS(
		56,
		OBJECT_REFERENCE.`as`("class name"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			val javaClass = `object`.javaObjectNotNull<Class<*>>()
			return array(
				stringFrom(javaClass.name))
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val className = subobjects[0]
			val javaClass = deserializer.runtime.lookupRawJavaClass(className)
			return equalityPojo(javaClass)
		}
	},

	/**
	 * An instance of [Method], likely created as part of
	 * [P_CreatePojoInstanceMethodFunction]. The method may be an instance
	 * method or a static method.
	 */
	RAW_POJO_METHOD(
		57,
		OBJECT_REFERENCE.`as`("declaring class pojo"),
		OBJECT_REFERENCE.`as`("method name string"),
		OBJECT_REFERENCE.`as`("marshaled argument pojo types"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			val method = `object`.javaObjectNotNull<Method>()
			val declaringClass = method.declaringClass
			val methodName = method.name
			val argumentTypes = method.parameterTypes

			return array(
				equalityPojo(declaringClass),
				stringFrom(methodName),
				generateObjectTupleFrom(
					argumentTypes.size
				) { i -> equalityPojo(argumentTypes[i - 1]) })
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val receiverType = subobjects[0]
			val methodName = subobjects[1]
			val argumentTypes = subobjects[2]

			val receiverClass = receiverType.javaObjectNotNull<Class<*>>()
			val argumentClasses = marshalTypes(argumentTypes)
			try
			{
				return equalityPojo(
					receiverClass.getMethod(
						methodName.asNativeString(), *argumentClasses))
			}
			catch (e: NoSuchMethodException)
			{
				throw AvailRuntimeException(E_JAVA_METHOD_NOT_AVAILABLE)
			}

		}
	},

	/**
	 * An instance of [Constructor], likely created as part of
	 * [P_CreatePojoConstructorFunction].
	 */
	RAW_POJO_CONSTRUCTOR(
		58,
		OBJECT_REFERENCE.`as`("declaring class pojo"),
		OBJECT_REFERENCE.`as`("marshaled argument pojo types"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			val constructor = `object`.javaObjectNotNull<Constructor<*>>()
			val declaringClass = constructor.declaringClass
			val argumentTypes = constructor.parameterTypes

			return array(
				equalityPojo(declaringClass),
				generateObjectTupleFrom(
					argumentTypes.size
				) { i -> equalityPojo(argumentTypes[i - 1]) })
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val receiverType = subobjects[0]
			val argumentTypes = subobjects[1]

			val receiverClass = receiverType.javaObjectNotNull<Class<*>>()
			val argumentClasses = marshalTypes(argumentTypes)
			try
			{
				return equalityPojo(
					receiverClass.getConstructor(*argumentClasses))
			}
			catch (e: NoSuchMethodException)
			{
				throw AvailRuntimeException(E_JAVA_METHOD_NOT_AVAILABLE)
			}

		}
	},

	/**
	 * The serialization of a raw (unparameterized) Java
	 * [primitive][Class.isPrimitive] [Class].
	 */
	RAW_PRIMITIVE_JAVA_CLASS(
		59,
		OBJECT_REFERENCE.`as`("primitive class name"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			val javaClass = `object`.javaObjectNotNull<Class<*>>()
			return array(stringFrom(javaClass.name))
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val className = subobjects[0]
			val javaClass: Class<*>
			when (className.asNativeString())
			{
				"void" -> javaClass = Void.TYPE
				"boolean" -> javaClass = java.lang.Boolean.TYPE
				"byte" -> javaClass = java.lang.Byte.TYPE
				"short" -> javaClass = java.lang.Short.TYPE
				"int" -> javaClass = Integer.TYPE
				"long" -> javaClass = java.lang.Long.TYPE
				"char" -> javaClass = Character.TYPE
				"float" -> javaClass = java.lang.Float.TYPE
				"double" -> javaClass = java.lang.Double.TYPE
				else ->
				{
					assert(false) {
						("There are only nine primitive types " +
							"(and $className is not one of them)!")
					}
					throw RuntimeException()
				}
			}
			return equalityPojo(javaClass)
		}
	},

	/**
	 * An [assignment phrase][AssignmentPhraseDescriptor].
	 */
	ASSIGNMENT_PHRASE(
		60,
		BYTE.`as`("flags"),
		OBJECT_REFERENCE.`as`("variable"),
		OBJECT_REFERENCE.`as`("expression"),
		OBJECT_REFERENCE.`as`("tokens"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			val isInline = isInline(`object`)
			return array(
				fromInt(if (isInline) 1 else 0),
				`object`.variable(),
				`object`.expression(),
				`object`.tokens())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val isInline = subobjects[0]
			val variableUse = subobjects[1]
			val expression = subobjects[2]
			val tokens = subobjects[3]
			return newAssignment(
				variableUse, expression, tokens, !isInline.equalsInt(0))
		}
	},

	/**
	 * A [block phrase][BlockPhraseDescriptor].
	 */
	BLOCK_PHRASE(
		61,
		TUPLE_OF_OBJECTS.`as`("arguments tuple"),
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.`as`("primitive name"),
		TUPLE_OF_OBJECTS.`as`("statements tuple"),
		OBJECT_REFERENCE.`as`("result type"),
		TUPLE_OF_OBJECTS.`as`("declared exceptions"),
		UNSIGNED_INT.`as`("starting line number"),
		TUPLE_OF_OBJECTS.`as`("tokens"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			val primitive = `object`.primitive()
			val primitiveName = if (primitive == null)
				emptyTuple()
			else
				stringFrom(primitive.name())
			return array(
				`object`.argumentsTuple(),
				primitiveName,
				`object`.statementsTuple(),
				`object`.resultType(),
				`object`.declaredExceptions().asTuple(),
				fromInt(`object`.startingLineNumber()),
				`object`.tokens())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val argumentsTuple = subobjects[0]
			val primitiveName = subobjects[1]
			val statementsTuple = subobjects[2]
			val resultType = subobjects[3]
			val declaredExceptionsTuple = subobjects[4]
			val startingLineNumber = subobjects[5]
			val tokens = subobjects[6]
			val primitiveNumber: Int
			primitiveNumber =
				if (primitiveName.tupleSize() == 0)
				{
					0
				}
				else
				{
					val primitive =
						stripNull(primitiveByName(primitiveName.asNativeString()))
					primitive.primitiveNumber
				}
			return newBlockNode(
				argumentsTuple,
				primitiveNumber,
				statementsTuple,
				resultType,
				declaredExceptionsTuple.asSet(),
				startingLineNumber.extractInt(),
				tokens)
		}
	},

	/**
	 * A [declaration phrase][DeclarationPhraseDescriptor].
	 */
	DECLARATION_PHRASE(
		62,
		BYTE.`as`("declaration kind ordinal"),
		OBJECT_REFERENCE.`as`("token"),
		OBJECT_REFERENCE.`as`("declared type"),
		OBJECT_REFERENCE.`as`("type expression"),
		OBJECT_REFERENCE.`as`("initialization expression"),
		OBJECT_REFERENCE.`as`("literal object"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			val kind = `object`.declarationKind()
			val token = `object`.token()
			val declaredType = `object`.declaredType()
			val typeExpression = `object`.typeExpression()
			val initializationExpression = `object`.initializationExpression()
			val literalObject = `object`.literalObject()
			return array(
				fromInt(kind.ordinal),
				token,
				declaredType,
				typeExpression,
				initializationExpression,
				literalObject)
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val declarationKindNumber = subobjects[0]
			val token = subobjects[1]
			val declaredType = subobjects[2]
			val typeExpression = subobjects[3]
			val initializationExpression = subobjects[4]
			val literalObject = subobjects[5]

			val declarationKind =
				DeclarationKind.lookup(declarationKindNumber.extractInt())
			return newDeclaration(
				declarationKind,
				token,
				declaredType,
				typeExpression,
				initializationExpression,
				literalObject)
		}
	},

	/**
	 * An [expression-as-statement][ExpressionAsStatementPhraseDescriptor].
	 */
	EXPRESSION_AS_STATEMENT_PHRASE(63, OBJECT_REFERENCE.`as`("expression"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(`object`.expression())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val expression = subobjects[0]
			return newExpressionAsStatement(expression)
		}
	},

	/**
	 * A [first-of-sequence phrase][FirstOfSequencePhraseDescriptor].
	 */
	FIRST_OF_SEQUENCE_PHRASE(64, TUPLE_OF_OBJECTS.`as`("statements"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(`object`.statements())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val statements = subobjects[0]
			return newFirstOfSequenceNode(statements)
		}
	},

	/**
	 * A [list phrase][ListPhraseDescriptor].
	 */
	LIST_PHRASE(65, TUPLE_OF_OBJECTS.`as`("expressions"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(`object`.expressionsTuple())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val expressionsTuple = subobjects[0]
			return newListNode(expressionsTuple)
		}
	},

	/**
	 * A [literal phrase][LiteralPhraseDescriptor].
	 */
	LITERAL_PHRASE(66, OBJECT_REFERENCE.`as`("literal token"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(`object`.token())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val literalToken = subobjects[0]
			return literalNodeFromToken(literalToken)
		}
	},

	/**
	 * A [macro substitution phrase][MacroSubstitutionPhraseDescriptor].
	 */
	MACRO_SUBSTITUTION_PHRASE(
		67,
		OBJECT_REFERENCE.`as`("original phrase"),
		OBJECT_REFERENCE.`as`("output phrase"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				`object`.macroOriginalSendNode(),
				`object`.outputPhrase())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val macroOriginalSendPhrase = subobjects[0]
			val outputPhrase = subobjects[1]
			return newMacroSubstitution(macroOriginalSendPhrase, outputPhrase)
		}
	},

	/**
	 * A [permuted list phrase][PermutedListPhraseDescriptor].
	 */
	PERMUTED_LIST_PHRASE(
		68,
		OBJECT_REFERENCE.`as`("list phrase"),
		TUPLE_OF_OBJECTS.`as`("permutation"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				`object`.list(),
				`object`.permutation())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val list = subobjects[0]
			val permutation = subobjects[1]
			return newPermutedListNode(list, permutation)
		}
	},

	/**
	 * A [permuted list phrase][PermutedListPhraseDescriptor].
	 */
	REFERENCE_PHRASE(69, OBJECT_REFERENCE.`as`("variable use"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(`object`.variable())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val variableUse = subobjects[0]
			return referenceNodeFromUse(variableUse)
		}
	},

	/**
	 * A [send phrase][SendPhraseDescriptor].
	 */
	SEND_PHRASE(
		70,
		OBJECT_REFERENCE.`as`("bundle"),
		OBJECT_REFERENCE.`as`("arguments list phrase"),
		OBJECT_REFERENCE.`as`("return type"),
		TUPLE_OF_OBJECTS.`as`("tokens"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				`object`.bundle(),
				`object`.argumentsListNode(),
				`object`.expressionType(),
				`object`.tokens())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val bundle = subobjects[0]
			val argsListNode = subobjects[1]
			val returnType = subobjects[2]
			val tokens = subobjects[3]
			return newSendNode(tokens, bundle, argsListNode, returnType)
		}
	},

	/**
	 * A [sequence phrase][SequencePhraseDescriptor].
	 */
	SEQUENCE_PHRASE(71, TUPLE_OF_OBJECTS.`as`("statements"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(`object`.statements())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val statements = subobjects[0]
			return newSequence(statements)
		}
	},

	/**
	 * A [super cast phrase][SuperCastPhraseDescriptor].
	 */
	SUPER_CAST_PHRASE(
		72,
		OBJECT_REFERENCE.`as`("expression"),
		OBJECT_REFERENCE.`as`("type for lookup"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				`object`.expression(),
				`object`.superUnionType())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val expression = subobjects[0]
			val superUnionType = subobjects[1]
			return newSuperCastNode(expression, superUnionType)
		}
	},

	/**
	 * A [variable use phrase][VariableUsePhraseDescriptor].
	 */
	VARIABLE_USE_PHRASE(
		73,
		OBJECT_REFERENCE.`as`("use token"),
		OBJECT_REFERENCE.`as`("declaration"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				`object`.token(),
				`object`.declaration())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val token = subobjects[0]
			val declaration = subobjects[1]
			return newUse(token, declaration)
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
		BYTE.`as`("primitive type ordinal"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(fromInt(extractOrdinal(`object`)))
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return TypeDescriptor.Types.all()[subobjects[0].extractInt()].o()
		}
	},

	/**
	 * A [variable][A_Variable] bound to a `static` Java field.
	 */
	STATIC_POJO_FIELD(
		75,
		OBJECT_REFERENCE.`as`("class name"),
		OBJECT_REFERENCE.`as`("field name"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			assert(`object`.descriptor() is PojoFinalFieldDescriptor)
			val field = `object`
				.slot(PojoFinalFieldDescriptor.ObjectSlots.FIELD)
				.javaObjectNotNull<Field>()
			val definingClass = field.declaringClass
			val className = stringFrom(definingClass.name)
			val fieldName = stringFrom(field.name)
			return array(className, fieldName)
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			try
			{
				val classLoader = deserializer.runtime.classLoader()
				val definingClass = Class.forName(
					subobjects[0].asNativeString(), true, classLoader)
				val field = definingClass.getField(
					subobjects[1].asNativeString())
				assert(field.modifiers and Modifier.STATIC != 0)
				val fieldType = resolvePojoType(
					field.genericType, emptyMap())
				return pojoFieldVariableForInnerType(
					equalityPojo(field), rawNullPojo(), fieldType)
			}
			catch (e: ClassNotFoundException)
			{
				throw RuntimeException(e)
			}
			catch (e: NoSuchFieldException)
			{
				throw RuntimeException(e)
			}

		}
	},

	/**
	 * Reserved for future use.
	 */
	@Suppress("unused") RESERVED_76(76)
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			throw RuntimeException("Reserved serializer operation")
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			throw RuntimeException("Reserved serializer operation")
		}
	},

	/**
	 * Reserved for future use.
	 */
	@Suppress("unused") RESERVED_77(77)
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			throw RuntimeException("Reserved serializer operation")
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			throw RuntimeException("Reserved serializer operation")
		}
	},

	/**
	 * Reserved for future use.
	 */
	@Suppress("unused") RESERVED_78(78)
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			throw RuntimeException("Reserved serializer operation")
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			throw RuntimeException("Reserved serializer operation")
		}
	},

	/**
	 * Reserved for future use.
	 */
	@Suppress("unused") RESERVED_79(79)
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			throw RuntimeException("Reserved serializer operation")
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			throw RuntimeException("Reserved serializer operation")
		}
	},

	/**
	 * A [fiber type][FiberTypeDescriptor].
	 */
	FIBER_TYPE(80, OBJECT_REFERENCE.`as`("Result type"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(`object`.resultType())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val resultType = subobjects[0]
			return fiberType(resultType)
		}
	},

	/**
	 * A [function type][FunctionTypeDescriptor].
	 */
	FUNCTION_TYPE(
		81,
		OBJECT_REFERENCE.`as`("Arguments tuple type"),
		OBJECT_REFERENCE.`as`("Return type"),
		TUPLE_OF_OBJECTS.`as`("Checked exceptions"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				`object`.argsTupleType(),
				`object`.returnType(),
				`object`.declaredExceptions().asTuple())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val argsTupleType = subobjects[0]
			val returnType = subobjects[1]
			val checkedExceptionsTuple = subobjects[2]
			return functionTypeFromArgumentTupleType(
				argsTupleType, returnType, checkedExceptionsTuple.asSet())
		}
	},

	/**
	 * A [tuple type][TupleTypeDescriptor].
	 */
	TUPLE_TYPE(
		82,
		OBJECT_REFERENCE.`as`("Tuple sizes"),
		TUPLE_OF_OBJECTS.`as`("Leading types"),
		OBJECT_REFERENCE.`as`("Default type"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				`object`.sizeRange(),
				`object`.typeTuple(),
				`object`.defaultType())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val sizeRange = subobjects[0]
			val typeTuple = subobjects[1]
			val defaultType = subobjects[2]
			return tupleTypeForSizesTypesDefaultType(
				sizeRange, typeTuple, defaultType)
		}
	},

	/**
	 * An [integer range type][IntegerRangeTypeDescriptor].
	 */
	INTEGER_RANGE_TYPE(
		83,
		BYTE.`as`("Inclusive flags"),
		OBJECT_REFERENCE.`as`("Lower bound"),
		OBJECT_REFERENCE.`as`("Upper bound"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			val flags =
				(if (`object`.lowerInclusive()) 1 else 0) +
					if (`object`.upperInclusive()) 2 else 0
			return array(
				fromInt(flags),
				`object`.lowerBound(),
				`object`.upperBound())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val flags = subobjects[0].extractUnsignedByte().toInt()
			val lowerBound = subobjects[1]
			val upperBound = subobjects[2]
			val lowerInclusive = flags and 1 != 0
			val upperInclusive = flags and 2 != 0
			return integerRangeType(
				lowerBound, lowerInclusive, upperBound, upperInclusive)
		}
	},

	/**
	 * A [pojo type][PojoTypeDescriptor] for which [AvailObject.isPojoFusedType]
	 * is `false`.  This indicates a representation with a juicy class filling,
	 * which allows a particularly compact representation involving the class
	 * name and its parameter types.
	 *
	 * A self pojo type may appear in the parameterization of this class.
	 * Convert such a self type into a 1-tuple containing the self type's class
	 * name.  We can't rely on a self pojo type being able to create a proxy for
	 * itself during serialization, because it is required to be equal to the
	 * (non-self) type which it parameterizes, leading to problems when
	 * encountering the self type during tracing.
	 *
	 */
	UNFUSED_POJO_TYPE(
		84,
		OBJECT_REFERENCE.`as`("class name"),
		TUPLE_OF_OBJECTS.`as`("class parameterization"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			assert(`object`.isPojoType)
			assert(!`object`.isPojoFusedType)
			val rawPojoType = `object`.javaClass()
			val baseClass = rawPojoType.javaObjectNotNull<Class<*>>()
			val className = stringFrom(baseClass.name)
			val ancestorMap = `object`.javaAncestors()
			val myParameters = ancestorMap.mapAt(rawPojoType)
			val processedParameters =
				ArrayList<A_BasicObject>(myParameters.tupleSize())
			for (parameter in myParameters)
			{
				assert(!parameter.isTuple)
				if (parameter.isPojoSelfType)
				{
					processedParameters.add(pojoSerializationProxy(parameter))
				}
				else
				{
					processedParameters.add(parameter)
				}
			}
			return array(
				className,
				tupleFromList(processedParameters))
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val processedParameters =
				ArrayList<AvailObject>(subobjects[1].tupleSize())
			val classLoader = deserializer.runtime.classLoader()
			try
			{
				for (parameter in subobjects[1])
				{
					processedParameters.add(
						if (parameter.isTuple)
							pojoFromSerializationProxy(parameter, classLoader)
						else
							parameter)
				}
				val baseClass = Class.forName(
					subobjects[0].asNativeString(), true, classLoader)
				return pojoTypeForClassWithTypeArguments(
					baseClass, tupleFromList(processedParameters))
			}
			catch (e: ClassNotFoundException)
			{
				throw RuntimeException(e)
			}

		}
	},

	/**
	 * A [pojo type][PojoTypeDescriptor] for which [AvailObject.isPojoFusedType]
	 * is `true`.  This indicates a representation without the juicy class
	 * filling, so we have to say how each ancestor is parameterized.
	 *
	 * We have to pre-convert self pojo types in the parameterizations map,
	 * otherwise one might be encountered during traversal.  This is bad because
	 * the self pojo type can be equal to another (non-self) pojo type, and in
	 * fact almost certainly will be equal to a previously encountered object (a
	 * pojo type that it's embedded in), so the serializer will think this is a
	 * cyclic structure.  To avoid this, we convert any occurrence of a self
	 * type into a tuple of size one, containing the name of the java class or
	 * interface name.  This is enough to reconstruct the self pojo type.
	 */
	FUSED_POJO_TYPE(85, GENERAL_MAP.`as`("ancestor parameterizations map"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			assert(`object`.isPojoType)
			assert(`object`.isPojoFusedType)
			var symbolicMap = emptyMap()
			for (entry in `object`.javaAncestors().mapIterable())
			{
				val baseClass = entry.key().javaObjectNotNull<Class<*>>()
				val className = stringFrom(baseClass.name)
				val processedParameters =
					ArrayList<A_BasicObject>(entry.value().tupleSize())
				for (parameter in entry.value())
				{
					assert(!parameter.isTuple)
					if (parameter.isPojoSelfType)
					{
						processedParameters.add(
							pojoSerializationProxy(parameter))
					}
					else
					{
						processedParameters.add(parameter)
					}
				}
				symbolicMap = symbolicMap.mapAtPuttingCanDestroy(
					className, tupleFromList(processedParameters), true)
			}
			return array(symbolicMap)
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val classLoader = deserializer.runtime.classLoader()
			var ancestorMap = emptyMap()
			try
			{
				for (entry in subobjects[0].mapIterable())
				{
					val baseClass = Class.forName(
						entry.key().asNativeString(), true, classLoader)
					val rawPojo = equalityPojo(baseClass)
					val processedParameters =
						ArrayList<AvailObject>(entry.value().tupleSize())
					for (parameter in entry.value())
					{
						if (parameter.isTuple)
						{
							processedParameters.add(
								pojoFromSerializationProxy(
									parameter, classLoader))
						}
						else
						{
							processedParameters.add(parameter)
						}
					}
					ancestorMap = ancestorMap.mapAtPuttingCanDestroy(
						rawPojo, tupleFromList(processedParameters), true)
				}
			}
			catch (e: ClassNotFoundException)
			{
				throw RuntimeException(e)
			}

			return fusedTypeFromAncestorMap(ancestorMap)
		}
	},

	/**
	 * A [pojo type][PojoTypeDescriptor] representing a Java array type.  We can
	 * reconstruct this array type from the content type and the range of
	 * allowable sizes (a much stronger model than Java itself supports).
	 */
	ARRAY_POJO_TYPE(
		86,
		OBJECT_REFERENCE.`as`("content type"),
		OBJECT_REFERENCE.`as`("size range"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			assert(`object`.isPojoArrayType)
			val contentType = `object`.contentType()
			val sizeRange = `object`.sizeRange()
			return array(contentType, sizeRange)
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val contentType = subobjects[0]
			val sizeRange = subobjects[1]
			return pojoArrayType(contentType, sizeRange)
		}
	},

	/**
	 * A [set][SetDescriptor] of [class][StringDescriptor] standing in for a
	 * [pojo type][PojoTypeDescriptor] representing a "self type".  A self type
	 * is used for for parameterizing a Java class by itself.  For example, in
	 * the parametric type `Enum<E extends Enum<E>>`, we parameterize the class
	 * `Enum` with such a self type.  To reconstruct a self type all we need is
	 * a way to get to the raw Java classes involved, so we serialize their
	 * names.
	 */
	SELF_POJO_TYPE_REPRESENTATIVE(87, TUPLE_OF_OBJECTS.`as`("class names"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			throw RuntimeException(
				"Can't serialize a self pojo type directly")
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			throw RuntimeException(
				"Can't serialize a self pojo type directly")
		}
	},

	/**
	 * The bottom [pojo type][PojoTypeDescriptor], representing the most
	 * specific type of pojo.
	 */
	BOTTOM_POJO_TYPE(88)
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array()
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return pojoBottom()
		}
	},

	/**
	 * The bottom [pojo type][PojoTypeDescriptor], representing the most
	 * specific type of pojo.
	 */
	COMPILED_CODE_TYPE(89, OBJECT_REFERENCE.`as`("function type for code type"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(`object`.functionType())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return compiledCodeTypeForFunctionType(subobjects[0])
		}
	},

	/**
	 * The bottom [pojo type][PojoTypeDescriptor], representing the most
	 * specific type of pojo.
	 */
	CONTINUATION_TYPE(
		90,
		OBJECT_REFERENCE.`as`("function type for continuation type"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(`object`.functionType())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return continuationTypeForFunctionType(subobjects[0])
		}
	},

	/**
	 * An Avail [enumeration][EnumerationTypeDescriptor], a type that has an
	 * explicit finite list of its instances.
	 */
	ENUMERATION_TYPE(91, TUPLE_OF_OBJECTS.`as`("set of instances"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(`object`.instances().asTuple())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return enumerationWith(subobjects[0].asSet())
		}
	},

	/**
	 * An Avail [singular enumeration][InstanceTypeDescriptor], a type that has
	 * a single (non-type) instance.
	 */
	INSTANCE_TYPE(92, OBJECT_REFERENCE.`as`("type's instance"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(`object`.instance())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return instanceType(subobjects[0])
		}
	},

	/**
	 * An Avail [instance meta][InstanceMetaDescriptor], a type that has an
	 * instance `i`, which is itself a type.  Subtypes of type `i` are also
	 * considered instances of this instance meta.
	 */
	INSTANCE_META(93, OBJECT_REFERENCE.`as`("meta's instance"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(`object`.instance())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return instanceMeta(subobjects[0])
		}
	},

	/**
	 * A [set type][SetTypeDescriptor].
	 */
	SET_TYPE(
		94,
		OBJECT_REFERENCE.`as`("size range"),
		OBJECT_REFERENCE.`as`("element type"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				`object`.sizeRange(),
				`object`.contentType())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val sizeRange = subobjects[0]
			val contentType = subobjects[1]
			return setTypeForSizesContentType(sizeRange, contentType)
		}
	},

	/**
	 * A [map type][MapTypeDescriptor].
	 */
	MAP_TYPE(
		95,
		OBJECT_REFERENCE.`as`("size range"),
		OBJECT_REFERENCE.`as`("key type"),
		OBJECT_REFERENCE.`as`("value type"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				`object`.sizeRange(),
				`object`.keyType(),
				`object`.valueType())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val sizeRange = subobjects[0]
			val keyType = subobjects[1]
			val valueType = subobjects[2]
			return mapTypeForSizesKeyTypeValueType(
				sizeRange, keyType, valueType)
		}
	},

	/**
	 * A [token type][TokenTypeDescriptor].
	 */
	TOKEN_TYPE(96, OBJECT_REFERENCE.`as`("literal type"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(fromInt(`object`.tokenType().ordinal))
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return tokenType(lookupTokenType(subobjects[0].extractInt()))
		}
	},

	/**
	 * A [literal token type][LiteralTokenTypeDescriptor].
	 */
	LITERAL_TOKEN_TYPE(97, OBJECT_REFERENCE.`as`("literal type"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(`object`.literalType())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return literalTokenType(subobjects[0])
		}
	},

	/**
	 * A [parse phrase type][PhraseTypeDescriptor].
	 */
	PARSE_NODE_TYPE(
		98,
		BYTE.`as`("kind"),
		OBJECT_REFERENCE.`as`("expression type"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				fromInt(`object`.phraseKind().ordinal),
				`object`.expressionType())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val phraseKindOrdinal = subobjects[0].extractInt()
			val expressionType = subobjects[1]
			val phraseKind = PhraseKind.lookup(phraseKindOrdinal)
			return phraseKind.create(expressionType)
		}
	},

	/**
	 * A [list phrase type][ListPhraseTypeDescriptor].
	 */
	LIST_NODE_TYPE(
		99,
		BYTE.`as`("list phrase kind"),
		OBJECT_REFERENCE.`as`("expression type"),
		OBJECT_REFERENCE.`as`("subexpressions tuple type"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				fromInt(`object`.phraseKind().ordinal),
				`object`.expressionType(),
				`object`.subexpressionsTupleType())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val phraseKindOrdinal = subobjects[0].extractInt()
			val expressionType = subobjects[1]
			val subexpressionsTupleType = subobjects[2]
			val phraseKind = PhraseKind.lookup(phraseKindOrdinal)
			return createListNodeType(
				phraseKind, expressionType, subexpressionsTupleType)
		}
	},

	/**
	 * A [variable type][VariableTypeDescriptor] for which the read type and
	 * write type are equal.
	 */
	SIMPLE_VARIABLE_TYPE(100, OBJECT_REFERENCE.`as`("content type"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			val readType = `object`.readType()
			assert(readType.equals(`object`.writeType()))
			return array(readType)
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return variableTypeFor(subobjects[0])
		}
	},

	/**
	 * A [variable type][ReadWriteVariableTypeDescriptor] for which the read
	 * type and write type are (actually) unequal.
	 */
	READ_WRITE_VARIABLE_TYPE(
		101,
		OBJECT_REFERENCE.`as`("read type"),
		OBJECT_REFERENCE.`as`("write type"))
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			val readType = `object`.readType()
			val writeType = `object`.writeType()
			assert(!readType.equals(writeType))
			return array(readType, writeType)
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return variableReadWriteType(
				subobjects[0],
				subobjects[1])
		}
	},

	/**
	 * The [bottom type][BottomTypeDescriptor], more specific than all other
	 * types.
	 */
	BOTTOM_TYPE(102)
	{
		override fun decompose(
			`object`: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array()
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return pojoBottom()
		}
	};

	/**
	 * The operands that this operation expects to see encoded after the tag.
	 */
	internal val operands: Array<out SerializerOperand>

	/**
	 * Answer whether this operation is the serialization of a
	 * [variable][VariableDescriptor].
	 *
	 * @return
	 *   `false` (`true` in the relevant enumeration values).
	 */
	internal open val isVariableCreation: Boolean
		get() = false

	init
	{
		assert(ordinal and 255 == ordinal)
		assert(ordinal == ordinal)
		this.operands = operands
	}

	/**
	 * Decompose the given [AvailObject] into an array of `AvailObject`s that
	 * correspond with my [operands].
	 *
	 * @param object
	 *   The object to decompose.
	 * @param serializer
	 *   The serializer requesting decomposition.
	 * @return
	 *   An array of `AvailObject`s whose entries agree with this
	 *   `SerializerOperation`'s operands.
	 */
	internal abstract fun decompose(
		`object`: AvailObject,
		serializer: Serializer): Array<out A_BasicObject>

	/**
	 * Reconstruct the given [AvailObject] from an array of `AvailObject`s that
	 * correspond with my [operands].
	 *
	 * @param subobjects
	 *   The array of `AvailObject`s to assemble into a new object.
	 * @param deserializer
	 *   The [Deserializer] for those instructions that do more than simply
	 *   assemble an object.
	 * @return
	 *   The new `AvailObject`.
	 */
	internal abstract fun compose(
		subobjects: Array<AvailObject>,
		deserializer: Deserializer): A_BasicObject

	/**
	 * Write the given [AvailObject] to the [Serializer].  It must have already
	 * been fully traced.
	 *
	 * @param operandValues
	 *   The already extracted array of operand values.
	 * @param serializer
	 *   Where to serialize it.
	 */
	internal fun writeObject(
		operandValues: Array<out A_BasicObject>,
		serializer: Serializer)
	{
		serializer.writeByte(ordinal)
		assert(operandValues.size == operands.size)
		for (i in operandValues.indices)
		{
			operands[i].write(operandValues[i] as AvailObject, serializer)
		}
	}

	/**
	 * Describe this operation and its operands.
	 *
	 * @param describer
	 *   The [DeserializerDescriber] on which to describe this.
	 */
	internal open fun describe(describer: DeserializerDescriber)
	{
		describer.append(this.name)
		for (operand in operands)
		{
			describer.append("\n\t")
			operand.describe(describer)
		}
	}

	companion object
	{
		/**
		 * The array of enumeration values.  Don't change it.
		 */
		private val all = values()

		/**
		 * Answer the enum value having the given ordinal.
		 *
		 * @param ordinal
		 *   The ordinal to look up.
		 * @return
		 *   The `SerializerOperation` having the given ordinal.
		 */
		internal fun byOrdinal(ordinal: Int) = all[ordinal]

		/** The maximum number of operands of any SerializerOperation.  */
		internal val maxSubobjects = Arrays.stream(all)
			.map { op -> op.operands.size }
			.reduce(0) { a, b -> max(a, b) }

		/**
		 * Find or create the atom with the given name in the module with the
		 * given name.
		 *
		 * @param atomName
		 *   The name of the atom.
		 * @param moduleName
		 *   The module that defines the atom.
		 * @param deserializer
		 *   A deserializer with which to look up modules.
		 * @return
		 *   The [atom][A_Atom].
		 */
		internal fun lookupAtom(
			atomName: A_String,
			moduleName: A_String,
			deserializer: Deserializer): A_Atom
		{
			val currentModule = deserializer.currentModule
			assert(!currentModule.equalsNil())
			if (moduleName.equals(currentModule.moduleName()))
			{
				// An atom in the current module.  Create it if necessary.
				// Check if it's already defined somewhere...
				val trueNames = currentModule.trueNamesForStringName(atomName)
				if (trueNames.setSize() == 1)
				{
					return trueNames.asTuple().tupleAt(1)
				}
				val atom = createAtomWithProperties(
					atomName, currentModule)
				atom.makeImmutable()
				currentModule.addPrivateName(atom)
				return atom
			}
			// An atom in an imported module.
			val module = deserializer.moduleNamed(moduleName)
			val newNames = module.newNames()
			if (newNames.hasKey(atomName))
			{
				return newNames.mapAt(atomName)
			}
			val privateNames = module.privateNames()
			if (privateNames.hasKey(atomName))
			{
				val candidates = privateNames.mapAt(atomName)
				if (candidates.setSize() == 1)
				{
					return candidates.iterator().next()
				}
				if (candidates.setSize() > 1)
				{
					throw RuntimeException(
						format(
							"Ambiguous atom \"%s\" in module %s",
							atomName,
							module))
				}
			}
			// This should probably fail more gracefully.
			throw RuntimeException(
				format(
					"Unknown atom %s in module %s",
					atomName,
					module))
		}

		/**
		 * This helper function takes a variable number of arguments as an
		 * array, and conveniently returns that array.  This is syntactically
		 * *much* cleaner than any built-in array building syntax.
		 *
		 * @param
		 *   objects The [AvailObject]s.
		 * @return
		 *   The same array of `AvailObject`s.
		 */
		internal fun array(vararg objects: A_BasicObject) = objects
	}
}