/*
 * A_Type.ky
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
package com.avail.descriptor.types

import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.numbers.A_Number
import com.avail.descriptor.objects.ObjectTypeDescriptor
import com.avail.descriptor.phrases.A_Phrase
import com.avail.descriptor.phrases.BlockPhraseDescriptor
import com.avail.descriptor.phrases.PhraseDescriptor
import com.avail.descriptor.phrases.SendPhraseDescriptor
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.tokens.TokenDescriptor
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import com.avail.interpreter.levelTwo.operand.TypeRestriction
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.CheckedMethod.Companion.instanceMethod
import com.avail.optimizer.jvm.ReferencedInGeneratedCode

/**
 * `A_Type` is an interface that specifies the operations specific to all of
 * Avail's types.  It's a sub-interface of [A_BasicObject], the interface that
 * defines the behavior that all AvailObjects are required to support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_Type : A_BasicObject
{
	/**
	 * Answer whether the [argument&amp;#32;types][AvailObject.argsTupleType]
	 * supported by the specified [function type][FunctionTypeDescriptor] are
	 * acceptable argument types for invoking a [function][FunctionDescriptor]
	 * whose type is the receiver.
	 *
	 * @param functionType
	 *   A function type.
	 * @return
	 *   `true` if the arguments of the receiver are, pairwise, more general
	 *   than those of `functionType`, `false` otherwise.
	 */
	fun acceptsArgTypesFromFunctionType(functionType: A_Type): Boolean

	/**
	 * Answer whether these are acceptable
	 * [argument&amp;#32;types][TypeDescriptor] for invoking a
	 * [function][FunctionDescriptor] whose type is the receiver.
	 *
	 * @param argTypes
	 *   A list containing the argument types to be checked.
	 * @return
	 *   `true` if the arguments of the receiver are, pairwise, more general
	 *   than those within the `argTypes` list, `false` otherwise.
	 */
	fun acceptsListOfArgTypes(argTypes: List<A_Type>): Boolean

	/**
	 * Answer whether these are acceptable arguments for invoking a
	 * [function][FunctionDescriptor] whose type is the receiver.
	 *
	 * @param argValues
	 *   A list containing the argument values to be checked.
	 * @return
	 *   `true` if the arguments of the receiver are, pairwise, more general
	 *   than the types of the values within the `argValues` list, `false`
	 *   otherwise.
	 */
	fun acceptsListOfArgValues(argValues: List<A_BasicObject>): Boolean

	/**
	 * Answer whether these are acceptable
	 * [argument&amp;#32;types][TypeDescriptor] for invoking a
	 * [function][FunctionDescriptor] that is an instance of the receiver. There
	 * may be more entries in the [tuple][TupleDescriptor] than are required by
	 * the [function type][FunctionTypeDescriptor].
	 *
	 * @param argTypes
	 *   A tuple containing the argument types to be checked.
	 * @return
	 *   `true` if the arguments of the receiver are, pairwise, more general
	 *   than the corresponding elements of the `argTypes` tuple, `false`
	 *   otherwise.
	 */
	fun acceptsTupleOfArgTypes(argTypes: A_Tuple): Boolean

	/**
	 * Answer whether these are acceptable arguments for invoking a
	 * [function][FunctionDescriptor] that is an instance of the receiver. There
	 * may be more entries in the [tuple][TupleDescriptor] than are required by
	 * the [function type][FunctionTypeDescriptor].
	 *
	 * @param arguments
	 *   A tuple containing the argument values to be checked.
	 * @return
	 *   `true` if the arguments of the receiver are, pairwise, more general
	 *   than the types of the corresponding elements of the `arguments` tuple,
	 *   `false` otherwise.
	 */
	fun acceptsTupleOfArguments(arguments: A_Tuple): Boolean

	/**
	 * Answer the tuple type describing this function type's argument types.
	 *
	 * @return
	 *   The tuple type for a function type's arguments.
	 */
	@ReferencedInGeneratedCode
	fun argsTupleType(): A_Type

	/**
	 * Answer the type of elements that this set type's sets may hold.
	 *
	 * @return
	 *   The set type's content type.
	 */
	fun contentType(): A_Type

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param argRestrictions
	 * @return
	 */
	fun couldEverBeInvokedWith(argRestrictions: List<TypeRestriction>): Boolean

	/**
	 * Also declared in [A_Phrase] for
	 * [block&amp;#32;phrases][BlockPhraseDescriptor] and
	 * [send&#32;phrases][SendPhraseDescriptor].
	 *
	 * @return
	 *   The set of declared exception types.
	 */
	fun declaredExceptions(): A_Set

	/**
	 * Dispatch to the descriptor.
	 *
	 * @return
	 */
	fun defaultType(): A_Type

	/**
	 * Return the phrase type's expression type, which is the type of object
	 * that will be produced by phrases of that type.
	 *
	 * Also implemented in [A_Phrase] (for phrase instances).
	 *
	 * @return
	 *   The [type][TypeDescriptor] of the [AvailObject] that will be produced
	 *   by this type of phrase.
	 */
	fun expressionType(): A_Type

	/**
	 * Given an [object&#32;type][ObjectTypeDescriptor], answer its map from
	 * fields to types.
	 *
	 * @return
	 *   The map of field types.
	 */
	fun fieldTypeMap(): A_Map

	/**
	 * @return
	 */
	fun functionType(): A_Type

	/**
	 * Answer whether this type is ⊥ ([bottom][BottomTypeDescriptor]), the most
	 * specific type.
	 *
	 * @return
	 *   Whether the type is bottom.
	 */
	val isBottom: Boolean

	/**
	 * Answer whether this type is known to have no instances.  For example, the
	 * [bottom type][BottomTypeDescriptor] (denoted ⊥) has no instances.
	 *
	 * @return
	 *   Whether the type is known to have no instances.
	 */
	val isVacuousType: Boolean

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param aType
	 * @return
	 */
	@ReferencedInGeneratedCode
	fun isSubtypeOf(aType: A_Type): Boolean

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param aVariableType
	 * @return
	 */
	fun isSupertypeOfVariableType(aVariableType: A_Type): Boolean

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param aContinuationType
	 * @return
	 */
	fun isSupertypeOfContinuationType(aContinuationType: A_Type): Boolean

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param aFiberType
	 * @return
	 */
	fun isSupertypeOfFiberType(aFiberType: A_Type): Boolean

	/**
	 * Dispatch to the descriptor.
	 * @param aFunctionType
	 * @return
	 */
	fun isSupertypeOfFunctionType(aFunctionType: A_Type): Boolean

	/**
	 * Dispatch to the descriptor.
	 * @param anIntegerRangeType
	 * @return
	 */
	fun isSupertypeOfIntegerRangeType(anIntegerRangeType: A_Type): Boolean

	/**
	 * Dispatch to the descriptor.
	 * @param aTokenType
	 * @return
	 */
	fun isSupertypeOfTokenType(aTokenType: A_Type): Boolean

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param aLiteralTokenType
	 * @return
	 */
	fun isSupertypeOfLiteralTokenType(aLiteralTokenType: A_Type): Boolean

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param aMapType
	 * @return
	 */
	fun isSupertypeOfMapType(aMapType: AvailObject): Boolean

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param anObjectType
	 * @return
	 */
	fun isSupertypeOfObjectType(anObjectType: AvailObject): Boolean

	/**
	 * @param aPhraseType
	 * @return
	 */
	fun isSupertypeOfPhraseType(aPhraseType: A_Type): Boolean

	/**
	 * Dispatch to the descriptor
	 *
	 * @param aPojoType
	 * @return
	 */
	fun isSupertypeOfPojoType(aPojoType: A_Type): Boolean

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param primitiveTypeEnum
	 * @return
	 */
	fun isSupertypeOfPrimitiveTypeEnum(
		primitiveTypeEnum: TypeDescriptor.Types): Boolean

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param aSetType
	 * @return
	 */
	fun isSupertypeOfSetType(aSetType: A_Type): Boolean

	/**
	 * Dispatch to the descriptor.
	 */
	val isSupertypeOfBottom: Boolean

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param aTupleType
	 * @return
	 */
	fun isSupertypeOfTupleType(aTupleType: A_Type): Boolean

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param anEnumerationType
	 * @return
	 */
	fun isSupertypeOfEnumerationType(anEnumerationType: A_Type): Boolean

	/**
	 * @param aCompiledCodeType
	 * @return
	 */
	fun isSupertypeOfCompiledCodeType(aCompiledCodeType: A_Type): Boolean

	/**
	 * @param aPojoType
	 * @return
	 */
	fun isSupertypeOfPojoBottomType(aPojoType: A_Type): Boolean

	/**
	 * Answer whether this type is ⊤ ([top][TypeDescriptor.Types.TOP]), the most
	 * general type.
	 *
	 * @return
	 *   Whether the type is type.
	 */
	val isTop: Boolean

	/**
	 * Dispatch to the descriptor.
	 * @return
	 */
	fun keyType(): A_Type

	/**
	 * Dispatch to the descriptor.
	 * @return
	 */
	fun lowerBound(): A_Number

	/**
	 * Dispatch to the descriptor.
	 * @return
	 */
	fun lowerInclusive(): Boolean

	/**
	 * Dispatch to the descriptor.
	 * @return
	 */
	fun parent(): A_BasicObject

	/**
	 * Also declared in [A_Phrase] for [phrases][PhraseDescriptor], not just
	 * phrase types.
	 *
	 * @return
	 *   Answer the phrase's PhraseKind.
	 */
	fun phraseKind(): PhraseKind

	/**
	 * @return
	 */
	fun readType(): A_Type

	/**
	 * Also declared in [A_Phrase] for
	 * [block&amp;#32;phrases][BlockPhraseDescriptor] and [send
	 * phrases][SendPhraseDescriptor].
	 * @return
	 */
	fun returnType(): A_Type

	/**
	 * Dispatch to the descriptor.
	 * @return
	 */
	fun sizeRange(): A_Type

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param startIndex
	 * @param endIndex
	 * @return
	 */
	fun tupleOfTypesFromTo(startIndex: Int, endIndex: Int): A_Tuple

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param index
	 * @return
	 */
	@ReferencedInGeneratedCode
	fun typeAtIndex(index: Int): A_Type

	/**
	 * Dispatch to the descriptor.
	 *
	 * @return
	 */
	fun typeTuple(): A_Tuple

	/**
	 * @param aCompiledCodeType
	 * @return
	 */
	fun typeUnionOfCompiledCodeType(aCompiledCodeType: A_Type): A_Type

	/**
	 * @param aFusedPojoType
	 * @return
	 */
	fun typeIntersectionOfPojoFusedType(aFusedPojoType: A_Type): A_Type

	/**
	 * @param anUnfusedPojoType
	 * @return
	 */
	fun typeIntersectionOfPojoUnfusedType(anUnfusedPojoType: A_Type): A_Type

	/**
	 * @param aFusedPojoType
	 * @return
	 */
	fun typeUnionOfPojoFusedType(aFusedPojoType: A_Type): A_Type

	/**
	 * @param anUnfusedPojoType
	 * @return
	 */
	fun typeUnionOfPojoUnfusedType(anUnfusedPojoType: A_Type): A_Type

	/**
	 * @param aTokenType
	 * @return
	 */
	fun typeIntersectionOfTokenType(aTokenType: A_Type): A_Type

	/**
	 * @param aLiteralTokenType
	 * @return
	 */
	fun typeIntersectionOfLiteralTokenType(aLiteralTokenType: A_Type): A_Type

	/**
	 * @param aTokenType
	 * @return
	 */
	fun typeUnionOfTokenType(aTokenType: A_Type): A_Type

	/**
	 * @param aLiteralTokenType
	 * @return
	 */
	fun typeUnionOfLiteralTokenType(aLiteralTokenType: A_Type): A_Type

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param another
	 * @return
	 */
	fun typeIntersection(another: A_Type): A_Type

	/**
	 * @param aCompiledCodeType
	 * @return
	 */
	fun typeIntersectionOfCompiledCodeType(aCompiledCodeType: A_Type): A_Type

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param aFiberType
	 * @return
	 */
	fun typeIntersectionOfFiberType(aFiberType: A_Type): A_Type

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param aFunctionType
	 * @return
	 */
	fun typeIntersectionOfFunctionType(aFunctionType: A_Type): A_Type

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param aListNodeType
	 * @return
	 */
	fun typeIntersectionOfListNodeType(aListNodeType: A_Type): A_Type

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param aVariableType
	 * @return
	 */
	fun typeIntersectionOfVariableType(aVariableType: A_Type): A_Type

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param aContinuationType
	 * @return
	 */
	fun typeIntersectionOfContinuationType(aContinuationType: A_Type): A_Type

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param anIntegerRangeType
	 * @return
	 */
	fun typeIntersectionOfIntegerRangeType(anIntegerRangeType: A_Type): A_Type

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param aMapType
	 * @return
	 */
	fun typeIntersectionOfMapType(aMapType: A_Type): A_Type

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param anObjectType
	 * @return
	 */
	fun typeIntersectionOfObjectType(anObjectType: AvailObject): A_Type

	/**
	 * @param aPhraseType
	 * @return
	 */
	fun typeIntersectionOfPhraseType(aPhraseType: A_Type): A_Type

	/**
	 * @param aPojoType
	 * @return
	 */
	fun typeIntersectionOfPojoType(aPojoType: A_Type): A_Type

	/**
	 * @param primitiveTypeEnum
	 * @return
	 */
	fun typeIntersectionOfPrimitiveTypeEnum(
		primitiveTypeEnum: TypeDescriptor.Types): A_Type

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param aSetType
	 * @return
	 */
	fun typeIntersectionOfSetType(aSetType: A_Type): A_Type

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param aTupleType
	 * @return
	 */
	fun typeIntersectionOfTupleType(aTupleType: A_Type): A_Type

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param another
	 * @return
	 */
	@ReferencedInGeneratedCode
	fun typeUnion(another: A_Type): A_Type

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param aFiberType
	 * @return
	 */
	fun typeUnionOfFiberType(aFiberType: A_Type): A_Type

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param aFunctionType
	 * @return
	 */
	fun typeUnionOfFunctionType(aFunctionType: A_Type): A_Type

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param aVariableType
	 * @return
	 */
	fun typeUnionOfVariableType(aVariableType: A_Type): A_Type

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param aContinuationType
	 * @return
	 */
	fun typeUnionOfContinuationType(aContinuationType: A_Type): A_Type

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param anIntegerRangeType
	 * @return
	 */
	fun typeUnionOfIntegerRangeType(anIntegerRangeType: A_Type): A_Type

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param aListNodeType
	 * @return
	 */
	fun typeUnionOfListNodeType(aListNodeType: A_Type): A_Type

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param aMapType
	 * @return
	 */
	fun typeUnionOfMapType(aMapType: A_Type): A_Type

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param anObjectType
	 * @return
	 */
	fun typeUnionOfObjectType(anObjectType: AvailObject): A_Type

	/**
	 * @param aPhraseType
	 * @return
	 */
	fun typeUnionOfPhraseType(aPhraseType: A_Type): A_Type

	/**
	 * @param aPojoType
	 * @return
	 */
	fun typeUnionOfPojoType(aPojoType: A_Type): A_Type

	/**
	 * @param primitiveTypeEnum
	 * @return
	 */
	fun typeUnionOfPrimitiveTypeEnum(
		primitiveTypeEnum: TypeDescriptor.Types): A_Type

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param aSetType
	 * @return
	 */
	fun typeUnionOfSetType(aSetType: A_Type): A_Type

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param aTupleType
	 * @return
	 */
	fun typeUnionOfTupleType(aTupleType: A_Type): A_Type

	/**
	 * @return
	 */
	fun typeVariables(): A_Map

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param startIndex
	 * @param endIndex
	 * @return
	 */
	fun unionOfTypesAtThrough(startIndex: Int, endIndex: Int): A_Type

	/**
	 * Dispatch to the descriptor.
	 *
	 * @return
	 */
	fun upperBound(): A_Number

	/**
	 * Dispatch to the descriptor.
	 *
	 * @return
	 */
	fun upperInclusive(): Boolean

	/**
	 * @return
	 */
	fun writeType(): A_Type

	/**
	 * Compute a [type][TypeDescriptor] that is an ancestor of the receiver, but
	 * is not an [enumeration][AbstractEnumerationTypeDescriptor].  Choose the
	 * most specific such type.  Fail if the receiver is not itself an
	 * enumeration.  Also fail if the receiver is
	 * [bottom][BottomTypeDescriptor].
	 *
	 * @return
	 *   The must specific non-union supertype.
	 */
	fun computeSuperkind(): A_Type

	/**
	 * @return
	 */
	val isEnumeration: Boolean

	/**
	 * @return
	 */
	fun valueType(): A_Type

	/**
	 * @return
	 */
	fun fieldTypeTuple(): A_Tuple

	/**
	 * Dispatch to the descriptor.
	 *
	 * @param potentialInstance
	 * @return
	 */
	fun hasObjectInstance(potentialInstance: AvailObject): Boolean

	/**
	 * @return
	 */
	fun instance(): AvailObject

	/**
	 * @return
	 */
	fun instanceCount(): A_Number

	/**
	 * @return
	 */
	fun instances(): A_Set

	/**
	 * @return
	 */
	fun tokenType(): TokenDescriptor.TokenType

	/**
	 * @return
	 */
	fun literalType(): A_Type

	/**
	 * @param anInt
	 * @return
	 */
	fun rangeIncludesInt(anInt: Int): Boolean

	/**
	 * Also declared in A_Phrase, so the same operation applies both to phrases
	 * and to phrase types.
	 *
	 * @param expectedPhraseKind
	 *   The [PhraseKind] to test this phrase type against.
	 * @return
	 *   Whether the receiver, a phrase type, has a [.phraseKind] at or below
	 *   the specified [PhraseKind].
	 */
	fun phraseKindIsUnder(expectedPhraseKind: PhraseKind): Boolean

	/**
	 * Answer the type of the subexpressions tuple that instances (list phrases)
	 * of me (a list phrase type) must have.
	 *
	 * @return
	 *   A tuple type of phrases.
	 */
	fun subexpressionsTupleType(): A_Type

	/**
	 * Answer whether the receiver, a type, is a supertype of the given
	 * [list&amp;#32;phrase&amp;#32;type][ListPhraseTypeDescriptor].
	 *
	 * @param aListNodeType
	 *   The list phrase type.
	 * @return
	 *   Whether the receiver is a supertype of the given type.
	 */
	fun isSupertypeOfListNodeType(aListNodeType: A_Type): Boolean

	companion object
	{
		/** The [CheckedMethod] for [.argsTupleType].  */
		val argsTupleTypeMethod = instanceMethod(
			A_Type::class.java,
			"argsTupleType",
			A_Type::class.java)

		/** The [CheckedMethod] for [.isSubtypeOf].  */
		val isSubtypeOfMethod = instanceMethod(
			A_Type::class.java,
			"isSubtypeOf",
			Boolean::class.javaPrimitiveType!!,
			A_Type::class.java)

		/** The [CheckedMethod] for [.typeAtIndex].  */
		val typeAtIndexMethod = instanceMethod(
			A_Type::class.java,
			"typeAtIndex",
			A_Type::class.java,
			Int::class.javaPrimitiveType!!)

		/** The [CheckedMethod] for [.typeUnion].  */
		val typeUnionMethod = instanceMethod(
			A_Type::class.java,
			"typeUnion",
			A_Type::class.java,
			A_Type::class.java)
	}
}