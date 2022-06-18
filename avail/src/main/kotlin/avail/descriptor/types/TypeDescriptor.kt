/*
 * TypeDescriptor.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.descriptor.types

import avail.descriptor.atoms.A_Atom
import avail.descriptor.maps.A_Map
import avail.descriptor.numbers.A_Number
import avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.sets.A_Set
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.types.A_Type.Companion.computeInstanceTag
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.interpreter.levelTwo.operand.TypeRestriction

/**
 * Every object in Avail has a type.  Types are also Avail objects.  The types
 * are related to each other by the [subtype][A_Type.isSubtypeOf] relation in
 * such a way that they form a lattice.  The top of the lattice is
 * [⊤][Types.TOP] (pronounced "top"), which is the most general type.  Every
 * object conforms with this type, and every subtype is a subtype of it.  The
 * bottom of the lattice is [⊥][BottomTypeDescriptor] (pronounced "bottom"),
 * which is the most specific type.  It has no instances, and it is a subtype of
 * all other types.
 *
 * The type lattice has a number of useful properties, such as closure under
 * type union.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Construct a new `TypeDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param typeTag
 *   The [TypeTag] to embed in the new descriptor.
 * @param objectSlotsEnumClass
 *   The Java [Class] which is a subclass of [ObjectSlotsEnum] and defines this
 *   object's object slots layout, or `null` if there are no object slots.
 * @param integerSlotsEnumClass
 *   The Java [Class] which is a subclass of [IntegerSlotsEnum] and defines this
 *   object's object slots layout, or `null` if there are no integer slots.
 */
abstract class TypeDescriptor
protected constructor(
	mutability: Mutability,
	typeTag: TypeTag,
	val instanceTag: TypeTag,
	objectSlotsEnumClass: Class<out ObjectSlotsEnum>?,
	integerSlotsEnumClass: Class<out IntegerSlotsEnum>?
) : AbstractTypeDescriptor(
	mutability,
	typeTag,
	objectSlotsEnumClass,
	integerSlotsEnumClass)
{

	override fun o_AcceptsArgTypesFromFunctionType(
		self: AvailObject,
		functionType: A_Type): Boolean = unsupported

	override fun o_AcceptsListOfArgTypes(
		self: AvailObject,
		argTypes: List<A_Type>): Boolean = unsupported

	override fun o_AcceptsListOfArgValues(
		self: AvailObject,
		argValues: List<A_BasicObject>): Boolean = unsupported

	override fun o_AcceptsTupleOfArgTypes(
		self: AvailObject,
		argTypes: A_Tuple): Boolean = unsupported

	override fun o_AcceptsTupleOfArguments(
		self: AvailObject,
		arguments: A_Tuple): Boolean = unsupported

	override fun o_ArgsTupleType(self: AvailObject): A_Type = unsupported

	override fun o_ComputeInstanceTag(self: AvailObject): TypeTag
	{
		if (typeTag == TypeTag.UNKNOWN_TAG) {
			throw UnsupportedOperationException(
				"${this::class.simpleName} should have overridden this " +
					"because its typeTag is UNKNOWN_TAG")
		}
		throw UnsupportedOperationException(
			"This should only be called if typeTag is UNKNOWN_TAG")
	}

	override fun o_ContentType(self: AvailObject): A_Type = unsupported

	override fun o_CouldEverBeInvokedWith(
		self: AvailObject,
		argRestrictions: List<TypeRestriction>): Boolean = unsupported

	override fun o_DeclaredExceptions(self: AvailObject): A_Set = unsupported

	override fun o_DefaultType(self: AvailObject): A_Type = unsupported

	abstract override fun o_Equals(
		self: AvailObject,
		another: A_BasicObject): Boolean

	override fun o_FunctionType(self: AvailObject): A_Type = unsupported

	override fun o_FieldTypeAt(self: AvailObject, field: A_Atom): A_Type =
		unsupported

	override fun o_FieldTypeAtOrNull(
		self: AvailObject,
		field: A_Atom
	): A_Type? = unsupported

	override fun o_FieldTypeMap(self: AvailObject): A_Map = unsupported

	override fun o_HasObjectInstance(
		self: AvailObject,
		potentialInstance: AvailObject): Boolean = false

	override fun o_InstanceCount(self: AvailObject): A_Number = positiveInfinity

	override fun o_IsBetterRepresentationThan(
		self: AvailObject,
		anotherObject: A_BasicObject): Boolean = true

	override fun o_RepresentationCostOfTupleType(
		self: AvailObject): Int = unsupported

	override fun o_InstanceTag(self: AvailObject): TypeTag =
		when (val tag = instanceTag)
		{
			TypeTag.UNKNOWN_TAG -> self.computeInstanceTag()
			else -> tag
		}

	override fun o_IsBottom(self: AvailObject): Boolean = false

	override fun o_IsVacuousType(self: AvailObject): Boolean = false

	override fun o_IsInstanceOfKind(
		self: AvailObject,
		aType: A_Type
	): Boolean = self.kind().isSubtypeOf(aType)

	override fun o_IsIntegerRangeType(self: AvailObject): Boolean = false

	override fun o_IsMapType(self: AvailObject): Boolean = false

	override fun o_IsSetType(self: AvailObject): Boolean = false

	override fun o_IsSupertypeOfFiberType(
		self: AvailObject,
		aType: A_Type): Boolean = false

	override fun o_IsSupertypeOfFunctionType(
		self: AvailObject,
		aFunctionType: A_Type): Boolean = false

	// By default, nothing is a supertype of a variable type unless it
	// states otherwise.
	override fun o_IsSupertypeOfVariableType(
		self: AvailObject,
		aVariableType: A_Type): Boolean = false

	override fun o_IsSupertypeOfContinuationType(
		self: AvailObject,
		aContinuationType: A_Type): Boolean = false

	override fun o_IsSupertypeOfCompiledCodeType(
		self: AvailObject,
		aCompiledCodeType: A_Type): Boolean = false

	// By default, nothing is a supertype of an integer range type unless
	// it states otherwise.
	override fun o_IsSupertypeOfIntegerRangeType(
		self: AvailObject,
		anIntegerRangeType: A_Type): Boolean = false

	// By default, nothing is a supertype of a list phrase type unless it
	// states otherwise.
	override fun o_IsSupertypeOfListNodeType(
		self: AvailObject,
		aListNodeType: A_Type): Boolean = false

	// By default, nothing is a supertype of a token type unless it states
	// otherwise.
	override fun o_IsSupertypeOfTokenType(
		self: AvailObject,
		aTokenType: A_Type): Boolean = false

	// By default, nothing is a supertype of a literal token type unless it
	// states otherwise.
	override fun o_IsSupertypeOfLiteralTokenType(
		self: AvailObject,
		aLiteralTokenType: A_Type): Boolean = false

	override fun o_IsSupertypeOfMapType(
		self: AvailObject,
		aMapType: AvailObject): Boolean = false

	// By default, nothing is a supertype of an eager object type unless it
	// states otherwise.
	override fun o_IsSupertypeOfObjectType(
		self: AvailObject,
		anObjectType: AvailObject): Boolean = false

	override fun o_IsSupertypeOfPhraseType(
		self: AvailObject,
		aPhraseType: A_Type): Boolean = false

	override fun o_IsSupertypeOfPojoType(
		self: AvailObject,
		aPojoType: A_Type): Boolean = false

	/* Check if object (some specialized type) is a supertype of
	 * aPrimitiveType (some primitive type).  The only primitive type this
	 * specialized type could be a supertype of is bottom, but
	 * bottom doesn't dispatch this message.  Overridden in
	 * PrimitiveTypeDescriptor.
	 */
	override fun o_IsSupertypeOfPrimitiveTypeEnum(
		self: AvailObject,
		primitiveTypeEnum: Types): Boolean = false

	override fun o_IsSupertypeOfSetType(
		self: AvailObject,
		aSetType: A_Type): Boolean = false

	override fun o_IsSupertypeOfTupleType(
		self: AvailObject,
		aTupleType: A_Type): Boolean = false

	override fun o_IsSupertypeOfEnumerationType(
		self: AvailObject,
		anEnumerationType: A_Type): Boolean = false

	override fun o_IsSupertypeOfPojoBottomType(
		self: AvailObject,
		aPojoType: A_Type): Boolean = false

	override fun o_IsTop(self: AvailObject): Boolean = false

	override fun o_IsTupleType(self: AvailObject): Boolean = false

	override fun o_KeyType(self: AvailObject): A_Type = unsupported

	override fun o_LowerBound(self: AvailObject): A_Number = unsupported

	override fun o_LowerInclusive(self: AvailObject): Boolean = unsupported

	// Most Avail types are opaque to Java, and can be characterized by the
	// class of AvailObject.
	override fun o_MarshalToJava(
		self: AvailObject,
		classHint: Class<*>?
	): Any? = AvailObject::class.java

	override fun o_Parent(self: AvailObject): A_BasicObject = unsupported

	override fun o_RangeIncludesLong(self: AvailObject, aLong: Long) = false

	override fun o_ReturnType(self: AvailObject): A_Type = unsupported

	override fun o_SizeRange(self: AvailObject): A_Type = unsupported

	override fun o_TrimType(self: AvailObject, typeToRemove: A_Type): A_Type
	{
		if (self.isSubtypeOf(typeToRemove)) return bottom
		return self
	}

	override fun o_TypeAtIndex(self: AvailObject, index: Int): A_Type =
		unsupported

	override fun o_TypeIntersectionOfFiberType(
		self: AvailObject,
		aFiberType: A_Type): A_Type = bottom

	override fun o_TypeIntersectionOfFunctionType(
		self: AvailObject,
		aFunctionType: A_Type): A_Type = bottom

	override fun o_TypeIntersectionOfVariableType(
		self: AvailObject,
		aVariableType: A_Type): A_Type = bottom

	override fun o_TypeIntersectionOfContinuationType(
		self: AvailObject,
		aContinuationType: A_Type): A_Type = bottom

	override fun o_TypeIntersectionOfCompiledCodeType(
		self: AvailObject,
		aCompiledCodeType: A_Type): A_Type = bottom

	override fun o_TypeIntersectionOfIntegerRangeType(
		self: AvailObject,
		anIntegerRangeType: A_Type): A_Type = bottom

	override fun o_TypeIntersectionOfListNodeType(
		self: AvailObject,
		aListNodeType: A_Type): A_Type = bottom

	override fun o_TypeIntersectionOfTokenType(
		self: AvailObject,
		aTokenType: A_Type): A_Type = bottom

	override fun o_TypeIntersectionOfLiteralTokenType(
		self: AvailObject,
		aLiteralTokenType: A_Type): A_Type = bottom

	override fun o_TypeIntersectionOfMapType(
		self: AvailObject,
		aMapType: A_Type): A_Type = bottom

	override fun o_TypeIntersectionOfObjectType(
		self: AvailObject,
		anObjectType: AvailObject): A_Type = bottom

	override fun o_TypeIntersectionOfPhraseType(
		self: AvailObject,
		aPhraseType: A_Type): A_Type = bottom

	override fun o_TypeIntersectionOfPojoType(
		self: AvailObject,
		aPojoType: A_Type): A_Type = bottom

	override fun o_TypeIntersectionOfPrimitiveTypeEnum(
		self: AvailObject,
		primitiveTypeEnum: Types): A_Type =
			if (Types.NONTYPE.superTests[primitiveTypeEnum.ordinal]) self
			else bottom


	override fun o_TypeIntersectionOfSetType(
		self: AvailObject,
		aSetType: A_Type): A_Type = bottom

	override fun o_TypeIntersectionOfTupleType(
		self: AvailObject,
		aTupleType: A_Type): A_Type = bottom

	override fun o_TypeTuple(self: AvailObject): A_Tuple = unsupported

	override fun o_TypeUnionOfFiberType(
		self: AvailObject,
		aFiberType: A_Type): A_Type = self.typeUnion(Types.NONTYPE.o)

	override fun o_TypeUnionOfFunctionType(
		self: AvailObject,
		aFunctionType: A_Type): A_Type = self.typeUnion(Types.NONTYPE.o)

	override fun o_TypeUnionOfVariableType(
		self: AvailObject,
		aVariableType: A_Type): A_Type = self.typeUnion(Types.NONTYPE.o)

	override fun o_TypeUnionOfContinuationType(
		self: AvailObject,
		aContinuationType: A_Type): A_Type = self.typeUnion(Types.NONTYPE.o)

	override fun o_TypeUnionOfCompiledCodeType(
		self: AvailObject,
		aCompiledCodeType: A_Type): A_Type = self.typeUnion(Types.NONTYPE.o)

	override fun o_TypeUnionOfIntegerRangeType(
		self: AvailObject,
		anIntegerRangeType: A_Type): A_Type = self.typeUnion(Types.NUMBER.o)

	override fun o_TypeUnionOfListNodeType(
		self: AvailObject,
		aListNodeType: A_Type): A_Type = self.typeUnion(Types.NONTYPE.o)

	override fun o_TypeUnionOfTokenType(
		self: AvailObject,
		aTokenType: A_Type): A_Type = self.typeUnion(Types.TOKEN.o)

	override fun o_TypeUnionOfLiteralTokenType(
		self: AvailObject,
		aLiteralTokenType: A_Type): A_Type = self.typeUnion(Types.TOKEN.o)

	override fun o_TypeUnionOfMapType(
		self: AvailObject,
		aMapType: A_Type): A_Type = self.typeUnion(Types.NONTYPE.o)

	override fun o_TypeUnionOfObjectType(
		self: AvailObject,
		anObjectType: AvailObject): A_Type = self.typeUnion(Types.NONTYPE.o)

	override fun o_TypeUnionOfPhraseType(
		self: AvailObject,
		aPhraseType: A_Type): A_Type = self.typeUnion(Types.NONTYPE.o)

	override fun o_TypeUnionOfPojoType(
		self: AvailObject,
		aPojoType: A_Type): A_Type = self.typeUnion(Types.NONTYPE.o)

	override fun o_TypeUnionOfPrimitiveTypeEnum(
		self: AvailObject,
		primitiveTypeEnum: Types): A_Type
	{
		var anotherAncestor = primitiveTypeEnum
		while (true)
		{
			if (self.isSubtypeOf(anotherAncestor.o))
			{
				return anotherAncestor.o
			}
			anotherAncestor = anotherAncestor.parent!!
		}
	}

	override fun o_TypeUnionOfSetType(
		self: AvailObject,
		aSetType: A_Type): A_Type = self.typeUnion(Types.NONTYPE.o)

	override fun o_TypeUnionOfTupleType(
		self: AvailObject,
		aTupleType: A_Type): A_Type = self.typeUnion(Types.NONTYPE.o)

	override fun o_UnionOfTypesAtThrough(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int): A_Type = unsupported

	override fun o_UpperBound(
		self: AvailObject): A_Number = unsupported

	override fun o_UpperInclusive(self: AvailObject): Boolean = unsupported

	override fun o_ValueType(self: AvailObject): A_Type = unsupported

	companion object
	{
		/**
		 * Answer whether the first type is a proper subtype of the second type,
		 * meaning that it's a subtype but not equal.
		 *
		 * @param type1
		 *   The purported subtype.
		 * @param type2
		 *   The purported supertype.
		 * @return
		 *   If type1 is a subtype of but not equal to type2.
		 */
		fun isProperSubtype(
			type1: A_Type,
			type2: A_Type): Boolean
		{
			return !type1.equals(type2) && type1.isSubtypeOf(type2)
		}
	}
}
