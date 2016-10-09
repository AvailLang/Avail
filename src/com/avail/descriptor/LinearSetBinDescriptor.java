/**
 * LinearSetBinDescriptor.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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
import static com.avail.descriptor.AvailObjectRepresentation.*;
import static com.avail.descriptor.Mutability.*;
import static java.lang.Long.bitCount;

import com.avail.annotations.AvailMethod;
import com.avail.annotations.HideFieldInDebugger;
import com.avail.descriptor.SetDescriptor.SetIterator;

/**
 * A {@code LinearSetBinDescriptor} is a leaf bin in a {@link SetDescriptor
 * set}'s hierarchy of bins.  It consists of a small number of distinct elements
 * in no particular order.  If more elements need to be stored, a {@linkplain
 * HashedSetBinDescriptor hashed bin} will be used instead.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class LinearSetBinDescriptor
extends SetBinDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * The low 32 bits are used for the {@link #BIN_HASH}, but the upper
		 * 32 can be used by other {@link BitField}s in subclasses.
		 */
		@HideFieldInDebugger
		BIN_HASH_AND_MORE;

		/**
		 * A slot to hold the bin's hash value, or zero if it has not been
		 * computed.
		 */
		static final BitField BIN_HASH = bitField(BIN_HASH_AND_MORE, 0, 32);

		static
		{
			assert SetBinDescriptor.IntegerSlots.BIN_HASH_AND_MORE.ordinal()
				== BIN_HASH_AND_MORE.ordinal();
			assert SetBinDescriptor.IntegerSlots.BIN_HASH.isSamePlaceAs(
				BIN_HASH);
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The elements of this bin.  The elements are never sub-bins, since
		 * this is a {@linkplain LinearSetBinDescriptor linear bin}, a leaf bin.
		 */
		BIN_ELEMENT_AT_
	}

	/**
	 * Check that this linear bin has a correct binHash.
	 *
	 * @param object A linear set bin.
	 */
	private static void checkBinHash (final AvailObject object)
	{
		assert object.descriptor instanceof LinearSetBinDescriptor;
		final int stored = object.binHash();
		int calculated = 0;
		for (int i = object.variableObjectSlotsCount(); i >= 1; i--)
		{
			final AvailObject subBin = object.slot(BIN_ELEMENT_AT_, i);
			final int subBinHash = subBin.hash();
			calculated += subBinHash;
		}
		assert calculated == stored : "Failed bin hash cross-check";
	}

	@Override @AvailMethod
	AvailObject o_BinElementAt (final AvailObject object, final int subscript)
	{
		return object.slot(BIN_ELEMENT_AT_, subscript);
	}

	@Override @AvailMethod
	A_BasicObject o_SetBinAddingElementHashLevelCanDestroy (
		final AvailObject object,
		final A_BasicObject elementObject,
		final int elementObjectHash,
		final byte myLevel,
		final boolean canDestroy)
	{
		// Add the given element to this bin, potentially modifying it if
		// canDestroy and it's mutable.  Answer the new bin.  Note that the
		// client is responsible for marking elementObject as immutable if
		// another reference exists.
		checkBinHash(object);
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
		if (oldSize == 0)
		{
			// Bin transitioned from empty to single, but every object can act
			// as a singleton set bin.
			return elementObject;
		}
		final int oldHash = object.binHash();
		AvailObject result;
		if (myLevel < numberOfLevels - 1 && oldSize >= 10)
		{
			final byte shift = (byte)(6 * myLevel);
			assert shift < 32;
			int bitPosition = (elementObjectHash >>> shift) & 63;
			long bitVector = bitShift(1L, bitPosition);
			for (int i = 1; i <= oldSize; i++)
			{
				final A_BasicObject element = object.slot(BIN_ELEMENT_AT_, i);
				bitPosition = (element.hash() >>> shift) & 63;
				bitVector |= bitShift(1L, bitPosition);
			}
			final int newLocalSize = bitCount(bitVector);
			result = HashedSetBinDescriptor.createInitializedBin(
				myLevel, newLocalSize, 0, 0, bitVector, NilDescriptor.nil());
			for (int i = 0; i <= oldSize; i++)
			{
				final A_BasicObject eachElement;
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
				final A_BasicObject localAddResult =
					result.setBinAddingElementHashLevelCanDestroy(
						eachElement, eachHash, myLevel, true);
				assert localAddResult.sameAddressAs(result)
				: "The element should have been added without reallocation";
			}
			assert result.binSize() == oldSize + 1;
			assert object.binHash() == oldHash;
			final int newHash = oldHash + elementObjectHash;
			assert result.binHash() == newHash;
			HashedSetBinDescriptor.checkBinHash(result);
			return result;
		}
		// Make a slightly larger linear bin and populate it.
		result = newLike(descriptorFor(MUTABLE, level), object, 1, 0);
		result.setSlot(BIN_ELEMENT_AT_, oldSize + 1, elementObject);
		result.setSlot(BIN_HASH, oldHash + elementObjectHash);
		if (isMutable())
		{
			if (canDestroy)
			{
				object.destroy();
			}
			else
			{
				object.makeSubobjectsImmutable();
			}
		}
		checkBinHash(result);
		return result;
	}

	@Override @AvailMethod
	boolean o_BinHasElementWithHash (
		final AvailObject object,
		final A_BasicObject elementObject,
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
	 * Remove elementObject from the bin object, if present. Answer the
	 * resulting bin. The bin may be modified if it's mutable and canDestroy.
	 */
	@Override @AvailMethod
	AvailObject o_BinRemoveElementHashLevelCanDestroy (
		final AvailObject object,
		final A_BasicObject elementObject,
		final int elementObjectHash,
		final byte myLevel,
		final boolean canDestroy)
	{
		assert level == myLevel;
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
				// Produce a smaller copy, truncating the last entry, then
				// replace the found element with the last entry of the original
				// bin.  Note that this changes the (irrelevant) order.
				final int oldHash = object.slot(BIN_HASH);
				final AvailObject result = newLike(
					descriptorFor(MUTABLE, level), object, -1, 0);
				if (searchIndex != oldSize)
				{
					result.setSlot(
						BIN_ELEMENT_AT_,
						searchIndex,
						object.slot(BIN_ELEMENT_AT_, oldSize));
				}
				result.setSlot(BIN_HASH, oldHash - elementObjectHash);
				if (!canDestroy)
				{
					result.makeSubobjectsImmutable();
				}
				checkBinHash(result);
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
		final AvailObject object,
		final A_Set potentialSuperset)
	{
		// Check if object, a bin, holds a subset of aSet's elements.
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
	int o_BinSize (final AvailObject object)
	{
		// Answer how many elements this bin contains.
		return object.variableObjectSlotsCount();
	}

	@Override @AvailMethod
	A_Type o_BinUnionKind (final AvailObject object)
	{
		// Answer the nearest kind of the union of the types of this bin's
		// elements. I'm supposed to be small, so recalculate it per request.
		A_Type unionKind = object.slot(BIN_ELEMENT_AT_, 1).kind();
		final int limit = object.variableObjectSlotsCount();
		for (int index = 2; index <= limit; index++)
		{
			unionKind = unionKind.typeUnion(
				object.slot(BIN_ELEMENT_AT_, index).kind());
		}
		return unionKind;
	}

	@Override @AvailMethod
	boolean o_BinElementsAreAllInstancesOfKind (
		final AvailObject object,
		final A_Type kind)
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

	@Override
	SetIterator o_SetBinIterator (final AvailObject object)
	{
		return new SetIterator()
		{
			final int limit = object.variableObjectSlotsCount();

			int index = 1;

			@Override
			public AvailObject next ()
			{
				assert index <= limit;
				return object.slot(BIN_ELEMENT_AT_, index++);
			}

			@Override
			public boolean hasNext ()
			{
				return index <= limit;
			}
		};
	}

	/**
	 * Create a mutable linear bin at the specified level with the given size
	 * and bin hash. The caller is responsible for initializing the elements
	 * and making them immutable if necessary.
	 *
	 * @param level The level of the new bin.
	 * @param size The number of elements in the new bin.
	 * @param hash The hash of the bin's elements, or zero if unknown.
	 * @return A new linear set bin with uninitialized element slots.
	 */
	public static AvailObject createBin (
		final byte level,
		final int size,
		final int hash)
	{
		final AvailObject instance = descriptorFor(MUTABLE, level).create(size);
		instance.setSlot(BIN_HASH, hash);
		return instance;
	}

	/**
	 * Create a mutable 2-element linear bin at the specified level and with the
	 * specified elements. The caller is responsible for making the elements
	 * immutable if necessary.
	 *
	 * @param level The level of the new bin.
	 * @param firstElement The first element of the new bin.
	 * @param secondElement The second element of the new bin.
	 * @return A 2-element set bin.
	 */
	public static AvailObject createPair (
		final byte level,
		final A_BasicObject firstElement,
		final A_BasicObject secondElement)
	{
		final AvailObject instance = descriptorFor(MUTABLE, level).create(2);
		instance.setSlot(BIN_ELEMENT_AT_, 1, firstElement);
		instance.setSlot(BIN_ELEMENT_AT_, 2, secondElement);
		instance.setSlot(BIN_HASH, firstElement.hash() + secondElement.hash());
		checkBinHash(instance);
		return instance;
	}

	/**
	 * The number of distinct levels at which {@linkplain LinearSetBinDescriptor
	 * linear bins} may occur.
	 */
	static final byte numberOfLevels = 6;

	/**
	 * Answer a suitable descriptor for a linear bin with the specified
	 * mutability and at the specified level.
	 *
	 * @param flag The desired {@linkplain Mutability mutability}.
	 * @param level The level for the bins using the descriptor.
	 * @return The descriptor with the requested properties.
	 */
	private static LinearSetBinDescriptor descriptorFor (
		final Mutability flag,
		final byte level)
	{
		assert 0 <= level && level < numberOfLevels;
		return descriptors[level * 3 + flag.ordinal()];
	}

	/**
	 * Construct a new {@link LinearSetBinDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 * @param level
	 *        The depth of the bin in the hash tree.
	 */
	private LinearSetBinDescriptor (
		final Mutability mutability,
		final int level)
	{
		super(mutability, ObjectSlots.class, IntegerSlots.class, level);
	}

	/**
	 * {@link LinearSetBinDescriptor}s clustered by mutability and level.
	 */
	static final LinearSetBinDescriptor[] descriptors;

	static
	{
		descriptors = new LinearSetBinDescriptor[numberOfLevels * 3];
		int target = 0;
		for (int level = 0; level < numberOfLevels; level++)
		{
			for (final Mutability mut : Mutability.values())
			{
				descriptors[target++] =
					new LinearSetBinDescriptor(mut, level);
			}
		}
	}

	@Override
	LinearSetBinDescriptor mutable ()
	{
		return descriptorFor(MUTABLE, level);
	}

	@Override
	LinearSetBinDescriptor immutable ()
	{
		return descriptorFor(IMMUTABLE, level);
	}

	@Override
	LinearSetBinDescriptor shared ()
	{
		return descriptorFor(SHARED, level);
	}

	/**
	 * The canonical array of empty bins, one for each level.
	 */
	private final static AvailObject [] emptyBins =
		new AvailObject [numberOfLevels];

	static
	{
		for (int i = 0; i < numberOfLevels; i++)
		{
			final AvailObject bin = createBin((byte) i, 0, 0);
			bin.makeShared();
			emptyBins[i] = bin;
		}
	}

	/**
	 * Answer an empty bin for the specified level.
	 *
	 * @param level The level at which this bin occurs.
	 * @return An empty bin.
	 */
	static AvailObject emptyBinForLevel (final byte level)
	{
		return emptyBins[level];
	}
}
