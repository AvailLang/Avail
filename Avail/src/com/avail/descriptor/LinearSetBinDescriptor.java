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

import static java.lang.Integer.bitCount;
import com.avail.annotations.NotNull;

public class LinearSetBinDescriptor
extends SetBinDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	{
		BIN_HASH
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		BIN_ELEMENT_AT_
	}

	@Override
	public @NotNull AvailObject o_BinElementAt (
		final @NotNull AvailObject object,
		final int subscript)
	{
		return object.objectSlotAt(ObjectSlots.BIN_ELEMENT_AT_, subscript);
	}

	@Override
	public void o_BinElementAtPut (
		final @NotNull AvailObject object,
		final int subscript,
		final @NotNull AvailObject value)
	{
		//  GENERATED setter method (indexed).

		object.objectSlotAtPut(ObjectSlots.BIN_ELEMENT_AT_, subscript, value);
	}

	@Override
	public void o_BinHash (
		final @NotNull AvailObject object,
		final int value)
	{
		object.integerSlotPut(IntegerSlots.BIN_HASH, value);
	}

	@Override
	public int o_BinHash (
		final @NotNull AvailObject object)
	{
		return object.integerSlot(IntegerSlots.BIN_HASH);
	}

	@Override
	public @NotNull AvailObject o_MakeImmutable (
		final @NotNull AvailObject object)
	{
		//  Make the object immutable so it can be shared safely.

		if (isMutable)
		{
			object.descriptor(
				LinearSetBinDescriptor.isMutableLevel(false, _level));
			object.makeSubobjectsImmutable();
		}
		return object;
	}

	@Override
	public @NotNull AvailObject o_BinAddingElementHashLevelCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject elementObject,
		final int elementObjectHash,
		final byte myLevel,
		final boolean canDestroy)
	{
		//  Add the given element to this bin, potentially modifying it if canDestroy and it's
		//  mutable.  Answer the new bin.  Note that the client is responsible for marking
		//  elementObject as immutable if another reference exists.

		assert myLevel == _level;
		if (object.binHasElementHash(elementObject, elementObjectHash))
		{
			if (!canDestroy || !isMutable)
			{
				object.makeImmutable();
			}
			return object;
		}
		//  It's not present, so grow the list.  Keep it simple for now by always replacing the list.
		final int oldSize = object.variableObjectSlotsCount();
		AvailObject result;
		if (myLevel < 7 && oldSize >= 10)
		{
			int bitPosition = bitShift(elementObjectHash, -5 * myLevel) & 31;
			int bitVector = bitShift(1,bitPosition);
			AvailObject unionType = elementObject.kind();
			for (int i = 1; i <= oldSize; i++)
			{
				final AvailObject element = object.binElementAt(i);
				bitPosition = bitShift(element.hash(), -5 * myLevel) & 31;
				bitVector |= bitShift(1,bitPosition);
				unionType = unionType.typeUnion(element.kind());
			}
		final int newSize = bitCount(bitVector);
			result = HashedSetBinDescriptor.isMutableLevel(true, myLevel)
				.create(newSize);
			result.binHash(0);
			result.binSize(0);
			result.binUnionTypeOrVoid(unionType);
			result.bitVector(bitVector);
			for (int i = 1; i <= newSize; i++)
			{
				result.binElementAtPut(i, NullDescriptor.nullObject());
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
				assert localAddResult.sameAddressAs(result)
				: "The element should have been added without reallocation";
			}
		final int newHash = object.binHash() + elementObjectHash;
			assert result.binHash() == newHash;
			assert result.binSize() == oldSize + 1;
			return result;
		}
		//  Make a slightly larger linear bin and populate it.
		result = LinearSetBinDescriptor.isMutableLevel(true, myLevel)
			.create(oldSize + 1);
		result.binHash(object.binHash() + elementObjectHash);
		result.binElementAtPut(oldSize + 1, elementObject);
		if (canDestroy && isMutable)
		{
			for (int i = 1; i <= oldSize; i++)
			{
				result.binElementAtPut(i, object.binElementAt(i));
				object.binElementAtPut(i, NullDescriptor.nullObject());
			}
		}
		else if (isMutable)
		{
			for (int i = 1; i <= oldSize; i++)
			{
				result.binElementAtPut(
					i,
					object.binElementAt(i).makeImmutable());
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
	public boolean o_BinHasElementHash (
		final @NotNull AvailObject object,
		final @NotNull AvailObject elementObject,
		final int elementObjectHash)
	{
		final int limit = object.variableObjectSlotsCount();
		for (int x = 1; x <= limit; x++)
		{
			if (elementObject.equals(object.binElementAt(x)))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Remove elementObject from the bin object, if present.  Answer the
	 * resulting bin.  The bin may be modified if it's mutable and canDestroy.
	 */
	@Override
	public @NotNull AvailObject o_BinRemoveElementHashCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject elementObject,
		final int elementObjectHash,
		final boolean canDestroy)
	{
		final int oldSize = object.variableObjectSlotsCount();
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
				result = LinearSetBinDescriptor.isMutableLevel(true, _level)
					.create(oldSize - 1);
				result.binHash(object.binHash() - elementObjectHash);
				for (int initIndex = 1; initIndex < oldSize; initIndex++)
				{
					result.binElementAtPut(
						initIndex,
						NullDescriptor.nullObject());
				}
				for (int copyIndex = 1; copyIndex < searchIndex; copyIndex++)
				{
					result.binElementAtPut(
						copyIndex,
						object.binElementAt(copyIndex));
				}
				for (
						int copyIndex = searchIndex + 1;
						copyIndex <= oldSize;
						copyIndex++)
				{
					result.binElementAtPut(
						copyIndex - 1,
						object.binElementAt(copyIndex));
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
	public boolean o_IsBinSubsetOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject potentialSuperset)
	{
		//  Check if object, a bin, holds a subset of aSet's elements.

		for (
				int physicalIndex = object.variableObjectSlotsCount();
				physicalIndex >= 1;
				physicalIndex--)
		{
			if (!object.binElementAt(physicalIndex).isBinSubsetOf(
				potentialSuperset))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Write set bin elements into the tuple, starting at the given
	 * startingIndex.  Answer the next available index in which to write.
	 */
	@Override
	public int o_PopulateTupleStartingAt (
		final @NotNull AvailObject object,
		final @NotNull AvailObject mutableTuple,
		final int startingIndex)
	{
		assert mutableTuple.descriptor().isMutable();
		int writeIndex = startingIndex;
		for (
			int
				readIndex = 1,
				end = object.variableObjectSlotsCount();
			readIndex <= end;
			readIndex++)
		{
			mutableTuple.tupleAtPut(writeIndex, object.binElementAt(readIndex));
			writeIndex++;
		}
		return writeIndex;
	}

	@Override
	public int o_BinSize (
		final @NotNull AvailObject object)
	{
		//  Answer how many elements this bin contains.

		return object.variableObjectSlotsCount();
	}

	@Override
	public @NotNull AvailObject o_BinUnionKind (
		final @NotNull AvailObject object)
	{
		//  Answer the union of the types of this bin's elements.  I'm supposed
		//  to be small, so recalculate it per request.

		AvailObject unionKind = object.binElementAt(1).kind();
		final int limit = object.variableObjectSlotsCount();
		for (int index = 2; index <= limit; index++)
		{
			unionKind = unionKind.typeUnion(object.binElementAt(index).kind());
		}
		return unionKind;
	}

	static byte numberOfLevels ()
	{
		return 8;
	}

	static LinearSetBinDescriptor isMutableLevel (
		final boolean flag,
		final byte level)
	{
		assert 0 <= level && level <= numberOfLevels();
		return descriptors [level * 2 + (flag ? 0 : 1)];
	}

	/**
	 * Construct a new {@link LinearSetBinDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 * @param level The depth of the bin in the hash tree.
	 */
	protected LinearSetBinDescriptor (
		final boolean isMutable,
		final int level)
	{
		super(
			isMutable,
			level);
	}

	final static LinearSetBinDescriptor[] descriptors =
	{
		new LinearSetBinDescriptor(true, 0),
		new LinearSetBinDescriptor(false, 0),
		new LinearSetBinDescriptor(true, 1),
		new LinearSetBinDescriptor(false, 1),
		new LinearSetBinDescriptor(true, 2),
		new LinearSetBinDescriptor(false, 2),
		new LinearSetBinDescriptor(true, 3),
		new LinearSetBinDescriptor(false, 3),
		new LinearSetBinDescriptor(true, 4),
		new LinearSetBinDescriptor(false, 4),
		new LinearSetBinDescriptor(true, 5),
		new LinearSetBinDescriptor(false, 5),
		new LinearSetBinDescriptor(true, 6),
		new LinearSetBinDescriptor(false, 6),
		new LinearSetBinDescriptor(true, 7),
		new LinearSetBinDescriptor(false, 7)
	};
}
