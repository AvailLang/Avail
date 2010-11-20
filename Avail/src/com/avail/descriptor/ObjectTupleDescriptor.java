/**
 * descriptor/ObjectTupleDescriptor.java
 * Copyright (c) 2010, Mark van Gulik.
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

import com.avail.descriptor.AvailObject;
import com.avail.descriptor.IntegerDescriptor;
import com.avail.descriptor.ObjectTupleDescriptor;
import com.avail.descriptor.SpliceTupleDescriptor;
import com.avail.descriptor.TupleDescriptor;
import static com.avail.descriptor.AvailObject.*;

@IntegerSlots("hashOrZero")
@ObjectSlots("tupleAt#")
public class ObjectTupleDescriptor extends TupleDescriptor
{


	// GENERATED accessors

	@Override
	public AvailObject ObjectTupleAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED getter method (indexed).

		return object.objectSlotAtByteIndex(((index * -4) + 0));
	}

	@Override
	public void ObjectTupleAtPut (
			final AvailObject object, 
			final int index, 
			final AvailObject value)
	{
		//  GENERATED setter method (indexed).

		object.objectSlotAtByteIndexPut(((index * -4) + 0), value);
	}



	// operations

	@Override
	public boolean ObjectCompareFromToWithStartingAt (
			final AvailObject object, 
			final int startIndex1, 
			final int endIndex1, 
			final AvailObject anotherObject, 
			final int startIndex2)
	{
		//  Compare sections of two tuples.

		return anotherObject.compareFromToWithObjectTupleStartingAt(
			startIndex2,
			((startIndex2 + endIndex1) - startIndex1),
			object,
			startIndex1);
	}

	@Override
	public boolean ObjectCompareFromToWithObjectTupleStartingAt (
			final AvailObject object, 
			final int startIndex1, 
			final int endIndex1, 
			final AvailObject anObjectTuple, 
			final int startIndex2)
	{
		//  Compare sections of two object tuples.

		if ((object.sameAddressAs(anObjectTuple) && (startIndex1 == startIndex2)))
		{
			return true;
		}
		//  Compare actual entries.
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

	@Override
	public boolean ObjectEquals (
			final AvailObject object, 
			final AvailObject another)
	{
		return another.equalsObjectTuple(object);
	}

	@Override
	public boolean ObjectEqualsObjectTuple (
			final AvailObject object, 
			final AvailObject anObjectTuple)
	{
		//  Compare this object tuple and the given object tuple.
		//
		//  Compare identity...

		if (object.sameAddressAs(anObjectTuple))
		{
			return true;
		}
		if (ObjectTupleSize(object) != anObjectTuple.tupleSize())
		{
			return false;
		}
		if (ObjectHash(object) != anObjectTuple.hash())
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
			object.makeImmutable();
		}
		else
		{
			object.becomeIndirectionTo(anObjectTuple);
			anObjectTuple.makeImmutable();
		}
		//  Now that there are at least two references to it
		return true;
	}

	@Override
	public boolean ObjectIsHashAvailable (
			final AvailObject object)
	{
		//  Answer whether this object's hash value can be computed without creating
		//  new objects.  This method is used by the garbage collector to decide which
		//  objects to attempt to coalesce.  The garbage collector uses the hash values
		//  to find objects that it is likely can be coalesced together.

		if (object.hashOrZero() != 0)
		{
			return true;
		}
		for (int i = 1, _end1 = object.tupleSize(); i <= _end1; i++)
		{
			if (!object.tupleAt(i).isHashAvailable())
			{
				return false;
			}
		}
		return true;
	}



	// operations-tuples

	@Override
	public AvailObject ObjectCopyTupleFromToCanDestroy (
			final AvailObject object, 
			final int start, 
			final int end, 
			final boolean canDestroy)
	{
		//  Make a tuple that only contains the given range of elements of the given tuple.
		//  If canDestroy and isMutable are true, go ahead and clobber all fields of the original
		//  tuple that don't make it into the subrange.  Replace these clobbered fields with the
		//  integer 0 (always immutable) after dropping the refcount on replaced objects.

		assert (1 <= start && start <= (end + 1));
		assert (0 <= end && end <= object.tupleSize());
		if (isMutable && canDestroy)
		{
			if (((start - 1) == end))
			{
				object.assertObjectUnreachableIfMutable();
				return TupleDescriptor.empty();
			}
			final AvailObject zeroObject = IntegerDescriptor.zero();
			object.hashOrZero(0);
			if (((start == 1) || ((end - start) < 30)))
			{
				if (start != 1)
				{
					for (int i = 1, _end1 = start - 1; i <= _end1; i++)
					{
						object.tupleAt(i).assertObjectUnreachableIfMutable();
					}
					for (int i = 1, _end2 = (end - start + 1); i <= _end2; i++)
					{
						object.tupleAtPut(i, object.tupleAt(start + i - 1));
					}
					for (int i = (end - start + 2); i <= end; i++)
					{
						object.tupleAtPut(i, zeroObject);
					}
				}
				for (int i = (end + 1), _end3 = object.tupleSize(); i <= _end3; i++)
				{
					object.tupleAt(i).assertObjectUnreachableIfMutable();
					object.tupleAtPut(i, zeroObject);
				}
				object.truncateTo(end - start + 1);
				//  Clip remaining items off end, padding lost space with a dummy header.
				return object;
			}
			for (int i = 1, _end4 = start - 1; i <= _end4; i++)
			{
				object.tupleAt(i).assertObjectUnreachableIfMutable();
				object.tupleAtPut(i, zeroObject);
			}
			for (int i = (end + 1), _end5 = object.tupleSize(); i <= _end5; i++)
			{
				object.tupleAt(i).assertObjectUnreachableIfMutable();
				object.tupleAtPut(i, zeroObject);
			}
		}
		if (((start - 1) == end))
		{
			return TupleDescriptor.empty();
		}
		//  Compute the hash ahead of time, because asking an element to hash
		//  might trigger a garbage collection.
		int newHash = 0;
		//  This is just to assist the type deducer.
		newHash = object.computeHashFromTo(start, end);
		AvailObject result;
		if (((end - start) < 10))
		{
			result = AvailObject.newIndexedDescriptor(end - start + 1, ObjectTupleDescriptor.mutableDescriptor());
			result.hashOrZero(newHash);
			for (int i = 1, _end6 = (end - start + 1); i <= _end6; i++)
			{
				result.tupleAtPut(i, object.tupleAt(i + start - 1).makeImmutable());
			}
		}
		else
		{
			result = AvailObject.newObjectIndexedIntegerIndexedDescriptor(
				1,
				2,
				SpliceTupleDescriptor.mutableDescriptor());
			if (isMutable && !canDestroy)
			{
				object.makeImmutable();
			}
			//  Share it - play nice
			result.hashOrZero(newHash);
			result.forZoneSetSubtupleStartSubtupleIndexEndOfZone(
				1,
				object,
				start,
				(end - start + 1));
			result.verify();
		}
		return result;
	}

	@Override
	public AvailObject ObjectTruncateTo (
			final AvailObject object, 
			final int newTupleSize)
	{
		//  Private
		//
		//  Shrink the current object on the right.  Assumes that elements beyond the new end
		//  have already been released if necessary.  Since my representation no longer varies
		//  with tupleSize (I used to have different descriptors for different ObjectTuple sizes),
		//  I can simply compute the delta for the number of slots.  I must pad the unused space
		//  on the right with a dummy descriptor and slotsSize for the garbage collector.

		assert isMutable;
		final int delta = object.tupleSize() - newTupleSize;
		if (delta == 0)
		{
			return object;
		}
		final int oldSlotsSize = object.objectSlotsCount();
		assert (oldSlotsSize > 0);
		final int newSlotsCount = oldSlotsSize - delta;
		assert (newSlotsCount > 0);
		object.truncateWithFillerForNewObjectSlotsCount(newSlotsCount);
		return object;
	}

	@Override
	public AvailObject ObjectTupleAtPuttingCanDestroy (
			final AvailObject object, 
			final int index, 
			final AvailObject newValueObject, 
			final boolean canDestroy)
	{
		//  Answer a tuple with all the elements of object except at the given index we should
		//  have newValueObject.  This may destroy the original tuple if canDestroy is true.

		assert ((index >= 1) && (index <= object.tupleSize()));
		if (!canDestroy || !isMutable)
		{
			return object.copyAsMutableObjectTuple().tupleAtPuttingCanDestroy(
				index,
				newValueObject,
				true);
		}
		object.tupleAtPut(index, newValueObject);
		object.hashOrZero(0);
		//  ...invalidate the hash value.
		return object;
	}

	@Override
	public int ObjectTupleIntAt (
			final AvailObject object, 
			final int index)
	{
		//  Answer the integer element at the given index in the tuple object.

		if (((index < 1) || (index > object.tupleSize())))
		{
			error("Out of bounds access to ObjectTuple", object);
			return 0;
		}
		return object.tupleAt(index).extractInt();
	}

	@Override
	public int ObjectTupleSize (
			final AvailObject object)
	{
		//  Answer the number of elements in the object (as a Smalltalk Integer).

		return (object.objectSlotsCount() - numberOfFixedObjectSlots());
	}



	// private-accessing

	@Override
	public int ObjectBitsPerEntry (
			final AvailObject object)
	{
		//  Answer approximately how many bits per entry are taken up by this object.

		return 32;
	}



	// private-computation

	@Override
	public int ObjectComputeHashFromTo (
			final AvailObject object, 
			final int start, 
			final int end)
	{
		//  See comment in superclass.  This method must produce the same value.

		int hash = 0;
		for (int index = end; index >= start; index--)
		{
			final int itemHash = (object.tupleAt(index).hash() ^ PreToggle);
			hash = TupleDescriptor.multiplierTimes(hash) + itemHash;
		}
		return TupleDescriptor.multiplierTimes(hash);
	}

	/**
	 * Construct a new {@link ObjectTupleDescriptor}.
	 *
	 * @param myId The id of the {@linkplain Descriptor descriptor}.
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected ObjectTupleDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	final static ObjectTupleDescriptor mutableDescriptor = new ObjectTupleDescriptor(true);

	public static ObjectTupleDescriptor mutableDescriptor ()
	{
		return mutableDescriptor;
	}

	final static ObjectTupleDescriptor immutableDescriptor = new ObjectTupleDescriptor(false);

	public static ObjectTupleDescriptor immutableDescriptor ()
	{
		return immutableDescriptor;
	}
}
