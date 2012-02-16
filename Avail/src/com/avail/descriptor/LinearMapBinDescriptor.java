/**
 * LinearMapBinDescriptor.java
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

import static com.avail.descriptor.LinearMapBinDescriptor.IntegerSlots.*;
import static com.avail.descriptor.LinearMapBinDescriptor.ObjectSlots.*;
import com.avail.annotations.*;

/**
 * A {@code LinearMapBinDescriptor} is a leaf bin in a {@link MapDescriptor
 * map}'s hierarchy of bins.  It consists of a (usually) small number of keys
 * and values in no particular order.  If more elements need to be stored, a
 * {@linkplain HashedMapBinDescriptor hashed bin} will be used instead.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class LinearMapBinDescriptor
extends MapBinDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots implements IntegerSlotsEnum
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
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * The elements of this bin.  The elements are never sub-bins, since
		 * this is a {@linkplain LinearMapBinDescriptor linear bin}, a leaf bin.
		 */
		BIN_SLOT_AT_
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final @NotNull AbstractSlotsEnum e)
	{
		return e == IntegerSlots.VALUES_HASH_OR_ZERO;
	}

	/**
	 * Debugging flag to force deep, expensive consistency checks.
	 */
	private final static boolean shouldCheckConsistency = false;

	/**
	 * Debugging flag to dump trace information to the console.
	 */
	private final static boolean shouldTrace = false;

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
	static void check (final @NotNull AvailObject object)
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
				final AvailObject key = object.slot(BIN_SLOT_AT_, i * 2 - 1);
				final AvailObject value = object.slot(BIN_SLOT_AT_, i * 2);
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

	static void trace (final String format, final Object... other)
	{
		if (shouldTrace)
		{
			System.out.printf(format, other);
		}
	}

	@Override @AvailMethod
	@NotNull AvailObject o_BinElementAt (
		final @NotNull AvailObject object,
		final int subscript)
	{
		return object.slot(BIN_SLOT_AT_, subscript);
	}

	@Override @AvailMethod
	void o_BinElementAtPut (
		final @NotNull AvailObject object,
		final int subscript,
		final @NotNull AvailObject value)
	{
		object.setSlot(BIN_SLOT_AT_, subscript, value);
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
	int o_BinSize (
		final @NotNull AvailObject object)
	{
		// Answer how many (key,value) pairs this bin contains.
		return object.variableIntegerSlotsCount();
	}

	@Override
	@NotNull AvailObject o_MapBinAtHash (
		final @NotNull AvailObject object,
		final @NotNull AvailObject key,
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
		// Not found.  Answer the null object.
		return NullDescriptor.nullObject();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_MapBinAtHashPutLevelCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject key,
		final int keyHash,
		final @NotNull AvailObject value,
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
				final AvailObject oldValue = object.slot(BIN_SLOT_AT_, i * 2);
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
						isMutableLevel(true, level),
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
			final AvailObject result =
				HashedMapBinDescriptor.createLevelBitVector(myLevel, bitVector);
			for (int i = 0; i <= oldSize; i++)
			{
				final AvailObject eachKey;
				final int eachHash;
				final AvailObject eachValue;
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
				final AvailObject localAddResult =
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
			isMutableLevel(true, myLevel),
			object,
			2,
			1);
		result.setSlot(KEYS_HASH, object.mapBinKeysHash() + keyHash);
		result.setSlot(VALUES_HASH_OR_ZERO, 0);
		result.setSlot(KEY_HASHES_, oldSize + 1, keyHash);
		result.setSlot(BIN_SLOT_AT_, oldSize * 2 + 1, key);
		result.setSlot(BIN_SLOT_AT_, oldSize * 2 + 2, value);
		if (canDestroy && isMutable)
		{
			// Ensure destruction of the old object doesn't drag along anything
			// shared, but don't go to the expense of marking anything in common
			// as shared.
			object.setToInvalidDescriptor();
		}
		else if (isMutable)
		{
			object.makeSubobjectsImmutable();
		}
		check(result);
		return result;
	}

	/**
	 * Remove the key from the bin object, if present.  Answer the resulting
	 * bin.  The bin may be modified if it's mutable and canDestroy.
	 */
	@Override @AvailMethod
	@NotNull AvailObject o_MapBinRemoveKeyHashCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject key,
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
					return NullDescriptor.nullObject();
				}
				//trace(" (found)");
				final AvailObject result = AvailObjectRepresentation.newLike(
					isMutableLevel(true, level),
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
	@NotNull AvailObject o_MapBinKeyUnionKind (
		final @NotNull AvailObject object)
	{
		// Answer the union of the types of this bin's keys.  I'm supposed to be
		// small, so recalculate it per request.
		AvailObject unionKind = BottomTypeDescriptor.bottom();
		final int limit = object.variableIntegerSlotsCount();
		for (int index = 1; index <= limit; index++)
		{
			unionKind = unionKind.typeUnion(
				object.slot(BIN_SLOT_AT_, index * 2 - 1).kind());
		}
		return unionKind;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_MapBinValueUnionKind (
		final @NotNull AvailObject object)
	{
		// Answer the union of the types of this bin's values.  I'm supposed to
		// be small, so recalculate it per request.
		AvailObject unionKind = BottomTypeDescriptor.bottom();
		final int limit = object.variableIntegerSlotsCount();
		for (int index = 1; index <= limit; index++)
		{
			unionKind = unionKind.typeUnion(
				object.slot(BIN_SLOT_AT_, index * 2).kind());
		}
		return unionKind;
	}

	@Override @AvailMethod
	int o_MapBinValuesHash (
		final @NotNull AvailObject object)
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


	/**
	 * Create a bin with a single (key,value) pair in it.
	 *
	 * @param key The key to include in the bin.
	 * @param keyHash The hash of the key, precomputed for performance.
	 * @param value The value to include in the bin.
	 * @param myLevel The level at which to label the bin.
	 * @return The new bin with only (key,value) in it.
	 */
	public static @NotNull AvailObject createSingle (
		final @NotNull AvailObject key,
		final int keyHash,
		final @NotNull AvailObject value,
		final byte myLevel)
	{
		//trace("%nnew Single");
		final LinearMapBinDescriptor descriptor =
			LinearMapBinDescriptor.isMutableLevel(true, myLevel);
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
	 * @param flag Whether the bins using the descriptor will be mutable.
	 * @param level The level for the bins using the descriptor.
	 * @return The descriptor with the requested properties.
	 */
	static LinearMapBinDescriptor isMutableLevel (
		final boolean flag,
		final byte level)
	{
		assert 0 <= level && level < numberOfLevels;
		return descriptors[level * 2 + (flag ? 0 : 1)];
	}

	/**
	 * Construct a new {@link LinearMapBinDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 * @param level The depth of the bin in the hash tree.
	 */
	LinearMapBinDescriptor (
		final boolean isMutable,
		final int level)
	{
		super(isMutable, level);
	}

	/**
	 * The array of {@link LinearMapBinDescriptor}s.
	 */
	static final LinearMapBinDescriptor[] descriptors;

	static
	{
		descriptors = new LinearMapBinDescriptor[numberOfLevels * 2];
		int target = 0;
		for (int level = 0; level < numberOfLevels; level++)
		{
			descriptors[target++] = new LinearMapBinDescriptor(true, level);
			descriptors[target++] = new LinearMapBinDescriptor(false, level);
		}
	};
}
