/**
 * ObjectTupleDescriptor.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

import static com.avail.descriptor.AvailObject.multiplier;
import com.avail.annotations.*;

/**
 * This is a representation for {@linkplain TupleDescriptor tuples} that can
 * consist of arbitrary {@link AvailObject}s.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class ObjectTupleDescriptor
extends TupleDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots implements IntegerSlotsEnum
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
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * The tuple elements themselves.
		 */
		TUPLE_AT_
	}

	@Override @AvailMethod
	AvailObject o_TupleAt (
		final AvailObject object,
		final int subscript)
	{
		return object.slot(ObjectSlots.TUPLE_AT_, subscript);
	}

	@Override @AvailMethod
	void o_TupleAtPut (
		final AvailObject object,
		final int subscript,
		final AvailObject value)
	{
		object.setSlot(ObjectSlots.TUPLE_AT_, subscript, value);
	}

	@Override @AvailMethod
	boolean o_CompareFromToWithStartingAt (
		final AvailObject object,
		final int startIndex1,
		final int endIndex1,
		final AvailObject anotherObject,
		final int startIndex2)
	{
		// Compare sections of two tuples.

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
		final AvailObject anObjectTuple,
		final int startIndex2)
	{
		// Compare sections of two object tuples.

		if (object.sameAddressAs(anObjectTuple) && startIndex1 == startIndex2)
		{
			return true;
		}
		// Compare actual entries.
		int index2 = startIndex2;
		for (int index1 = startIndex1; index1 <= endIndex1; index1++)
		{
			if (!object.tupleAt(index1).equals(anObjectTuple.tupleAt(index2)))
			{
				return false;
			}
			index2++;
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_Equals (
		final AvailObject object,
		final AvailObject another)
	{
		return another.equalsObjectTuple(object);
	}

	@Override @AvailMethod
	boolean o_EqualsObjectTuple (
		final AvailObject object,
		final AvailObject anObjectTuple)
	{
		// Compare this object tuple and the given object tuple.
		//
		// Compare identity...

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
		if (object.isBetterRepresentationThan(anObjectTuple))
		{
			anObjectTuple.becomeIndirectionTo(object);
			// Now there are at least two references to it.
			object.makeImmutable();
		}
		else
		{
			object.becomeIndirectionTo(anObjectTuple);
			// Now there are at least two references to it.
			anObjectTuple.makeImmutable();
		}
		return true;
	}

	@Override @AvailMethod
	AvailObject o_CopyTupleFromToCanDestroy (
		final AvailObject object,
		final int startIndex,
		final int endIndex,
		final boolean canDestroy)
	{
		// Make a tuple that only contains the given range of elements of the
		// given tuple.  If canDestroy and isMutable are true, go ahead and
		// clobber all fields of the original tuple that don't make it into the
		// subrange.  Replace these clobbered fields with the integer 0 (always
		// immutable) after dropping the reference count on replaced objects.

		assert 1 <= startIndex && startIndex <= endIndex + 1;
		final int originalSize = object.tupleSize();
		assert 0 <= endIndex && endIndex <= originalSize;
		if (isMutable && canDestroy)
		{
			if (startIndex - 1 == endIndex)
			{
				object.assertObjectUnreachableIfMutable();
				return TupleDescriptor.empty();
			}
			final AvailObject zeroObject = IntegerDescriptor.zero();
			object.hashOrZero(0);
			if (startIndex == 1 || endIndex - startIndex < 30)
			{
				if (startIndex != 1)
				{
					for (int i = 1; i < startIndex; i++)
					{
						object.tupleAt(i).assertObjectUnreachableIfMutable();
					}
					for (int i = 1; i <= endIndex - startIndex + 1; i++)
					{
						object.tupleAtPut(
							i,
							object.tupleAt(startIndex + i - 1));
					}
					for (int i = endIndex - startIndex + 2; i <= endIndex; i++)
					{
						object.tupleAtPut(i, zeroObject);
					}
				}
				for (int i = endIndex + 1; i <= originalSize; i++)
				{
					object.tupleAt(i).assertObjectUnreachableIfMutable();
					object.tupleAtPut(i, zeroObject);
				}
				object.truncateTo(endIndex - startIndex + 1);
				// Clip remaining items off end, padding lost space with a
				// dummy header.
				return object;
			}
			for (int i = 1; i < startIndex; i++)
			{
				object.tupleAt(i).assertObjectUnreachableIfMutable();
				object.tupleAtPut(i, zeroObject);
			}
			for (int i = endIndex + 1; i <= originalSize; i++)
			{
				object.tupleAt(i).assertObjectUnreachableIfMutable();
				object.tupleAtPut(i, zeroObject);
			}
		}
		if (startIndex - 1 == endIndex)
		{
			return TupleDescriptor.empty();
		}
		// Compute the hash ahead of time, because asking an element to hash
		// might trigger a garbage collection.
		final int newHash = object.computeHashFromTo(startIndex, endIndex);
		AvailObject result;
		if (endIndex - startIndex < 20)
		{
			result = mutable().create(endIndex - startIndex + 1);
			result.hashOrZero(newHash);
			for (int i = 1, end = endIndex - startIndex + 1; i <= end; i++)
			{
				result.tupleAtPut(
					i,
					object.tupleAt(i + startIndex - 1).makeImmutable());
			}
		}
		else
		{
			result = AvailObject.newObjectIndexedIntegerIndexedDescriptor(
				1,
				2,
				SpliceTupleDescriptor.mutable());
			if (isMutable && !canDestroy)
			{
				object.makeImmutable();
			}
			// Share it - play nice
			result.hashOrZero(newHash);
			result.forZoneSetSubtupleStartSubtupleIndexEndOfZone(
				1,
				object,
				startIndex,
				(endIndex - startIndex + 1));
			result.verify();
		}
		return result;
	}

	@Override @AvailMethod
	AvailObject o_TruncateTo (
		final AvailObject object,
		final int newTupleSize)
	{
		// Shrink the current object on the right.  Assumes that elements beyond
		// the new end have already been released if necessary.  Since my
		// representation no longer varies with tupleSize (I used to have
		// different descriptors for different o_Tuple sizes), I can simply
		// compute the delta for the number of slots.  I must pad the unused
		// space on the right with a dummy descriptor and slotsSize for the
		// garbage collector.
		assert isMutable;
		final int delta = object.tupleSize() - newTupleSize;
		if (delta == 0)
		{
			return object;
		}
		final int oldSlotsSize = object.objectSlotsCount();
		assert oldSlotsSize > 0;
		final int newSlotsCount = oldSlotsSize - delta;
		assert newSlotsCount > 0;
		object.truncateWithFillerForNewObjectSlotsCount(newSlotsCount);
		return object;
	}

	@Override @AvailMethod
	AvailObject o_TupleAtPuttingCanDestroy (
		final AvailObject object,
		final int index,
		final AvailObject newValueObject,
		final boolean canDestroy)
	{
		// Answer a tuple with all the elements of object except at the given
		// index we should have newValueObject.  This may destroy the original
		// tuple if canDestroy is true.
		assert index >= 1 && index <= object.tupleSize();
		if (!canDestroy || !isMutable)
		{
			return object.copyAsMutableObjectTuple().tupleAtPuttingCanDestroy(
				index,
				newValueObject,
				true);
		}
		object.tupleAtPut(index, newValueObject);
		// Invalidate the hash value.
		object.hashOrZero(0);
		return object;
	}

	@Override @AvailMethod
	int o_TupleIntAt (
		final AvailObject object,
		final int index)
	{
		// Answer the integer element at the given index in the tuple object.
		return object.tupleAt(index).extractInt();
	}

	@Override @AvailMethod
	int o_TupleSize (
		final AvailObject object)
	{
		// Answer the number of elements in the object (as a Java int).
		return object.variableObjectSlotsCount();
	}

	@Override @AvailMethod
	int o_BitsPerEntry (
		final AvailObject object)
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
			final int itemHash = object.tupleAt(index).hash() ^ preToggle;
			hash = hash * multiplier + itemHash;
		}
		return hash * multiplier;
	}

	/**
	 * Construct a new {@link ObjectTupleDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	private ObjectTupleDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link ObjectTupleDescriptor}.
	 */
	private static final ObjectTupleDescriptor mutable = new ObjectTupleDescriptor(true);

	/**
	 * Answer the mutable {@link ObjectTupleDescriptor}.
	 *
	 * @return The mutable {@link ObjectTupleDescriptor}.
	 */
	public static ObjectTupleDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link ObjectTupleDescriptor}.
	 */
	private static final ObjectTupleDescriptor immutable = new ObjectTupleDescriptor(false);

	/**
	 * Answer the immutable {@link ObjectTupleDescriptor}.
	 *
	 * @return The immutable {@link ObjectTupleDescriptor}.
	 */
	public static ObjectTupleDescriptor immutable ()
	{
		return immutable;
	}
}
