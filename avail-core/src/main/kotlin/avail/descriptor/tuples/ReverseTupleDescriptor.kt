/*
 * ReverseTupleDescriptor.kt
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
package avail.descriptor.tuples

import avail.annotations.HideFieldInDebugger
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.BitField
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.tuples.A_Tuple.Companion.childAt
import avail.descriptor.tuples.A_Tuple.Companion.childCount
import avail.descriptor.tuples.A_Tuple.Companion.concatenateWith
import avail.descriptor.tuples.A_Tuple.Companion.copyTupleFromToCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.treeTupleLevel
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleAtPuttingCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.tupleElementsInRangeAreInstancesOf
import avail.descriptor.tuples.A_Tuple.Companion.tupleIntAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleLongAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleReverse
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.generateObjectTupleFrom
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.ReverseTupleDescriptor.IntegerSlots.Companion.HASH_OR_ZERO
import avail.descriptor.tuples.ReverseTupleDescriptor.IntegerSlots.Companion.SIZE
import avail.descriptor.tuples.ReverseTupleDescriptor.ObjectSlots.ORIGIN_TUPLE
import avail.descriptor.tuples.TreeTupleDescriptor.Companion.internalTreeReverse
import avail.descriptor.types.A_Type

/**
 * A reverse tuple holds a reference to an "origin" tuple and the origin
 * tuple's size.
 *
 *
 * To avoid arbitrarily deep constructs, the origin tuple must not itself be
 * a reverse tuple.  Any attempt to create a reverse tuple from a reverse
 * tuple will return the origin tuple.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 * Construct a new `ReverseTupleDescriptor`.
 *
 * @param mutability
 *   The mutability of the descriptor.
 */
class ReverseTupleDescriptor private constructor(mutability: Mutability)
	: TupleDescriptor(
		mutability, ObjectSlots::class.java, IntegerSlots::class.java)
{
	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum
	{
		/**
		 * The low 32 bits are used for the [HASH_OR_ZERO], but the upper 32 can
		 * be used by other [BitField]s in subclasses of [TupleDescriptor].
		 */
		@HideFieldInDebugger
		HASH_AND_MORE;

		companion object
		{
			/**
			 * A slot to hold the cached hash value of a tuple.  If zero, then
			 * the hash value must be computed upon request.  Note that in the
			 * very rare case that the hash value actually equals zero, the hash
			 * value has to be computed every time it is requested.
			 */
			val HASH_OR_ZERO = BitField(HASH_AND_MORE, 0, 32) { null }

			/** The number of elements in this tuple. */
			val SIZE = BitField(HASH_AND_MORE, 32, 32, Int::toString)

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
		/**
		 * The basis tuple of which this is a subrange.  The basis tuple must be
		 * flat -- it may not be another subrange tuple, nor may it be a tree
		 * tuple.
		 */
		ORIGIN_TUPLE
	}

	override fun o_AppendCanDestroy(
		self: AvailObject,
		newElement: A_BasicObject,
		canDestroy: Boolean): A_Tuple
	{
		// Fall back to concatenating a singleton.
		if (!canDestroy)
		{
			self.makeImmutable()
		}
		val singleton = tuple(newElement)
		return self.concatenateWith(singleton, canDestroy)
	}

	// Answer maximum integer value so that any other representation
	// for comparison is used in favor of a Reverse Tuple representation.
	override fun o_BitsPerEntry(self: AvailObject): Int = Int.MAX_VALUE

	override fun o_ChildAt(self: AvailObject, childIndex: Int): A_Tuple
	{
		if (!self.descriptor().isShared)
		{
			val treeTuple = internalTreeReverse(self.slot(ORIGIN_TUPLE))
			treeTuple.setHashOrZero(self.slot(HASH_OR_ZERO))
			self.becomeIndirectionTo(treeTuple)
			return treeTuple.childAt(childIndex)
		}
		// Object is shared so it cannot change to an indirection.  Instead, we
		// need to return the reverse of the child one level down at the
		// opposite end of the tree from the childIndex.
		val adjustedSubscript = self.childCount + 1 - childIndex
		return self.slot(ORIGIN_TUPLE)
			.childAt(adjustedSubscript)
			.tupleReverse()
	}

	override fun o_ChildCount(self: AvailObject): Int =
		self.slot(ORIGIN_TUPLE).childCount

	override fun o_CompareFromToWithStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anotherObject: A_Tuple,
		startIndex2: Int): Boolean
	{
		var index = startIndex1
		var index2 = startIndex2
		while (index <= endIndex1)
		{
			if (!self.tupleAt(index).equals(anotherObject.tupleAt(index2)))
			{
				return false
			}
			index++
			index2++
		}
		return true
	}

	override fun o_ConcatenateWith(
		self: AvailObject,
		otherTuple: A_Tuple,
		canDestroy: Boolean): A_Tuple
	{
		// If the receiver tuple is empty return the otherTuple.
		val size1 = self.tupleSize
		if (size1 == 0)
		{
			if (!canDestroy)
			{
				otherTuple.makeImmutable()
			}
			return otherTuple
		}
		// If otherTuple is empty return the receiver tuple, object.
		val size2 = otherTuple.tupleSize
		if (size2 == 0)
		{
			if (!canDestroy)
			{
				self.makeImmutable()
			}
			return self
		}
		val newSize = size1 + size2
		if (newSize <= maximumCopySize)
		{
			// Copy the objects.
			val unreversedFirstTuple: A_Tuple =
				self.slot(ORIGIN_TUPLE)
			return generateObjectTupleFrom(newSize)
			{
				if (it <= size1)
				{
					unreversedFirstTuple.tupleAt(size1 + 1 - it)
				}
				else
				{
					otherTuple.tupleAt(it - size1)
				}
			}
		}
		if (!canDestroy)
		{
			self.makeImmutable()
			otherTuple.makeImmutable()
		}
		if (self.slot(ORIGIN_TUPLE).treeTupleLevel == 0)
		{
			return if (otherTuple.treeTupleLevel == 0)
			{
				TreeTupleDescriptor.createTwoPartTreeTuple(
					self, otherTuple, 1, 0)
			}
			else TreeTupleDescriptor.concatenateAtLeastOneTree(
				self, otherTuple, true)
		}
		val newTree = internalTreeReverse(
			self.slot(ORIGIN_TUPLE))
		return TreeTupleDescriptor
			.concatenateAtLeastOneTree(newTree, otherTuple, true)
	}

	override fun o_CopyTupleFromToCanDestroy(
		self: AvailObject,
		start: Int,
		end: Int,
		canDestroy: Boolean): A_Tuple
	{
		val tupleSize = self.tupleSize
		assert(start in 1..end + 1 && end <= tupleSize)
		val subrangeSize = end - start + 1
		if (subrangeSize == 0)
		{
			if (isMutable && canDestroy)
			{
				self.assertObjectUnreachableIfMutable()
			}
			return emptyTuple
		}
		if (subrangeSize == tupleSize)
		{
			if (isMutable && !canDestroy)
			{
				self.makeImmutable()
			}
			return self
		}
		if (subrangeSize < maximumCopySize)
		{
			// It's not empty, it's not a total copy, and it's reasonably small.
			// Just copy the applicable entries out.
			val result = generateObjectTupleFrom(
				subrangeSize) { self.tupleAt(it + start - 1) }
			if (canDestroy)
			{
				self.assertObjectUnreachableIfMutable()
			}
			else
			{
				result.makeSubobjectsImmutable()
			}
			result.setSlot(HASH_OR_ZERO, 0)
			return result
		}
		val subrangeOnOrigin =
			self.slot(ORIGIN_TUPLE).copyTupleFromToCanDestroy(
				self.tupleSize + 1 - end,
				self.tupleSize + 1 - start,
				canDestroy)
		return subrangeOnOrigin.tupleReverse()
	}

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsReverseTuple(self)

	override fun o_EqualsReverseTuple(
		self: AvailObject,
		aTuple: A_Tuple
	): Boolean =
		self.slot(ORIGIN_TUPLE).equals(
			(aTuple as AvailObject).slot(ORIGIN_TUPLE))

	override fun o_TreeTupleLevel(self: AvailObject): Int =
		self.slot(ORIGIN_TUPLE).treeTupleLevel

	override fun o_TupleAt(self: AvailObject, index: Int): AvailObject
	{
		val size = self.slot(ORIGIN_TUPLE).tupleSize
		assert(index in 1 .. size)
		val reverseIndex = size + 1 - index
		return self.slot(ORIGIN_TUPLE).tupleAt(reverseIndex)
	}

	override fun o_TupleAtPuttingCanDestroy(
		self: AvailObject,
		index: Int,
		newValueObject: A_BasicObject,
		canDestroy: Boolean): A_Tuple
	{
		// Answer a tuple with all the elements of object except at the given
		// index we should have newValueObject.  This may destroy the original
		// tuple if canDestroy is true.
		assert(index >= 1 && index <= self.tupleSize)
		val innerTuple = self.slot(ORIGIN_TUPLE).tupleAtPuttingCanDestroy(
			self.slot(SIZE) + 1 - index, newValueObject, canDestroy)
		if (!canDestroy || !isMutable)
		{
			return createReverseTuple(innerTuple)
		}
		self.setSlot(ORIGIN_TUPLE, innerTuple)
		self.setHashOrZero(0)
		return self
	}

	override fun o_TupleElementsInRangeAreInstancesOf(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int,
		type: A_Type): Boolean
	{
		val size = self.slot(ORIGIN_TUPLE).tupleSize
		val originStart = size + 1 - endIndex
		val originEnd = size + 1 - startIndex
		return self.slot(ORIGIN_TUPLE).tupleElementsInRangeAreInstancesOf(
			originStart, originEnd, type)
	}

	override fun o_TupleIntAt(self: AvailObject, index: Int): Int
	{
		val size = self.slot(ORIGIN_TUPLE).tupleSize
		assert(index in 1 .. size)
		val reverseIndex = size + 1 - index
		return self.slot(ORIGIN_TUPLE).tupleIntAt(reverseIndex)
	}

	override fun o_TupleLongAt(self: AvailObject, index: Int): Long
	{
		val size = self.slot(ORIGIN_TUPLE).tupleSize
		assert(index in 1 .. size)
		val reverseIndex = size + 1 - index
		return self.slot(ORIGIN_TUPLE).tupleLongAt(reverseIndex)
	}

	override fun o_TupleReverse(self: AvailObject): A_Tuple =
		self.slot(ORIGIN_TUPLE)

	override fun o_TupleSize(self: AvailObject): Int =
		self.slot(SIZE)

	override fun mutable(): ReverseTupleDescriptor = mutable

	override fun immutable(): ReverseTupleDescriptor = immutable

	override fun shared(): ReverseTupleDescriptor = shared

	companion object
	{
		/**
		 * Defined threshold for making copies versus using
		 * [TreeTupleDescriptor]/using other forms of reference instead of
		 * creating a new tuple.
		 */
		private const val maximumCopySize = 32

		/** The mutable [ReverseTupleDescriptor]. */
		val mutable = ReverseTupleDescriptor(Mutability.MUTABLE)

		/** The immutable [ReverseTupleDescriptor]. */
		private val immutable = ReverseTupleDescriptor(Mutability.IMMUTABLE)

		/** The shared [ReverseTupleDescriptor]. */
		private val shared = ReverseTupleDescriptor(Mutability.SHARED)

		/**
		 * Create a new [AvailObject] that wraps the specified [AvailObject]
		 * tuple and provides it with a `ReverseTupleDescriptor` descriptor.
		 *
		 * The original tuple may be destroyed by this operation.  If you need
		 * the original after this call, use [A_BasicObject.makeImmutable]  on
		 * it prior to the call.
		 *
		 * @param originTuple
		 *   The tuple to be reversed.
		 * @return
		 *   A new reverse tuple.
		 */
		fun createReverseTuple(originTuple: A_Tuple): AvailObject =
			mutable.create {
				setSlot(ORIGIN_TUPLE, originTuple)
				setSlot(SIZE, originTuple.tupleSize)
			}
	}
}
