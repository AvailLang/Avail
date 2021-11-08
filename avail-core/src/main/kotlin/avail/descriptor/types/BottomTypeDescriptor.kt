/*
 * BottomTypeDescriptor.kt
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
package avail.descriptor.types

import avail.descriptor.atoms.A_Atom
import avail.descriptor.maps.A_Map
import avail.descriptor.numbers.A_Number
import avail.descriptor.numbers.InfinityDescriptor.Companion.negativeInfinity
import avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.Mutability
import avail.descriptor.sets.A_Set
import avail.descriptor.sets.A_Set.Companion.setSize
import avail.descriptor.sets.SetDescriptor
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.RepeatedElementTupleDescriptor.Companion.createRepeatedElementTuple
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.isSupertypeOfPrimitiveTypeEnum
import avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.serialization.SerializerOperation
import org.availlang.json.JSONWriter
import java.util.IdentityHashMap

/**
 * `BottomTypeDescriptor` represents Avail's most specific type, ⊥ (pronounced
 * bottom). ⊥ is an abstract type; it cannot have any instances, since its
 * instances must be able to meaningfully perform all operations, and this is
 * clearly logically inconsistent.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *  Construct a new `BottomTypeDescriptor`.
 */
class BottomTypeDescriptor
private constructor() : AbstractEnumerationTypeDescriptor(
	Mutability.SHARED,
	TypeTag.BOTTOM_TYPE_TAG,
	null,
	null)
{
	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		builder.append("⊥")
	}

	/**
	 * Compute the type intersection of the object which is the bottom type,
	 * and the argument, which may be any type.
	 *
	 * @param self
	 *   The bottom type.
	 * @param another
	 *   Another type.
	 * @return
	 *   The most general type that is a subtype of both object and another.
	 */
	public override fun computeIntersectionWith(
		self: AvailObject,
		another: A_Type): A_Type = self // Easy -- it's always the type bottom.

	/**
	 * Compute the type union of the object which is the bottom type, and
	 * the argument, which may be any type.
	 *
	 * @param self
	 *   The bottom type.
	 * @param another
	 *   Another type.
	 * @return
	 *   The most specific type that is a supertype of both object and another.
	 */
	public override fun computeUnionWith(
		self: AvailObject,
		another: A_Type): A_Type
	{
		// Easy -- it's always the other type.
		assert(another.isType)
		return another
	}

	override fun o_AcceptsArgTypesFromFunctionType(
		self: AvailObject,
		functionType: A_Type): Boolean = true

	override fun o_AcceptsListOfArgTypes(
		self: AvailObject,
		argTypes: List<A_Type>): Boolean = true

	override fun o_AcceptsListOfArgValues(
		self: AvailObject,
		argValues: List<A_BasicObject>): Boolean = true

	override fun o_AcceptsTupleOfArgTypes(
		self: AvailObject,
		argTypes: A_Tuple): Boolean = true

	override fun o_AcceptsTupleOfArguments(
		self: AvailObject,
		arguments: A_Tuple): Boolean = true

	// Because ⊥ is a subtype of all other types, it is considered a
	// function type. In particular, if ⊥ is viewed as a function type, it
	// can take any number of arguments of any type (since there are no
	// complying function instances).
	override fun o_ArgsTupleType(self: AvailObject): A_Type =
		TupleTypeDescriptor.mostGeneralTupleType

	/**
	 * {@inheritDoc}
	 *
	 * Even though bottom is a union-y type (and the most specific one), it
	 * technically "is" also a kind (a non-union-y type).  Thus, it's still
	 * technically correct to return bottom as the nearest kind.  Code that
	 * relies on this operation *not* returning a union-y type should
	 * deal with this one special case with correspondingly special logic.
	 */
	override fun o_ComputeSuperkind(self: AvailObject): A_Type = self

	override fun o_ContentType(self: AvailObject): A_Type = self

	override fun o_CouldEverBeInvokedWith(
		self: AvailObject,
		argRestrictions: List<TypeRestriction>): Boolean = true

	override fun o_DeclaredExceptions(self: AvailObject): A_Set = emptySet

	// Since I'm a degenerate tuple type, I must answer ⊥.
	override fun o_DefaultType(self: AvailObject): A_Type = self

	/**
	 * Bottom is an empty [enumeration][AbstractEnumerationTypeDescriptor], so
	 * the answer is `false`.
	 */
	override fun o_EnumerationIncludesInstance(
		self: AvailObject,
		potentialInstance: AvailObject): Boolean = false

	/**
	 * {@inheritDoc}
	 *
	 * An instance type is only equal to another instance type, and only when
	 * they refer to equal instances.
	 */
	override fun o_Equals(
		self: AvailObject,
		another: A_BasicObject): Boolean =
			another.traversed().sameAddressAs(self)

	/**
	 * {@inheritDoc}
	 *
	 * Determine if the object is an
	 * [enumeration][AbstractEnumerationTypeDescriptor] over the given
	 * [set][SetDescriptor] of instances.  Since the object is the
	 * [bottom&#32;type][BottomTypeDescriptor], just check if the set of
	 * instances is empty.
	 */
	override fun o_EqualsEnumerationWithSet(
		self: AvailObject,
		aSet: A_Set): Boolean = aSet.setSize == 0

	override fun o_PhraseTypeExpressionType(self: AvailObject): A_Type = self

	// All fields would be present for this type, but they would have type
	// bottom.
	override fun o_FieldTypeAt(self: AvailObject, field: A_Atom): A_Type = self

	// All fields would be present for this type, but they would have type
	// bottom.
	override fun o_FieldTypeAtOrNull(
		self: AvailObject,
		field: A_Atom
	): A_Type = self

	override fun o_FieldTypeMap(self: AvailObject): A_Map
	{
		// TODO: [MvG] It's unclear what to return here. Maybe raise an
		// unchecked exception. Or if we ever implement more precise map types
		// containing key type -> value type pairs we might be able to change
		// the object type interface to use one of those instead of a map.
		unsupportedOperation()
	}

	override fun o_FunctionType(self: AvailObject): A_Type = self

	override fun o_Hash(self: AvailObject): Int = 0x4a22a80a

	override fun o_HasObjectInstance(
		self: AvailObject,
		potentialInstance: AvailObject): Boolean = false

	// ⊥ is the empty enumeration.
	override fun o_InstanceCount(self: AvailObject): A_Number = zero

	// ⊥ is the empty enumeration.
	override fun o_Instances(self: AvailObject): A_Set = emptySet

	override fun o_IsBottom(self: AvailObject): Boolean = true

	override fun o_IsVacuousType(self: AvailObject): Boolean = true

	override fun o_IsInstanceOf(
		self: AvailObject,
		aType: A_Type): Boolean
	{
		// Bottom is an instance of every metatype except for itself.
		assert(aType.isType)
		if (self.equals(aType))
		{
			// Bottom is not an instance of itself.
			return false
		}
		if (aType.isEnumeration)
		{
			return aType.enumerationIncludesInstance(self)
		}
		// Bottom is an instance of top and any.
		return if (aType.isTop || aType.equals(ANY.o))
		{
			true
		}
		else aType.isSubtypeOf(InstanceMetaDescriptor.topMeta())
		// Bottom is an instance of every meta (everything that inherits
		// from TYPE).
	}

	override fun o_IsInstanceOfKind(
		self: AvailObject,
		aType: A_Type): Boolean
	{
		assert(!aType.isBottom)
		return (aType.isSupertypeOfPrimitiveTypeEnum(ANY)
				|| aType.isSubtypeOf(InstanceMetaDescriptor.topMeta()))
	}

	// Because ⊥ is a subtype of all other types, it is considered an
	// integer range type - in particular, the degenerate integer type
	// (∞..-∞).
	override fun o_IsIntegerRangeType(self: AvailObject): Boolean = true

	override fun o_IsLiteralTokenType(self: AvailObject): Boolean = true

	// Because ⊥ is a subtype of all other types, it is considered a map
	// type - in particular, a degenerate map type. Its size range is ⊥, its
	// key type is ⊥, and its value type is ⊥.
	override fun o_IsMapType(self: AvailObject): Boolean = true

	override fun o_IsPojoArrayType(self: AvailObject): Boolean = true

	override fun o_IsPojoFusedType(self: AvailObject): Boolean = true

	override fun o_IsPojoSelfType(self: AvailObject): Boolean = false

	override fun o_IsPojoType(self: AvailObject): Boolean = true

	override fun o_IsSetType(self: AvailObject): Boolean = true

	override fun o_IsSubtypeOf(self: AvailObject, aType: A_Type): Boolean = true

	// Because ⊥ is a subtype of all other types, it is considered a tuple
	// type - in particular, a degenerate tuple type. Its size range is ⊥,
	// its leading type tuple is <>, and its default type is ⊥.
	override fun o_IsTupleType(self: AvailObject): Boolean = true

	// Answer what type my keys are. Since I'm a degenerate map type,
	// answer ⊥.
	override fun o_KeyType(self: AvailObject): A_Type = self

	// Pretend we go from +∞ to -∞ exclusive. That should be a nice empty
	// range.
	override fun o_LowerBound(self: AvailObject): A_Number = positiveInfinity

	// Pretend we go from +∞ to -∞ exclusive. That should be a nice empty
	// range.
	override fun o_LowerInclusive(self: AvailObject): Boolean = false

	override fun o_Parent(self: AvailObject): A_BasicObject
	{
		unsupportedOperation()
	}

	override fun o_RangeIncludesLong(self: AvailObject, aLong: Long): Boolean =
		false

	override fun o_ReturnType(self: AvailObject): A_Type = self

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.BOTTOM_TYPE

	// Answer what sizes my instances can be. Since I'm a degenerate
	// map type, answer ⊥, a degenerate integer type.
	override fun o_SizeRange(self: AvailObject): A_Type = self

	// See ListPhraseDescriptor.
	override fun o_SubexpressionsTupleType(self: AvailObject): A_Type = self

	override fun o_TrimType(self: AvailObject, typeToRemove: A_Type): A_Type =
		self

	// Answer what type the given index would have in an object instance of
	// me. Answer ⊥ if the index is out of bounds, which is always because
	// I'm a degenerate tuple type.
	override fun o_TypeAtIndex(self: AvailObject, index: Int): A_Type = self

	// Since I'm a degenerate tuple type, I have no leading types.
	override fun o_TypeTuple(self: AvailObject): A_Tuple = emptyTuple

	// Answer the union of the types the given indices would have in an
	// object instance of me. Answer ⊥ if the index is out of bounds, which
	// is always because I'm a degenerate tuple type.
	override fun o_UnionOfTypesAtThrough(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int): A_Type = self

	// Pretend we go from +∞ to -∞ exclusive. That should be a nice empty
	// range.
	override fun o_UpperBound(self: AvailObject): A_Number = negativeInfinity

	// Pretend we go from +∞ to -∞ exclusive. That should be a nice empty
	// range.
	override fun o_UpperInclusive(self: AvailObject): Boolean = false

	// Answer what type my values are. Since I'm a degenerate map type,
	// answer ⊥.
	override fun o_ValueType(self: AvailObject): A_Type = self

	override fun o_ReadType(self: AvailObject): A_Type = Types.TOP.o

	// Answer the tuple of types over the given range of indices.  Any
	// indices out of range for this tuple type will be ⊥.
	override fun o_TupleOfTypesFromTo(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int): A_Tuple = createRepeatedElementTuple(
			endIndex - startIndex + 1,
			self)

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write("bottom")
		writer.endObject()
	}

	override fun o_WriteType(self: AvailObject): A_Type = bottom

	override fun o_ComputeTypeTag(self: AvailObject): TypeTag = unsupported

	override fun o_InstanceTag(self: AvailObject): TypeTag =
		TypeTag.NIL_TAG  // Shouldn't happen.

	override fun o_ComputeInstanceTag(self: AvailObject): TypeTag = unsupported

	override fun mutable(): BottomTypeDescriptor
	{
		unsupportedOperation()
	}

	override fun immutable(): BottomTypeDescriptor
	{
		unsupportedOperation()
	}

	override fun shared(): BottomTypeDescriptor = shared

	companion object
	{
		/** The shared [BottomTypeDescriptor]. */
		private val shared = BottomTypeDescriptor()

		/**
		 * The unique object that represents the type with no instances.
		 */
		val bottom: A_Type = shared.create()

		/** The meta-type with exactly one instance, [bottom]. */
		val bottomMeta: A_Type = instanceMeta(bottom).makeShared()
	}
}
