/*
 * ReverseTupleDescriptor.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

package com.avail.descriptor;

import com.avail.annotations.AvailMethod;
import com.avail.annotations.HideFieldInDebugger;

import static com.avail.descriptor.ObjectTupleDescriptor.generateObjectTupleFrom;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.ReverseTupleDescriptor.IntegerSlots.HASH_OR_ZERO;
import static com.avail.descriptor.ReverseTupleDescriptor.IntegerSlots.SIZE;
import static com.avail.descriptor.ReverseTupleDescriptor.ObjectSlots.ORIGIN_TUPLE;
import static com.avail.descriptor.TreeTupleDescriptor.concatenateAtLeastOneTree;
import static com.avail.descriptor.TreeTupleDescriptor.createTwoPartTreeTuple;
import static com.avail.descriptor.TreeTupleDescriptor.internalTreeReverse;

/**
 * A reverse tuple holds a reference to an "origin" tuple and the origin
 * tuple's size.
 *
 * <p>To avoid arbitrarily deep constructs, the origin tuple must not itself be
 * a reverse tuple.  Any attempt to create a reverse tuple from a reverse
 * tuple will return the origin tuple.</p>
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public final class ReverseTupleDescriptor
extends TupleDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * The low 32 bits are used for the {@link #HASH_OR_ZERO}, but the upper
		 * 32 can be used by other {@link BitField}s in subclasses of {@link
		 * TupleDescriptor}.
		 */
		@HideFieldInDebugger
		HASH_AND_MORE;

		/**
		 * A slot to hold the cached hash value of a tuple.  If zero, then the
		 * hash value must be computed upon request.  Note that in the very rare
		 * case that the hash value actually equals zero, the hash value has to
		 * be computed every time it is requested.
		 */
		static final BitField HASH_OR_ZERO = bitField(HASH_AND_MORE, 0, 32);

		/** The number of elements in this tuple. */
		static final BitField SIZE = bitField(HASH_AND_MORE, 32, 32);

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
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The basis tuple of which this is a subrange.  The basis tuple must be
		 * flat -- it may not be another subrange tuple, nor may it be a tree
		 * tuple.
		 */
		ORIGIN_TUPLE;
	}

	/**
	 * Defined threshold for making copies versus using {@linkplain
	 * TreeTupleDescriptor}/using other forms of reference instead of creating
	 * a new tuple.
	 */
	private static final int maximumCopySize = 32;

	/** The mutable {@link ReverseTupleDescriptor}. */
	public static final ReverseTupleDescriptor mutable =
		new ReverseTupleDescriptor(Mutability.MUTABLE);

	@Override @AvailMethod
	A_Tuple o_AppendCanDestroy (
		final AvailObject object,
		final A_BasicObject newElement,
		final boolean canDestroy)
	{
		// Fall back to concatenating a singleton.
		if (!canDestroy)
		{
			object.makeImmutable();
		}
		final A_Tuple singleton = tuple(newElement);
		return object.concatenateWith(singleton, canDestroy);
	}

	@Override @AvailMethod
	int o_BitsPerEntry (final AvailObject object)
	{
		// Answer maximum integer value so that any other representation
		// for comparison is used in favor of a Reverse Tuple representation.
		return Integer.MAX_VALUE;
	}

	@Override @AvailMethod
	A_Tuple o_ChildAt (final AvailObject object, final int childIndex)
	{
		if (!object.descriptor.isShared())
		{
			final AvailObject treeTuple =
				internalTreeReverse(object.slot(ORIGIN_TUPLE));
			treeTuple.hashOrZero(object.slot(HASH_OR_ZERO));
			object.becomeIndirectionTo(treeTuple);
			return treeTuple.childAt(childIndex);
		}
		// Object is shared so it cannot change to an indirection.  Instead, we
		// need to return the reverse of the child one level down at the
		// opposite end of the tree from the childIndex.
		final int adjustedSubscript = object.childCount() + 1 - childIndex;
		return object.slot(ORIGIN_TUPLE)
			.childAt(adjustedSubscript)
			.tupleReverse();
	}

	@Override @AvailMethod
	int o_ChildCount (final AvailObject object)
	{
		return object.slot(ORIGIN_TUPLE).childCount();
	}

	@Override @AvailMethod
	boolean o_CompareFromToWithStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aTuple,
		final int startIndex2)
	{
		for (int index = startIndex1,
			index2 = startIndex2;
			index <= endIndex1; index++, index2++)
		{
			if (!object.tupleAt(index).equals(aTuple.tupleAt(index2)))
			{
				return false;
			}
		}
		return true;
	}

	@Override
	A_Tuple o_ConcatenateWith (
		final AvailObject object,
		final A_Tuple otherTuple,
		final boolean canDestroy)
	{
		// If the receiver tuple is empty return the otherTuple.
		final int size1 = object.tupleSize();
		if (size1 == 0)
		{
			if (!canDestroy)
			{
				otherTuple.makeImmutable();
			}
			return otherTuple;
		}
		// If otherTuple is empty return the receiver tuple, object.
		final int size2 = otherTuple.tupleSize();
		if (size2 == 0)
		{
			if (!canDestroy)
			{
				object.makeImmutable();
			}
			return object;
		}
		final int newSize = size1 + size2;
		if (newSize <= maximumCopySize)
		{
			// Copy the objects.
			final A_Tuple dereversedFirstTuple = object.slot(ORIGIN_TUPLE);
			return generateObjectTupleFrom(
				newSize,
				i -> i <= size1
					? dereversedFirstTuple.tupleAt(size1 + 1 - i)
					: otherTuple.tupleAt(i - size1));
		}
		if (!canDestroy)
		{
			object.makeImmutable();
			otherTuple.makeImmutable();
		}
		if (object.slot(ORIGIN_TUPLE).treeTupleLevel() == 0)
		{
			if (otherTuple.treeTupleLevel() == 0)
			{
				return createTwoPartTreeTuple(object, otherTuple, 1, 0);
			}
			return concatenateAtLeastOneTree(object, otherTuple, true);
		}

		final AvailObject newTree =
			internalTreeReverse(object.slot(ORIGIN_TUPLE));
		return concatenateAtLeastOneTree(newTree, otherTuple, true);
	}

	@Override @AvailMethod
	A_Tuple o_CopyTupleFromToCanDestroy (
		final AvailObject object,
		final int start,
		final int end,
		final boolean canDestroy)
	{
		final int tupleSize = object.tupleSize();
		assert 1 <= start && start <= end + 1 && end <= tupleSize;
		final int subrangeSize = end - start + 1;
		if (subrangeSize == 0)
		{
			if (isMutable() && canDestroy)
			{
				object.assertObjectUnreachableIfMutable();
			}
			return emptyTuple();
		}
		if (subrangeSize == tupleSize)
		{
			if (isMutable() && !canDestroy)
			{
				object.makeImmutable();
			}
			return object;
		}
		if (subrangeSize < maximumCopySize)
		{
			// It's not empty, it's not a total copy, and it's reasonably small.
			// Just copy the applicable entries out.
			final AvailObject result = generateObjectTupleFrom(
				subrangeSize, index -> object.tupleAt(index + start - 1));
			if (canDestroy)
			{
				object.assertObjectUnreachableIfMutable();
			}
			else
			{
				result.makeSubobjectsImmutable();
			}
			result.setSlot(HASH_OR_ZERO, 0);
			return result;
		}
		final A_Tuple subrangeOnOrigin =
			object.slot(ORIGIN_TUPLE).copyTupleFromToCanDestroy(
				object.tupleSize() + 1 - end,
				object.tupleSize() + 1 - start,
				canDestroy);

		return subrangeOnOrigin.tupleReverse();
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsReverseTuple(object);
	}

	@Override @AvailMethod
	boolean o_EqualsAnyTuple (
		final AvailObject object,
		final A_Tuple aTuple)
	{
		// Compare this arbitrary Tuple and the given arbitrary tuple.
		if (object.sameAddressAs(aTuple))
		{
			return true;
		}
		// Compare sizes...
		final int size = object.tupleSize();
		if (size != aTuple.tupleSize())
		{
			return false;
		}
		if (o_Hash(object) != aTuple.hash())
		{
			return false;
		}
		for (int i = 1; i <= size; i++)
		{
			if (!o_TupleAt(object, i).equals(aTuple.tupleAt(i)))
			{
				return false;
			}
		}
		if (object.isBetterRepresentationThan(aTuple))
		{
			if (!aTuple.descriptor().isShared())
			{
				object.makeImmutable();
				aTuple.becomeIndirectionTo(object);
			}
		}
		else
		{
			if (!isShared())
			{
				aTuple.makeImmutable();
				object.becomeIndirectionTo(aTuple);
			}
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_EqualsReverseTuple (final AvailObject object,
		final A_Tuple aTuple)
	{
		return object.slot(ORIGIN_TUPLE).
			equals(((AvailObject) aTuple).slot(ORIGIN_TUPLE));
	}

	@Override
	int o_TreeTupleLevel (final AvailObject object)
	{
		return object.slot(ORIGIN_TUPLE).treeTupleLevel();
	}

	@Override @AvailMethod
	AvailObject o_TupleAt (final AvailObject object, final int index)
	{
		final int size = object.slot(ORIGIN_TUPLE).tupleSize();

		assert 1 <= index && index <= size;
		final int reverseIndex = size + 1 - index;
		return object.slot(ORIGIN_TUPLE).tupleAt(reverseIndex);
	}

	@Override @AvailMethod
	A_Tuple o_TupleAtPuttingCanDestroy (
		final AvailObject object,
		final int index,
		final A_BasicObject newValueObject,
		final boolean canDestroy)
	{
		// Answer a tuple with all the elements of object except at the given
		// index we should have newValueObject.  This may destroy the original
		// tuple if canDestroy is true.
		assert index >= 1 && index <= object.tupleSize();
		final A_Tuple innerTuple = object.slot(ORIGIN_TUPLE)
			.tupleAtPuttingCanDestroy(
				object.slot(SIZE) + 1 - index,
				newValueObject,
				canDestroy);
		if (!canDestroy || !isMutable())
		{
			return createReverseTuple(innerTuple);
		}
		object.setSlot(ORIGIN_TUPLE, innerTuple);
		object.hashOrZero(0);
		return object;
	}

	@Override
	boolean o_TupleElementsInRangeAreInstancesOf (
		final AvailObject object,
		final int startIndex,
		final int endIndex,
		final A_Type type)
	{
		final int size = object.slot(ORIGIN_TUPLE).tupleSize();
		final int originStart = size + 1 - endIndex;
		final int originEnd = size + 1 - startIndex;
		return object.slot(ORIGIN_TUPLE).tupleElementsInRangeAreInstancesOf(
			originStart, originEnd, type);
	}

	@Override @AvailMethod
	int o_TupleIntAt (final AvailObject object, final int index)
	{
		final int size = object.slot(ORIGIN_TUPLE).tupleSize();

		assert 1 <= index && index <= size;
		final int reverseIndex = size + 1 - index;
		return object.slot(ORIGIN_TUPLE).tupleIntAt(reverseIndex);
	}

	@Override @AvailMethod
	A_Tuple o_TupleReverse(final AvailObject object)
	{
		return object.slot(ORIGIN_TUPLE);
	}

	@Override @AvailMethod
	int o_TupleSize(final AvailObject object)
	{
		return object.slot(SIZE);
	}

	@Override
	ReverseTupleDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link ReverseTupleDescriptor}. */
	private static final ReverseTupleDescriptor immutable =
		new ReverseTupleDescriptor(Mutability.IMMUTABLE);

	@Override
	ReverseTupleDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link ReverseTupleDescriptor}. */
	private static final ReverseTupleDescriptor shared =
		new ReverseTupleDescriptor(Mutability.SHARED);

	@Override
	ReverseTupleDescriptor shared ()
	{
		return shared;
	}

	/**
	 * Construct a new {@code ReverseTupleDescriptor}.
	 *
	 * @param mutability The mutability of the descriptor.
	 */
	private ReverseTupleDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, IntegerSlots.class);
	}

	/**
	 * Create a new {@link AvailObject} that wraps the specified {@linkplain
	 * AvailObject} tuple and provides it with a {@code ReverseTupleDescriptor}
	 * descriptor.
	 *
	 * <p>The original tuple may be destroyed by this operation.  If you need
	 * the original after this call, use {@link A_BasicObject#makeImmutable()}
	 * on it prior to the call.</p>
	 *
	 * @param originTuple The tuple to be reversed.
	 * @return A new reverse tuple.
	 */
	public static AvailObject createReverseTuple (
		final A_Tuple originTuple)
	{
		final AvailObject instance = mutable.create();
		instance.setSlot(ORIGIN_TUPLE, originTuple);
		instance.setSlot(SIZE, originTuple.tupleSize());
		return instance;
	}
}
