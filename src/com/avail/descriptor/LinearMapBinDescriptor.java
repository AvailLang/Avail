/**
 * LinearMapBinDescriptor.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

import com.avail.annotations.AvailMethod;
import com.avail.annotations.InnerAccess;
import com.avail.descriptor.MapDescriptor.Entry;
import com.avail.descriptor.MapDescriptor.MapIterable;

import java.util.NoSuchElementException;

import static com.avail.descriptor.AvailObjectRepresentation.newLike;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.LinearMapBinDescriptor.IntegerSlots.*;
import static com.avail.descriptor.LinearMapBinDescriptor.ObjectSlots.*;
import static com.avail.descriptor.Mutability.*;
import static com.avail.descriptor.NilDescriptor.nil;

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
		 * A long holding {@link BitField}s containing the combined keys hash
		 * and the combined values hash or zero.
		 */
		COMBINED_HASHES,

		/**
		 * The hash values of the keys present in this bin.  These are recorded
		 * separately here to reduce the cost of locating a particular key.
		 */
		KEY_HASHES_AREA_;

		/**
		 * The sum of the hashes of the elements recursively within this bin.
		 */
		public static final BitField KEYS_HASH = bitField(
			COMBINED_HASHES, 0, 32);

		/**
		 * The sum of the hashes of the elements recursively within this bin,
		 * or zero if not computed.
		 */
		public static final BitField VALUES_HASH_OR_ZERO = bitField(
			COMBINED_HASHES, 32, 32);

		static
		{
			assert MapBinDescriptor.IntegerSlots.COMBINED_HASHES.ordinal()
				== COMBINED_HASHES.ordinal();
			assert MapBinDescriptor.IntegerSlots.KEYS_HASH
				.isSamePlaceAs(KEYS_HASH);
			assert MapBinDescriptor.IntegerSlots.VALUES_HASH_OR_ZERO
				.isSamePlaceAs(VALUES_HASH_OR_ZERO);
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The union of the types of all keys recursively within this bin.
		 * If this is {@linkplain NilDescriptor#nil() top}, then it can
		 * be recomputed when needed and cached.
		 */
		BIN_KEY_UNION_KIND_OR_NULL,

		/**
		 * The union of the types of all keys recursively within this bin.
		 * If this is {@linkplain NilDescriptor#nil() top}, then it can
		 * be recomputed when needed and cached.
		 */
		BIN_VALUE_UNION_KIND_OR_NULL,

		/**
		 * The elements of this bin. The elements are never sub-bins, since
		 * this is a {@linkplain LinearMapBinDescriptor linear bin}, a leaf bin.
		 */
		BIN_SLOT_AT_
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == COMBINED_HASHES
			|| e == BIN_KEY_UNION_KIND_OR_NULL
			|| e == BIN_VALUE_UNION_KIND_OR_NULL;
	}

	/**
	 * Debugging flag to force deep, expensive consistency checks.
	 */
	private static final boolean shouldCheckConsistency = false; //XXX

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
			final int numObjectSlots = object.variableObjectSlotsCount();
			assert (numObjectSlots & 1) == 0;
			final int numEntries = numObjectSlots >> 1;
			assert numEntries == entryCount(object);
			final int numIntegerSlots = object.variableIntegerSlotsCount();
			assert numIntegerSlots == ((numEntries + 1) >> 1);
			int computedKeyHashSum = 0;
			int computedValueHashSum = 0;
			for (int i = 1; i <= numEntries; i++)
			{
				final int keyHash = object.intSlot(KEY_HASHES_AREA_, i);
				final A_BasicObject key =
					object.slot(BIN_SLOT_AT_, (i << 1) - 1);
				final A_BasicObject value = object.slot(BIN_SLOT_AT_, i << 1);
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

	/**
	 * Answer how many <key,value> pairs are present in the given linear map
	 * bin.
	 *
	 * @param object
	 *        An {@link AvailObject} whose descriptor is a {@link
	 *        LinearMapBinDescriptor}.
	 * @return The number of entries in the bin.
	 */
	@InnerAccess static int entryCount (final AvailObject object)
	{
		return object.variableObjectSlotsCount() >> 1;
	}

	@Override @AvailMethod
	AvailObject o_BinElementAt (final AvailObject object, final int subscript)
	{
		return object.slot(BIN_SLOT_AT_, subscript);
	}

	@Override @AvailMethod
	int o_BinSize (final AvailObject object)
	{
		// Answer how many (key,value) pairs this bin contains.
		return entryCount(object);
	}

	@Override
	AvailObject o_MapBinAtHash (
		final AvailObject object,
		final A_BasicObject key,
		final int keyHash)
	{
		final int limit = entryCount(object);
		for (int i = 1; i <= limit; i++)
		{
			if (object.intSlot(KEY_HASHES_AREA_, i) == keyHash
				&& object.slot(BIN_SLOT_AT_, i * 2 - 1).equals(key))
			{
				return object.slot(BIN_SLOT_AT_, i * 2);
			}
		}
		// Not found. Answer nil.
		return nil();
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
		assert myLevel == level;
		final int oldSize = entryCount(object);
		for (int i = 1; i <= oldSize; i++)
		{
			if (object.intSlot(KEY_HASHES_AREA_, i) == keyHash
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
						object.setSlot(
							BIN_VALUE_UNION_KIND_OR_NULL, nil());
						// No need to clear the key union kind, since the keys
						// didn't change.
						if (!canDestroy)
						{
							object.makeImmutable();
						}
					}
					check(object);
					return object;
				}
				// The key is present with a different value.
				final AvailObject newBin;
				if (canDestroy && isMutable())
				{
					newBin = object;
				}
				else
				{
					if (isMutable())
					{
						object.makeSubobjectsImmutable();
					}
					newBin = newLike(descriptorFor(
						MUTABLE,
						level), object, 0, 0);
				}
				newBin.setSlot(BIN_SLOT_AT_, i * 2, value);
				newBin.setSlot(VALUES_HASH_OR_ZERO, 0);
				newBin.setSlot(BIN_KEY_UNION_KIND_OR_NULL, nil());
				newBin.setSlot(
					BIN_VALUE_UNION_KIND_OR_NULL, nil());
				check(newBin);
				return newBin;
			}
		}
		// It's not present, so grow the list.  Keep it simple for now by always
		// replacing the list.
		if (myLevel < numberOfLevels - 1 && oldSize >= thresholdToHash)
		{
			// Convert to a hashed bin.
			int bitPosition = bitShift(keyHash, -6 * myLevel) & 63;
			long bitVector = 1L << bitPosition;
			for (int i = 1; i <= oldSize; i++)
			{
				final int anotherKeyHash =
					object.intSlot(KEY_HASHES_AREA_, i);
				bitPosition = bitShift(anotherKeyHash, -6 * myLevel) & 63;
				bitVector |= 1L << bitPosition;
			}
			final AvailObject result =
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
					eachHash = object.intSlot(KEY_HASHES_AREA_, i);
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
		final AvailObject result = newLike(
			descriptorFor(MUTABLE, myLevel),
			object,
			2,
			(oldSize & 1) ^ 1);  // Grow if it had an even number of ints
		result.setSlot(KEYS_HASH, object.mapBinKeysHash() + keyHash);
		result.setSlot(VALUES_HASH_OR_ZERO, 0);
		result.setIntSlot(KEY_HASHES_AREA_, oldSize + 1, keyHash);
		result.setSlot(BIN_SLOT_AT_, oldSize * 2 + 1, key);
		result.setSlot(BIN_SLOT_AT_, oldSize * 2 + 2, value);
		final A_Type oldKeyKind = object.slot(BIN_KEY_UNION_KIND_OR_NULL);
		if (!oldKeyKind.equalsNil())
		{
			result.setSlot(
				BIN_KEY_UNION_KIND_OR_NULL,
				oldKeyKind.typeUnion(key.kind()));
		}
		final A_Type oldValueKind = object.slot(BIN_VALUE_UNION_KIND_OR_NULL);
		if (!oldValueKind.equalsNil())
		{
			result.setSlot(
				BIN_VALUE_UNION_KIND_OR_NULL,
				oldValueKind.typeUnion(value.kind()));
		}
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
		check(object);
		final int oldSize = entryCount(object);
		for (int searchIndex = 1; searchIndex <= oldSize; searchIndex++)
		{
			if (object.intSlot(KEY_HASHES_AREA_, searchIndex) == keyHash
				&& object.slot(BIN_SLOT_AT_, searchIndex * 2 - 1).equals(key))
			{
				if (oldSize == 1)
				{
					return nil();
				}
				final AvailObject result = newLike(
					descriptorFor(MUTABLE, level),
					object,
					-2,
					-(oldSize & 1));  // Reduce size only if it was odd
				if (searchIndex < oldSize)
				{
					result.setIntSlot(
						KEY_HASHES_AREA_,
						searchIndex,
						object.intSlot(KEY_HASHES_AREA_, oldSize));
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
				result.setSlot(BIN_KEY_UNION_KIND_OR_NULL, nil());
				result.setSlot(
					BIN_VALUE_UNION_KIND_OR_NULL, nil());
				if (!canDestroy)
				{
					result.makeSubobjectsImmutable();
				}
				check(result);
				return result;
			}
		}
		if (!canDestroy)
		{
			object.makeImmutable();
		}
		check(object);
		return object;
	}

	/**
	 * Compute this bin's key type, hoisted up to the nearest kind.
	 *
	 * @param object The {@link LinearMapBinDescriptor bin} to scan.
	 * @return The union of the kinds of this bin's keys.
	 */
	private A_Type computeKeyKind (final AvailObject object)
	{
		A_Type keyType = bottom();
		for (int i = entryCount(object) * 2 - 1; i >= 1; i -= 2)
		{
			final AvailObject entry = object.slot(BIN_SLOT_AT_, i);
			keyType = keyType.typeUnion(entry.kind());
		}
		if (isShared())
		{
			keyType = keyType.makeShared();
		}
		return keyType;
	}

	/**
	 * Compute and install the bin key union kind for the specified
	 * {@linkplain LinearMapBinDescriptor object}.
	 *
	 * @param object A linear map bin.
	 * @return The union of the key types as a kind.
	 */
	private A_Type mapBinKeyUnionKind (final AvailObject object)
	{
		A_Type keyType = object.slot(BIN_KEY_UNION_KIND_OR_NULL);
		if (keyType.equalsNil())
		{
			keyType = computeKeyKind(object);
			object.setSlot(BIN_KEY_UNION_KIND_OR_NULL, keyType);
		}
		return keyType;
	}

	@Override @AvailMethod
	A_Type o_MapBinKeyUnionKind (final AvailObject object)
	{
		if (isShared())
		{
			synchronized (object)
			{
				return mapBinKeyUnionKind(object);
			}
		}
		return mapBinKeyUnionKind(object);
	}

	/**
	 * Compute this bin's value type, hoisted up to the nearest kind.
	 *
	 * @param object The {@link LinearMapBinDescriptor bin} to scan.
	 * @return The union of the kinds of this bin's values.
	 */
	private A_Type computeValueKind (final AvailObject object)
	{
		A_Type valueType = bottom();
		for (int i = entryCount(object) * 2; i >= 1; i -= 2)
		{
			final AvailObject entry = object.slot(BIN_SLOT_AT_, i);
			valueType = valueType.typeUnion(entry.kind());
		}
		if (isShared())
		{
			valueType = valueType.makeShared();
		}
		return valueType;
	}

	/**
	 * Compute and install the bin value union kind for the specified
	 * {@linkplain LinearMapBinDescriptor object}.
	 *
	 * @param object An object.
	 * @return A key type.
	 */
	private A_Type mapBinValueUnionKind (final AvailObject object)
	{
		A_Type valueType = object.slot(BIN_VALUE_UNION_KIND_OR_NULL);
		if (valueType.equalsNil())
		{
			valueType = computeValueKind(object);
			object.setSlot(BIN_VALUE_UNION_KIND_OR_NULL, valueType);
		}
		return valueType;
	}

	@Override @AvailMethod
	A_Type o_MapBinValueUnionKind (final AvailObject object)
	{
		if (isShared())
		{
			synchronized (object)
			{
				return mapBinValueUnionKind(object);
			}
		}
		return mapBinValueUnionKind(object);
	}

	/**
	 * Lazily compute and install the hash of the values within the specified
	 * {@link LinearMapBinDescriptor}.
	 *
	 * @param object An object.
	 * @return A hash.
	 */
	private static int mapBinValuesHash (final AvailObject object)
	{
		int valuesHash = object.slot(VALUES_HASH_OR_ZERO);
		if (valuesHash == 0)
		{
			final int size = entryCount(object);
			for (int i = 1; i <= size; i++)
			{
				valuesHash += object.slot(BIN_SLOT_AT_, i * 2).hash();
			}
			object.setSlot(VALUES_HASH_OR_ZERO, valuesHash);
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
			final int limit = entryCount(object);
			int nextIndex = 1;

			@Override
			public Entry next ()
			{
				if (nextIndex > limit)
				{
					throw new NoSuchElementException();
				}
				entry.setKeyAndHashAndValue(
					object.slot(BIN_SLOT_AT_, nextIndex * 2 - 1),
					object.intSlot(KEY_HASHES_AREA_, nextIndex),
					object.slot(BIN_SLOT_AT_, nextIndex * 2));
				nextIndex++;
				return entry;
			}

			@Override
			public boolean hasNext ()
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
				2, 1, descriptor);
		bin.setSlot(KEYS_HASH, keyHash);
		bin.setSlot(VALUES_HASH_OR_ZERO, 0);
		bin.setSlot(BIN_KEY_UNION_KIND_OR_NULL, nil());
		bin.setSlot(BIN_VALUE_UNION_KIND_OR_NULL, nil());
		bin.setIntSlot(KEY_HASHES_AREA_, 1, keyHash);
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
		super(
			mutability,
			TypeTag.MAP_LINEAR_BIN_TAG,
			ObjectSlots.class,
			IntegerSlots.class, level);
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
			for (final Mutability mut : Mutability.values())
			{
				descriptors[target++] =
					new LinearMapBinDescriptor(mut, level);
			}
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
