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

import static com.avail.descriptor.LinearSetBinDescriptor.ObjectSlots.*;
import static com.avail.descriptor.LinearSetBinDescriptor.IntegerSlots.*;
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
		return object.slot(BIN_ELEMENT_AT_, subscript);
	}

	@Override @AvailMethod
	void o_BinElementAtPut (
		final @NotNull AvailObject object,
		final int subscript,
		final @NotNull AvailObject value)
	{
		//  GENERATED setter method (indexed).

		object.setSlot(BIN_ELEMENT_AT_, subscript, value);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_MakeImmutable (
		final @NotNull AvailObject object)
	{
		if (isMutable)
		{
			object.descriptor = isMutableLevel(false, level);
			object.makeSubobjectsImmutable();
		}
		return object;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_SetBinAddingElementHashLevelCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject elementObject,
		final int elementObjectHash,
		final byte myLevel,
		final boolean canDestroy)
	{
		// Add the given element to this bin, potentially modifying it if
		// canDestroy and it's mutable.  Answer the new bin.  Note that the
		// client is responsible for marking elementObject as immutable if
		// another reference exists.
		assert myLevel == level;
		if (object.binHasElementWithHash(elementObject, elementObjectHash))
		{
			if (!canDestroy)
			{
				object.makeImmutable();
			}
			return object;
		}
		// It's not present, so grow the list.  Keep it simple for now by always
		// replacing the list.
		final int oldSize = object.variableObjectSlotsCount();
		AvailObject result;
		if (myLevel < numberOfLevels - 1 && oldSize >= 10)
		{
			int bitPosition = bitShift(elementObjectHash, -5 * myLevel) & 31;
			int bitVector = bitShift(1, bitPosition);
			for (int i = 1; i <= oldSize; i++)
			{
				final AvailObject element = object.slot(BIN_ELEMENT_AT_, i);
				bitPosition = bitShift(element.hash(), -5 * myLevel) & 31;
				bitVector |= bitShift(1, bitPosition);
			}
			final int newSize = bitCount(bitVector);
			result = HashedSetBinDescriptor.createBin(
				myLevel,
				newSize,
				0,
				0,
				bitVector,
				NullDescriptor.nullObject());
			for (int i = 1; i <= newSize; i++)
			{
				result.setSlot(
					HashedSetBinDescriptor.ObjectSlots.BIN_ELEMENT_AT_,
					i,
					NullDescriptor.nullObject());
			}
			AvailObject localAddResult;
			for (int i = 0; i <= oldSize; i++)
			{
				final AvailObject eachElement;
				final int eachHash;
				if (i == 0)
				{
					eachElement = elementObject;
					eachHash = elementObjectHash;
				}
				else
				{
					eachElement = object.slot(BIN_ELEMENT_AT_, i);
					eachHash = eachElement.hash();
				}
				assert result.descriptor().isMutable();
				localAddResult = result.setBinAddingElementHashLevelCanDestroy(
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
		// Make a slightly larger linear bin and populate it.
		result = LinearSetBinDescriptor.createBin(
			myLevel,
			oldSize + 1,
			object.binHash() + elementObjectHash);
		result.setSlot(BIN_ELEMENT_AT_, oldSize + 1, elementObject);
		if (canDestroy && isMutable)
		{
			for (int i = 1; i <= oldSize; i++)
			{
				// Clear old bin for safety.
				result.setSlot(
					BIN_ELEMENT_AT_,
					i,
					object.slot(BIN_ELEMENT_AT_, i));
				object.setSlot(BIN_ELEMENT_AT_, i, NullDescriptor.nullObject());
			}
		}
		else if (isMutable)
		{
			for (int i = 1; i <= oldSize; i++)
			{
				result.setSlot(
					BIN_ELEMENT_AT_,
					i,
					object.slot(BIN_ELEMENT_AT_, i).makeImmutable());
			}
		}
		else
		{
			for (int i = 1; i <= oldSize; i++)
			{
				result.setSlot(
					BIN_ELEMENT_AT_,
					i,
					object.slot(BIN_ELEMENT_AT_, i));
			}
		}
		return result;
	}

	@Override @AvailMethod
	boolean o_BinHasElementWithHash (
		final @NotNull AvailObject object,
		final @NotNull AvailObject elementObject,
		final int elementObjectHash)
	{
		final int limit = object.variableObjectSlotsCount();
		for (int i = 1; i <= limit; i++)
		{
			if (elementObject.equals(object.slot(BIN_ELEMENT_AT_, i)))
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
			if (object.slot(BIN_ELEMENT_AT_, searchIndex).equals(elementObject))
			{
				if (oldSize == 2)
				{
					final AvailObject survivor =
						object.slot(BIN_ELEMENT_AT_, 3 - searchIndex);
					if (!canDestroy)
					{
						survivor.makeImmutable();
					}
					return survivor;
				}
				final AvailObject result = LinearSetBinDescriptor.createBin(
					level,
					oldSize - 1,
					object.binHash() - elementObjectHash);
				for (int copyIndex = 1; copyIndex < searchIndex; copyIndex++)
				{
					result.setSlot(
						BIN_ELEMENT_AT_,
						copyIndex,
						object.slot(BIN_ELEMENT_AT_, copyIndex));
				}
				for (
					int copyIndex = searchIndex + 1;
					copyIndex <= oldSize;
					copyIndex++)
				{
					result.setSlot(
						BIN_ELEMENT_AT_,
						copyIndex - 1,
						object.slot(BIN_ELEMENT_AT_, copyIndex));
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
			if (!object.slot(BIN_ELEMENT_AT_, physicalIndex).isBinSubsetOf(
				potentialSuperset))
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	int o_BinSize (
		final @NotNull AvailObject object)
	{
		// Answer how many elements this bin contains.
		return object.variableObjectSlotsCount();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_BinUnionKind (
		final @NotNull AvailObject object)
	{
		// Answer the nearest kind of the union of the types of this bin's
		// elements.  I'm supposed to be small, so recalculate it per request.

		AvailObject unionKind = object.slot(BIN_ELEMENT_AT_, 1).kind();
		final int limit = object.variableObjectSlotsCount();
		for (int index = 2; index <= limit; index++)
		{
			unionKind = unionKind.typeUnion(
				object.slot(BIN_ELEMENT_AT_, index).kind());
		}
		return unionKind;
	}

	@Override @AvailMethod
	@NotNull boolean o_BinElementsAreAllInstancesOfKind (
		final @NotNull AvailObject object,
		final @NotNull AvailObject kind)
	{
		final int limit = object.variableObjectSlotsCount();
		for (int index = 1; index <= limit; index++)
		{
			if (!object.slot(BIN_ELEMENT_AT_, index).isInstanceOfKind(kind))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Create a mutable linear bin at the specified level with the given size
	 * and bin hash.  The caller is responsible for initializing the elements
	 * and making them immutable if necessary.
	 *
	 * @param level The level of the new bin.
	 * @param size The number of elements in the new bin.
	 * @param hash The hash of the bin's elements, or zero if unknown.
	 * @return A new linear set bin with uninitialized element slots.
	 */
	public static @NotNull AvailObject createBin (
		final byte level,
		final int size,
		final int hash)
	{
		final AvailObject instance = isMutableLevel(true, level).create(size);
		instance.setSlot(BIN_HASH, hash);
		return instance;
	}

	/**
	 * Create a mutable 2-element linear bin at the specified level and with the
	 * specified elements.  The caller is responsible for making the elements
	 * immutable if necessary.
	 *
	 * @param level The level of the new bin.
	 * @param firstElement The first element of the new bin.
	 * @param secondElement The second element of the new bin.
	 * @return A 2-element set bin.
	 */
	public static @NotNull AvailObject createPair (
		final byte level,
		final @NotNull AvailObject firstElement,
		final @NotNull AvailObject secondElement)
	{
		final AvailObject instance = isMutableLevel(true, level).create(2);
		instance.setSlot(BIN_ELEMENT_AT_, 1, firstElement);
		instance.setSlot(BIN_ELEMENT_AT_, 2, secondElement);
		instance.setSlot(BIN_HASH, firstElement.hash() + secondElement.hash());
		return instance;
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
	}
}
