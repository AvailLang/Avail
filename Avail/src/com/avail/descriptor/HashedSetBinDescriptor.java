/**
 * HashedSetBinDescriptor.java
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

import static java.lang.Integer.bitCount;
import static com.avail.descriptor.HashedSetBinDescriptor.IntegerSlots.*;
import static com.avail.descriptor.HashedSetBinDescriptor.ObjectSlots.*;
import static com.avail.descriptor.Mutability.*;
import java.util.*;
import com.avail.annotations.*;
import com.avail.descriptor.SetDescriptor.SetIterator;

/**
 * TODO: [MvG] Document this type!
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd Smith &lt;todd@availlang.org&gt;
 */
public final class HashedSetBinDescriptor
extends SetBinDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
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
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The union of the types of all elements recursively within this bin.
		 * If this is {@linkplain NilDescriptor#nil() top}, then it can
		 * be recomputed when needed and cached.
		 */
		BIN_UNION_TYPE_OR_NULL,

		/**
		 * The actual bin elements or sub-bins.  Each slot corresponds to a 1
		 * bit in the bit vector, treating it as an unsigned vector of bits.
		 */
		BIN_ELEMENT_AT_
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == BIN_UNION_TYPE_OR_NULL;
	}

	@Override @AvailMethod
	int o_BinSize (final AvailObject object)
	{
		return object.slot(BIN_SIZE);
	}

	@Override @AvailMethod
	void o_BinSize (final AvailObject object, final int value)
	{
		object.setSlot(BIN_SIZE, value);
	}

	@Override @AvailMethod
	void o_BitVector (final AvailObject object, final int value)
	{
		object.setSlot(BIT_VECTOR, value);
	}

	@Override @AvailMethod
	AvailObject o_BinElementAt (final AvailObject object, final int subscript)
	{
		return object.slot(BIN_ELEMENT_AT_, subscript);
	}

	@Override @AvailMethod
	void o_BinElementAtPut (
		final AvailObject object,
		final int subscript,
		final A_BasicObject value)
	{
		object.setSlot(BIN_ELEMENT_AT_, subscript, value);
	}

	/**
	 * Lazily compute and install the union kind of the specified {@linkplain
	 * HashedSetBinDescriptor object}.
	 *
	 * @param object An object.
	 * @return A type.
	 */
	private A_Type binUnionKind (final AvailObject object)
	{
		A_Type union = object.slot(BIN_UNION_TYPE_OR_NULL);
		if (union.equalsNil())
		{
			union = object.slot(BIN_ELEMENT_AT_, 1).binUnionKind();
			final int limit = object.variableObjectSlotsCount();
			for (int i = 2; i <= limit; i++)
			{
				union = union.typeUnion(
					object.slot(BIN_ELEMENT_AT_, i).binUnionKind());
			}
			if (isShared())
			{
				union = union.traversed().makeShared();
			}
			object.setSlot(BIN_UNION_TYPE_OR_NULL, union);
		}
		return union;
	}

	@Override @AvailMethod
	A_Type o_BinUnionKind (final AvailObject object)
	{
		if (isShared())
		{
			synchronized (object)
			{
				return binUnionKind(object);
			}
		}
		return binUnionKind(object);
	}

	@Override @AvailMethod
	boolean o_BinElementsAreAllInstancesOfKind (
		final AvailObject object,
		final A_Type kind)
	{
		return object.binUnionKind().isSubtypeOf(kind);
	}

	/**
	 * Add the given element to this bin, potentially modifying it if canDestroy
	 * is true and it's mutable.  Answer the new bin.  Note that the client is
	 * responsible for marking elementObject as immutable if another reference
	 * exists.
	 */
	@Override @AvailMethod
	A_BasicObject o_SetBinAddingElementHashLevelCanDestroy (
		final AvailObject object,
		final A_BasicObject elementObject,
		final int elementObjectHash,
		final byte myLevel,
		final boolean canDestroy)
	{
		assert myLevel == level;
		//  First, grab the appropriate 5 bits from the hash.
		final int objectEntryCount = object.variableObjectSlotsCount();
		final int logicalIndex = bitShift(elementObjectHash, -5 * level) & 31;
		final int logicalBitValue = bitShift(1, logicalIndex);
		final int vector = object.slot(BIT_VECTOR);
		final int masked = vector & logicalBitValue - 1;
		final int physicalIndex = bitCount(masked) + 1;
		final AvailObject objectToModify;
		A_Type typeUnion;
		if ((vector & logicalBitValue) != 0)
		{
			A_BasicObject entry = object.slot(BIN_ELEMENT_AT_, physicalIndex);
			final int previousBinSize = entry.binSize();
			final int previousHash = entry.binHash();
			entry = entry.setBinAddingElementHashLevelCanDestroy(
				elementObject,
				elementObjectHash,
				((byte)(level + 1)),
				canDestroy);
			final int delta = entry.binSize() - previousBinSize;
			if (delta == 0)
			{
				if (!canDestroy)
				{
					object.makeImmutable();
				}
				return object;
			}
			//  The element had to be added.
			final int hashDelta = entry.binHash() - previousHash;
			final int newSize = object.slot(BIN_SIZE) + delta;
			if (canDestroy & isMutable())
			{
				// Clobber the object in place.
				objectToModify = object;
				objectToModify.setSlot(BIN_HASH, object.binHash() + hashDelta);
				objectToModify.setSlot(BIN_SIZE, newSize);
				objectToModify.setSlot(
					BIN_UNION_TYPE_OR_NULL,
					NilDescriptor.nil());
			}
			else
			{
				if (!canDestroy & isMutable())
				{
					object.makeSubobjectsImmutable();
				}
				objectToModify = HashedSetBinDescriptor.createBin(
					level,
					objectEntryCount,
					newSize,
					object.binHash() + hashDelta,
					vector,
					NilDescriptor.nil());
				final int limit = object.variableObjectSlotsCount();
				for (int i = 1; i <= limit; i++)
				{
					objectToModify.setSlot(
						BIN_ELEMENT_AT_,
						i,
						object.slot(BIN_ELEMENT_AT_, i));
				}
			}
			objectToModify.setSlot(BIN_ELEMENT_AT_, physicalIndex, entry);
			return objectToModify;
		}
		// Augment object with a new entry.
		if (!canDestroy & isMutable())
		{
			object.makeSubobjectsImmutable();
		}
		typeUnion = object.mutableSlot(BIN_UNION_TYPE_OR_NULL);
		if (!typeUnion.equalsNil())
		{
			typeUnion = typeUnion.typeUnion(elementObject.kind());
		}
		objectToModify = HashedSetBinDescriptor.createBin(
			level,
			objectEntryCount + 1,
			object.binSize() + 1,
			object.binHash() + elementObjectHash,
			vector | logicalBitValue,
			typeUnion);
		for (int i = 1, end = physicalIndex - 1; i <= end; i++)
		{
			objectToModify.setSlot(
				BIN_ELEMENT_AT_,
				i,
				object.slot(BIN_ELEMENT_AT_, i));
		}
		objectToModify.setSlot(BIN_ELEMENT_AT_, physicalIndex, elementObject);
		for (int i = physicalIndex; i <= objectEntryCount; i++)
		{
			objectToModify.setSlot(
				BIN_ELEMENT_AT_,
				i + 1,
				object.slot(BIN_ELEMENT_AT_, i));
		}
		return objectToModify;
	}

	@Override @AvailMethod
	boolean o_BinHasElementWithHash (
		final AvailObject object,
		final A_BasicObject elementObject,
		final int elementObjectHash)
	{
		// First, grab the appropriate 5 bits from the hash.

		final int logicalIndex = bitShift(elementObjectHash, -5 * level) & 31;
		final int logicalBitValue = bitShift(1, logicalIndex);
		final int vector = object.slot(BIT_VECTOR);
		if ((vector & logicalBitValue) == 0)
		{
			return false;
		}
		// There's an entry.  Count the 1-bits below it to compute its
		// zero-relative physicalIndex.
		final int masked = vector & logicalBitValue - 1;
		final int physicalIndex = bitCount(masked) + 1;
		final A_BasicObject subBin = object.slot(BIN_ELEMENT_AT_, physicalIndex);
		return subBin.binHasElementWithHash(
			elementObject,
			elementObjectHash);
	}

	/**
	 * Remove elementObject from the bin object, if present. Answer the
	 * resulting bin. The bin may be modified if it's mutable and canDestroy.
	 */
	@Override @AvailMethod
	AvailObject o_BinRemoveElementHashCanDestroy (
		final AvailObject object,
		final A_BasicObject elementObject,
		final int elementObjectHash,
		final boolean canDestroy)
	{

		final int objectEntryCount = object.variableObjectSlotsCount();
		final int logicalIndex = bitShift(elementObjectHash, -5 * level) & 31;
		final int logicalBitValue = bitShift(1, logicalIndex);
		final int vector = object.slot(BIT_VECTOR);
		if ((vector & logicalBitValue) == 0)
		{
			if (!canDestroy)
			{
				object.makeImmutable();
			}
			return object;
		}
		final int masked = vector & logicalBitValue - 1;
		final int physicalIndex = bitCount(masked) + 1;
		final A_BasicObject oldEntry =
			object.slot(BIN_ELEMENT_AT_, physicalIndex);
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
		if (replacementEntry.equalsNil())
		{
			// Exclude the entire hash entry.
			if (objectEntryCount == 1)
			{
				return NilDescriptor.nil();
			}
			result = HashedSetBinDescriptor.createBin(
				level,
				objectEntryCount - 1,
				object.binSize() + deltaSize,
				object.binHash() + deltaHash,
				vector ^ logicalBitValue,
				NilDescriptor.nil());
			int writeIndex = 1;
			for (int readIndex = 1; readIndex <= objectEntryCount; readIndex++)
			{
				if (readIndex != physicalIndex)
				{
					final AvailObject eachBin =
						object.slot(BIN_ELEMENT_AT_, readIndex);
					if (!canDestroy)
					{
						eachBin.makeImmutable();
					}
					result.setSlot(BIN_ELEMENT_AT_, writeIndex, eachBin);
					writeIndex++;
				}
			}
			//  i.e., number of entries in result + 1...
			assert writeIndex == objectEntryCount;
		}
		else
		{
			// Replace the hash entry.
			result = HashedSetBinDescriptor.createBin(
				level,
				objectEntryCount,
				object.binSize() + deltaSize,
				object.binHash() + deltaHash,
				vector,
				NilDescriptor.nil());
			for (int index = 1; index <= objectEntryCount; index++)
			{
				if (index == physicalIndex)
				{
					result.setSlot(BIN_ELEMENT_AT_, index, replacementEntry);
				}
				else
				{
					final AvailObject eachBin =
						object.slot(BIN_ELEMENT_AT_, index);
					if (!canDestroy)
					{
						eachBin.makeImmutable();
					}
					result.setSlot(BIN_ELEMENT_AT_, index, eachBin);
				}
			}
			for (int index = 1; index <= objectEntryCount; index++)
			{
				if (index == physicalIndex)
				{
					result.setSlot(BIN_ELEMENT_AT_, index, replacementEntry);
				}
				else
				{
					final AvailObject eachBin =
						object.slot(BIN_ELEMENT_AT_, index);
					if (!canDestroy)
					{
						eachBin.makeImmutable();
					}
					result.setSlot(BIN_ELEMENT_AT_, index, eachBin);
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
		final AvailObject object,
		final A_Set potentialSuperset)
	{
		// TODO: [MvG] This could be much quicker in the case that some of the
		// bins are shared between the sets.  Even if not, we should be able to
		// avoid traversing some of the hashed layers for each element.
		final int limit = object.objectSlotsCount() - numberOfFixedObjectSlots;
		for (int index = 1; index <= limit; index++)
		{
			final A_BasicObject subBin = object.slot(BIN_ELEMENT_AT_, index);
			if (!subBin.isBinSubsetOf(potentialSuperset))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * A {@link SetIterator} for iterating over a set whose root bin happens to
	 * be hashed.
	 */
	static class HashedSetBinIterator
	extends SetIterator
	{
		/**
		 * The path through set bins, excluding the leaf (non-bin) element.
		 */
		final Deque<AvailObject> binStack = new ArrayDeque<AvailObject>();

		/**
		 * The position navigated through each bin.  It should always contain
		 * the same number of elements as in binStack.
		 */
		final Deque<Integer> subscriptStack = new ArrayDeque<Integer>();

		/**
		 * The next value that will returned by {@link #next()}, or null if the
		 * iterator is exhausted.
		 */
		private @Nullable AvailObject currentElement;

		/**
		 * Construct a new {@link SetIterator} over the elements recursively
		 * contained in the given bin / null / single object.
		 *
		 * @param root The root bin over which to iterate.
		 */
		HashedSetBinIterator (final AvailObject root)
		{
			followLeftmost(root);
		}

		/**
		 * Visit this bin or element.  In particular, travel down its left spine
		 * so that it's positioned at the leftmost descendant.  Return the
		 * (non-bin) element at the bottom of the spine, which may be the
		 * argument itself.
		 *
		 * @param binOrElement The bin or element to begin enumerating.
		 */
		private void followLeftmost (final AvailObject binOrElement)
		{
			AvailObject current = binOrElement;
			while (current.isSetBin())
			{
				binStack.addLast(current);
				subscriptStack.addLast(1);
				current = current.binElementAt(1);
			}
			assert binStack.size() == subscriptStack.size();
			currentElement = current;
		}

		@Override
		public AvailObject next ()
		{
			assert currentElement != null;
			final AvailObject result = currentElement;
			assert !binStack.isEmpty();
			assert binStack.size() == subscriptStack.size();
			do
			{
				final AvailObject leafBin = binStack.getLast();
				final int nextIndex = subscriptStack.removeLast() + 1;
				if (nextIndex <= leafBin.variableObjectSlotsCount())
				{
					// Advance along the bin.
					subscriptStack.add(nextIndex);
					assert binStack.size() == subscriptStack.size();
					followLeftmost(leafBin.binElementAt(nextIndex));
					return result;
				}
				// Exhausted the bin.
				binStack.removeLast();
				assert binStack.size() == subscriptStack.size();
			}
			while (!binStack.isEmpty());
			currentElement = null;
			return result;
		}

		@Override
		public boolean hasNext ()
		{
			return currentElement != null;
		}
	}

	@Override
	SetIterator o_SetBinIterator (final AvailObject object)
	{
		return new HashedSetBinIterator(object);
	}

	/**
	 * Create a new hashed set bin with the given level, local size, total
	 * recursive number of elements, hash, bit vector, and either the bin union
	 * kind or null.  The client is responsible for setting the bin elements and
	 * making things immutable if necessary.
	 *
	 * @param level The tree level at which this hashed bin occurs.
	 * @param localSize The number of slots to allocate.
	 * @param totalSize The number of elements recursively within me.
	 * @param hash The hash of this bin.
	 * @param bitVector The bit vector indicating which hash values are present.
	 * @param unionKindOrNull
	 *            Either nil or the kind that is nearest to the
	 *            union of the elements' types.
	 * @return A new hashed set bin with uninitialized sub-bin slots.
	 */
	public static AvailObject createBin (
		final byte level,
		final int localSize,
		final int totalSize,
		final int hash,
		final int bitVector,
		final A_Type unionKindOrNull)
	{
		assert bitCount(bitVector) == localSize;
		final AvailObject instance =
			descriptorFor(MUTABLE, level).create(localSize);
		instance.setSlot(BIN_HASH, hash);
		instance.setSlot(BIN_SIZE, totalSize);
		instance.setSlot(BIT_VECTOR, bitVector);
		instance.setSlot(BIN_UNION_TYPE_OR_NULL, unionKindOrNull);
		return instance;
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
	static HashedSetBinDescriptor descriptorFor (
		final Mutability flag,
		final byte level)
	{
		assert 0 <= level && level < numberOfLevels;
		return descriptors[level * 3 + flag.ordinal()];
	}

	/**
	 * Construct a new {@link HashedSetBinDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 * @param level
	 *        The depth of the bin in the hash tree.
	 */
	HashedSetBinDescriptor (
		final Mutability mutability,
		final int level)
	{
		super(mutability, level);
	}

	/**
	 * {@link HashedSetBinDescriptor}s clustered by mutability and level.
	 */
	static final HashedSetBinDescriptor descriptors[];

	static
	{
		descriptors = new HashedSetBinDescriptor[numberOfLevels * 3];
		int target = 0;
		for (int level = 0; level < numberOfLevels; level++)
		{
			descriptors[target++] =
				new HashedSetBinDescriptor(MUTABLE, level);
			descriptors[target++] =
				new HashedSetBinDescriptor(IMMUTABLE, level);
			descriptors[target++] =
				new HashedSetBinDescriptor(SHARED, level);
		}
	}

	@Override
	HashedSetBinDescriptor mutable ()
	{
		return descriptorFor(MUTABLE, level);
	}

	@Override
	HashedSetBinDescriptor immutable ()
	{
		return descriptorFor(IMMUTABLE, level);
	}

	@Override
	HashedSetBinDescriptor shared ()
	{
		return descriptorFor(SHARED, level);
	}
}
