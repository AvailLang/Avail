/**
 * RepeatedElementTupleDescriptor.java
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

import static com.avail.descriptor.RepeatedElementTupleDescriptor.IntegerSlots.*;
import static com.avail.descriptor.RepeatedElementTupleDescriptor.ObjectSlots.*;
import java.util.ArrayList;
import java.util.List;
import com.avail.annotations.AvailMethod;
import com.avail.annotations.HideFieldInDebugger;

/**
 * {@code RepeatedElementTupleDescriptor} represents a tuple with a single
 * ELEMENT repeated SIZE times. Note that SIZE is the number of tuple slots
 * containing the element and is therefore the size of the tuple.
 *
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 */
public class RepeatedElementTupleDescriptor
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

		/**
		 * The number of elements in the tuple.
		 *
		 * The API's {@link AvailObject#tupleSize() tuple size accessor}
		 * currently returns a Java integer, because there wasn't much of a
		 * problem limiting manually-constructed tuples to two billion elements.
		 * This restriction will eventually be removed.
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
		/** The element to be repeated. */
		ELEMENT
	}

	/**
	 * The minimum size for repeated element tuple creation. All tuples
	 * requested below this size will be created as standard tuples or the empty
	 * tuple.
	 */
	private static int minimumRepeatSize = 2;

	@Override @AvailMethod
	public boolean o_IsRepeatedElementTuple(final AvailObject object)
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
			if (isMutable() && canDestroy)
			{
				// Recycle the object.
				object.setSlot(SIZE, newSize);
				return object;
			}
			return createRepeatedElementTuple(newSize, object.slot(ELEMENT));
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
		return anotherObject.compareFromToWithRepeatedElementTupleStartingAt(
			startIndex2,
			startIndex2 + endIndex1 - startIndex1,
			object,
			startIndex1);
	}

	@Override @AvailMethod
	boolean o_CompareFromToWithRepeatedElementTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple aRepeatedElementTuple,
		final int startIndex2)
	{
		// If the objects refer to the same memory, and the indices match
		// up, the subranges are the same.
		if (object.sameAddressAs(aRepeatedElementTuple) &&
			startIndex1 == startIndex2)
		{
			return true;
		}

		// If the objects do not refer to the same memory but the tuples are
		// identical,
		if (object.equals(aRepeatedElementTuple))
		{
			// indirect one to the other if it is not shared.
			if (!isShared())
			{
				aRepeatedElementTuple.makeImmutable();
				object.becomeIndirectionTo(aRepeatedElementTuple);
			}
			else if (!aRepeatedElementTuple.descriptor().isShared())
			{
				object.makeImmutable();
				aRepeatedElementTuple.becomeIndirectionTo(object);
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
		final A_Tuple second = aRepeatedElementTuple.copyTupleFromToCanDestroy(
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

		// Assess the possibility that the concatenation will still be a
		// repeated element tuple.
		if (otherTuple.isRepeatedElementTuple())
		{
			final AvailObject otherDirect = otherTuple.traversed();
			final AvailObject element = object.slot(ELEMENT);

			// If the other's element is the same as mine,
			if (element.equals(otherDirect.slot(ELEMENT)))
			{
				// then we can be concatenated.
				final int newSize = object.slot(SIZE) +
					otherDirect.slot(SIZE);

				// If we can do replacement in place,
				// use me for the return value.
				if (isMutable())
				{
					object.setSlot(SIZE, newSize);
					object.hashOrZero(0);
					return object;
				}
				// Or the other one.
				if (otherTuple.descriptor().isMutable())
				{
					otherDirect.setSlot(SIZE, newSize);
					otherDirect.hashOrZero(0);
					return otherDirect;
				}

				// Otherwise, create a new repeated element tuple.
				return createRepeatedElementTuple(newSize, object.slot(ELEMENT));
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
		return another.equalsRepeatedElementTuple(object);
	}

	@Override @AvailMethod
	boolean o_EqualsRepeatedElementTuple (
		final AvailObject object,
		final A_Tuple aRepeatedElementTuple)
	{
		// First, check for object-structure (address) identity.
		if (object.sameAddressAs(aRepeatedElementTuple))
		{
			return true;
		}

		// If the objects do not refer to the same memory, check if the tuples
		// are identical.
		final AvailObject firstTraversed = object.traversed();
		final AvailObject secondTraversed = aRepeatedElementTuple.traversed();

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
		if (!firstTraversed.slot(ELEMENT).equals(secondTraversed.slot(ELEMENT)))
		{
			return false;
		}

		// All the slots match. Indirect one to the other if it is not shared.
		if (!isShared())
		{
			aRepeatedElementTuple.makeImmutable();
			object.becomeIndirectionTo(aRepeatedElementTuple);
		}
		else if (!aRepeatedElementTuple.descriptor().isShared())
		{
			object.makeImmutable();
			aRepeatedElementTuple.becomeIndirectionTo(object);
		}
		return true;

	}

	@Override
	int o_BitsPerEntry (final AvailObject object)
	{
		// Consider a billion-element tuple. Since a repeated element tuple
		// requires only O(1) storage, irrespective of its size, the average
		// bits per entry is 0.
		return 0;
	}

	@Override @AvailMethod
	AvailObject o_TupleAt (final AvailObject object, final int index)
	{
		// Answer the value at the given index in the tuple object.
		// Every element in this tuple is identical.
		return object.slot(ELEMENT);
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
		return object.slot(ELEMENT).extractInt();
	}

	@Override @AvailMethod
	A_Tuple o_TupleReverse(final AvailObject object)
	{
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
		return object.slot(ELEMENT).isInstanceOf(type);
	}

	/** The mutable {@link RepeatedElementTupleDescriptor}. */
	public static final RepeatedElementTupleDescriptor mutable =
		new RepeatedElementTupleDescriptor(Mutability.MUTABLE);

	@Override
	RepeatedElementTupleDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link RepeatedElementTupleDescriptor}. */
	private static final RepeatedElementTupleDescriptor immutable =
		new RepeatedElementTupleDescriptor(Mutability.IMMUTABLE);

	@Override
	RepeatedElementTupleDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link RepeatedElementTupleDescriptor}. */
	private static final RepeatedElementTupleDescriptor shared =
		new RepeatedElementTupleDescriptor(Mutability.SHARED);

	@Override
	RepeatedElementTupleDescriptor shared ()
	{
		return shared;
	}

	/**
	 * Construct a new {@link RepeatedElementTupleDescriptor}.
	 *
	 * @param mutability
	 */
	private RepeatedElementTupleDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, IntegerSlots.class);
	}

	/**
	 * Create a new repeated element tuple according to the parameters.
	 *
	 * @param size The number of repetitions of the element.
	 * @param element The value to be repeated.
	 * @return The new repeated element tuple.
	 */
	public static A_Tuple createRepeatedElementTuple (
		final int size,
		final A_BasicObject element)
	{
		// If there are no members in the range, return the empty tuple.
		if (size == 0)
		{
			return TupleDescriptor.empty();
		}

		// If there are fewer than minimumRepeatSize members in this tuple,
		// create a normal tuple with them in it instead.
		if (size < minimumRepeatSize)
		{
			final List<A_BasicObject> members =
				new ArrayList<A_BasicObject>(size);
			for (int i = 0; i < size; i++)
			{
				members.add(element);
			}
			return TupleDescriptor.fromList(members);
		}

		// No other efficiency shortcuts. Create a repeated element tuple.
		return forceCreate(size, element);
	}

	/**
	 * Create a new RepeatedElement using the supplied arguments,
	 * regardless of the suitability of other representations.
	 *
	 * @param size The number of repetitions of the element.
	 * @param element The value to be repeated.
	 * @return The new repeated element tuple.
	 */
	static A_Tuple forceCreate (
		final int size,
		final A_BasicObject element)
	{
		final AvailObject repeatedElementTuple = mutable.create();
		repeatedElementTuple.setSlot(HASH_OR_ZERO, 0);
		repeatedElementTuple.setSlot(SIZE, size);
		repeatedElementTuple.setSlot(ELEMENT, element);
		return repeatedElementTuple;
	}
}
