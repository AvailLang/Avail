/*
 * SerializerOperation.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
import com.avail.AvailRuntime.Companion.specialObject
import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.atoms.A_Atom.Companion.atomName
import com.avail.descriptor.atoms.A_Atom.Companion.bundleOrCreate
import com.avail.descriptor.atoms.A_Atom.Companion.bundleOrNil
import com.avail.descriptor.atoms.A_Atom.Companion.getAtomProperty
import com.avail.descriptor.atoms.A_Atom.Companion.issuingModule
import com.avail.descriptor.atoms.A_Atom.Companion.setAtomProperty
import com.avail.descriptor.atoms.AtomDescriptor
import com.avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom
import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom.EXPLICIT_SUBCLASSING_KEY
import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom.HERITABLE_KEY
import com.avail.descriptor.atoms.AtomWithPropertiesSharedDescriptor
import com.avail.descriptor.bundles.A_Bundle
import com.avail.descriptor.bundles.A_Bundle.Companion.bundleMethod
import com.avail.descriptor.bundles.A_Bundle.Companion.macrosTuple
import com.avail.descriptor.bundles.A_Bundle.Companion.message
import com.avail.descriptor.bundles.MessageBundleDescriptor
import com.avail.descriptor.character.A_Character.Companion.codePoint
import com.avail.descriptor.character.CharacterDescriptor
import com.avail.descriptor.character.CharacterDescriptor.Companion.fromCodePoint
import com.avail.descriptor.functions.CompiledCodeDescriptor
import com.avail.descriptor.functions.CompiledCodeDescriptor.Companion.newCompiledCode
import com.avail.descriptor.functions.ContinuationDescriptor.Companion.createContinuationWithFrame
import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.functions.FunctionDescriptor.Companion.createFunction
import com.avail.descriptor.maps.A_Map.Companion.forEach
import com.avail.descriptor.maps.A_Map.Companion.hasKey
import com.avail.descriptor.maps.A_Map.Companion.mapAt
import com.avail.descriptor.maps.A_Map.Companion.mapAtPuttingCanDestroy
import com.avail.descriptor.maps.MapDescriptor
import com.avail.descriptor.maps.MapDescriptor.Companion.emptyMap
import com.avail.descriptor.methods.A_Definition
import com.avail.descriptor.methods.A_Macro
import com.avail.descriptor.methods.AbstractDefinitionDescriptor
import com.avail.descriptor.methods.ForwardDefinitionDescriptor
import com.avail.descriptor.methods.MethodDefinitionDescriptor
import com.avail.descriptor.methods.MethodDescriptor
import com.avail.descriptor.module.A_Module.Companion.addPrivateName
import com.avail.descriptor.module.A_Module.Companion.constantBindings
import com.avail.descriptor.module.A_Module.Companion.hasAncestor
import com.avail.descriptor.module.A_Module.Companion.moduleName
import com.avail.descriptor.module.A_Module.Companion.moduleState
import com.avail.descriptor.module.A_Module.Companion.newNames
import com.avail.descriptor.module.A_Module.Companion.privateNames
import com.avail.descriptor.module.A_Module.Companion.recordBlockPhrase
import com.avail.descriptor.module.A_Module.Companion.trueNamesForStringName
import com.avail.descriptor.module.A_Module.Companion.variableBindings
import com.avail.descriptor.module.ModuleDescriptor.State.Loading
import com.avail.descriptor.numbers.A_Number.Companion.equalsInt
import com.avail.descriptor.numbers.A_Number.Companion.extractDouble
import com.avail.descriptor.numbers.A_Number.Companion.extractFloat
import com.avail.descriptor.numbers.A_Number.Companion.extractInt
import com.avail.descriptor.numbers.A_Number.Companion.extractUnsignedByte
import com.avail.descriptor.numbers.DoubleDescriptor
import com.avail.descriptor.numbers.DoubleDescriptor.Companion.fromDouble
import com.avail.descriptor.numbers.FloatDescriptor
import com.avail.descriptor.numbers.FloatDescriptor.Companion.fromFloat
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromUnsignedByte
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.one
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.two
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import com.avail.descriptor.objects.ObjectDescriptor.Companion.objectFromMap
import com.avail.descriptor.objects.ObjectTypeDescriptor.Companion.objectTypeFromMap
import com.avail.descriptor.phrases.A_Phrase.Companion.argumentsListNode
import com.avail.descriptor.phrases.A_Phrase.Companion.argumentsTuple
import com.avail.descriptor.phrases.A_Phrase.Companion.bundle
import com.avail.descriptor.phrases.A_Phrase.Companion.declaration
import com.avail.descriptor.phrases.A_Phrase.Companion.declaredExceptions
import com.avail.descriptor.phrases.A_Phrase.Companion.declaredType
import com.avail.descriptor.phrases.A_Phrase.Companion.expression
import com.avail.descriptor.phrases.A_Phrase.Companion.expressionsTuple
import com.avail.descriptor.phrases.A_Phrase.Companion.initializationExpression
import com.avail.descriptor.phrases.A_Phrase.Companion.list
import com.avail.descriptor.phrases.A_Phrase.Companion.literalObject
import com.avail.descriptor.phrases.A_Phrase.Companion.macroOriginalSendNode
import com.avail.descriptor.phrases.A_Phrase.Companion.outputPhrase
import com.avail.descriptor.phrases.A_Phrase.Companion.permutation
import com.avail.descriptor.phrases.A_Phrase.Companion.phraseExpressionType
import com.avail.descriptor.phrases.A_Phrase.Companion.statements
import com.avail.descriptor.phrases.A_Phrase.Companion.statementsTuple
import com.avail.descriptor.phrases.A_Phrase.Companion.superUnionType
import com.avail.descriptor.phrases.A_Phrase.Companion.token
import com.avail.descriptor.phrases.A_Phrase.Companion.tokens
import com.avail.descriptor.phrases.A_Phrase.Companion.typeExpression
import com.avail.descriptor.phrases.A_Phrase.Companion.variable
import com.avail.descriptor.phrases.AssignmentPhraseDescriptor
import com.avail.descriptor.phrases.AssignmentPhraseDescriptor.Companion.isInline
import com.avail.descriptor.phrases.AssignmentPhraseDescriptor.Companion.newAssignment
import com.avail.descriptor.phrases.BlockPhraseDescriptor
import com.avail.descriptor.phrases.BlockPhraseDescriptor.Companion.newBlockNode
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.Companion.newDeclaration
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor.DeclarationKind
import com.avail.descriptor.phrases.ExpressionAsStatementPhraseDescriptor
import com.avail.descriptor.phrases.ExpressionAsStatementPhraseDescriptor.Companion.newExpressionAsStatement
import com.avail.descriptor.phrases.FirstOfSequencePhraseDescriptor
import com.avail.descriptor.phrases.FirstOfSequencePhraseDescriptor.Companion.newFirstOfSequenceNode
import com.avail.descriptor.phrases.ListPhraseDescriptor
import com.avail.descriptor.phrases.ListPhraseDescriptor.Companion.newListNode
import com.avail.descriptor.phrases.LiteralPhraseDescriptor
import com.avail.descriptor.phrases.LiteralPhraseDescriptor.Companion.literalNodeFromToken
import com.avail.descriptor.phrases.MacroSubstitutionPhraseDescriptor
import com.avail.descriptor.phrases.MacroSubstitutionPhraseDescriptor.Companion.newMacroSubstitution
import com.avail.descriptor.phrases.PermutedListPhraseDescriptor
import com.avail.descriptor.phrases.PermutedListPhraseDescriptor.Companion.newPermutedListNode
import com.avail.descriptor.phrases.ReferencePhraseDescriptor.Companion.referenceNodeFromUse
import com.avail.descriptor.phrases.SendPhraseDescriptor
import com.avail.descriptor.phrases.SendPhraseDescriptor.Companion.newSendNode
import com.avail.descriptor.phrases.SequencePhraseDescriptor
import com.avail.descriptor.phrases.SequencePhraseDescriptor.Companion.newSequence
import com.avail.descriptor.phrases.SuperCastPhraseDescriptor
import com.avail.descriptor.phrases.SuperCastPhraseDescriptor.Companion.newSuperCastNode
import com.avail.descriptor.phrases.VariableUsePhraseDescriptor
import com.avail.descriptor.phrases.VariableUsePhraseDescriptor.Companion.newUse
import com.avail.descriptor.pojos.PojoFieldDescriptor.Companion.pojoFieldVariableForInnerType
import com.avail.descriptor.pojos.PojoFinalFieldDescriptor
import com.avail.descriptor.pojos.RawPojoDescriptor
import com.avail.descriptor.pojos.RawPojoDescriptor.Companion.equalityPojo
import com.avail.descriptor.pojos.RawPojoDescriptor.Companion.rawNullPojo
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.NilDescriptor
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.sets.A_Set.Companion.asTuple
import com.avail.descriptor.sets.A_Set.Companion.setSize
import com.avail.descriptor.sets.SetDescriptor
import com.avail.descriptor.tokens.CommentTokenDescriptor.Companion.newCommentToken
import com.avail.descriptor.tokens.LiteralTokenDescriptor
import com.avail.descriptor.tokens.LiteralTokenDescriptor.Companion.literalToken
import com.avail.descriptor.tokens.TokenDescriptor
import com.avail.descriptor.tokens.TokenDescriptor.Companion.newToken
import com.avail.descriptor.tokens.TokenDescriptor.TokenType.Companion.lookupTokenType
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.A_Tuple.Companion.asSet
import com.avail.descriptor.tuples.A_Tuple.Companion.component1
import com.avail.descriptor.tuples.A_Tuple.Companion.component2
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.generateObjectTupleFrom
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import com.avail.descriptor.tuples.StringDescriptor
import com.avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import com.avail.descriptor.tuples.TupleDescriptor.Companion.toList
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.argsTupleType
import com.avail.descriptor.types.A_Type.Companion.contentType
import com.avail.descriptor.types.A_Type.Companion.defaultType
import com.avail.descriptor.types.A_Type.Companion.fieldTypeMap
import com.avail.descriptor.types.A_Type.Companion.instance
import com.avail.descriptor.types.A_Type.Companion.instances
import com.avail.descriptor.types.A_Type.Companion.keyType
import com.avail.descriptor.types.A_Type.Companion.literalType
import com.avail.descriptor.types.A_Type.Companion.lowerBound
import com.avail.descriptor.types.A_Type.Companion.lowerInclusive
import com.avail.descriptor.types.A_Type.Companion.phraseKind
import com.avail.descriptor.types.A_Type.Companion.phraseTypeExpressionType
import com.avail.descriptor.types.A_Type.Companion.readType
import com.avail.descriptor.types.A_Type.Companion.returnType
import com.avail.descriptor.types.A_Type.Companion.sizeRange
import com.avail.descriptor.types.A_Type.Companion.subexpressionsTupleType
import com.avail.descriptor.types.A_Type.Companion.typeTuple
import com.avail.descriptor.types.A_Type.Companion.upperBound
import com.avail.descriptor.types.A_Type.Companion.upperInclusive
import com.avail.descriptor.types.A_Type.Companion.valueType
import com.avail.descriptor.types.A_Type.Companion.writeType
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.BottomPojoTypeDescriptor.Companion.pojoBottom
import com.avail.descriptor.types.BottomTypeDescriptor
import com.avail.descriptor.types.CompiledCodeTypeDescriptor.Companion.compiledCodeTypeForFunctionType
import com.avail.descriptor.types.ContinuationTypeDescriptor.Companion.continuationTypeForFunctionType
import com.avail.descriptor.types.EnumerationTypeDescriptor
import com.avail.descriptor.types.FiberTypeDescriptor
import com.avail.descriptor.types.FiberTypeDescriptor.Companion.fiberType
import com.avail.descriptor.types.FunctionTypeDescriptor
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionTypeFromArgumentTupleType
import com.avail.descriptor.types.InstanceMetaDescriptor
import com.avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import com.avail.descriptor.types.InstanceTypeDescriptor
import com.avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integerRangeType
import com.avail.descriptor.types.ListPhraseTypeDescriptor
import com.avail.descriptor.types.ListPhraseTypeDescriptor.Companion.createListNodeType
import com.avail.descriptor.types.LiteralTokenTypeDescriptor
import com.avail.descriptor.types.LiteralTokenTypeDescriptor.Companion.literalTokenType
import com.avail.descriptor.types.MapTypeDescriptor
import com.avail.descriptor.types.MapTypeDescriptor.Companion.mapTypeForSizesKeyTypeValueType
import com.avail.descriptor.types.PhraseTypeDescriptor
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import com.avail.descriptor.types.PojoTypeDescriptor
import com.avail.descriptor.types.PojoTypeDescriptor.Companion.fusedTypeFromAncestorMap
import com.avail.descriptor.types.PojoTypeDescriptor.Companion.marshalTypes
import com.avail.descriptor.types.PojoTypeDescriptor.Companion.pojoArrayType
import com.avail.descriptor.types.PojoTypeDescriptor.Companion.pojoTypeForClassWithTypeArguments
import com.avail.descriptor.types.PojoTypeDescriptor.Companion.resolvePojoType
import com.avail.descriptor.types.PrimitiveTypeDescriptor.Companion.extractOrdinal
import com.avail.descriptor.types.ReadWriteVariableTypeDescriptor
import com.avail.descriptor.types.SelfPojoTypeDescriptor.Companion.pojoFromSerializationProxy
import com.avail.descriptor.types.SelfPojoTypeDescriptor.Companion.pojoSerializationProxy
import com.avail.descriptor.types.SetTypeDescriptor
import com.avail.descriptor.types.SetTypeDescriptor.Companion.setTypeForSizesContentType
import com.avail.descriptor.types.TokenTypeDescriptor
import com.avail.descriptor.types.TokenTypeDescriptor.Companion.tokenType
import com.avail.descriptor.types.TupleTypeDescriptor
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForSizesTypesDefaultType
import com.avail.descriptor.types.TypeDescriptor.Types
import com.avail.descriptor.types.VariableTypeDescriptor
import com.avail.descriptor.types.VariableTypeDescriptor.Companion.variableReadWriteType
import com.avail.descriptor.types.VariableTypeDescriptor.Companion.variableTypeFor
import com.avail.descriptor.variables.A_Variable
import com.avail.descriptor.variables.VariableDescriptor
import com.avail.descriptor.variables.VariableDescriptor.Companion.newVariableWithOuterType
import com.avail.exceptions.AvailErrorCode.E_JAVA_METHOD_NOT_AVAILABLE
import com.avail.exceptions.AvailRuntimeException
import com.avail.exceptions.MalformedMessageException
import com.avail.interpreter.Primitive.Companion.primitiveByName
import com.avail.interpreter.levelTwo.L2Chunk.ChunkEntryPoint.TO_RESTART
import com.avail.interpreter.levelTwo.L2Chunk.ChunkEntryPoint.TO_RETURN_INTO
import com.avail.interpreter.levelTwo.L2Chunk.Companion.unoptimizedChunk
import com.avail.interpreter.primitive.pojos.P_CreatePojoConstructorFunction
import com.avail.interpreter.primitive.pojos.P_CreatePojoInstanceMethodFunction
import com.avail.performance.Statistic
import com.avail.performance.StatisticReport.DESERIALIZE
import com.avail.performance.StatisticReport.SERIALIZE_TRACE
import com.avail.performance.StatisticReport.SERIALIZE_WRITE
import com.avail.serialization.SerializerOperandEncoding.BIG_INTEGER_DATA
import com.avail.serialization.SerializerOperandEncoding.BYTE
import com.avail.serialization.SerializerOperandEncoding.BYTE_CHARACTER_TUPLE
import com.avail.serialization.SerializerOperandEncoding.COMPRESSED_ARBITRARY_CHARACTER_TUPLE
import com.avail.serialization.SerializerOperandEncoding.COMPRESSED_INT_TUPLE
import com.avail.serialization.SerializerOperandEncoding.COMPRESSED_SHORT
import com.avail.serialization.SerializerOperandEncoding.COMPRESSED_SHORT_CHARACTER_TUPLE
import com.avail.serialization.SerializerOperandEncoding.GENERAL_MAP
import com.avail.serialization.SerializerOperandEncoding.OBJECT_REFERENCE
import com.avail.serialization.SerializerOperandEncoding.SIGNED_INT
import com.avail.serialization.SerializerOperandEncoding.TUPLE_OF_OBJECTS
import com.avail.serialization.SerializerOperandEncoding.UNCOMPRESSED_BYTE_TUPLE
import com.avail.serialization.SerializerOperandEncoding.UNCOMPRESSED_NYBBLE_TUPLE
import com.avail.serialization.SerializerOperandEncoding.UNCOMPRESSED_SHORT
import com.avail.serialization.SerializerOperandEncoding.UNSIGNED_INT
import java.lang.Double.doubleToRawLongBits
import java.lang.Double.longBitsToDouble
import java.lang.Float.floatToRawIntBits
import java.lang.Float.intBitsToFloat
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

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
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array()
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return zero
		}
	},

	/**
	 * The Avail integer 1.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	ONE_INTEGER(1)
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array()
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return one
		}
	},

	/**
	 * The Avail integer 2.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	TWO_INTEGER(2)
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array()
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return two
		}
	},

	/**
	 * The Avail integer 3.  Note that there are no operands, since the value is
	 * encoded in the choice of instruction itself.
	 */
	THREE_INTEGER(3)
	{
		override fun decompose(
			obj: AvailObject,
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
			obj: AvailObject,
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
			obj: AvailObject,
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
			obj: AvailObject,
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
			obj: AvailObject,
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
			obj: AvailObject,
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
			obj: AvailObject,
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
			obj: AvailObject,
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
	BYTE_INTEGER(11, BYTE.named("only byte"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(obj)
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
	SHORT_INTEGER(12, UNCOMPRESSED_SHORT.named("the unsigned short"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(obj)
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
	INT_INTEGER(13, SIGNED_INT.named("int's value"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(obj)
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
	 * An Avail integer that cannot be represented as an [Int].
	 */
	BIG_INTEGER(14, BIG_INTEGER_DATA.named("constituent ints"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(obj)
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
			obj: AvailObject,
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
	CHECKPOINT(16, OBJECT_REFERENCE.named("object to checkpoint"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			// Make sure the function actually gets written out.
			return array(obj)
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
	SPECIAL_OBJECT(17, COMPRESSED_SHORT.named("special object number"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				fromInt(Serializer.indexOfSpecialObject(obj)))
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
	SPECIAL_ATOM(18, COMPRESSED_SHORT.named("special atom number"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				fromInt(Serializer.indexOfSpecialAtom(obj)))
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
	BYTE_CHARACTER(19, BYTE.named("Latin-1 code point"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(fromInt(obj.codePoint()))
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
	SHORT_CHARACTER(20, UNCOMPRESSED_SHORT.named("BMP code point"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(fromInt(obj.codePoint()))
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
		BYTE.named("SMP codepoint high byte"),
		BYTE.named("SMP codepoint middle byte"),
		BYTE.named("SMP codepoint low byte"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			val codePoint = obj.codePoint()
			return array(
				fromInt(codePoint shr 16 and 0xFF),
				fromInt(codePoint shr 8 and 0xFF),
				fromInt(codePoint and 0xFF))
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val (high, mid, low) = subobjects
			return fromCodePoint(
				(high.extractUnsignedByte().toInt() shl 16)
					+ (mid.extractUnsignedByte().toInt() shl 8)
					+ low.extractUnsignedByte().toInt())
		}
	},

	/**
	 * A [float][FloatDescriptor].  Convert the raw bits to an int for writing.
	 */
	FLOAT(22, SIGNED_INT.named("raw bits"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			val floatValue = obj.extractFloat()
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
		SIGNED_INT.named("upper raw bits"),
		SIGNED_INT.named("lower raw bits"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			val doubleValue = obj.extractDouble()
			val doubleBits = doubleToRawLongBits(doubleValue)
			return array(
				fromInt((doubleBits shr 32).toInt()),
				fromInt(doubleBits.toInt()))
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val (high, low) = subobjects
			val doubleBits = (high.extractInt().toLong() shl 32) +
				(low.extractInt().toLong() and 0xFFFFFFFFL)
			return fromDouble(longBitsToDouble(doubleBits))
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
	GENERAL_TUPLE(24, TUPLE_OF_OBJECTS.named("tuple elements"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(obj)
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return subobjects[0]
		}
	},

	/**
	 * A [tuple&#32;of&#32;characters][StringDescriptor] with code points in
	 * Latin-1.  Write the size of the tuple then the sequence of character
	 * bytes.
	 */
	BYTE_STRING(25, BYTE_CHARACTER_TUPLE.named("Latin-1 string"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(obj)
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return subobjects[0]
		}
	},

	/**
	 * A [tuple&#32;of&#32;characters][StringDescriptor] whose code points all
	 * fall in the range 0..65535.  Write the compressed number of characters
	 * then each compressed character.
	 */
	SHORT_STRING(
		26,
		COMPRESSED_SHORT_CHARACTER_TUPLE.named(
			"Basic Multilingual Plane string"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(obj)
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return subobjects[0]
		}
	},

	/**
	 * A [tuple&#32;of&#32;characters][StringDescriptor] with arbitrary code
	 * points. Write the compressed number of characters then each compressed
	 * character.
	 */
	ARBITRARY_STRING(
		27,
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.named("arbitrary string"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(obj)
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return subobjects[0]
		}
	},

	/**
	 * A [tuple][TupleDescriptor] of integers whose values all fall in the range
	 * 0..2^31-1.
	 */
	INT_TUPLE(28, COMPRESSED_INT_TUPLE.named("tuple of ints"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(obj)
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return subobjects[0]
		}
	},

	/**
	 * A [tuple][TupleDescriptor] of integers whose values all fall in the range
	 * 0..255.
	 */
	BYTE_TUPLE(29, UNCOMPRESSED_BYTE_TUPLE.named("tuple of bytes"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(obj)
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return subobjects[0]
		}
	},

	/**
	 * A [tuple][TupleDescriptor] whose values fall in the range 0..15.
	 */
	NYBBLE_TUPLE(30, UNCOMPRESSED_NYBBLE_TUPLE.named("tuple of nybbles"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(obj)
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return subobjects[0]
		}
	},

	/**
	 * A [map][MapDescriptor].  Convert it to a tuple (key1, value1, …
	 * key```[N]```, value```[N]```) and work with that, converting it back to a
	 * map when deserializing.
	 */
	MAP(31, GENERAL_MAP.named("map contents"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(obj)
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
	OBJECT(32, GENERAL_MAP.named("field map"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(obj.fieldMap())
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
	OBJECT_TYPE(33, GENERAL_MAP.named("field type map"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(obj.fieldTypeMap())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return objectTypeFromMap(subobjects[0])
		}
	},

	/**
	 * An [atom][A_Atom].  Output the atom name and the name of the
	 * module that issued it.  Look up the corresponding atom during
	 * reconstruction, recreating it if it's not present and supposed to have
	 * been issued by the current module.
	 */
	ATOM(
		34,
		OBJECT_REFERENCE.named("atom name"),
		OBJECT_REFERENCE.named("module name"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			serializer.checkAtom(obj)
			assert(obj.getAtomProperty(HERITABLE_KEY.atom).equalsNil())
			val module = obj.issuingModule()
			if (module.equalsNil())
			{
				throw RuntimeException("Atom has no issuing module")
			}
			return array(obj.atomName(), module.moduleName())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val (atomName, moduleName) = subobjects
			val atom = lookupAtom(atomName, moduleName, deserializer)
			return atom.makeShared()
		}
	},

	/**
	 * An [atom][A_Atom].  Output the atom name and the name of the
	 * module that issued it.  Look up the corresponding atom during
	 * reconstruction, recreating it if it's not present and supposed to have
	 * been issued by the current module.
	 */
	HERITABLE_ATOM(
		35,
		OBJECT_REFERENCE.named("atom name"),
		OBJECT_REFERENCE.named("module name"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			serializer.checkAtom(obj)
			assert(
				obj.getAtomProperty(HERITABLE_KEY.atom).equals(
					trueObject
				))
			val module = obj.issuingModule()
			if (module.equalsNil())
			{
				throw RuntimeException("Atom has no issuing module")
			}
			return array(obj.atomName(), module.moduleName())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val (atomName, moduleName) = subobjects
			val atom = lookupAtom(atomName, moduleName, deserializer)
			atom.setAtomProperty(HERITABLE_KEY.atom, trueObject)
			return atom.makeShared()
		}
	},

	/**
	 * A [compiled&#32;code&#32;object][CompiledCodeDescriptor].  Output any
	 * information needed to reconstruct the compiled code object.
	 */
	COMPILED_CODE(
		36,
		COMPRESSED_SHORT.named("Total number of frame slots"),
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.named("Primitive name"),
		OBJECT_REFERENCE.named("Type if primitive fails"),
		OBJECT_REFERENCE.named("Function type"),
		UNCOMPRESSED_NYBBLE_TUPLE.named("Level one nybblecodes"),
		TUPLE_OF_OBJECTS.named("Regular literals"),
		TUPLE_OF_OBJECTS.named("Local types"),
		TUPLE_OF_OBJECTS.named("Constant types"),
		TUPLE_OF_OBJECTS.named("Outer types"),
		OBJECT_REFERENCE.named("Module name"),
		UNSIGNED_INT.named("Line number"),
		COMPRESSED_INT_TUPLE.named("Encoded line number deltas"),
		OBJECT_REFERENCE.named("Originating phrase"),
		OBJECT_REFERENCE.named("Packed declaration names"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			val originatingPhraseOrIndex = obj.originatingPhraseOrIndex()
			val module = obj.module()
			val phraseOrIndex = when
			{
				module.equalsNil()
					|| module.moduleState() != Loading
					|| serializer.module === null
					|| !serializer.module.hasAncestor(module)
					|| originatingPhraseOrIndex.equalsNil()
					|| originatingPhraseOrIndex.isInt
				-> originatingPhraseOrIndex
				else ->
				{
					val index = module.recordBlockPhrase(
						originatingPhraseOrIndex.makeShared())
					obj.setOriginatingPhraseOrIndex(index as AvailObject)
					index
				}
			}

			val numLocals = obj.numLocals()
			val numConstants = obj.numConstants()
			val numOuters = obj.numOuters()
			val numRegularLiterals =
				obj.numLiterals() - numConstants - numLocals - numOuters
			val regularLiterals = generateObjectTupleFrom(numRegularLiterals) {
				obj.literalAt(it)
			}
			val localTypes =
				generateObjectTupleFrom(numLocals) { obj.localTypeAt(it) }
			val constantTypes =
				generateObjectTupleFrom(numConstants) { obj.constantTypeAt(it) }
			val outerTypes =
				generateObjectTupleFrom(numOuters) { obj.outerTypeAt(it) }
			val moduleName =
				if (module.equalsNil()) emptyTuple
				else module.moduleName()
			val primitive = obj.primitive()
			val primName =
				if (primitive === null) emptyTuple
				else stringFrom(primitive.name)
			return array(
				fromInt(obj.numSlots()),
				primName,
				obj.returnTypeIfPrimitiveFails(),
				obj.functionType(),
				obj.nybbles(),
				regularLiterals,
				localTypes,
				constantTypes,
				outerTypes,
				moduleName,
				fromInt(obj.startingLineNumber()),
				obj.lineNumberEncodedDeltas(),
				phraseOrIndex,
				obj.packedDeclarationNames())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val numSlots = subobjects[0].extractInt()
			val primitive = subobjects[1]
			val returnTypeIfPrimitiveFails = subobjects[2]
			val functionType = subobjects[3]
			val nybbles = subobjects[4]
			val regularLiterals = subobjects[5]
			val localTypes = subobjects[6]
			val constantTypes = subobjects[7]
			val outerTypes = subobjects[8]
			val moduleName = subobjects[9]
			val lineNumberInteger = subobjects[10]
			val lineNumberEncodedDeltas = subobjects[11]
			val originatingPhrase = subobjects[12]
			val packedDeclarationNames = subobjects[13]

			val numArgsRange = functionType.argsTupleType().sizeRange()
			val numArgs = numArgsRange.lowerBound().extractInt()
			assert(numArgsRange.upperBound().extractInt() == numArgs)
			val numLocals = localTypes.tupleSize()
			val numConstants = constantTypes.tupleSize()

			val module =
				if (moduleName.tupleSize() == 0) nil
				else deserializer.moduleNamed(moduleName)
			return newCompiledCode(
				nybbles,
				numSlots - numConstants - numLocals - numArgs,
				functionType,
				primitiveByName(primitive.asNativeString()),
				returnTypeIfPrimitiveFails,
				regularLiterals,
				localTypes,
				constantTypes,
				outerTypes,
				module,
				lineNumberInteger.extractInt(),
				lineNumberEncodedDeltas,
				originatingPhrase,
				packedDeclarationNames)
		}
	},

	/**
	 * A [function][FunctionDescriptor] with no outer (lexically captured)
	 * variables.
	 */
	CLEAN_FUNCTION(37, OBJECT_REFERENCE.named("Compiled code"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			assert(obj.numOuterVars() == 0)
			return array(
				obj.code())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val code = subobjects[0]
			return createFunction(code, emptyTuple)
		}
	},

	/**
	 * A [function][FunctionDescriptor] with one or more outer (lexically
	 * captured) variables.
	 */
	GENERAL_FUNCTION(
		38,
		OBJECT_REFERENCE.named("Compiled code"),
		TUPLE_OF_OBJECTS.named("Outer values"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			val outers = generateObjectTupleFrom(obj.numOuterVars()) {
				obj.outerVarAt(it)
			}
			return array(
				obj.code(),
				outers)
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val (code, outers) = subobjects
			return createFunction(code, outers)
		}
	},

	/**
	 * A non-global [variable][VariableDescriptor].  Deserialization
	 * reconstructs a new one, since there's no way to know where the original
	 * one came from.
	 */
	LOCAL_VARIABLE(39, OBJECT_REFERENCE.named("variable type"))
	{
		override val isVariableCreation: Boolean
			// This was a local variable, so answer true, indicating that we
			// also need to serialize the content.  If the variable had instead
			// been looked up (as for a GLOBAL_VARIABLE), we would answer false
			// to indicate that the variable had already been initialized
			// elsewhere.
			get() = true

		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			assert(!obj.isGlobal())
			return array(obj.kind())
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
		OBJECT_REFERENCE.named("variable type"),
		OBJECT_REFERENCE.named("module name"),
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.named("variable name"),
		BYTE.named("flags"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			assert(obj.isGlobal())
			val flags =
				(if (obj.isInitializedWriteOnceVariable) 1 else 0) +
					if (obj.valueWasStablyComputed()) 2 else 0
			return array(
				obj.kind(),
				obj.globalModule(),
				obj.globalName(),
				fromInt(flags))
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val (varType, module, varName, flags) = subobjects

			val flagsInt = flags.extractInt()
			val writeOnce = flagsInt and 1 != 0
			val stablyComputed = flagsInt and 2 != 0
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
	SET(41, TUPLE_OF_OBJECTS.named("tuple of objects"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(obj.asTuple())
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
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.named("token string"),
		SIGNED_INT.named("start position"),
		SIGNED_INT.named("line number"),
		BYTE.named("token type code"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				obj.string(),
				fromInt(obj.start()),
				fromInt(obj.lineNumber()),
				fromInt(obj.tokenType().ordinal))
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val (string, start, lineNumber, tokenTypeOrdinal) = subobjects
			return newToken(
				string,
				start.extractInt(),
				lineNumber.extractInt(),
				lookupTokenType(tokenTypeOrdinal.extractInt()))
		}
	},

	/**
	 * A [literal&#32;token][LiteralTokenDescriptor].
	 */
	LITERAL_TOKEN(
		43,
		OBJECT_REFERENCE.named("token string"),
		OBJECT_REFERENCE.named("literal value"),
		SIGNED_INT.named("start position"),
		SIGNED_INT.named("line number"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				obj.string(),
				obj.literal(),
				fromInt(obj.start()),
				fromInt(obj.lineNumber()))
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val (string, literal, start, lineNumber) = subobjects
			return literalToken(
				string, start.extractInt(), lineNumber.extractInt(), literal)
		}
	},

	/**
	 * A [token][TokenDescriptor].
	 */
	COMMENT_TOKEN(
		44,
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.named("token string"),
		SIGNED_INT.named("start position"),
		SIGNED_INT.named("line number"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				obj.string(),
				fromInt(obj.start()),
				fromInt(obj.lineNumber()))
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val (string, start, lineNumber) = subobjects
			return newCommentToken(
				string, start.extractInt(), lineNumber.extractInt())
		}
	},

	/**
	 * This special opcode causes a previously built variable to have a
	 * previously built value to be assigned to it at this point during
	 * deserialization.
	 */
	ASSIGN_TO_VARIABLE(
		45,
		OBJECT_REFERENCE.named("variable to assign"),
		OBJECT_REFERENCE.named("value to assign"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				obj,
				obj.value())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val (variable, value) = subobjects
			variable.setValue(value)
			return nil
		}
	},

	/**
	 * The representation of a continuation, which is just its level one state.
	 */
	CONTINUATION(
		46,
		OBJECT_REFERENCE.named("calling continuation"),
		OBJECT_REFERENCE.named("continuation's function"),
		TUPLE_OF_OBJECTS.named("continuation frame slots"),
		COMPRESSED_SHORT.named("program counter"),
		COMPRESSED_SHORT.named("stack pointer"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			val frameSlotCount = obj.numSlots()
			val frameSlotsList = mutableListOf<AvailObject>()
			for (i in 1..frameSlotCount)
			{
				frameSlotsList.add(obj.frameAt(i))
			}
			return array(
				obj.caller(),
				obj.function(),
				tupleFromList(frameSlotsList),
				fromInt(obj.pc()),
				fromInt(obj.stackp()))
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val (caller, function, frameSlots, pcInteger, stackpInteger) =
				subobjects
			val continuation = createContinuationWithFrame(
				function,
				caller,
				nil,
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
	 * during deserialization.  A method can have multiple
	 * [message&#32;bundles][MessageBundleDescriptor], and *each* <module name,
	 * atom name> pair is recorded during serialization. For system atoms we
	 * output nil for the module name.  During deserialization, the list is
	 * searched for a module that has been loaded, and if the corresponding name
	 * is an atom, and if that atom has a bundle associated with it, that
	 * bundle's method is used.
	 */
	METHOD(47, TUPLE_OF_OBJECTS.named("module name / atom name pairs"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			assert(obj.isInstanceOf(Types.METHOD.o))
			val pairs = mutableListOf<A_Tuple>()
			for (bundle in obj.bundles())
			{
				val atom = bundle.message()
				val module = atom.issuingModule()
				if (!module.equalsNil())
				{
					pairs.add(tuple(module.moduleName(), atom.atomName()))
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
			for ((moduleName, atomName) in pairs)
			{
				if (!moduleName.equalsNil() &&
					deserializer.loadedModules.hasKey(moduleName))
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
			for ((moduleName, atomName) in pairs)
			{
				if (moduleName.equalsNil())
				{
					val specialAtom = Serializer.specialAtomsByName[atomName]
					if (specialAtom !== null)
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
		OBJECT_REFERENCE.named("method"),
		OBJECT_REFERENCE.named("signature"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			assert(obj.isMethodDefinition())
			return array(
				obj.definitionMethod(),
				obj.bodySignature())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_Definition
		{
			val (definitionMethod, signature) = subobjects
			val definitions = definitionMethod.definitionsTuple()
				.filter { it.bodySignature().equals(signature) }
			assert(definitions.size == 1)
			val definition = definitions[0]
			assert(definition.isMethodDefinition())
			return definition
		}
	},

	/**
	 * A reference to a [macro][A_Macro], which should be reconstructed by
	 * looking it up.
	 */
	MACRO_DEFINITION(
		49,
		OBJECT_REFERENCE.named("bundle"),
		OBJECT_REFERENCE.named("signature"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				obj.definitionBundle(),
				obj.bodySignature())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val definitionBundle: A_Bundle = subobjects[0]
			val signature: A_Type = subobjects[1]
			val definitions = definitionBundle.macrosTuple()
				.filter { it.bodySignature().equals(signature) }
			assert(definitions.size == 1)
			return definitions[0]
		}
	},

	/**
	 * A reference to an [abstract][AbstractDefinitionDescriptor], which should
	 * be reconstructed by looking it up.
	 */
	ABSTRACT_DEFINITION(
		50,
		OBJECT_REFERENCE.named("method"),
		OBJECT_REFERENCE.named("signature"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			assert(obj.isAbstractDefinition())
			return array(
				obj.definitionMethod(),
				obj.bodySignature())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val (definitionMethod, signature) = subobjects
			val definitions = definitionMethod.definitionsTuple()
				.filter { it.bodySignature().equals(signature) }
			assert(definitions.size == 1)
			val definition = definitions[0]
			assert(definition.isAbstractDefinition())
			return definition
		}
	},

	/**
	 * A reference to a [forward][ForwardDefinitionDescriptor], which should be
	 * reconstructed by looking it up.
	 */
	FORWARD_DEFINITION(
		51,
		OBJECT_REFERENCE.named("method"),
		OBJECT_REFERENCE.named("signature"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			assert(obj.isForwardDefinition())
			return array(
				obj.definitionMethod(),
				obj.bodySignature())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val (definitionMethod, signature) = subobjects
			val definitions = definitionMethod.definitionsTuple()
				.filter { it.bodySignature().equals(signature) }
			assert(definitions.size == 1)
			val definition = definitions[0]
			assert(definition.isForwardDefinition())
			return definition
		}
	},

	/**
	 * A reference to a [message&#32;bundle][MessageBundleDescriptor],
	 * which should be reconstructed by looking it up.
	 */
	MESSAGE_BUNDLE(52, OBJECT_REFERENCE.named("message atom"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				obj.message())
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
	MODULE(53, OBJECT_REFERENCE.named("module name"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(obj.moduleName())
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
			else deserializer.loadedModules.mapAt(moduleName)
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
		OBJECT_REFERENCE.named("atom name"),
		OBJECT_REFERENCE.named("module name"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			serializer.checkAtom(obj)
			assert(
				obj.getAtomProperty(HERITABLE_KEY.atom).equalsNil())
			assert(
				obj.getAtomProperty(EXPLICIT_SUBCLASSING_KEY.atom)
					.equals(EXPLICIT_SUBCLASSING_KEY.atom))
			val module = obj.issuingModule()
			if (module.equalsNil())
			{
				throw RuntimeException("Atom has no issuing module")
			}
			return array(obj.atomName(), module.moduleName())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val (atomName, moduleName) = subobjects
			val atom = lookupAtom(atomName, moduleName, deserializer)
			atom.setAtomProperty(
				EXPLICIT_SUBCLASSING_KEY.atom,
				EXPLICIT_SUBCLASSING_KEY.atom)
			return atom.makeShared()
		}
	},

	/**
	 * The [raw&#32;pojo][RawPojoDescriptor] for the Java `null`
	 * value.
	 */
	RAW_POJO_NULL(55)
	{
		override fun decompose(
			obj: AvailObject,
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
		OBJECT_REFERENCE.named("class name"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			val javaClass = obj.javaObjectNotNull<Class<*>>()
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
		OBJECT_REFERENCE.named("declaring class pojo"),
		OBJECT_REFERENCE.named("method name string"),
		OBJECT_REFERENCE.named("marshaled argument pojo types"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			val method = obj.javaObjectNotNull<Method>()
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
			val (receiverType, methodName, argumentTypes) = subobjects

			val receiverClass: Class<*> = receiverType.javaObjectNotNull()
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
		OBJECT_REFERENCE.named("declaring class pojo"),
		OBJECT_REFERENCE.named("marshaled argument pojo types"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			val constructor = obj.javaObjectNotNull<Constructor<*>>()
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
			val (receiverType, argumentTypes) = subobjects

			val receiverClass: Class<*> = receiverType.javaObjectNotNull()
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
		OBJECT_REFERENCE.named("primitive class name"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			val javaClass = obj.javaObjectNotNull<Class<*>>()
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
	 * An [assignment&#32;phrase][AssignmentPhraseDescriptor].
	 */
	ASSIGNMENT_PHRASE(
		60,
		BYTE.named("flags"),
		OBJECT_REFERENCE.named("variable"),
		OBJECT_REFERENCE.named("expression"),
		OBJECT_REFERENCE.named("tokens"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			val isInline = isInline(obj)
			return array(
				fromInt(if (isInline) 1 else 0),
				obj.variable(),
				obj.expression(),
				obj.tokens())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val (isInline, variableUse, expression, tokens) = subobjects
			return newAssignment(
				variableUse, expression, tokens, !isInline.equalsInt(0))
		}
	},

	/**
	 * A [block&#32;phrase][BlockPhraseDescriptor].
	 */
	BLOCK_PHRASE(
		61,
		TUPLE_OF_OBJECTS.named("arguments tuple"),
		COMPRESSED_ARBITRARY_CHARACTER_TUPLE.named("primitive name"),
		TUPLE_OF_OBJECTS.named("statements tuple"),
		OBJECT_REFERENCE.named("result type"),
		TUPLE_OF_OBJECTS.named("declared exceptions"),
		UNSIGNED_INT.named("starting line number"),
		TUPLE_OF_OBJECTS.named("tokens"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			val primitiveName = when (val primitive = obj.primitive())
			{
				null -> emptyTuple
				else -> stringFrom(primitive.name)
			}
			return array(
				obj.argumentsTuple(),
				primitiveName,
				obj.statementsTuple(),
				obj.resultType(),
				obj.declaredExceptions().asTuple(),
				fromInt(obj.startingLineNumber()),
				obj.tokens())
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
			val primitive = when {
				primitiveName.tupleSize() == 0 -> null
				else -> primitiveByName(primitiveName.asNativeString())!!
			}
			return newBlockNode(
				argumentsTuple,
				primitive,
				statementsTuple,
				resultType,
				declaredExceptionsTuple.asSet(),
				startingLineNumber.extractInt(),
				tokens)
		}
	},

	/**
	 * A [declaration&#32;phrase][DeclarationPhraseDescriptor].
	 */
	DECLARATION_PHRASE(
		62,
		BYTE.named("declaration kind ordinal"),
		OBJECT_REFERENCE.named("token"),
		OBJECT_REFERENCE.named("declared type"),
		OBJECT_REFERENCE.named("type expression"),
		OBJECT_REFERENCE.named("initialization expression"),
		OBJECT_REFERENCE.named("literal object"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			val kind = obj.declarationKind()
			val token = obj.token()
			val declaredType = obj.declaredType()
			val typeExpression = obj.typeExpression()
			val initializationExpression = obj.initializationExpression()
			val literalObject = obj.literalObject()
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
	EXPRESSION_AS_STATEMENT_PHRASE(63, OBJECT_REFERENCE.named("expression"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(obj.expression())
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
	 * A [first-of-sequence&#32;phrase][FirstOfSequencePhraseDescriptor].
	 */
	FIRST_OF_SEQUENCE_PHRASE(64, TUPLE_OF_OBJECTS.named("statements"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(obj.statements())
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
	 * A [list&#32;phrase][ListPhraseDescriptor].
	 */
	LIST_PHRASE(65, TUPLE_OF_OBJECTS.named("expressions"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(obj.expressionsTuple())
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
	 * A [literal&#32;phrase][LiteralPhraseDescriptor].
	 */
	LITERAL_PHRASE(66, OBJECT_REFERENCE.named("literal token"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(obj.token())
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
	 * A [macro&#32;substitution&#32;phrase][MacroSubstitutionPhraseDescriptor].
	 */
	MACRO_SUBSTITUTION_PHRASE(
		67,
		OBJECT_REFERENCE.named("original phrase"),
		OBJECT_REFERENCE.named("output phrase"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				obj.macroOriginalSendNode(),
				obj.outputPhrase())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val (macroOriginalSendPhrase, outputPhrase) = subobjects
			return newMacroSubstitution(macroOriginalSendPhrase, outputPhrase)
		}
	},

	/**
	 * A [permuted&#32;list&#32;phrase][PermutedListPhraseDescriptor].
	 */
	PERMUTED_LIST_PHRASE(
		68,
		OBJECT_REFERENCE.named("list phrase"),
		TUPLE_OF_OBJECTS.named("permutation"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				obj.list(),
				obj.permutation())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val (list, permutation) = subobjects
			return newPermutedListNode(list, permutation)
		}
	},

	/**
	 * A [permuted&#32;list&#32;phrase][PermutedListPhraseDescriptor].
	 */
	REFERENCE_PHRASE(69, OBJECT_REFERENCE.named("variable use"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(obj.variable())
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
	 * A [send&#32;phrase][SendPhraseDescriptor].
	 */
	SEND_PHRASE(
		70,
		OBJECT_REFERENCE.named("bundle"),
		OBJECT_REFERENCE.named("arguments list phrase"),
		OBJECT_REFERENCE.named("return type"),
		TUPLE_OF_OBJECTS.named("tokens"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				obj.bundle(),
				obj.argumentsListNode(),
				obj.phraseExpressionType(),
				obj.tokens())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val (bundle, argsListNode, returnType, tokens) = subobjects
			return newSendNode(tokens, bundle, argsListNode, returnType)
		}
	},

	/**
	 * A [sequence&#32;phrase][SequencePhraseDescriptor].
	 */
	SEQUENCE_PHRASE(71, TUPLE_OF_OBJECTS.named("statements"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(obj.statements())
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
	 * A [super&#32;cast&#32;phrase][SuperCastPhraseDescriptor].
	 */
	SUPER_CAST_PHRASE(
		72,
		OBJECT_REFERENCE.named("expression"),
		OBJECT_REFERENCE.named("type for lookup"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				obj.expression(),
				obj.superUnionType())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val (expression, superUnionType) = subobjects
			return newSuperCastNode(expression, superUnionType)
		}
	},

	/**
	 * A [variable&#32;use&#32;phrase][VariableUsePhraseDescriptor].
	 */
	VARIABLE_USE_PHRASE(
		73,
		OBJECT_REFERENCE.named("use token"),
		OBJECT_REFERENCE.named("declaration"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				obj.token(),
				obj.declaration())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val (token, declaration) = subobjects
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
		BYTE.named("primitive type ordinal"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(fromInt(extractOrdinal(obj)))
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return Types.all()[subobjects[0].extractInt()].o
		}
	},

	/**
	 * A [variable][A_Variable] bound to a `static` Java field.
	 */
	STATIC_POJO_FIELD(
		75,
		OBJECT_REFERENCE.named("class name"),
		OBJECT_REFERENCE.named("field name"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			assert(obj.descriptor() is PojoFinalFieldDescriptor)
			val field = obj
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
			val (className, fieldName) = subobjects
			try
			{
				val classLoader = deserializer.runtime.classLoader
				val definingClass = Class.forName(
					className.asNativeString(), true, classLoader)
				val field = definingClass.getField(
					fieldName.asNativeString())
				assert(field.modifiers and Modifier.STATIC != 0)
				val fieldType = resolvePojoType(
					field.genericType, emptyMap)
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
			obj: AvailObject,
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
			obj: AvailObject,
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
			obj: AvailObject,
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
			obj: AvailObject,
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
	 * A [fiber&#32;type][FiberTypeDescriptor].
	 */
	FIBER_TYPE(80, OBJECT_REFERENCE.named("Result type"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(obj.resultType())
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
	 * A [function&#32;type][FunctionTypeDescriptor].
	 */
	FUNCTION_TYPE(
		81,
		OBJECT_REFERENCE.named("Arguments tuple type"),
		OBJECT_REFERENCE.named("Return type"),
		TUPLE_OF_OBJECTS.named("Checked exceptions"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				obj.argsTupleType(),
				obj.returnType(),
				obj.declaredExceptions().asTuple())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val (argsTupleType, returnType, checkedExceptionsTuple) = subobjects
			return functionTypeFromArgumentTupleType(
				argsTupleType, returnType, checkedExceptionsTuple.asSet())
		}
	},

	/**
	 * A [tuple&#32;type][TupleTypeDescriptor].
	 */
	TUPLE_TYPE(
		82,
		OBJECT_REFERENCE.named("Tuple sizes"),
		TUPLE_OF_OBJECTS.named("Leading types"),
		OBJECT_REFERENCE.named("Default type"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				obj.sizeRange(),
				obj.typeTuple(),
				obj.defaultType())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val (sizeRange, typeTuple, defaultType) = subobjects
			return tupleTypeForSizesTypesDefaultType(
				sizeRange, typeTuple, defaultType)
		}
	},

	/**
	 * An [integer&#32;range&#32;type][IntegerRangeTypeDescriptor].
	 */
	INTEGER_RANGE_TYPE(
		83,
		BYTE.named("Inclusive flags"),
		OBJECT_REFERENCE.named("Lower bound"),
		OBJECT_REFERENCE.named("Upper bound"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			val flags =
				(if (obj.lowerInclusive()) 1 else 0) +
					if (obj.upperInclusive()) 2 else 0
			return array(
				fromInt(flags),
				obj.lowerBound(),
				obj.upperBound())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val (flags, lowerBound, upperBound) = subobjects
			val flagsInt = flags.extractUnsignedByte().toInt()
			val lowerInclusive = flagsInt and 1 != 0
			val upperInclusive = flagsInt and 2 != 0
			return integerRangeType(
				lowerBound, lowerInclusive, upperBound, upperInclusive)
		}
	},

	/**
	 * A [pojo&#32;type][PojoTypeDescriptor] for which
	 * [AvailObject.isPojoFusedType] is `false`.  This indicates a
	 * representation with a juicy class filling, which allows a particularly
	 * compact representation involving the class name and its parameter types.
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
		OBJECT_REFERENCE.named("class name"),
		TUPLE_OF_OBJECTS.named("class parameterization"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			assert(obj.isPojoType)
			assert(!obj.isPojoFusedType)
			val rawPojoType = obj.javaClass()
			val baseClass = rawPojoType.javaObjectNotNull<Class<*>>()
			val className = stringFrom(baseClass.name)
			val ancestorMap = obj.javaAncestors()
			val myParameters = ancestorMap.mapAt(rawPojoType)
			val processedParameters = mutableListOf<A_BasicObject>()
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
			val (className, parameters) = subobjects
			val classLoader = deserializer.runtime.classLoader
			try
			{
				val processedParameters = parameters.map {
					if (it.isTuple) pojoFromSerializationProxy(it, classLoader)
					else it
				}
				val baseClass = Class.forName(
					className.asNativeString(), true, classLoader)
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
	 * A [pojo&#32;type][PojoTypeDescriptor] for which
	 * [AvailObject.isPojoFusedType] is `true`.  This indicates a representation
	 * without the juicy class filling, so we have to say how each ancestor is
	 * parameterized.
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
	FUSED_POJO_TYPE(85, GENERAL_MAP.named("ancestor parameterizations map"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			assert(obj.isPojoType)
			assert(obj.isPojoFusedType)
			var symbolicMap = emptyMap
			obj.javaAncestors().forEach { key, value ->
				val baseClass = key.javaObjectNotNull<Class<*>>()
				val className = stringFrom(baseClass.name)
				val processedParameters = mutableListOf<A_BasicObject>()
				for (parameter in value)
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
			val classLoader = deserializer.runtime.classLoader
			var ancestorMap = emptyMap
			try
			{
				subobjects[0].forEach { key, value ->
					val baseClass = Class.forName(
						key.asNativeString(), true, classLoader)
					val rawPojo = equalityPojo(baseClass)
					val processedParameters = mutableListOf<AvailObject>()
					value.forEach { parameter ->
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
	 * A [pojo&#32;type][PojoTypeDescriptor] representing a Java array type.  We
	 * can reconstruct this array type from the content type and the range of
	 * allowable sizes (a much stronger model than Java itself supports).
	 */
	ARRAY_POJO_TYPE(
		86,
		OBJECT_REFERENCE.named("content type"),
		OBJECT_REFERENCE.named("size range"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			assert(obj.isPojoArrayType)
			val contentType = obj.contentType()
			val sizeRange = obj.sizeRange()
			return array(contentType, sizeRange)
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val (contentType, sizeRange) = subobjects
			return pojoArrayType(contentType, sizeRange)
		}
	},

	/**
	 * A [set][SetDescriptor] of [class][StringDescriptor] standing in for a
	 * [pojo&#32;type][PojoTypeDescriptor] representing a "self type".  A self
	 * type is used for for parameterizing a Java class by itself.  For example,
	 * in the parametric type `Enum<E extends Enum<E>>`, we parameterize the
	 * class `Enum` with such a self type.  To reconstruct a self type all we
	 * need is a way to get to the raw Java classes involved, so we serialize
	 * their names.
	 */
	SELF_POJO_TYPE_REPRESENTATIVE(87, TUPLE_OF_OBJECTS.named("class names"))
	{
		override fun decompose(
			obj: AvailObject,
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
	 * The bottom [pojo&#32;type][PojoTypeDescriptor], representing the most
	 * specific type of pojo.
	 */
	BOTTOM_POJO_TYPE(88)
	{
		override fun decompose(
			obj: AvailObject,
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
	 * The bottom [pojo&#32;type][PojoTypeDescriptor], representing the most
	 * specific type of pojo.
	 */
	COMPILED_CODE_TYPE(
		89,
		OBJECT_REFERENCE.named("function type for code type"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(obj.functionType())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return compiledCodeTypeForFunctionType(subobjects[0])
		}
	},

	/**
	 * The bottom [pojo&#32;type][PojoTypeDescriptor], representing the most
	 * specific type of pojo.
	 */
	CONTINUATION_TYPE(
		90,
		OBJECT_REFERENCE.named("function type for continuation type"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(obj.functionType())
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
	ENUMERATION_TYPE(91, TUPLE_OF_OBJECTS.named("set of instances"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(obj.instances().asTuple())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return enumerationWith(subobjects[0].asSet())
		}
	},

	/**
	 * An Avail [singular&#32;enumeration][InstanceTypeDescriptor], a type that
	 * has a single (non-type) instance.
	 */
	INSTANCE_TYPE(92, OBJECT_REFERENCE.named("type's instance"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(obj.instance())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return instanceType(subobjects[0])
		}
	},

	/**
	 * An Avail [instance&#32;meta][InstanceMetaDescriptor], a type that has an
	 * instance `i`, which is itself a type.  Subtypes of type `i` are also
	 * considered instances of this instance meta.
	 */
	INSTANCE_META(93, OBJECT_REFERENCE.named("meta's instance"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(obj.instance())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return instanceMeta(subobjects[0])
		}
	},

	/**
	 * A [set&#32;type][SetTypeDescriptor].
	 */
	SET_TYPE(
		94,
		OBJECT_REFERENCE.named("size range"),
		OBJECT_REFERENCE.named("element type"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				obj.sizeRange(),
				obj.contentType())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val (sizeRange, contentType) = subobjects
			return setTypeForSizesContentType(sizeRange, contentType)
		}
	},

	/**
	 * A [map&#32;type][MapTypeDescriptor].
	 */
	MAP_TYPE(
		95,
		OBJECT_REFERENCE.named("size range"),
		OBJECT_REFERENCE.named("key type"),
		OBJECT_REFERENCE.named("value type"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				obj.sizeRange(),
				obj.keyType(),
				obj.valueType())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val (sizeRange, keyType, valueType) = subobjects
			return mapTypeForSizesKeyTypeValueType(
				sizeRange, keyType, valueType)
		}
	},

	/**
	 * A [token&#32;type][TokenTypeDescriptor].
	 */
	TOKEN_TYPE(96, OBJECT_REFERENCE.named("literal type"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(fromInt(obj.tokenType().ordinal))
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return tokenType(lookupTokenType(subobjects[0].extractInt()))
		}
	},

	/**
	 * A [literal&#32;token&#32;type][LiteralTokenTypeDescriptor].
	 */
	LITERAL_TOKEN_TYPE(97, OBJECT_REFERENCE.named("literal type"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(obj.literalType())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			return literalTokenType(subobjects[0])
		}
	},

	/**
	 * A [parse&#32;phrase&#32;type][PhraseTypeDescriptor].
	 */
	PARSE_NODE_TYPE(
		98,
		BYTE.named("kind"),
		OBJECT_REFERENCE.named("expression type"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				fromInt(obj.phraseKind().ordinal),
				obj.phraseTypeExpressionType())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val (phraseKindOrdinal, expressionType) = subobjects
			return PhraseKind.lookup(phraseKindOrdinal.extractInt())
				.create(expressionType)
		}
	},

	/**
	 * A [list&#32;phrase&#32;type][ListPhraseTypeDescriptor].
	 */
	LIST_NODE_TYPE(
		99,
		BYTE.named("list phrase kind"),
		OBJECT_REFERENCE.named("expression type"),
		OBJECT_REFERENCE.named("subexpressions tuple type"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			return array(
				fromInt(obj.phraseKind().ordinal),
				obj.phraseTypeExpressionType(),
				obj.subexpressionsTupleType())
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val (phraseKindOrdinal, expressionType, subexpressionsTupleType) =
				subobjects
			return createListNodeType(
				PhraseKind.lookup(phraseKindOrdinal.extractInt()),
				expressionType,
				subexpressionsTupleType)
		}
	},

	/**
	 * A [variable&#32;type][VariableTypeDescriptor] for which the read type and
	 * write type are equal.
	 */
	SIMPLE_VARIABLE_TYPE(100, OBJECT_REFERENCE.named("content type"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			val readType = obj.readType()
			assert(readType.equals(obj.writeType()))
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
	 * A [variable&#32;type][ReadWriteVariableTypeDescriptor] for which the read
	 * type and write type are (actually) unequal.
	 */
	READ_WRITE_VARIABLE_TYPE(
		101,
		OBJECT_REFERENCE.named("read type"),
		OBJECT_REFERENCE.named("write type"))
	{
		override fun decompose(
			obj: AvailObject,
			serializer: Serializer): Array<out A_BasicObject>
		{
			val readType = obj.readType()
			val writeType = obj.writeType()
			assert(!readType.equals(writeType))
			return array(readType, writeType)
		}

		override fun compose(
			subobjects: Array<AvailObject>,
			deserializer: Deserializer): A_BasicObject
		{
			val (read, write) = subobjects
			return variableReadWriteType(read, write)
		}
	},

	/**
	 * The [bottom&#32;type][BottomTypeDescriptor], more specific than all other
	 * types.
	 */
	BOTTOM_TYPE(102)
	{
		override fun decompose(
			obj: AvailObject,
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

	/** The [Statistic] for tracing for serialization, by operation. */
	internal val traceStat = Statistic(SERIALIZE_TRACE, name)

	/** The [Statistic] for serializing traced objects, by operation. */
	internal val serializeStat = Statistic(SERIALIZE_WRITE, name)

	/** The [Statistic] for deserialization, by operation. */
	internal val deserializeStat = Statistic(DESERIALIZE, name)

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
	 * @param obj
	 *   The object to decompose.
	 * @param serializer
	 *   The serializer requesting decomposition.
	 * @return
	 *   An array of `AvailObject`s whose entries agree with this
	 *   `SerializerOperation`'s operands.
	 */
	internal abstract fun decompose(
		obj: AvailObject,
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
		for (i in operandValues.indices) {
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
		operands.forEach {
			describer.append("\n\t")
			it.describe(describer)
		}
	}

	companion object
	{
		/** The array of enumeration values.  Don't change it. */
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

		/** The maximum number of operands of any SerializerOperation. */
		internal val maxSubobjects = all.maxBy {
			it.operands.size
		}!!.operands.size

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
				val atom = AtomWithPropertiesSharedDescriptor.shared
					.createInitialized(atomName, currentModule, nil, 0)
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
				if (candidates.setSize() == 1) return candidates.single()
				if (candidates.setSize() > 1)
				{
					throw RuntimeException(
						"Ambiguous atom $atomName in module $module")
				}
			}
			// This should probably fail more gracefully.
			throw RuntimeException(
				"Unknown atom $atomName in module $module")
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
