/*
 * IntegerIntervalTupleDescriptor.java
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

import com.avail.annotations.HideFieldInDebugger;
import com.avail.descriptor.JavaCompatibility.IntegerSlotsEnumJava;
import com.avail.descriptor.JavaCompatibility.ObjectSlotsEnumJava;
import com.avail.descriptor.numbers.A_Number;
import com.avail.descriptor.representation.A_BasicObject;
import com.avail.descriptor.representation.AvailObject;
import com.avail.descriptor.representation.BitField;
import com.avail.descriptor.representation.Mutability;
import com.avail.descriptor.types.A_Type;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import static com.avail.descriptor.numbers.IntegerDescriptor.fromInt;
import static com.avail.descriptor.numbers.IntegerDescriptor.zero;
import static com.avail.descriptor.representation.AvailObjectRepresentation.newLike;
import static com.avail.descriptor.tuples.IntegerIntervalTupleDescriptor.IntegerSlots.HASH_OR_ZERO;
import static com.avail.descriptor.tuples.IntegerIntervalTupleDescriptor.IntegerSlots.SIZE;
import static com.avail.descriptor.tuples.IntegerIntervalTupleDescriptor.ObjectSlots.DELTA;
import static com.avail.descriptor.tuples.IntegerIntervalTupleDescriptor.ObjectSlots.END;
import static com.avail.descriptor.tuples.IntegerIntervalTupleDescriptor.ObjectSlots.START;
import static com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.tuples.ObjectTupleDescriptor.tupleFromList;
import static com.avail.descriptor.tuples.SmallIntegerIntervalTupleDescriptor.createSmallInterval;
import static com.avail.descriptor.tuples.SmallIntegerIntervalTupleDescriptor.isSmallIntervalCandidate;
import static com.avail.descriptor.tuples.TreeTupleDescriptor.concatenateAtLeastOneTree;
import static com.avail.descriptor.tuples.TreeTupleDescriptor.createTwoPartTreeTuple;
import static com.avail.descriptor.types.IntegerRangeTypeDescriptor.inclusive;

/**
 * {@code IntegerIntervalTupleDescriptor} represents an ordered tuple of integers that each differ from their predecessor by DELTA, an integer value.
 *
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 */
public final class IntegerIntervalTupleDescriptor
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
		HASH_AND_MORE;

		/**
		 * The number of elements in the tuple.
		 *
		 * The API's {@link AvailObject#tupleSize() tuple size accessor} currently returns a Java integer, because there wasn't much of a problem limiting manually-constructed tuples to two billion elements. This restriction will eventually be removed.
		 */
		public static final BitField SIZE = new BitField(HASH_AND_MORE, 32, 32);

		/**
		 * A slot to hold the cached hash value of a tuple.  If zero, then the
		 * hash value must be computed upon request.  Note that in the very rare
		 * case that the hash value actually equals zero, the hash value has to
		 * be computed every time it is requested.
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
		DELTA
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		object.slot(START).printOnAvoidingIndent(aStream, recursionMap, indent);
		aStream.append(" to ");
		object.slot(END).printOnAvoidingIndent(aStream, recursionMap, indent);
		final A_Number delta = object.slot(DELTA);
		if (!delta.equalsInt(1))
		{
			aStream.append(" by ");
			delta.printOnAvoidingIndent(aStream, recursionMap, indent);
		}
	}

	/**
	 * The minimum size for integer interval tuple creation. All tuples
	 * requested below this size will be created as standard tuples or the empty
	 * tuple.
	 */
	private static final int maximumCopySize = 4;

	@Override
	public A_Tuple o_AppendCanDestroy (
		final AvailObject object,
		final A_BasicObject newElement,
		final boolean canDestroy)
	{
		final int originalSize = object.tupleSize();
		final A_Number endValue = object.slot(END);
		final A_Number deltaValue = object.slot(DELTA);
		final A_Number nextValue = endValue.plusCanDestroy(deltaValue, false);
		if (newElement.equals(nextValue))
		{
			final AvailObject result = canDestroy && isMutable()
				? object
				: newLike(mutable, object, 0, 0);
			result.setSlot(END, newElement);
			result.setSlot(SIZE, originalSize + 1);
			result.setSlot(HASH_OR_ZERO, 0);
			return result;
		}
		// Transition to a tree tuple.
		final A_Tuple singleton = tuple(newElement);
		return object.concatenateWith(singleton, canDestroy);
	}

	@Override
	public int o_BitsPerEntry (final AvailObject object)
	{
		// Consider a billion element tuple. Since an interval tuple requires
		// only O(1) storage, irrespective of its size, the average bits per
		// entry is 0.
		return 0;
	}

	@Override
	public boolean o_CompareFromToWithIntegerIntervalTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anIntegerIntervalTuple,
		final int startIndex2)
	{
		// If the objects refer to the same memory, and the indices match
		// up, the subranges are the same.
		if (object.sameAddressAs(anIntegerIntervalTuple) &&
			startIndex1 == startIndex2)
		{
			return true;
		}

		// If the objects do not refer to the same memory but the tuples are
		// identical,
		if (object.equals(anIntegerIntervalTuple))
		{
			// indirect one to the other if it is not shared.
			if (!isShared())
			{
				anIntegerIntervalTuple.makeImmutable();
				object.becomeIndirectionTo(anIntegerIntervalTuple);
			}
			else if (!anIntegerIntervalTuple.descriptor().isShared())
			{
				object.makeImmutable();
				anIntegerIntervalTuple.becomeIndirectionTo(object);
			}

			// If the subranges start at the same place, they are the same.
			return startIndex1 == startIndex2;
		}

		// Finally, check the subranges.
		final A_Tuple first = object.copyTupleFromToCanDestroy(
			startIndex1,
			endIndex1,
			false);
		final A_Tuple second = anIntegerIntervalTuple.copyTupleFromToCanDestroy(
			startIndex2,
			startIndex2 + endIndex1 - startIndex1,
			false);
		return first.equals(second);
	}

	@Override
	public boolean o_CompareFromToWithStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anotherObject,
		final int startIndex2)
	{
		return anotherObject.compareFromToWithIntegerIntervalTupleStartingAt(
			startIndex2,
			startIndex2 + endIndex1 - startIndex1,
			object,
			startIndex1);
	}

	@Override
	public A_Tuple o_CopyTupleFromToCanDestroy (
		final AvailObject object,
		final int start,
		final int end,
		final boolean canDestroy)
	{
		// Ensure parameters are in bounds
		final int oldSize = object.slot(SIZE);
		assert 1 <= start && start <= end + 1 && end <= oldSize;

		// If the requested copy is a proper subrange, create it.
		final int newSize = end - start + 1;
		if (newSize != oldSize)
		{
			final AvailObject delta = object.slot(DELTA).makeImmutable();
			final AvailObject oldStartValue = object.slot(START);

			final AvailObject newStartValue =
				(AvailObject) oldStartValue.plusCanDestroy(
					fromInt(start - 1).multiplyByIntegerCanDestroy(delta, true),
					canDestroy);
			final AvailObject newEndValue =
				(AvailObject) newStartValue.plusCanDestroy(
					fromInt(newSize - 1).multiplyByIntegerCanDestroy(
						delta, true),
					false);

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

	@Override
	public A_Tuple o_ConcatenateWith (
		final AvailObject object,
		final A_Tuple otherTuple,
		final boolean canDestroy)
	{
		if (!canDestroy)
		{
			object.makeImmutable();
			otherTuple.makeImmutable();
		}

		// Assess the possibility that the concatenation will still be an
		// integer interval tuple.
		if (otherTuple.isIntegerIntervalTuple())
		{
			final AvailObject otherDirect = otherTuple.traversed();
			final AvailObject delta = object.slot(DELTA);

			// If the other's delta is the same as mine,
			if (delta.equals(otherDirect.slot(DELTA)))
			{
				// and the other's start is one delta away from my end,
				if (object.slot(END).plusCanDestroy(delta, false)
					.equals(otherDirect.slot(START)))
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
						object.setHashOrZero(0);
						return object;
					}
					// Or the other one.
					if (otherTuple.descriptor().isMutable())
					{
						otherDirect.setSlot(START, object.slot(START));
						otherDirect.setSlot(SIZE, newSize);
						otherDirect.setHashOrZero(0);
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
			return createTwoPartTreeTuple(object, otherTuple, 1, 0);
		}
		return concatenateAtLeastOneTree(object, otherTuple, true);
	}

	@Override
	public boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsIntegerIntervalTuple(object);
	}

	@Override
	public boolean o_EqualsIntegerIntervalTuple (
		final AvailObject object,
		final A_Tuple anIntegerIntervalTuple)
	{
		// First, check for object-structure (address) identity.
		if (object.sameAddressAs(anIntegerIntervalTuple))
		{
			return true;
		}

		// If the objects do not refer to the same memory, check if the tuples
		// are identical.
		final AvailObject firstTraversed = object.traversed();
		final AvailObject secondTraversed = anIntegerIntervalTuple.traversed();

		// Check that the slots match.
		final int firstHash = firstTraversed.slot(HASH_OR_ZERO);
		final int secondHash = secondTraversed.slot(HASH_OR_ZERO);
		if (firstHash != 0 && secondHash != 0 && firstHash != secondHash)
		{
			return false;
		}
		// Since we have SIZE as int, it's cheaper to check it than END.
		if (firstTraversed.slot(SIZE) != secondTraversed.slot(SIZE))
		{
			return false;
		}
		if (!firstTraversed.slot(DELTA).equals(secondTraversed.slot(DELTA)))
		{
			return false;
		}
		if (!firstTraversed.slot(START).equals(secondTraversed.slot(START)))
		{
			return false;
		}

		// All the slots match. Indirect one to the other if it is not shared.
		if (!isShared())
		{
			anIntegerIntervalTuple.makeImmutable();
			object.becomeIndirectionTo(anIntegerIntervalTuple);
		}
		else if (!anIntegerIntervalTuple.descriptor().isShared())
		{
			object.makeImmutable();
			anIntegerIntervalTuple.becomeIndirectionTo(object);
		}
		return true;

	}

	@Override
	public boolean o_IsIntegerIntervalTuple(final AvailObject object)
	{
		return true;
	}

	@Override
	public AvailObject o_TupleAt (final AvailObject object, final int index)
	{
		// Answer the value at the given index in the tuple object.
		// START + (index-1) * DELTA
		assert index >= 1 && index <= object.tupleSize();
		A_Number temp = fromInt(index - 1);
		temp = temp.timesCanDestroy(object.slot(DELTA), false);
		temp = temp.plusCanDestroy(object.slot(START), false);
		return (AvailObject) temp;
	}

	@Override
	public A_Tuple o_TupleAtPuttingCanDestroy (
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
		final A_Tuple result =
			object.copyAsMutableObjectTuple().tupleAtPuttingCanDestroy(
				index, newValueObject, true);
		if (!canDestroy)
		{
			object.makeImmutable();
		}
		return result;
	}

	@Override
	public boolean o_TupleElementsInRangeAreInstancesOf (
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

	@Override
	public int o_TupleIntAt (final AvailObject self, final int index)
	{
		// Answer the value at the given index in the tuple object.
		return self.tupleAt(index).extractInt();
	}

	@Override
	public A_Tuple o_TupleReverse(final AvailObject object)
	{
		//If tuple is small enough or is immutable, create a new Interval
		if (object.tupleSize() < maximumCopySize || !isMutable())
		{
			final A_Number newDelta =
				object.slot(DELTA).timesCanDestroy(fromInt(-1), true);
			return forceCreate (
				object.slot(END),
				object.slot(START),
				newDelta,
				o_TupleSize(object));
		}

		//The interval is mutable and large enough to warrant changing in place.
		final A_Number newStart = object.slot(END);
		final A_Number newEnd = object.slot(START);
		final A_Number newDelta =
			object.slot(DELTA).timesCanDestroy(fromInt(-1), true);

		object.setSlot(START, newStart);
		object.setSlot(END, newEnd);
		object.setSlot(DELTA, newDelta);
		return object;
	}

	@Override
	public int o_TupleSize (final AvailObject object)
	{
		return object.slot(SIZE);
	}

	/**
	 * Create a new interval according to the parameters.
	 *
	 * @param start
	 * The first integer in the interval.
	 * @param end
	 * The last allowable integer in the interval.
	 * @param delta
	 *   The difference between an integer and its subsequent neighbor in the
	 *   interval. Delta is nonzero.
	 * @return
	 * The new interval.
	 */
	public static A_Tuple createInterval (
		final AvailObject start,
		final AvailObject end,
		final AvailObject delta)
	{
		assert !delta.equalsInt(0);

		final A_Number difference = end.minusCanDestroy(start, false);
		final A_Number zero = zero();

		// If there is only one member in the range, return that integer in
		// its own tuple.
		if (difference.equalsInt(0))
		{
			return tuple(start);
		}

		// If the progression is in a different direction than the delta, there
		// are no members of this interval, so return the empty tuple.
		if (difference.greaterThan(zero) != delta.greaterThan(zero))
		{
			return emptyTuple();
		}

		// If there are fewer than maximumCopySize members in this interval,
		// create a normal tuple with them in it instead of an interval tuple.
		final int size =
			1 + difference.divideCanDestroy(delta, false).extractInt();
		if (size < maximumCopySize)
		{
			final List<A_Number> members = new ArrayList<>(size);
			A_Number newMember = start;
			for (int i = 0; i < size; i++)
			{
				members.add(newMember);
				newMember = newMember.addToIntegerCanDestroy(delta, false);
			}
			return tupleFromList(members);
		}

		// If the slot contents are small enough, create a
		// SmallIntegerIntervalTuple.
		if (isSmallIntervalCandidate(start, end, delta))
		{
			return createSmallInterval(
				start.extractInt(), end.extractInt(), delta.extractInt());
		}

		// No other efficiency shortcuts. Normalize end, and create a range.
		final A_Number adjustedEnd = start.plusCanDestroy(
			delta.timesCanDestroy(fromInt(size - 1), false), false);
		return forceCreate(start, adjustedEnd, delta, size);
	}

	/**
	 * Create a new IntegerIntervalTuple using the supplied arguments,
	 * regardless of the suitability of other representations.
	 *
	 * @param start
	 * The first integer in the interval.
	 * @param normalizedEnd
	 * The last integer in the interval.
	 * @param delta
	 * The difference between an integer and its subsequent neighbor in the interval. Delta is nonzero.
	 * @param size
	 * The size of the interval, in number of elements.
	 * @return
	 * The new interval.
	 */
	private static A_Tuple forceCreate (
		final A_Number start,
		final A_Number normalizedEnd,
		final A_Number delta,
		final int size)
	{
		final AvailObject interval = mutable.create();
		interval.setSlot(HASH_OR_ZERO, 0);
		interval.setSlot(START, start.makeImmutable());
		interval.setSlot(END, normalizedEnd.makeImmutable());
		interval.setSlot(DELTA, delta.makeImmutable());
		interval.setSlot(SIZE, size);
		return interval;
	}

	/** The mutable {@link IntegerIntervalTupleDescriptor}. */
	public static final IntegerIntervalTupleDescriptor mutable =
		new IntegerIntervalTupleDescriptor(Mutability.MUTABLE);

	@Override
	public IntegerIntervalTupleDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link IntegerIntervalTupleDescriptor}. */
	private static final IntegerIntervalTupleDescriptor immutable =
		new IntegerIntervalTupleDescriptor(Mutability.IMMUTABLE);

	@Override
	public IntegerIntervalTupleDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link IntegerIntervalTupleDescriptor}. */
	private static final IntegerIntervalTupleDescriptor shared =
		new IntegerIntervalTupleDescriptor(Mutability.SHARED);

	@Override
	public IntegerIntervalTupleDescriptor shared ()
	{
		return shared;
	}

	/**
	 * Construct a new {@code IntegerIntervalTupleDescriptor}.
	 *
	 * @param mutability
	 * The mutability of the new descriptor.
	 */
	private IntegerIntervalTupleDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, IntegerSlots.class);
	}
}
