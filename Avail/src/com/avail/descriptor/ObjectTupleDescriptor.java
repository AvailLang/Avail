/**
 * ObjectTupleDescriptor.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

import static com.avail.descriptor.ObjectTupleDescriptor.ObjectSlots.*;
import static com.avail.descriptor.ObjectTupleDescriptor.IntegerSlots.*;
import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.AvailObjectRepresentation.newLike;
import com.avail.annotations.*;

/**
 * This is a representation for {@linkplain TupleDescriptor tuples} that can
 * consist of arbitrary {@link AvailObject}s.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class ObjectTupleDescriptor
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
		HASH_OR_ZERO;

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
		 * The tuple elements themselves.
		 */
		TUPLE_AT_
	}

	/**
	 * Defined threshold for making copies versus using {@linkplain
	 * TreeTupleDescriptor}/using other forms of reference instead of creating
	 * an new tuple.
	 */
	private static final int minimumCopySize = 32;

	@Override @AvailMethod
	AvailObject o_TupleAt (final AvailObject object, final int subscript)
	{
		return object.slot(TUPLE_AT_, subscript);
	}

	/**
	 * @param object
	 * @param index
	 * @param anObject
	 */
	@Override @AvailMethod
	void o_ObjectTupleAtPut (
		final AvailObject object,
		final int index,
		final A_BasicObject anObject)
	{
		object.setSlot(TUPLE_AT_, index, anObject);
	}

	@Override @AvailMethod
	boolean o_CompareFromToWithStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anotherObject,
		final int startIndex2)
	{
		return anotherObject.compareFromToWithObjectTupleStartingAt(
			startIndex2,
			(startIndex2 + endIndex1 - startIndex1),
			object,
			startIndex1);
	}

	@Override @AvailMethod
	boolean o_CompareFromToWithObjectTupleStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final A_Tuple anObjectTuple,
		final int startIndex2)
	{
		// Compare sections of two object tuples.
		if (object.sameAddressAs(anObjectTuple) && startIndex1 == startIndex2)
		{
			return true;
		}
		// Compare actual entries.
		for (
			int index1 = startIndex1, index2 = startIndex2;
			index1 <= endIndex1;
			index1++, index2++)
		{
			if (!object.tupleAt(index1).equals(anObjectTuple.tupleAt(index2)))
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsObjectTuple(object);
	}

	@Override @AvailMethod
	boolean o_EqualsObjectTuple (
		final AvailObject object,
		final A_Tuple anObjectTuple)
	{
		if (object.sameAddressAs(anObjectTuple))
		{
			return true;
		}
		if (o_TupleSize(object) != anObjectTuple.tupleSize())
		{
			return false;
		}
		if (o_Hash(object) != anObjectTuple.hash())
		{
			return false;
		}
		if (!object.compareFromToWithObjectTupleStartingAt(
			1,
			object.tupleSize(),
			anObjectTuple,
			1))
		{
			return false;
		}
		if (anObjectTuple.isBetterRepresentationThan(object))
		{
			if (!isShared())
			{
				anObjectTuple.makeImmutable();
				object.becomeIndirectionTo(anObjectTuple);
			}
		}
		else
		{
			if (!anObjectTuple.descriptor().isShared())
			{
				object.makeImmutable();
				anObjectTuple.becomeIndirectionTo(object);
			}
		}
		return true;
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
		// Answer the integer element at the given index in the tuple object.
		return object.tupleAt(index).extractInt();
	}

	@Override @AvailMethod
	A_Tuple o_TupleReverse(final AvailObject object)
	{
		if (o_TupleSize(object) >= minimumCopySize)
		{
			return super.o_TupleReverse(object);
		}

		final int size = object.tupleSize();
		final AvailObject instance =
			mutable.create(size);
		instance.hashOrZero(object.hashOrZero());
		for (int i = 1; i <= size; i++)
		{
			instance.objectTupleAtPut(size-i+1, object.tupleAt(i));
		}
		return instance;
	}

	@Override @AvailMethod
	int o_TupleSize (final AvailObject object)
	{
		// Answer the number of elements in the object (as a Java int).
		return object.variableObjectSlotsCount();
	}

	@Override @AvailMethod
	int o_BitsPerEntry (final AvailObject object)
	{
		// Answer approximately how many bits per entry are taken up by this
		// object.
		return 32;
	}

	@Override @AvailMethod
	int o_ComputeHashFromTo (
		final AvailObject object,
		final int start,
		final int end)
	{
		// See comment in superclass.  This method must produce the same value.
		int hash = 0;
		for (int index = end; index >= start; index--)
		{
			final int itemHash = object.tupleAt(index).hash();
			hash = hash * multiplier + (itemHash ^ preToggle);
		}
		return hash * multiplier;
	}

	@Override @AvailMethod
	A_Tuple o_ConcatenateWith (
		final AvailObject object,
		final A_Tuple otherTuple,
		final boolean canDestroy)
	{
		final int size1 = object.tupleSize();
		if (size1 == 0)
		{
			if (!canDestroy)
			{
				otherTuple.makeImmutable();
			}
			return otherTuple;
		}
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
				result.setSlot(TUPLE_AT_, dest, otherTuple.tupleAt(src));
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

	/**
	 * If a subrange ends up getting constructed from this object tuple then it
	 * may leak memory.  The references that are out of bounds of the subrange
	 * might no longer be semantically reachable by Avail, but Java won't be
	 * able to collect them.  Eventually we'll have an Avail-specific garbage
	 * collector again, at which point we'll solve this problem for real – along
	 * with many others, I'm sure.
	 */
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
		final int size = end - start + 1;
		if (size > 0 && size < tupleSize && size < minimumCopySize)
		{
			// It's not empty, it's not a total copy, and it's reasonably small.
			// Just copy the applicable entries out.  In theory we could use
			// newLike() if start is 1.
			final AvailObject result = createUninitialized(size);
			int dest = 1;
			for (int src = start; src <= end; src++, dest++)
			{
				result.setSlot(TUPLE_AT_, dest, object.slot(TUPLE_AT_, src));
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
		return super.o_CopyTupleFromToCanDestroy(
			object, start, end, canDestroy);
	}

	/**
	 * Create an {@linkplain ObjectTupleDescriptor object tuple} whose slots
	 * have not been initialized.
	 *
	 * @param size The number of elements in the resulting tuple.
	 * @return An uninitialized object tuple of the requested size.
	 */
	public static AvailObject createUninitialized (final int size)
	{
		return mutable.create(size);
	}

	/**
	 * Construct a new {@link ObjectTupleDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private ObjectTupleDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, IntegerSlots.class);
	}

	/** The mutable {@link ObjectTupleDescriptor}. */
	public static final ObjectTupleDescriptor mutable =
		new ObjectTupleDescriptor(Mutability.MUTABLE);

	@Override
	ObjectTupleDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link ObjectTupleDescriptor}. */
	private static final ObjectTupleDescriptor immutable =
		new ObjectTupleDescriptor(Mutability.IMMUTABLE);

	@Override
	ObjectTupleDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link ObjectTupleDescriptor}. */
	private static final ObjectTupleDescriptor shared =
		new ObjectTupleDescriptor(Mutability.SHARED);

	@Override
	ObjectTupleDescriptor shared ()
	{
		return shared;
	}
}
