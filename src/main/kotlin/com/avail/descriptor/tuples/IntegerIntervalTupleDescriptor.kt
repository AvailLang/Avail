/*
 * IntegerIntervalTupleDescriptor.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
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
package com.avail.descriptor.tuples

import com.avail.annotations.HideFieldInDebugger
import com.avail.descriptor.numbers.A_Number
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.AvailObjectRepresentation.Companion.newLike
import com.avail.descriptor.representation.BitField
import com.avail.descriptor.representation.IntegerSlotsEnum
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.tuples.A_Tuple.Companion.compareFromToWithIntegerIntervalTupleStartingAt
import com.avail.descriptor.tuples.A_Tuple.Companion.concatenateWith
import com.avail.descriptor.tuples.A_Tuple.Companion.copyAsMutableObjectTuple
import com.avail.descriptor.tuples.A_Tuple.Companion.copyTupleFromToCanDestroy
import com.avail.descriptor.tuples.A_Tuple.Companion.treeTupleLevel
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleAtPuttingCanDestroy
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleIntAt
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import com.avail.descriptor.tuples.IntegerIntervalTupleDescriptor.IntegerSlots.Companion.HASH_OR_ZERO
import com.avail.descriptor.tuples.IntegerIntervalTupleDescriptor.IntegerSlots.Companion.SIZE
import com.avail.descriptor.tuples.IntegerIntervalTupleDescriptor.ObjectSlots.DELTA
import com.avail.descriptor.tuples.IntegerIntervalTupleDescriptor.ObjectSlots.END
import com.avail.descriptor.tuples.IntegerIntervalTupleDescriptor.ObjectSlots.START
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromList
import com.avail.descriptor.tuples.TreeTupleDescriptor.Companion.concatenateAtLeastOneTree
import com.avail.descriptor.tuples.TreeTupleDescriptor.Companion.createTwoPartTreeTuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.IntegerRangeTypeDescriptor
import java.util.IdentityHashMap

/**
 * `IntegerIntervalTupleDescriptor` represents an ordered tuple of integers that
 * each differ from their predecessor by DELTA, an integer value.
 *
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 *
 * @constructor
 * Construct a new `IntegerIntervalTupleDescriptor`.
 *
 * @param mutability
 *   The mutability of the new descriptor.
 */
class IntegerIntervalTupleDescriptor private constructor(mutability: Mutability)
	: NumericTupleDescriptor(
		mutability, ObjectSlots::class.java, IntegerSlots::class.java)
{
	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum
	{
		/**
		 * A slot to hold the cached hash value of a tuple. If zero, then the
		 * hash value must be computed upon request. Note that in the very rare
		 * case that the hash value actually equals zero, the hash value has to
		 * be computed every time it is requested.
		 */
		@HideFieldInDebugger
		HASH_AND_MORE;

		companion object
		{
			/**
			 * The number of elements in the tuple.
			 *
			 * The API's [tuple size accessor][A_Tuple.tupleSize] currently
			 * returns a Java integer, because there wasn't much of a problem
			 * limiting manually-constructed tuples to two billion elements.
			 * This restriction will eventually be removed.
			 */
			@JvmField
			val SIZE = BitField(HASH_AND_MORE, 32, 32)

			/**
			 * A slot to hold the cached hash value of a tuple.  If zero, then
			 * the hash value must be computed upon request.  Note that in the
			 * very rare case that the hash value actually equals zero, the hash
			 * value has to be computed every time it is requested.
			 */
			@JvmField
			val HASH_OR_ZERO = BitField(HASH_AND_MORE, 0, 32)

			init
			{
				assert(TupleDescriptor.IntegerSlots.HASH_AND_MORE.ordinal
						   == HASH_AND_MORE.ordinal)
				assert(TupleDescriptor.IntegerSlots.HASH_OR_ZERO.isSamePlaceAs(
					HASH_OR_ZERO))
			}
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/** The first value in the tuple, inclusive.  */
		START,

		/**
		 * The last value in the tuple, inclusive. Within the constructor,
		 * the supplied END is normalized to the actual last value.
		 */
		END,

		/**
		 * The difference between a value and its subsequent neighbor in the
		 * tuple.
		 */
		DELTA
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		self.slot(START).printOnAvoidingIndent(
			builder, recursionMap, indent)
		builder.append(" to ")
		self.slot(END).printOnAvoidingIndent(
			builder, recursionMap, indent)
		val delta: A_Number = self.slot(DELTA)
		if (!delta.equalsInt(1))
		{
			builder.append(" by ")
			delta.printOnAvoidingIndent(builder, recursionMap, indent)
		}
	}

	override fun o_AppendCanDestroy(
		self: AvailObject,
		newElement: A_BasicObject,
		canDestroy: Boolean): A_Tuple
	{
		val originalSize = self.tupleSize()
		val endValue: A_Number = self.slot(END)
		val deltaValue: A_Number = self.slot(DELTA)
		val nextValue = endValue.plusCanDestroy(deltaValue, false)
		if (newElement.equals(nextValue))
		{
			val result =
				if (canDestroy && isMutable) self
				else newLike(mutable, self, 0, 0)
			result.setSlot(END, newElement)
			result.setSlot(SIZE, originalSize + 1)
			result.setSlot(HASH_OR_ZERO, 0)
			return result
		}
		// Transition to a tree tuple.
		val singleton = tuple(newElement)
		return self.concatenateWith(singleton, canDestroy)
	}

	// Consider a billion element tuple. Since an interval tuple requires
	// only O(1) storage, irrespective of its size, the average bits per
	// entry is 0.
	override fun o_BitsPerEntry(self: AvailObject): Int = 0

	override fun o_CompareFromToWithIntegerIntervalTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anIntegerIntervalTuple: A_Tuple,
		startIndex2: Int): Boolean
	{
		// If the objects refer to the same memory, and the indices match
		// up, the subranges are the same.
		if (self.sameAddressAs(anIntegerIntervalTuple) &&
			startIndex1 == startIndex2)
		{
			return true
		}

		// If the objects do not refer to the same memory but the tuples are
		// identical,
		if (self.equals(anIntegerIntervalTuple))
		{
			// indirect one to the other if it is not shared.
			if (!isShared)
			{
				anIntegerIntervalTuple.makeImmutable()
				self.becomeIndirectionTo(anIntegerIntervalTuple)
			}
			else if (!anIntegerIntervalTuple.descriptor().isShared)
			{
				self.makeImmutable()
				anIntegerIntervalTuple.becomeIndirectionTo(self)
			}

			// If the subranges start at the same place, they are the same.
			return startIndex1 == startIndex2
		}

		// Finally, check the subranges.
		val first = self.copyTupleFromToCanDestroy(
			startIndex1,
			endIndex1,
			false)
		val second = anIntegerIntervalTuple.copyTupleFromToCanDestroy(
			startIndex2,
			startIndex2 + endIndex1 - startIndex1,
			false)
		return first.equals(second)
	}

	override fun o_CompareFromToWithStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anotherObject: A_Tuple,
		startIndex2: Int): Boolean =
			anotherObject.compareFromToWithIntegerIntervalTupleStartingAt(
				startIndex2,
				startIndex2 + endIndex1 - startIndex1,
				self,
				startIndex1)

	override fun o_CopyTupleFromToCanDestroy(
		self: AvailObject,
		start: Int,
		end: Int,
		canDestroy: Boolean): A_Tuple
	{
		// Ensure parameters are in bounds
		val oldSize = self.slot(SIZE)
		assert(1 <= start && start <= end + 1 && end <= oldSize)

		// If the requested copy is a proper subrange, create it.
		val newSize = end - start + 1
		if (newSize != oldSize)
		{
			val delta = self.slot(DELTA).makeImmutable()
			val oldStartValue = self.slot(START)
			val newStartValue = oldStartValue.plusCanDestroy(
				fromInt(start - 1).multiplyByIntegerCanDestroy(delta, true),
				canDestroy) as AvailObject
			val newEndValue = newStartValue.plusCanDestroy(
				fromInt(newSize - 1).multiplyByIntegerCanDestroy(
					delta, true),
				false) as AvailObject
			if (isMutable && canDestroy)
			{
				// Recycle the object.
				self.setSlot(START, newStartValue)
				self.setSlot(END, newEndValue)
				self.setSlot(SIZE, newSize)
				return self
			}
			return createInterval(newStartValue, newEndValue, delta)
		}

		// Otherwise, this method is requesting a full copy of the original.
		if (isMutable && !canDestroy)
		{
			self.makeImmutable()
		}
		return self
	}

	override fun o_ConcatenateWith(
		self: AvailObject,
		otherTuple: A_Tuple,
		canDestroy: Boolean): A_Tuple
	{
		if (!canDestroy)
		{
			self.makeImmutable()
			otherTuple.makeImmutable()
		}

		// Assess the possibility that the concatenation will still be an
		// integer interval tuple.
		if (otherTuple.isIntegerIntervalTuple)
		{
			val otherDirect = otherTuple.traversed()
			val delta = self.slot(DELTA)

			// If the other's delta is the same as mine,
			if (delta.equals(otherDirect.slot(DELTA)))
			{
				// and the other's start is one delta away from my end,
				if (self.slot(END).plusCanDestroy(delta, false)
						.equals(otherDirect.slot(START)))
				{
					// then we're adjacent.
					val newSize = self.slot(SIZE) +
								  otherDirect.slot(SIZE)

					// If we can do replacement in place,
					// use me for the return value.
					if (isMutable)
					{
						self.setSlot(END, otherDirect.slot(END))
						self.setSlot(SIZE, newSize)
						self.setHashOrZero(0)
						return self
					}
					// Or the other one.
					if (otherTuple.descriptor().isMutable)
					{
						otherDirect.setSlot(START, self.slot(START))
						otherDirect.setSlot(SIZE, newSize)
						otherDirect.setHashOrZero(0)
						return otherDirect
					}

					// Otherwise, create a new interval.
					return createInterval(
						self.slot(START),
						otherDirect.slot(END),
						delta)
				}
			}
		}
		return if (otherTuple.treeTupleLevel() == 0)
		{
			createTwoPartTreeTuple(self, otherTuple, 1, 0)
		}
		else
		{
			concatenateAtLeastOneTree(self, otherTuple, true)
		}
	}

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsIntegerIntervalTuple(self)

	override fun o_EqualsIntegerIntervalTuple(
		self: AvailObject,
		anIntegerIntervalTuple: A_Tuple): Boolean
	{
		// First, check for object-structure (address) identity.
		if (self.sameAddressAs(anIntegerIntervalTuple))
		{
			return true
		}

		// If the objects do not refer to the same memory, check if the tuples
		// are identical.
		val firstTraversed = self.traversed()
		val secondTraversed = anIntegerIntervalTuple.traversed()

		// Check that the slots match.
		val firstHash = firstTraversed.slot(HASH_OR_ZERO)
		val secondHash = secondTraversed.slot(HASH_OR_ZERO)
		when
		{
			firstHash != 0 && secondHash != 0 && firstHash != secondHash ->
				return false
			// Since we have SIZE as int, it's cheaper to check it than END.
			firstTraversed.slot(SIZE) !=
				secondTraversed.slot(SIZE) -> return false
			!firstTraversed.slot(DELTA).equals(secondTraversed.slot(DELTA)) ->
				return false
			!firstTraversed.slot(START).equals(secondTraversed.slot(START)) ->
				return false

			// All the slots match. Indirect one to the other if it is not shared.
			!isShared ->
			{
				anIntegerIntervalTuple.makeImmutable()
				self.becomeIndirectionTo(anIntegerIntervalTuple)
			}
			!anIntegerIntervalTuple.descriptor().isShared ->
			{
				self.makeImmutable()
				anIntegerIntervalTuple.becomeIndirectionTo(self)
			}
		}

		return true
	}

	override fun o_IsIntegerIntervalTuple(self: AvailObject): Boolean = true

	override fun o_TupleAt(self: AvailObject, index: Int): AvailObject
	{
		// Answer the value at the given index in the tuple object.
		// START + (index-1) * DELTA
		assert(index >= 1 && index <= self.tupleSize())
		var temp: A_Number = fromInt(index - 1)
		temp = temp.timesCanDestroy(self.slot(DELTA), false)
		temp = temp.plusCanDestroy(self.slot(START), false)
		return temp as AvailObject
	}

	override fun o_TupleAtPuttingCanDestroy(
		self: AvailObject,
		index: Int,
		newValueObject: A_BasicObject,
		canDestroy: Boolean): A_Tuple
	{
		// Answer a tuple with all the elements of object except at the given
		// index we should have newValueObject. This may destroy the original
		// tuple if canDestroy is true.
		assert(index >= 1 && index <= self.tupleSize())
		if (newValueObject.isInt
			&& self.tupleIntAt(index)
			== (newValueObject as A_Number).extractInt())
		{
			// The element is to be replaced with itself.
			if (!canDestroy)
			{
				self.makeImmutable()
			}
			return self
		}
		val result = self.copyAsMutableObjectTuple().tupleAtPuttingCanDestroy(
			index, newValueObject, true)
		if (!canDestroy)
		{
			self.makeImmutable()
		}
		return result
	}

	override fun o_TupleElementsInRangeAreInstancesOf(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int,
		type: A_Type): Boolean
	{
		val start: A_Number = self.tupleAt(startIndex)
		val end: A_Number = self.tupleAt(endIndex)
		val low: A_Number
		val high: A_Number
		if (start.lessThan(end))
		{
			low = start
			high = end
		}
		else
		{
			low = end
			high = start
		}
		return (type.isSupertypeOfIntegerRangeType(
				IntegerRangeTypeDescriptor.inclusive(low, high))
			|| super.o_TupleElementsInRangeAreInstancesOf(
				self, startIndex, endIndex, type))
	}

	override fun o_TupleIntAt(self: AvailObject, index: Int): Int =
		self.tupleAt(index).extractInt()

	override fun o_TupleLongAt(self: AvailObject, index: Int): Long =
		self.tupleAt(index).extractLong()

	override fun o_TupleReverse(self: AvailObject): A_Tuple
	{
		//If tuple is small enough or is immutable, create a new Interval
		if (self.tupleSize() < maximumCopySize || !isMutable)
		{
			val newDelta =
				self.slot(DELTA).timesCanDestroy(fromInt(-1), true)
			return forceCreate(
				self.slot(END),
				self.slot(START),
				newDelta,
				o_TupleSize(self))
		}

		//The interval is mutable and large enough to warrant changing in place.
		val newStart: A_Number = self.slot(END)
		val newEnd: A_Number = self.slot(START)
		val newDelta =
			self.slot(DELTA).timesCanDestroy(fromInt(-1), true)
		self.setSlot(START, newStart)
		self.setSlot(END, newEnd)
		self.setSlot(DELTA, newDelta)
		return self
	}

	override fun o_TupleSize(self: AvailObject): Int = self.slot(SIZE)

	override fun mutable(): IntegerIntervalTupleDescriptor =
		mutable

	override fun immutable(): IntegerIntervalTupleDescriptor =
		immutable

	override fun shared(): IntegerIntervalTupleDescriptor = shared

	companion object
	{
		/**
		 * The minimum size for integer interval tuple creation. All tuples
		 * requested below this size will be created as standard tuples or the
		 * empty tuple.
		 */
		private const val maximumCopySize = 4

		/**
		 * Create a new interval according to the parameters.
		 *
		 * @param start
		 *   The first integer in the interval.
		 * @param end
		 *   The last allowable integer in the interval.
		 * @param delta
		 *   The difference between an integer and its subsequent neighbor in
		 *   the interval. Delta is nonzero.
		 * @return
		 *   The new interval.
		 */
		@JvmStatic
		fun createInterval(
			start: AvailObject,
			end: AvailObject,
			delta: AvailObject): A_Tuple
		{
			assert(!delta.equalsInt(0))
			val difference = end.minusCanDestroy(start, false)
			val zero: A_Number = zero()

			// If there is only one member in the range, return that integer in
			// its own tuple.
			if (difference.equalsInt(0))
			{
				return tuple(start)
			}

			// If the progression is in a different direction than the delta, there
			// are no members of this interval, so return the empty tuple.
			if (difference.greaterThan(zero) != delta.greaterThan(zero))
			{
				return emptyTuple
			}

			// If there are fewer than maximumCopySize members in this interval,
			// create a normal tuple with them in it instead of an interval tuple.
			val size = 1 +
					   difference.divideCanDestroy(delta, false).extractInt()
			if (size < maximumCopySize)
			{
				val members = mutableListOf<A_Number>()
				var newMember: A_Number = start
				for (i in 0 until size)
				{
					members.add(newMember)
					newMember = newMember.addToIntegerCanDestroy(delta, false)
				}
				return tupleFromList(members)
			}

			// If the slot contents are small enough, create a
			// SmallIntegerIntervalTuple.
			if (SmallIntegerIntervalTupleDescriptor.isSmallIntervalCandidate(
					start, end, delta))
			{
				return SmallIntegerIntervalTupleDescriptor.createSmallInterval(
					start.extractInt(),
					end.extractInt(),
					delta.extractInt().toLong())
			}

			// No other efficiency shortcuts. Normalize end, and create a range.
			val adjustedEnd = start.plusCanDestroy(
				delta.timesCanDestroy(
					fromInt(size - 1), false), false)
			return forceCreate(start, adjustedEnd, delta, size)
		}

		/**
		 * Create a new IntegerIntervalTuple using the supplied arguments,
		 * regardless of the suitability of other representations.
		 *
		 * @param start
		 *   The first integer in the interval.
		 * @param normalizedEnd
		 *   The last integer in the interval.
		 * @param delta
		 *   The difference between an integer and its subsequent neighbor in
		 *   the interval. Delta is nonzero.
		 * @param size
		 *   The size of the interval, in number of elements.
		 * @return
		 *   The new interval.
		 */
		private fun forceCreate(
			start: A_Number,
			normalizedEnd: A_Number,
			delta: A_Number,
			size: Int): A_Tuple
		{
			return mutable.create {
				setSlot(HASH_OR_ZERO, 0)
				setSlot(START, start.makeImmutable())
				setSlot(END, normalizedEnd.makeImmutable())
				setSlot(DELTA, delta.makeImmutable())
				setSlot(SIZE, size)
			}
		}

		/** The mutable [IntegerIntervalTupleDescriptor].  */
		val mutable = IntegerIntervalTupleDescriptor(Mutability.MUTABLE)

		/** The immutable [IntegerIntervalTupleDescriptor].  */
		private val immutable =
			IntegerIntervalTupleDescriptor(Mutability.IMMUTABLE)

		/** The shared [IntegerIntervalTupleDescriptor].  */
		private val shared = IntegerIntervalTupleDescriptor(Mutability.SHARED)
	}
}
