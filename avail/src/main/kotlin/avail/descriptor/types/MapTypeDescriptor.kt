/*
 * MapTypeDescriptor.kt
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

import avail.descriptor.numbers.A_Number.Companion.equalsInt
import avail.descriptor.numbers.A_Number.Companion.lessOrEqual
import avail.descriptor.numbers.A_Number.Companion.minusCanDestroy
import avail.descriptor.numbers.A_Number.Companion.plusCanDestroy
import avail.descriptor.numbers.IntegerDescriptor.Companion.one
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.combine4
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.types.A_Type.Companion.computeSuperkind
import avail.descriptor.types.A_Type.Companion.instanceCount
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.isSupertypeOfMapType
import avail.descriptor.types.A_Type.Companion.keyType
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.trimType
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.A_Type.Companion.typeIntersectionOfMapType
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.A_Type.Companion.typeUnionOfMapType
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.A_Type.Companion.upperInclusive
import avail.descriptor.types.A_Type.Companion.valueType
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.int32
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.singleInteger
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.descriptor.types.MapTypeDescriptor.ObjectSlots
import avail.descriptor.types.MapTypeDescriptor.ObjectSlots.KEY_TYPE
import avail.descriptor.types.MapTypeDescriptor.ObjectSlots.SIZE_RANGE
import avail.descriptor.types.MapTypeDescriptor.ObjectSlots.VALUE_TYPE
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.serialization.SerializerOperation
import org.availlang.json.JSONWriter
import java.util.IdentityHashMap

/**
 * An object instance of `MapTypeDescriptor` is a type which maps may conform
 * to. The map type has a [size&#32;range][ObjectSlots.SIZE_RANGE], and
 * [key][ObjectSlots.KEY_TYPE] and [value][ObjectSlots.VALUE_TYPE] types. For a
 * map to conform to a map type, it must be within the indicates size range and
 * have keys and values of the specified [types][TypeDescriptor].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Construct a new [MapTypeDescriptor].
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 */
class MapTypeDescriptor
private constructor(
	mutability: Mutability
) : TypeDescriptor(
	mutability,
	TypeTag.MAP_TYPE_TAG,
	TypeTag.MAP_TAG,
	ObjectSlots::class.java,
	null)
{
	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * The number of elements that a map can have while conforming to this
		 * map type.
		 */
		SIZE_RANGE,

		/**
		 * The types of keys that a map can have while conforming to this map
		 * type.
		 */
		KEY_TYPE,

		/**
		 * The types of values that a map can have while conforming to this map
		 * type.
		 */
		VALUE_TYPE
	}

	override fun o_KeyType(self: AvailObject): A_Type =
		self.slot(KEY_TYPE)

	override fun o_SizeRange(self: AvailObject): A_Type =
		self.slot(SIZE_RANGE)

	override fun o_ValueType(self: AvailObject): A_Type =
		self.slot(VALUE_TYPE)

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		if (self.slot(KEY_TYPE).equals(ANY.o)
			&& self.slot(VALUE_TYPE).equals(ANY.o)
			&& self.slot(SIZE_RANGE).equals(wholeNumbers))
		{
			builder.append("map")
			return
		}
		builder.append('{')
		self.keyType.printOnAvoidingIndent(
			builder, recursionMap, indent + 1)
		builder.append('→')
		self.valueType.printOnAvoidingIndent(
			builder, recursionMap, indent + 1)
		builder.append('|')
		val sizeRange: A_Type = self.slot(SIZE_RANGE)
		if (sizeRange.equals(wholeNumbers))
		{
			builder.append('}')
			return
		}
		sizeRange.lowerBound.printOnAvoidingIndent(
			builder, recursionMap, indent + 1)
		if (!sizeRange.lowerBound.equals(sizeRange.upperBound))
		{
			builder.append("..")
			sizeRange.upperBound.printOnAvoidingIndent(
				builder, recursionMap, indent + 1)
		}
		builder.append('}')
	}

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsMapType(self)

	override fun o_EqualsMapType(self: AvailObject, aMapType: A_Type): Boolean =
		if (self.sameAddressAs(aMapType)) true
		else self.slot(SIZE_RANGE).equals(aMapType.sizeRange)
			&& self.slot(KEY_TYPE).equals(aMapType.keyType)
			&& self.slot(VALUE_TYPE).equals(aMapType.valueType)

	// Answer a 32-bit integer that is always the same for equal objects,
	// but statistically different for different objects.
	override fun o_Hash(self: AvailObject): Int = combine4(
		self.slot(SIZE_RANGE).hash(),
		self.slot(KEY_TYPE).hash(),
		self.slot(VALUE_TYPE).hash(),
		0x4e53eb41)

	override fun o_IsMapType(self: AvailObject): Boolean = true

	// Check if object (a type) is a subtype of aType (should also be a
	// type).
	override fun o_IsSubtypeOf(self: AvailObject, aType: A_Type): Boolean =
		aType.isSupertypeOfMapType(self)

	// Map type A is a subtype of B if and only if their size ranges are
	// covariant and their key types and value types are each covariant.
	override fun o_IsSupertypeOfMapType(
		self: AvailObject,
		aMapType: AvailObject): Boolean =
			(aMapType.slot(SIZE_RANGE).isSubtypeOf(
					self.slot(SIZE_RANGE))
				&& aMapType.slot(KEY_TYPE).isSubtypeOf(
					self.slot(KEY_TYPE))
				&& aMapType.slot(VALUE_TYPE).isSubtypeOf(
					self.slot(VALUE_TYPE)))

	override fun o_IsVacuousType(self: AvailObject): Boolean =
		(!self.slot(SIZE_RANGE).lowerBound.equalsInt(0)
			&& (self.slot(KEY_TYPE).isVacuousType
				|| self.slot(VALUE_TYPE).isVacuousType))

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.MAP_TYPE

	override fun o_TrimType(self: AvailObject, typeToRemove: A_Type): A_Type
	{
		if (!self.sizeRange.isSubtypeOf(int32))
		{
			// Trim the type to only those that are physically possible.
			self.makeImmutable()
			return mapTypeForSizesKeyTypeValueType(
				self.sizeRange.typeIntersection(int32),
				self.keyType,
				self.valueType
			).trimType(typeToRemove)
		}
		if (self.isSubtypeOf(typeToRemove)) return bottom
		if (!typeToRemove.isMapType) return self
		if (typeToRemove.isEnumeration) return self
		self.makeImmutable()
		typeToRemove.makeImmutable()
		if (!typeToRemove.sizeRange.isSubtypeOf(int32))
		{
			// Trim the type to only those that are physically possible.
			return self.trimType(
				mapTypeForSizesKeyTypeValueType(
					typeToRemove.sizeRange.typeIntersection(int32),
					typeToRemove.keyType,
					typeToRemove.valueType))
		}
		val keyType = self.keyType
		val valueType = self.valueType
		val sizeRange = self.sizeRange
		val removedKeyType = typeToRemove.keyType
		val removedValueType = typeToRemove.valueType
		val removedSizeRange = typeToRemove.sizeRange
		if (keyType.isSubtypeOf(removedKeyType)
			&& valueType.isSubtypeOf(removedValueType))
		{
			// The key/value types won't be enough to keep maps around, so we
			// can use the map sizes to determine what we can actually exclude.
			return mapTypeForSizesKeyTypeValueType(
				sizeRange.trimType(removedSizeRange),
				keyType,
				valueType)
		}
		// We could do something similar if the sizes and keys were all excluded
		// but not the values, or the sizes and values but not the keys, but
		// we'd have to decide which conservative approximation would be most
		// worth returning.
		return self
	}

	// Answer the most general type that is still at least as specific as
	// these.
	override fun o_TypeIntersection(self: AvailObject, another: A_Type): A_Type =
		when
		{
			self.isSubtypeOf(another) -> self
			another.isSubtypeOf(self) -> another
			else -> another.typeIntersectionOfMapType(self)
		}

	// Answer the most general type that is still at least as specific as
	// these.
	//
	// Note that the subcomponents must be made immutable in case one of the
	// input mapTypes is mutable (and may be destroyed *recursively* by
	// post-primitive code).
	override fun o_TypeIntersectionOfMapType(
		self: AvailObject,
		aMapType: A_Type): A_Type =
			mapTypeForSizesKeyTypeValueType(
				self.slot(SIZE_RANGE).typeIntersection(
					aMapType.sizeRange
				).makeImmutable(),
				self.slot(KEY_TYPE).typeIntersection(
					aMapType.keyType
				).makeImmutable(),
				self.slot(VALUE_TYPE).typeIntersection(
					aMapType.valueType
				).makeImmutable())

	// Answer the most specific type that is still at least as general as
	// these.
	override fun o_TypeUnion(self: AvailObject, another: A_Type): A_Type =
		when
		{
			self.equals(another) -> self
			self.isSubtypeOf(another) -> another
			another.isSubtypeOf(self) -> self
			else -> another.typeUnionOfMapType(self)
		}

	// Answer the most specific type that is still at least as general as
	// these.
	//
	// Note that the subcomponents must be made immutable in case one of the
	// input mapTypes is mutable (and may be destroyed *recursively* by
	// post-primitive code).
	override fun o_TypeUnionOfMapType(
		self: AvailObject,
		aMapType: A_Type): A_Type =
			mapTypeForSizesKeyTypeValueType(
				self.slot(SIZE_RANGE).typeUnion(
					aMapType.sizeRange
				).makeImmutable(),
				self.slot(KEY_TYPE).typeUnion(
					aMapType.keyType
				).makeImmutable(),
				self.slot(VALUE_TYPE).typeUnion(
					aMapType.valueType
				).makeImmutable())

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write("map type")
		writer.write("key type")
		self.slot(KEY_TYPE).writeTo(writer)
		writer.write("value type")
		self.slot(VALUE_TYPE).writeTo(writer)
		writer.write("cardinality")
		self.slot(SIZE_RANGE).writeTo(writer)
		writer.endObject()
	}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write("map type")
		writer.write("key type")
		self.slot(KEY_TYPE).writeSummaryTo(writer)
		writer.write("value type")
		self.slot(VALUE_TYPE).writeSummaryTo(writer)
		writer.write("cardinality")
		self.slot(SIZE_RANGE).writeTo(writer)
		writer.endObject()
	}

	override fun mutable(): MapTypeDescriptor = mutable

	override fun immutable(): MapTypeDescriptor = immutable

	override fun shared(): MapTypeDescriptor = shared

	companion object
	{
		/**
		 * Construct a new map type with the specified permitted range of number
		 * of * elements, the specified types of keys, and the specified types
		 * of values. Canonicalize the values for singularities: such as the
		 * size range being zero (in which case the key type and value type are
		 * reduced to bottom).
		 *
		 *
		 * * An enumeration for the size range is weakened to a kind.
		 * * A ⊥ key type or value type implies exactly zero elements, or ⊥ as
		 *   the resulting map type if zero is not an allowed size.
		 * * At most zero elements implies ⊥ key and value types.
		 * * A non-meta enumeration for the key type bounds the maximum size of
		 *   the map (e.g., a map from booleans has at most 2 elements).
		 * * Similarly, an integral range for the key type bounds the maximum
		 *   size of the map (you can't have a 1000-element map from bytes).
		 *
		 *
		 * @param sizeRange
		 *   An [integer&#32;range&#32;type][IntegerRangeTypeDescriptor]
		 *   specifying the permitted sizes of a map of the proposed type.
		 * @param keyType
		 *   The type of all keys of maps of the proposed type.
		 * @param valueType
		 *   The type of all values of maps of the proposed type.
		 * @return
		 *   The requested map type.
		 */
		fun mapTypeForSizesKeyTypeValueType(
			sizeRange: A_Type,
			keyType: A_Type,
			valueType: A_Type): A_Type
		{
			if (sizeRange.isBottom)
			{
				return bottom
			}
			assert(sizeRange.lowerBound.isFinite)
			assert(zero.lessOrEqual(sizeRange.lowerBound))
			assert(sizeRange.upperBound.isFinite || !sizeRange.upperInclusive)
			val sizeRangeKind =
				if (sizeRange.isEnumeration) sizeRange.computeSuperkind()
				else sizeRange
			val newSizeRange: A_Type
			val newKeyType: A_Type
			val newValueType: A_Type
			if (sizeRangeKind.upperBound.equalsInt(0))
			{
				newSizeRange = sizeRangeKind
				newKeyType = bottom
				newValueType = bottom
			}
			else if (keyType.isBottom || valueType.isBottom)
			{
				newSizeRange = singleInteger(zero)
				newKeyType = bottom
				newValueType = bottom
			}
			else
			{
				val contentRestrictedSizes: A_Type =
					when
					{
						keyType.isEnumeration && !keyType.isInstanceMeta ->
						{
							// There can't ever be more entries in the map than there
							// are distinct possible keys.
							inclusive(zero, keyType.instanceCount)
						}
						keyType.isIntegerRangeType
						&& (keyType.lowerBound.isFinite
							|| keyType.upperBound.isFinite
							|| keyType.lowerBound.equals(
							keyType.upperBound)) ->
						{
							// We had already ruled out ⊥ for the keys (and also
							// for the values), and the latest test rules out
							// [-∞..∞], [-∞..∞), (-∞..∞], and (-∞..∞), allowing
							// safe subtraction.
							inclusive(
								zero,
								keyType.upperBound
									.minusCanDestroy(keyType.lowerBound, false)
									.plusCanDestroy(one, false))
						}
						else ->
						{
							// Otherwise don't narrow the size range.
							wholeNumbers
						}
					}
				newSizeRange = sizeRangeKind.typeIntersection(
					contentRestrictedSizes)
				newKeyType = keyType
				newValueType = valueType
			}
			return mutable.create {
				setSlot(SIZE_RANGE, newSizeRange)
				setSlot(KEY_TYPE, newKeyType)
				setSlot(VALUE_TYPE, newValueType)
			}
		}

		/** The mutable [MapTypeDescriptor]. */
		private val mutable = MapTypeDescriptor(Mutability.MUTABLE)

		/** The immutable [MapTypeDescriptor]. */
		private val immutable = MapTypeDescriptor(Mutability.IMMUTABLE)

		/** The shared [MapTypeDescriptor]. */
		private val shared = MapTypeDescriptor(Mutability.SHARED)

		/** The most general map type. */
		private val mostGeneralType: A_Type =
			mapTypeForSizesKeyTypeValueType(
				wholeNumbers, ANY.o, ANY.o
			).makeShared()

		/**
		 * Answer the most general [map type][MapTypeDescriptor].
		 *
		 * @return
		 *   The most general map type.
		 */
		fun mostGeneralMapType(): A_Type = mostGeneralType

		/**
		 * The metatype for all map types.
		 */
		private val meta: A_Type = instanceMeta(mostGeneralType).makeShared()

		/**
		 * Answer the metatype for all map types.
		 *
		 * @return
		 *   The statically referenced metatype.
		 */
		fun mapMeta(): A_Type = meta
	}
}
