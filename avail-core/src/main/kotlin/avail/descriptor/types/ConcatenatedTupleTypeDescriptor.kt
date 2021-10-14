/*
 * ConcatenatedTupleTypeDescriptor.kt
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

import avail.descriptor.numbers.A_Number
import avail.descriptor.numbers.A_Number.Companion.equalsInt
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.greaterThan
import avail.descriptor.numbers.A_Number.Companion.lessThan
import avail.descriptor.numbers.A_Number.Companion.minusCanDestroy
import avail.descriptor.numbers.A_Number.Companion.noFailPlusCanDestroy
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.numbers.IntegerDescriptor.Companion.one
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.BitField
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleAtPuttingCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.generateObjectTupleFrom
import avail.descriptor.types.A_Type.Companion.defaultType
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.isSupertypeOfTupleType
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.tupleOfTypesFromTo
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.A_Type.Companion.typeIntersectionOfTupleType
import avail.descriptor.types.A_Type.Companion.typeTuple
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.A_Type.Companion.typeUnionOfTupleType
import avail.descriptor.types.A_Type.Companion.unionOfTypesAtThrough
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.ConcatenatedTupleTypeDescriptor.IntegerSlots.Companion.TUPLE_TYPE_COMPLEXITY
import avail.descriptor.types.ConcatenatedTupleTypeDescriptor.ObjectSlots.FIRST_TUPLE_TYPE
import avail.descriptor.types.ConcatenatedTupleTypeDescriptor.ObjectSlots.SECOND_TUPLE_TYPE
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForSizesTypesDefaultType
import avail.serialization.SerializerOperation
import org.availlang.json.JSONWriter
import kotlin.math.max
import kotlin.math.min

/**
 * An object instance of `ConcatenatedTupleTypeDescriptor` is an optimization
 * that postpones (or ideally avoids) the creation of a
 * [tuple&#32;type][TupleTypeDescriptor] when computing the static type of
 * the concatenation of two tuples.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Construct a new `ConcatenatedTupleTypeDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 */
class ConcatenatedTupleTypeDescriptor private constructor(
	mutability: Mutability) : TypeDescriptor(
		mutability,
		TypeTag.TUPLE_TYPE_TAG,
		TypeTag.TUPLE_TAG,
		ObjectSlots::class.java,
		IntegerSlots::class.java)
{
	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum
	{
		/**
		 * [BitField]s holding the tuple type complexity and other fields if
		 * needed.
		 */
		TUPLE_TYPE_COMPLEXITY_AND_MORE;

		companion object
		{
			/**
			 * The number of layers of virtualized concatenation in this tuple
			 * type. This may become a conservatively large estimate due to my
			 * subobjects being coalesced with more direct representations.
			 */
			val TUPLE_TYPE_COMPLEXITY =
				BitField(TUPLE_TYPE_COMPLEXITY_AND_MORE, 0, 32)
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * The type of the left tuple being concatenated.
		 */
		FIRST_TUPLE_TYPE,

		/**
		 * The type of the right tuple being concatenated.
		 */
		SECOND_TUPLE_TYPE
	}

	/**
	 * Answer the type that my last element must have, if any.
	 */
	override fun o_DefaultType(self: AvailObject): A_Type
	{
		val a: A_Type = self.slot(FIRST_TUPLE_TYPE)
		val b: A_Type = self.slot(SECOND_TUPLE_TYPE)
		return defaultTypeOfConcatenation(a, b)
	}

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsTupleType(self)

	override fun o_EqualsTupleType(
		self: AvailObject,
		aTupleType: A_Type): Boolean
	{
		// Tuple types are equal iff their size range, leading type tuple, and
		// default type match.
		when
		{
			self.sameAddressAs(aTupleType) -> return true
			!self.sizeRange.equals(aTupleType.sizeRange) -> return false
			!self.defaultType.equals(aTupleType.defaultType) -> return false
			!self.typeTuple.equals(aTupleType.typeTuple) -> return false
			self.representationCostOfTupleType()
				< aTupleType.representationCostOfTupleType() ->
			{
				if (!aTupleType.descriptor().isShared)
				{
					aTupleType.becomeIndirectionTo(self.makeImmutable())
				}
			}
			else ->
			{
				if (!isShared)
				{
					self.becomeIndirectionTo(aTupleType.makeImmutable())
				}
			}
		}
		return true
	}

	/**
	 *
	 * Answer a 32-bit long that is always the same for equal objects, but
	 * statistically different for different objects.  This requires an object
	 * creation, so don't call it from the garbage collector.
	 */
	override fun o_Hash(self: AvailObject): Int
	{
		becomeRealTupleType(self)
		return self.hash()
	}

	/**
	 * {@inheritDoc}
	 *
	 * A concatenated tuple type isn't a very fast representation to use even
	 * though it's easy to construct.
	 */
	override fun o_IsBetterRepresentationThan(
		self: AvailObject,
		anotherObject: A_BasicObject): Boolean =
			(self.representationCostOfTupleType()
				< anotherObject.representationCostOfTupleType())

	/**
	 * {@inheritDoc}
	 *
	 * I'm not a very time-efficient representation of a tuple type.
	 */
	override fun o_RepresentationCostOfTupleType(self: AvailObject): Int =
		self.slot(TUPLE_TYPE_COMPLEXITY)

	/**
	 * Answer what range of tuple sizes my instances could be. Note that this
	 * can not be asked during a garbage collection because it allocates space
	 * for its answer.
	 */
	override fun o_SizeRange(self: AvailObject): A_Type
	{
		val sizeRange1 = self.slot(FIRST_TUPLE_TYPE).sizeRange
		val sizeRange2 = self.slot(SECOND_TUPLE_TYPE).sizeRange
		return sizeRangeOfConcatenation(sizeRange1, sizeRange2)
	}

	/**
	 * Check if object is a subtype of aType.  They should both be types.
	 */
	override fun o_IsSubtypeOf(self: AvailObject, aType: A_Type): Boolean =
		aType.isSupertypeOfTupleType(self)

	/**
	 * Tuple type A is a supertype of tuple type B iff all the *possible
	 * instances* of B would also be instances of A.  Types
	 * indistinguishable under these conditions are considered the same type.
	 */
	override fun o_IsSupertypeOfTupleType(
		self: AvailObject,
		aTupleType: A_Type): Boolean
	{
		when
		{
			self.equals(aTupleType) -> return true
			!aTupleType.sizeRange.isSubtypeOf(self.sizeRange) ->
				return false
			!aTupleType.defaultType.isSubtypeOf(self.defaultType) ->
				return false
			else ->
			{
				val subTuple = aTupleType.typeTuple
				val superTuple = self.typeTuple
				val limit = max(subTuple.tupleSize, superTuple.tupleSize)
				for (i in 1 .. limit)
				{
					val subType =
						if (i <= subTuple.tupleSize) subTuple.tupleAt(i)
						else aTupleType.defaultType
					val superType =
						if (i <= superTuple.tupleSize) superTuple.tupleAt(i)
						else self.defaultType
					if (!subType.isSubtypeOf(superType))
					{
						return false
					}
				}
				return true
			}
		}
	}

	override fun o_IsTupleType(self: AvailObject): Boolean = true

	override fun o_IsVacuousType(self: AvailObject): Boolean =
		(self.slot(FIRST_TUPLE_TYPE).isVacuousType
			|| self.slot(SECOND_TUPLE_TYPE).isVacuousType)

	override fun o_MakeShared(self: AvailObject): AvailObject
	{
		// Before an object using this descriptor can be shared, it must first
		// become (an indirection to) a proper tuple type.
		assert(!isShared)
		becomeRealTupleType(self)
		self.makeShared()
		return self.traversed()
	}

	override fun o_SerializerOperation(
		self: AvailObject): SerializerOperation
	{
		becomeRealTupleType(self)
		return self.serializerOperation()
	}

	override fun o_TupleOfTypesFromTo(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int): A_Tuple
	{
		becomeRealTupleType(self)
		assert(self.descriptor() !== this)
		return self.tupleOfTypesFromTo(startIndex, endIndex)
	}

	/**
	 * Answer what type the given index would have in an object instance of me.
	 * Answer bottom if the index is definitely out of bounds.
	 */
	override fun o_TypeAtIndex(self: AvailObject, index: Int): A_Type
	{
		val firstTupleType = self.slot(FIRST_TUPLE_TYPE)
		val secondTupleType = self.slot(SECOND_TUPLE_TYPE)
		return elementOfConcatenation(firstTupleType, secondTupleType, index)
	}

	override fun o_TypeIntersection(
		self: AvailObject,
		another: A_Type): A_Type =
			when
			{
				self.isSubtypeOf(another) ->
				{
					self
				}
				else ->
				{
					if (another.isSubtypeOf(self)) another
					else another.typeIntersectionOfTupleType(self)
				}
			}

	override fun o_TypeIntersectionOfTupleType(
		self: AvailObject,
		aTupleType: A_Type): A_Type
	{
		val newSizesObject =
			self.sizeRange.typeIntersection(aTupleType.sizeRange)
		val lead1 = self.typeTuple
		val lead2 = aTupleType.typeTuple
		var newLeading: A_Tuple
		newLeading = if (lead1.tupleSize > lead2.tupleSize)
		{
			lead1
		}
		else
		{
			lead2
		}
		newLeading.makeImmutable()
		//  Ensure first write attempt will force copying.
		val newLeadingSize = newLeading.tupleSize
		for (i in 1 .. newLeadingSize)
		{
			val intersectionObject = self.typeAtIndex(i).typeIntersection(
				aTupleType.typeAtIndex(i))
			if (intersectionObject.isBottom)
			{
				return bottom
			}
			newLeading = newLeading.tupleAtPuttingCanDestroy(
				i, intersectionObject, true)
		}
		// Make sure entries in newLeading are immutable, as typeIntersection
		// can answer one of its arguments.
		newLeading.makeSubobjectsImmutable()
		val newDefault =
			self.typeAtIndex(newLeadingSize + 1).typeIntersection(
				aTupleType.typeAtIndex(newLeadingSize + 1))
		newDefault.makeImmutable()
		return tupleTypeForSizesTypesDefaultType(
			newSizesObject, newLeading, newDefault)
	}

	/**
	 * Since this is really tricky, just compute the TupleTypeDescriptor that
	 * this is shorthand for.  Answer that tupleType's typeTuple.  This is the
	 * leading types of the tupleType, up to but not including where they all
	 * have the same type.  Don't run this from within a garbage collection, as
	 * it allocates objects.
	 */
	override fun o_TypeTuple(self: AvailObject): A_Tuple
	{
		becomeRealTupleType(self)
		return self.typeTuple
	}

	override fun o_TypeUnion(
		self: AvailObject,
		another: A_Type): A_Type =
			when
			{
				self.isSubtypeOf(another) -> another
				else ->
				{
					if (another.isSubtypeOf(self)) self
					else another.typeUnionOfTupleType(self)
				}
			}

	override fun o_TypeUnionOfTupleType(
		self: AvailObject,
		aTupleType: A_Type): A_Type
	{
		val newSizesObject = self.sizeRange.typeUnion(
			aTupleType.sizeRange)
		val lead1 = self.typeTuple
		val lead2 = aTupleType.typeTuple
		var newLeading: A_Tuple
		newLeading =
			if (lead1.tupleSize > lead2.tupleSize) lead1
			else lead2
		newLeading.makeImmutable()
		// Ensure first write attempt will force copying.
		val newLeadingSize = newLeading.tupleSize
		for (i in 1 .. newLeadingSize)
		{
			val unionObject = self.typeAtIndex(i).typeUnion(
				aTupleType.typeAtIndex(i))
			newLeading = newLeading.tupleAtPuttingCanDestroy(
				i,
				unionObject,
				true)
		}
		// Make sure entries in newLeading are immutable, as typeUnion can
		// answer one of its arguments.
		newLeading.makeSubobjectsImmutable()
		val newDefault = self.typeAtIndex(newLeadingSize + 1).typeUnion(
			aTupleType.typeAtIndex(newLeadingSize + 1))
		newDefault.makeImmutable()
		return tupleTypeForSizesTypesDefaultType(
			newSizesObject, newLeading, newDefault)
	}

	override fun o_UnionOfTypesAtThrough(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int): A_Type
	{
		// Answer the union of the types that object's instances could have in
		// the given range of indices. Out-of-range indices are treated as
		// bottom, which don't affect the union (unless all indices are out
		// of range).
		assert(startIndex <= endIndex)
		if (startIndex == endIndex)
		{
			return self.typeAtIndex(startIndex)
		}
		if (endIndex <= 0)
		{
			return bottom
		}
		val firstTupleType: A_Type = self.slot(FIRST_TUPLE_TYPE)
		val secondTupleType: A_Type = self.slot(SECOND_TUPLE_TYPE)
		val firstUpper = firstTupleType.sizeRange.upperBound
		val secondUpper = secondTupleType.sizeRange.upperBound
		val totalUpper = firstUpper.noFailPlusCanDestroy(secondUpper, false)
		val startIndexObject: A_Number = fromInt(startIndex)
		if (totalUpper.isFinite)
		{
			if (startIndexObject.greaterThan(totalUpper))
			{
				return bottom
			}
		}
		var typeUnion =
			firstTupleType.unionOfTypesAtThrough(startIndex, endIndex)
		val startInSecondObject =
			startIndexObject.minusCanDestroy(firstUpper, false)
		val startInSecond =
			if (startInSecondObject.lessThan(one)) 1
			else startInSecondObject.extractInt
		val endInSecondObject = fromInt(endIndex).minusCanDestroy(
			firstTupleType.sizeRange.lowerBound,
			false)
		val endInSecond =
			when
			{
				endInSecondObject.lessThan(one) -> 1
				endInSecondObject.isInt -> endInSecondObject.extractInt
				else -> Int.MAX_VALUE
			}
		typeUnion = typeUnion.typeUnion(
			secondTupleType.unionOfTypesAtThrough(startInSecond, endInSecond))
		return typeUnion
	}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter)
	{
		becomeRealTupleType(self)
		writer.startObject()
		writer.write("kind")
		writer.write("tuple type")
		writer.write("leading types")
		self.typeTuple.writeTo(writer)
		writer.write("default type")
		self.defaultType.writeTo(writer)
		writer.write("cardinality")
		self.sizeRange.writeTo(writer)
		writer.endObject()
	}

	/**
	 * Expand me into an actual TupleTypeDescriptor, converting my storage into
	 * an indirection object to the actual tupleType.
	 *
	 * @param self
	 *   The object instance of `ConcatenatedTupleTypeDescriptor` to transform.
	 */
	private fun becomeRealTupleType(self: AvailObject)
	{
		// There isn't even a shared descriptor -- we reify the tuple type upon
		// sharing.
		assert(!isShared)
		val newObject = reallyConcatenate(
			self.slot(FIRST_TUPLE_TYPE),
			self.slot(SECOND_TUPLE_TYPE))
		self.becomeIndirectionTo(newObject)
	}

	override fun mutable(): ConcatenatedTupleTypeDescriptor = mutable

	override fun immutable(): ConcatenatedTupleTypeDescriptor = immutable

	override fun shared(): ConcatenatedTupleTypeDescriptor
	{
		unsupportedOperation()
	}

	companion object
	{
		/**
		 * The maximum depth of
		 * [concatenated&#32;tuple&#32;types][ConcatenatedTupleTypeDescriptor]
		 * that may exist before converting to a fully reified
		 * [tuple&#32;type][TupleTypeDescriptor].
		 */
		private const val maximumConcatenationDepth = 10

		/**
		 * Answer what type the given index would have in a tuple whose type
		 * complies with the concatenation of the two tuple types. Answer bottom
		 * if the index is definitely out of bounds.
		 *
		 * @param firstTupleType
		 *   The first [tuple&#32;type][TupleTypeDescriptor].
		 * @param secondTupleType
		 *   The second tuple type.
		 * @param index
		 *   The element index.
		 * @return
		 *   The type of the specified index within the concatenated tuple type.
		 */
		private fun elementOfConcatenation(
			firstTupleType: A_Type,
			secondTupleType: A_Type,
			index: Int): A_Type
		{
			if (index <= 0)
			{
				return bottom
			}
			val firstSizeRange = firstTupleType.sizeRange
			val firstUpper = firstSizeRange.upperBound
			val secondUpper = secondTupleType.sizeRange.upperBound
			val totalUpper = firstUpper.noFailPlusCanDestroy(secondUpper, false)
			if (totalUpper.isFinite)
			{
				val indexObject: A_Number = fromInt(index)
				if (indexObject.greaterThan(totalUpper))
				{
					return bottom
				}
			}
			val firstLower = firstSizeRange.lowerBound
			if (index <= firstLower.extractInt)
			{
				return firstTupleType.typeAtIndex(index)
			}
			// Besides possibly being at a fixed offset within the firstTupleType,
			// the index might represent a range of possible indices of the
			// secondTupleType, depending on the spread between the first tuple
			// type's lower and upper bounds. Compute the union of these types.
			val typeFromFirstTuple = firstTupleType.typeAtIndex(index)
			val startIndex: Int
			startIndex = when
			{
				firstUpper.isFinite -> max(index - firstUpper.extractInt, 1)
				else -> 1
			}
			val endIndex = index - firstLower.extractInt
			assert(endIndex >= startIndex)
			return typeFromFirstTuple.typeUnion(
				secondTupleType.unionOfTypesAtThrough(startIndex, endIndex))
		}

		/**
		 * Answer the [size&#32;range][A_Type.sizeRange] of the
		 * concatenation of tuples having the given size ranges.
		 *
		 * @param sizeRange1
		 *   The first tuple's sizeRange.
		 * @param sizeRange2
		 *   The second tuple's sizeRange.
		 * @return
		 *   The range of sizes of the concatenated tuple.
		 */
		private fun sizeRangeOfConcatenation(
			sizeRange1: A_Type,
			sizeRange2: A_Type): A_Type
		{
			val lower = sizeRange1.lowerBound.noFailPlusCanDestroy(
				sizeRange2.lowerBound, false)
			val upper = sizeRange1.upperBound.noFailPlusCanDestroy(
				sizeRange2.upperBound, false)
			return IntegerRangeTypeDescriptor.integerRangeType(
				lower, true, upper, upper.isFinite)
		}

		/**
		 * Given two tuple types, the second of which must not be always empty,
		 * determine what a complying tuple's last element's type must be.
		 *
		 * @param tupleType1
		 *   The first tuple type.
		 * @param tupleType2
		 *   The second tuple type.
		 * @return
		 *   The type of the last element of the concatenation of the tuple
		 *   types.
		 */
		private fun defaultTypeOfConcatenation(
			tupleType1: A_Type,
			tupleType2: A_Type): A_Type
		{
			val bRange = tupleType2.sizeRange
			assert(!bRange.upperBound.equalsInt(0))
			if (tupleType1.sizeRange.upperBound.isFinite)
			{
				return tupleType2.defaultType
			}
			val highIndexInB: Int
			highIndexInB = when
			{
				bRange.upperBound.isFinite -> bRange.upperBound.extractInt
				else -> tupleType2.typeTuple.tupleSize + 1
			}
			return tupleType1.defaultType.typeUnion(
				tupleType2.unionOfTypesAtThrough(1, highIndexInB))
		}

		/**
		 * Produce a fully reified concatenation from the given tuple types
		 * (i.e., not a placeholder using [ConcatenatedTupleTypeDescriptor]).
		 *
		 * @param part1
		 *   The left tuple type.
		 * @param part2
		 *   The right tuple type.
		 * @return
		 *   The concatenated tuple type.
		 */
		private fun reallyConcatenate(
			part1: A_Type,
			part2: A_Type): A_Type
		{
			val sizes1 = part1.sizeRange
			val upper1 = sizes1.upperBound
			val limit1 = if (upper1.isInt) upper1.extractInt
			else max(
				part1.typeTuple.tupleSize + 1,
				sizes1.lowerBound.extractInt)
			val sizes2 = part2.sizeRange
			val upper2 = sizes2.upperBound
			val limit2 =
				if (upper2.isInt) upper2.extractInt
				else part2.typeTuple.tupleSize + 1
			val total = limit1 + limit2
			val section1 = min(sizes1.lowerBound.extractInt, limit1)
			val typeTuple: A_Tuple = generateObjectTupleFrom(total) {
				if (it <= section1) part1.typeAtIndex(it)
				else elementOfConcatenation(part1, part2, it)
			}
			return tupleTypeForSizesTypesDefaultType(
				sizeRangeOfConcatenation(sizes1, sizes2),
				typeTuple,
				defaultTypeOfConcatenation(part1, part2))
		}

		/**
		 * Construct a lazy concatenated tuple type object to represent the type
		 * that is the concatenation of the two tuple types.  Make the objects
		 * be immutable, because the new type represents the concatenation of
		 * the objects *at the time it was built*.
		 *
		 * @param firstTupleType
		 *   The first tuple type to concatenate.
		 * @param secondTupleType
		 *   The second tuple type to concatenate.
		 * @return
		 *   A simple representation of the tuple type whose instances are all
		 *   the concatenations of instances of the two given tuple types.
		 */
		fun concatenatingAnd(
			firstTupleType: A_Type,
			secondTupleType: A_Type): A_Type
		{
			assert(firstTupleType.isTupleType && !firstTupleType.isBottom)
			assert(secondTupleType.isTupleType && !secondTupleType.isBottom)
			if (secondTupleType.sizeRange.upperBound.equalsInt(0))
			{
				return firstTupleType.makeImmutable()
			}
			if (firstTupleType.sizeRange.upperBound.equalsInt(0))
			{
				return secondTupleType.makeImmutable()
			}
			val maxCost = max(
				firstTupleType.representationCostOfTupleType(),
				secondTupleType.representationCostOfTupleType())
			if (maxCost > maximumConcatenationDepth)
			{
				return reallyConcatenate(
					firstTupleType.makeImmutable(),
					secondTupleType.makeImmutable())
			}
			return mutable.create {
				setSlot(TUPLE_TYPE_COMPLEXITY, maxCost + 1)
				setSlot(FIRST_TUPLE_TYPE, firstTupleType.makeImmutable())
				setSlot(SECOND_TUPLE_TYPE, secondTupleType.makeImmutable())
			}
		}

		/** The mutable [ConcatenatedTupleTypeDescriptor]. */
		private val mutable =
			ConcatenatedTupleTypeDescriptor(Mutability.MUTABLE)

		/** The immutable [ConcatenatedTupleTypeDescriptor]. */
		private val immutable =
			ConcatenatedTupleTypeDescriptor(Mutability.IMMUTABLE)
	}
}
