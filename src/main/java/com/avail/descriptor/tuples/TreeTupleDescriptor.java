/*
 * TreeTupleDescriptor.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
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

package com.avail.descriptor.tuples;

import com.avail.annotations.AvailMethod;
import com.avail.annotations.HideFieldInDebugger;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.JavaCompatibility.IntegerSlotsEnumJava;
import com.avail.descriptor.JavaCompatibility.ObjectSlotsEnumJava;
import com.avail.descriptor.representation.A_BasicObject;
import com.avail.descriptor.representation.BitField;
import com.avail.descriptor.representation.Mutability;
import com.avail.descriptor.types.A_Type;

import java.nio.ByteBuffer;

import static com.avail.descriptor.AvailObject.newLike;
import static com.avail.descriptor.AvailObject.newObjectIndexedIntegerIndexedDescriptor;
import static com.avail.descriptor.representation.Mutability.*;
import static com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.tuples.TreeTupleDescriptor.IntegerSlots.CUMULATIVE_SIZES_AREA_;
import static com.avail.descriptor.tuples.TreeTupleDescriptor.IntegerSlots.HASH_OR_ZERO;
import static com.avail.descriptor.tuples.TreeTupleDescriptor.ObjectSlots.SUBTUPLE_AT_;
import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * A tree tuple is a tuple organized as a constant height tree, similar to the
 * well known B-Tree family, but without the requirement to fit the nodes onto
 * a small number of disk pages.  Instead of the hundreds or thousands of
 * children that B-Tree nodes have, the tree tuple nodes have between
 * {@linkplain #minWidthOfNonRoot 16} and {@linkplain #maxWidth 64} children,
 * except the root which may have as few as 2.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class TreeTupleDescriptor
extends TupleDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots implements IntegerSlotsEnumJava
	{
		/**
		 * The low 32 bits are used for the {@link #HASH_OR_ZERO}, but the upper
		 * 32 can be used by other {@link BitField}s in subclasses of {@link
		 * TupleDescriptor}.
		 */
		@HideFieldInDebugger
		HASH_AND_MORE,

		/**
		 * The number of elements in the 1<sup>st</sup> through N<sup>th</sup>
		 * subtuples, as {@code int} slots.
		 */
		CUMULATIVE_SIZES_AREA_;

		/**
		 * The hash value of this tree tuple, or zero.  If the hash value
		 * happens to equal zero it will have to be recomputed each time it is
		 * requested.  Note that the hash function for tuples was chosen in such
		 * a way that the hash value of the concatenation of subtuples is easily
		 * computable from the hashes of the subtuples and their lengths.
		 */
		public static final BitField HASH_OR_ZERO =
			new BitField(HASH_AND_MORE, 0, 32);

		static
		{
			assert TupleDescriptor.IntegerSlots.HASH_AND_MORE.ordinal()
				== HASH_AND_MORE.ordinal();
			assert TupleDescriptor.IntegerSlots.HASH_OR_ZERO.isSamePlaceAs(
				HASH_OR_ZERO);
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnumJava
	{
		/**
		 * The subtuples of which this tree tuple is composed.  The subtuples
		 * of a level=1 tree tuple must be flat or subrange tuples, but the
		 * subtuples of a level=N>1 tree tuple must be level=N-1 tree tuples,
		 * each containing at least {@link #minWidthOfNonRoot}.
		 */
		SUBTUPLE_AT_;
	}

	@Override @AvailMethod
	protected A_Tuple o_AppendCanDestroy (
		final AvailObject object,
		final A_BasicObject newElement,
		final boolean canDestroy)
	{
		// Fall back to concatenating a singleton tuple.
		final A_Tuple singleton = tuple(newElement);
		return concatenateAtLeastOneTree(object, singleton, canDestroy);
	}

	/**
	 * Answer approximately how many bits per entry are taken up by this object.
	 *
	 * <p>Make this always seem a little worse than flat representations.</p>
	 */
	@Override @AvailMethod
	protected int o_BitsPerEntry (final AvailObject object)
	{
		return 65;
	}

	@Override @AvailMethod
	protected A_Tuple o_ChildAt (final AvailObject object, final int childIndex)
	{
		return object.slot(SUBTUPLE_AT_, childIndex);
	}

	@Override @AvailMethod
	protected int o_ChildCount (final AvailObject object)
	{
		return object.variableObjectSlotsCount();
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Compare a subrange of this tree tuple with a subrange of the given tuple.
	 * </p>
	 */
	@Override @AvailMethod
	protected boolean o_CompareFromToWithStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anotherObject,
		final int startIndex2)
	{
		if (object.sameAddressAs(anotherObject) && startIndex1 == startIndex2)
		{
			return true;
		}
		if (level < anotherObject.treeTupleLevel())
		{
			// The argument is deeper, so follow its structure to increase the
			// chance that common substructures will be found (and merged).
			return anotherObject.compareFromToWithStartingAt(
				startIndex2,
				endIndex1 + startIndex2 - startIndex1,
				object,
				startIndex1);
		}
		final int startChildIndex = childSubscriptForIndex(object, startIndex1);
		final int endChildIndex = childSubscriptForIndex(object, endIndex1);
		for (
			int childIndex = startChildIndex;
			childIndex < endChildIndex;
			childIndex++)
		{
			final A_Tuple child = object.slot(SUBTUPLE_AT_, childIndex);
			final int childOffset = offsetForChildSubscript(object, childIndex);
			final int childSize = object.intSlot(
					CUMULATIVE_SIZES_AREA_, childIndex)
				- childOffset;
			final int startIndexInChild = max(startIndex1 - childOffset, 1);
			final int endIndexInChild = min(endIndex1 - childOffset, childSize);
			if (!child.compareFromToWithStartingAt(
				startIndexInChild,
				endIndexInChild,
				anotherObject,
				startIndexInChild + childOffset - startIndex1 + startIndex2))
			{
				return false;
			}
		}
		if (startIndex1 == 1
			&& startIndex2 == 1
			&& endIndex1 == object.tupleSize()
			&& endIndex1 == anotherObject.tupleSize())
		{
			if (!isShared())
			{
				anotherObject.makeImmutable();
				object.becomeIndirectionTo(anotherObject);
			}
			else if (!anotherObject.descriptor().isShared())
			{
				object.makeImmutable();
				anotherObject.becomeIndirectionTo(object);
			}
		}
		return true;
	}

	@Override @AvailMethod
	protected boolean o_CompareFromToWithByteStringStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_String aByteString,
		final int startIndex2)
	{
		return o_CompareFromToWithStartingAt(
			object, startIndex1, endIndex1, aByteString, startIndex2);
	}

	@Override @AvailMethod
	protected boolean o_CompareFromToWithByteTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aByteTuple,
		final int startIndex2)
	{
		return o_CompareFromToWithStartingAt(
			object, startIndex1, endIndex1, aByteTuple, startIndex2);
	}

	@Override @AvailMethod
	protected boolean o_CompareFromToWithNybbleTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aNybbleTuple,
		final int startIndex2)
	{
		return o_CompareFromToWithStartingAt(
			object, startIndex1, endIndex1, aNybbleTuple, startIndex2);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Compare a subrange of this splice tuple and a subrange of the given
	 * object tuple.
	 * </p>
	 */
	@Override @AvailMethod
	protected boolean o_CompareFromToWithObjectTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anObjectTuple,
		final int startIndex2)
	{
		return o_CompareFromToWithStartingAt(
			object, startIndex1, endIndex1, anObjectTuple, startIndex2);
	}

	/**
	 * Hash part of the tuple object.
	 */
	@Override @AvailMethod
	protected int o_ComputeHashFromTo (
		final AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		final int tupleSize = object.tupleSize();
		assert 1 <= startIndex && startIndex <= tupleSize;
		assert startIndex - 1 <= endIndex && endIndex <= tupleSize;
		if (endIndex == 0)
		{
			assert startIndex == 1;
			return 0;
		}
		// Non-empty range, so start and end are both within range.
		final int startChildSubscript =
			childSubscriptForIndex(object, startIndex);
		final int endChildSubscript =
			childSubscriptForIndex(object, endIndex);
		int hash = 0;
		for (int i = startChildSubscript; i <= endChildSubscript; i++)
		{
			// At least one element of this child is involved in the hash.
			final int startOfChild = offsetForChildSubscript(object, i) + 1;
			final int endOfChild = object.intSlot(CUMULATIVE_SIZES_AREA_, i);
			final int startIndexInChild =
				max(0, startIndex - startOfChild) + 1;
			final int endIndexInChild =
				min(endOfChild, endIndex) - startOfChild + 1;
			final A_Tuple child = object.slot(SUBTUPLE_AT_, i);
			int sectionHash =
				child.hashFromTo(startIndexInChild, endIndexInChild);
			final int indexAdjustment = startOfChild + startIndexInChild - 2;
			sectionHash *= multiplierRaisedTo(indexAdjustment);
			hash += sectionHash;
		}
		return hash;
	}

	@Override @AvailMethod
	protected A_Tuple o_ConcatenateWith (
		final AvailObject object,
		final A_Tuple otherTuple,
		final boolean canDestroy)
	{
		return concatenateAtLeastOneTree(object, otherTuple, canDestroy);
	}

	/**
	 * Make a tuple that only contains the given range of elements of the given
	 * tuple.  Optimized here to take advantage of the tree structure.
	 */
	@Override @AvailMethod
	protected A_Tuple o_CopyTupleFromToCanDestroy (
		final AvailObject object,
		final int start,
		final int end,
		final boolean canDestroy)
	{
		final int tupleSize = object.tupleSize();
		assert 1 <= start && start <= end + 1 && end <= tupleSize;
		if (start - 1 == end)
		{
			return emptyTuple();
		}
		if (!canDestroy)
		{
			object.makeImmutable();
		}
		if (start == 1 && end == object.tupleSize())
		{
			return object;
		}
		final int lowChildIndex = childSubscriptForIndex(object, start);
		final int highChildIndex = childSubscriptForIndex(object, end);
		if (lowChildIndex == highChildIndex)
		{
			// Starts and ends in the same child.  Pass the buck downwards.
			final int offset = offsetForChildSubscript(object, lowChildIndex);
			return object.childAt(lowChildIndex).copyTupleFromToCanDestroy(
				start - offset,
				end - offset,
				canDestroy);
		}
		assert lowChildIndex < highChildIndex;
		// The endpoints occur in distinct children.  Splice together the left
		// fractional child, the right fractional child, and any complete
		// children that may occur between them.
		final int leftOffset =
			offsetForChildSubscript(object, lowChildIndex);
		final int rightOffset =
			offsetForChildSubscript(object, highChildIndex);
		final A_Tuple leftPart =
			object.childAt(lowChildIndex).copyTupleFromToCanDestroy(
				start - leftOffset,
				offsetForChildSubscript(object, lowChildIndex + 1) - leftOffset,
				canDestroy);
		final A_Tuple rightPart =
			object.childAt(highChildIndex).copyTupleFromToCanDestroy(
				1,
				end - rightOffset,
				canDestroy);
		A_Tuple accumulator = leftPart;
		if (lowChildIndex + 5 < highChildIndex)
		{
			// There are enough inner children that we can add them more
			// efficiently in a bunch than one at a time.
			final AvailObject innerSection = createUninitializedTree(
				level, highChildIndex - lowChildIndex - 1);
			final int delta = object.intSlot(
				CUMULATIVE_SIZES_AREA_, lowChildIndex);
			int dest = 1;
			for (
				int src = lowChildIndex + 1;
				src < highChildIndex;
				src++, dest++)
			{
				final A_Tuple completeChild = object.slot(SUBTUPLE_AT_, src);
				innerSection.setSlot(SUBTUPLE_AT_, dest, completeChild);
				innerSection.setIntSlot(
					CUMULATIVE_SIZES_AREA_,
					dest,
					object.intSlot(CUMULATIVE_SIZES_AREA_, src) - delta);
			}
			assert dest == innerSection.childCount() + 1;
			innerSection.setSlot(HASH_OR_ZERO, 0);
			check(innerSection);
			accumulator = accumulator.concatenateWith(innerSection, true);
		}
		else
		{
			// There are few enough inner children that we can add them one at
			// a time.
			for (
				int childIndex = lowChildIndex + 1;
				childIndex < highChildIndex;
				childIndex++)
			{
				accumulator = accumulator.concatenateWith(
					object.slot(SUBTUPLE_AT_, childIndex),
					true);
			}
		}
		accumulator = accumulator.concatenateWith(rightPart, true);
		return accumulator;
	}

	@Override @AvailMethod
	public boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsAnyTuple(object);
	}

	@Override @AvailMethod
	protected boolean o_EqualsAnyTuple (
		final AvailObject object,
		final A_Tuple anotherTuple)
	{
		if (object.sameAddressAs(anotherTuple))
		{
			return true;
		}
		if (object.tupleSize() != anotherTuple.tupleSize())
		{
			return false;
		}
		if (object.hash() != anotherTuple.hash())
		{
			return false;
		}
		if (level < anotherTuple.treeTupleLevel())
		{
			// The other tuple has a deeper structure.  Break the other tuple
			// down for the comparison to increase the chance that common
			// subtuples will be discovered (and skipped/coalesced).
			return anotherTuple.equalsAnyTuple(object);
		}
		return object.compareFromToWithStartingAt(
			1,
			object.tupleSize(),
			anotherTuple,
			1);
	}

	@Override @AvailMethod
	protected A_Tuple o_ReplaceFirstChild (
		final AvailObject object,
		final A_Tuple replacementChild)
	{
		assert replacementChild.treeTupleLevel() == level - 1;
		final A_Tuple oldChild = object.slot(SUBTUPLE_AT_, 1);
		final int replacementSize = replacementChild.tupleSize();
		final int delta = replacementSize - oldChild.tupleSize();
		final AvailObject result = isMutable()
			? object
			: newLike(mutable(), object, 0, 0);
		final int oldHash = object.slot(HASH_OR_ZERO);
		if (oldHash != 0)
		{
			final int hashMinusFirstChild = oldHash - oldChild.hash();
			final int rescaledHashWithoutFirstChild =
				hashMinusFirstChild * multiplierRaisedTo(delta);
			final int newHash =
				rescaledHashWithoutFirstChild + replacementChild.hash();
			result.setSlot(HASH_OR_ZERO, newHash);
		}
		result.setSlot(SUBTUPLE_AT_, 1, replacementChild);
		final int childCount = object.childCount();
		if (delta != 0)
		{
			for (int childIndex = 1; childIndex <= childCount; childIndex++)
			{
				result.setIntSlot(
					CUMULATIVE_SIZES_AREA_,
					childIndex,
					result.intSlot(CUMULATIVE_SIZES_AREA_, childIndex) + delta);
			}
		}
		check(result);
		return result;
	}

	@Override
	protected void o_TransferIntoByteBuffer (
		final AvailObject object,
		final int startIndex,
		final int endIndex,
		final ByteBuffer outputByteBuffer)
	{
		final int lowChildIndex = childSubscriptForIndex(object, startIndex);
		final int highChildIndex = childSubscriptForIndex(object, endIndex);
		if (lowChildIndex == highChildIndex)
		{
			// Starts and ends in the same child.  Pass the buck downwards.
			final int offset = offsetForChildSubscript(object, lowChildIndex);
			object.childAt(lowChildIndex).transferIntoByteBuffer(
				startIndex - offset,
				endIndex - offset,
				outputByteBuffer);
			return;
		}
		assert lowChildIndex < highChildIndex;
		// The endpoints occur in distinct children.
		final int leftOffset = offsetForChildSubscript(object, lowChildIndex);
		A_Tuple child = object.childAt(lowChildIndex);
		child.transferIntoByteBuffer(
			startIndex - leftOffset,
			child.tupleSize(),
			outputByteBuffer);
		for (
			int childIndex = lowChildIndex + 1;
			childIndex < highChildIndex;
			childIndex ++)
		{
			child = object.childAt(childIndex);
			child.transferIntoByteBuffer(
				1, child.tupleSize(), outputByteBuffer);
		}
		child = object.childAt(highChildIndex);
		final int rightOffset = offsetForChildSubscript(object, highChildIndex);
		child.transferIntoByteBuffer(
			1,
			endIndex - rightOffset,
			outputByteBuffer);
	}

	@Override @AvailMethod
	protected int o_TreeTupleLevel (final AvailObject object)
	{
		return level;
	}

	/**
	 * Answer the element at the given index in the tuple object.
	 */
	@Override @AvailMethod
	protected AvailObject o_TupleAt (final AvailObject object, final int index)
	{
		final int childSubscript = childSubscriptForIndex(object, index);
		final int offset = offsetForChildSubscript(object, childSubscript);
		final A_Tuple child = object.slot(SUBTUPLE_AT_, childSubscript);
		return child.tupleAt(index - offset);
	}

	/**
	 * Answer a tuple with all the elements of object except at the given index
	 * we should have newValueObject.  This may destroy the original tuple if
	 * canDestroy is true.
	 */
	@Override @AvailMethod
	protected A_Tuple o_TupleAtPuttingCanDestroy (
		final AvailObject object,
		final int index,
		final A_BasicObject newValueObject,
		final boolean canDestroy)
	{
		assert index >= 1 && index <= object.tupleSize();
		AvailObject result = object;
		if (!(canDestroy && isMutable()))
		{
			result = newLike(mutable(), object, 0, 0);
		}
		final int subtupleSubscript = childSubscriptForIndex(object, index);
		final A_Tuple oldSubtuple =
			object.slot(SUBTUPLE_AT_, subtupleSubscript);
		final int delta = offsetForChildSubscript(object, subtupleSubscript);
		final int oldHash = object.slot(HASH_OR_ZERO);
		if (oldHash != 0)
		{
			// Maintain the already-computed hash.
			final A_BasicObject oldValue = oldSubtuple.tupleAt(index - delta);
			final int adjustment = newValueObject.hash() - oldValue.hash();
			final int scaledAdjustment = adjustment * multiplierRaisedTo(index);
			result.setSlot(HASH_OR_ZERO, oldHash + scaledAdjustment);
		}
		final A_Tuple newSubtuple = oldSubtuple.tupleAtPuttingCanDestroy(
			index - delta, newValueObject, canDestroy);
		final int newLevel = newSubtuple.treeTupleLevel();
		if (newLevel == level - 1)
		{
			// Most common case
			result.setSlot(SUBTUPLE_AT_, subtupleSubscript, newSubtuple);
			check(result);
			return result;
		}
		// The replacement in the subtuple changed its height.  We'll have to
		// use general concatenation to combine the section of the original
		// tuple left of the subtuple, the newSubtuple, and the section to the
		// right of the subtuple.  Don't allow the left and right extractions to
		// be destructive, since we mustn't modify the tuple between the
		// extractions.
		final A_Tuple leftPart = object.copyTupleFromToCanDestroy(
			1, delta, false);
		final A_Tuple rightPart = object.copyTupleFromToCanDestroy(
			offsetForChildSubscript(object, subtupleSubscript + 1) + 1,
			object.tupleSize(),
			false);
		final A_Tuple leftAndMiddle =
			leftPart.concatenateWith(newSubtuple, true);
		return leftAndMiddle.concatenateWith(rightPart, true);
	}

	@Override
	protected boolean o_TupleElementsInRangeAreInstancesOf (
		final AvailObject object,
		final int startIndex,
		final int endIndex,
		final A_Type type)
	{
		final int tupleSize = object.tupleSize();
		assert 1 <= startIndex && startIndex <= tupleSize;
		assert startIndex - 1 <= endIndex && endIndex <= tupleSize;
		if (endIndex == startIndex - 1)
		{
			return true;
		}
		// Non-empty range, so start and end are both within range.
		final int startChildSubscript =
			childSubscriptForIndex(object, startIndex);
		final int endChildSubscript =
			childSubscriptForIndex(object, endIndex);
		for (int i = startChildSubscript; i <= endChildSubscript; i++)
		{
			// At least one element of this child is involved in the hash.
			final int startOfChild = offsetForChildSubscript(object, i) + 1;
			final int endOfChild = object.intSlot(CUMULATIVE_SIZES_AREA_, i);
			final int startIndexInChild = max(0, startIndex - startOfChild) + 1;
			final int endIndexInChild =
				min(endOfChild, endIndex) - startOfChild + 1;
			final A_Tuple child = object.slot(SUBTUPLE_AT_, i);
			if (!child.tupleElementsInRangeAreInstancesOf(
				startIndexInChild, endIndexInChild, type))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Answer the integer element at the given index in the tuple object.
	 */
	@Override @AvailMethod
	protected int o_TupleIntAt (final AvailObject object, final int index)
	{
		final int childSubscript = childSubscriptForIndex(object, index);
		final int offset = offsetForChildSubscript(object, childSubscript);
		final A_Tuple child = object.slot(SUBTUPLE_AT_, childSubscript);
		return child.tupleIntAt(index - offset);
	}

	/**
	 * Answer the number of elements in the tuple as an int.
	 */
	@Override @AvailMethod
	protected int o_TupleSize (final AvailObject object)
	{
		return object.intSlot(
			CUMULATIVE_SIZES_AREA_,
			object.variableObjectSlotsCount());
	}

	/**
	 * Concatenate two tree tuples that are at the same level.  Destroy or reuse
	 * both tuples if they're mutable.
	 *
	 * @param tuple1 The left tuple.
	 * @param tuple2 The right tuple.
	 * @return A tuple containing the left tuple's elements followed by the
	 *         right tuple's elements.
	 */
	static AvailObject concatenateSameLevel (
		final A_Tuple tuple1,
		final A_Tuple tuple2)
	{
		final int level = tuple1.treeTupleLevel();
		assert level == tuple2.treeTupleLevel();
		final int count1 = tuple1.childCount();
		final int count2 = tuple2.childCount();
		// First work out the resulting hash if it's inexpensive.
		final int newHash = tuple1.hashOrZero() == 0 || tuple2.hashOrZero() == 0
			? 0
			: tuple1.hashOrZero()
				+ (tuple2.hashOrZero()
					* multiplierRaisedTo(tuple1.tupleSize()));
		if (count1 >= minWidthOfNonRoot && count2 >= minWidthOfNonRoot)
		{
			// They're each big enough to be non-roots.  Do the cheapest thing
			// and create a 2-way node holding both.
			return createTwoPartTreeTuple(tuple1, tuple2, level + 1, newHash);
		}
		if (count1 + count2 <= maxWidth)
		{
			// Fits in a single node.
			final AvailObject newNode = newLike(
				tuple1.descriptor().mutable(),
				(AvailObject) tuple1,
				count2,
				((count1 + count2 + 1) >> 1) - ((count1 + 1) >> 1));
			int size = tuple1.tupleSize();
			int dest = count1 + 1;
			for (int src = 1; src <= count2; src++, dest++)
			{
				final A_Tuple child = tuple2.childAt(src);
				newNode.setSlot(SUBTUPLE_AT_, dest, child);
				size += child.tupleSize();
				newNode.setIntSlot(CUMULATIVE_SIZES_AREA_, dest, size);
			}
			newNode.setSlot(HASH_OR_ZERO, newHash);
			check(newNode);
			return newNode;
		}
		// Must divide the children (evenly) into two nodes, then introduce a
		// 2-way node holding both.
		final int totalCount = count1 + count2;
		final int leftCount = (totalCount + 1) >> 1;
		final int rightCount = totalCount - leftCount;
		final AvailObject newLeft = createUninitializedTree(level, leftCount);
		final AvailObject newRight = createUninitializedTree(level, rightCount);
		AvailObject target = newLeft;
		int destLimit = leftCount;
		int size = 0;
		int dest = 1;
		for (int whichSource = 1; whichSource <= 2; whichSource++)
		{
			final A_Tuple source = whichSource == 1 ? tuple1 : tuple2;
			final int limit = whichSource == 1 ? count1 : count2;
			for (int src = 1; src <= limit; src++, dest++)
			{
				final A_Tuple child = source.childAt(src);
				if (dest > destLimit)
				{
					assert target == newLeft;
					target = newRight;
					destLimit = rightCount;
					dest = 1;
					size = 0;
				}
				size += child.tupleSize();
				target.setSlot(SUBTUPLE_AT_, dest, child);
				target.setIntSlot(CUMULATIVE_SIZES_AREA_, dest, size);
			}
		}
		assert target == newRight;
		assert dest == destLimit + 1;
		check(target);
		// And tape them back together.
		return createTwoPartTreeTuple(newLeft, newRight, level + 1, newHash);
	}

	/**
	 * Concatenate the two tuples together.  At least one of them must be a
	 * tree tuple.
	 *
	 * @param tuple1
	 *        The first tuple.
	 * @param tuple2
	 *        The second tuple.
	 * @param canDestroy
	 *        Whether one of the tuples is allowed to be destroyed if it's also
	 *        mutable.
	 * @return The concatenated tuple, perhaps having destroyed or recycled one
	 *         of the input tuples if permitted.
	 */
	static A_Tuple concatenateAtLeastOneTree (
		final AvailObject tuple1,
		final A_Tuple tuple2,
		final boolean canDestroy)
	{
		final int size1 = tuple1.tupleSize();
		final int size2 = tuple2.tupleSize();
		final int level1 = tuple1.treeTupleLevel();
		final int level2 = tuple2.treeTupleLevel();
		assert level1 > 0 || level2 > 0;
		if (!canDestroy)
		{
			tuple1.makeImmutable();
			tuple2.makeImmutable();
		}
		if (level1 == level2)
		{
			return concatenateSameLevel(tuple1, tuple2);
		}
		// Work out the resulting hash if it's inexpensive.
		int newHash = 0;
		final int h1 = tuple1.hashOrZero();
		if (h1 != 0)
		{
			final int h2 = tuple2.hashOrZero();
			if (h2 != 0)
			{
				newHash = h1 + (h2 * multiplierRaisedTo(size1));
			}
		}
		if (level1 > level2)
		{
			final int childCount1 = tuple1.childCount();
			final A_Tuple oldLast = tuple1.childAt(childCount1);
			final A_Tuple newLast = oldLast.concatenateWith(tuple2, true);
			if (newLast.treeTupleLevel() == level1)
			{
				// Last child overflowed.  Combine myself minus the last, with
				// the new peer.  Be careful of the int-packed cumulative sizes
				// area.
				final AvailObject withoutLast = newLike(
					descriptorFor(MUTABLE, level1),
					tuple1,
					-1,
					-(childCount1 & 1));
				if ((childCount1 & 1) == 0)
				{
					// Be tidy.
					withoutLast.setIntSlot(
						CUMULATIVE_SIZES_AREA_, childCount1, 0);
				}
				withoutLast.setSlot(HASH_OR_ZERO, 0);
				return concatenateSameLevel(withoutLast, newLast);
			}
			assert newLast.treeTupleLevel() == level1 - 1;
			// Replace the last child.  In place if possible.
			final AvailObject result =
				canDestroy && tuple1.descriptor().isMutable()
					? tuple1
					: newLike(descriptorFor(MUTABLE, level1), tuple1, 0, 0);
			result.setSlot(SUBTUPLE_AT_, childCount1, newLast);
			result.setIntSlot(
				CUMULATIVE_SIZES_AREA_,
				childCount1,
				tuple1.intSlot(CUMULATIVE_SIZES_AREA_, childCount1) + size2);
			result.setSlot(HASH_OR_ZERO, newHash);
			check(result);
			return result;
		}
		//noinspection ConstantConditions
		assert level1 < level2;
		final int childCount2 = tuple2.childCount();
		final A_Tuple oldFirst = tuple2.childAt(1);
		// Don't allow oldFirst to be clobbered, otherwise tuple2 will have
		// incorrect cumulative sizes.
		oldFirst.makeImmutable();
		final A_Tuple newFirst = tuple1.concatenateWith(oldFirst, true);
		if (newFirst.treeTupleLevel() == level2)
		{
			// First child overflowed.  Combine this new peer with the other
			// tuple, minus its first child.
			final AvailObject withoutFirst =
				createUninitializedTree(level2, childCount2 - 1);
			int size = 0;
			for (int src = 2; src <= childCount2; src++)
			{
				final A_Tuple child = tuple2.childAt(src);
				size += child.tupleSize();
				withoutFirst.setSlot(SUBTUPLE_AT_, src - 1, child);
				withoutFirst.setIntSlot(CUMULATIVE_SIZES_AREA_, src - 1, size);
			}
			withoutFirst.setSlot(HASH_OR_ZERO, 0);
			check(withoutFirst);
			return concatenateSameLevel(newFirst, withoutFirst);
		}
		assert newFirst.treeTupleLevel() == level2 - 1;
		// Replace the first child of other.
		return tuple2.replaceFirstChild(newFirst);
	}

	/**
	 * Answer the one-based subscript into the 32-bit int fields of {@link
	 * IntegerSlots#CUMULATIVE_SIZES_AREA_}, in which the specified tuple index
	 * occurs.
	 *
	 * @param object The tree tuple node to search.
	 * @param index The 1-based tuple index to search for.
	 * @return The 1-based subscript of the subtuple containing the tuple index.
	 */
	private static int childSubscriptForIndex (
		final AvailObject object,
		final int index)
	{
		final int childCount = object.variableObjectSlotsCount();
		assert index >= 1;
		assert index <= object.intSlot(CUMULATIVE_SIZES_AREA_, childCount);
		final int childSlotIndex = object.intBinarySearch(
			CUMULATIVE_SIZES_AREA_, childCount, index);
		return (childSlotIndex >= 0 ? childSlotIndex : ~childSlotIndex)
			- CUMULATIVE_SIZES_AREA_.ordinal() + 2;
	}

	/**
	 * Answer one less than the first one-based index of elements that fall
	 * within the subtuple with the specified childSubscript.  This is the
	 * difference between the coordinate system of the tuple and the coordinate
	 * system of the subtuple.
	 *
	 * @param object The tuple.
	 * @param childSubscript Which subtuple to transform an index into.
	 * @return How much to subtract to go from an index into the tuple to an
	 *         index into the subtuple.
	 */
	private static int offsetForChildSubscript (
		final AvailObject object,
		final int childSubscript)
	{
		return childSubscript == 1
			? 0
			: object.intSlot(CUMULATIVE_SIZES_AREA_, childSubscript - 1);
	}

	/**
	 * Perform a sanity check on the passed tree tuple.  Fail if it's invalid.
	 *
	 * @param object The tree tuple to check.
	 */
	private static void check (final AvailObject object)
	{
		assert object.descriptor() instanceof TreeTupleDescriptor;
		assert (object.variableObjectSlotsCount() + 1) >> 1
			== object.variableIntegerSlotsCount();
		final int myLevelMinusOne = object.treeTupleLevel() - 1;
		final int childCount = object.childCount();
		int cumulativeSize = 0;
		for (int childIndex = 1; childIndex <= childCount; childIndex++)
		{
			final A_Tuple child = object.slot(SUBTUPLE_AT_, childIndex);
			assert child.treeTupleLevel() == myLevelMinusOne;
			assert myLevelMinusOne == 0
				|| child.childCount() >= minWidthOfNonRoot;
			final int childSize = child.tupleSize();
			assert childSize > 0;
			cumulativeSize += childSize;
			assert object.intSlot(CUMULATIVE_SIZES_AREA_, childIndex)
				== cumulativeSize;
		}
	}

	/**
	 * Create a new tree tuple with the given level.  The client is responsible
	 * for setting the bin elements and updating the hash and tuple size.
	 *
	 * @param level The tree level at which this hashed bin occurs.
	 * @param size The number of children this tree tuple should have.
	 * @return A new tree tuple with uninitialized {@linkplain
	 *         ObjectSlots#SUBTUPLE_AT_ subtuple} slots and {@linkplain
	 *         IntegerSlots#CUMULATIVE_SIZES_AREA_ cumulative size} slots.
	 */
	private static AvailObject createUninitializedTree (
		final int level,
		final int size)
	{
		final AvailObject instance =
			newObjectIndexedIntegerIndexedDescriptor(
				size,
				(size + 1) >> 1,
				descriptorFor(MUTABLE, level));
		instance.setSlot(HASH_OR_ZERO, 0);
		return instance;
	}

	/**
	 * Create a 2-child tree tuple at the specified level.  The children must
	 * both be at newLevel - 1.  Neither may be empty.
	 *
	 * @param left The left child.
	 * @param right The right child.
	 * @param newLevel The level at which to create a new node.
	 * @param newHashOrZero The new hash, or zero if inconvenient to produce.
	 * @return A new tree tuple at newLevel.
	 */
	public static AvailObject createTwoPartTreeTuple (
		final A_Tuple left,
		final A_Tuple right,
		final int newLevel,
		final int newHashOrZero)
	{
		assert left.treeTupleLevel() == newLevel - 1;
		assert right.treeTupleLevel() == newLevel - 1;
		assert left.tupleSize() > 0;
		assert right.tupleSize() > 0;
		final AvailObject newNode = createUninitializedTree(newLevel, 2);
		newNode.setSlot(SUBTUPLE_AT_, 1, left);
		newNode.setSlot(SUBTUPLE_AT_, 2, right);
		newNode.setIntSlot(CUMULATIVE_SIZES_AREA_, 1, left.tupleSize());
		newNode.setIntSlot(
			CUMULATIVE_SIZES_AREA_, 2, left.tupleSize() + right.tupleSize());
		newNode.setSlot(HASH_OR_ZERO, newHashOrZero);
		check(newNode);
		return newNode;
	}

	/**
	 * Reverse each child of this tree, then assemble them in reverse order into
	 * a new tree.  Not that reversing each child may also have to recursively
	 * navigate more levels of tree.
	 *
	 * @param object The tree tuple to reverse.
	 * @return A tree tuple that is the reverse of the given tree tuple.
	 */
	public static AvailObject internalTreeReverse (final AvailObject object)
	{
		final int childCount = object.childCount();
		final AvailObject newTree =
			createUninitializedTree(object.treeTupleLevel(), childCount);
		int cumulativeSize = 0;
		for (int src = childCount, dest = 1; src > 0; src--, dest++)
		{
			final A_Tuple child = object.childAt(src);
			newTree.setSlot(SUBTUPLE_AT_, dest, child.tupleReverse());
			cumulativeSize += child.tupleSize();
			newTree.setIntSlot(CUMULATIVE_SIZES_AREA_, dest, cumulativeSize);
		}
		assert cumulativeSize == object.tupleSize();
		newTree.setSlot(HASH_OR_ZERO,0);
		return newTree;
	}

	/**
	 * Answer the minimum number of children a non-root tree tuple may have.
	 */
	private static final int minWidthOfNonRoot = 16;

	/**
	 * Answer the maximum number of children a tree tuple node may have.
	 */
	private static final int maxWidth = 64;


	/**
	 * The number of distinct levels that my instances can occupy in a tree
	 * tuple.
	 */
	private static final int numberOfLevels = 10;

	/**
	 * The height of a tuple tree with this descriptor.
	 */
	private final int level;

	/**
	 * Answer the appropriate {@code TreeTupleDescriptor} to use for the
	 * given mutability and level.
	 *
	 * @param flag Whether the descriptor is to be used for a mutable object.
	 * @param level The tree tuple level that its objects should occupy.
	 * @return A suitable {@code TreeTupleDescriptor}.
	 */
	static TreeTupleDescriptor descriptorFor (
		final Mutability flag,
		final int level)
	{
		assert 0 <= level && level < numberOfLevels;
		return descriptors[level * 3 + flag.ordinal()];
	}

	/**
	 * Construct a new {@code TreeTupleDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 * @param level
	 *        The height of the node in the tree tuple.
	 */
	TreeTupleDescriptor (
		final Mutability mutability,
		final int level)
	{
		super(mutability, ObjectSlots.class, IntegerSlots.class);
		this.level = level;
	}

	/**
	 * {@link TreeTupleDescriptor}s organized by mutability and level.
	 */
	static final TreeTupleDescriptor[] descriptors;

	static
	{
		descriptors = new TreeTupleDescriptor[numberOfLevels * 3];
		int target = 0;
		for (int level = 0; level < numberOfLevels; level++)
		{
			descriptors[target++] = new TreeTupleDescriptor(MUTABLE, level);
			descriptors[target++] = new TreeTupleDescriptor(IMMUTABLE, level);
			descriptors[target++] = new TreeTupleDescriptor(SHARED, level);
		}
	}

	@Override
	public TreeTupleDescriptor mutable ()
	{
		return descriptorFor(MUTABLE, level);
	}

	@Override
	public TreeTupleDescriptor immutable ()
	{
		return descriptorFor(IMMUTABLE, level);
	}

	@Override
	public TreeTupleDescriptor shared ()
	{
		return descriptorFor(SHARED, level);
	}
}
