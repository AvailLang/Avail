/*
 * HashedSetBinDescriptor.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
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
package com.avail.descriptor.sets

import com.avail.descriptor.maps.HashedMapBinDescriptor
import com.avail.descriptor.maps.MapDescriptor
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.A_BasicObject.Companion.synchronizeIf
import com.avail.descriptor.representation.AbstractSlotsEnum
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.AvailObjectRepresentation.Companion.newLike
import com.avail.descriptor.representation.BitField
import com.avail.descriptor.representation.IntegerSlotsEnum
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.sets.A_SetBin.Companion.binElementAt
import com.avail.descriptor.sets.A_SetBin.Companion.binHasElementWithHash
import com.avail.descriptor.sets.A_SetBin.Companion.binRemoveElementHashLevelCanDestroy
import com.avail.descriptor.sets.A_SetBin.Companion.binUnionKind
import com.avail.descriptor.sets.A_SetBin.Companion.isBinSubsetOf
import com.avail.descriptor.sets.A_SetBin.Companion.isSetBin
import com.avail.descriptor.sets.A_SetBin.Companion.setBinAddingElementHashLevelCanDestroy
import com.avail.descriptor.sets.A_SetBin.Companion.setBinHash
import com.avail.descriptor.sets.A_SetBin.Companion.setBinSize
import com.avail.descriptor.sets.HashedSetBinDescriptor.IntegerSlots.BIT_VECTOR
import com.avail.descriptor.sets.HashedSetBinDescriptor.IntegerSlots.Companion.BIN_HASH
import com.avail.descriptor.sets.HashedSetBinDescriptor.IntegerSlots.Companion.BIN_SIZE
import com.avail.descriptor.sets.HashedSetBinDescriptor.ObjectSlots.BIN_ELEMENT_AT_
import com.avail.descriptor.sets.HashedSetBinDescriptor.ObjectSlots.BIN_UNION_KIND_OR_NIL
import com.avail.descriptor.sets.LinearSetBinDescriptor.Companion.emptyLinearSetBin
import com.avail.descriptor.sets.SetDescriptor.SetIterator
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.isSubtypeOf
import com.avail.descriptor.types.A_Type.Companion.typeUnion
import com.avail.descriptor.types.TypeTag
import java.util.ArrayDeque
import java.util.NoSuchElementException

/**
 * This class implements the internal hashed nodes of a Bagwell Ideal Hash Tree.
 * It's similar to [HashedMapBinDescriptor], but has operations suitable for use
 * by a [set][SetDescriptor] rather than a [map][MapDescriptor].
 *
 * The basic idea is that a single value is treated as a bin of size one, a
 * small number of elements can be placed in a [linear][LinearSetBinDescriptor]
 * bin, but larger bins use the hash of the element to determine which one of
 * the up to 64 child bins is responsible for that element. Different levels of
 * the tree use different 6-bit regions of the hash values. We could always
 * store 64 slots every time, but Bagwell's mechanism is to store a 64-bit
 * vector where a 1 bit indicates that the corresponding index (0..63) extracted
 * from the hash value has a pointer to the corresponding sub-bin.  If the bit
 * is 0 then that pointer is elided entirely.  By suitable use of bit shifting,
 * masking, and [counting][Integer.bitCount], one is able to extract the 6
 * appropriate dispatch bits and access the Nth sub-bin or determine that it's
 * not already present.  This mechanism produces a hash tree no deeper than
 * about 6 levels, even for a huge number of entries.
 *
 * It also allows efficient "persistent" manipulation (in the function
 * programming sense).  Given a set one can produce another set that has a small
 * number of edits (added and removed elements) using only a few additional bins
 * – without disrupting the original set.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param level
 *   The depth of the bin in the hash tree.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class HashedSetBinDescriptor private constructor(
	mutability: Mutability,
	level: Int
) : SetBinDescriptor(
	mutability,
	TypeTag.SET_HASHED_BIN_TAG,
	ObjectSlots::class.java,
	IntegerSlots::class.java,
	level
) {
	/**
	 * The amount to shift a hash rightward by before masking with 63 to get the
	 * local logical index.  The *physical* index depends how many bits are set
	 * below that position in the bit vector.
	 */
	val shift: Int = (level * 6).also {
		assert(it < 32)
	}

	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum {
		/**
		 * The low 32 bits are used for the [BIN_HASH], and in this subclass,
		 * the high 32 bits hold the bin size.
		 */
		BIN_HASH_AND_MORE,

		/**
		 * A bit vector indicating which (masked, shifted) hash values are
		 * non-empty and represented by a slot.
		 */
		BIT_VECTOR;

		companion object {
			/**
			 * A slot to hold the bin's hash value, or zero if it has not been
			 * computed.
			 */
			@JvmField
			val BIN_HASH = BitField(BIN_HASH_AND_MORE, 0, 32)

			/**
			 * The total number of elements within this bin.
			 */
			@JvmField
			val BIN_SIZE = BitField(BIN_HASH_AND_MORE, 32, 32)

			init {
				assert(SetBinDescriptor.IntegerSlots.BIN_HASH_AND_MORE.ordinal
					== BIN_HASH_AND_MORE.ordinal)
				assert(SetBinDescriptor.IntegerSlots.BIN_HASH.isSamePlaceAs(
					BIN_HASH))
			}
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * The union of the types of all elements recursively within this bin.
		 * If this is [nil], then it can be recomputed when needed and cached.
		 */
		BIN_UNION_KIND_OR_NIL,

		/**
		 * The actual bin elements or sub-bins.  Each slot corresponds to a 1
		 * bit in the bit vector, treating it as an unsigned vector of bits.
		 */
		BIN_ELEMENT_AT_
	}

	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum
	): Boolean = e === BIN_UNION_KIND_OR_NIL

	override fun o_SetBinSize(self: AvailObject): Int = self.slot(BIN_SIZE)

	override fun o_BinElementAt(
		self: AvailObject,
		index: Int
	): AvailObject = self.slot(BIN_ELEMENT_AT_, index)

	/**
	 * Lazily compute and install the union kind of this bin.
	 *
	 * @param self
	 *   An object.
	 * @return
	 *   A type.
	 */
	private fun binUnionKind(self: AvailObject): A_Type {
		var union: A_Type = self.slot(BIN_UNION_KIND_OR_NIL)
		if (union.equalsNil()) {
			union = self.slot(BIN_ELEMENT_AT_, 1).binUnionKind()
			for (i in 2..self.variableObjectSlotsCount()) {
				union = union.typeUnion(
					self.slot(BIN_ELEMENT_AT_, i).binUnionKind())
			}
			if (isShared) {
				union = union.makeShared()
			}
			self.setSlot(BIN_UNION_KIND_OR_NIL, union)
		}
		return union
	}

	override fun o_BinUnionKind(self: AvailObject): A_Type =
		self.synchronizeIf(isShared) { binUnionKind(self) }

	override fun o_BinElementsAreAllInstancesOfKind(
		self: AvailObject,
		kind: A_Type
	): Boolean = self.binUnionKind().isSubtypeOf(kind)

	/**
	 * Add the given element to this bin, potentially modifying it if canDestroy
	 * is true and it's mutable.  Answer the new bin.  Note that the client is
	 * responsible for marking elementObject as immutable if another reference
	 * exists.
	 */
	override fun o_SetBinAddingElementHashLevelCanDestroy(
		self: AvailObject,
		elementObject: A_BasicObject,
		elementObjectHash: Int,
		myLevel: Int,
		canDestroy: Boolean
	): A_SetBin
	{
		assert(myLevel == level)
		//  First, grab the appropriate 6 bits from the hash.
		val objectEntryCount = self.variableObjectSlotsCount()
		val logicalIndex = elementObjectHash ushr shift and 63
		val logicalBitValue = 1L shl logicalIndex
		val vector = self.slot(BIT_VECTOR)
		val masked = vector and logicalBitValue - 1
		val physicalIndex = java.lang.Long.bitCount(masked) + 1
		val objectToModify: AvailObject
		var typeUnion: A_Type
		if (vector and logicalBitValue != 0L) {
			// The appropriate bil is already present.
			var entry: A_SetBin = self.slot(BIN_ELEMENT_AT_, physicalIndex)
			val previousBinSize = entry.setBinSize()
			val previousEntryHash = entry.setBinHash()
			val previousTotalHash = self.setBinHash()
			entry = entry.setBinAddingElementHashLevelCanDestroy(
				elementObject,
				elementObjectHash,
				level + 1,
				canDestroy)
			val delta = entry.setBinSize() - previousBinSize
			if (delta == 0) {
				// The bin was unchanged.
				if (!canDestroy) self.makeImmutable()
				return self
			}
			//  The element had to be added.
			val hashDelta = entry.setBinHash() - previousEntryHash
			val newSize = self.slot(BIN_SIZE) + delta
			typeUnion = self.slot(BIN_UNION_KIND_OR_NIL)
			if (!typeUnion.equalsNil()) {
				typeUnion = typeUnion.typeUnion(entry.binUnionKind())
			}
			objectToModify = if (canDestroy && isMutable) {
				// Clobber the object in place.
				self
			} else {
				if (!canDestroy && isMutable) {
					self.makeSubobjectsImmutable()
				}
				newLike(descriptorFor(Mutability.MUTABLE, level), self, 0, 0)
			}
			return objectToModify.apply {
				setSlot(BIN_HASH, previousTotalHash + hashDelta)
				setSlot(BIN_SIZE, newSize)
				setSlot(BIN_UNION_KIND_OR_NIL, typeUnion)
				setSlot(BIN_ELEMENT_AT_, physicalIndex, entry)
			}
		}
		// Augment object with a new entry.
		if (!canDestroy && isMutable) {
			self.makeSubobjectsImmutable()
		}
		typeUnion = self.mutableSlot(BIN_UNION_KIND_OR_NIL)
		if (!typeUnion.equalsNil()) {
			typeUnion = typeUnion.typeUnion(elementObject.kind())
		}
		objectToModify = createUninitializedHashedSetBin(
			level,
			objectEntryCount + 1,
			self.setBinSize() + 1,
			self.setBinHash() + elementObjectHash,
			vector or logicalBitValue,
			typeUnion)
		objectToModify.setSlotsFromObjectSlots(
			BIN_ELEMENT_AT_,
			1,
			self,
			BIN_ELEMENT_AT_,
			1,
			physicalIndex - 1)
		objectToModify.setSlot(BIN_ELEMENT_AT_, physicalIndex, elementObject)
		objectToModify.setSlotsFromObjectSlots(
			BIN_ELEMENT_AT_,
			physicalIndex + 1,
			self,
			BIN_ELEMENT_AT_,
			physicalIndex,
			objectEntryCount - physicalIndex + 1)
		return objectToModify
	}

	override fun o_BinHasElementWithHash(
		self: AvailObject,
		elementObject: A_BasicObject,
		elementObjectHash: Int
	): Boolean {
		// First, grab the appropriate 6 bits from the hash.
		val logicalIndex = elementObjectHash ushr shift and 63
		val logicalBitValue = 1L shl logicalIndex
		val vector = self.slot(BIT_VECTOR)
		if (vector and logicalBitValue == 0L) {
			return false
		}
		// There's an entry.  Count the 1-bits below it to compute its
		// zero-relative physicalIndex.
		val masked = vector and logicalBitValue - 1
		val physicalIndex: Int = java.lang.Long.bitCount(masked) + 1
		val subBin: A_SetBin = self.slot(BIN_ELEMENT_AT_, physicalIndex)
		return subBin.binHasElementWithHash(elementObject, elementObjectHash)
	}

	/**
	 * Remove elementObject from the bin object, if present. Answer the
	 * resulting bin. The bin may be modified if it's mutable and canDestroy.
	 */
	override fun o_BinRemoveElementHashLevelCanDestroy(
		self: AvailObject,
		elementObject: A_BasicObject,
		elementObjectHash: Int,
		myLevel: Int,
		canDestroy: Boolean
	): A_SetBin
	{
		assert(level == myLevel)
		val objectEntryCount = self.variableObjectSlotsCount()
		val logicalIndex = elementObjectHash ushr shift and 63
		val logicalBitValue = 1L shl logicalIndex
		val vector = self.slot(BIT_VECTOR)
		if (vector and logicalBitValue == 0L) {
			if (!canDestroy) {
				self.makeImmutable()
			}
			return self
		}
		val masked = vector and logicalBitValue - 1
		val physicalIndex: Int = java.lang.Long.bitCount(masked) + 1
		val oldEntry: A_SetBin = self.slot(BIN_ELEMENT_AT_, physicalIndex)
		val oldEntryHash = oldEntry.setBinHash()
		val oldEntrySize = oldEntry.setBinSize()
		val oldTotalHash = self.setBinHash()
		val oldTotalSize = self.setBinSize()
		val replacementEntry = oldEntry.binRemoveElementHashLevelCanDestroy(
			elementObject,
			elementObjectHash,
			level + 1,
			canDestroy)
		val deltaHash = replacementEntry.setBinHash() - oldEntryHash
		val deltaSize = replacementEntry.setBinSize() - oldEntrySize
		val result: AvailObject
		if (replacementEntry.setBinSize() == 0) {
			// Exclude the entire hash entry.
			if (objectEntryCount == 1) {
				// That was the last entry that we just removed.
				return emptyLinearSetBin(level)
			}
			result = createUninitializedHashedSetBin(
				level,
				objectEntryCount - 1,
				oldTotalSize + deltaSize,
				oldTotalHash + deltaHash,
				vector xor logicalBitValue,
				nil)
			result.setSlotsFromObjectSlots(
				BIN_ELEMENT_AT_,
				1,
				self,
				BIN_ELEMENT_AT_,
				1,
				physicalIndex - 1)
			result.setSlotsFromObjectSlots(
				BIN_ELEMENT_AT_,
				physicalIndex,
				self,
				BIN_ELEMENT_AT_,
				physicalIndex + 1,
				objectEntryCount - physicalIndex)
			if (!canDestroy) {
				result.makeSubobjectsImmutable()
			}
		} else {
			// Replace the hash entry.
			result = when {
				canDestroy && isMutable -> self
				else -> {
					if (!canDestroy) self.makeSubobjectsImmutable()
					newLike(descriptorFor(Mutability.MUTABLE, level), self, 0, 0)
				}
			}
			with(result) {
				setSlot(BIN_ELEMENT_AT_, physicalIndex, replacementEntry)
				setSlot(BIN_HASH, oldTotalHash + deltaHash)
				setSlot(BIN_SIZE, oldTotalSize + deltaSize)
				setSlot(BIN_UNION_KIND_OR_NIL, nil)
			}
		}
		return result
	}

	/**
	 * Check if object, a bin, holds a subset of the [potentialSuperset]'s
	 * elements.
	 *
	 * TODO (MvG]) – This could be much quicker in the case that some of the
	 * bins are shared between the sets.  Even if not, we should be able to
	 * avoid traversing some of the hashed layers for each element.
	 */
	override fun o_IsBinSubsetOf(
		self: AvailObject,
		potentialSuperset: A_Set
	): Boolean = (1..self.variableObjectSlotsCount()).all {
		self.slot(BIN_ELEMENT_AT_, it).isBinSubsetOf(potentialSuperset)
	}

	/**
	 * A [SetIterator] for iterating over a set whose root bin happens to be
	 * hashed.
	 *
	 * @constructor
	 *
	 * @param root
	 *   The root bin over which to iterate.
	 */
	internal class HashedSetBinIterator(
		root: A_SetBin
	) : SetIterator() {
		/**
		 * The path through set bins, excluding the leaf (non-bin) element.
		 */
		private val binStack = ArrayDeque<A_SetBin>()

		/**
		 * The position navigated through each bin.  It should always contain
		 * the same number of elements as in binStack.
		 */
		private val subscriptStack = ArrayDeque<Int>()

		/**
		 * The next value that will returned by [next], or null if the iterator
		 * is exhausted.
		 */
		private var currentElement: A_SetBin? = null

		init {
			traceDownward(root)
		}

		/**
		 * Visit this bin or element.  In particular, travel down its left spine
		 * so that it's positioned at the leftmost descendant.  Return the
		 * (non-bin) element at the bottom of the spine, which may be the
		 * argument itself.
		 *
		 * @param binOrElement
		 *   The bin or element to begin enumerating.
		 */
		private fun traceDownward(binOrElement: A_SetBin) {
			var current = binOrElement
			while (current.isSetBin) {
				binStack.add(current)
				val size = current.variableObjectSlotsCount()
				subscriptStack.add(size)
				current = current.binElementAt(size)
			}
			assert(binStack.size == subscriptStack.size)
			currentElement = current
		}

		override fun next(): AvailObject {
			if (currentElement === null) {
				throw NoSuchElementException()
			}
			val result: A_SetBin = currentElement!!
			assert(binStack.isNotEmpty())
			assert(binStack.size == subscriptStack.size)
			do {
				val leafBin = binStack.last()
				val nextIndex = subscriptStack.removeLast() - 1
				if (nextIndex >= 1) {
					// Advance along the bin.
					subscriptStack.add(nextIndex)
					assert(binStack.size == subscriptStack.size)
					traceDownward(leafBin.binElementAt(nextIndex))
					return result as AvailObject
				}
				// Exhausted the bin.
				binStack.removeLast()
				assert(binStack.size == subscriptStack.size)
			} while (!binStack.isEmpty())
			currentElement = null
			return result as AvailObject
		}

		override fun hasNext(): Boolean = currentElement !== null
	}

	override fun o_SetBinIterator(self: AvailObject): SetIterator =
		HashedSetBinIterator(self)

	override fun mutable() =
		descriptorFor(Mutability.MUTABLE, level)

	override fun immutable() =
		descriptorFor(Mutability.IMMUTABLE, level)

	override fun shared() =
		descriptorFor(Mutability.SHARED, level)

	companion object {
		/** Whether to do sanity checks on hashed set bins' hashes.  */
		private const val checkBinHashes = false

		/**
		 * Check that this hashed bin has a correct binHash.
		 *
		 * @param self
		 *   A hashed set bin.
		 */
		fun checkHashedSetBin(self: AvailObject) {
			@Suppress("ConstantConditionIf")
			if (checkBinHashes) {
				assert(self.descriptor() is HashedSetBinDescriptor)
				val stored = self.setBinHash()
				var calculated = 0
				for (i in 1..self.variableObjectSlotsCount()) {
					calculated +=
						self.slot(BIN_ELEMENT_AT_, i).setBinHash()
				}
				assert(calculated == stored) { "Failed bin hash cross-check" }
			}
		}

		/**
		 * Create a new hashed set bin with the given level, local size, total
		 * recursive number of elements, hash, bit vector, and either the bin
		 * union kind or [nil].  The client is responsible for setting the bin
		 * elements and making things immutable if necessary.
		 *
		 * @param level
		 *   The tree level at which this hashed bin occurs.
		 * @param localSize
		 *   The number of slots to allocate.
		 * @param totalSize
		 *   The number of elements recursively within me.
		 * @param hash
		 *   The hash of this bin.
		 * @param bitVector
		 *   The bit vector indicating which hash values are present.
		 * @param unionKindOrNil
		 *   Either nil or the kind that is nearest to the union of the
		 *   elements' types.
		 * @return
		 *   A new hashed set bin with uninitialized sub-bin slots.
		 */
		private fun createUninitializedHashedSetBin(
			level: Int,
			localSize: Int,
			totalSize: Int,
			hash: Int,
			bitVector: Long,
			unionKindOrNil: A_Type
		): AvailObject {
			assert(java.lang.Long.bitCount(bitVector) == localSize)
			val descriptor = descriptorFor(Mutability.MUTABLE, level)
			return descriptor.create(localSize) {
				setSlot(BIN_HASH, hash)
				setSlot(BIN_SIZE, totalSize)
				setSlot(BIT_VECTOR, bitVector)
				setSlot(BIN_UNION_KIND_OR_NIL, unionKindOrNil)
			}
		}

		/**
		 * Create a new hashed set bin with the given level, local size, total
		 * recursive number of elements, hash, bit vector, and either the bin
		 * union kind or [nil].  Initialize each sub-bin to the empty bin at
		 * level + 1.
		 *
		 * @param level
		 *   The tree level at which this hashed bin occurs.
		 * @param localSize
		 *   The number of slots to allocate.
		 * @param bitVector
		 *   The bit vector indicating which hash values are present.
		 * @return
		 *   A new hashed set bin with empty linear sub-bins.
		 */
		fun createInitializedHashSetBin(
			level: Int,
			localSize: Int,
			bitVector: Long
		): AvailObject {
			val instance = createUninitializedHashedSetBin(
				level, localSize, 0, 0, bitVector, nil)
			val emptySubbin: AvailObject =
				emptyLinearSetBin(level + 1)
			instance.fillSlots(BIN_ELEMENT_AT_, 1, localSize, emptySubbin)
			return instance
		}

		/**
		 * Create a hashed set bin from the size and generator.
		 *
		 * @param level
		 *   The level of bin to create.
		 * @param size
		 *   The number of values to take from the generator.
		 * @param generator
		 *   The generator.
		 * @return
		 *   A set bin.
		 */
		fun generateHashedSetBinFrom(
			level: Int,
			size: Int,
			generator: (Int)->A_BasicObject
		): AvailObject {
			// First, group the elements by the relevant 6 bits of hash.
			val groups = arrayOfNulls<MutableList<A_BasicObject>>(64)
			val shift = 6 * level
			for (i in 1..size) {
				val element = generator(i)
				val groupIndex = element.hash() ushr shift and 63
				var group = groups[groupIndex]
				if (group === null) {
					group = mutableListOf(element)
					groups[groupIndex] = group
				} else {
					// Check if the new element is the same as the first member
					// of the group.  This reduces the chance of having to
					// recurse to the bottom level before discovering massive
					// duplication.
					if (element.equals(group[0])) {
						continue
					}
					group.add(element)
				}
			}
			// Convert groups into bins.
			var bitVector: Long = 0
			var occupiedBinCount = 0
			for (binIndex in 0..63) {
				if (groups[binIndex] !== null) {
					bitVector = bitVector or (1L shl binIndex)
					occupiedBinCount++
				}
			}
			assert(bitVector != 0L)
			assert(occupiedBinCount != 0)
			val hashedBin = createUninitializedHashedSetBin(
				level,
				occupiedBinCount,
				0,  // Total count fixed later.
				0,  // Hash fixed later.
				bitVector,
				nil)
			var written = 0
			var hash = 0
			var totalCount = 0
			for (binIndex in 0..63) {
				val group: List<A_BasicObject>? = groups[binIndex]
				if (group !== null) {
					val childBin =
						generateSetBinFrom(level + 1, group.size) {
							group[it - 1]
						}
					totalCount += childBin.setBinSize()
					hash += childBin.setBinHash()
					hashedBin.setSlot(BIN_ELEMENT_AT_, ++written, childBin)
					groups[binIndex] = null  // Allow GC to clean it up early.
				}
			}
			if (hashedBin.setBinSize() == 1) {
				// All elements were equal.  Return the element itself to act as
				// a singleton bin.
				return hashedBin.slot(BIN_ELEMENT_AT_, 1)
			}
			assert(written == occupiedBinCount)
			hashedBin.setSlot(BIN_SIZE, totalCount)
			hashedBin.setSlot(BIN_HASH, hash)
			return hashedBin
		}

		/**
		 * The number of distinct levels that my instances can occupy in a set's
		 * hash tree.
		 */
		const val numberOfLevels: Int = 6

		/**
		 * Answer the appropriate `HashedSetBinDescriptor` to use for the given
		 * mutability and level.
		 *
		 * @param flag
		 *   Whether the descriptor is to be used for a mutable object.
		 * @param level
		 *   The bin tree level that its objects should occupy.
		 * @return
		 *   A suitable [HashedSetBinDescriptor].
		 */
		fun descriptorFor(
			flag: Mutability,
			level: Int
		): HashedSetBinDescriptor {
			assert(level in 0 until numberOfLevels)
			return descriptors[level * 3 + flag.ordinal]
		}

		/**
		 * [HashedSetBinDescriptor]s are clustered by mutability and level.
		 */
		val descriptors = Array(numberOfLevels * 3) {
			val level = it / 3
			val mut = Mutability.values()[it - level * 3]
			HashedSetBinDescriptor(mut, level)
		}
	}
}
