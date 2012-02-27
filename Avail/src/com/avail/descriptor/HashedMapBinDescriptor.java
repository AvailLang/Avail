/**
 * HashedMapBinDescriptor.java
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
import static com.avail.descriptor.HashedMapBinDescriptor.IntegerSlots.*;
import static com.avail.descriptor.HashedMapBinDescriptor.ObjectSlots.*;
import com.avail.annotations.*;

/**
 * This class implements the internal hashed nodes of a Bagwell Ideal Hash Tree.
 * It's similar to {@link HashedSetBinDescriptor}, but has operations suitable
 * for use by a {@linkplain MapDescriptor map} rather than a {@linkplain
 * SetDescriptor set}.  The basic idea is that small bins are simple {@linkplain
 * LinearMapBinDescriptor linear structures}, but larger bins use the hash of
 * the key to determine which one of the up to 32 child bins is responsible for
 * that key.  Different levels of the tree use different 5-bit regions of the
 * hash values.  We could always store 32 slots, but Bagwell's mechanism is to
 * store a 32-bit vector where a 1 bit indicates that the corresponding index
 * (0..31) extracted from the hash value has a pointer to the corresponding
 * sub-bin.  If the bit is 0 then that pointer is elided entirely.  By suitable
 * use of bit shifting, masking, and {@linkplain Integer#bitCount counting}, one
 * is able to extract the 5 appropriate dispatch bits and access the Nth sub-bin
 * or determine that it's not already present.  This mechanism produces a hash
 * tree no deeper than about 7 levels, even for a huge number of entries.  It
 * also allows efficient "persistent" manipulation (in the function programming
 * sense).  Given a map one can produce another map that has a small number of
 * edits (new keys, removed keys, new values for existing keys) using only a few
 * additional bins – without disrupting the original map.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd Smith &lt;anarakul@gmail.com&gt;
 */
public final class HashedMapBinDescriptor
extends MapBinDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots implements IntegerSlotsEnum
	{
		/**
		 * The sum of the hashes of the elements recursively within this bin.
		 */
		KEYS_HASH,

		/**
		 * The sum of the hashes of the elements recursively within this bin,
		 * or zero if not computed.
		 */
		VALUES_HASH_OR_ZERO,

		/**
		 * The total number of elements recursively contained within this bin.
		 */
		BIN_SIZE,

		/**
		 * A bit vector indicating which (masked, shifted) hash values are
		 * non-empty and represented by a slot.
		 */
		BIT_VECTOR;

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
		 * The union of the types of all keys recursively within this bin.
		 * If this is {@linkplain NullDescriptor#nullObject() top}, then it can
		 * be recomputed when needed and cached.
		 */
		BIN_KEY_UNION_KIND_OR_NULL,

		/**
		 * The union of the types of all keys recursively within this bin.
		 * If this is {@linkplain NullDescriptor#nullObject() top}, then it can
		 * be recomputed when needed and cached.
		 */
		BIN_VALUE_UNION_KIND_OR_NULL,

		/**
		 * The actual bin elements or sub-bins.  Each slot corresponds to a 1
		 * bit in the bit vector, treating it as an unsigned vector of bits.
		 */
		SUB_BINS_
	}

	/**
	 * A static switch for enabling slow, detailed correctness checks.
	 */
	private final static boolean shouldCheck = false;

	/**
	 * Make sure the {@link HashedMapBinDescriptor hashed map bin} is
	 * well-formed at this moment.
	 *
	 * @param object A hashed bin used by maps.
	 */
	static void check (final @NotNull AvailObject object)
	{
		if (shouldCheck)
		{
			assert object.descriptor() instanceof HashedMapBinDescriptor;
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
			final int storedValuesHash = object.slot(VALUES_HASH_OR_ZERO);
			assert storedValuesHash == 0 || storedValuesHash == valueHashSum;
			assert object.slot(BIN_SIZE) == totalCount;
		}
	}

	@Override @AvailMethod
	@NotNull AvailObject o_BinElementAt (
		final @NotNull AvailObject object,
		final int subscript)
	{
		return object.slot(SUB_BINS_, subscript);
	}

	@Override @AvailMethod
	void o_BinElementAtPut (
		final @NotNull AvailObject object,
		final int subscript,
		final @NotNull AvailObject value)
	{
		object.setSlot(SUB_BINS_, subscript, value);
	}

	@Override @AvailMethod
	void o_BinSize (
		final @NotNull AvailObject object,
		final int value)
	{
		object.setSlot(BIN_SIZE, value);
	}

	@Override @AvailMethod
	void o_BitVector (
		final @NotNull AvailObject object,
		final int value)
	{
		object.setSlot(BIT_VECTOR, value);
	}

	@Override @AvailMethod
	int o_BinSize (
		final @NotNull AvailObject object)
	{
		return object.slot(BIN_SIZE);
	}

	/**
	 * Compute this bin's key type and value type.
	 *
	 * @param object
	 *            The {@link HashedMapBinDescriptor bin} to populate with key
	 *            and value type information.
	 */
	private void computeKeyAndValueKinds (
		final @NotNull AvailObject object)
	{
		AvailObject keyType = BottomTypeDescriptor.bottom();
		AvailObject valueType = BottomTypeDescriptor.bottom();
		final int binCount =
			object.objectSlotsCount() - numberOfFixedObjectSlots;
		for (int i = 1; i <= binCount; i++)
		{
			final AvailObject subBin = object.slot(SUB_BINS_, i);
			keyType = keyType.typeUnion(subBin.mapBinKeyUnionKind());
			valueType = valueType.typeUnion(subBin.mapBinValueUnionKind());
		}
		object.setSlot(BIN_KEY_UNION_KIND_OR_NULL, keyType);
		object.setSlot(BIN_VALUE_UNION_KIND_OR_NULL, valueType);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_MapBinKeyUnionKind (
		final @NotNull AvailObject object)
	{
		AvailObject keyType = object.slot(BIN_KEY_UNION_KIND_OR_NULL);
		if (keyType.equalsNull())
		{
			computeKeyAndValueKinds(object);
			keyType = object.slot(BIN_KEY_UNION_KIND_OR_NULL);
		}
		return keyType;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_MapBinValueUnionKind (
		final @NotNull AvailObject object)
	{
		AvailObject valueType = object.slot(BIN_VALUE_UNION_KIND_OR_NULL);
		if (valueType.equalsNull())
		{
			computeKeyAndValueKinds(object);
			valueType = object.slot(BIN_VALUE_UNION_KIND_OR_NULL);
		}
		return valueType;
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final @NotNull AbstractSlotsEnum e)
	{
		return e == VALUES_HASH_OR_ZERO
			|| e == BIN_KEY_UNION_KIND_OR_NULL
			|| e == BIN_VALUE_UNION_KIND_OR_NULL;
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

	/**
	 * Add the given (key,value) pair to this bin, potentially modifying it if
	 * canDestroy is true and it's mutable.  Answer the new bin.  Note that the
	 * client is responsible for marking both the key and value as immutable if
	 * other references exists.
	 */
	@Override @AvailMethod
	@NotNull AvailObject o_MapBinAtHashPutLevelCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject key,
		final int keyHash,
		final @NotNull AvailObject value,
		final byte myLevel,
		final boolean canDestroy)
	{
		assert myLevel == level;
		check(object);
		// First, grab the appropriate 5 bits from the hash.
		final int oldKeysHash = object.mapBinKeysHash();
		final int oldSize = object.binSize();
		final int objectEntryCount = object.variableObjectSlotsCount();
		final int logicalIndex = bitShift(keyHash, -5 * level) & 31;
		final int vector = object.slot(BIT_VECTOR);
		final int masked = vector & bitShift(1, logicalIndex) - 1;
		final int physicalIndex = bitCount(masked) + 1;
		final int delta;
		final int hashDelta;
		AvailObject objectToModify;
		if ((vector & bitShift(1, logicalIndex)) != 0)
		{
			// Sub-bin already exists for those hash bits.  Update the sub-bin.
			final AvailObject oldSubBin = object.slot(
				SUB_BINS_,
				physicalIndex);
			final int oldSubBinSize = oldSubBin.binSize();
			final int oldSubBinKeyHash = oldSubBin.mapBinKeysHash();
			final AvailObject newSubBin =
				oldSubBin.mapBinAtHashPutLevelCanDestroy(
					key,
					keyHash,
					value,
					(byte)(myLevel + 1),
					canDestroy);
			delta = newSubBin.binSize() - oldSubBinSize;
			hashDelta = newSubBin.mapBinKeysHash() - oldSubBinKeyHash;
			if (canDestroy & isMutable)
			{
				objectToModify = object;
			}
			else
			{
				if (!canDestroy & isMutable)
				{
					object.makeSubobjectsImmutable();
				}
				objectToModify = AvailObjectRepresentation.newLike(
					isMutableLevel(true, level),
					object,
					0,
					0);
			}
			objectToModify.setSlot(
				SUB_BINS_,
				physicalIndex,
				newSubBin);
		}
		else
		{
			delta = 1;
			hashDelta = keyHash;
			if (!canDestroy & isMutable)
			{
				object.makeSubobjectsImmutable();
			}
			objectToModify = isMutableLevel(true, level).create(
				objectEntryCount + 1);
			objectToModify.setSlot(
				BIT_VECTOR,
				vector | bitShift(1, logicalIndex));
			for (int i = 1, end = physicalIndex - 1; i <= end; i++)
			{
				objectToModify.binElementAtPut(
					i,
					object.slot(SUB_BINS_, i));
			}
			final AvailObject newSingleBin =
				LinearMapBinDescriptor.createSingle(
					key,
					keyHash,
					value,
					(byte)(myLevel + 1));
			objectToModify.binElementAtPut(physicalIndex, newSingleBin);
			for (int i = physicalIndex; i <= objectEntryCount; i++)
			{
				objectToModify.setSlot(
					SUB_BINS_,
					i + 1,
					object.slot(SUB_BINS_, i));
			}
		}
		objectToModify.setSlot(
			KEYS_HASH,
			oldKeysHash + hashDelta);
		objectToModify.setSlot(
			VALUES_HASH_OR_ZERO,
			0);
		objectToModify.setSlot(
			BIN_SIZE,
			oldSize + delta);
		objectToModify.setSlot(
			BIN_KEY_UNION_KIND_OR_NULL,
			NullDescriptor.nullObject());
		objectToModify.setSlot(
			BIN_VALUE_UNION_KIND_OR_NULL,
			NullDescriptor.nullObject());
		check(objectToModify);
		return objectToModify;
	}

	@Override
	@NotNull AvailObject o_MapBinAtHash (
		final @NotNull AvailObject object,
		final @NotNull AvailObject key,
		final int keyHash)
	{
		// First, grab the appropriate 5 bits from the hash.
		final int logicalIndex = bitShift(keyHash, -5 * level) & 31;
		final int vector = object.slot(BIT_VECTOR);
		if ((vector & bitShift(1, logicalIndex)) == 0)
		{
			// Not found.  Answer the null object.
			return NullDescriptor.nullObject();
		}
		// There's an entry.  Count the 1-bits below it to compute its
		// zero-relative physicalIndex.
		final int masked = vector & bitShift(1, logicalIndex) - 1;
		final int physicalIndex = bitCount(masked) + 1;
		final AvailObject subBin = object.slot(SUB_BINS_, physicalIndex);
		return subBin.mapBinAtHash(key, keyHash);
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
		check(object);
		if (isMutable() & !canDestroy)
		{
			object.makeImmutable();
		}
		// First, grab the appropriate 5 bits from the hash.
		final int logicalIndex = bitShift(keyHash, -5 * level) & 31;
		final int vector = object.slot(BIT_VECTOR);
		if ((vector & bitShift(1, logicalIndex)) == 0)
		{
			// Definitely not present.
			return object;
		}
		// There's an entry which might contain the key and value.  Count the
		// 1-bits below it to compute its zero-relative physicalIndex.
		final int oldSize = object.slot(BIN_SIZE);
		final int oldKeysHash = object.slot(KEYS_HASH);
		final int masked = vector & bitShift(1, logicalIndex) - 1;
		final int physicalIndex = bitCount(masked) + 1;
		final AvailObject oldSubBin = object.slot(
			SUB_BINS_,
			physicalIndex);
		final int oldSubBinKeysHash = oldSubBin.mapBinKeysHash();
		final int oldSubBinSize = oldSubBin.binSize();
		final AvailObject newSubBin = oldSubBin.mapBinRemoveKeyHashCanDestroy(
			key,
			keyHash,
			canDestroy);
		final int delta;
		final int deltaHash;
		final AvailObject objectToModify;
		if (newSubBin.equalsNull())
		{
			// The entire subBin can be removed.
			final int oldSlotCount = bitCount(vector);
			if (oldSlotCount == 1)
			{
				// ...and so can this one.
				return NullDescriptor.nullObject();
			}
			objectToModify = AvailObject.newIndexedDescriptor(
				bitCount(vector) - 1,
				isMutableLevel(true, level));
			int destination = 1;
			for (int source = 1; source <= oldSlotCount; source++)
			{
				if (source != physicalIndex)
				{
					objectToModify.setSlot(
						SUB_BINS_,
						destination,
						object.slot(SUB_BINS_, source));
					destination++;
				}
			}
			delta = -1;
			deltaHash = -oldSubBinKeysHash;
			objectToModify.setSlot(
				BIT_VECTOR,
				object.slot(BIT_VECTOR) & ~bitShift(1, logicalIndex));
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
					isMutableLevel(true, level),
					object,
					0,
					0);
			}
			objectToModify.setSlot(
				SUB_BINS_,
				physicalIndex,
				newSubBin);
		}
		objectToModify.setSlot(BIN_SIZE, oldSize + delta);
		objectToModify.setSlot(KEYS_HASH, oldKeysHash + deltaHash);
		objectToModify.setSlot(VALUES_HASH_OR_ZERO, 0);
		objectToModify.setSlot(
			BIN_KEY_UNION_KIND_OR_NULL,
			NullDescriptor.nullObject());
		objectToModify.setSlot(
			BIN_VALUE_UNION_KIND_OR_NULL,
			NullDescriptor.nullObject());
		check(objectToModify);
		return objectToModify;
	}

	/**
	 * Check if object, a bin, holds a subset of aSet's elements.
	 */
	@Override @AvailMethod
	boolean o_IsBinSubsetOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject potentialSuperset)
	{
		final int size = object.variableObjectSlotsCount();
		for (int index = 1; index <= size; index++)
		{
			final AvailObject subBin = object.slot(SUB_BINS_, index);
			if (!subBin.isBinSubsetOf(potentialSuperset))
			{
				return false;
			}
		}
		return true;
	}

	@Override
	boolean o_IsHashedMapBin (
		final @NotNull AvailObject object)
	{
		return true;
	}


	@Override
	int o_MapBinValuesHash (
		final @NotNull AvailObject object)
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

	/**
	 * @param myLevel
	 * @param bitVector
	 * @return
	 */
	static @NotNull AvailObject createLevelBitVector (
		final byte myLevel,
		final int bitVector)
	{
		AvailObject result;
		final int newSize = bitCount(bitVector);
		result = isMutableLevel(true, myLevel).create(newSize);
		result.setSlot(KEYS_HASH, 0);
		result.setSlot(VALUES_HASH_OR_ZERO, 0);
		result.setSlot(BIN_SIZE, 0);
		result.setSlot(BIT_VECTOR, bitVector);
		result.setSlot(
			BIN_KEY_UNION_KIND_OR_NULL,
			NullDescriptor.nullObject());
		result.setSlot(
			BIN_VALUE_UNION_KIND_OR_NULL,
			NullDescriptor.nullObject());
		for (int i = 1; i <= newSize; i++)
		{
			result.setSlot(
				SUB_BINS_,
				i,
				NullDescriptor.nullObject());
		}
		check(result);
		return result;
	}


	/**
	 * The number of distinct levels that my instances can occupy in a set's
	 * hash tree.
	 */
	private static final byte numberOfLevels = 7;

	/**
	 * Answer the appropriate {@link HashedMapBinDescriptor} to use for the
	 * given mutability and level.
	 *
	 * @param flag Whether the descriptor is to be used for a mutable object.
	 * @param level The bin tree level that its objects should occupy.
	 * @return A suitable {@code HashedSetBinDescriptor}.
	 */
	static HashedMapBinDescriptor isMutableLevel (
		final boolean flag,
		final byte level)
	{
		assert 0 <= level && level < numberOfLevels;
		return descriptors [level * 2 + (flag ? 0 : 1)];
	}

	/**
	 * Construct a new {@link HashedMapBinDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 * @param level The depth of the bin in the hash tree.
	 */
	HashedMapBinDescriptor (
		final boolean isMutable,
		final int level)
	{
		super(isMutable, level);
	}

	/**
	 *
	 */
	static final HashedMapBinDescriptor descriptors[];

	static {
		descriptors = new HashedMapBinDescriptor[numberOfLevels * 2];
		int target = 0;
		for (int level = 0; level < numberOfLevels; level++)
		{
			descriptors[target++] = new HashedMapBinDescriptor(true, level);
			descriptors[target++] = new HashedMapBinDescriptor(false, level);
		}
	}
}
