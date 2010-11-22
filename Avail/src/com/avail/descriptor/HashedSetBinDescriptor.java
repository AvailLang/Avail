/**
 * descriptor/HashedSetBinDescriptor.java
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
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.descriptor.VoidDescriptor;
import static java.lang.Integer.*;

@IntegerSlots({
	"binHash",
	"binSize",
	"bitVector"
})
@ObjectSlots({
	"binUnionType",
	"binElementAt#"
})
public class HashedSetBinDescriptor extends SetBinDescriptor
{


	// GENERATED accessors

	@Override
	public AvailObject ObjectBinElementAt (
			final AvailObject object,
			final int index)
	{
		//  GENERATED getter method (indexed).

		return object.objectSlotAtByteIndex(((index * -4) + -4));
	}

	@Override
	public void ObjectBinElementAtPut (
			final AvailObject object,
			final int index,
			final AvailObject value)
	{
		//  GENERATED setter method (indexed).

		object.objectSlotAtByteIndexPut(((index * -4) + -4), value);
	}

	/**
	 * Setter for field binHash.
	 */
	@Override
	public void ObjectBinHash (
			final AvailObject object,
			final int value)
	{
		object.integerSlotAtByteIndexPut(4, value);
	}

	/**
	 * Setter for field binSize.
	 */
	@Override
	public void ObjectBinSize (
			final AvailObject object,
			final int value)
	{
		object.integerSlotAtByteIndexPut(8, value);
	}

	/**
	 * Setter for field binUnionType.
	 */
	@Override
	public void ObjectBinUnionType (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotAtByteIndexPut(-4, value);
	}

	/**
	 * Setter for field bitVector.
	 */
	@Override
	public void ObjectBitVector (
			final AvailObject object,
			final int value)
	{
		object.integerSlotAtByteIndexPut(12, value);
	}

	/**
	 * Getter for field binHash.
	 */
	@Override
	public int ObjectBinHash (
			final AvailObject object)
	{
		return object.integerSlotAtByteIndex(4);
	}

	/**
	 * Getter for field binSize.
	 */
	@Override
	public int ObjectBinSize (
			final AvailObject object)
	{
		return object.integerSlotAtByteIndex(8);
	}

	/**
	 * Getter for field binUnionType.
	 */
	@Override
	public AvailObject ObjectBinUnionType (
			final AvailObject object)
	{
		return object.objectSlotAtByteIndex(-4);
	}

	/**
	 * Getter for field bitVector.
	 */
	@Override
	public int ObjectBitVector (
			final AvailObject object)
	{
		return object.integerSlotAtByteIndex(12);
	}



	// operations

	@Override
	public AvailObject ObjectMakeImmutable (
			final AvailObject object)
	{
		//  Make the object immutable so it can be shared safely.

		if (isMutable)
		{
			object.descriptor(HashedSetBinDescriptor.isMutableLevel(false, _level));
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
		//  First, grab the appropriate 5 bits from the hash.
		final int objectEntryCount = (object.objectSlotsCount() - numberOfFixedObjectSlots());
		final int logicalIndex = (bitShift(elementObjectHash, -5 * _level) & 31);
		final int vector = object.bitVector();
		final int masked = (vector & (bitShift(1,logicalIndex) - 1));
		final int physicalIndex = bitCount(masked) + 1;
		AvailObject objectToModify;
		AvailObject unionType;
		if (((vector & bitShift(1,logicalIndex)) != 0))
		{
			AvailObject entry = object.binElementAt(physicalIndex);
			final int previousBinSize = entry.binSize();
			final int previousHash = entry.binHash();
			entry = entry.binAddingElementHashLevelCanDestroy(
				elementObject,
				elementObjectHash,
				((byte)(_level + 1)),
				canDestroy);
			final int delta = entry.binSize() - previousBinSize;
			if (delta == 0)
			{
				return object;
			}
			//  The element had to be added.
			final int hashDelta = entry.binHash() - previousHash;
			unionType = object.binUnionType().typeUnion(elementObject.type());
			final int newSize = object.binSize() + delta;
			if (canDestroy & isMutable)
			{
				objectToModify = object;
			}
			else
			{
				if ((!canDestroy) & isMutable)
				{
					object.makeSubobjectsImmutable();
				}
				objectToModify = AvailObject.newIndexedDescriptor(objectEntryCount, HashedSetBinDescriptor.isMutableLevel(true, _level));
				objectToModify.bitVector(vector);
				for (int i = 1, _end1 = (object.objectSlotsCount() - numberOfFixedObjectSlots()); i <= _end1; i++)
				{
					objectToModify.binElementAtPut(i, object.binElementAt(i));
				}
			}
			objectToModify.binHash(object.binHash() + hashDelta);
			objectToModify.binSize(newSize);
			objectToModify.binUnionType(unionType);
			objectToModify.binElementAtPut(physicalIndex, entry);
			return objectToModify;
		}
		if ((!canDestroy) & isMutable)
		{
			object.makeSubobjectsImmutable();
		}
		unionType = object.binUnionType().typeUnion(elementObject.type());
		objectToModify = AvailObject.newIndexedDescriptor(objectEntryCount + 1, HashedSetBinDescriptor.isMutableLevel(true, _level));
		objectToModify.binHash(object.binHash() + elementObjectHash);
		objectToModify.binSize(object.binSize() + 1);
		objectToModify.binUnionType(unionType);
		objectToModify.bitVector(vector | bitShift(1,logicalIndex));
		for (int i = 1, _end2 = physicalIndex - 1; i <= _end2; i++)
		{
			objectToModify.binElementAtPut(i, object.binElementAt(i));
		}
		objectToModify.binElementAtPut(physicalIndex, elementObject);
		for (int i = physicalIndex; i <= objectEntryCount; i++)
		{
			objectToModify.binElementAtPut(i + 1, object.binElementAt(i));
		}
		return objectToModify;
	}

	@Override
	public boolean ObjectBinHasElementHash (
			final AvailObject object,
			final AvailObject elementObject,
			final int elementObjectHash)
	{
		//  First, grab the appropriate 5 bits from the hash.

		final int logicalIndex = (bitShift(elementObjectHash, -5 * _level) & 31);
		final int vector = object.bitVector();
		if (((vector & bitShift(1,logicalIndex)) == 0))
		{
			return false;
		}
		//  There's an entry.  Count the 1-bits below it to compute its zero-relative physicalIndex.
		final int masked = (vector & (bitShift(1,logicalIndex) - 1));
		final int physicalIndex = bitCount(masked) + 1;
		return object.binElementAt(physicalIndex).binHasElementHash(elementObject, elementObjectHash);
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

		final int objectEntryCount = (object.objectSlotsCount() - numberOfFixedObjectSlots());
		final int logicalIndex = (bitShift(elementObjectHash, -5 * _level) & 31);
		final int vector = object.bitVector();
		if (((vector & bitShift(1,logicalIndex)) == 0))
		{
			if (!canDestroy)
			{
				object.makeImmutable();
			}
			return object;
		}
		final int masked = (vector & (bitShift(1,logicalIndex) - 1));
		final int physicalIndex = bitCount(masked) + 1;
		final AvailObject oldEntry = object.binElementAt(physicalIndex);
		final int oldHash = oldEntry.binHash();
		final int oldSize = oldEntry.binSize();
		final AvailObject replacementEntry = oldEntry.binRemoveElementHashCanDestroy(
			elementObject,
			elementObjectHash,
			canDestroy);
		final int deltaHash = replacementEntry.binHash() - oldHash;
		final int deltaSize = replacementEntry.binSize() - oldSize;
		AvailObject result;
		if (replacementEntry.equalsVoid())
		{
			if (objectEntryCount == 1)
			{
				return VoidDescriptor.voidObject();
			}
			//  Calculate the union type before allocating the new bin, so we don't have to
			//  worry about a partially initialized object during a type computation which may
			//  require memory allocation.
			AvailObject newUnionType = Types.terminates.object();
			for (int index = 1; index <= objectEntryCount; index++)
			{
				if (index != physicalIndex)
				{
					newUnionType = newUnionType.typeUnion(object.binElementAt(index).binUnionType());
				}
			}
			result = AvailObject.newIndexedDescriptor(objectEntryCount - 1, HashedSetBinDescriptor.isMutableLevel(true, _level));
			result.binHash(object.binHash() + deltaHash);
			result.binSize(object.binSize() + deltaSize);
			result.binUnionType(newUnionType);
			result.bitVector(vector ^ bitShift(1,logicalIndex));
			for (int index = 1, _end1 = objectEntryCount - 1; index <= _end1; index++)
			{
				result.binElementAtPut(index, VoidDescriptor.voidObject());
			}
			int writePosition = 1;
			for (int readPosition = 1; readPosition <= objectEntryCount; readPosition++)
			{
				if (readPosition != physicalIndex)
				{
					AvailObject eachBin = object.binElementAt(readPosition);
					if (!canDestroy)
					{
						eachBin.makeImmutable();
					}
					result.binElementAtPut(writePosition, eachBin);
					writePosition++;
				}
			}
			//  i.e., number of entries in result + 1...
			assert (writePosition == objectEntryCount);
		}
		else
		{
			AvailObject newUnionType = Types.terminates.object();
			for (int index = 1; index <= objectEntryCount; index++)
			{
				if (index == physicalIndex)
				{
					newUnionType = newUnionType.typeUnion(replacementEntry.binUnionType());
				}
				else
				{
					newUnionType = newUnionType.typeUnion(object.binElementAt(index).binUnionType());
				}
			}
			result = AvailObject.newIndexedDescriptor(objectEntryCount, HashedSetBinDescriptor.isMutableLevel(true, _level));
			result.binHash(object.binHash() + deltaHash);
			result.binSize(object.binSize() + deltaSize);
			result.binUnionType(newUnionType);
			result.bitVector(vector);
			for (int index = 1; index <= objectEntryCount; index++)
			{
				AvailObject eachBin = object.binElementAt(index);
				if (index == physicalIndex)
				{
					result.binElementAtPut(index, replacementEntry);
				}
				else
				{
					if (!canDestroy)
					{
						eachBin.makeImmutable();
					}
					result.binElementAtPut(index, eachBin);
				}
			}
			for (int index = 1; index <= objectEntryCount; index++)
			{
				AvailObject eachBin = object.binElementAt(index);
				if (index == physicalIndex)
				{
					result.binElementAtPut(index, replacementEntry);
				}
				else
				{
					if (!canDestroy)
					{
						eachBin.makeImmutable();
					}
					result.binElementAtPut(index, eachBin);
				}
			}
		}
		return result;
	}

	@Override
	public boolean ObjectIsBinSubsetOf (
			final AvailObject object,
			final AvailObject potentialSuperset)
	{
		//  Check if object, a bin, holds a subset of aSet's elements.

		for (int physicalIndex = 1, _end1 = object.objectSlotsCount() - numberOfFixedObjectSlots; physicalIndex <= _end1; physicalIndex++)
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
		for (int readIndex = 1, _end1 = object.objectSlotsCount() - numberOfFixedObjectSlots; readIndex <= _end1; readIndex++)
		{
			writeIndex = object.binElementAt(readIndex).populateTupleStartingAt(mutableTuple, writeIndex);
		}
		return writeIndex;
	}



	/* Descriptor lookup */
	static byte numberOfLevels ()
	{
		return 7;
	};

	static HashedSetBinDescriptor isMutableLevel (boolean flag, byte level)
	{
		assert(0 <= level && level <= numberOfLevels());
		return descriptors [level * 2 + (flag ? 0 : 1)];
	};

	/**
	 * Construct a new {@link HashedSetBinDescriptor}.
	 *
	 * @param myId The id of the {@linkplain Descriptor descriptor}.
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 * @param level The depth of the bin in the hash tree.
	 */
	protected HashedSetBinDescriptor (
		final boolean isMutable,
		final int level)
	{
		super(
			isMutable,
			level);
	}

	final static HashedSetBinDescriptor descriptors[] = {
		new HashedSetBinDescriptor(true, 0),
		new HashedSetBinDescriptor(false, 0),
		new HashedSetBinDescriptor(true, 1),
		new HashedSetBinDescriptor(false, 1),
		new HashedSetBinDescriptor(true, 2),
		new HashedSetBinDescriptor(false, 2),
		new HashedSetBinDescriptor(true, 3),
		new HashedSetBinDescriptor(false, 3),
		new HashedSetBinDescriptor(true, 4),
		new HashedSetBinDescriptor(false, 4),
		new HashedSetBinDescriptor(true, 5),
		new HashedSetBinDescriptor(false, 5),
		new HashedSetBinDescriptor(true, 6),
		new HashedSetBinDescriptor(false, 6)
	};

}
