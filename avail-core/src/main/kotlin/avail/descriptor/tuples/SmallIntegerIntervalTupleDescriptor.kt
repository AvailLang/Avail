/*
 * SmallIntegerIntervalTupleDescriptor.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
package avail.descriptor.tuples

import avail.annotations.HideFieldInDebugger
import avail.descriptor.numbers.A_Number
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.extractLong
import avail.descriptor.numbers.A_Number.Companion.isInt
import avail.descriptor.numbers.A_Number.Companion.lessThan
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.BitField
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.tuples.A_Tuple.Companion.appendCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.compareFromToWithSmallIntegerIntervalTupleStartingAt
import avail.descriptor.tuples.A_Tuple.Companion.concatenateWith
import avail.descriptor.tuples.A_Tuple.Companion.copyTupleFromToCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.treeTupleLevel
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleIntAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ByteTupleDescriptor.Companion.generateByteTupleFrom
import avail.descriptor.tuples.IntTupleDescriptor.Companion.generateIntTupleFrom
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.generateObjectTupleFrom
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.SmallIntegerIntervalTupleDescriptor.IntegerSlots.Companion.END
import avail.descriptor.tuples.SmallIntegerIntervalTupleDescriptor.IntegerSlots.Companion.HASH_OR_ZERO
import avail.descriptor.tuples.SmallIntegerIntervalTupleDescriptor.IntegerSlots.Companion.SIZE
import avail.descriptor.tuples.SmallIntegerIntervalTupleDescriptor.IntegerSlots.Companion.START
import avail.descriptor.tuples.SmallIntegerIntervalTupleDescriptor.IntegerSlots.DELTA
import avail.descriptor.tuples.TreeTupleDescriptor.Companion.concatenateAtLeastOneTree
import avail.descriptor.tuples.TreeTupleDescriptor.Companion.createTwoPartTreeTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.isSupertypeOfIntegerRangeType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import java.util.IdentityHashMap

/**
 * `SmallIntegerIntervalTupleDescriptor` represents an [integer interval
 * tuple][IntegerIntervalTupleDescriptor] whose slots are all Java `long`s.
 *
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 *
 * @constructor
 * Construct a new `SmallIntegerIntervalTupleDescriptor`.
 *
 * @param mutability
 *   The mutability of the descriptor.
 */
class SmallIntegerIntervalTupleDescriptor constructor(mutability: Mutability?)
	: NumericTupleDescriptor(
		mutability!!, null, IntegerSlots::class.java)
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
		HASH_AND_MORE,

		/** [BitField]s containing the extrema of the tuple. */
		START_AND_END,

		/**
		 * The difference between a value and its subsequent neighbor in the
		 * tuple.
		 */
		DELTA;

		companion object
		{
			/** The number of elements in the tuple. */
			val SIZE = BitField(HASH_AND_MORE, 32, 32)

			/** The first value in the tuple, inclusive. */
			val START = BitField(START_AND_END, 32, 32)

			/**
			 * The last value in the tuple, inclusive. Within the constructor,
			 * the supplied END is normalized to the actual last value.
			 */
			val END = BitField(START_AND_END, 0, 32)

			/**
			 * A slot to hold the cached hash value of a tuple.  If zero, then
			 * the hash value must be computed upon request.  Note that in the
			 * very rare case that the hash value actually equals zero, the hash
			 * value has to be computed every time it is requested.
			 */
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

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		builder.append(self.slot(START))
		builder.append(" to ")
		builder.append(self.slot(END))
		val delta = self.slot(DELTA)
		if (delta != 1L)
		{
			builder.append(" by ")
			builder.append(delta)
		}
	}

	override fun o_AppendCanDestroy(
		self: AvailObject,
		newElement: A_BasicObject,
		canDestroy: Boolean): A_Tuple
	{
		val originalSize = self.tupleSize
		val endValue = self.slot(END).toLong()
		val deltaValue = self.slot(DELTA)
		val newElementStrong = newElement as AvailObject
		if (newElementStrong.isInt)
		{
			val newElementValue = newElementStrong.extractInt
			if (newElementValue.toLong() ==
				endValue + deltaValue && originalSize < Int.MAX_VALUE)
			{
				// Extend the interval.
				if (canDestroy && isMutable)
				{
					self.setSlot(END, newElementValue)
					self.setSlot(SIZE, originalSize + 1)
					self.setSlot(HASH_OR_ZERO, 0)
					return self
				}
				// Create another small integer interval.
				return createSmallInterval(
					self.slot(START), newElementValue, deltaValue)
			}
			// The new value isn't consecutive, but it's still an int.
			if (originalSize < maximumCopySize)
			{
				val start = self.slot(START)
				return generateIntTupleFrom(originalSize + 1) {
					if (it == originalSize) newElementValue
					else start + ((it - 1) * deltaValue).toInt()
				}
			}
			// Too big; fall through and make a tree-tuple.
		}
		// Fall back to concatenating a singleton.
		val singleton = tuple(newElement)
		return self.concatenateWith(singleton, canDestroy)
	}

	// Consider a billion element tuple. Since a small interval tuple
	// requires only O(1) storage, irrespective of its size, the average
	// bits per entry is 0.
	override fun o_BitsPerEntry(self: AvailObject): Int = 0

	override fun o_CompareFromToWithStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anotherObject: A_Tuple,
		startIndex2: Int
	): Boolean =
		anotherObject.compareFromToWithSmallIntegerIntervalTupleStartingAt(
			startIndex2,
			startIndex2 + endIndex1 - startIndex1,
			self,
			startIndex1)

	override fun o_CompareFromToWithSmallIntegerIntervalTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aSmallIntegerIntervalTuple: A_Tuple,
		startIndex2: Int): Boolean
	{
		// If the objects refer to the same memory, and the indices match
		// up, the subranges are the same.
		if (self.sameAddressAs(aSmallIntegerIntervalTuple) &&
			startIndex1 == startIndex2)
		{
			return true
		}

		// If the objects do not refer to the same memory but the tuples are
		// identical,
		if (self.equals(aSmallIntegerIntervalTuple))
		{
			// indirect one to the other if it is not shared.
			if (!isShared)
			{
				aSmallIntegerIntervalTuple.makeImmutable()
				self.becomeIndirectionTo(aSmallIntegerIntervalTuple)
			}
			else if (!aSmallIntegerIntervalTuple.descriptor().isShared)
			{
				self.makeImmutable()
				aSmallIntegerIntervalTuple.becomeIndirectionTo(self)
			}

			// If the subranges start at the same place, they are the same.
			return startIndex1 == startIndex2
		}

		// Finally, check the subranges.
		val first = self.copyTupleFromToCanDestroy(
			startIndex1, endIndex1, false)
		val second = aSmallIntegerIntervalTuple.copyTupleFromToCanDestroy(
			startIndex2, startIndex2 + endIndex1 - startIndex1, false)
		return first.equals(second)
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

		if (otherTuple.tupleSize == 0) return self

		// Assess the possibility that the concatenation will still be a small
		// integer interval tuple.
		if (otherTuple.isSmallIntegerIntervalTuple)
		{
			val otherDirect = otherTuple.traversed()
			val delta = self.slot(DELTA)

			// If the other's delta is the same as mine,
			if (delta == otherDirect.slot(DELTA))
			{
				val newSize = self.slot(SIZE) + otherDirect.slot(SIZE).toLong()
				// and the other's start is one delta away from my end,
				if ((self.slot(END) + delta == otherDirect.slot(START).toLong()
					&& newSize == newSize.toInt().toLong()))
				{
					// then we're adjacent.

					// If we can do replacement in place,
					// use me for the return value.
					if (isMutable)
					{
						self.setSlot(END, otherDirect.slot(END))
						self.setSlot(SIZE, newSize.toInt())
						self.setHashOrZero(0)
						return self
					}
					// Or the other one.
					if (otherDirect.descriptor().isMutable)
					{
						otherDirect.setSlot(START, self.slot(START))
						otherDirect.setSlot(SIZE, newSize.toInt())
						otherDirect.setHashOrZero(0)
						return otherDirect
					}

					// Otherwise, create a new interval.
					return createSmallInterval(
						self.slot(START), otherDirect.slot(END), delta)
				}
			}
		}
		return if (otherTuple.treeTupleLevel == 0)
		{
			createTwoPartTreeTuple(self, otherTuple, 1, 0)
		}
		else
		{
			concatenateAtLeastOneTree(self, otherTuple, true)
		}
	}

	override fun o_CopyTupleFromToCanDestroy(
		self: AvailObject,
		start: Int,
		end: Int,
		canDestroy: Boolean): A_Tuple
	{
		// Ensure parameters are in bounds
		val oldSize = self.slot(SIZE)
		assert(start in 1..end + 1 && end <= oldSize)
		val newSize = end - start + 1
		if (newSize == oldSize)
		{
			// This method is requesting a full copy of the original.
			if (isMutable && !canDestroy)
			{
				self.makeImmutable()
			}
			return self
		}

		// The request is for a proper subrange.
		val delta = self.slot(DELTA)
		val oldStartValue = self.slot(START)
		val newStartValue = oldStartValue + delta * (start - 1)
		assert(newStartValue == newStartValue.toInt().toLong())
		val newEndValue = newStartValue + delta * (newSize - 1)
		assert(newEndValue == newEndValue.toInt().toLong())
		if (isMutable && canDestroy)
		{
			// Recycle the object.
			self.setSlot(START, newStartValue.toInt())
			self.setSlot(END, newEndValue.toInt())
			self.setSlot(SIZE, newSize)
			return self
		}
		return createSmallInterval(newStartValue.toInt(), newEndValue.toInt(), delta)
	}

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsSmallIntegerIntervalTuple(self)

	override fun o_EqualsSmallIntegerIntervalTuple(
		self: AvailObject,
		aSmallIntegerIntervalTuple: A_Tuple): Boolean
	{
		// First, check for object-structure (address) identity.
		if (self.sameAddressAs(aSmallIntegerIntervalTuple))
		{
			return true
		}

		// If the objects do not refer to the same memory, check if the tuples
		// are identical.
		val firstTraversed = self.traversed()
		val secondTraversed = aSmallIntegerIntervalTuple.traversed()

		// Check that the slots match.
		val firstHash = firstTraversed.slot(HASH_OR_ZERO)
		val secondHash = secondTraversed.slot(HASH_OR_ZERO)
		when
		{
			firstHash != 0 && secondHash != 0 && firstHash != secondHash ->
				return false
			firstTraversed.slot(SIZE) != secondTraversed.slot(SIZE) ->
				return false
			firstTraversed.slot(DELTA) != secondTraversed.slot(DELTA) ->
				return false
			firstTraversed.slot(START) != secondTraversed.slot(START) ->
				return false

			// All the slots match. Indirect one to the other if it is not shared.
			!isShared ->
			{
				aSmallIntegerIntervalTuple.makeImmutable()
				self.becomeIndirectionTo(aSmallIntegerIntervalTuple)
			}
			!aSmallIntegerIntervalTuple.descriptor().isShared ->
			{
				self.makeImmutable()
				aSmallIntegerIntervalTuple.becomeIndirectionTo(self)
			}
		}
		return true
	}

	override fun o_IsSmallIntegerIntervalTuple(self: AvailObject): Boolean =
		true

	override fun o_TupleAt(self: AvailObject, index: Int): AvailObject
	{
		// Answer the value at the given index in the tuple object.
		// START + (index-1) × DELTA
		assert(index >= 1 && index <= self.tupleSize)
		val temp = self.slot(START) + (index - 1) * self.slot(DELTA)
		assert(temp == temp.toInt().toLong())
		return fromInt(temp.toInt())
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
		val size = self.tupleSize
		assert(index in 1..size)
		val newValueStrong = newValueObject as AvailObject
		if (newValueStrong.isInt
			&& self.tupleIntAt(index) == newValueStrong.extractInt)
		{
			// The element is to be replaced with itself.
			if (!canDestroy) self.makeImmutable()
			return self
		}
		val start = self.slot(START)
		val end = self.slot(END)
		val delta = self.slot(DELTA)
		val result = when
		{
			// Everything will be bytes. Synthesize a byte tuple. The tuple will
			// have at most 256 elements.
			start and 255.inv() == 0
					&& end and 255.inv() == 0
					&& newValueObject.isUnsignedByte ->
				generateByteTupleFrom(self.slot(SIZE)) {
					if (it == index) (newValueObject as A_Number).extractInt
					else start + ((it - 1) * delta).toInt()
				}
			// Synthesize a (reasonably small) general object tuple instead.
			size < 256 ->
				generateObjectTupleFrom(self.slot(SIZE)) {
					if (it == index) newValueObject
					else fromInt(start + ((it - 1) * delta).toInt())
				}
			// The tuple might be huge, so splice the new element between two
			// slices of the original.  The tree tuple and subrange mechanisms
			// will deal with big and tiny cases appropriately.
			else ->
			{
				// Make the object immutable because we're extracting two
				// subranges.
				self.makeImmutable()
				val left = self.copyTupleFromToCanDestroy(1, index - 1, true)
				val right =
					self.copyTupleFromToCanDestroy(index + 1, size, true)
				left.appendCanDestroy(newValueObject, true)
					.concatenateWith(right, true)
			}
		}
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
		return (type.isSupertypeOfIntegerRangeType(inclusive(low, high))
			|| super.o_TupleElementsInRangeAreInstancesOf(
				self, startIndex, endIndex, type))
	}

	override fun o_TupleIntAt(self: AvailObject, index: Int): Int
	{
		// Answer the value at the given index in the tuple object.
		// START + (index-1) × DELTA
		assert(index >= 1 && index <= self.tupleSize)
		var temp = index - 1.toLong()
		temp *= self.slot(DELTA)
		temp += self.slot(START).toLong()
		assert(temp == temp.toInt().toLong())
		return temp.toInt()
	}

	override fun o_TupleLongAt(self: AvailObject, index: Int): Long
	{
		// Answer the value at the given index in the tuple object.
		// START + (index-1) × DELTA
		assert(index >= 1 && index <= self.tupleSize)
		var temp = index - 1.toLong()
		temp *= self.slot(DELTA)
		temp += self.slot(START).toLong()
		return temp
	}

	override fun o_TupleReverse(self: AvailObject): A_Tuple
	{
		val newDelta = 0 - self.slot(DELTA)
		// If tuple is small enough or is immutable, create a new interval.
		if (!isMutable)
		{
			return createSmallInterval(
				self.slot(END), self.slot(START), newDelta)
		}

		//The interval is mutable and large enough to warrant changing in place.
		val newStart = self.slot(END)
		val newEnd = self.slot(START)
		self.setSlot(START, newStart)
		self.setSlot(END, newEnd)
		self.setSlot(DELTA, newDelta)
		return self
	}

	override fun o_TupleSize(self: AvailObject): Int = self.slot(SIZE)

	override fun mutable(): SmallIntegerIntervalTupleDescriptor = mutable

	override fun immutable(): SmallIntegerIntervalTupleDescriptor = immutable

	override fun shared(): SmallIntegerIntervalTupleDescriptor = shared

	companion object
	{
		/**
		 * Defined threshold for making copies versus using
		 * [TreeTupleDescriptor]/using other forms of reference instead of
		 * creating a new tuple.
		 */
		private const val maximumCopySize = 32

		/** The mutable [SmallIntegerIntervalTupleDescriptor]. */
		private val mutable =
			SmallIntegerIntervalTupleDescriptor(Mutability.MUTABLE)

		/** The immutable [IntegerIntervalTupleDescriptor]. */
		private val immutable =
			SmallIntegerIntervalTupleDescriptor(Mutability.IMMUTABLE)

		/** The shared [SmallIntegerIntervalTupleDescriptor]. */
		private val shared =
			SmallIntegerIntervalTupleDescriptor(Mutability.SHARED)

		/**
		 * Evaluates whether the supplied parameters for an integer interval
		 * tuple are small enough that the small integer interval tuple
		 * representation can be used.
		 *
		 * @param newStart
		 *   The start value for the candidate interval tuple.
		 * @param newEnd
		 *   The end value for the candidate interval tuple.
		 * @param delta
		 *   The delta for the candidate interval tuple.
		 * @return
		 *   `true` if all values would fit in the small representation, `false`
		 *   otherwise.
		 */
		fun isSmallIntervalCandidate(
			newStart: A_Number,
			newEnd: A_Number,
			delta: A_Number): Boolean
		{
			// Size is always an integer, so no need to check it.
			if (!newStart.isInt || !newEnd.isInt || !delta.isInt)
			{
				return false
			}
			val size =
				((newEnd.extractLong - newStart.extractLong)
					/ delta.extractInt + 1L)
			// Watch out for the case that they're all ints, but the size is
			// bigger than Integer.MAX_VALUE.  (e.g., -2 billion to +2 billion
			// has a size of 4 billion, which is bigger than a signed int can
			// hold.
			return size == size.toInt().toLong()
		}

		/**
		 * Create a new interval according to the parameters.
		 *
		 * @param newStart
		 *   The first integer in the interval.
		 * @param newEnd
		 *   The last integer in the interval.
		 * @param delta
		 *   The difference between an integer and its subsequent neighbor in
		 *   the interval. Delta is nonzero.
		 * @return
		 *   The new interval.
		 */
		fun createSmallInterval(
			newStart: Int,
			newEnd: Int,
			delta: Long): A_Tuple
		{
			assert(delta != 0L)
			val size = (newEnd.toLong() - newStart.toLong()) / delta + 1L
			assert(size == size.toInt().toLong())
				{ "Proposed tuple has too many elements" }
			val adjustedEnd = newStart + delta * (size.toInt() - 1)
			assert(adjustedEnd == adjustedEnd.toInt().toLong())
			return mutable.create {
				setSlot(START, newStart)
				setSlot(END, adjustedEnd.toInt())
				setSlot(DELTA, delta)
				setSlot(HASH_OR_ZERO, 0)
				setSlot(SIZE, size.toInt())
			}
		}
	}
}
