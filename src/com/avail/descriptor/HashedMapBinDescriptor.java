/**
 * HashedMapBinDescriptor.java
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

import static java.lang.Long.bitCount;
import static com.avail.descriptor.HashedMapBinDescriptor.IntegerSlots.*;
import static com.avail.descriptor.HashedMapBinDescriptor.ObjectSlots.*;
import static com.avail.descriptor.Mutability.*;
import java.util.*;

import com.avail.annotations.AvailMethod;
import com.avail.descriptor.MapDescriptor.MapIterable;
import com.avail.descriptor.MapDescriptor.Entry;

/**
 * This class implements the internal hashed nodes of a Bagwell Ideal Hash Tree.
 * It's similar to {@link HashedSetBinDescriptor}, but has operations suitable
 * for use by a {@linkplain MapDescriptor map} rather than a {@linkplain
 * SetDescriptor set}.  The basic idea is that small bins are simple {@linkplain
 * LinearMapBinDescriptor linear structures}, but larger bins use the hash of
 * the key to determine which one of the up to 64 child bins is responsible for
 * that key.  Different levels of the tree use different 6-bit regions of the
 * hash values.  We could always store 64 slots, but Bagwell's mechanism is to
 * store a 64-bit vector where a 1 bit indicates that the corresponding index
 * (0..63) extracted from the hash value has a pointer to the corresponding
 * sub-bin.  If the bit is 0 then that pointer is elided entirely.  By suitable
 * use of bit shifting, masking, and {@linkplain Integer#bitCount counting}, one
 * is able to extract the 6 appropriate dispatch bits and access the Nth sub-bin
 * or determine that it's not already present.  This mechanism produces a hash
 * tree no deeper than about 6 levels, even for a huge number of entries.  It
 * also allows efficient "persistent" manipulation (in the function programming
 * sense).  Given a map one can produce another map that has a small number of
 * edits (new keys, removed keys, new values for existing keys) using only a few
 * additional bins – without disrupting the original map.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd Smith &lt;todd@availlang.org&gt;
 */
final class HashedMapBinDescriptor
extends MapBinDescriptor
{
	/**
	 * A static switch for enabling slow, detailed correctness checks.
	 */
	private final static boolean shouldCheck = false; //XXX

	/**
	 * Make sure the {@link HashedMapBinDescriptor hashed map bin} is
	 * well-formed at this moment.
	 *
	 * @param object A hashed bin used by maps.
	 */
	static void check (final AvailObject object)
	{
		if (shouldCheck)
		{
			int keyHashSum = 0;
			int valueHashSum = 0;
			int totalCount = 0;
			final int size = object.variableObjectSlotsCount();
			assert bitCount(object.slot(BIT_VECTOR)) == size;
			for (int i = 1; i <= size; i++)
			{
				final AvailObject subBin = object.slot(SUB_BINS_, i);
				keyHashSum += subBin.mapBinKeysHash();
				valueHashSum += subBin.mapBinValuesHash();
				totalCount += subBin.binSize();
			}
			assert object.slot(KEYS_HASH) == keyHashSum;
			final int storedValuesHash =
				object.mutableSlot(VALUES_HASH_OR_ZERO);
			assert storedValuesHash == 0 || storedValuesHash == valueHashSum;
			assert object.slot(BIN_SIZE) == totalCount;
		}
	}

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
		 * The total number of elements recursively contained within this bin.
		 */
		BIN_SIZE,

		/**
		 * A bit vector indicating which (masked, shifted) hash values are
		 * non-empty and represented by a slot.
		 */
		BIT_VECTOR;

		/**
		 * The sum of the hashes of the elements recursively within this bin.
		 */
		public final static BitField KEYS_HASH = bitField(
			COMBINED_HASHES, 0, 32);

		/**
		 * The sum of the hashes of the elements recursively within this bin,
		 * or zero if not computed.
		 */
		public final static BitField VALUES_HASH_OR_ZERO = bitField(
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
		 * The actual bin elements or sub-bins.  Each slot corresponds to a 1
		 * bit in the bit vector, treating it as an unsigned vector of bits.
		 */
		SUB_BINS_
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == COMBINED_HASHES
			|| e == BIN_KEY_UNION_KIND_OR_NULL
			|| e == BIN_VALUE_UNION_KIND_OR_NULL;
	}

	@Override @AvailMethod
	AvailObject o_BinElementAt (final AvailObject object, final int subscript)
	{
		return object.slot(SUB_BINS_, subscript);
	}

	@Override @AvailMethod
	int o_BinSize (final AvailObject object)
	{
		return (int)object.slot(BIN_SIZE);
	}

	/**
	 * Check if object, a bin, holds a subset of aSet's elements.
	 */
	@Override @AvailMethod
	boolean o_IsBinSubsetOf (
		final AvailObject object,
		final A_Set potentialSuperset)
	{
		final int size = object.variableObjectSlotsCount();
		for (int index = 1; index <= size; index++)
		{
			final A_BasicObject subBin = object.slot(SUB_BINS_, index);
			if (!subBin.isBinSubsetOf(potentialSuperset))
			{
				return false;
			}
		}
		return true;
	}

	@Override
	boolean o_IsHashedMapBin (final AvailObject object)
	{
		return true;
	}

	/**
	 * Compute this bin's key type and value type.
	 *
	 * @param object
	 *        The {@link HashedMapBinDescriptor bin} to populate with key
	 *        and value type information.
	 */
	private void computeKeyAndValueKinds (final AvailObject object)
	{
		A_Type keyType = BottomTypeDescriptor.bottom();
		A_Type valueType = BottomTypeDescriptor.bottom();
		final int binCount =
			object.objectSlotsCount() - numberOfFixedObjectSlots;
		for (int i = 1; i <= binCount; i++)
		{
			final AvailObject subBin = object.slot(SUB_BINS_, i);
			keyType = keyType.typeUnion(subBin.mapBinKeyUnionKind());
			valueType = valueType.typeUnion(subBin.mapBinValueUnionKind());
		}
		if (isShared())
		{
			keyType = keyType.traversed().makeShared();
			valueType = valueType.traversed().makeShared();
		}
		object.setSlot(BIN_KEY_UNION_KIND_OR_NULL, keyType);
		object.setSlot(BIN_VALUE_UNION_KIND_OR_NULL, valueType);
	}

	/**
	 * Compute and install the bin key union kind for the specified
	 * {@linkplain HashedMapBinDescriptor object}.
	 *
	 * @param object An object.
	 * @return A key type.
	 */
	private AvailObject mapBinKeyUnionKind (final AvailObject object)
	{
		AvailObject keyType = object.slot(BIN_KEY_UNION_KIND_OR_NULL);
		if (keyType.equalsNil())
		{
			computeKeyAndValueKinds(object);
			keyType = object.slot(BIN_KEY_UNION_KIND_OR_NULL);
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
	 * Compute and install the bin value union kind for the specified
	 * {@linkplain HashedMapBinDescriptor object}.
	 *
	 * @param object An object.
	 * @return A key type.
	 */
	private AvailObject mapBinValueUnionKind (final AvailObject object)
	{
		AvailObject valueType = object.slot(BIN_VALUE_UNION_KIND_OR_NULL);
		if (valueType.equalsNil())
		{
			computeKeyAndValueKinds(object);
			valueType = object.slot(BIN_VALUE_UNION_KIND_OR_NULL);
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
	 * Add the given (key,value) pair to this bin, potentially modifying it if
	 * canDestroy is true and it's mutable.  Answer the new bin.  Note that the
	 * client is responsible for marking both the key and value as immutable if
	 * other references exists.
	 */
	@Override @AvailMethod
	A_BasicObject o_MapBinAtHashPutLevelCanDestroy (
		final AvailObject object,
		final A_BasicObject key,
		final int keyHash,
		final A_BasicObject value,
		final byte myLevel,
		final boolean canDestroy)
	{
		assert myLevel == level;
		check(object);
		// First, grab the appropriate 6 bits from the hash.
		final int oldKeysHash = object.mapBinKeysHash();
		final int oldSize = object.binSize();
		final int objectEntryCount = object.variableObjectSlotsCount();
		final int logicalIndex = (keyHash >>> shift) & 63;
		final long vector = object.slot(BIT_VECTOR);
		final long masked = vector & bitShift(1L, logicalIndex) - 1;
		final int physicalIndex = bitCount(masked) + 1;
		final int delta;
		final int hashDelta;
		AvailObject objectToModify;
		if ((vector & bitShift(1L, logicalIndex)) != 0)
		{
			// Sub-bin already exists for those hash bits.  Update the sub-bin.
			final AvailObject oldSubBin = object.slot(SUB_BINS_, physicalIndex);
			final int oldSubBinSize = oldSubBin.binSize();
			final int oldSubBinKeyHash = oldSubBin.mapBinKeysHash();
			final A_BasicObject newSubBin =
				oldSubBin.mapBinAtHashPutLevelCanDestroy(
					key,
					keyHash,
					value,
					(byte)(myLevel + 1),
					canDestroy);
			delta = newSubBin.binSize() - oldSubBinSize;
			hashDelta = newSubBin.mapBinKeysHash() - oldSubBinKeyHash;
			if (canDestroy && isMutable())
			{
				objectToModify = object;
			}
			else
			{
				if (!canDestroy & isMutable())
				{
					object.makeSubobjectsImmutable();
				}
				objectToModify = AvailObjectRepresentation.newLike(
					descriptorFor(MUTABLE, level), object, 0, 0);
			}
			objectToModify.setSlot(SUB_BINS_, physicalIndex, newSubBin);
		}
		else
		{
			// Add a sub-bin for that hash slot.
			delta = 1;
			hashDelta = keyHash;
			if (!canDestroy & isMutable())
			{
				object.makeSubobjectsImmutable();
			}
			objectToModify = descriptorFor(MUTABLE, level).create(
				objectEntryCount + 1);
			objectToModify.setSlot(
				BIT_VECTOR, vector | bitShift(1L, logicalIndex));
			for (int i = 1, end = physicalIndex - 1; i <= end; i++)
			{
				objectToModify.setSlot(SUB_BINS_, i, object.slot(SUB_BINS_,i));
			}
			final A_BasicObject newSingleBin =
				LinearMapBinDescriptor.createSingle(
					key,
					keyHash,
					value,
					(byte)(myLevel + 1));
			objectToModify.setSlot(SUB_BINS_, physicalIndex, newSingleBin);
			for (int i = physicalIndex; i <= objectEntryCount; i++)
			{
				objectToModify.setSlot(
					SUB_BINS_, i + 1, object.slot(SUB_BINS_, i));
			}
		}
		assert objectToModify.descriptor.isMutable();
		objectToModify.setSlot(KEYS_HASH, oldKeysHash + hashDelta);
		objectToModify.setSlot(VALUES_HASH_OR_ZERO, 0);
		objectToModify.setSlot(BIN_SIZE, oldSize + delta);
		objectToModify.setSlot(BIN_KEY_UNION_KIND_OR_NULL, NilDescriptor.nil());
		objectToModify.setSlot(
			BIN_VALUE_UNION_KIND_OR_NULL, NilDescriptor.nil());
		check(objectToModify);
		return objectToModify;
	}

	@Override
	AvailObject o_MapBinAtHash (
		final AvailObject object,
		final A_BasicObject key,
		final int keyHash)
	{
		// First, grab the appropriate 6 bits from the hash.
		final int logicalIndex = (keyHash >>> shift) & 63;
		final long vector = object.slot(BIT_VECTOR);
		if ((vector & bitShift(1L, logicalIndex)) == 0)
		{
			// Not found.  Answer nil.
			return NilDescriptor.nil();
		}
		// There's an entry.  Count the 1-bits below it to compute its
		// zero-relative physicalIndex.
		final long masked = vector & bitShift(1L, logicalIndex) - 1;
		final int physicalIndex = bitCount(masked) + 1;
		final AvailObject subBin = object.slot(SUB_BINS_, physicalIndex);
		return subBin.mapBinAtHash(key, keyHash);
	}

	/**
	 * Remove the key from the bin object, if present.  Answer the resulting
	 * bin.  The bin may be modified if it's mutable and canDestroy.
	 */
	@Override @AvailMethod
	A_BasicObject o_MapBinRemoveKeyHashCanDestroy (
		final AvailObject object,
		final A_BasicObject key,
		final int keyHash,
		final boolean canDestroy)
	{
		check(object);
		if (isMutable() & !canDestroy)
		{
			object.makeImmutable();
		}
		// First, grab the appropriate 6 bits from the hash.
		final int logicalIndex = (keyHash >>> shift) & 63;
		final long vector = object.slot(BIT_VECTOR);
		if ((vector & bitShift(1L, logicalIndex)) == 0)
		{
			// Definitely not present.
			return object;
		}
		// There's an entry which might contain the key and value.  Count the
		// 1-bits below it to compute its zero-relative physicalIndex.
		final int oldSize = (int)object.slot(BIN_SIZE);
		final int oldKeysHash = object.slot(KEYS_HASH);
		final long masked = vector & bitShift(1L, logicalIndex) - 1;
		final int physicalIndex = bitCount(masked) + 1;
		final AvailObject oldSubBin = object.slot(SUB_BINS_, physicalIndex);
		final int oldSubBinKeysHash = oldSubBin.mapBinKeysHash();
		final int oldSubBinSize = oldSubBin.binSize();
		final A_BasicObject newSubBin = oldSubBin.mapBinRemoveKeyHashCanDestroy(
			key, keyHash, canDestroy);
		final int delta;
		final int deltaHash;
		final AvailObject objectToModify;
		if (newSubBin.equalsNil())
		{
			// The entire subBin must be removed.
			final int oldSlotCount = bitCount(vector);
			if (oldSlotCount == 1)
			{
				// ...and so must this one.
				return NilDescriptor.nil();
			}
			objectToModify = AvailObject.newIndexedDescriptor(
				bitCount(vector) - 1,
				descriptorFor(MUTABLE, level));
			int destination = 1;
			for (int source = 1; source <= oldSlotCount; source++)
			{
				if (source != physicalIndex)
				{
					objectToModify.setSlot(
						SUB_BINS_, destination, object.slot(SUB_BINS_, source));
					destination++;
				}
			}
			delta = -1;
			deltaHash = -oldSubBinKeysHash;
			objectToModify.setSlot(
				BIT_VECTOR,
				object.slot(BIT_VECTOR) & ~bitShift(1L, logicalIndex));
		}
		else
		{
			// The subBin has to be replaced...
			delta = newSubBin.binSize() - oldSubBinSize;
			deltaHash = newSubBin.mapBinKeysHash() - oldSubBinKeysHash;
			assert canDestroy || !object.descriptor().isMutable();
			if (object.descriptor().isMutable())
			{
				objectToModify = object;
			}
			else
			{
				objectToModify = AvailObjectRepresentation.newLike(
					descriptorFor(MUTABLE, level), object, 0, 0);
			}
			objectToModify.setSlot(SUB_BINS_, physicalIndex, newSubBin);
		}
		assert objectToModify.descriptor.isMutable();
		objectToModify.setSlot(BIN_SIZE, oldSize + delta);
		objectToModify.setSlot(KEYS_HASH, oldKeysHash + deltaHash);
		objectToModify.setSlot(VALUES_HASH_OR_ZERO, 0);
		objectToModify.setSlot(BIN_KEY_UNION_KIND_OR_NULL, NilDescriptor.nil());
		objectToModify.setSlot(
			BIN_VALUE_UNION_KIND_OR_NULL, NilDescriptor.nil());
		check(objectToModify);
		return objectToModify;
	}

	/**
	 * Lazily compute and install the values hash of the specified {@linkplain
	 * HashedMapBinDescriptor object}.
	 *
	 * @param object An object.
	 * @return The hash.
	 */
	private int mapBinValuesHash (final AvailObject object)
	{
		int valuesHash = object.slot(VALUES_HASH_OR_ZERO);
		if (valuesHash == 0)
		{
			final int size = object.variableIntegerSlotsCount();
			for (int i = 1; i <= size; i++)
			{
				final AvailObject subBin = object.slot(SUB_BINS_, i);
				valuesHash += subBin.mapBinValuesHash();
			}
			object.setSlot(VALUES_HASH_OR_ZERO, valuesHash);
		}
		return valuesHash;
	}

	@Override
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

	/**
	 * A MapIterable used for iterating over the key/value pairs of a map whose
	 * root bin happens to be hashed.
	 */
	static class HashedMapBinIterable
	extends MapIterable
	{
		/**
		 * The path through map bins, including the current linear bin.
		 */
		final Deque<AvailObject> binStack = new ArrayDeque<>();

		/**
		 * The current position in each bin on the binStack, including the
		 * linear bin.  It should be the same size as the binStack.  When
		 * they're both empty it indicates {@code !hasNext()}.
		 */
		final Deque<Integer> subscriptStack = new ArrayDeque<>();

		/**
		 * Construct a new {@link MapIterable} over the keys and values
		 * recursively contained in the given root bin / null.
		 *
		 * @param root The root bin over which to iterate.
		 */
		HashedMapBinIterable (final AvailObject root)
		{
			followLeftmost(root);
		}

		/**
		 * Visit this bin or {@link NilDescriptor#nil() nil}. In particular,
		 * travel down its left spine so that it's positioned at the leftmost
		 * descendant.
		 *
		 * @param bin The bin or nil at which to begin enumerating.
		 */
		private void followLeftmost (final AvailObject bin)
		{
			if (bin.equalsNil())
			{
				// Nil may only occur at the top of the bin tree.
				assert binStack.isEmpty();
				assert subscriptStack.isEmpty();
				entry.setKeyAndHashAndValue(null, 0, null);
				return;
			}
			AvailObject currentBin = bin.traversed();
			while (currentBin.isHashedMapBin())
			{
				binStack.addLast(currentBin);
				final int count = currentBin.variableObjectSlotsCount();
				// Move right to left for a simpler limit check.
				subscriptStack.addLast(count);
				currentBin = currentBin.binElementAt(count).traversed();
			}
			binStack.addLast(currentBin);
			// Move leftward in this linear bin.  Note that slots are
			// alternating keys and values.
			final int linearSlotCount = currentBin.variableObjectSlotsCount();
			subscriptStack.addLast(linearSlotCount >> 1);
			assert binStack.size() == subscriptStack.size();
		}

		@Override
		public Entry next ()
		{
			assert !binStack.isEmpty();
			final AvailObject linearBin = binStack.getLast().traversed();
			final Integer linearIndex = subscriptStack.getLast();
			entry.setKeyAndHashAndValue(
				linearBin.binElementAt((linearIndex << 1) - 1),
				linearBin.intSlot(
					LinearMapBinDescriptor.IntegerSlots.KEY_HASHES_AREA_,
					linearIndex),
				linearBin.binElementAt(linearIndex << 1));
			// Got the result.  Now advance the state...
			if (linearIndex > 1)
			{
				// Continue in same leaf bin.
				subscriptStack.removeLast();
				subscriptStack.addLast(linearIndex - 1);
				return entry;
			}
			// Find another leaf bin.
			binStack.removeLast();
			subscriptStack.removeLast();
			assert binStack.size() == subscriptStack.size();
			while (true)
			{
				if (subscriptStack.isEmpty())
				{
					// This was the last entry in the map.
					return entry;
				}
				final int nextSubscript = subscriptStack.removeLast() - 1;
				if (nextSubscript != 0)
				{
					// Continue in current internal (hashed) bin.
					subscriptStack.addLast(nextSubscript);
					assert binStack.size() == subscriptStack.size();
					followLeftmost(
						binStack.getLast().binElementAt(nextSubscript));
					assert binStack.size() == subscriptStack.size();
					return entry;
				}
				binStack.removeLast();
				assert binStack.size() == subscriptStack.size();
			}
		}

		@Override
		public boolean hasNext ()
		{
			return !binStack.isEmpty();
		}
	}

	@Override
	MapIterable o_MapBinIterable (final AvailObject object)
	{
		return new HashedMapBinIterable(object);
	}

	/**
	 * Create a hashed map bin at the given level and with the given bit vector.
	 * The number of 1 bits in the bit vector determine how many sub-bins to
	 * allocate.  Start each sub-bin as {@link NilDescriptor#nil() nil}, with
	 * the expectation that it will be populated during subsequent
	 * initialization of this bin.
	 *
	 * @param myLevel
	 *        The hash tree depth, which controls how much to shift hashes.
	 * @param bitVector
	 *        The {@code long} containing a 1 bit for each sub-bin slot.
	 * @return
	 *        A hash map bin suitable for adding entries to.  The bin is
	 *        denormalized, with all sub-bins set to {@link NilDescriptor#nil()
	 *        nil}.
	 */
	static AvailObject createLevelBitVector (
		final byte myLevel,
		final long bitVector)
	{
		AvailObject result;
		final int newSize = bitCount(bitVector);
		result = descriptorFor(MUTABLE, myLevel).create(newSize);
		result.setSlot(KEYS_HASH, 0);
		result.setSlot(VALUES_HASH_OR_ZERO, 0);
		result.setSlot(BIN_SIZE, 0);
		result.setSlot(BIT_VECTOR, bitVector);
		result.setSlot(BIN_KEY_UNION_KIND_OR_NULL, NilDescriptor.nil());
		result.setSlot(BIN_VALUE_UNION_KIND_OR_NULL, NilDescriptor.nil());
		for (int i = 1; i <= newSize; i++)
		{
			result.setSlot(SUB_BINS_, i, NilDescriptor.nil());
		}
		check(result);
		return result;
	}

	/**
	 * The number of distinct levels that my instances can occupy in a set's
	 * hash tree.
	 */
	private static final byte numberOfLevels = 6;

	/**
	 * Answer the appropriate {@link HashedMapBinDescriptor} to use for the
	 * given mutability and level.
	 *
	 * @param flag The mutability of the object.
	 * @param level The bin tree level that its objects should occupy.
	 * @return A suitable {@code HashedSetBinDescriptor}.
	 */
	private static HashedMapBinDescriptor descriptorFor (
		final Mutability flag,
		final byte level)
	{
		assert 0 <= level && level < numberOfLevels;
		return descriptors[level * 3 + flag.ordinal()];
	}

	/**
	 * The number of bits to shift a hash value to the right before extracting
	 * six bits to determine a bin number.  This is a function of the level,
	 * so that each level is hashed with a different part of the hash value.
	 */
	private final byte shift;

	/**
	 * Construct a new {@link HashedMapBinDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 * @param level
	 *        The depth of the bin in the hash tree.
	 */
	private HashedMapBinDescriptor (
		final Mutability mutability,
		final int level)
	{
		super(
			mutability,
			TypeTag.MAP_HASHED_BIN_TAG,
			ObjectSlots.class,
			IntegerSlots.class, level);
		shift = (byte)(level * 6);
		assert shift < 32;
	}

	/**
	 * {@link HashedMapBinDescriptor}s clustered by mutability and level.
	 */
	static final HashedMapBinDescriptor[] descriptors;

	static
	{
		descriptors = new HashedMapBinDescriptor[numberOfLevels * 3];
		int target = 0;
		for (int level = 0; level < numberOfLevels; level++)
		{
			for (final Mutability mut : Mutability.values())
			{
				descriptors[target++] =
					new HashedMapBinDescriptor(mut, level);
			}
		}
	}

	@Override
	HashedMapBinDescriptor mutable ()
	{
		return descriptorFor(MUTABLE, level);
	}

	@Override
	HashedMapBinDescriptor immutable ()
	{
		return descriptorFor(IMMUTABLE, level);
	}

	@Override
	HashedMapBinDescriptor shared ()
	{
		return descriptorFor(SHARED, level);
	}
}
