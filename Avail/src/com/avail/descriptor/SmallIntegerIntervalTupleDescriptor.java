/**
 * SmallIntegerIntervalTupleDescriptor.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

import static com.avail.descriptor.SmallIntegerIntervalTupleDescriptor.IntegerSlots.*;
import com.avail.annotations.AvailMethod;
import com.avail.annotations.HideFieldInDebugger;

/**
 * {@code SmallIntegerIntervalTupleDescriptor} represents an {@linkplain
 * IntegerIntervalTupleDescriptor integer interval tuple} whose slots are all
 * Java primitive numerics.
 *
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 */
public class SmallIntegerIntervalTupleDescriptor
extends TupleDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * A slot to hold the cached hash value of a tuple. If zero, then the
		 * hash value must be computed upon request. Note that in the very rare
		 * case that the hash value actually equals zero, the hash value has to
		 * be computed every time it is requested.
		 */
		@HideFieldInDebugger
		HASH_OR_ZERO,

		/** The first value in the tuple, inclusive. */
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
		DELTA,

		/**
		 * The number of elements in the tuple.
		 */
		SIZE;

		static
		{
			assert TupleDescriptor.IntegerSlots.HASH_OR_ZERO.ordinal()
				== HASH_OR_ZERO.ordinal();
		}
	}

	/**
	 * Defined threshold for making copies versus using {@linkplain
	 * TreeTupleDescriptor}/using other forms of reference instead of creating
	 * a new tuple.
	 */
	private final int maximumCopySize = 32;

	@Override @AvailMethod
	public boolean o_IsSmallIntegerIntervalTuple(final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	A_Tuple o_CopyTupleFromToCanDestroy (
		final AvailObject object,
		final int start,
		final int end,
		final boolean canDestroy)
	{
		// Ensure parameters are in bounds
		assert 1 <= start && start <= end + 1;
		final int oldSize = object.slot(SIZE);
		final int newSize = end - start + 1;
		assert 0 <= end && end <= oldSize;

		// If the requested copy is a proper subrange, create it.
		if (newSize != oldSize)
		{
			final int delta = object.slot(DELTA);
			final int oldStartValue = object.slot(START);

			final int newStartValue = oldStartValue + delta * (start - 1);
			final int newEndValue = newStartValue + delta * (newSize - 1);

			if (isMutable() && canDestroy)
			{
				// Recycle the object.
				object.setSlot(START, newStartValue);
				object.setSlot(END, newEndValue);
				object.setSlot(SIZE, newSize);
				return object;
			}
			return createInterval(newStartValue, newEndValue, delta);
		}

		// Otherwise, this method is requesting a full copy of the original.
		if (isMutable() && !canDestroy)
		{
			object.makeImmutable();
		}
		return object;
	}

	@Override @AvailMethod
	boolean o_CompareFromToWithStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anotherObject,
		final int startIndex2)
	{
		return anotherObject.compareFromToWithSmallIntegerIntervalTupleStartingAt(
			startIndex2,
			startIndex2 + endIndex1 - startIndex1,
			object,
			startIndex1);
	}

	@Override @AvailMethod
	boolean o_CompareFromToWithSmallIntegerIntervalTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aSmallIntegerIntervalTuple,
		final int startIndex2)
	{
		// If the objects refer to the same memory, and the indices match
		// up, the subranges are the same.
		if (object.sameAddressAs(aSmallIntegerIntervalTuple) &&
			startIndex1 == startIndex2)
		{
			return true;
		}

		// If the objects do not refer to the same memory but the tuples are
		// identical,
		if (object.equals(aSmallIntegerIntervalTuple))
		{
			// indirect one to the other if it is not shared.
			if (!isShared())
			{
				aSmallIntegerIntervalTuple.makeImmutable();
				object.becomeIndirectionTo(aSmallIntegerIntervalTuple);
			}
			else if (!aSmallIntegerIntervalTuple.descriptor().isShared())
			{
				object.makeImmutable();
				aSmallIntegerIntervalTuple.becomeIndirectionTo(object);
			}

			// If the subranges start at the same place, they are the same.
			if (startIndex1 == startIndex2)
			{
				return true;
			}
			return false;
		}

		// Finally, check the subranges.
		final A_Tuple first = object.copyTupleFromToCanDestroy(
			startIndex1,
			endIndex1,
			false);
		final A_Tuple second = aSmallIntegerIntervalTuple.copyTupleFromToCanDestroy(
			startIndex2,
			startIndex2 + endIndex1 - startIndex1,
			false);
		return first.equals(second);
	}


	@Override
	A_Tuple o_ConcatenateWith (
		final AvailObject object,
		final A_Tuple otherTuple,
		final boolean canDestroy)
	{
		if (!canDestroy)
		{
			object.makeImmutable();
			otherTuple.makeImmutable();
		}

		// Assess the possibility that the concatenation will still be a small
		// integer interval tuple.
		if (otherTuple.isSmallIntegerIntervalTuple())
		{
			final AvailObject otherDirect = otherTuple.traversed();
			final int delta = object.slot(DELTA);

			// If the other's delta is the same as mine,
			if (delta == otherDirect.slot(DELTA))
			{
				// and the other's start is one delta away from my end,
				if (object.slot(END) + delta == otherDirect.slot(START))
				{
					// then we're adjacent.
					final int newSize = object.slot(SIZE) +
						otherDirect.slot(SIZE);

					// If we can do replacement in place,
					// use me for the return value.
					if (isMutable())
					{
						object.setSlot(END, otherDirect.slot(END));
						object.setSlot(SIZE, newSize);
						object.hashOrZero(0);
						return object;
					}
					// Or the other one.
					if (otherDirect.descriptor().isMutable())
					{
						otherDirect.setSlot(START, object.slot(START));
						otherDirect.setSlot(SIZE, newSize);
						otherDirect.hashOrZero(0);
						return otherDirect;
					}

					// Otherwise, create a new interval.
					return createInterval(
						object.slot(START),
						otherDirect.slot(END),
						delta);
				}
			}
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
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsSmallIntegerIntervalTuple(object);
	}

	@Override @AvailMethod
	boolean o_EqualsSmallIntegerIntervalTuple (
		final AvailObject object,
		final A_Tuple aSmallIntegerIntervalTuple)
	{
		// First, check for object-structure (address) identity.
		if (object.sameAddressAs(aSmallIntegerIntervalTuple))
		{
			return true;
		}

		// If the objects do not refer to the same memory, check if the tuples
		// are identical.
		final AvailObject firstTraversed = object.traversed();
		final AvailObject secondTraversed =
			aSmallIntegerIntervalTuple.traversed();

		// Check that the slots match.
		final int firstHash = firstTraversed.slot(HASH_OR_ZERO);
		final int secondHash = secondTraversed.slot(HASH_OR_ZERO);
		if (firstHash != 0 && secondHash != 0 && firstHash != secondHash)
		{
			return false;
		}
		if (firstTraversed.slot(SIZE) != secondTraversed.slot(SIZE))
		{
			return false;
		}
		if (firstTraversed.slot(DELTA) != secondTraversed.slot(DELTA))
		{
			return false;
		}
		if (firstTraversed.slot(START) != secondTraversed.slot(START))
		{
			return false;
		}

		// All the slots match. Indirect one to the other if it is not shared.
		if (!isShared())
		{
			aSmallIntegerIntervalTuple.makeImmutable();
			object.becomeIndirectionTo(aSmallIntegerIntervalTuple);
		}
		else if (!aSmallIntegerIntervalTuple.descriptor().isShared())
		{
			object.makeImmutable();
			aSmallIntegerIntervalTuple.becomeIndirectionTo(object);
		}
		return true;

	}

	@Override
	int o_BitsPerEntry (final AvailObject object)
	{
		// Consider a billion element tuple. Since a small interval tuple
		// requires only O(1) storage, irrespective of its size, the average
		// bits per entry is 0.
		return 0;
	}

	@Override @AvailMethod
	AvailObject o_TupleAt (final AvailObject object, final int index)
	{
		// Answer the value at the given index in the tuple object.
		// START + (index-1) × DELTA
		assert index >= 1 && index <= object.tupleSize();
		int temp = index - 1;
		temp = temp * object.slot(DELTA);
		temp = temp + object.slot(START);
		return (AvailObject) IntegerDescriptor.fromInt(temp);
	}

	@Override @AvailMethod
	A_Tuple o_TupleAtPuttingCanDestroy (
		final AvailObject object,
		final int index,
		final A_BasicObject newValueObject,
		final boolean canDestroy)
	{
		// Answer a tuple with all the elements of object except at the given
		// index we should have newValueObject. This may destroy the original
		// tuple if canDestroy is true.
		assert index >= 1 && index <= object.tupleSize();
		if (!canDestroy || !isMutable())
		{
			/* TODO: [LAS] Later - Create nybble or byte tuples if appropriate. */
			return object.copyAsMutableObjectTuple().tupleAtPuttingCanDestroy(
				index,
				newValueObject,
				true);
		}
		object.objectTupleAtPut(index, newValueObject);
		// Invalidate the hash value.
		object.hashOrZero(0);
		return object;
	}

	@Override @AvailMethod
	int o_TupleIntAt (final AvailObject object, final int index)
	{
		// Answer the value at the given index in the tuple object.
		// START + (index-1) × DELTA
		assert index >= 1 && index <= object.tupleSize();
		int temp = index - 1;
		temp = temp * object.slot(DELTA);
		temp = temp + object.slot(START);
		return temp;
	}

	@Override @AvailMethod
	A_Tuple o_TupleReverse(final AvailObject object)
	{
		//If tuple is small enough or is immutable, create a new Interval
		if (object.tupleSize() < maximumCopySize || !isMutable())
		{
			final int newDelta = object.slot(DELTA) * -1;

			return createInterval (
				object.slot(END),
				object.slot(START),
				newDelta);
		}

		//The interval is mutable and large enough to warrant changing in place.
		final int newStart = object.slot(END);
		final int newEnd = object.slot(START);
		final int newDelta = object.slot(DELTA) * -1;

		object.setSlot(START, newStart);
		object.setSlot(END, newEnd);
		object.setSlot(DELTA, newDelta);
		return object;
	}

	@Override @AvailMethod
	int o_TupleSize (final AvailObject object)
	{
		return object.slot(SIZE);
	}

	@Override
	boolean o_TupleElementsInRangeAreInstancesOf (
		final AvailObject object,
		final int startIndex,
		final int endIndex,
		final A_Type type)
	{
		final A_Number start = object.tupleAt(startIndex);
		final A_Number end = object.tupleAt(endIndex);
		final A_Number low;
		final A_Number high;
		if (start.lessThan(end))
		{
			low = start;
			high = end;
		}
		else
		{
			low = end;
			high = start;
		}
		if (type.isSupertypeOfIntegerRangeType(
			IntegerRangeTypeDescriptor.inclusive(low, high)))
		{
			return true;
		}
		return super.o_TupleElementsInRangeAreInstancesOf(
			object,
			startIndex,
			endIndex,
			type);
	}

	/** The mutable {@link SmallIntegerIntervalTupleDescriptor}. */
	public static final SmallIntegerIntervalTupleDescriptor mutable =
		new SmallIntegerIntervalTupleDescriptor(Mutability.MUTABLE);

	@Override
	SmallIntegerIntervalTupleDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link IntegerIntervalTupleDescriptor}. */
	private static final SmallIntegerIntervalTupleDescriptor immutable =
		new SmallIntegerIntervalTupleDescriptor(Mutability.IMMUTABLE);

	@Override
	SmallIntegerIntervalTupleDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link SmallIntegerIntervalTupleDescriptor}. */
	private static final SmallIntegerIntervalTupleDescriptor shared =
		new SmallIntegerIntervalTupleDescriptor(Mutability.SHARED);

	@Override
	SmallIntegerIntervalTupleDescriptor shared ()
	{
		return shared;
	}

	/**
	 * Construct a new {@link SmallIntegerIntervalTupleDescriptor}.
	 *
	 * @param mutability
	 */
	public SmallIntegerIntervalTupleDescriptor (final Mutability mutability)
	{
		super(mutability, null, IntegerSlots.class);
	}

	/**
	 * Evaluates whether the supplied parameters for an integer interval tuple
	 * are small enough that the small integer interval tuple representation
	 * can be used.
	 *
	 * @param newStart The start value for the candidate interval tuple.
	 * @param newEnd The end value for the candidate interval tuple.
	 * @param delta The delta for the candidate interval tuple.
	 * @return True if all values would fit in the small representation, false
	 *         otherwise.
	 */
	static boolean isCandidate (
		final A_Number newStart,
		final A_Number newEnd,
		final A_Number delta)
	{
		// Size is always an integer, so no need to check it.

		final A_Number integerMin =
			IntegerDescriptor.fromInt(Integer.MIN_VALUE);
		final A_Number integerMax =
			IntegerDescriptor.fromInt(Integer.MAX_VALUE);

		if (newStart.greaterOrEqual(integerMin)
				&& newStart.lessOrEqual(integerMax)
			&& newEnd.lessOrEqual(integerMax)
				&& newEnd.lessOrEqual(integerMax)
			&& delta.lessOrEqual(integerMax)
				&& delta.lessOrEqual(integerMax))
		{
			return true;
		}
		return false;
	}

	/**
	 * Create a new interval according to the parameters.
	 *
	 * @param newStart The first integer in the interval.
	 * @param newEnd The last integer in the interval.
	 * @param delta The difference between an integer and its subsequent
	 *              neighbor in the interval. Delta is nonzero.
	 * @return The new interval.
	 */
	public static A_Tuple createInterval (
		final int newStart,
		final int newEnd,
		final int delta)
	{
		final long size = ((long)newEnd - (long)newStart) / delta + 1L;
		assert size == (int)size
			: "Proposed tuple has too many elements";
		final int adjustedEnd = newStart + delta * ((int)size - 1);
		final AvailObject interval = mutable.create();
		interval.setSlot(START, newStart);
		interval.setSlot(END, adjustedEnd);
		interval.setSlot(DELTA, delta);
		interval.setSlot(HASH_OR_ZERO, 0);
		interval.setSlot(SIZE, (int)size);
		return interval;
	}
}
