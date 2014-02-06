/**
 * ReverseTupleDescriptor.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

import static com.avail.descriptor.AvailObjectRepresentation.newLike;
import static com.avail.descriptor.ObjectTupleDescriptor.ObjectSlots.TUPLE_AT_;
import static com.avail.descriptor.ReverseTupleDescriptor.IntegerSlots.*;
import static com.avail.descriptor.ReverseTupleDescriptor.ObjectSlots.*;
import com.avail.annotations.*;

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
		 * The tuple's hash value or zero if not computed.  If the hash value
		 * happens to be zero (very rare) we have to recalculate it on demand.
		 */
		@HideFieldInDebugger
		HASH_OR_ZERO,

		/**
		 * The number of elements in the originating tuple.
		 */
		SIZE;

		static
		{
			assert TupleDescriptor.IntegerSlots.HASH_OR_ZERO.ordinal()
				== HASH_OR_ZERO.ordinal();
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
	 * an new tuple.
	 */
	private static final int minimumCopySize = 32;

	@Override @AvailMethod
	AvailObject o_TupleAt (final AvailObject object, final int index)
	{
		final int size = object.slot(ORIGIN_TUPLE).tupleSize();

		assert 1 <= index && index <= size;
		final int reverseIndex = size + 1 - index;
		return object.slot(ORIGIN_TUPLE).tupleAt(reverseIndex);
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

	/** The mutable {@link ReverseTupleDescriptor}. */
	public static final ReverseTupleDescriptor mutable =
		new ReverseTupleDescriptor(Mutability.MUTABLE);

	@Override @AvailMethod
	int o_BitsPerEntry (final AvailObject object)
	{
		// Answer maximum integer value so that any other representation
		// for comparison is used in favor of a Reverse Tuple representation
		return Integer.MAX_VALUE;
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

	/**
	 * Hash part of the tuple object.
	 */
//	@Override @AvailMethod
//	int o_ComputeHashFromTo (
//		final AvailObject object,
//		final int startIndex,
//		final int endIndex)
//	{
//		final A_Tuple basis = object.slot(ORIGIN_TUPLE);
//		final int size = object.slot(SIZE);
//		assert 1 <= startIndex && startIndex <= size;
//		assert startIndex - 1 <= endIndex && endIndex <= size;
//		final int adjustment = size - 1;
//		return basis.computeHashFromTo(
//			endIndex - adjustment,
//			startIndex + adjustment);
//	}

	@Override
	A_Tuple o_ConcatenateWith (
		final AvailObject object,
		final A_Tuple otherTuple,
		final boolean canDestroy)
	{
		//If the receiver tuple is empty return the target tuple, otherTuple
		final int size1 = object.tupleSize();
		if (size1 == 0)
		{
			if (!canDestroy)
			{
				otherTuple.makeImmutable();
			}
			return otherTuple;
		}
		//If the target tuple is empty return the receiver tuple, object
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
		if (newSize <= minimumCopySize)
		{
			// Copy the objects.
			final int deltaSlots = newSize - object.variableObjectSlotsCount();
			final AvailObject result = newLike(
				mutable(), object, deltaSlots, 0);
			int dest = size1 + 1;
			for (int src = 1; src <= size2; src++, dest++)
			{
				result.setSlot(ORIGIN_TUPLE, dest, otherTuple.tupleAt(src));
			}
			result.setSlot(HASH_OR_ZERO, 0);
			return result;
		}
		if (!canDestroy)
		{
			object.makeImmutable();
			otherTuple.makeImmutable();
		}
		if (otherTuple.treeTupleLevel() == 0)
		{
			return TreeTupleDescriptor.createPair(object, otherTuple, 1, 0);
		}
		return TreeTupleDescriptor.concatenateAtLeastOneTree(
			object,
			otherTuple,
			true);
	}

	@Override @AvailMethod
	A_Tuple o_CopyTupleFromToCanDestroy (
		final AvailObject object,
		final int start,
		final int end,
		final boolean canDestroy)
	{
		assert 1 <= start && start <= end + 1;
		final int tupleSize = object.tupleSize();
		assert 0 <= end && end <= tupleSize;
		final int subrangeSize = end - start + 1;
		if (subrangeSize == 0)
		{
			if (isMutable() && canDestroy)
			{
				object.assertObjectUnreachableIfMutable();
			}
			return TupleDescriptor.empty();
		}
		if (subrangeSize == tupleSize)
		{
			if (isMutable() && !canDestroy)
			{
				object.makeImmutable();
			}
			return object;
		}
		if (subrangeSize > 0 && subrangeSize < tupleSize && subrangeSize < minimumCopySize)
		{
			// It's not empty, it's not a total copy, and it's reasonably small.
			// Just copy the applicable entries out.
			final AvailObject result =
				ObjectTupleDescriptor.createUninitialized(subrangeSize);
			int dest = 1;
			for (int src = start; src <= end; src++, dest++)
			{
				result.setSlot(TUPLE_AT_, dest, object.tupleAt(src));
			}
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
	boolean o_EqualsReverseTuple (final AvailObject object,
		final A_Tuple aTuple)
	{
		return object.slot(ORIGIN_TUPLE).
			equals(((AvailObject)aTuple).slot(ORIGIN_TUPLE));
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

	@Override
	int o_TreeTupleLevel (final AvailObject object)
	{
		return object.slot(ORIGIN_TUPLE).treeTupleLevel();
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
	 * Construct a new {@link ReverseTupleDescriptor}.
	 *
	 * @param mutability
	 */

	private ReverseTupleDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, IntegerSlots.class);
	}

	/**
	 * Create a new {@link AvailObject} that wraps the specified {@linkplain
	 * AvailObject} tuple and provides it with a
	 * {@linkplain ReverseTupleDescriptor} descriptor.
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