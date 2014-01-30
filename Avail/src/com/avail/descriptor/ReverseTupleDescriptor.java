/**
 * ReverseTupleDescriptor.java Copyright (c) 1993-2013, Mark van Gulik and Todd
 * L Smith. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
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

	@Override @AvailMethod
	AvailObject o_TupleAt (final AvailObject object, final int index)
	{
		final int size = object.slot(ORIGIN_TUPLE).tupleSize();

		assert 1 <= index && index <= size;
		final int reverseIndex = size - index;
		return object.slot(ORIGIN_TUPLE).tupleAt(reverseIndex);
	}

	@Override @AvailMethod
	int o_TupleIntAt (final AvailObject object, final int index)
	{
		final int size = object.slot(ORIGIN_TUPLE).tupleSize();

		assert 1 <= index && index <= size;
		final int reverseIndex = size - index;
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
		if (!canDestroy || !isMutable())
		{
			return object.copyAsMutableObjectTuple().tupleAtPuttingCanDestroy(
				object.slot(SIZE) - index ,
				newValueObject,
				true);
		}
		object.objectTupleAtPut(object.slot(SIZE) - index, newValueObject);
		// Invalidate the hash value.
		object.hashOrZero(0);
		return object;
	}

	/** The mutable {@link ReverseTupleDescriptor}. */
	public static final ReverseTupleDescriptor mutable =
		new ReverseTupleDescriptor(Mutability.MUTABLE);

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Compare a subrange of this subrange tuple with part of the given tuple.
	 * </p>
	 */
	@Override @AvailMethod
	boolean o_CompareFromToWithStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anotherObject,
		final int startIndex2)
	{

		return o_CompareFromToWithAnyTupleStartingAt(
			object, startIndex1, endIndex1, anotherObject, startIndex2);
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
	@Override @AvailMethod
	int o_ComputeHashFromTo (
		final AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		final A_Tuple basis = object.slot(ORIGIN_TUPLE);
		final int size = object.slot(SIZE);
		assert 1 <= startIndex && startIndex <= size;
		assert startIndex - 1 <= endIndex && endIndex <= size;
		final int adjustment = size - 1;
		return basis.computeHashFromTo(
			endIndex - adjustment,
			startIndex + adjustment);
	}

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
		if (newSize <= 32)
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
	 * @param originTuple The tuple to be reversed.
	 * @return A new reverse tuple.
	 */
	public static AvailObject createReverseTuple (
		final AvailObject originTuple)
	{
		final AvailObject instance = mutable.create();
		instance.setSlot(ORIGIN_TUPLE, originTuple);
		instance.setSlot(SIZE, originTuple.tupleSize());
		return instance;
	}

	/**
	 * Create a new {@link AvailObject} that unwraps the specified {@linkplain
	 * AvailObject} for identity-based comparison semantics.
	 *
	 * @param originTuple The tuple to be reversed.
	 * @param tupleDescriptor Avail object input tuple descriptor. Used to
	 * identify the correct behavior as in unwrapping the original tuple by
	 * retrieving the original tuple's reference.
	 * @return An un-reversed reversed tuple.
	 */
	public static AvailObject reverseTuple (
		final AvailObject originTuple,
		final ReverseTupleDescriptor tupleDescriptor)
	{
		return originTuple.slot(ORIGIN_TUPLE);
	}
}