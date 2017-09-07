/**
 * HashedSetBinDescriptor.java
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
import com.avail.annotations.HideFieldInDebugger;
import com.avail.descriptor.SetDescriptor.SetIterator;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.NoSuchElementException;

import static com.avail.descriptor.AvailObjectRepresentation.newLike;
import static com.avail.descriptor.HashedSetBinDescriptor.IntegerSlots.*;
import static com.avail.descriptor.HashedSetBinDescriptor.ObjectSlots.BIN_ELEMENT_AT_;
import static com.avail.descriptor.HashedSetBinDescriptor.ObjectSlots.BIN_UNION_TYPE_OR_NULL;
import static com.avail.descriptor.Mutability.*;
import static com.avail.descriptor.NilDescriptor.nil;
import static java.lang.Long.bitCount;

/**
 * This class implements the internal hashed nodes of a Bagwell Ideal Hash Tree.
 * It's similar to {@link HashedMapBinDescriptor}, but has operations suitable
 * for use by a {@linkplain SetDescriptor set} rather than a {@linkplain
 * MapDescriptor map}.  The basic idea is that a single value is treated as a
 * bin of size one, a small number of elements can be placed in a {@linkplain
 * LinearSetBinDescriptor linear bin}, but larger bins use the hash of the
 * element to determine which one of the up to 64 child bins is responsible for
 * that element.  Different levels of the tree use different 6-bit regions of
 * the hash values.  We could always store 64 slots, but Bagwell's mechanism is
 * to store a 64-bit vector where a 1 bit indicates that the corresponding index
 * (0..63) extracted from the hash value has a pointer to the corresponding
 * sub-bin.  If the bit is 0 then that pointer is elided entirely.  By suitable
 * use of bit shifting, masking, and {@linkplain Integer#bitCount counting}, one
 * is able to extract the 6 appropriate dispatch bits and access the Nth sub-bin
 * or determine that it's not already present.  This mechanism produces a hash
 * tree no deeper than about 6 levels, even for a huge number of entries.  It
 * also allows efficient "persistent" manipulation (in the function programming
 * sense).  Given a set one can produce another set that has a small number of
 * edits (added and removed elements) using only a few additional bins – without
 * disrupting the original set.
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
		 * The low 32 bits are used for the {@link #BIN_HASH}, but the upper
		 * 32 can be used by other {@link BitField}s in subclasses.
		 */
		@HideFieldInDebugger
		BIN_HASH_AND_MORE,

		/**
		 * A bit vector indicating which (masked, shifted) hash values are
		 * non-emptySet and represented by a slot.
		 */
		BIT_VECTOR;

		/**
		 * A slot to hold the bin's hash value, or floatZero if it has not been
		 * computed.
		 */
		static final BitField BIN_HASH = bitField(BIN_HASH_AND_MORE, 0, 32);

		/**
		 * The total number of elements within this bin.
		 */
		static final BitField BIN_SIZE = bitField(BIN_HASH_AND_MORE, 32, 32);

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
		 * The union of the types of all elements recursively within this bin.
		 * If this is {@linkplain NilDescriptor#nil() nil}, then it can
		 * be recomputed when needed and cached.
		 */
		BIN_UNION_TYPE_OR_NULL,

		/**
		 * The actual bin elements or sub-bins.  Each slot corresponds to a 1
		 * bit in the bit vector, treating it as an unsigned vector of bits.
		 */
		BIN_ELEMENT_AT_;
	}

	/**
	 * Check that this linear bin has a correct binHash.
	 *
	 * @param object A linear set bin.
	 */
	static void checkBinHash (final AvailObject object)
	{
		assert object.descriptor instanceof HashedSetBinDescriptor;
		final int stored = object.binHash();
		int calculated = 0;
		for (int i = object.variableObjectSlotsCount(); i >= 1; i--)
		{
			final AvailObject subBin = object.slot(BIN_ELEMENT_AT_, i);
			final int subBinHash = subBin.binHash();
			calculated += subBinHash;
		}
		assert calculated == stored : "Failed bin hash cross-check";
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
	AvailObject o_BinElementAt (final AvailObject object, final int subscript)
	{
		return object.slot(BIN_ELEMENT_AT_, subscript);
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
		//  First, grab the appropriate 6 bits from the hash.
		final int objectEntryCount = object.variableObjectSlotsCount();
		final int logicalIndex = (elementObjectHash >>> shift) & 63;
		final long logicalBitValue = 1L << logicalIndex;
		final long vector = object.slot(BIT_VECTOR);
		final long masked = vector & (logicalBitValue - 1);
		final int physicalIndex = bitCount(masked) + 1;
		final AvailObject objectToModify;
		A_Type typeUnion;
		if ((vector & logicalBitValue) != 0)
		{
			A_BasicObject entry = object.slot(BIN_ELEMENT_AT_, physicalIndex);
			final int previousBinSize = entry.binSize();
			final int previousEntryHash = entry.binHash();
			final int previousTotalHash = object.binHash();
			entry = entry.setBinAddingElementHashLevelCanDestroy(
				elementObject,
				elementObjectHash,
				(byte)(level + 1),
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
			final int hashDelta = entry.binHash() - previousEntryHash;
			final int newSize = object.slot(BIN_SIZE) + delta;
			typeUnion = object.slot(BIN_UNION_TYPE_OR_NULL);
			if (!typeUnion.equalsNil())
			{
				typeUnion = typeUnion.typeUnion(entry.binUnionKind());
			}
			if (canDestroy && isMutable())
			{
				// Clobber the object in place.
				objectToModify = object;
			}
			else
			{
				if (!canDestroy && isMutable())
				{
					object.makeSubobjectsImmutable();
				}
				objectToModify = newLike(descriptorFor(
					MUTABLE,
					level), object, 0, 0);
			}
			objectToModify.setSlot(BIN_HASH, previousTotalHash + hashDelta);
			objectToModify.setSlot(BIN_SIZE, newSize);
			objectToModify.setSlot(BIN_UNION_TYPE_OR_NULL, typeUnion);
			objectToModify.setSlot(BIN_ELEMENT_AT_, physicalIndex, entry);
			return objectToModify;
		}
		// Augment object with a new entry.
		if (!canDestroy && isMutable())
		{
			object.makeSubobjectsImmutable();
		}
		typeUnion = object.mutableSlot(BIN_UNION_TYPE_OR_NULL);
		if (!typeUnion.equalsNil())
		{
			typeUnion = typeUnion.typeUnion(elementObject.kind());
		}
		objectToModify = HashedSetBinDescriptor.createUninitializedBin(
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
		// First, grab the appropriate 6 bits from the hash.
		final int logicalIndex = (elementObjectHash >>> shift) & 63;
		final long logicalBitValue = 1L << logicalIndex;
		final long vector = object.slot(BIT_VECTOR);
		if ((vector & logicalBitValue) == 0)
		{
			return false;
		}
		// There's an entry.  Count the 1-bits below it to compute its
		// floatZero-relative physicalIndex.
		final long masked = vector & (logicalBitValue - 1);
		final int physicalIndex = bitCount(masked) + 1;
		final A_BasicObject subBin =
			object.slot(BIN_ELEMENT_AT_, physicalIndex);
		return subBin.binHasElementWithHash(elementObject, elementObjectHash);
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
		final int objectEntryCount = object.variableObjectSlotsCount();
		final int logicalIndex = (elementObjectHash >>> shift) & 63;
		final long logicalBitValue = 1L << logicalIndex;
		final long vector = object.slot(BIT_VECTOR);
		if ((vector & logicalBitValue) == 0)
		{
			if (!canDestroy)
			{
				object.makeImmutable();
			}
			return object;
		}
		final long masked = vector & (logicalBitValue - 1);
		final int physicalIndex = bitCount(masked) + 1;
		final A_BasicObject oldEntry =
			object.slot(BIN_ELEMENT_AT_, physicalIndex);
		final int oldEntryHash = oldEntry.binHash();
		final int oldEntrySize = oldEntry.binSize();
		final int oldTotalHash = object.binHash();
		final int oldTotalSize = object.binSize();
		final AvailObject replacementEntry =
			oldEntry.binRemoveElementHashLevelCanDestroy(
				elementObject,
				elementObjectHash,
				(byte) (level + 1),
				canDestroy);
		final int deltaHash = replacementEntry.binHash() - oldEntryHash;
		final int deltaSize = replacementEntry.binSize() - oldEntrySize;
		final AvailObject result;
		if (replacementEntry.binSize() == 0)
		{
			// Exclude the entire hash entry.
			if (objectEntryCount == 1)
			{
				// That was the last entry that we just removed.
				return LinearSetBinDescriptor.emptyBinForLevel(level);
			}
			result = HashedSetBinDescriptor.createUninitializedBin(
				level,
				objectEntryCount - 1,
				oldTotalSize + deltaSize,
				oldTotalHash + deltaHash,
				vector ^ logicalBitValue,
				nil());
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
			if (canDestroy && isMutable())
			{
				result = object;
			}
			else
			{
				if (!canDestroy)
				{
					object.makeSubobjectsImmutable();
				}
				result = newLike(descriptorFor(
					MUTABLE,
					level), object, 0, 0);
			}
			result.setSlot(BIN_ELEMENT_AT_, physicalIndex, replacementEntry);
			result.setSlot(BIN_HASH, oldTotalHash + deltaHash);
			result.setSlot(BIN_SIZE, oldTotalSize + deltaSize);
			result.setSlot(BIN_UNION_TYPE_OR_NULL, nil());
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
		final int limit = object.variableObjectSlotsCount();
		for (int index = limit; index >= 1; index--)
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
		final Deque<AvailObject> binStack = new ArrayDeque<>(3);

		/**
		 * The position navigated through each bin.  It should always contain
		 * the same number of elements as in binStack.
		 */
		final Deque<Integer> subscriptStack = new ArrayDeque<>(3);

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
			traceDownward(root);
		}

		/**
		 * Visit this bin or element.  In particular, travel down its left spine
		 * so that it's positioned at the leftmost descendant.  Return the
		 * (non-bin) element at the bottom of the spine, which may be the
		 * argument itself.
		 *
		 * @param binOrElement The bin or element to begin enumerating.
		 */
		private void traceDownward (final AvailObject binOrElement)
		{
			AvailObject current = binOrElement;
			while (current.isSetBin())
			{
				binStack.addLast(current);
				final int size = current.variableObjectSlotsCount();
				subscriptStack.addLast(size);
				current = current.binElementAt(size);
			}
			assert binStack.size() == subscriptStack.size();
			currentElement = current;
		}

		@Override
		public AvailObject next ()
		{
			if (currentElement == null)
			{
				throw new NoSuchElementException();
			}
			final AvailObject result = currentElement;
			assert !binStack.isEmpty();
			assert binStack.size() == subscriptStack.size();
			do
			{
				final AvailObject leafBin = binStack.getLast();
				final int nextIndex = subscriptStack.removeLast() - 1;
				if (nextIndex >= 1)
				{
					// Advance along the bin.
					subscriptStack.add(nextIndex);
					assert binStack.size() == subscriptStack.size();
					traceDownward(leafBin.binElementAt(nextIndex));
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
	 * @param unionKindOrNil
	 *            Either nil or the kind that is nearest to the
	 *            union of the elements' types.
	 * @return A new hashed set bin with uninitialized sub-bin slots.
	 */
	public static AvailObject createUninitializedBin (
		final byte level,
		final int localSize,
		final int totalSize,
		final int hash,
		final long bitVector,
		final A_Type unionKindOrNil)
	{
		assert bitCount(bitVector) == localSize;
		final AvailObject instance =
			descriptorFor(MUTABLE, level).create(localSize);
		instance.setSlot(BIN_HASH, hash);
		instance.setSlot(BIN_SIZE, totalSize);
		instance.setSlot(BIT_VECTOR, bitVector);
		instance.setSlot(BIN_UNION_TYPE_OR_NULL, unionKindOrNil);
		return instance;
	}

	/**
	 * Create a new hashed set bin with the given level, local size, total
	 * recursive number of elements, hash, bit vector, and either the bin union
	 * kind or null.  Initialize each sub-bin to the emptySet bin at level + 1.
	 *
	 * @param level The tree level at which this hashed bin occurs.
	 * @param localSize The number of slots to allocate.
	 * @param totalSize The number of elements recursively within me.
	 * @param hash The hash of this bin.
	 * @param bitVector The bit vector indicating which hash values are present.
	 * @param unionKindOrNil
	 *            Either nil or the kind that is nearest to the
	 *            union of the elements' types.
	 * @return A new hashed set bin with emptySet linear sub-bins.
	 */
	public static AvailObject createInitializedBin (
		final byte level,
		final int localSize,
		final int totalSize,
		final int hash,
		final long bitVector,
		final A_Type unionKindOrNil)
	{
		final AvailObject instance = createUninitializedBin(
			level, localSize, totalSize, hash, bitVector, unionKindOrNil);
		final AvailObject subBin =
			LinearSetBinDescriptor.emptyBinForLevel((byte)(level + 1));
		for (int i = 1; i <= localSize; i++)
		{
			instance.setSlot(BIN_ELEMENT_AT_, i, subBin);
		}
		return instance;
	}

	/**
	 * The number of distinct levels that my instances can occupy in a set's
	 * hash tree.
	 */
	private static final byte numberOfLevels = 6;

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
	 * The amount to shift a hash rightward by before masking with 63 to get
	 * the local logical index.  The <em>physical</em> index depends how many
	 * bits are set below that position in the bit vector.
	 */
	final byte shift;

	/**
	 * Construct a new {@link HashedSetBinDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 * @param level
	 *        The depth of the bin in the hash tree.
	 */
	private HashedSetBinDescriptor (
		final Mutability mutability,
		final int level)
	{
		super(
			mutability,
			TypeTag.SET_HASHED_BIN_TAG,
			ObjectSlots.class,
			IntegerSlots.class, level);
		shift = (byte)(level * 6);
		assert level < 32;
	}

	/**
	 * {@link HashedSetBinDescriptor}s clustered by mutability and level.
	 */
	static final HashedSetBinDescriptor[] descriptors;

	static
	{
		descriptors = new HashedSetBinDescriptor[numberOfLevels * 3];
		int target = 0;
		for (int level = 0; level < numberOfLevels; level++)
		{
			for (final Mutability mut : Mutability.values())
			{
				descriptors[target++] =
					new HashedSetBinDescriptor(mut, level);
			}
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
