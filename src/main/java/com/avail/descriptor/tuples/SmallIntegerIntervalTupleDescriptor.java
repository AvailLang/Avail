/*
 * SmallIntegerIntervalTupleDescriptor.java
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
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.AbstractDescriptor;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.JavaCompatibility.IntegerSlotsEnumJava;
import com.avail.descriptor.numbers.A_Number;
import com.avail.descriptor.representation.BitField;
import com.avail.descriptor.representation.Mutability;
import com.avail.descriptor.types.A_Type;

import java.util.IdentityHashMap;

import static com.avail.descriptor.numbers.IntegerDescriptor.fromInt;
import static com.avail.descriptor.tuples.ByteTupleDescriptor.generateByteTupleFrom;
import static com.avail.descriptor.tuples.IntTupleDescriptor.generateIntTupleFrom;
import static com.avail.descriptor.tuples.ObjectTupleDescriptor.generateObjectTupleFrom;
import static com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.tuples.SmallIntegerIntervalTupleDescriptor.IntegerSlots.*;
import static com.avail.descriptor.tuples.TreeTupleDescriptor.concatenateAtLeastOneTree;
import static com.avail.descriptor.tuples.TreeTupleDescriptor.createTwoPartTreeTuple;
import static com.avail.descriptor.types.IntegerRangeTypeDescriptor.inclusive;

/**
 * {@code SmallIntegerIntervalTupleDescriptor} represents an {@linkplain
 * IntegerIntervalTupleDescriptor integer interval tuple} whose slots are all
 * Java {@code long}s.
 *
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 */
public class SmallIntegerIntervalTupleDescriptor
extends NumericTupleDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots implements IntegerSlotsEnumJava
	{
		/**
		 * A slot to hold the cached hash value of a tuple. If zero, then the
		 * hash value must be computed upon request. Note that in the very rare
		 * case that the hash value actually equals zero, the hash value has to
		 * be computed every time it is requested.
		 */
		@HideFieldInDebugger
		HASH_AND_MORE,

		/** {@link BitField}s containing the extrema of the tuple. */
		START_AND_END,

		/**
		 * The difference between a value and its subsequent neighbor in the
		 * tuple.
		 */
		DELTA;

		/** The number of elements in the tuple. */
		static final BitField SIZE = AbstractDescriptor
			.bitField(HASH_AND_MORE, 32, 32);

		/** The first value in the tuple, inclusive. */
		static final BitField START = AbstractDescriptor
			.bitField(START_AND_END, 32, 32);

		/**
		 * The last value in the tuple, inclusive. Within the constructor,
		 * the supplied END is normalized to the actual last value.
		 */
		static final BitField END = AbstractDescriptor
			.bitField(START_AND_END, 0, 32);

		/**
		 * A slot to hold the cached hash value of a tuple.  If zero, then the
		 * hash value must be computed upon request.  Note that in the very rare
		 * case that the hash value actually equals zero, the hash value has to
		 * be computed every time it is requested.
		 */
		static final BitField HASH_OR_ZERO = AbstractDescriptor
			.bitField(HASH_AND_MORE, 0, 32);

		static
		{
			assert TupleDescriptor.IntegerSlots.HASH_AND_MORE.ordinal()
				== HASH_AND_MORE.ordinal();
			assert TupleDescriptor.IntegerSlots.HASH_OR_ZERO.isSamePlaceAs(
				HASH_OR_ZERO);
		}
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		aStream.append(object.slot(START));
		aStream.append(" to ");
		aStream.append(object.slot(END));
		final long delta = object.slot(DELTA);
		if (delta != 1L)
		{
			aStream.append(" by ");
			aStream.append(delta);
		}
	}

	/**
	 * Defined threshold for making copies versus using {@linkplain
	 * TreeTupleDescriptor}/using other forms of reference instead of creating
	 * a new tuple.
	 */
	private static final int maximumCopySize = 32;

	@Override @AvailMethod
	protected A_Tuple o_AppendCanDestroy (
		final AvailObject object,
		final A_BasicObject newElement,
		final boolean canDestroy)
	{
		final int originalSize = object.tupleSize();
		final long endValue = object.slot(END);
		final long deltaValue = object.slot(DELTA);
		if (newElement.isInt())
		{
			final int newElementValue = ((A_Number) newElement).extractInt();
			if (newElementValue == endValue + deltaValue
				&& originalSize < Integer.MAX_VALUE)
			{
				// Extend the interval.
				if (canDestroy && isMutable())
				{
					object.setSlot(END, newElementValue);
					object.setSlot(SIZE, originalSize + 1);
					object.setSlot(HASH_OR_ZERO, 0);
					return object;
				}
				// Create another small integer interval.
				return createSmallInterval(
					object.slot(START), newElementValue, deltaValue);
			}
			// The new value isn't consecutive, but it's still an int.
			if (originalSize < maximumCopySize)
			{
				final int start = object.slot(START);
				return generateIntTupleFrom(
					originalSize + 1,
					counter ->
						counter == originalSize
							? newElementValue
							: start + (int)((counter - 1) * deltaValue));
			}
			// Too big; fall through and make a tree-tuple.
		}
		// Fall back to concatenating a singleton.
		final A_Tuple singleton = tuple(newElement);
		return object.concatenateWith(singleton, canDestroy);
	}

	@Override
	protected int o_BitsPerEntry (final AvailObject object)
	{
		// Consider a billion element tuple. Since a small interval tuple
		// requires only O(1) storage, irrespective of its size, the average
		// bits per entry is 0.
		return 0;
	}

	@Override @AvailMethod
	protected boolean o_CompareFromToWithStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anotherObject,
		final int startIndex2)
	{
		return anotherObject
			.compareFromToWithSmallIntegerIntervalTupleStartingAt(
				startIndex2,
				startIndex2 + endIndex1 - startIndex1,
				object,
				startIndex1);
	}

	@Override @AvailMethod
	protected boolean o_CompareFromToWithSmallIntegerIntervalTupleStartingAt (
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
			return startIndex1 == startIndex2;
		}

		// Finally, check the subranges.
		final A_Tuple first = object.copyTupleFromToCanDestroy(
			startIndex1,
			endIndex1,
			false);
		final A_Tuple second =
			aSmallIntegerIntervalTuple.copyTupleFromToCanDestroy(
				startIndex2,
				startIndex2 + endIndex1 - startIndex1,
				false);
		return first.equals(second);
	}

	@Override
	protected A_Tuple o_ConcatenateWith (
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
			final long delta = object.slot(DELTA);

			// If the other's delta is the same as mine,
			if (delta == otherDirect.slot(DELTA))
			{
				final long newSize = object.slot(SIZE) + otherDirect.slot(SIZE);
				// and the other's start is one delta away from my end,
				if (object.slot(END) + delta == otherDirect.slot(START)
					&& newSize == (int) newSize)
				{
					// then we're adjacent.

					// If we can do replacement in place,
					// use me for the return value.
					if (isMutable())
					{
						object.setSlot(END, otherDirect.slot(END));
						object.setSlot(SIZE, (int) newSize);
						object.hashOrZero(0);
						return object;
					}
					// Or the other one.
					if (otherDirect.descriptor().isMutable())
					{
						otherDirect.setSlot(START, object.slot(START));
						otherDirect.setSlot(SIZE, (int) newSize);
						otherDirect.hashOrZero(0);
						return otherDirect;
					}

					// Otherwise, create a new interval.
					return createSmallInterval(
						object.slot(START),
						otherDirect.slot(END),
						delta);
				}
			}
		}
		if (otherTuple.treeTupleLevel() == 0)
		{
			return createTwoPartTreeTuple(object, otherTuple, 1, 0);
		}
		return concatenateAtLeastOneTree(object, otherTuple, true);
	}

	@Override @AvailMethod
	protected A_Tuple o_CopyTupleFromToCanDestroy (
		final AvailObject object,
		final int start,
		final int end,
		final boolean canDestroy)
	{
		// Ensure parameters are in bounds
		final int oldSize = object.slot(SIZE);
		assert 1 <= start && start <= end + 1 && end <= oldSize;
		final int newSize = end - start + 1;
		if (newSize == oldSize)
		{
			// This method is requesting a full copy of the original.
			if (isMutable() && !canDestroy)
			{
				object.makeImmutable();
			}
			return object;
		}

		// The request is for a proper subrange.
		final long delta = object.slot(DELTA);
		final int oldStartValue = object.slot(START);

		final long newStartValue = oldStartValue + delta * (start - 1);
		assert newStartValue == (int) newStartValue;
		final long newEndValue = newStartValue + delta * (newSize - 1);
		assert newEndValue == (int) newEndValue;

		if (isMutable() && canDestroy)
		{
			// Recycle the object.
			object.setSlot(START, (int) newStartValue);
			object.setSlot(END, (int) newEndValue);
			object.setSlot(SIZE, newSize);
			return object;
		}
		return createSmallInterval((int) newStartValue, (int) newEndValue, delta);

	}

	@Override @AvailMethod
	public boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsSmallIntegerIntervalTuple(object);
	}

	@Override @AvailMethod
	protected boolean o_EqualsSmallIntegerIntervalTuple (
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

	@Override @AvailMethod
	public boolean o_IsSmallIntegerIntervalTuple(final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	protected AvailObject o_TupleAt (final AvailObject object, final int index)
	{
		// Answer the value at the given index in the tuple object.
		// START + (index-1) × DELTA
		assert index >= 1 && index <= object.tupleSize();
		final long temp = object.slot(START) + (index - 1) * object.slot(DELTA);
		assert temp == (int) temp;
		return fromInt((int) temp);
	}

	@Override @AvailMethod
	protected A_Tuple o_TupleAtPuttingCanDestroy (
		final AvailObject object,
		final int index,
		final A_BasicObject newValueObject,
		final boolean canDestroy)
	{
		// Answer a tuple with all the elements of object except at the given
		// index we should have newValueObject. This may destroy the original
		// tuple if canDestroy is true.
		assert index >= 1 && index <= object.tupleSize();
		if (newValueObject.isInt()
			&& object.tupleIntAt(index)
				== ((A_Number)newValueObject).extractInt())
		{
			// The element is to be replaced with itself.
			if (!canDestroy)
			{
				object.makeImmutable();
			}
			return object;
		}
		final int start = object.slot(START);
		final int end = object.slot(END);
		final long delta = object.slot(DELTA);
		final AvailObject result;
		if ((start & ~255) == 0
			&& (end & ~255) == 0
			&& newValueObject.isUnsignedByte())
		{
			// Everything will be bytes.  Synthesize a byte tuple.
			result = generateByteTupleFrom(
				object.slot(SIZE),
				counter ->
					counter == index
						? ((A_Number)newValueObject).extractUnsignedByte()
						: start + (int)((counter - 1) * delta));
		}
		else
		{
			// Synthesize a general object tuple instead.
			result = generateObjectTupleFrom(
				object.slot(SIZE),
				counter ->
					counter == index
						? newValueObject
						: fromInt(start + (int)((counter - 1) * delta)));
		}
		if (!canDestroy)
		{
			object.makeImmutable();
		}
		return result;
	}

	@Override
	protected boolean o_TupleElementsInRangeAreInstancesOf (
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
		return type.isSupertypeOfIntegerRangeType(inclusive(low, high))
			|| super.o_TupleElementsInRangeAreInstancesOf(
				object, startIndex, endIndex, type);
	}

	@Override @AvailMethod
	protected int o_TupleIntAt (final AvailObject object, final int index)
	{
		// Answer the value at the given index in the tuple object.
		// START + (index-1) × DELTA
		assert index >= 1 && index <= object.tupleSize();
		long temp = index - 1;
		temp *= object.slot(DELTA);
		temp += object.slot(START);
		assert temp == (int) temp;
		return (int) temp;
	}

	@Override @AvailMethod
	protected A_Tuple o_TupleReverse(final AvailObject object)
	{
		final long newDelta = 0 - object.slot(DELTA);
		// If tuple is small enough or is immutable, create a new interval.
		if (object.tupleSize() < maximumCopySize || !isMutable())
		{
			return createSmallInterval(
				object.slot(END),
				object.slot(START),
				newDelta);
		}

		//The interval is mutable and large enough to warrant changing in place.
		final int newStart = object.slot(END);
		final int newEnd = object.slot(START);
		object.setSlot(START, newStart);
		object.setSlot(END, newEnd);
		object.setSlot(DELTA, newDelta);
		return object;
	}

	@Override @AvailMethod
	protected int o_TupleSize (final AvailObject object)
	{
		return object.slot(SIZE);
	}

	/** The mutable {@link SmallIntegerIntervalTupleDescriptor}. */
	public static final SmallIntegerIntervalTupleDescriptor mutable =
		new SmallIntegerIntervalTupleDescriptor(Mutability.MUTABLE);

	@Override
	public SmallIntegerIntervalTupleDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link IntegerIntervalTupleDescriptor}. */
	private static final SmallIntegerIntervalTupleDescriptor immutable =
		new SmallIntegerIntervalTupleDescriptor(Mutability.IMMUTABLE);

	@Override
	public SmallIntegerIntervalTupleDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link SmallIntegerIntervalTupleDescriptor}. */
	private static final SmallIntegerIntervalTupleDescriptor shared =
		new SmallIntegerIntervalTupleDescriptor(Mutability.SHARED);

	@Override
	public SmallIntegerIntervalTupleDescriptor shared ()
	{
		return shared;
	}

	/**
	 * Construct a new {@code SmallIntegerIntervalTupleDescriptor}.
	 *
	 * @param mutability The mutability of the descriptor.
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
	static boolean isSmallIntervalCandidate (
		final A_Number newStart,
		final A_Number newEnd,
		final A_Number delta)
	{
		// Size is always an integer, so no need to check it.
		if (!newStart.isInt() || !newEnd.isInt() || !delta.isInt())
		{
			return false;
		}
		final long size =
			(newEnd.extractLong() - newStart.extractLong())
					/ delta.extractInt()
				+ 1L;
		// Watch out for the case that they're all ints, but the size is bigger
		// than Integer.MAX_VALUE.  (e.g., -2 billion to +2 billion has a size
		// of 4 billion, which is bigger than a signed int can hold.
		return size == (int) size;
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
	public static A_Tuple createSmallInterval (
		final int newStart,
		final int newEnd,
		final long delta)
	{
		assert delta != 0;
		final long size = ((long) newEnd - (long) newStart) / delta + 1L;
		assert size == (int) size
			: "Proposed tuple has too many elements";
		final long adjustedEnd = newStart + delta * ((int) size - 1);
		assert adjustedEnd == (int) adjustedEnd;
		final AvailObject interval = mutable.create();
		interval.setSlot(START, newStart);
		interval.setSlot(END, (int) adjustedEnd);
		interval.setSlot(DELTA, delta);
		interval.setSlot(HASH_OR_ZERO, 0);
		interval.setSlot(SIZE, (int) size);
		return interval;
	}
}
