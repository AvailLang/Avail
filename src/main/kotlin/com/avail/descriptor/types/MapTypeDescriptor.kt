/*
 * MapTypeDescriptor.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

import com.avail.descriptor.numbers.IntegerDescriptor.Companion.one
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import com.avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.singleInteger
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import com.avail.descriptor.types.MapTypeDescriptor.ObjectSlots
import com.avail.serialization.SerializerOperation
import com.avail.utility.json.JSONWriter
import java.util.*

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
class MapTypeDescriptor private constructor(mutability: Mutability)
	: TypeDescriptor(
		mutability, TypeTag.MAP_TYPE_TAG, ObjectSlots::class.java, null)
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
		self.slot(ObjectSlots.KEY_TYPE)

	override fun o_SizeRange(self: AvailObject): A_Type =
		self.slot(ObjectSlots.SIZE_RANGE)

	override fun o_ValueType(self: AvailObject): A_Type =
		self.slot(ObjectSlots.VALUE_TYPE)

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		if (self.slot(ObjectSlots.KEY_TYPE).equals(Types.ANY.o())
		    && self.slot(ObjectSlots.VALUE_TYPE).equals(Types.ANY.o())
		    && self.slot(ObjectSlots.SIZE_RANGE).equals(wholeNumbers()))
		{
			builder.append("map")
			return
		}
		builder.append('{')
		self.keyType().printOnAvoidingIndent(
			builder, recursionMap, indent + 1)
		builder.append('→')
		self.valueType().printOnAvoidingIndent(
			builder, recursionMap, indent + 1)
		builder.append('|')
		val sizeRange: A_Type = self.slot(ObjectSlots.SIZE_RANGE)
		if (sizeRange.equals(wholeNumbers()))
		{
			builder.append('}')
			return
		}
		sizeRange.lowerBound().printOnAvoidingIndent(
			builder, recursionMap, indent + 1)
		if (!sizeRange.lowerBound().equals(sizeRange.upperBound()))
		{
			builder.append("..")
			sizeRange.upperBound().printOnAvoidingIndent(
				builder, recursionMap, indent + 1)
		}
		builder.append('}')
	}

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsMapType(self)

	override fun o_EqualsMapType(self: AvailObject, aMapType: A_Type): Boolean =
		if (self.sameAddressAs(aMapType)) true
		else self.slot(ObjectSlots.SIZE_RANGE).equals(aMapType.sizeRange())
		     && self.slot(ObjectSlots.KEY_TYPE).equals(aMapType.keyType())
		     && self.slot(ObjectSlots.VALUE_TYPE).equals(aMapType.valueType())

	// Answer a 32-bit integer that is always the same for equal objects,
	// but statistically different for different objects.
	override fun o_Hash(self: AvailObject): Int =
		computeHashForSizeRangeHashKeyTypeHashValueTypeHash(
			self.slot(ObjectSlots.SIZE_RANGE).hash(),
			self.slot(ObjectSlots.KEY_TYPE).hash(),
			self.slot(ObjectSlots.VALUE_TYPE).hash())

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
			(aMapType.slot(ObjectSlots.SIZE_RANGE).isSubtypeOf(
					self.slot(ObjectSlots.SIZE_RANGE))
		        && aMapType.slot(ObjectSlots.KEY_TYPE).isSubtypeOf(
					self.slot(ObjectSlots.KEY_TYPE))
		        && aMapType.slot(ObjectSlots.VALUE_TYPE).isSubtypeOf(
					self.slot(ObjectSlots.VALUE_TYPE)))

	override fun o_IsVacuousType(self: AvailObject): Boolean =
		(!self.slot(ObjectSlots.SIZE_RANGE).lowerBound().equalsInt(0)
			&& (self.slot(ObjectSlots.KEY_TYPE).isVacuousType
		        || self.slot(ObjectSlots.VALUE_TYPE).isVacuousType))

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
				self.slot(ObjectSlots.SIZE_RANGE).typeIntersection(
					aMapType.sizeRange()).makeImmutable(),
				self.slot(ObjectSlots.KEY_TYPE).typeIntersection(
					aMapType.keyType()).makeImmutable(),
				self.slot(ObjectSlots.VALUE_TYPE).typeIntersection(
					aMapType.valueType()).makeImmutable())

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
				self.slot(ObjectSlots.SIZE_RANGE).typeUnion(
					aMapType.sizeRange()).makeImmutable(),
				self.slot(ObjectSlots.KEY_TYPE).typeUnion(
					aMapType.keyType()).makeImmutable(),
				self.slot(ObjectSlots.VALUE_TYPE).typeUnion(
					aMapType.valueType()).makeImmutable())

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.MAP_TYPE

	override fun o_MakeImmutable(self: AvailObject): AvailObject =
		if (isMutable)
		{
			// Make the object shared, since there isn't an immutable
			// descriptor.
			self.makeShared()
		}
		else self

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write("map type")
		writer.write("key type")
		self.slot(ObjectSlots.KEY_TYPE).writeTo(writer)
		writer.write("value type")
		self.slot(ObjectSlots.VALUE_TYPE).writeTo(writer)
		writer.write("cardinality")
		self.slot(ObjectSlots.SIZE_RANGE).writeTo(writer)
		writer.endObject()
	}

	override fun o_WriteSummaryTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write("map type")
		writer.write("key type")
		self.slot(ObjectSlots.KEY_TYPE).writeSummaryTo(writer)
		writer.write("value type")
		self.slot(ObjectSlots.VALUE_TYPE).writeSummaryTo(writer)
		writer.write("cardinality")
		self.slot(ObjectSlots.SIZE_RANGE).writeTo(writer)
		writer.endObject()
	}

	override fun mutable(): MapTypeDescriptor =  mutable

	// There is no immutable descriptor.
	override fun immutable(): MapTypeDescriptor = shared

	override fun shared(): MapTypeDescriptor = shared

	companion object
	{
		/**
		 * Compute what the map type's hash would be, given the hashes of its
		 * constituent parts.
		 *
		 * @param sizesHash
		 *   The hash of the
		 *   [integer&#32;range&#32;type][IntegerRangeTypeDescriptor] that
		 *   constrains the map size.
		 * @param keyTypeHash
		 *   The hash of the key type.
		 * @param valueTypeHash
		 *   The hash of the value type.
		 * @return
		 *  The hash of the resulting map type.
		 */
		private fun computeHashForSizeRangeHashKeyTypeHashValueTypeHash(
			sizesHash: Int,
			keyTypeHash: Int,
			valueTypeHash: Int): Int =
				sizesHash * 3 + keyTypeHash * 5 + valueTypeHash * 13

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
		@JvmStatic
		fun mapTypeForSizesKeyTypeValueType(
			sizeRange: A_Type,
			keyType: A_Type,
			valueType: A_Type): A_Type
		{
			if (sizeRange.isBottom)
			{
				return bottom()
			}
			assert(sizeRange.lowerBound().isFinite)
			assert(zero().lessOrEqual(sizeRange.lowerBound()))
			assert(sizeRange.upperBound().isFinite || !sizeRange.upperInclusive())
			val sizeRangeKind =
				if (sizeRange.isEnumeration) sizeRange.computeSuperkind()
				else sizeRange
			val newSizeRange: A_Type
			val newKeyType: A_Type
			val newValueType: A_Type
			if (sizeRangeKind.upperBound().equalsInt(0))
			{
				newSizeRange = sizeRangeKind
				newKeyType = bottom()
				newValueType = bottom()
			}
			else if (keyType.isBottom || valueType.isBottom)
			{
				newSizeRange = singleInteger(zero())
				newKeyType = bottom()
				newValueType = bottom()
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
							inclusive(zero(), keyType.instanceCount())
						}
						keyType.isIntegerRangeType
						&& (keyType.lowerBound().isFinite
						    || keyType.upperBound().isFinite
						    || keyType.lowerBound().equals(
							keyType.upperBound())) ->
						{
							// We had already ruled out ⊥ for the keys (and also
							// for the values), and the latest test rules out
							// [-∞..∞], [-∞..∞), (-∞..∞], and (-∞..∞), allowing
							// safe subtraction.
							inclusive(
								zero(),
								keyType.upperBound()
									.minusCanDestroy(keyType.lowerBound(), false)
									.plusCanDestroy(one(), false))
						}
						else ->
						{
							// Otherwise don't narrow the size range.
							wholeNumbers()
						}
					}
				newSizeRange = sizeRangeKind.typeIntersection(
					contentRestrictedSizes)
				newKeyType = keyType
				newValueType = valueType
			}
			val result = mutable.create()
			result.setSlot(ObjectSlots.SIZE_RANGE, newSizeRange)
			result.setSlot(ObjectSlots.KEY_TYPE, newKeyType)
			result.setSlot(ObjectSlots.VALUE_TYPE, newValueType)
			return result
		}

		/** The mutable [MapTypeDescriptor].  */
		private val mutable = MapTypeDescriptor(Mutability.MUTABLE)

		/** The shared [MapTypeDescriptor].  */
		private val shared = MapTypeDescriptor(Mutability.SHARED)

		/** The most general map type.  */
		private val mostGeneralType: A_Type =
			mapTypeForSizesKeyTypeValueType(
				wholeNumbers(), Types.ANY.o(), Types.ANY.o()).makeShared()

		/**
		 * Answer the most general [map type][MapTypeDescriptor].
		 *
		 * @return
		 *   The most general map type.
		 */
		@JvmStatic
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
		@JvmStatic
		fun mapMeta(): A_Type =  meta
	}
}