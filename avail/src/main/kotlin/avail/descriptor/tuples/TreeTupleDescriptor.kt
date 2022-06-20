/*
 * TreeTupleDescriptor.kt
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
import avail.descriptor.representation.AvailObject.Companion.newObjectIndexedIntegerIndexedDescriptor
import avail.descriptor.representation.AvailObjectRepresentation.Companion.newLike
import avail.descriptor.representation.BitField
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.Mutability.IMMUTABLE
import avail.descriptor.representation.Mutability.MUTABLE
import avail.descriptor.representation.Mutability.SHARED
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.tuples.A_Tuple.Companion.childAt
import avail.descriptor.tuples.A_Tuple.Companion.childCount
import avail.descriptor.tuples.A_Tuple.Companion.compareFromToWithStartingAt
import avail.descriptor.tuples.A_Tuple.Companion.concatenateWith
import avail.descriptor.tuples.A_Tuple.Companion.copyTupleFromToCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.hashFromTo
import avail.descriptor.tuples.A_Tuple.Companion.replaceFirstChild
import avail.descriptor.tuples.A_Tuple.Companion.transferIntoByteBuffer
import avail.descriptor.tuples.A_Tuple.Companion.treeTupleLevel
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleAtPuttingCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.tupleElementsInRangeAreInstancesOf
import avail.descriptor.tuples.A_Tuple.Companion.tupleIntAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleLongAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleReverse
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.TreeTupleDescriptor.Companion.maxWidth
import avail.descriptor.tuples.TreeTupleDescriptor.Companion.minWidthOfNonRoot
import avail.descriptor.tuples.TreeTupleDescriptor.IntegerSlots.CUMULATIVE_SIZES_AREA_
import avail.descriptor.tuples.TreeTupleDescriptor.IntegerSlots.Companion.HASH_OR_ZERO
import avail.descriptor.tuples.TreeTupleDescriptor.ObjectSlots.SUBTUPLE_AT_
import avail.descriptor.types.A_Type
import avail.utility.structures.EnumMap.Companion.enumMap
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * A tree tuple is a tuple organized as a constant height tree, similar to the
 * well known B-Tree family, but without the requirement to fit the nodes onto
 * a small number of disk pages.  Instead of the hundreds or thousands of
 * children that B-Tree nodes have, the tree tuple nodes have between
 * [16][minWidthOfNonRoot] and [64][maxWidth] children, except the root which
 * may have as few as 2.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property level
 *   The height of a tuple tree with this descriptor.
 *
 * @constructor
 * Construct a new `TreeTupleDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param level
 *   The height of the node in the tree tuple.
 */
class TreeTupleDescriptor internal constructor(
	mutability: Mutability,
	private val level: Int
) : TupleDescriptor(
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
		HASH_AND_MORE,

		/**
		 * The number of elements in the 1<sup>st</sup> through N<sup>th</sup>
		 * subtuples, as `int` slots.
		 */
		CUMULATIVE_SIZES_AREA_;

		companion object
		{
			/**
			 * The hash value of this tree tuple, or zero.  If the hash value
			 * happens to equal zero it will have to be recomputed each time it
			 * is requested.  Note that the hash function for tuples was chosen
			 * in such a way that the hash value of the concatenation of
			 * subtuples is easily computable from the hashes of the subtuples
			 * and their lengths.
			 */
			val HASH_OR_ZERO = BitField(HASH_AND_MORE, 0, 32) { null }

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
		 * The subtuples of which this tree tuple is composed.  The subtuples
		 * of a level=1 tree tuple must be flat or subrange tuples, but the
		 * subtuples of a level=N>1 tree tuple must be level=N-1 tree tuples,
		 * each containing at least [minWidthOfNonRoot].
		 */
		SUBTUPLE_AT_
	}

	// Fall back to concatenating a singleton tuple.
	override fun o_AppendCanDestroy(
		self: AvailObject,
		newElement: A_BasicObject,
		canDestroy: Boolean): A_Tuple =
			concatenateAtLeastOneTree(self, tuple(newElement), canDestroy)

	/**
	 * Answer approximately how many bits per entry are taken up by this object.
	 *
	 * Make this always seem a little worse than flat representations.
	 */
	override fun o_BitsPerEntry(self: AvailObject): Int = 65

	override fun o_ChildAt(self: AvailObject, childIndex: Int): A_Tuple =
		self.slot(SUBTUPLE_AT_, childIndex)

	override fun o_ChildCount(self: AvailObject): Int =
		self.variableObjectSlotsCount()

	/**
	 * {@inheritDoc}
	 *
	 * Compare a subrange of this tree tuple with a subrange of the given tuple.
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
		if (level < anotherObject.treeTupleLevel)
		{
			// The argument is deeper, so follow its structure to increase the
			// chance that common substructures will be found (and merged).
			return anotherObject.compareFromToWithStartingAt(
				startIndex2,
				endIndex1 + startIndex2 - startIndex1,
				self,
				startIndex1)
		}
		val startChildIndex = childSubscriptForIndex(self, startIndex1)
		val endChildIndex = childSubscriptForIndex(self, endIndex1)
		for (childIndex in startChildIndex until endChildIndex)
		{
			val child: A_Tuple = self.slot(SUBTUPLE_AT_, childIndex)
			val childOffset = offsetForChildSubscript(self, childIndex)
			val childSize =
				(self.intSlot(CUMULATIVE_SIZES_AREA_, childIndex) - childOffset)
			val startIndexInChild =
				(startIndex1 - childOffset).coerceAtLeast(1)
			val endIndexInChild =
				(endIndex1 - childOffset).coerceAtMost(childSize)
			if (!child.compareFromToWithStartingAt(
					startIndexInChild,
					endIndexInChild,
					anotherObject,
					startIndexInChild + childOffset - startIndex1 + startIndex2))
			{
				return false
			}
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

	/**
	 * {@inheritDoc}
	 *
	 * Compare a subrange of this splice tuple and a subrange of the given
	 * object tuple.
	 */
	override fun o_CompareFromToWithObjectTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anObjectTuple: A_Tuple,
		startIndex2: Int): Boolean =
			o_CompareFromToWithStartingAt(
				self, startIndex1, endIndex1, anObjectTuple, startIndex2)

	/**
	 * Hash part of the tuple object.
	 */
	override fun o_ComputeHashFromTo(
		self: AvailObject,
		start: Int,
		end: Int): Int
	{
		val tupleSize = self.tupleSize
		assert(start in 1..tupleSize)
		assert(end in start - 1..tupleSize)
		if (end == 0)
		{
			assert(start == 1)
			return 0
		}
		// Non-empty range, so start and end are both within range.
		val startChildSubscript = childSubscriptForIndex(self, start)
		val endChildSubscript = childSubscriptForIndex(self, end)
		var hash = 0
		for (i in startChildSubscript .. endChildSubscript)
		{
			// At least one element of this child is involved in the hash.
			val startOfChild = offsetForChildSubscript(self, i) + 1
			val endOfChild =
				self.intSlot(CUMULATIVE_SIZES_AREA_, i)
			val startIndexInChild = max(0, start - startOfChild) + 1
			val endIndexInChild = min(endOfChild, end) - startOfChild + 1
			val child: A_Tuple = self.slot(SUBTUPLE_AT_, i)
			var sectionHash =
				child.hashFromTo(startIndexInChild, endIndexInChild)
			val indexAdjustment = startOfChild + startIndexInChild - 2
			sectionHash *= multiplierRaisedTo(indexAdjustment)
			hash += sectionHash
		}
		return hash
	}

	override fun o_ConcatenateWith(
		self: AvailObject,
		otherTuple: A_Tuple,
		canDestroy: Boolean
	): A_Tuple = concatenateAtLeastOneTree(self, otherTuple, canDestroy)

	/**
	 * Make a tuple that only contains the given range of elements of the given
	 * tuple.  Optimized here to take advantage of the tree structure.
	 */
	override fun o_CopyTupleFromToCanDestroy(
		self: AvailObject,
		start: Int,
		end: Int,
		canDestroy: Boolean): A_Tuple
	{
		val tupleSize = self.tupleSize
		assert(start in 1..end + 1 && end <= tupleSize)
		if (start - 1 == end)
		{
			return emptyTuple
		}
		if (!canDestroy)
		{
			self.makeImmutable()
		}
		if (start == 1 && end == self.tupleSize)
		{
			return self
		}
		val lowChildIndex = childSubscriptForIndex(self, start)
		val highChildIndex = childSubscriptForIndex(self, end)
		if (lowChildIndex == highChildIndex)
		{
			// Starts and ends in the same child.  Pass the buck downwards.
			val offset = offsetForChildSubscript(self, lowChildIndex)
			return self.childAt(lowChildIndex).copyTupleFromToCanDestroy(
				start - offset,
				end - offset,
				canDestroy)
		}
		assert(lowChildIndex < highChildIndex)
		// The endpoints occur in distinct children.  Splice together the left
		// fractional child, the right fractional child, and any complete
		// children that may occur between them.
		val leftOffset = offsetForChildSubscript(self, lowChildIndex)
		val rightOffset = offsetForChildSubscript(self, highChildIndex)
		val leftPart =
			self.childAt(lowChildIndex).copyTupleFromToCanDestroy(
				start - leftOffset,
				offsetForChildSubscript(
					self, lowChildIndex + 1) - leftOffset,
				canDestroy)
		val rightPart =
			self.childAt(highChildIndex).copyTupleFromToCanDestroy(
				1,
				end - rightOffset,
				canDestroy)
		var accumulator = leftPart
		if (lowChildIndex + 5 < highChildIndex)
		{
			// There are enough inner children that we can add them more
			// efficiently in a bunch than one at a time.
			val innerSection = createUninitializedTree(
				level, highChildIndex - lowChildIndex - 1)
			val delta =
				self.intSlot(CUMULATIVE_SIZES_AREA_, lowChildIndex)
			var dest = 1
			var src = lowChildIndex + 1
			while (src < highChildIndex)
			{
				val completeChild: A_Tuple = self.slot(SUBTUPLE_AT_, src)
				innerSection.setSlot(SUBTUPLE_AT_, dest, completeChild)
				innerSection.setIntSlot(
					CUMULATIVE_SIZES_AREA_,
					dest,
					self.intSlot(CUMULATIVE_SIZES_AREA_, src) - delta)
				src++
				dest++
			}
			assert(dest == innerSection.childCount + 1)
			innerSection.setSlot(HASH_OR_ZERO, 0)
			check(innerSection)
			accumulator = accumulator.concatenateWith(innerSection, true)
		}
		else
		{
			// There are few enough inner children that we can add them one at
			// a time.
			for (childIndex in lowChildIndex + 1 until highChildIndex)
			{
				accumulator = accumulator.concatenateWith(
					self.slot(SUBTUPLE_AT_, childIndex),
					true)
			}
		}
		accumulator = accumulator.concatenateWith(rightPart, true)
		return accumulator
	}

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsAnyTuple(self)

	override fun o_EqualsAnyTuple(
		self: AvailObject,
		aTuple: A_Tuple): Boolean
	{
		// The other tuple has a deeper structure.  Break the other tuple
		// down for the comparison to increase the chance that common
		// subtuples will be discovered (and skipped/coalesced).
		return when
		{
			self.sameAddressAs(aTuple) -> true
			self.tupleSize != aTuple.tupleSize -> false
			self.hash() != aTuple.hash() -> false
			level < aTuple.treeTupleLevel ->
			{
				// The other tuple has a deeper structure.  Break the other
				// tuple down for the comparison to increase the chance that
				// common subtuples will be discovered (and skipped/coalesced).
				aTuple.equalsAnyTuple(self)
			}
			else ->
				self.compareFromToWithStartingAt(1, self.tupleSize, aTuple, 1)
		}
	}

	override fun o_ReplaceFirstChild(
		self: AvailObject,
		newFirst: A_Tuple): A_Tuple
	{
		assert(newFirst.treeTupleLevel == level - 1)
		val oldChild: A_Tuple = self.slot(SUBTUPLE_AT_, 1)
		val replacementSize = newFirst.tupleSize
		val delta = replacementSize - oldChild.tupleSize
		val result =
			if (isMutable) self
			else newLike(mutable(), self, 0, 0)
		val oldHash = self.slot(HASH_OR_ZERO)
		if (oldHash != 0)
		{
			val hashMinusFirstChild = oldHash - oldChild.hash()
			val rescaledHashWithoutFirstChild =
				hashMinusFirstChild * multiplierRaisedTo(delta)
			val newHash = rescaledHashWithoutFirstChild + newFirst.hash()
			result.setSlot(HASH_OR_ZERO, newHash)
		}
		result.setSlot(SUBTUPLE_AT_, 1, newFirst)
		val childCount = self.childCount
		if (delta != 0)
		{
			for (childIndex in 1 .. childCount)
			{
				result.setIntSlot(
					CUMULATIVE_SIZES_AREA_,
					childIndex,
					result.intSlot(CUMULATIVE_SIZES_AREA_, childIndex) + delta)
			}
		}
		check(result)
		return result
	}

	override fun o_TransferIntoByteBuffer(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int,
		outputByteBuffer: ByteBuffer)
	{
		val lowChildIndex = childSubscriptForIndex(self, startIndex)
		val highChildIndex = childSubscriptForIndex(self, endIndex)
		if (lowChildIndex == highChildIndex)
		{
			// Starts and ends in the same child.  Pass the buck downwards.
			val offset = offsetForChildSubscript(self, lowChildIndex)
			self.childAt(lowChildIndex).transferIntoByteBuffer(
				startIndex - offset,
				endIndex - offset,
				outputByteBuffer)
			return
		}
		assert(lowChildIndex < highChildIndex)
		// The endpoints occur in distinct children.
		val leftOffset = offsetForChildSubscript(self, lowChildIndex)
		var child = self.childAt(lowChildIndex)
		child.transferIntoByteBuffer(
			startIndex - leftOffset,
			child.tupleSize,
			outputByteBuffer)
		for (childIndex in lowChildIndex + 1 until highChildIndex)
		{
			child = self.childAt(childIndex)
			child.transferIntoByteBuffer(
				1, child.tupleSize, outputByteBuffer)
		}
		child = self.childAt(highChildIndex)
		val rightOffset = offsetForChildSubscript(self, highChildIndex)
		child.transferIntoByteBuffer(
			1,
			endIndex - rightOffset,
			outputByteBuffer)
	}

	override fun o_TreeTupleLevel(self: AvailObject): Int = level

	/**
	 * Answer the element at the given index in the tuple object.
	 */
	override fun o_TupleAt(self: AvailObject, index: Int): AvailObject
	{
		val childSubscript = childSubscriptForIndex(self, index)
		val offset = offsetForChildSubscript(self, childSubscript)
		val child: A_Tuple = self.slot(SUBTUPLE_AT_, childSubscript)
		return child.tupleAt(index - offset)
	}

	/**
	 * Answer a tuple with all the elements of object except at the given index
	 * we should have newValueObject.  This may destroy the original tuple if
	 * canDestroy is true.
	 */
	override fun o_TupleAtPuttingCanDestroy(
		self: AvailObject,
		index: Int,
		newValueObject: A_BasicObject,
		canDestroy: Boolean): A_Tuple
	{
		assert(index >= 1 && index <= self.tupleSize)
		var result = self
		if (!(canDestroy && isMutable))
		{
			result = newLike(mutable(), self, 0, 0)
		}
		val subtupleSubscript = childSubscriptForIndex(self, index)
		val oldSubtuple: A_Tuple =
			self.slot(SUBTUPLE_AT_, subtupleSubscript)
		val delta = offsetForChildSubscript(self, subtupleSubscript)
		val oldHash = self.slot(HASH_OR_ZERO)
		if (oldHash != 0)
		{
			// Maintain the already-computed hash.
			val oldValue: A_BasicObject = oldSubtuple.tupleAt(index - delta)
			val adjustment = newValueObject.hash() - oldValue.hash()
			val scaledAdjustment = adjustment * multiplierRaisedTo(index)
			result.setSlot(
				HASH_OR_ZERO, oldHash + scaledAdjustment)
		}
		val newSubtuple = oldSubtuple.tupleAtPuttingCanDestroy(
			index - delta, newValueObject, canDestroy)
		val newLevel = newSubtuple.treeTupleLevel
		if (newLevel == level - 1)
		{
			// Most common case
			result.setSlot(
				SUBTUPLE_AT_, subtupleSubscript, newSubtuple)
			check(result)
			return result
		}
		// The replacement in the subtuple changed its height.  We'll have to
		// use general concatenation to combine the section of the original
		// tuple left of the subtuple, the newSubtuple, and the section to the
		// right of the subtuple.  Don't allow the left and right extractions to
		// be destructive, since we mustn't modify the tuple between the
		// extractions.
		val leftPart = self.copyTupleFromToCanDestroy(
			1, delta, false)
		val rightPart = self.copyTupleFromToCanDestroy(
			offsetForChildSubscript(self, subtupleSubscript + 1) + 1,
			self.tupleSize,
			false)
		val leftAndMiddle = leftPart.concatenateWith(newSubtuple, true)
		return leftAndMiddle.concatenateWith(rightPart, true)
	}

	override fun o_TupleElementsInRangeAreInstancesOf(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int,
		type: A_Type): Boolean
	{
		val tupleSize = self.tupleSize
		assert(startIndex in 1..tupleSize)
		assert(endIndex in startIndex - 1..tupleSize)
		if (endIndex == startIndex - 1)
		{
			return true
		}
		// Non-empty range, so start and end are both within range.
		val startChildSubscript = childSubscriptForIndex(self, startIndex)
		val endChildSubscript = childSubscriptForIndex(self, endIndex)
		for (i in startChildSubscript .. endChildSubscript)
		{
			// At least one element of this child is involved in the hash.
			val startOfChild = offsetForChildSubscript(self, i) + 1
			val endOfChild =
				self.intSlot(CUMULATIVE_SIZES_AREA_, i)
			val startIndexInChild =
				max(0, startIndex - startOfChild) + 1
			val endIndexInChild = min(endOfChild, endIndex) - startOfChild + 1
			val child: A_Tuple = self.slot(SUBTUPLE_AT_, i)
			if (!child.tupleElementsInRangeAreInstancesOf(
					startIndexInChild, endIndexInChild, type))
			{
				return false
			}
		}
		return true
	}

	override fun o_TupleIntAt(self: AvailObject, index: Int): Int
	{
		val childSubscript = childSubscriptForIndex(self, index)
		val offset = offsetForChildSubscript(self, childSubscript)
		val child: A_Tuple = self.slot(SUBTUPLE_AT_, childSubscript)
		return child.tupleIntAt(index - offset)
	}

	override fun o_TupleLongAt(self: AvailObject, index: Int): Long
	{
		val childSubscript = childSubscriptForIndex(self, index)
		val offset = offsetForChildSubscript(self, childSubscript)
		val child: A_Tuple = self.slot(SUBTUPLE_AT_, childSubscript)
		return child.tupleLongAt(index - offset)
	}

	/**
	 * Answer the number of elements in the tuple as an int.
	 */
	override fun o_TupleSize(self: AvailObject): Int =
		self.intSlot(
			CUMULATIVE_SIZES_AREA_, self.variableObjectSlotsCount())

	companion object
	{
		/**
		 * Concatenate two tree tuples that are at the same level.  Destroy or
		 * reuse both tuples if they're mutable.
		 *
		 * @param tuple1
		 *   The left tuple.
		 * @param tuple2
		 *   The right tuple.
		 * @return
		 *   A tuple containing the left tuple's elements followed by the right
		 *   tuple's elements.
		 */
		private fun concatenateSameLevel(
			tuple1: A_Tuple,
			tuple2: A_Tuple): AvailObject
		{
			val level = tuple1.treeTupleLevel
			assert(level == tuple2.treeTupleLevel)
			val count1 = tuple1.childCount
			val count2 = tuple2.childCount
			// First work out the resulting hash if it's inexpensive.
			val newHash =
				(if (tuple1.hashOrZero() == 0 || tuple2.hashOrZero() == 0) 0
				else tuple1.hashOrZero()) + (tuple2.hashOrZero()
					* multiplierRaisedTo(tuple1.tupleSize))
			if (count1 >= minWidthOfNonRoot && count2 >= minWidthOfNonRoot)
			{
				// They're each big enough to be non-roots.  Do the cheapest
				// thing and create a 2-way node holding both.
				return createTwoPartTreeTuple(tuple1, tuple2, level + 1, newHash)
			}
			if (count1 + count2 <= maxWidth)
			{
				// Fits in a single node.
				val newNode = newLike(
					tuple1.descriptor().mutable(),
					(tuple1 as AvailObject),
					count2,
					(count1 + count2 + 1 shr 1) - (count1 + 1 shr 1))
				var size = tuple1.tupleSize
				var dest = count1 + 1
				var src = 1
				while (src <= count2)
				{
					val child = tuple2.childAt(src)
					newNode.setSlot(SUBTUPLE_AT_, dest, child)
					size += child.tupleSize
					newNode.setIntSlot(
						CUMULATIVE_SIZES_AREA_, dest, size)
					src++
					dest++
				}
				newNode.setSlot(HASH_OR_ZERO, newHash)
				check(newNode)
				return newNode
			}
			// Must divide the children (evenly) into two nodes, then introduce
			// a 2-way node holding both.
			val totalCount = count1 + count2
			val leftCount = totalCount + 1 shr 1
			val rightCount = totalCount - leftCount
			val newLeft = createUninitializedTree(level, leftCount)
			val newRight = createUninitializedTree(level, rightCount)
			var target = newLeft
			var destLimit = leftCount
			var size = 0
			var dest = 1
			for (whichSource in 1 .. 2)
			{
				val source = if (whichSource == 1) tuple1 else tuple2
				val limit = if (whichSource == 1) count1 else count2
				var src = 1
				while (src <= limit)
				{
					val child = source.childAt(src)
					if (dest > destLimit)
					{
						assert(target === newLeft)
						target = newRight
						destLimit = rightCount
						dest = 1
						size = 0
					}
					size += child.tupleSize
					target.setSlot(SUBTUPLE_AT_, dest, child)
					target.setIntSlot(CUMULATIVE_SIZES_AREA_, dest, size)
					src++
					dest++
				}
			}
			assert(target === newRight)
			assert(dest == destLimit + 1)
			check(target)
			// And tape them back together.
			return createTwoPartTreeTuple(newLeft, newRight, level + 1, newHash)
		}

		/**
		 * Concatenate the two tuples together.  At least one of them must be a
		 * tree tuple.
		 *
		 * @param tuple1
		 * The first tuple.
		 * @param tuple2
		 * The second tuple.
		 * @param canDestroy
		 * Whether one of the tuples is allowed to be destroyed if it's also mutable.
		 * @return
		 * The concatenated tuple, perhaps having destroyed or recycled one of the input tuples if permitted.
		 */
		fun concatenateAtLeastOneTree(
			tuple1: AvailObject,
			tuple2: A_Tuple,
			canDestroy: Boolean): A_Tuple
		{
			val size1 = tuple1.tupleSize
			val size2 = tuple2.tupleSize
			val level1 = tuple1.treeTupleLevel
			val level2 = tuple2.treeTupleLevel
			assert(level1 > 0 || level2 > 0)
			if (!canDestroy)
			{
				tuple1.makeImmutable()
				tuple2.makeImmutable()
			}
			if (level1 == level2)
			{
				return concatenateSameLevel(tuple1, tuple2)
			}
			// Work out the resulting hash if it's inexpensive.
			var newHash = 0
			val h1 = tuple1.hashOrZero()
			if (h1 != 0)
			{
				val h2 = tuple2.hashOrZero()
				if (h2 != 0)
				{
					newHash = h1 + h2 * multiplierRaisedTo(size1)
				}
			}
			if (level1 > level2)
			{
				val childCount1 = tuple1.childCount
				val oldLast = tuple1.childAt(childCount1)
				val newLast = oldLast.concatenateWith(tuple2, true)
				if (newLast.treeTupleLevel == level1)
				{
					// Last child overflowed.  Combine myself minus the last, with
					// the new peer.  Be careful of the int-packed cumulative sizes
					// area.
					val withoutLast = newLike(
						descriptors[MUTABLE]!![level1],
						tuple1,
						-1,
						-(childCount1 and 1))
					if (childCount1 and 1 == 0)
					{
						// Be tidy.
						withoutLast.setIntSlot(
							CUMULATIVE_SIZES_AREA_, childCount1, 0)
					}
					withoutLast.setSlot(HASH_OR_ZERO, 0)
					return concatenateSameLevel(withoutLast, newLast)
				}
				assert(newLast.treeTupleLevel == level1 - 1)
				// Replace the last child.  In place if possible.
				val result =
					if (canDestroy && tuple1.descriptor().isMutable)
					{
						tuple1
					}
					else
					{
						newLike(descriptors[MUTABLE]!![level1], tuple1, 0, 0)
					}
				result.setSlot(SUBTUPLE_AT_, childCount1, newLast)
				result.setIntSlot(
					CUMULATIVE_SIZES_AREA_,
					childCount1,
					tuple1.intSlot(
						CUMULATIVE_SIZES_AREA_, childCount1) + size2)
				result.setSlot(HASH_OR_ZERO, newHash)
				check(result)
				return result
			}
			assert(level1 < level2)
			val childCount2 = tuple2.childCount
			val oldFirst = tuple2.childAt(1)
			// Don't allow oldFirst to be clobbered, otherwise tuple2 will have
			// incorrect cumulative sizes.
			oldFirst.makeImmutable()
			val newFirst = tuple1.concatenateWith(oldFirst, true)
			if (newFirst.treeTupleLevel == level2)
			{
				// First child overflowed.  Combine this new peer with the other
				// tuple, minus its first child.
				val withoutFirst =
					createUninitializedTree(level2, childCount2 - 1)
				var size = 0
				for (src in 2 .. childCount2)
				{
					val child = tuple2.childAt(src)
					size += child.tupleSize
					withoutFirst.setSlot(
						SUBTUPLE_AT_, src - 1, child)
					withoutFirst.setIntSlot(
						CUMULATIVE_SIZES_AREA_, src - 1, size)
				}
				withoutFirst.setSlot(HASH_OR_ZERO, 0)
				check(withoutFirst)
				return concatenateSameLevel(newFirst, withoutFirst)
			}
			assert(newFirst.treeTupleLevel == level2 - 1)
			// Replace the first child of other.
			return tuple2.replaceFirstChild(newFirst)
		}

		/**
		 * Answer the one-based subscript into the 32-bit int fields of
		 * [IntegerSlots.CUMULATIVE_SIZES_AREA_], in which the specified tuple
		 * index occurs.
		 *
		 * @param self
		 *   The tree tuple node to search.
		 * @param index
		 *   The 1-based tuple index to search for.
		 * @return
		 *   The 1-based subscript of the subtuple containing the tuple index.
		 */
		private fun childSubscriptForIndex(
			self: AvailObject,
			index: Int): Int
		{
			val childCount = self.variableObjectSlotsCount()
			assert(index >= 1)
			assert(index <= self.intSlot(CUMULATIVE_SIZES_AREA_, childCount))
			val childSlotIndex =
				self.intBinarySearch(
					CUMULATIVE_SIZES_AREA_, childCount, index)
			return if (childSlotIndex >= 0)
			{
				childSlotIndex
			}
			else
			{
				childSlotIndex.inv()
			} - CUMULATIVE_SIZES_AREA_.ordinal + 2
		}

		/**
		 * When enabled, extra safety checks are run to ensure tree-tuples are
		 * working correctly.
		 */
		private const val shouldCheckTreeTuple = false

		/**
		 * Answer one less than the first one-based index of elements that fall
		 * within the subtuple with the specified childSubscript.  This is the
		 * difference between the coordinate system of the tuple and the
		 * coordinate system of the subtuple.
		 *
		 * @param self
		 *   The tuple.
		 * @param childSubscript
		 *   Which subtuple to transform an index into.
		 * @return
		 *   How much to subtract to go from an index into the tuple to an index
		 *   into the subtuple.
		 */
		private fun offsetForChildSubscript(
			self: AvailObject,
			childSubscript: Int
		): Int = when (childSubscript)
		{
			1 -> 0
			else -> self.intSlot(CUMULATIVE_SIZES_AREA_, childSubscript - 1)
		}

		/**
		 * Perform a sanity check on the passed tree tuple.  Fail if it's
		 * invalid.
		 *
		 * @param self
		 *   The tree tuple to check.
		 */
		private fun check(self: AvailObject)
		{
			if (!shouldCheckTreeTuple) return

			assert(self.descriptor() is TreeTupleDescriptor)
			assert(self.variableObjectSlotsCount() + 1 shr 1
						== self.variableIntegerSlotsCount())
			val myLevelMinusOne = self.treeTupleLevel - 1
			val childCount = self.childCount
			var cumulativeSize = 0
			for (childIndex in 1 .. childCount)
			{
				val child: A_Tuple =
					self.slot(SUBTUPLE_AT_, childIndex)
				assert(child.treeTupleLevel == myLevelMinusOne)
				assert(myLevelMinusOne == 0
						|| child.childCount >= minWidthOfNonRoot)
				val childSize = child.tupleSize
				assert(childSize > 0)
				cumulativeSize += childSize
				assert(self.intSlot(CUMULATIVE_SIZES_AREA_, childIndex)
					== cumulativeSize)
			}
		}

		/**
		 * Create a new tree tuple with the given level.  The client is
		 * responsible for setting the bin elements and updating the hash and
		 * tuple size.
		 *
		 * @param level
		 *   The tree level at which this hashed bin occurs.
		 * @param size
		 *   The number of children this tree tuple should have.
		 * @return
		 *   A new tree tuple with uninitialized
		 *   [subtuple][ObjectSlots.SUBTUPLE_AT_] slots and
		 *   [cumulative&#32;size][IntegerSlots.CUMULATIVE_SIZES_AREA_] slots.
		 */
		private fun createUninitializedTree(level: Int, size: Int): AvailObject
		{
			val instance = newObjectIndexedIntegerIndexedDescriptor(
				size,
				size + 1 shr 1,
				descriptors[MUTABLE]!![level])
			instance.setSlot(HASH_OR_ZERO, 0)
			return instance
		}

		/**
		 * Create a 2-child tree tuple at the specified level.  The children
		 * must both be at newLevel - 1.  Neither may be empty.
		 *
		 * @param left
		 *   The left child.
		 * @param right
		 *   The right child.
		 * @param newLevel
		 *   The level at which to create a new node.
		 * @param newHashOrZero
		 *   The new hash, or zero if inconvenient to produce.
		 * @return
		 *   A new tree tuple at newLevel.
		 */
		fun createTwoPartTreeTuple(
			left: A_Tuple,
			right: A_Tuple,
			newLevel: Int,
			newHashOrZero: Int): AvailObject
		{
			assert(left.treeTupleLevel == newLevel - 1)
			assert(right.treeTupleLevel == newLevel - 1)
			assert(left.tupleSize > 0)
			assert(right.tupleSize > 0)
			val newNode = createUninitializedTree(newLevel, 2)
			newNode.setSlot(SUBTUPLE_AT_, 1, left)
			newNode.setSlot(SUBTUPLE_AT_, 2, right)
			newNode.setIntSlot(CUMULATIVE_SIZES_AREA_, 1, left.tupleSize)
			newNode.setIntSlot(
				CUMULATIVE_SIZES_AREA_,
				2,
				left.tupleSize +
				right.tupleSize)
			newNode.setSlot(HASH_OR_ZERO, newHashOrZero)
			check(newNode)
			return newNode
		}

		/**
		 * Reverse each child of this tree, then assemble them in reverse order
		 * into a new tree.  Not that reversing each child may also have to
		 * recursively navigate more levels of tree.
		 *
		 * @param self
		 *   The tree tuple to reverse.
		 * @return
		 *   A tree tuple that is the reverse of the given tree tuple.
		 */
		fun internalTreeReverse(self: AvailObject): AvailObject
		{
			val childCount = self.childCount
			val newTree =
				createUninitializedTree(self.treeTupleLevel, childCount)
			var cumulativeSize = 0
			var src = childCount
			var dest = 1
			while (src > 0)
			{
				val child = self.childAt(src)
				newTree.setSlot(
					SUBTUPLE_AT_, dest, child.tupleReverse())
				cumulativeSize += child.tupleSize
				newTree.setIntSlot(CUMULATIVE_SIZES_AREA_, dest, cumulativeSize)
				src--
				dest++
			}
			assert(cumulativeSize == self.tupleSize)
			newTree.setSlot(HASH_OR_ZERO, 0)
			return newTree
		}

		/**
		 * Answer the minimum number of children a non-root tree tuple may have.
		 */
		private const val minWidthOfNonRoot = 16

		/**
		 * Answer the maximum number of children a tree tuple node may have.
		 */
		private const val maxWidth = 64

		/**
		 * The number of distinct levels that my instances can occupy in a tree
		 * tuple.
		 */
		private const val numberOfLevels = 10

		/**
		 * The [TreeTupleDescriptor]s, organized by mutability then level.
		 */
		val descriptors = enumMap { mut: Mutability ->
			Array(numberOfLevels) { level -> TreeTupleDescriptor(mut, level) }
		}
	}

	override fun mutable(): TreeTupleDescriptor = descriptors[MUTABLE]!![level]

	override fun immutable(): TreeTupleDescriptor =
		descriptors[IMMUTABLE]!![level]

	override fun shared(): TreeTupleDescriptor = descriptors[SHARED]!![level]
}
