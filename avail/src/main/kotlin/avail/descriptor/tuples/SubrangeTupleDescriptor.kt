/*
 * SubrangeTupleDescriptor.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.BitField
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.tuples.A_Tuple.Companion.compareFromToWithStartingAt
import avail.descriptor.tuples.A_Tuple.Companion.computeHashFromTo
import avail.descriptor.tuples.A_Tuple.Companion.concatenateWith
import avail.descriptor.tuples.A_Tuple.Companion.copyTupleFromToCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.firstIndexOf
import avail.descriptor.tuples.A_Tuple.Companion.transferIntoByteBuffer
import avail.descriptor.tuples.A_Tuple.Companion.treeTupleLevel
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleAtPuttingCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.tupleCodePointAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleElementsInRangeAreInstancesOf
import avail.descriptor.tuples.A_Tuple.Companion.tupleIntAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleLongAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleReverse
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.SubrangeTupleDescriptor.IntegerSlots.Companion.HASH_OR_ZERO
import avail.descriptor.tuples.SubrangeTupleDescriptor.IntegerSlots.Companion.SIZE
import avail.descriptor.tuples.SubrangeTupleDescriptor.IntegerSlots.Companion.START_INDEX
import avail.descriptor.tuples.SubrangeTupleDescriptor.ObjectSlots.BASIS_TUPLE
import avail.descriptor.tuples.TreeTupleDescriptor.Companion.concatenateAtLeastOneTree
import avail.descriptor.tuples.TreeTupleDescriptor.Companion.createTwoPartTreeTuple
import avail.descriptor.types.A_Type
import java.nio.ByteBuffer

/**
 * A subrange tuple holds a reference to a "basis" tuple, the subrange's
 * starting index within that tuple, and the size of the subrange.  The subrange
 * is itself a tuple.
 *
 * To avoid arbitrarily deep constructs, the basis tuple must not itself be a
 * subrange tuple, nor may it be a [tree tuple][TreeTupleDescriptor]. In
 * general, tree tuples contain as leaves either subrange tuples or flat tuples,
 * and subrange tuples may only contain flat tuples.
 *
 * A subrange must not be empty.  Additionally, it should be at least some
 * threshold minimum size, otherwise a flat tuple would do the job more
 * efficiently.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *   Construct a new [SubrangeTupleDescriptor].
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 */
class SubrangeTupleDescriptor private constructor(mutability: Mutability)
	: TupleDescriptor(
		mutability, ObjectSlots::class.java, IntegerSlots::class.java)
{
	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum
	{
		/**
		 * The low 32 bits are used for the [HASH_OR_ZERO], but the upper 32
		 * can be used by other [BitField]s in subclasses of [TupleDescriptor].
		 */
		@HideFieldInDebugger
		HASH_AND_MORE,

		/**
		 * [BitField]s holding the starting position and tuple size.
		 */
		START_AND_SIZE;

		companion object
		{
			/**
			 * A slot to hold the cached hash value of a tuple.  If zero, then
			 * the hash value must be computed upon request.  Note that in the
			 * very rare case that the hash value actually equals zero, the hash
			 * value has to be computed every time it is requested.
			 */
			val HASH_OR_ZERO = BitField(HASH_AND_MORE, 0, 32) { null }

			/**
			 * The first index of the basis tuple that is within this subrange.
			 */
			val START_INDEX = BitField(START_AND_SIZE, 0, 32, Int::toString)

			/**
			 * The number of elements in this subrange tuple, starting at the
			 * [START_INDEX].  Must not be zero, and should probably be at
			 * least some reasonable size to avoid time and space overhead.
			 */
			val SIZE = BitField(START_AND_SIZE, 32, 32, Int::toString)

			init
			{
				assert(TupleDescriptor.IntegerSlots.HASH_AND_MORE.ordinal
							== HASH_AND_MORE.ordinal)
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
		BASIS_TUPLE
	}

	override fun o_AppendCanDestroy(
		self: AvailObject,
		newElement: A_BasicObject,
		canDestroy: Boolean): A_Tuple
	{
		val startIndex = self.slot(START_INDEX)
		val originalSize = self.slot(SIZE)
		val endIndex = startIndex + originalSize - 1
		val basisTuple = self.slot(BASIS_TUPLE)
		if (endIndex < basisTuple.tupleSize
			&& basisTuple.tupleAt(endIndex).equals(newElement))
		{
			// We merely need to increase the range.
			if (canDestroy && isMutable)
			{
				self.setSlot(SIZE, originalSize + 1)
				self.setSlot(HASH_OR_ZERO, 0)
				return self
			}
			basisTuple.makeImmutable()
			return createSubrange(basisTuple, startIndex, originalSize + 1)
		}
		// Fall back to concatenating with a singleton.
		val singleton = tuple(newElement)
		return self.concatenateWith(singleton, canDestroy)
	}

	/**
	 * Answer approximately how many bits per entry are taken up by this object.
	 *
	 * Make this always seem a little better than the worst flat representation.
	 */
	override fun o_BitsPerEntry(self: AvailObject): Int = 63

	override fun o_CompareFromToWithByteStringStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aByteString: A_String,
		startIndex2: Int): Boolean =
			o_CompareFromToWithStartingAt(
				self, startIndex1, endIndex1, aByteString, startIndex2)

	override fun o_CompareFromToWithByteTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aByteTuple: A_Tuple,
		startIndex2: Int): Boolean =
		o_CompareFromToWithStartingAt(
				self, startIndex1, endIndex1, aByteTuple, startIndex2)

	override fun o_CompareFromToWithNybbleTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aNybbleTuple: A_Tuple,
		startIndex2: Int): Boolean =
			o_CompareFromToWithStartingAt(
				self, startIndex1, endIndex1, aNybbleTuple, startIndex2)

	override fun o_CompareFromToWithObjectTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anObjectTuple: A_Tuple,
		startIndex2: Int): Boolean =
			o_CompareFromToWithStartingAt(
				self, startIndex1, endIndex1, anObjectTuple, startIndex2)

	/**
	 * {@inheritDoc}
	 * Compare a subrange of this subrange tuple with part of the given tuple.
	 */
	override fun o_CompareFromToWithStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anotherObject: A_Tuple,
		startIndex2: Int): Boolean
	{
		if (self.sameAddressAs(anotherObject) && startIndex1 == startIndex2)
		{
			return true
		}
		val offset = self.slot(START_INDEX)
		if (!self.slot(BASIS_TUPLE).compareFromToWithStartingAt(
				startIndex1 + offset - 1,
				endIndex1 + offset - 1,
				anotherObject,
				startIndex2))
		{
			return false
		}
		if (startIndex1 == 1
			&& startIndex2 == 1
			&& endIndex1 == self.tupleSize
			&& endIndex1 == anotherObject.tupleSize)
		{
			if (!isShared)
			{
				anotherObject.makeImmutable()
				self.becomeIndirectionTo(anotherObject)
			}
			else if (!anotherObject.descriptor().isShared)
			{
				self.makeImmutable()
				anotherObject.becomeIndirectionTo(self)
			}
		}
		return true
	}

	/**
	 * Hash part of the tuple object.
	 */
	override fun o_ComputeHashFromTo(
		self: AvailObject,
		start: Int,
		end: Int): Int
	{
		val basis: A_Tuple = self.slot(BASIS_TUPLE)
		val size = self.slot(SIZE)
		assert(start in 1..size)
		assert(end in start - 1..size)
		val adjustment = self.slot(START_INDEX) - 1
		return basis.computeHashFromTo(start + adjustment, end + adjustment)
	}

	override fun o_ConcatenateWith(
		self: AvailObject,
		otherTuple: A_Tuple,
		canDestroy: Boolean): A_Tuple
	{
		assert(self.tupleSize > 0)
		if (otherTuple.tupleSize == 0)
		{
			if (!canDestroy)
			{
				self.makeImmutable()
			}
			return self
		}
		if (!canDestroy)
		{
			self.makeImmutable()
			otherTuple.makeImmutable()
		}
		return if (otherTuple.treeTupleLevel == 0)
		{
			// No tree tuples are involved yet.  Create a bottom-level tree
			// tuple on these two level zero tuples (the tuples may be flat or
			// subranges).
			createTwoPartTreeTuple(self, otherTuple, 1, 0)
		}
		else
		{
			concatenateAtLeastOneTree(self, otherTuple, true)
		}
	}

	/**
	 * Make a tuple that only contains the given range of elements of the given
	 * tuple.  While it would be easy to always produce another subrange tuple,
	 * this isn't a good idea.  Let the specific kind of flat tuple that is our
	 * basis decide what the cutoff size is.
	 */
	override fun o_CopyTupleFromToCanDestroy(
		self: AvailObject,
		start: Int,
		end: Int,
		canDestroy: Boolean): A_Tuple
	{
		val oldSize = self.slot(SIZE)
		assert(start in 1..end + 1 && end <= oldSize)
		val newSize = end - start + 1
		if (newSize == 0)
		{
			return emptyTuple
		}
		val oldStartIndex = self.slot(START_INDEX)
		if (canDestroy && isMutable && newSize >= minSize)
		{
			// Modify the bounds in place.
			self.setSlot(START_INDEX, oldStartIndex + start - 1)
			self.setSlot(SIZE, newSize)
			return self
		}
		val basis = self.slot(BASIS_TUPLE)
		if (!canDestroy)
		{
			basis.makeImmutable()
		}
		// Let the basis decide if a subrange or copying is most appropriate.
		return basis.copyTupleFromToCanDestroy(
			start + oldStartIndex - 1,
			end + oldStartIndex - 1,
			canDestroy)
	}

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsAnyTuple(self)

	override fun o_EqualsAnyTuple(
		self: AvailObject,
		aTuple: A_Tuple): Boolean
	{
		when
		{
			self.sameAddressAs(aTuple) -> return true
			self.tupleSize != aTuple.tupleSize -> return false
			self.hash() != aTuple.hash() -> return false
			else ->
			{
				val startIndex = self.slot(START_INDEX)
				val size = self.slot(SIZE)
				return self.slot(BASIS_TUPLE)
					.compareFromToWithStartingAt(
						startIndex,
						startIndex + size - 1,
						aTuple,
						1)
			}
		}
	}

	override fun o_FirstIndexOf(
		self: AvailObject,
		value: A_BasicObject,
		startIndex: Int,
		endIndex: Int): Int
	{
		val size = self.slot(SIZE)
		assert(startIndex in 1..size)
		assert(endIndex in 1..size)
		val adjustment = self.slot(START_INDEX) - 1
		return self.slot(BASIS_TUPLE).firstIndexOf(
			value, startIndex + adjustment, endIndex + adjustment)
	}

	override fun o_LastIndexOf(
		self: AvailObject,
		value: A_BasicObject,
		startIndex: Int,
		endIndex: Int): Int
	{
		val size = self.slot(SIZE)
		assert(startIndex in 1..size)
		assert(endIndex in 1..size)
		val adjustment = self.slot(START_INDEX) - 1
		return self.slot(BASIS_TUPLE).firstIndexOf(
			value, startIndex + adjustment, endIndex + adjustment)
	}

	override fun o_TransferIntoByteBuffer(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int,
		outputByteBuffer: ByteBuffer)
	{
		val basis: A_Tuple = self.slot(BASIS_TUPLE)
		val size = self.slot(SIZE)
		assert(startIndex in 1..size)
		assert(endIndex in startIndex - 1..size)
		val adjustment = self.slot(START_INDEX) - 1
		basis.transferIntoByteBuffer(
			startIndex + adjustment,
			endIndex + adjustment,
			outputByteBuffer)
	}

	/**
	 * Answer the element at the given index in the tuple object.
	 */
	override fun o_TupleAt(self: AvailObject, index: Int): AvailObject
	{
		assert(index in 1..self.slot(SIZE))
		val adjustedIndex = index + self.slot(START_INDEX) - 1
		return self.slot(BASIS_TUPLE).tupleAt(adjustedIndex)
	}

	/**
	 * Answer a tuple with all the elements of object except at the given index
	 * we should have newValueObject.  This may destroy the original tuple if
	 * canDestroy is true.
	 *
	 * We want to balance having to clone the entire basis tuple with having to
	 * build a lot of infrastructure to deal with the alteration.  We keep it
	 * fairly simple by creating two subranges spanning the left half and
	 * right half of this subrange.  We then recurse to update the tuple in the
	 * half containing the index.  Eventually the pieces will be small enough
	 * that a subrange won't be produced, and another implementation will be
	 * invoked instead to deal with a small flat tuple.
	 */
	override fun o_TupleAtPuttingCanDestroy(
		self: AvailObject,
		index: Int,
		newValueObject: A_BasicObject,
		canDestroy: Boolean): A_Tuple
	{
		// Ideas:
		// * Sometimes split the range into more manageable-sized pieces, group
		//   them into a tree tuple, then recurse on the interesting part.
		// * Sometimes copy out the interesting flat range and clobber the slot,
		//   answering the new flat tuple.
		// If the range was small enough for the latter it would have already
		// made the small copy during the initial subrange extraction.  Still,
		// if it's mutable it might be worthwhile.  However, this could still be
		// expensive if tupleAtPuttingCanDestroy has to transform the
		// representation into something broader (e.g., nybbles -> objects).
		val tupleSize = self.slot(SIZE)
		assert(index in 1 .. tupleSize)
		val adjustment = self.slot(START_INDEX) - 1
		val basis = self.slot(BASIS_TUPLE).traversed()
		if (!canDestroy)
		{
			basis.makeImmutable()
		}
		assert(tupleSize >= 3)
			{ "subrange is too small; recursion won't bottom out correctly" }
		// Freeze the basis, since there may be two references to it below.
		basis.makeImmutable()
		// Split into two parts, approximately evenly.  Use the coordinate
		// system of the basis tuple.
		val start = 1 + adjustment
		val splitPoint = start + (tupleSize ushr 1)
		var leftPart = basis.copyTupleFromToCanDestroy(
			start, splitPoint - 1, false)
		assert(1 <= leftPart.tupleSize)
		assert(leftPart.tupleSize < tupleSize)
		val end = tupleSize + adjustment
		var rightPart = basis.copyTupleFromToCanDestroy(splitPoint, end, false)
		assert(1 <= rightPart.tupleSize)
		assert(rightPart.tupleSize < tupleSize)
		assert(leftPart.tupleSize + rightPart.tupleSize == tupleSize)
		val adjustedIndex = index + adjustment
		if (adjustedIndex < splitPoint)
		{
			leftPart = leftPart.tupleAtPuttingCanDestroy(
				index, newValueObject, true)
		}
		else
		{
			rightPart = rightPart.tupleAtPuttingCanDestroy(
				index + start - splitPoint, newValueObject, canDestroy)
		}
		return leftPart.concatenateWith(rightPart, true)
	}

	override fun o_TupleCodePointAt(self: AvailObject, index: Int): Int =
		self.slot(BASIS_TUPLE)
			.tupleCodePointAt(index + self.slot(START_INDEX) - 1)

	override fun o_TupleElementsInRangeAreInstancesOf(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int,
		type: A_Type): Boolean
	{
		val offset = self.slot(START_INDEX) - 1
		return self.slot(BASIS_TUPLE).tupleElementsInRangeAreInstancesOf(
			startIndex + offset, endIndex + offset, type)
	}

	override fun o_TupleIntAt(self: AvailObject, index: Int): Int =
		self.slot(BASIS_TUPLE)
			.tupleIntAt(index + self.slot(START_INDEX) - 1)

	override fun o_TupleLongAt(self: AvailObject, index: Int): Long =
		self.slot(BASIS_TUPLE)
			.tupleLongAt(index + self.slot(START_INDEX) - 1)

	override fun o_TupleReverse(self: AvailObject): A_Tuple
	{
		//Because SubrangeTupleDescriptor is also a wrapper, presume
		//that decision was already made that tuple size was too big to make
		//a copy.
		return mutable.create {
			setSlot(
				BASIS_TUPLE,
				self.slot(BASIS_TUPLE).tupleReverse())
			setSlot(
				START_INDEX,
				self.slot(BASIS_TUPLE).tupleSize + 2 -
					(self.tupleSize + self.slot(START_INDEX)))
			setSlot(SIZE, self.tupleSize)
		}
	}

	/**
	 * Answer the number of elements in the tuple as an int.
	 */
	override fun o_TupleSize(self: AvailObject): Int = self.slot(SIZE)

	override fun mutable(): SubrangeTupleDescriptor = mutable

	override fun immutable(): SubrangeTupleDescriptor = immutable

	override fun shared(): SubrangeTupleDescriptor = shared

	companion object
	{
		/**
		 * Answer the minimum number of elements a subrange tuple may have.
		 * Below this threshold the subrange representation is expected to be
		 * unnecessarily verbose and slow.
		 */
		const val minSize = 10

		/**
		 * Create a [subrange tuple][SubrangeTupleDescriptor] with the given
		 * basis tuple, start index, and size.  Make the basis tuple immutable
		 * for safety.
		 *
		 * @param basisTuple
		 *   The basis tuple of this subrange tuple.
		 * @param startIndex
		 *   The starting index within the basis tuple
		 * @param size
		 *   The size of this subrange tuple.
		 * @return
		 *   A fresh subrange tuple.
		 */
		fun createSubrange(
			basisTuple: A_Tuple,
			startIndex: Int,
			size: Int): AvailObject
		{
			assert(size >= minSize)
			assert(size < basisTuple.tupleSize)
			basisTuple.makeImmutable()
			return mutable.create(size) {
				setSlot(BASIS_TUPLE, basisTuple)
				setSlot(START_INDEX, startIndex)
				setSlot(SIZE, size)
			}
		}

		/** The mutable [SubrangeTupleDescriptor]. */
		val mutable = SubrangeTupleDescriptor(Mutability.MUTABLE)

		/** The immutable [SubrangeTupleDescriptor]. */
		private val immutable = SubrangeTupleDescriptor(Mutability.IMMUTABLE)

		/** The shared [SubrangeTupleDescriptor]. */
		private val shared = SubrangeTupleDescriptor(Mutability.SHARED)
	}
}
