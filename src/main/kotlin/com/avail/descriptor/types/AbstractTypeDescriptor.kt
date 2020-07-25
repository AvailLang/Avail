/*
 * AbstractTypeDescriptor.kt
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

import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.numbers.A_Number
import com.avail.descriptor.representation.*
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.tuples.A_Tuple
import com.avail.interpreter.levelTwo.operand.TypeRestriction
import com.avail.serialization.SerializerOperation

/**
 * `AbstractTypeDescriptor` explicitly defines the responsibilities of all
 * [Avail&#32;types][TypeDescriptor]. Many of these operations are actually
 * undefined in subclasses, in clear violation of the Liskov substitution
 * principle, yet this organization is still useful to see the aggregate
 * capabilities of Avail types.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct a new `AbstractTypeDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param typeTag
 *   The [TypeTag] to embed in the new descriptor.
 * @param objectSlotsEnumClass
 *   The Java [Class] which is a subclass of [ObjectSlotsEnum] and defines this
 *   object's object slots layout, or null if there are no object slots.
 * @param integerSlotsEnumClass
 *   The Java [Class] which is a subclass of [IntegerSlotsEnum] and defines this
 *   object's object slots layout, or null if there are no integer slots.
 */
abstract class AbstractTypeDescriptor protected constructor(
	mutability: Mutability,
	typeTag: TypeTag,
	objectSlotsEnumClass: Class<out ObjectSlotsEnum>?,
	integerSlotsEnumClass: Class<out IntegerSlotsEnum>?) : Descriptor(
		mutability, typeTag, objectSlotsEnumClass, integerSlotsEnumClass)
{
	abstract override fun o_AcceptsArgTypesFromFunctionType(
		self: AvailObject,
		functionType: A_Type): Boolean

	abstract override fun o_AcceptsListOfArgTypes(
		self: AvailObject,
		argTypes: List<A_Type>): Boolean

	abstract override fun o_AcceptsListOfArgValues(
		self: AvailObject,
		argValues: List<A_BasicObject>): Boolean

	abstract override fun o_AcceptsTupleOfArgTypes(
		self: AvailObject,
		argTypes: A_Tuple): Boolean

	abstract override fun o_AcceptsTupleOfArguments(
		self: AvailObject,
		arguments: A_Tuple): Boolean

	abstract override fun o_ArgsTupleType(
		self: AvailObject): A_Type

	abstract override fun o_DeclaredExceptions(
		self: AvailObject): A_Set

	abstract override fun o_FunctionType(
		self: AvailObject): A_Type

	abstract override fun o_ContentType(
		self: AvailObject): A_Type

	abstract override fun o_CouldEverBeInvokedWith(
		self: AvailObject,
		argRestrictions: List<TypeRestriction>): Boolean

	abstract override fun o_DefaultType(
		self: AvailObject): A_Type

	abstract override fun o_Equals(
		self: AvailObject,
		another: A_BasicObject): Boolean

	abstract override fun o_FieldTypeAt(
		self: AvailObject,
		field: A_Atom): A_Type

	abstract override fun o_FieldTypeAtOrNull(
		self: AvailObject,
		field: A_Atom): A_Type?

	abstract override fun o_FieldTypeMap(
		self: AvailObject): A_Map

	abstract override fun o_Hash(self: AvailObject): Int

	abstract override fun o_HasObjectInstance(
		self: AvailObject,
		potentialInstance: AvailObject): Boolean

	abstract override fun o_InstanceCount(
		self: AvailObject): A_Number

	abstract override fun o_IsBetterRepresentationThan(
		self: AvailObject,
		anotherObject: A_BasicObject): Boolean

	abstract override fun o_RepresentationCostOfTupleType(
		self: AvailObject): Int

	abstract override fun o_IsInstanceOfKind(
		self: AvailObject,
		aType: A_Type): Boolean

	abstract override fun o_IsIntegerRangeType(
		self: AvailObject): Boolean

	abstract override fun o_IsMapType(self: AvailObject): Boolean

	abstract override fun o_IsSetType(self: AvailObject): Boolean

	abstract override fun o_IsSubtypeOf(
		self: AvailObject,
		aType: A_Type): Boolean

	abstract override fun o_IsSupertypeOfFiberType(
		self: AvailObject,
		aType: A_Type): Boolean

	abstract override fun o_IsSupertypeOfFunctionType(
		self: AvailObject,
		aFunctionType: A_Type): Boolean

	abstract override fun o_IsSupertypeOfVariableType(
		self: AvailObject,
		aVariableType: A_Type): Boolean

	abstract override fun o_IsSupertypeOfContinuationType(
		self: AvailObject,
		aContinuationType: A_Type): Boolean

	abstract override fun o_IsSupertypeOfCompiledCodeType(
		self: AvailObject,
		aCompiledCodeType: A_Type): Boolean

	abstract override fun o_IsSupertypeOfIntegerRangeType(
		self: AvailObject,
		anIntegerRangeType: A_Type): Boolean

	abstract override fun o_IsSupertypeOfListNodeType(
		self: AvailObject,
		aListNodeType: A_Type): Boolean

	abstract override fun o_IsSupertypeOfTokenType(
		self: AvailObject,
		aTokenType: A_Type): Boolean

	abstract override fun o_IsSupertypeOfLiteralTokenType(
		self: AvailObject,
		aLiteralTokenType: A_Type): Boolean

	abstract override fun o_IsSupertypeOfMapType(
		self: AvailObject,
		aMapType: AvailObject): Boolean

	abstract override fun o_IsSupertypeOfObjectType(
		self: AvailObject,
		anObjectType: AvailObject): Boolean

	abstract override fun o_IsSupertypeOfPhraseType(
		self: AvailObject,
		aPhraseType: A_Type): Boolean

	abstract override fun o_IsSupertypeOfPojoType(
		self: AvailObject,
		aPojoType: A_Type): Boolean

	abstract override fun o_IsSupertypeOfPrimitiveTypeEnum(
		self: AvailObject,
		primitiveTypeEnum: TypeDescriptor.Types): Boolean

	abstract override fun o_IsSupertypeOfSetType(
		self: AvailObject,
		aSetType: A_Type): Boolean

	abstract override fun o_IsSupertypeOfTupleType(
		self: AvailObject,
		aTupleType: A_Type): Boolean

	abstract override fun o_IsSupertypeOfPojoBottomType(
		self: AvailObject,
		aPojoType: A_Type): Boolean

	// All types are supertypes of bottom.
	override fun o_IsSupertypeOfBottom(self: AvailObject): Boolean = true

	abstract override fun o_IsTupleType(self: AvailObject): Boolean

	override fun o_IsType(self: AvailObject): Boolean = true

	abstract override fun o_KeyType(self: AvailObject): A_Type

	// A type's kind is always ANY, since there are no more metatypes that
	// are kinds.
	override fun o_Kind(self: AvailObject): A_Type =
		TypeDescriptor.Types.ANY.o()

	abstract override fun o_LowerBound(self: AvailObject): A_Number

	abstract override fun o_LowerInclusive(self: AvailObject): Boolean

	abstract override fun o_Parent(self: AvailObject): A_BasicObject

	abstract override fun o_ReturnType(self: AvailObject): A_Type

	abstract override fun o_SizeRange(self: AvailObject): A_Type

	abstract override fun o_TypeAtIndex(self: AvailObject, index: Int): A_Type

	abstract override fun o_TypeIntersection(
		self: AvailObject,
		another: A_Type): A_Type

	abstract override fun o_TypeIntersectionOfFiberType(
		self: AvailObject,
		aFiberType: A_Type): A_Type

	abstract override fun o_TypeIntersectionOfFunctionType(
		self: AvailObject,
		aFunctionType: A_Type): A_Type

	abstract override fun o_TypeIntersectionOfVariableType(
		self: AvailObject,
		aVariableType: A_Type): A_Type

	abstract override fun o_TypeIntersectionOfContinuationType(
		self: AvailObject,
		aContinuationType: A_Type): A_Type

	abstract override fun o_TypeIntersectionOfCompiledCodeType(
		self: AvailObject,
		aCompiledCodeType: A_Type): A_Type

	abstract override fun o_TypeIntersectionOfIntegerRangeType(
		self: AvailObject,
		anIntegerRangeType: A_Type): A_Type

	abstract override fun o_TypeIntersectionOfListNodeType(
		self: AvailObject,
		aListNodeType: A_Type): A_Type

	abstract override fun o_TypeIntersectionOfTokenType(
		self: AvailObject,
		aTokenType: A_Type): A_Type

	abstract override fun o_TypeIntersectionOfLiteralTokenType(
		self: AvailObject,
		aLiteralTokenType: A_Type): A_Type

	abstract override fun o_TypeIntersectionOfMapType(
		self: AvailObject,
		aMapType: A_Type): A_Type

	abstract override fun o_TypeIntersectionOfObjectType(
		self: AvailObject,
		anObjectType: AvailObject): A_Type

	abstract override fun o_TypeIntersectionOfPhraseType(
		self: AvailObject,
		aPhraseType: A_Type): A_Type

	abstract override fun o_TypeIntersectionOfPojoType(
		self: AvailObject,
		aPojoType: A_Type): A_Type

	abstract override fun o_TypeIntersectionOfPrimitiveTypeEnum(
		self: AvailObject,
		primitiveTypeEnum: TypeDescriptor.Types): A_Type

	abstract override fun o_TypeIntersectionOfSetType(
		self: AvailObject,
		aSetType: A_Type): A_Type

	abstract override fun o_TypeIntersectionOfTupleType(
		self: AvailObject,
		aTupleType: A_Type): A_Type

	abstract override fun o_TypeTuple(self: AvailObject): A_Tuple

	abstract override fun o_TypeUnion(
		self: AvailObject,
		another: A_Type): A_Type

	abstract override fun o_TypeUnionOfFiberType(
		self: AvailObject,
		aFiberType: A_Type): A_Type

	abstract override fun o_TypeUnionOfFunctionType(
		self: AvailObject,
		aFunctionType: A_Type): A_Type

	abstract override fun o_TypeUnionOfVariableType(
		self: AvailObject,
		aVariableType: A_Type): A_Type

	abstract override fun o_TypeUnionOfContinuationType(
		self: AvailObject,
		aContinuationType: A_Type): A_Type

	abstract override fun o_TypeUnionOfCompiledCodeType(
		self: AvailObject,
		aCompiledCodeType: A_Type): A_Type

	abstract override fun o_TypeUnionOfIntegerRangeType(
		self: AvailObject,
		anIntegerRangeType: A_Type): A_Type

	abstract override fun o_TypeUnionOfListNodeType(
		self: AvailObject,
		aListNodeType: A_Type): A_Type

	abstract override fun o_TypeUnionOfTokenType(
		self: AvailObject,
		aTokenType: A_Type): A_Type

	abstract override fun o_TypeUnionOfLiteralTokenType(
		self: AvailObject,
		aLiteralTokenType: A_Type): A_Type

	abstract override fun o_TypeUnionOfMapType(
		self: AvailObject,
		aMapType: A_Type): A_Type

	abstract override fun o_TypeUnionOfObjectType(
		self: AvailObject,
		anObjectType: AvailObject): A_Type

	abstract override fun o_TypeUnionOfPhraseType(
		self: AvailObject,
		aPhraseType: A_Type): A_Type

	abstract override fun o_TypeUnionOfPrimitiveTypeEnum(
		self: AvailObject,
		primitiveTypeEnum: TypeDescriptor.Types): A_Type

	abstract override fun o_TypeUnionOfSetType(
		self: AvailObject,
		aSetType: A_Type): A_Type

	abstract override fun o_TypeUnionOfPojoType(
		self: AvailObject,
		aPojoType: A_Type): A_Type

	abstract override fun o_TypeUnionOfTupleType(
		self: AvailObject,
		aTupleType: A_Type): A_Type

	abstract override fun o_UnionOfTypesAtThrough(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int): A_Type

	abstract override fun o_UpperBound(
		self: AvailObject): A_Number

	abstract override fun o_UpperInclusive(
		self: AvailObject): Boolean

	abstract override fun o_ValueType(
		self: AvailObject): A_Type

	abstract override fun o_RangeIncludesLong(
		self: AvailObject,
		aLong: Long
	): Boolean

	abstract override fun o_SerializerOperation(
		self: AvailObject): SerializerOperation

	abstract override fun o_IsBottom(self: AvailObject): Boolean

	abstract override fun o_IsVacuousType(self: AvailObject): Boolean

	abstract override fun o_IsTop(self: AvailObject): Boolean
}
