/**
 * LinearSetBinDescriptor.java
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

import static java.lang.Integer.bitCount;
import com.avail.annotations.*;

/**
 * A {@code LinearSetBinDescriptor} is a leaf bin in a {@link SetDescriptor
 * set}'s hierarchy of bins.  It consists of a small number of distinct elements
 * in no particular order.  If more elements need to be stored, a {@linkplain
 * HashedSetBinDescriptor hashed bin} will be used instead.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class LinearSetBinDescriptor
extends SetBinDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots implements IntegerSlotsEnum
	{
		/**
		 * The sum of the hashes of the elements within this bin.
		 */
		BIN_HASH;

		static
		{
			assert SetBinDescriptor.IntegerSlots.BIN_HASH.ordinal()
				== BIN_HASH.ordinal();
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * The elements of this bin.  The elements are never sub-bins, since
		 * this is a {@linkplain LinearSetBinDescriptor linear bin}, a leaf bin.
		 */
		BIN_ELEMENT_AT_
	}

	@Override @AvailMethod
	@NotNull AvailObject o_BinElementAt (
		final @NotNull AvailObject object,
		final int subscript)
	{
		return object.slot(ObjectSlots.BIN_ELEMENT_AT_, subscript);
	}

	@Override @AvailMethod
	void o_BinElementAtPut (
		final @NotNull AvailObject object,
		final int subscript,
		final @NotNull AvailObject value)
	{
		//  GENERATED setter method (indexed).

		object.setSlot(ObjectSlots.BIN_ELEMENT_AT_, subscript, value);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_MakeImmutable (
		final @NotNull AvailObject object)
	{
		//  Make the object immutable so it can be shared safely.

		if (isMutable)
		{
			object.descriptor = isMutableLevel(false, level);
			object.makeSubobjectsImmutable();
		}
		return object;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_BinAddingElementHashLevelCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject elementObject,
		final int elementObjectHash,
		final byte myLevel,
		final boolean canDestroy)
	{
		//  Add the given element to this bin, potentially modifying it if canDestroy and it's
		//  mutable.  Answer the new bin.  Note that the client is responsible for marking
		//  elementObject as immutable if another reference exists.

		assert myLevel == level;
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
			AvailObject typeUnion = elementObject.kind();
			for (int i = 1; i <= oldSize; i++)
			{
				final AvailObject element = object.binElementAt(i);
				bitPosition = bitShift(element.hash(), -5 * myLevel) & 31;
				bitVector |= bitShift(1,bitPosition);
				typeUnion = typeUnion.typeUnion(element.kind());
			}
		final int newSize = bitCount(bitVector);
			result = HashedSetBinDescriptor.isMutableLevel(true, myLevel)
				.create(newSize);
			result.binHash(0);
			result.binSize(0);
			result.binUnionTypeOrTop(typeUnion);
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

	@Override @AvailMethod
	boolean o_BinHasElementHash (
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
	@Override @AvailMethod
	@NotNull AvailObject o_BinRemoveElementHashCanDestroy (
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
				result = LinearSetBinDescriptor.isMutableLevel(true, level)
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

	@Override @AvailMethod
	boolean o_IsBinSubsetOf (
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
	@Override @AvailMethod
	int o_PopulateTupleStartingAt (
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

	@Override @AvailMethod
	int o_BinSize (
		final @NotNull AvailObject object)
	{
		//  Answer how many elements this bin contains.

		return object.variableObjectSlotsCount();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_BinUnionKind (
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

	/**
	 * The number of distinct levels at which {@linkplain LinearSetBinDescriptor
	 * linear bins} may occur.
	 */
	static final byte numberOfLevels = 8;

	/**
	 * Answer a suitable descriptor for a linear bin with the specified
	 * mutability and at the specified level.
	 *
	 * @param flag Whether the bins using the descriptor will be mutable.
	 * @param level The level for the bins using the descriptor.
	 * @return The descriptor with the requested properties.
	 */
	static LinearSetBinDescriptor isMutableLevel (
		final boolean flag,
		final byte level)
	{
		assert 0 <= level && level < numberOfLevels;
		return descriptors[level * 2 + (flag ? 0 : 1)];
	}

	/**
	 * Construct a new {@link LinearSetBinDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 * @param level The depth of the bin in the hash tree.
	 */
	LinearSetBinDescriptor (
		final boolean isMutable,
		final int level)
	{
		super(isMutable, level);
	}

	/**
	 * The array of {@link LinearSetBinDescriptor}s.
	 */
	static final LinearSetBinDescriptor[] descriptors;

	static
	{
		descriptors = new LinearSetBinDescriptor[numberOfLevels * 2];
		int target = 0;
		for (int level = 0; level < numberOfLevels; level++)
		{
			descriptors[target++] = new LinearSetBinDescriptor(true, level);
			descriptors[target++] = new LinearSetBinDescriptor(false, level);
		}
	};
}
