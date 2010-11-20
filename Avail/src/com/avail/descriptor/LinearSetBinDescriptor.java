/**
 * descriptor/LinearSetBinDescriptor.java
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
import com.avail.descriptor.HashedSetBinDescriptor;
import com.avail.descriptor.LinearSetBinDescriptor;
import com.avail.descriptor.VoidDescriptor;
import static java.lang.Integer.*;

@IntegerSlots("binHash")
@ObjectSlots("binElementAt#")
public class LinearSetBinDescriptor extends SetBinDescriptor
{


	// GENERATED accessors

	@Override
	public AvailObject ObjectBinElementAt (
			final AvailObject object, 
			final int index)
	{
		//  GENERATED getter method (indexed).

		return object.objectSlotAtByteIndex(((index * -4) + 0));
	}

	@Override
	public void ObjectBinElementAtPut (
			final AvailObject object, 
			final int index, 
			final AvailObject value)
	{
		//  GENERATED setter method (indexed).

		object.objectSlotAtByteIndexPut(((index * -4) + 0), value);
	}

	/**
	 * Setter for field !B!inHash.
	 */
	@Override
	public void ObjectBinHash (
			final AvailObject object, 
			final int value)
	{
		object.integerSlotAtByteIndexPut(4, value);
	}

	/**
	 * Getter for field !B!inHash.
	 */
	@Override
	public int ObjectBinHash (
			final AvailObject object)
	{
		return object.integerSlotAtByteIndex(4);
	}



	// operations

	@Override
	public AvailObject ObjectMakeImmutable (
			final AvailObject object)
	{
		//  Make the object immutable so it can be shared safely.

		if (isMutable)
		{
			object.descriptor(LinearSetBinDescriptor.isMutableLevel(false, _level));
			object.makeSubobjectsImmutable();
		}
		return object;
	}



	// operations-set bins

	@Override
	public AvailObject ObjectBinAddingElementHashLevelCanDestroy (
			final AvailObject object, 
			final AvailObject elementObject, 
			final int elementObjectHash, 
			final byte myLevel, 
			final boolean canDestroy)
	{
		//  Add the given element to this bin, potentially modifying it if canDestroy and it's
		//  mutable.  Answer the new bin.  Note that the client is responsible for marking
		//  elementObject as immutable if another reference exists.

		assert (myLevel == _level);
		if (object.binHasElementHash(elementObject, elementObjectHash))
		{
			if (!canDestroy || !isMutable)
			{
				object.makeImmutable();
			}
			return object;
		}
		//  It's not present, so grow the list.  Keep it simple for now by always replacing the list.
		final int oldSize = (object.objectSlotsCount() - numberOfFixedObjectSlots());
		AvailObject result;
		if (((myLevel < 7) && (oldSize >= 10)))
		{
			int bitPosition = (bitShift(elementObjectHash, -5 * myLevel) & 31);
			int bitVector = bitShift(1,bitPosition);
			AvailObject unionType = elementObject.type();
			for (int i = 1; i <= oldSize; i++)
			{
				final AvailObject element = object.binElementAt(i);
				bitPosition = (bitShift(element.hash(), -5 * myLevel) & 31);
				bitVector |= bitShift(1,bitPosition);
				unionType = unionType.typeUnion(element.type());
			}
			final int newSize = bitCount(bitVector);
			result = AvailObject.newIndexedDescriptor(newSize, HashedSetBinDescriptor.isMutableLevel(true, myLevel));
			result.binHash(0);
			result.binSize(0);
			result.binUnionType(unionType);
			result.bitVector(bitVector);
			for (int i = 1; i <= newSize; i++)
			{
				result.binElementAtPut(i, VoidDescriptor.voidObject());
			}
			AvailObject localAddResult;
			for (int i = 0; i <= oldSize; i++)
			{
				AvailObject eachElement;
				int eachHash;
				if (i == 0)
				{
					eachElement = elementObject;
					eachHash = elementObjectHash;
				}
				else
				{
					eachElement = object.binElementAt(i);
					eachHash = eachElement.hash();
				}
				assert result.descriptor().isMutable();
				localAddResult = result.binAddingElementHashLevelCanDestroy(
					eachElement,
					eachHash,
					myLevel,
					true);
				assert localAddResult.sameAddressAs(result) : "The element should have been added without reallocation";
			}
			final int newHash = object.binHash() + elementObjectHash;
			assert (result.binHash() == newHash);
			assert (result.binSize() == (oldSize + 1));
			return result;
		}
		//  Make a slightly larger linear bin and populate it.
		result = AvailObject.newIndexedDescriptor(oldSize + 1, LinearSetBinDescriptor.isMutableLevel(true, myLevel));
		result.binHash(object.binHash() + elementObjectHash);
		result.binElementAtPut(oldSize + 1, elementObject);
		if (canDestroy && isMutable)
		{
			for (int i = 1; i <= oldSize; i++)
			{
				result.binElementAtPut(i, object.binElementAt(i));
				object.binElementAtPut(i, VoidDescriptor.voidObject());
			}
		}
		else if (isMutable)
		{
			for (int i = 1; i <= oldSize; i++)
			{
				result.binElementAtPut(i, object.binElementAt(i).makeImmutable());
			}
		}
		else
		{
			for (int i = 1; i <= oldSize; i++)
			{
				result.binElementAtPut(i, object.binElementAt(i));
			}
		}
		return result;
	}

	@Override
	public boolean ObjectBinHasElementHash (
			final AvailObject object, 
			final AvailObject elementObject, 
			final int elementObjectHash)
	{
		for (int x = 1, _end1 = (object.objectSlotsCount() - numberOfFixedObjectSlots()); x <= _end1; x++)
		{
			if (elementObject.equals(object.binElementAt(x)))
			{
				return true;
			}
		}
		return false;
	}

	@Override
	public AvailObject ObjectBinRemoveElementHashCanDestroy (
			final AvailObject object, 
			final AvailObject elementObject, 
			final int elementObjectHash, 
			final boolean canDestroy)
	{
		//  Remove elementObject from the bin object, if present.  Answer the resulting bin.  The bin
		//  may be modified if it's mutable and canDestroy.

		final int oldSize = (object.objectSlotsCount() - numberOfFixedObjectSlots());
		for (int searchIndex = 1; searchIndex <= oldSize; searchIndex++)
		{
			if (object.binElementAt(searchIndex).equals(elementObject))
			{
				AvailObject result;
				if (oldSize == 2)
				{
					result = object.binElementAt(3 - searchIndex);
					if (!canDestroy)
					{
						result.makeImmutable();
					}
					return result;
				}
				result = AvailObject.newIndexedDescriptor(oldSize - 1, LinearSetBinDescriptor.isMutableLevel(true, _level));
				result.binHash(object.binHash() - elementObjectHash);
				for (int initIndex = 1, _end1 = oldSize - 1; initIndex <= _end1; initIndex++)
				{
					result.binElementAtPut(initIndex, VoidDescriptor.voidObject());
				}
				for (int copyIndex = 1, _end2 = searchIndex - 1; copyIndex <= _end2; copyIndex++)
				{
					result.binElementAtPut(copyIndex, object.binElementAt(copyIndex));
				}
				for (int copyIndex = searchIndex + 1; copyIndex <= oldSize; copyIndex++)
				{
					result.binElementAtPut(copyIndex - 1, object.binElementAt(copyIndex));
				}
				if (!canDestroy)
				{
					result.makeSubobjectsImmutable();
				}
				return result;
			}
		}
		if (!canDestroy)
		{
			object.makeImmutable();
		}
		return object;
	}

	@Override
	public boolean ObjectIsBinSubsetOf (
			final AvailObject object, 
			final AvailObject potentialSuperset)
	{
		//  Check if object, a bin, holds a subset of aSet's elements.

		for (int physicalIndex = 1, _end1 = (object.objectSlotsCount() - numberOfFixedObjectSlots()); physicalIndex <= _end1; physicalIndex++)
		{
			if (!object.binElementAt(physicalIndex).isBinSubsetOf(potentialSuperset))
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public int ObjectPopulateTupleStartingAt (
			final AvailObject object, 
			final AvailObject mutableTuple, 
			final int startingIndex)
	{
		//  Write set bin elements into the tuple, starting at the given startingIndex.  Answer
		//  the next available index in which to write.

		assert mutableTuple.descriptor().isMutable();
		int writeIndex = startingIndex;
		for (int readIndex = 1, _end1 = (object.objectSlotsCount() - numberOfFixedObjectSlots()); readIndex <= _end1; readIndex++)
		{
			mutableTuple.tupleAtPut(writeIndex, object.binElementAt(readIndex));
			writeIndex++;
		}
		return writeIndex;
	}

	@Override
	public int ObjectBinSize (
			final AvailObject object)
	{
		//  Answer how many elements this bin contains.

		return (object.objectSlotsCount() - numberOfFixedObjectSlots());
	}

	@Override
	public AvailObject ObjectBinUnionType (
			final AvailObject object)
	{
		//  Answer the union of the types of this bin's elements.  I'm supposed
		//  to be small, so recalculate it per request.

		AvailObject unionType = object.binElementAt(1).type();
		for (int index = 2, _end1 = (object.objectSlotsCount() - numberOfFixedObjectSlots()); index <= _end1; index++)
		{
			unionType = unionType.typeUnion(object.binElementAt(index).type());
		}
		return unionType;
	}





	/* Descriptor lookup */
	static byte numberOfLevels ()
	{
		return 8;
	};

	static LinearSetBinDescriptor isMutableLevel (boolean flag, byte level)
	{
		assert(0<= level && level <= numberOfLevels()); 
		return (LinearSetBinDescriptor) allDescriptors [84 + (level * 2) + (flag ? 0 : 1)];
	};

	/**
	 * Construct a new {@link LinearSetBinDescriptor}.
	 *
	 * @param myId The id of the {@linkplain Descriptor descriptor}.
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 * @param numberOfFixedObjectSlots
	 *        The number of fixed {@linkplain AvailObject object} slots.
	 * @param numberOfFixedIntegerSlots The number of fixed integer slots.
	 * @param hasVariableObjectSlots
	 *        Does an {@linkplain AvailObject object} using this {@linkplain
	 *        Descriptor} have any variable object slots?
	 * @param hasVariableIntegerSlots
	 *        Does an {@linkplain AvailObject object} using this {@linkplain
	 *        Descriptor} have any variable integer slots?
	 * @param level The depth of the bin in the hash tree.
	 */
	protected LinearSetBinDescriptor (
		final int myId,
		final boolean isMutable,
		final int numberOfFixedObjectSlots,
		final int numberOfFixedIntegerSlots,
		final boolean hasVariableObjectSlots,
		final boolean hasVariableIntegerSlots,
		final int level)
	{
		super(
			myId,
			isMutable,
			numberOfFixedObjectSlots,
			numberOfFixedIntegerSlots,
			hasVariableObjectSlots,
			hasVariableIntegerSlots,
			level);
	}
}
