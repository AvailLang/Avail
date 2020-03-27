/*
 * LinearSetBinDescriptor.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package com.avail.descriptor.sets;

import com.avail.annotations.AvailMethod;
import com.avail.annotations.HideFieldInDebugger;
import com.avail.descriptor.*;
import com.avail.descriptor.AbstractDescriptor;
import com.avail.descriptor.representation.BitField;
import com.avail.descriptor.representation.IntegerSlotsEnum;
import com.avail.descriptor.representation.Mutability;
import com.avail.descriptor.representation.ObjectSlotsEnum;
import com.avail.descriptor.sets.SetDescriptor.SetIterator;
import com.avail.descriptor.types.A_Type;
import com.avail.descriptor.types.TypeTag;

import java.util.NoSuchElementException;
import java.util.function.IntFunction;

import static com.avail.descriptor.representation.AvailObjectRepresentation.newLike;
import static com.avail.descriptor.representation.Mutability.*;
import static com.avail.descriptor.sets.HashedSetBinDescriptor.*;
import static com.avail.descriptor.sets.LinearSetBinDescriptor.IntegerSlots.BIN_HASH;
import static com.avail.descriptor.sets.LinearSetBinDescriptor.ObjectSlots.BIN_ELEMENT_AT_;
import static java.lang.Long.bitCount;

/**
 * A {@code LinearSetBinDescriptor} is a leaf bin in a {@link SetDescriptor
 * set}'s hierarchy of bins.  It consists of a small number of distinct elements
 * in no particular order.  If more elements need to be stored, a {@linkplain
 * HashedSetBinDescriptor hashed bin} will be used instead.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class LinearSetBinDescriptor
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
		static final BitField BIN_HASH = AbstractDescriptor
			.bitField(BIN_HASH_AND_MORE, 0, 32);

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
		BIN_ELEMENT_AT_;
	}

	/**
	 * When a {@linkplain LinearSetBinDescriptor linear bin} reaches this many
	 * entries and it's not already at the bottom allowable level ({@link
	 * HashedSetBinDescriptor#numberOfLevels} - 1) of the hash tree, then
	 * convert it to a hashed bin.
	 */
	static final int thresholdToHash = 10;

	/** Whether to do sanity checks on linear set bins' hashes. */
	private static final boolean checkBinHashes = false;

	/**
	 * Check that this linear bin has a correct setBinHash.
	 *
	 * @param object A linear set bin.
	 */
	private static void checkBinHash (final AvailObject object)
	{
		if (checkBinHashes)
		{
			assert object.descriptor() instanceof LinearSetBinDescriptor;
			final int stored = object.setBinHash();
			int calculated = 0;
			for (int i = object.variableObjectSlotsCount(); i >= 1; i--)
			{
				final AvailObject subBin = object.slot(BIN_ELEMENT_AT_, i);
				final int subBinHash = subBin.hash();
				calculated += subBinHash;
			}
			assert calculated == stored : "Failed bin hash cross-check";
		}
	}

	@Override @AvailMethod
	protected AvailObject o_BinElementAt (final AvailObject object, final int subscript)
	{
		return object.slot(BIN_ELEMENT_AT_, subscript);
	}

	@Override @AvailMethod
	protected A_BasicObject o_SetBinAddingElementHashLevelCanDestroy (
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
		final int oldHash = object.setBinHash();
		final AvailObject result;
		if (myLevel < numberOfLevels - 1 && oldSize >= thresholdToHash)
		{
			final byte shift = (byte) (6 * myLevel);
			assert shift < 32;
			int bitPosition = (elementObjectHash >>> shift) & 63;
			long bitVector = 1L << bitPosition;
			for (int i = 1; i <= oldSize; i++)
			{
				final A_BasicObject element = object.slot(BIN_ELEMENT_AT_, i);
				bitPosition = (element.hash() >>> shift) & 63;
				bitVector |= 1L << bitPosition;
			}
			final int newLocalSize = bitCount(bitVector);
			result = createInitializedHashSetBin(
				myLevel, newLocalSize, bitVector);
			result.setBinAddingElementHashLevelCanDestroy(
				elementObject, elementObjectHash, myLevel, true);
			for (int i = 1; i <= oldSize; i++)
			{
				final A_BasicObject eachElement =
					object.slot(BIN_ELEMENT_AT_, i);
				final A_BasicObject localAddResult =
					result.setBinAddingElementHashLevelCanDestroy(
						eachElement, eachElement.hash(), myLevel, true);
				assert localAddResult.sameAddressAs(result)
					: "The element should have been added without reallocation";
			}
			assert result.setBinSize() == oldSize + 1;
			assert object.setBinHash() == oldHash;
			final int newHash = oldHash + elementObjectHash;
			assert result.setBinHash() == newHash;
			checkHashedSetBin(result);
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
	protected boolean o_BinHasElementWithHash (
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
	protected AvailObject o_BinRemoveElementHashLevelCanDestroy (
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
	protected boolean o_IsBinSubsetOf (
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
	protected int o_SetBinSize (final AvailObject object)
	{
		// Answer how many elements this bin contains.
		return object.variableObjectSlotsCount();
	}

	@Override @AvailMethod
	protected A_Type o_BinUnionKind (final AvailObject object)
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
	protected boolean o_BinElementsAreAllInstancesOfKind (
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
	protected SetIterator o_SetBinIterator (final AvailObject object)
	{
		return new SetIterator()
		{
			/** How many elements will be visited. */
			final int limit = object.variableObjectSlotsCount();

			/** The next one-based index to visit. */
			int index = 1;

			@Override
			public AvailObject next ()
			{
				if (index > limit)
				{
					throw new NoSuchElementException();
				}
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
	 * Create a mutable 2-element linear bin at the specified level and with the
	 * specified elements. The caller is responsible for making the elements
	 * immutable if necessary.
	 *
	 * @param level The level of the new bin.
	 * @param firstElement The first element of the new bin.
	 * @param secondElement The second element of the new bin.
	 * @return A 2-element set bin.
	 */
	public static AvailObject createLinearSetBinPair (
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
	 * Construct a new {@code LinearSetBinDescriptor}.
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
		super(
			mutability,
			TypeTag.SET_LINEAR_BIN_TAG,
			ObjectSlots.class,
			IntegerSlots.class, level);
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
			for (final Mutability mut : values())
			{
				descriptors[target++] =
					new LinearSetBinDescriptor(mut, level);
			}
		}
	}

	@Override
	public LinearSetBinDescriptor mutable ()
	{
		return descriptorFor(MUTABLE, level);
	}

	@Override
	public LinearSetBinDescriptor immutable ()
	{
		return descriptorFor(IMMUTABLE, level);
	}

	@Override
	public LinearSetBinDescriptor shared ()
	{
		return descriptorFor(SHARED, level);
	}

	/**
	 * The canonical array of empty bins, one for each level.
	 */
	private static final AvailObject [] emptyBins =
		new AvailObject [numberOfLevels];

	static
	{
		for (int i = 0; i < numberOfLevels; i++)
		{
			final AvailObject bin = descriptorFor(MUTABLE, (byte) i).create(0);
			bin.setSlot(BIN_HASH, 0);
			emptyBins[i] = bin.makeShared();
		}
	}

	/**
	 * Answer an empty bin for the specified level.
	 *
	 * @param level The level at which this bin occurs.
	 * @return An empty bin.
	 */
	public static AvailObject emptyLinearSetBin (final byte level)
	{
		return emptyBins[level];
	}

	/**
	 * Generate a linear set bin by extracting the specified number of values
	 * from the generator.  The values might not necessarily be unique, so
	 * reduce them.  If they are all the same value, answer the value itself as
	 * a singleton bin.
	 *
	 * <p>Each element is compared against all the others to detect duplicates
	 * while populating the bin.  If any duplicates are detected, a copy is made
	 * of the populated prefix of the bin.</p>
	 *
	 * @param level The level of the bin to create.
	 * @param size The number of values to extract from the generator.
	 * @param generator A generator to provide {@link AvailObject}s to store.
	 * @return A top-level linear set bin.
	 */
	static AvailObject generateLinearSetBinFrom (
		final byte level,
		final int size,
		final IntFunction<? extends A_BasicObject> generator)
	{
		final AvailObject bin = descriptorFor(MUTABLE, level).create(size);
		int hash = 0;
		int written = 0;
		outer: for (int i = 1; i <= size; i++)
		{
			final A_BasicObject element = generator.apply(i);
			for (int j = 1; j <= written; j++)
			{
				if (element.equals(bin.slot(BIN_ELEMENT_AT_, j)))
				{
					continue outer;
				}
			}
			bin.setSlot(BIN_ELEMENT_AT_, ++written, element);
			hash += element.hash();
		}
		bin.setSlot(BIN_HASH, hash);
		if (written == size)
		{
			return bin;
		}
		if (written == 1)
		{
			return bin.slot(BIN_ELEMENT_AT_, 1);
		}
		return newLike(bin.descriptor(), bin, written - size, 0);
	}
}
