/**
 * HashedSetBinDescriptor.java
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
 * TODO: [MvG] Document this type!
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd Smith &lt;anarakul@gmail.com&gt;
 */
public final class HashedSetBinDescriptor
extends SetBinDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots implements IntegerSlotsEnum
	{
		/**
		 * The sum of the hashes of the elements recursively within this bin.
		 */
		BIN_HASH,

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
		 * The union of the types of all elements recursively within this bin.
		 * If this is {@linkplain NullDescriptor#nullObject() top}, then it can
		 * be recomputed when needed and cached.
		 */
		BIN_UNION_TYPE_OR_NULL,

		/**
		 * The actual bin elements or sub-bins.  Each slot corresponds to a 1
		 * bit in the bit vector, treating it as an unsigned vector of bits.
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
	void o_BinSize (
		final @NotNull AvailObject object,
		final int value)
	{
		object.setSlot(IntegerSlots.BIN_SIZE, value);
	}

	@Override @AvailMethod
	void o_BinUnionTypeOrNull (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.setSlot(ObjectSlots.BIN_UNION_TYPE_OR_NULL, value);
	}

	@Override @AvailMethod
	void o_BitVector (
		final @NotNull AvailObject object,
		final int value)
	{
		object.setSlot(IntegerSlots.BIT_VECTOR, value);
	}

	@Override @AvailMethod
	int o_BinSize (
		final @NotNull AvailObject object)
	{
		return object.slot(IntegerSlots.BIN_SIZE);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_BinUnionTypeOrNull (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.BIN_UNION_TYPE_OR_NULL);
	}

	@Override @AvailMethod
	int o_BitVector (
		final @NotNull AvailObject object)
	{
		return object.slot(IntegerSlots.BIT_VECTOR);
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final @NotNull AbstractSlotsEnum e)
	{
		return e == ObjectSlots.BIN_UNION_TYPE_OR_NULL;
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
	AvailObject o_BinUnionKind (
			final @NotNull AvailObject object)
	{
		AvailObject union = object.binUnionTypeOrNull();
		if (union.equalsNull())
		{
			union = object.binElementAt(1).binUnionKind();
			final int limit = object.variableObjectSlotsCount();
			for (int i = 2; i <= limit; i++)
			{
				union = union.typeUnion(object.binElementAt(i).binUnionKind());
			}
			object.binUnionTypeOrNull(union);
		}
		return union;
	}


	/**
	 * Add the given element to this bin, potentially modifying it if canDestroy
	 * is true and it's mutable.  Answer the new bin.  Note that the client is
	 * responsible for marking elementObject as immutable if another reference
	 * exists.
	 */
	@Override @AvailMethod
	@NotNull AvailObject o_BinAddingElementHashLevelCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject elementObject,
		final int elementObjectHash,
		final byte myLevel,
		final boolean canDestroy)
	{
		assert myLevel == level;
		//  First, grab the appropriate 5 bits from the hash.
		final int objectEntryCount = object.variableObjectSlotsCount();
		final int logicalIndex = bitShift(elementObjectHash, -5 * level) & 31;
		final int vector = object.bitVector();
		final int masked = vector & bitShift(1,logicalIndex) - 1;
		final int physicalIndex = bitCount(masked) + 1;
		AvailObject objectToModify;
		AvailObject typeUnion;
		if ((vector & bitShift(1,logicalIndex)) != 0)
		{
			AvailObject entry = object.binElementAt(physicalIndex);
			final int previousBinSize = entry.binSize();
			final int previousHash = entry.binHash();
			entry = entry.binAddingElementHashLevelCanDestroy(
				elementObject,
				elementObjectHash,
				((byte)(level + 1)),
				canDestroy);
			final int delta = entry.binSize() - previousBinSize;
			if (delta == 0)
			{
				return object;
			}
			//  The element had to be added.
			final int hashDelta = entry.binHash() - previousHash;
			final int newSize = object.binSize() + delta;
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
				objectToModify =
					HashedSetBinDescriptor.isMutableLevel(true, level)
						.create(objectEntryCount);
				objectToModify.bitVector(vector);
				final int limit = object.variableObjectSlotsCount();
				for (int i = 1; i <= limit; i++)
				{
					objectToModify.binElementAtPut(i, object.binElementAt(i));
				}
			}
			objectToModify.binHash(object.binHash() + hashDelta);
			objectToModify.binSize(newSize);
			objectToModify.binUnionTypeOrNull(NullDescriptor.nullObject());
			objectToModify.binElementAtPut(physicalIndex, entry);
			return objectToModify;
		}
		if (!canDestroy & isMutable)
		{
			object.makeSubobjectsImmutable();
		}
		typeUnion = object.binUnionTypeOrNull();
		if (!typeUnion.equalsNull())
		{
			typeUnion = typeUnion.typeUnion(elementObject.kind());
		}
		objectToModify = HashedSetBinDescriptor.isMutableLevel(true, level)
			.create(objectEntryCount + 1);
		objectToModify.binHash(object.binHash() + elementObjectHash);
		objectToModify.binSize(object.binSize() + 1);
		objectToModify.binUnionTypeOrNull(typeUnion);
		objectToModify.bitVector(vector | bitShift(1,logicalIndex));
		for (int i = 1, end = physicalIndex - 1; i <= end; i++)
		{
			objectToModify.binElementAtPut(i, object.binElementAt(i));
		}
		objectToModify.binElementAtPut(physicalIndex, elementObject);
		for (int i = physicalIndex; i <= objectEntryCount; i++)
		{
			objectToModify.binElementAtPut(i + 1, object.binElementAt(i));
		}
		return objectToModify;
	}

	@Override @AvailMethod
	boolean o_BinHasElementHash (
		final @NotNull AvailObject object,
		final @NotNull AvailObject elementObject,
		final int elementObjectHash)
	{
		// First, grab the appropriate 5 bits from the hash.

		final int logicalIndex = bitShift(elementObjectHash, -5 * level) & 31;
		final int vector = object.bitVector();
		if ((vector & bitShift(1,logicalIndex)) == 0)
		{
			return false;
		}
		// There's an entry.  Count the 1-bits below it to compute its
		// zero-relative physicalIndex.
		final int masked = vector & bitShift(1,logicalIndex) - 1;
		final int physicalIndex = bitCount(masked) + 1;
		return object.binElementAt(physicalIndex).binHasElementHash(
			elementObject,
			elementObjectHash);
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

		final int objectEntryCount = object.variableObjectSlotsCount();
		final int logicalIndex = bitShift(elementObjectHash, -5 * level) & 31;
		final int vector = object.bitVector();
		if ((vector & bitShift(1,logicalIndex)) == 0)
		{
			if (!canDestroy)
			{
				object.makeImmutable();
			}
			return object;
		}
		final int masked = vector & bitShift(1,logicalIndex) - 1;
		final int physicalIndex = bitCount(masked) + 1;
		final AvailObject oldEntry = object.binElementAt(physicalIndex);
		final int oldHash = oldEntry.binHash();
		final int oldSize = oldEntry.binSize();
		final AvailObject replacementEntry =
			oldEntry.binRemoveElementHashCanDestroy(
				elementObject,
				elementObjectHash,
				canDestroy);
		final int deltaHash = replacementEntry.binHash() - oldHash;
		final int deltaSize = replacementEntry.binSize() - oldSize;
		AvailObject result;
		if (replacementEntry.equalsNull())
		{
			if (objectEntryCount == 1)
			{
				return NullDescriptor.nullObject();
			}
			result = HashedSetBinDescriptor.isMutableLevel(true, level).create(
				objectEntryCount - 1);
			result.binHash(object.binHash() + deltaHash);
			result.binSize(object.binSize() + deltaSize);
			result.binUnionTypeOrNull(NullDescriptor.nullObject());
			result.bitVector(vector ^ bitShift(1,logicalIndex));
			for (int index = objectEntryCount - 1; index >= 1; index--)
			{
				result.binElementAtPut(index, NullDescriptor.nullObject());
			}
			int writeIndex = 1;
			for (int readIndex = 1; readIndex <= objectEntryCount; readIndex++)
			{
				if (readIndex != physicalIndex)
				{
					final AvailObject eachBin = object.binElementAt(
						readIndex);
					if (!canDestroy)
					{
						eachBin.makeImmutable();
					}
					result.binElementAtPut(writeIndex, eachBin);
					writeIndex++;
				}
			}
			//  i.e., number of entries in result + 1...
			assert writeIndex == objectEntryCount;
		}
		else
		{
			result = HashedSetBinDescriptor.isMutableLevel(true, level).create(
				objectEntryCount);
			result.binHash(object.binHash() + deltaHash);
			result.binSize(object.binSize() + deltaSize);
			result.binUnionTypeOrNull(NullDescriptor.nullObject());
			result.bitVector(vector);
			for (int index = 1; index <= objectEntryCount; index++)
			{
				final AvailObject eachBin = object.binElementAt(index);
				if (index == physicalIndex)
				{
					result.binElementAtPut(index, replacementEntry);
				}
				else
				{
					if (!canDestroy)
					{
						eachBin.makeImmutable();
					}
					result.binElementAtPut(index, eachBin);
				}
			}
			for (int index = 1; index <= objectEntryCount; index++)
			{
				final AvailObject eachBin = object.binElementAt(index);
				if (index == physicalIndex)
				{
					result.binElementAtPut(index, replacementEntry);
				}
				else
				{
					if (!canDestroy)
					{
						eachBin.makeImmutable();
					}
					result.binElementAtPut(index, eachBin);
				}
			}
		}
		return result;
	}

	/**
	 * Check if object, a bin, holds a subset of aSet's elements.
	 */
	@Override @AvailMethod
	boolean o_IsBinSubsetOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject potentialSuperset)
	{
		for (
				int index = object.objectSlotsCount()
					- numberOfFixedObjectSlots;
				index >= 1;
				index--)
		{
			if (!object.binElementAt(index).isBinSubsetOf(potentialSuperset))
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
			int index = object.variableObjectSlotsCount();
			index >= 1;
			index--)
		{
			writeIndex = object.binElementAt(index).populateTupleStartingAt(
				mutableTuple,
				writeIndex);
		}
		return writeIndex;
	}

	/**
	 * The number of distinct levels that my instances can occupy in a set's
	 * hash tree.
	 */
	private static final byte numberOfLevels = 7;

	/**
	 * Answer the appropriate {@link HashedSetBinDescriptor} to use for the
	 * given mutability and level.
	 *
	 * @param flag Whether the descriptor is to be used for a mutable object.
	 * @param level The bin tree level that its objects should occupy.
	 * @return A suitable {@code HashedSetBinDescriptor}.
	 */
	static HashedSetBinDescriptor isMutableLevel (
		final boolean flag,
		final byte level)
	{
		assert 0 <= level && level < numberOfLevels;
		return descriptors [level * 2 + (flag ? 0 : 1)];
	}

	/**
	 * Construct a new {@link HashedSetBinDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 * @param level The depth of the bin in the hash tree.
	 */
	HashedSetBinDescriptor (
		final boolean isMutable,
		final int level)
	{
		super(isMutable, level);
	}

	/**
	 *
	 */
	static final HashedSetBinDescriptor descriptors[];

	static {
		descriptors = new HashedSetBinDescriptor[numberOfLevels * 2];
		int target = 0;
		for (int level = 0; level < numberOfLevels; level++)
		{
			descriptors[target++] = new HashedSetBinDescriptor(true, level);
			descriptors[target++] = new HashedSetBinDescriptor(false, level);
		}
	};

}
