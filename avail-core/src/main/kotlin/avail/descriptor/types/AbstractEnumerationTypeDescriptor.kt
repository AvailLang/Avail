/*
 * AbstractEnumerationTypeDescriptor.kt
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
 *    and/or other materials provided with the distribution.
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
import avail.descriptor.objects.ObjectLayoutVariant
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.sets.A_Set
import avail.descriptor.sets.A_Set.Companion.setSize
import avail.descriptor.sets.SetDescriptor
import avail.descriptor.sets.SetDescriptor.Companion.setFromCollection
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.types.A_Type.Companion.computeSuperkind
import avail.descriptor.types.A_Type.Companion.instances
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.objectTypeVariant
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.optimizer.jvm.CheckedMethod
import avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import avail.optimizer.jvm.ReferencedInGeneratedCode
import avail.utility.cast

/**
 * I represent the abstract concept of enumerations. In particular, every object
 * has a type which is effectively a singular enumeration, which has as
 * instances that object plus any subtypes if that object is a
 * [type][TypeDescriptor]). Such a singular enumeration is always represented
 * via the subclass [InstanceTypeDescriptor]. Enumerations with multiple
 * elements are always represented with an [EnumerationTypeDescriptor]. Any
 * object present in this element set (or a subtype of an element that's a type)
 * is considered an instance of this enumeration. The enumeration with no
 * elements (there's only one) uses [BottomTypeDescriptor].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Construct a new `AbstractEnumerationTypeDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param typeTag
 *   The TypeTag associated with objects having this descriptor.
 * @param objectSlotsEnumClass
 *   The Java [Class] which is a subclass of [ObjectSlotsEnum] and defines this
 *   object's object slots layout, or `null` if there are no object slots.
 * @param integerSlotsEnumClass
 *   The Java [Class] which is a subclass of [IntegerSlotsEnum] and defines this
 *   object's object slots layout, or null if there are no integer slots.
 */
abstract class AbstractEnumerationTypeDescriptor
protected constructor(
	mutability: Mutability,
	typeTag: TypeTag,
	objectSlotsEnumClass: Class<out ObjectSlotsEnum>?,
	integerSlotsEnumClass: Class<out IntegerSlotsEnum>?
) : AbstractTypeDescriptor(
	mutability,
	typeTag,
	objectSlotsEnumClass,
	integerSlotsEnumClass)
{
	abstract override fun o_InstanceCount(self: AvailObject): A_Number

	abstract override fun o_Instances(self: AvailObject): A_Set

	abstract override fun o_Equals(
		self: AvailObject,
		another: A_BasicObject): Boolean

	override fun o_IsEnumeration(self: AvailObject): Boolean = true

	/**
	 * Compute the type intersection of the [object][AvailObject] which is an
	 * `AbstractEnumerationTypeDescriptor enumeration`, and the argument, which
	 * may or may not be an enumeration (but must be a [type][TypeDescriptor]).
	 *
	 * @param self
	 *   An enumeration.
	 * @param another
	 *   Another type.
	 * @return
	 *   The most general type that is a subtype of both self and `another`.
	 */
	protected abstract fun computeIntersectionWith(
		self: AvailObject,
		another: A_Type): A_Type

	/**
	 * Compute the type union of the [object][AvailObject] which is an
	 * `AbstractEnumerationTypeDescriptor enumeration`, and the argument, which
	 * may or may not be an enumeration (but must be a [type][TypeDescriptor]).
	 *
	 * @param self
	 *   An enumeration.
	 * @param another
	 *   Another type.
	 * @return
	 *   The most general type that is a subtype of both self and `another`.
	 */
	protected abstract fun computeUnionWith(
		self: AvailObject, another: A_Type): A_Type

	/**
	 * Answer the kind (i.e., a type that's not an
	 * [enumeration][AbstractEnumerationTypeDescriptor]) that is closest to this
	 * type. Fail if the object is [bottom][BottomTypeDescriptor].
	 *
	 * @param self
	 *   The enumeration.
	 * @return
	 *   The closest supertype of the argument that isn't an enumeration.
	 */
	abstract override fun o_ComputeSuperkind(self: AvailObject): A_Type

	override fun o_TypeIntersection(self: AvailObject, another: A_Type): A_Type =
		computeIntersectionWith(self, another)

	override fun o_TypeIntersectionOfContinuationType(
		self: AvailObject,
		aContinuationType: A_Type): A_Type =
			computeIntersectionWith(self, aContinuationType)

	override fun o_TypeIntersectionOfCompiledCodeType(
		self: AvailObject,
		aCompiledCodeType: A_Type): A_Type =
			computeIntersectionWith(self, aCompiledCodeType)

	override fun o_TypeIntersectionOfFiberType(
		self: AvailObject,
		aFiberType: A_Type): A_Type = computeIntersectionWith(self, aFiberType)

	override fun o_TypeIntersectionOfFunctionType(
		self: AvailObject,
		aFunctionType: A_Type): A_Type =
			computeIntersectionWith(self, aFunctionType)

	override fun o_TypeIntersectionOfIntegerRangeType(
		self: AvailObject,
		anIntegerRangeType: A_Type): A_Type =
			computeIntersectionWith(self, anIntegerRangeType)

	override fun o_TypeIntersectionOfTokenType(
		self: AvailObject,
		aTokenType: A_Type): A_Type =
			computeIntersectionWith(self, aTokenType)

	override fun o_TypeIntersectionOfLiteralTokenType(
		self: AvailObject,
		aLiteralTokenType: A_Type): A_Type =
			computeIntersectionWith(self, aLiteralTokenType)

	override fun o_TypeIntersectionOfListNodeType(
		self: AvailObject,
		aListNodeType: A_Type): A_Type =
			computeIntersectionWith(self, aListNodeType)

	override fun o_TypeIntersectionOfMapType(
		self: AvailObject,
		aMapType: A_Type): A_Type = computeIntersectionWith(self, aMapType)

	override fun o_TypeIntersectionOfPhraseType(
		self: AvailObject,
		aPhraseType: A_Type): A_Type =
			computeIntersectionWith(self, aPhraseType)

	override fun o_TypeIntersectionOfPrimitiveTypeEnum(
		self: AvailObject,
		primitiveTypeEnum: Types): A_Type =
			computeIntersectionWith(self, primitiveTypeEnum.o)

	override fun o_TypeIntersectionOfVariableType(
		self: AvailObject,
		aVariableType: A_Type): A_Type =
			computeIntersectionWith(self, aVariableType)

	override fun o_TypeIntersectionOfObjectType(
		self: AvailObject,
		anObjectType: AvailObject): A_Type =
			computeIntersectionWith(self, anObjectType)

	override fun o_TypeIntersectionOfPojoType(
		self: AvailObject,
		aPojoType: A_Type): A_Type =
		computeIntersectionWith(self, aPojoType)

	override fun o_TypeIntersectionOfSetType(
		self: AvailObject,
		aSetType: A_Type): A_Type =
			computeIntersectionWith(self, aSetType)

	override fun o_TypeIntersectionOfTupleType(
		self: AvailObject,
		aTupleType: A_Type): A_Type = computeIntersectionWith(self, aTupleType)

	override fun o_TypeUnion(
		self: AvailObject,
		another: A_Type): A_Type = computeUnionWith(self, another)

	override fun o_TypeUnionOfContinuationType(
		self: AvailObject,
		aContinuationType: A_Type): A_Type =
			computeUnionWith(self, aContinuationType)

	override fun o_TypeUnionOfCompiledCodeType(
		self: AvailObject,
		aCompiledCodeType: A_Type): A_Type =
			computeUnionWith(self, aCompiledCodeType)

	override fun o_TypeUnionOfTokenType(
		self: AvailObject,
		aTokenType: A_Type): A_Type = computeUnionWith(self, aTokenType)

	override fun o_TypeUnionOfLiteralTokenType(
		self: AvailObject,
		aLiteralTokenType: A_Type): A_Type =
			computeUnionWith(self, aLiteralTokenType)

	override fun o_TypeUnionOfFiberType(
		self: AvailObject,
		aFiberType: A_Type): A_Type = computeUnionWith(self, aFiberType)

	override fun o_TypeUnionOfFunctionType(
		self: AvailObject,
		aFunctionType: A_Type): A_Type = computeUnionWith(self, aFunctionType)

	override fun o_TypeUnionOfVariableType(
		self: AvailObject,
		aVariableType: A_Type): A_Type = computeUnionWith(self, aVariableType)

	override fun o_TypeUnionOfIntegerRangeType(
		self: AvailObject,
		anIntegerRangeType: A_Type): A_Type =
			computeUnionWith(self, anIntegerRangeType)

	override fun o_TypeUnionOfListNodeType(
		self: AvailObject,
		aListNodeType: A_Type): A_Type = computeUnionWith(self, aListNodeType)

	override fun o_TypeUnionOfMapType(
		self: AvailObject,
		aMapType: A_Type): A_Type = computeUnionWith(self, aMapType)

	override fun o_TypeUnionOfObjectType(
		self: AvailObject,
		anObjectType: AvailObject): A_Type =
			computeUnionWith(self, anObjectType)

	override fun o_TypeUnionOfPhraseType(
		self: AvailObject,
		aPhraseType: A_Type): A_Type = computeUnionWith(self, aPhraseType)

	override fun o_TypeUnionOfPojoType(
		self: AvailObject,
		aPojoType: A_Type): A_Type = computeUnionWith(self, aPojoType)

	override fun o_TypeUnionOfPrimitiveTypeEnum(
		self: AvailObject,
		primitiveTypeEnum: Types): A_Type =
			computeUnionWith(self, primitiveTypeEnum.o)

	override fun o_TypeUnionOfSetType(
		self: AvailObject,
		aSetType: A_Type): A_Type = computeUnionWith(self, aSetType)

	override fun o_TypeUnionOfTupleType(
		self: AvailObject,
		aTupleType: A_Type): A_Type = computeUnionWith(self, aTupleType)

	abstract override fun o_IsInstanceOf(
		self: AvailObject,
		aType: A_Type): Boolean

	override fun o_IsInstanceOfKind(
		self: AvailObject,
		aType: A_Type): Boolean = self.kind().isSubtypeOf(aType)

	abstract override fun o_FieldTypeAt(
		self: AvailObject,
		field: A_Atom): A_Type

	abstract override fun o_FieldTypeAtOrNull(
		self: AvailObject,
		field: A_Atom): A_Type?

	abstract override fun o_FieldTypeMap(self: AvailObject): A_Map

	abstract override fun o_LowerBound(self: AvailObject): A_Number

	abstract override fun o_LowerInclusive(self: AvailObject): Boolean

	abstract override fun o_UpperBound(self: AvailObject): A_Number

	abstract override fun o_UpperInclusive(self: AvailObject): Boolean

	abstract override fun o_TypeAtIndex(
		self: AvailObject,
		index: Int): A_Type

	abstract override fun o_UnionOfTypesAtThrough(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int): A_Type

	abstract override fun o_DefaultType(self: AvailObject): A_Type

	abstract override fun o_SizeRange(self: AvailObject): A_Type

	abstract override fun o_TypeTuple(self: AvailObject): A_Tuple

	abstract override fun o_TupleOfTypesFromTo(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int): A_Tuple

	abstract override fun o_IsSubtypeOf(
		self: AvailObject,
		aType: A_Type): Boolean

	abstract override fun o_IsIntegerRangeType(self: AvailObject): Boolean

	abstract override fun o_IsLiteralTokenType(self: AvailObject): Boolean

	abstract override fun o_IsMapType(self: AvailObject): Boolean

	abstract override fun o_IsSetType(self: AvailObject): Boolean

	abstract override fun o_IsTupleType(self: AvailObject): Boolean

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

	abstract override fun o_ArgsTupleType(self: AvailObject): A_Type

	abstract override fun o_DeclaredExceptions(self: AvailObject): A_Set

	abstract override fun o_FunctionType(self: AvailObject): A_Type

	abstract override fun o_ContentType(self: AvailObject): A_Type

	abstract override fun o_CouldEverBeInvokedWith(
		self: AvailObject,
		argRestrictions: List<TypeRestriction>): Boolean

	override fun o_IsBetterRepresentationThan(
		self: AvailObject,
		anotherObject: A_BasicObject): Boolean = true

	override fun o_RepresentationCostOfTupleType(self: AvailObject): Int = 0

	override fun o_IsSupertypeOfFiberType(
		self: AvailObject,
		aType: A_Type): Boolean = false

	override fun o_IsSupertypeOfFunctionType(
		self: AvailObject,
		aFunctionType: A_Type): Boolean = false

	override fun o_IsSupertypeOfVariableType(
		self: AvailObject,
		aVariableType: A_Type): Boolean = false

	override fun o_IsSupertypeOfContinuationType(
		self: AvailObject,
		aContinuationType: A_Type): Boolean = false

	override fun o_IsSupertypeOfCompiledCodeType(
		self: AvailObject,
		aCompiledCodeType: A_Type): Boolean = false

	override fun o_IsSupertypeOfListNodeType(
		self: AvailObject,
		aListNodeType: A_Type): Boolean = false

	override fun o_IsSupertypeOfTokenType(
		self: AvailObject,
		aTokenType: A_Type): Boolean = false

	override fun o_IsSupertypeOfLiteralTokenType(
		self: AvailObject,
		aLiteralTokenType: A_Type): Boolean = false

	override fun o_IsSupertypeOfIntegerRangeType(
		self: AvailObject,
		anIntegerRangeType: A_Type): Boolean = false

	override fun o_IsSupertypeOfMapType(
		self: AvailObject,
		aMapType: AvailObject): Boolean = false

	override fun o_IsSupertypeOfObjectType(
		self: AvailObject,
		anObjectType: AvailObject): Boolean = false

	override fun o_IsSupertypeOfPhraseType(
		self: AvailObject,
		aPhraseType: A_Type): Boolean = false

	override fun o_IsSupertypeOfPojoType(
		self: AvailObject,
		aPojoType: A_Type): Boolean = false

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

	abstract override fun o_KeyType(self: AvailObject): A_Type

	abstract override fun o_Parent(self: AvailObject): A_BasicObject

	abstract override fun o_ReturnType(self: AvailObject): A_Type

	abstract override fun o_ValueType(self: AvailObject): A_Type

	abstract override fun o_EnumerationIncludesInstance(
		self: AvailObject,
		potentialInstance: AvailObject): Boolean

	abstract override fun o_ReadType(self: AvailObject): A_Type

	abstract override fun o_WriteType(self: AvailObject): A_Type

	abstract override fun o_PhraseTypeExpressionType(self: AvailObject): A_Type

	override fun o_IsBottom(self: AvailObject): Boolean = false

	// This type is literally composed of counterexamples.  Overridden in
	// BottomTypeDescriptor.
	override fun o_IsVacuousType(self: AvailObject): Boolean = false

	override fun o_IsTop(self: AvailObject): Boolean = false

	abstract override fun o_ComputeTypeTag(self: AvailObject): TypeTag

	//TODO - This probably isn't correct.  Dispatching on an enumeration
	// of objects of different shape should probably best be done by
	// duplicating subtrees for each variant.
	override fun o_ObjectTypeVariant(self: AvailObject): ObjectLayoutVariant =
		self.computeSuperkind().objectTypeVariant

	override fun o_FieldTypeAtIndex(self: AvailObject, index: Int): A_Type =
		instanceTypeOrMetaOn(
			setFromCollection(
				self.instances.mapTo(mutableSetOf()) { instance ->
					instance.fieldAtIndex(index)
				}))


	companion object
	{
		/**
		 * Answer a new object instance of this descriptor based on the set of
		 * objects that will be considered instances of that type. Normalize the
		 * cases where the set has zero or one elements to use
		 * [BottomTypeDescriptor] and [InstanceTypeDescriptor], respectively.
		 *
		 * Note that we also have to assure type union metainvariance, namely:
		 * <sub>x,yT</sub>(T(x)T(y) = T(xy)).
		 * Thus, if there are multiple instances which are types, use their type
		 * union as a single member in place of them.
		 *
		 *
		 * @param instancesSet
		 *   The [set][SetDescriptor] of objects which are to be instances of
		 *   the new type.
		 * @return
		 *   An [AvailObject] representing the type whose instances are those
		 *   objects specified in the argument.
		 */
		fun enumerationWith(instancesSet: A_Set): A_Type
		{
			val setSize = instancesSet.setSize
			if (setSize == 0)
			{
				return bottom
			}
			val typeCount = instancesSet.count(AvailObject::isType)
			return when
			{
				typeCount == 0 && setSize == 1 ->
					instanceType(instancesSet.single())
				typeCount == 0 ->
					EnumerationTypeDescriptor.fromNormalizedSet(instancesSet)
				// They're all types.
				typeCount == setSize ->
					instanceMeta(
						instancesSet.reduce { union: A_Type, type ->
							union.typeUnion(type).cast()
						})
				// It's a mix of types and non-types.
				else -> Types.ANY.o
			}
		}

		/**
		 * Answer a new object instance of this descriptor based on the single
		 * object that will be considered an instance of that type. If a type is
		 * specified, all subtypes will also be considered instances of that
		 * type.
		 *
		 * @param instance
		 *   The [object][AvailObject] which is to be an instance of the new
		 *   type.
		 * @return
		 *   An [AvailObject] representing the type whose instance is the object
		 *   specified in the argument.
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun instanceTypeOrMetaOn(instance: A_BasicObject): A_Type =
			if (instance.isType)
			{
				instanceMeta(instance as A_Type)
			}
			else
			{
				instanceType(instance)
			}

		/**
		 * The [CheckedMethod] for [instanceTypeOrMetaOn].
		 */
		val instanceTypeOrMetaOnMethod = staticMethod(
			AbstractEnumerationTypeDescriptor::class.java,
			::instanceTypeOrMetaOn.name,
			A_Type::class.java,
			A_BasicObject::class.java)
	}
}
