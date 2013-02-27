/**
 * LinearMapBinDescriptor.java
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

import static com.avail.descriptor.LinearMapBinDescriptor.IntegerSlots.*;
import static com.avail.descriptor.LinearMapBinDescriptor.ObjectSlots.*;
import static com.avail.descriptor.Mutability.*;
import com.avail.annotations.*;
import com.avail.descriptor.MapDescriptor.*;

/**
 * A {@code LinearMapBinDescriptor} is a leaf bin in a {@link MapDescriptor
 * map}'s hierarchy of bins.  It consists of a (usually) small number of keys
 * and values in no particular order.  If more elements need to be stored, a
 * {@linkplain HashedMapBinDescriptor hashed bin} will be used instead.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
final class LinearMapBinDescriptor
extends MapBinDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * The sum of the hashes of the keys within this bin.
		 */
		KEYS_HASH,

		/**
		 * The sum of the hashes of the values within this bin.
		 */
		VALUES_HASH_OR_ZERO,

		/**
		 * The hash values of the keys present in this bin.  These are recorded
		 * separately here to reduce the cost of locating a particular key.
		 */
		KEY_HASHES_;

		static
		{
			assert MapBinDescriptor.IntegerSlots.KEYS_HASH.ordinal()
				== KEYS_HASH.ordinal();
			assert MapBinDescriptor.IntegerSlots.VALUES_HASH_OR_ZERO.ordinal()
				== VALUES_HASH_OR_ZERO.ordinal();
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The elements of this bin. The elements are never sub-bins, since
		 * this is a {@linkplain LinearMapBinDescriptor linear bin}, a leaf bin.
		 */
		BIN_SLOT_AT_
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == IntegerSlots.VALUES_HASH_OR_ZERO;
	}

	/**
	 * Debugging flag to force deep, expensive consistency checks.
	 */
	private final static boolean shouldCheckConsistency = false;

	/**
	 * When a {@linkplain LinearMapBinDescriptor linear bin} reaches this many
	 * entries and it's not already at the bottom allowable level ({@link
	 * #numberOfLevels} - 1) of the hash tree, then convert it to a hashed bin.
	 */
	private static final int thresholdToHash = 50;

	/**
	 * Check this {@linkplain LinearMapBinDescriptor linear map bin} for
	 * internal consistency.
	 *
	 * @param object A linear map bin.
	 */
	static void check (final AvailObject object)
	{
		if (shouldCheckConsistency)
		{
			assert object.descriptor() instanceof LinearMapBinDescriptor;
			//trace("%nCheck: %d", object.binSize());
			int computedKeyHashSum = 0;
			int computedValueHashSum = 0;
			final int size = object.variableIntegerSlotsCount();
			for (int i = 1; i <= size; i++)
			{
				final int keyHash = object.slot(KEY_HASHES_, i);
				final A_BasicObject key = object.slot(BIN_SLOT_AT_, i * 2 - 1);
				final A_BasicObject value = object.slot(BIN_SLOT_AT_, i * 2);
				assert key.hash() == keyHash;
				computedKeyHashSum += keyHash;
				computedValueHashSum += value.hash();
			}
			assert object.slot(KEYS_HASH) == computedKeyHashSum;
			final int storedValueHashSum = object.slot(VALUES_HASH_OR_ZERO);
			if (storedValueHashSum != 0)
			{
				assert storedValueHashSum == computedValueHashSum;
			}
		}
	}

	@Override @AvailMethod
	AvailObject o_BinElementAt (final AvailObject object, final int subscript)
	{
		return object.slot(BIN_SLOT_AT_, subscript);
	}

	@Override @AvailMethod
	void o_BinElementAtPut (
		final AvailObject object,
		final int subscript,
		final A_BasicObject value)
	{
		object.setSlot(BIN_SLOT_AT_, subscript, value);
	}

	@Override @AvailMethod
	int o_BinSize (final AvailObject object)
	{
		// Answer how many (key,value) pairs this bin contains.
		return object.variableIntegerSlotsCount();
	}

	@Override
	AvailObject o_MapBinAtHash (
		final AvailObject object,
		final A_BasicObject key,
		final int keyHash)
	{
		final int limit = object.variableIntegerSlotsCount();
		for (int i = 1; i <= limit; i++)
		{
			if (object.slot(KEY_HASHES_, i) == keyHash
				&& object.slot(BIN_SLOT_AT_, i * 2 - 1).equals(key))
			{
				return object.slot(BIN_SLOT_AT_, i * 2);
			}
		}
		// Not found. Answer nil.
		return NilDescriptor.nil();
	}

	@Override @AvailMethod
	A_BasicObject o_MapBinAtHashPutLevelCanDestroy (
		final AvailObject object,
		final A_BasicObject key,
		final int keyHash,
		final A_BasicObject value,
		final byte myLevel,
		final boolean canDestroy)
	{
		// Associate the key and value in this bin, potentially modifying it if
		// canDestroy and it's mutable.  Answer the new bin.  Note that the
		// client is responsible for marking the key and value as immutable if
		// other references exist.
		//trace("%nAtPut: %d", object.binSize());
		assert myLevel == level;
		final int limit = object.variableIntegerSlotsCount();
		for (int i = 1; i <= limit; i++)
		{
			if (object.slot(KEY_HASHES_, i) == keyHash
				&& object.slot(BIN_SLOT_AT_, i * 2 - 1).equals(key))
			{
				final A_BasicObject oldValue = object.slot(BIN_SLOT_AT_, i * 2);
				if (oldValue.equals(value))
				{
					// The (key,value) pair is present.
					if (isMutable())
					{
						// This may seem silly, but a common usage pattern is to
						// have a map whose values are sets.  Some key is looked
						// up, the value (set) is modified destructively, and
						// the resulting set is written back to the map.  If we
						// didn't clear the values hash here, it would stay
						// wrong after this compound operation.
						object.setSlot(VALUES_HASH_OR_ZERO, 0);
						if (!canDestroy)
						{
							object.makeImmutable();
						}
					}
					//trace("%n\tExisting (key,value)");
					check(object);
					return object;
				}
				// The key is present with a different value.
				//trace("%n\tReplace key");
				final AvailObject newBin;
				if (canDestroy && isMutable())
				{
					//trace(" (in place)");
					newBin = object;
				}
				else
				{
					if (isMutable())
					{
						object.makeSubobjectsImmutable();
					}
					//trace(" (copy)");
					newBin = AvailObjectRepresentation.newLike(
						descriptorFor(MUTABLE, level),
						object,
						0,
						0);
				}
				newBin.setSlot(BIN_SLOT_AT_, i * 2, value);
				newBin.setSlot(VALUES_HASH_OR_ZERO, 0);
				check(newBin);
				return newBin;
			}
		}
		// It's not present, so grow the list.  Keep it simple for now by always
		// replacing the list.
		final int oldSize = object.variableIntegerSlotsCount();
		if (myLevel < 7 && oldSize >= thresholdToHash)
		{
			// Convert to a hashed bin.
			//trace("%n\tCREATING HASHED BIN");
			int bitPosition = bitShift(keyHash, -5 * myLevel) & 31;
			int bitVector = bitShift(1, bitPosition);
			for (int i = 1; i <= oldSize; i++)
			{
				final int anotherKeyHash = object.slot(KEY_HASHES_, i);
				bitPosition = bitShift(anotherKeyHash, -5 * myLevel) & 31;
				bitVector |= bitShift(1, bitPosition);
			}
			final A_BasicObject result =
				HashedMapBinDescriptor.createLevelBitVector(myLevel, bitVector);
			for (int i = 0; i <= oldSize; i++)
			{
				final A_BasicObject eachKey;
				final int eachHash;
				final A_BasicObject eachValue;
				if (i == 0)
				{
					eachKey = key;
					eachHash = keyHash;
					eachValue = value;
				}
				else
				{
					eachKey = object.slot(BIN_SLOT_AT_, i * 2 - 1);
					eachHash = object.slot(KEY_HASHES_, i);
					eachValue = object.slot(BIN_SLOT_AT_, i * 2);
				}
				assert result.descriptor().isMutable();
				final A_BasicObject localAddResult =
					result.mapBinAtHashPutLevelCanDestroy(
						eachKey,
						eachHash,
						eachValue,
						myLevel,
						true);
				assert localAddResult.sameAddressAs(result)
				: "The element should have been added without copying";
			}
			assert result.binSize() == oldSize + 1;
			HashedMapBinDescriptor.check(result);
			return result;
		}
		//  Make a slightly larger linear bin and populate it.
		//trace("%n\tGrowing");
		final AvailObject result = AvailObjectRepresentation.newLike(
			descriptorFor(MUTABLE, myLevel),
			object,
			2,
			1);
		result.setSlot(KEYS_HASH, object.mapBinKeysHash() + keyHash);
		result.setSlot(VALUES_HASH_OR_ZERO, 0);
		result.setSlot(KEY_HASHES_, oldSize + 1, keyHash);
		result.setSlot(BIN_SLOT_AT_, oldSize * 2 + 1, key);
		result.setSlot(BIN_SLOT_AT_, oldSize * 2 + 2, value);
		if (canDestroy && isMutable())
		{
			// Ensure destruction of the old object doesn't drag along anything
			// shared, but don't go to the expense of marking anything in common
			// as shared.
			object.setToInvalidDescriptor();
		}
		else if (isMutable())
		{
			object.makeSubobjectsImmutable();
		}
		check(result);
		return result;
	}

	/**
	 * Remove the key from the bin object, if present. Answer the resulting
	 * bin. The bin may be modified if it's mutable and canDestroy.
	 */
	@Override @AvailMethod
	A_BasicObject o_MapBinRemoveKeyHashCanDestroy (
		final AvailObject object,
		final A_BasicObject key,
		final int keyHash,
		final boolean canDestroy)
	{
		//trace("%nRemove: %d", object.binSize());
		check(object);
		final int oldSize = object.variableIntegerSlotsCount();
		for (int searchIndex = 1; searchIndex <= oldSize; searchIndex++)
		{
			if (object.slot(KEY_HASHES_, searchIndex) == keyHash
				&& object.slot(BIN_SLOT_AT_, searchIndex * 2 - 1).equals(key))
			{
				if (oldSize == 1)
				{
					//trace(" (last element)");
					return NilDescriptor.nil();
				}
				//trace(" (found)");
				final AvailObject result = AvailObjectRepresentation.newLike(
					descriptorFor(MUTABLE, level),
					object,
					-2,
					-1);
				if (searchIndex < oldSize)
				{
					result.setSlot(
						KEY_HASHES_,
						searchIndex,
						object.slot(KEY_HASHES_, oldSize));
					result.setSlot(
						BIN_SLOT_AT_,
						searchIndex * 2 - 1,
						object.slot(BIN_SLOT_AT_, oldSize * 2 - 1));
					result.setSlot(
						BIN_SLOT_AT_,
						searchIndex * 2,
						object.slot(BIN_SLOT_AT_, oldSize * 2));
				}
				// Adjust keys hash by the removed key.
				result.setSlot(KEYS_HASH, object.slot(KEYS_HASH) - keyHash);
				result.setSlot(VALUES_HASH_OR_ZERO, 0);
				if (!canDestroy)
				{
					result.makeSubobjectsImmutable();
				}
				check(result);
				return result;
			}
		}
		//trace(" (not found)");
		if (!canDestroy)
		{
			object.makeImmutable();
		}
		check(object);
		return object;
	}

	@Override @AvailMethod
	A_Type o_MapBinKeyUnionKind (final AvailObject object)
	{
		// Answer the union of the types of this bin's keys. I'm supposed to be
		// small, so recalculate it per request.
		A_Type unionKind = BottomTypeDescriptor.bottom();
		final int limit = object.variableIntegerSlotsCount();
		for (int index = 1; index <= limit; index++)
		{
			unionKind = unionKind.typeUnion(
				object.slot(BIN_SLOT_AT_, index * 2 - 1).kind());
		}
		return unionKind;
	}

	@Override @AvailMethod
	A_Type o_MapBinValueUnionKind (final AvailObject object)
	{
		// Answer the union of the types of this bin's values. I'm supposed to
		// be small, so recalculate it per request.
		A_Type unionKind = BottomTypeDescriptor.bottom();
		final int limit = object.variableIntegerSlotsCount();
		for (int index = 1; index <= limit; index++)
		{
			unionKind = unionKind.typeUnion(
				object.slot(BIN_SLOT_AT_, index * 2).kind());
		}
		return unionKind;
	}

	/**
	 * Lazily compute and install the hash of the specified {@linkplain
	 * LinearMapBinDescriptor object}.
	 *
	 * @param object An object.
	 * @return A hash.
	 */
	private int mapBinValuesHash (final AvailObject object)
	{
		int valuesHash = object.slot(VALUES_HASH_OR_ZERO);
		if (valuesHash == 0)
		{
			//trace("%nVALUE_HASH:(");
			final int size = object.variableIntegerSlotsCount();
			for (int i = 1; i <= size; i++)
			{
				final int valueHash = object.slot(BIN_SLOT_AT_, i * 2).hash();
				//trace("%s%08x", (i==1?"":","), valueHash);
				valuesHash += valueHash;
			}
			object.setSlot(VALUES_HASH_OR_ZERO, valuesHash);
			//trace(")=%08x", valuesHash);
		}
		return valuesHash;
	}

	@Override @AvailMethod
	int o_MapBinValuesHash (final AvailObject object)
	{
		if (isShared())
		{
			synchronized (object)
			{
				return mapBinValuesHash(object);
			}
		}
		return mapBinValuesHash(object);
	}

	@Override
	MapIterable o_MapBinIterable (final AvailObject object)
	{
		return new MapIterable()
		{
			final int limit = object.variableIntegerSlotsCount();
			int nextIndex = 1;

			@Override
			public final Entry next ()
			{
				entry.setKeyAndHashAndValue(
					object.slot(BIN_SLOT_AT_, nextIndex * 2 - 1),
					object.slot(KEY_HASHES_, nextIndex),
					object.slot(BIN_SLOT_AT_, nextIndex * 2));
				nextIndex++;
				return entry;
			}

			@Override
			public final boolean hasNext ()
			{
				return nextIndex <= limit;
			}
		};
	}

	/**
	 * Create a bin with a single (key,value) pair in it.
	 *
	 * @param key The key to include in the bin.
	 * @param keyHash The hash of the key, precomputed for performance.
	 * @param value The value to include in the bin.
	 * @param myLevel The level at which to label the bin.
	 * @return The new bin with only (key,value) in it.
	 */
	static A_BasicObject createSingle (
		final A_BasicObject key,
		final int keyHash,
		final A_BasicObject value,
		final byte myLevel)
	{
		final LinearMapBinDescriptor descriptor =
			LinearMapBinDescriptor.descriptorFor(MUTABLE, myLevel);
		final AvailObject bin =
			AvailObject.newObjectIndexedIntegerIndexedDescriptor(
				2,
				1,
				descriptor);
		bin.setSlot(KEYS_HASH, keyHash);
		bin.setSlot(VALUES_HASH_OR_ZERO, 0);
		bin.setSlot(KEY_HASHES_, 1, keyHash);
		bin.setSlot(BIN_SLOT_AT_, 1, key);
		bin.setSlot(BIN_SLOT_AT_, 2, value);
		check(bin);
		return bin;
	}

	/**
	 * The number of distinct levels at which {@linkplain LinearMapBinDescriptor
	 * linear bins} may occur.
	 */
	static final byte numberOfLevels = 8;

	/**
	 * Answer a suitable descriptor for a linear bin with the specified
	 * mutability and at the specified level.
	 *
	 * @param flag The desired {@linkplain Mutability mutability}.
	 * @param level The level for the bins using the descriptor.
	 * @return The descriptor with the requested properties.
	 */
	private static LinearMapBinDescriptor descriptorFor (
		final Mutability flag,
		final byte level)
	{
		assert 0 <= level && level < numberOfLevels;
		return descriptors[level * 3 + flag.ordinal()];
	}

	/**
	 * Construct a new {@link LinearMapBinDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 * @param level
	 * The depth of the bin in the hash tree.
	 */
	private LinearMapBinDescriptor (
		final Mutability mutability,
		final int level)
	{
		super(mutability, level);
	}

	/**
	 * {@link LinearMapBinDescriptor}s clustered by mutability and level.
	 */
	static final LinearMapBinDescriptor[] descriptors;

	static
	{
		descriptors = new LinearMapBinDescriptor[numberOfLevels * 3];
		int target = 0;
		for (int level = 0; level < numberOfLevels; level++)
		{
			descriptors[target++] =
				new LinearMapBinDescriptor(MUTABLE, level);
			descriptors[target++] =
				new LinearMapBinDescriptor(IMMUTABLE, level);
			descriptors[target++] =
				new LinearMapBinDescriptor(SHARED, level);
		}
	}

	@Override
	LinearMapBinDescriptor mutable ()
	{
		return descriptorFor(MUTABLE, level);
	}

	@Override
	LinearMapBinDescriptor immutable ()
	{
		return descriptorFor(IMMUTABLE, level);
	}

	@Override
	LinearMapBinDescriptor shared ()
	{
		return descriptorFor(SHARED, level);
	}
}
