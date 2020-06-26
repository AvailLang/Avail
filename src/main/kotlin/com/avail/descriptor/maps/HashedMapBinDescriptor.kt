/*
 * HashedMapBinDescriptor.kt
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
package com.avail.descriptor.maps

 import com.avail.descriptor.maps.HashedMapBinDescriptor.IntegerSlots.BIN_SIZE
 import com.avail.descriptor.maps.HashedMapBinDescriptor.IntegerSlots.BIT_VECTOR
 import com.avail.descriptor.maps.HashedMapBinDescriptor.IntegerSlots.COMBINED_HASHES
 import com.avail.descriptor.maps.HashedMapBinDescriptor.IntegerSlots.Companion.KEYS_HASH
 import com.avail.descriptor.maps.HashedMapBinDescriptor.IntegerSlots.Companion.VALUES_HASH_OR_ZERO
 import com.avail.descriptor.maps.HashedMapBinDescriptor.ObjectSlots.BIN_KEY_UNION_KIND_OR_NIL
 import com.avail.descriptor.maps.HashedMapBinDescriptor.ObjectSlots.BIN_VALUE_UNION_KIND_OR_NIL
 import com.avail.descriptor.maps.HashedMapBinDescriptor.ObjectSlots.SUB_BINS_
 import com.avail.descriptor.maps.LinearMapBinDescriptor.Companion.createSingleLinearMapBin
 import com.avail.descriptor.maps.LinearMapBinDescriptor.Companion.emptyLinearMapBin
 import com.avail.descriptor.maps.MapDescriptor.MapIterable
 import com.avail.descriptor.representation.A_BasicObject
 import com.avail.descriptor.representation.A_BasicObject.Companion.synchronizeIf
 import com.avail.descriptor.representation.AbstractSlotsEnum
 import com.avail.descriptor.representation.AvailObject
 import com.avail.descriptor.representation.AvailObject.Companion.newIndexedDescriptor
 import com.avail.descriptor.representation.AvailObjectRepresentation.Companion.newLike
 import com.avail.descriptor.representation.BitField
 import com.avail.descriptor.representation.IntegerSlotsEnum
 import com.avail.descriptor.representation.Mutability
 import com.avail.descriptor.representation.Mutability.MUTABLE
 import com.avail.descriptor.representation.NilDescriptor.Companion.nil
 import com.avail.descriptor.representation.ObjectSlotsEnum
 import com.avail.descriptor.sets.A_Set
 import com.avail.descriptor.sets.HashedSetBinDescriptor
 import com.avail.descriptor.sets.SetDescriptor
 import com.avail.descriptor.types.A_Type
 import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
 import com.avail.descriptor.types.TypeTag
 import com.avail.utility.cast
 import java.util.*

/**
 * This class implements the internal hashed nodes of a Bagwell Ideal Hash Tree.
 * It's similar to [HashedSetBinDescriptor], but has operations suitable for use
 * by a [map][MapDescriptor] rather than a [set][SetDescriptor].
 *
 * The basic idea is that small bins are simple
 * [linear&#32;structures][LinearMapBinDescriptor], but larger bins use the hash
 * of the key to determine which one of the up to 64 child bins is responsible
 * for that key.  Different levels of the tree use different 6-bit regions of
 * the hash values.  We could always store 64 slots, but Bagwell's mechanism is
 * to store a 64-bit vector where a 1 bit indicates that the corresponding index
 * (0..63) extracted from the hash value has a pointer to the corresponding
 * sub-bin.  If the bit is 0 then that pointer is elided entirely.  By suitable
 * use of bit shifting, masking, and [counting][Integer.bitCount], one is able
 * to extract the 6 appropriate dispatch bits and access the Nth sub-bin or
 * determine that it's not already present.
 *
 * This mechanism produces a hash tree no deeper than about 6 levels, even for a
 * huge number of entries.  It also allows efficient "persistent" manipulation
 * (in the function programming sense).  Given a map one can produce another map
 * that has a small number of edits (new keys, removed keys, new values for
 * existing keys) using only a few additional bins – without disrupting the
 * original map.
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
class HashedMapBinDescriptor private constructor(
	mutability: Mutability,
	level: Int
) : MapBinDescriptor(
	mutability,
	TypeTag.MAP_HASHED_BIN_TAG,
	ObjectSlots::class.java,
	IntegerSlots::class.java,
	level
) {
	/**
	 * The number of bits to shift a hash value to the right before extracting
	 * six bits to determine a bin number.  This is a function of the level,
	 * so that each level is hashed with a different part of the hash value.
	 */
	private val shift: Int = (level * 6).also { assert(it < 32) }

	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum {
		/**
		 * A long holding [BitField]s containing the combined keys hash and the
		 * combined values hash or zero.
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

		companion object {
			/**
			 * The sum of the hashes of the elements recursively within this
			 * bin.
			 */
			@JvmField
			val KEYS_HASH = BitField(COMBINED_HASHES, 0, 32)

			/**
			 * The sum of the hashes of the elements recursively within this
			 * bin, or zero if not computed.
			 */
			@JvmField
			val VALUES_HASH_OR_ZERO = BitField(COMBINED_HASHES, 32, 32)

			init {
				assert(MapBinDescriptor.IntegerSlots.COMBINED_HASHES.ordinal
					== COMBINED_HASHES.ordinal)
				assert(MapBinDescriptor.IntegerSlots.KEYS_HASH
					.isSamePlaceAs(KEYS_HASH))
				assert(MapBinDescriptor.IntegerSlots.VALUES_HASH_OR_ZERO
					.isSamePlaceAs(VALUES_HASH_OR_ZERO))
			}
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum {
		/**
		 * The union of the types of all keys recursively within this bin. If
		 * this is [nil], then it can be recomputed when needed and cached.
		 */
		BIN_KEY_UNION_KIND_OR_NIL,

		/**
		 * The union of the types of all values recursively within this bin. If
		 * this is [nil], then it can be recomputed when needed and cached.
		 */
		BIN_VALUE_UNION_KIND_OR_NIL,

		/**
		 * The actual sub-bins.  Each slot corresponds with a 1 bit in the bit
		 * vector, treating it as an unsigned vector of bits.
		 */
		SUB_BINS_
	}

	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum
	) = e === COMBINED_HASHES
		|| e === BIN_KEY_UNION_KIND_OR_NIL
		|| e === BIN_VALUE_UNION_KIND_OR_NIL

	override fun o_BinElementAt(
		self: AvailObject,
		index: Int
	): AvailObject = self.slot(SUB_BINS_, index)

	override fun o_ForEachInMapBin(
		self: AvailObject,
		action: (AvailObject, AvailObject) -> Unit
	) {
		for (i in 1..self.variableObjectSlotsCount()) {
			self.slot(SUB_BINS_, i).forEachInMapBin(action)
		}
	}

	override fun o_MapBinSize(self: AvailObject) = self.slot(BIN_SIZE).toInt()

	/**
	 * Check if object, a bin, holds a subset of aSet's elements.
	 */
	override fun o_IsBinSubsetOf(
		self: AvailObject,
		potentialSuperset: A_Set
	) = (1..self.variableObjectSlotsCount())
		.all { self.slot(SUB_BINS_, it).isBinSubsetOf(potentialSuperset) }

	override fun o_IsHashedMapBin(self: AvailObject) = true

	/**
	 * Compute this bin's key type and value type.
	 *
	 * @param self
	 *   The hashed map bin to populate with key and value type information.
	 */
	private fun computeKeyAndValueKinds(self: AvailObject) {
		var keyType = bottom()
		var valueType = bottom()
		val binCount = self.objectSlotsCount() - numberOfFixedObjectSlots()
		for (i in 1..binCount) {
			val subBin = self.slot(SUB_BINS_, i)
			keyType = keyType.typeUnion(subBin.mapBinKeyUnionKind())
			valueType = valueType.typeUnion(subBin.mapBinValueUnionKind())
		}
		if (isShared) {
			keyType = keyType.traversed().makeShared()
			valueType = valueType.traversed().makeShared()
		}
		self.setSlot(BIN_KEY_UNION_KIND_OR_NIL, keyType)
		self.setSlot(BIN_VALUE_UNION_KIND_OR_NIL, valueType)
	}

	/**
	 * Compute and install the bin key union kind for the specified hashed map
	 * bin.
	 *
	 * @param self
	 *   The hashed map bin.
	 * @return
	 *   The union kind of the bin's key types.
	 */
	private fun mapBinKeyUnionKind(self: AvailObject): AvailObject {
		val keyType = self.slot(BIN_KEY_UNION_KIND_OR_NIL)
		if (!keyType.equalsNil()) return keyType
		computeKeyAndValueKinds(self)
		return self.slot(BIN_KEY_UNION_KIND_OR_NIL)
	}

	override fun o_MapBinKeyUnionKind(self: AvailObject): A_Type =
		self.synchronizeIf(isShared) { mapBinKeyUnionKind(self) }

	/**
	 * Compute and install the bin value union kind for the specified hashed map
	 * bin.
	 *
	 * @param self
	 *   The hashed map bin.
	 * @return
	 *   The union kind of the bin's value types.
	 */
	private fun mapBinValueUnionKind(self: AvailObject): AvailObject {
		val valueType = self.slot(BIN_VALUE_UNION_KIND_OR_NIL)
		if (!valueType.equalsNil()) return valueType
		computeKeyAndValueKinds(self)
		return self.slot(BIN_VALUE_UNION_KIND_OR_NIL)
	}

	override fun o_MapBinValueUnionKind(self: AvailObject): A_Type =
		self.synchronizeIf(isShared) { mapBinValueUnionKind(self) }

	/**
	 * Add the given (key,value) pair to this bin, potentially modifying it if
	 * canDestroy is true and it's mutable.  Answer the new bin.  Note that the
	 * client is responsible for marking both the key and value as immutable if
	 * other references exists.
	 */
	override fun o_MapBinAtHashPutLevelCanDestroy(
		self: AvailObject,
		key: A_BasicObject,
		keyHash: Int,
		value: A_BasicObject,
		myLevel: Int,
		canDestroy: Boolean
	): A_MapBin {
		assert(myLevel == level)
		checkHashedMapBin(self)
		val oldKeysHash = self.mapBinKeysHash()
		val oldSize = self.mapBinSize()
		val objectEntryCount = self.variableObjectSlotsCount()
		// Grab the appropriate 6 bits from the hash.
		val logicalIndex = keyHash ushr shift and 63
		val vector = self.slot(BIT_VECTOR)
		val masked = vector and (1L shl logicalIndex) - 1
		val physicalIndex: Int = java.lang.Long.bitCount(masked) + 1
		val delta: Int
		val hashDelta: Int
		val objectToModify: AvailObject
		if (vector and (1L shl logicalIndex) != 0L)
		{
			// Sub-bin already exists for those hash bits.  Update the sub-bin.
			val oldSubBin = self.slot(SUB_BINS_, physicalIndex)
			val oldSubBinSize = oldSubBin.mapBinSize()
			val oldSubBinKeyHash = oldSubBin.mapBinKeysHash()
			val newSubBin = oldSubBin.mapBinAtHashPutLevelCanDestroy(
				key, keyHash, value, myLevel + 1, canDestroy)
			delta = newSubBin.mapBinSize() - oldSubBinSize
			hashDelta = newSubBin.mapBinKeysHash() - oldSubBinKeyHash
			objectToModify = if (canDestroy && isMutable) {
				self
			} else {
				if (!canDestroy && isMutable) {
					self.makeSubobjectsImmutable()
				}
				newLike(
					descriptorFor(MUTABLE, level), self, 0, 0)
			}
			objectToModify.setSlot(SUB_BINS_, physicalIndex, newSubBin)
		}
		else
		{
			// Add a sub-bin for that hash slot.
			delta = 1
			hashDelta = keyHash
			if (!canDestroy && isMutable) {
				self.makeSubobjectsImmutable()
			}
			objectToModify = descriptorFor(MUTABLE, level)
				.create(objectEntryCount + 1)
			objectToModify.setSlot(BIT_VECTOR, vector or (1L shl logicalIndex))
			objectToModify.setSlotsFromObjectSlots(
				SUB_BINS_, 1, self, SUB_BINS_, 1, physicalIndex - 1)
			val newSingleBin = createSingleLinearMapBin(
				key, keyHash, value, myLevel + 1)
			objectToModify.setSlot(SUB_BINS_, physicalIndex, newSingleBin)
			objectToModify.setSlotsFromObjectSlots(
				SUB_BINS_,
				physicalIndex + 1,
				self,
				SUB_BINS_,
				physicalIndex,
				objectEntryCount - physicalIndex + 1)
		}
		assert(objectToModify.descriptor().isMutable)
		objectToModify.setSlot(KEYS_HASH, oldKeysHash + hashDelta)
		objectToModify.setSlot(VALUES_HASH_OR_ZERO, 0)
		objectToModify.setSlot(BIN_SIZE, oldSize + delta.toLong())
		objectToModify.setSlot(BIN_KEY_UNION_KIND_OR_NIL, nil)
		objectToModify.setSlot(BIN_VALUE_UNION_KIND_OR_NIL, nil)
		checkHashedMapBin(objectToModify)
		return objectToModify
	}

	override fun o_MapBinAtHash(
		self: AvailObject,
		key: A_BasicObject,
		keyHash: Int
	): AvailObject? {
		// First, grab the appropriate 6 bits from the hash.
		val logicalIndex = keyHash ushr shift and 63
		val vector = self.slot(BIT_VECTOR)
		if (vector and (1L shl logicalIndex) == 0L) {
			// Not found.  Answer null.
			return null
		}
		// There's an entry.  Count the 1-bits below it to compute its
		// zero-relative physicalIndex.
		val masked = vector and (1L shl logicalIndex) - 1
		val physicalIndex: Int = java.lang.Long.bitCount(masked) + 1
		val subBin = self.slot(SUB_BINS_, physicalIndex)
		return subBin.mapBinAtHash(key, keyHash)
	}

	/**
	 * Remove the key from the bin object, if present.  Answer the resulting
	 * bin.  The bin may be modified if it's mutable and canDestroy.
	 */
	override fun o_MapBinRemoveKeyHashCanDestroy(
		self: AvailObject,
		key: A_BasicObject,
		keyHash: Int,
		canDestroy: Boolean
	): A_MapBin {
		checkHashedMapBin(self)
		if (isMutable && !canDestroy) {
			self.makeImmutable()
		}
		// Grab the appropriate 6 bits from the hash.
		val logicalIndex = keyHash ushr shift and 63
		val vector = self.slot(BIT_VECTOR)
		if (vector and (1L shl logicalIndex) == 0L) {
			// Definitely not present.
			return self
		}
		// There's an entry which might contain the key and value.  Count the
		// 1-bits below it to compute its zero-relative physicalIndex.
		val oldSize = self.slot(BIN_SIZE).toInt()
		val oldKeysHash = self.slot(KEYS_HASH)
		val masked = vector and (1L shl logicalIndex) - 1
		val physicalIndex: Int = java.lang.Long.bitCount(masked) + 1
		val oldSubBin = self.slot(SUB_BINS_, physicalIndex)
		val oldSubBinKeysHash = oldSubBin.mapBinKeysHash()
		val oldSubBinSize = oldSubBin.mapBinSize()
		val newSubBin = oldSubBin.mapBinRemoveKeyHashCanDestroy(
			key, keyHash, canDestroy)
		val delta: Int
		val deltaHash: Int
		val objectToModify: AvailObject
		if (newSubBin.mapBinSize() == 0) {
			// The entire subBin must be removed.
			val oldSlotCount: Int = java.lang.Long.bitCount(vector)
			if (oldSlotCount == 1) {
				// ...and so must this one.
				return emptyLinearMapBin(level)
			}
			objectToModify = newIndexedDescriptor(
				java.lang.Long.bitCount(vector) - 1,
				descriptorFor(MUTABLE, level))
			var destination = 1
			for (source in 1..oldSlotCount) {
				if (source != physicalIndex) {
					objectToModify.setSlot(
						SUB_BINS_, destination, self.slot(SUB_BINS_, source))
					destination++
				}
			}
			delta = -1
			deltaHash = -oldSubBinKeysHash
			objectToModify.setSlot(
				BIT_VECTOR,
				self.slot(BIT_VECTOR) and (1L shl logicalIndex).inv())
		} else {
			// The subBin has to be replaced...
			delta = newSubBin.mapBinSize() - oldSubBinSize
			deltaHash = newSubBin.mapBinKeysHash() - oldSubBinKeysHash
			assert(canDestroy || !self.descriptor().isMutable)
			objectToModify =
				if (self.descriptor().isMutable) {
					self
				} else {
					newLike(descriptorFor(MUTABLE, level), self, 0, 0)
				}
			objectToModify.setSlot(SUB_BINS_, physicalIndex, newSubBin)
		}
		assert(objectToModify.descriptor().isMutable)
		objectToModify.setSlot(BIN_SIZE, oldSize + delta.toLong())
		objectToModify.setSlot(KEYS_HASH, oldKeysHash + deltaHash)
		objectToModify.setSlot(VALUES_HASH_OR_ZERO, 0)
		objectToModify.setSlot(BIN_KEY_UNION_KIND_OR_NIL, nil)
		objectToModify.setSlot(BIN_VALUE_UNION_KIND_OR_NIL, nil)
		checkHashedMapBin(objectToModify)
		return objectToModify
	}

	override fun o_MapBinAtHashReplacingLevelCanDestroy(
		self: AvailObject,
		key: A_BasicObject,
		keyHash: Int,
		notFoundValue: A_BasicObject,
		transformer: (AvailObject, AvailObject) -> A_BasicObject,
		myLevel: Int,
		canDestroy: Boolean
	): A_MapBin {
		checkHashedMapBin(self)
		if (isMutable && !canDestroy) {
			self.makeImmutable()
		}
		// First, grab the appropriate 6 bits from the hash.
		val logicalIndex = keyHash ushr shift and 63
		val vector = self.slot(BIT_VECTOR)
		if (vector and (1L shl logicalIndex) == 0L) {
			// Definitely not present, so add it.
			return self.mapBinAtHashPutLevelCanDestroy(
				key,
				keyHash,
				transformer(key.cast(), notFoundValue.cast()),
				level,
				canDestroy)
		}
		// Sub-bin already exists for those hash bits.  Update the sub-bin.
		val oldSize = self.slot(BIN_SIZE).toInt()
		val oldKeysHash = self.slot(KEYS_HASH)
		val masked = vector and (1L shl logicalIndex) - 1
		val physicalIndex: Int = java.lang.Long.bitCount(masked) + 1
		val oldSubBin = self.slot(SUB_BINS_, physicalIndex)
		val oldSubBinSize = oldSubBin.mapBinSize()
		val oldSubBinKeyHash = oldSubBin.mapBinKeysHash()
		val newSubBin = oldSubBin.mapBinAtHashReplacingLevelCanDestroy(
			key,
			keyHash,
			notFoundValue,
			transformer,
			myLevel + 1,
			canDestroy)
		val delta = newSubBin.mapBinSize() - oldSubBinSize
		val hashDelta = newSubBin.mapBinKeysHash() - oldSubBinKeyHash
		val objectToModify =
			if (canDestroy && isMutable) self
			else newLike(descriptorFor(MUTABLE, level), self, 0, 0)
		assert(objectToModify.descriptor().isMutable)
		objectToModify.setSlot(SUB_BINS_, physicalIndex, newSubBin)
		objectToModify.setSlot(KEYS_HASH, oldKeysHash + hashDelta)
		objectToModify.setSlot(VALUES_HASH_OR_ZERO, 0)
		objectToModify.setSlot(BIN_SIZE, oldSize + delta.toLong())
		objectToModify.setSlot(BIN_KEY_UNION_KIND_OR_NIL, nil)
		objectToModify.setSlot(BIN_VALUE_UNION_KIND_OR_NIL, nil)
		checkHashedMapBin(objectToModify)
		return objectToModify
	}

	override fun o_MapBinValuesHash(self: AvailObject): Int =
		self.synchronizeIf(isShared) { mapBinValuesHash(self) }

	/**
	 * A [MapIterable] used for iterating over the key/value pairs of a map
	 * whose root bin happens to be hashed.
	 */
	internal class HashedMapBinIterable(root: AvailObject) : MapIterable() {
		/**
		 * The path through map bins, including the current linear bin.
		 */
		private val binStack: Deque<AvailObject> = ArrayDeque()

		/**
		 * The current position in each bin on the binStack, including the
		 * linear bin.  It should be the same size as the binStack.  When
		 * they're both empty it indicates `!hasNext()`.
		 */
		private val subscriptStack: Deque<Int> = ArrayDeque()

		/**
		 * When constructing a new instance, do this.
		 */
		init {
			followRightmost(root)
		}

		/**
		 * Visit this bin. In particular, travel down its right spine so that
		 * it's positioned at the rightmost descendant.
		 *
		 * @param bin
		 *   The bin at which to begin enumerating.
		 */
		private fun followRightmost(bin: AvailObject) {
			if (bin.mapBinSize() == 0) {
				// An empty bin may only occur at the top of the bin tree.
				assert(binStack.isEmpty())
				assert(subscriptStack.isEmpty())
				entry.setKeyAndHashAndValue(null, 0, null)
				return
			}
			var currentBin = bin.traversed()
			while (currentBin.isHashedMapBin()) {
				binStack.addLast(currentBin)
				val count = currentBin.variableObjectSlotsCount()
				// Move right-to-left for a simpler limit check.
				subscriptStack.addLast(count)
				currentBin = currentBin.binElementAt(count).traversed()
			}
			binStack.addLast(currentBin)
			// Move leftward in this linear bin.  Note that slots are
			// alternating keys and values.
			val linearSlotCount = currentBin.variableObjectSlotsCount()
			subscriptStack.addLast(linearSlotCount shr 1)
			assert(binStack.size == subscriptStack.size)
		}

		override fun next(): MapDescriptor.Entry {
			if (binStack.isEmpty()) {
				throw NoSuchElementException()
			}
			val linearBin = binStack.last.traversed()
			val linearIndex = subscriptStack.last
			entry.setKeyAndHashAndValue(
				linearBin.binElementAt((linearIndex shl 1) - 1),
				linearBin.intSlot(
					LinearMapBinDescriptor.IntegerSlots.KEY_HASHES_AREA_,
					linearIndex),
				linearBin.binElementAt(linearIndex shl 1))
			// Got the result.  Now advance the state...
			if (linearIndex > 1) {
				// Continue in same leaf bin.
				subscriptStack.removeLast()
				subscriptStack.addLast(linearIndex - 1)
				return entry
			}
			// Find another leaf bin.
			binStack.removeLast()
			subscriptStack.removeLast()
			assert(binStack.size == subscriptStack.size)
			while (true) {
				if (subscriptStack.isEmpty()) {
					// This was the last entry in the map.
					return entry
				}
				val nextSubscript = subscriptStack.removeLast() - 1
				if (nextSubscript != 0) {
					// Continue in current internal (hashed) bin.
					subscriptStack.addLast(nextSubscript)
					assert(binStack.size == subscriptStack.size)
					followRightmost(binStack.last.binElementAt(nextSubscript))
					assert(binStack.size == subscriptStack.size)
					return entry
				}
				binStack.removeLast()
				assert(binStack.size == subscriptStack.size)
			}
		}

		override fun hasNext() = !binStack.isEmpty()
	}

	override fun o_MapBinIterable(self: AvailObject): MapIterable =
		HashedMapBinIterable(self)

	companion object {
		/**
		 * A static switch for enabling slow, detailed correctness checks.
		 */
		private const val shouldCheck = false

		/**
		 * Make sure the `HashedMapBinDescriptor hashed map bin` is
		 * well-formed at this moment.
		 *
		 * @param self
		 *   A hashed bin used by maps.
		 */
		fun checkHashedMapBin(self: AvailObject) {
			@Suppress("ConstantConditionIf")
			if (shouldCheck) {
				val size = self.variableObjectSlotsCount()
				assert(java.lang.Long.bitCount(self.slot(BIT_VECTOR)) == size)
				var keyHashSum = 0
				var valueHashSum = 0
				var totalCount = 0
				for (i in 1..size) {
					val subBin = self.slot(SUB_BINS_, i)
					keyHashSum += subBin.mapBinKeysHash()
					valueHashSum += subBin.mapBinValuesHash()
					totalCount += subBin.mapBinSize()
				}
				assert(self.slot(KEYS_HASH) == keyHashSum)
				val storedValuesHash = self.mutableSlot(VALUES_HASH_OR_ZERO)
				assert(storedValuesHash == 0
					|| storedValuesHash == valueHashSum)
				assert(self.slot(BIN_SIZE) == totalCount.toLong())
			}
		}

		/**
		 * Lazily compute and install the values hash of the specified map bin.
		 *
		 * @param self
		 *   The map bin.
		 * @return
		 *   The hash of the bin's values.
		 */
		private fun mapBinValuesHash(self: AvailObject): Int {
			var valuesHash = self.slot(VALUES_HASH_OR_ZERO)
			if (valuesHash == 0) {
				val size = self.variableIntegerSlotsCount()
				(1..size).forEach {
					valuesHash += self.slot(SUB_BINS_, it).mapBinValuesHash()
				}
				self.setSlot(VALUES_HASH_OR_ZERO, valuesHash)
			}
			return valuesHash
		}

		/**
		 * Create a hashed map bin at the given level and with the given bit
		 * vector. The number of 1 bits in the bit vector determine how many
		 * sub-bins to allocate.  Start each sub-bin as an empty linear bin,
		 * with the expectation that it will be populated during subsequent
		 * initialization of this bin.
		 *
		 * @param myLevel
		 *   The hash tree depth, which controls how much to shift hashes.
		 * @param bitVector
		 *   The [Long] containing a 1 bit for each sub-bin slot.
		 * @return
		 *   A hash map bin suitable for adding entries to.  The bin is
		 *   denormalized, with all sub-bins set to empty linear bins.
		 */
		fun createLevelBitVector(
			myLevel: Int,
			bitVector: Long
		): AvailObject {
			val newSize: Int = java.lang.Long.bitCount(bitVector)
			val result = descriptorFor(MUTABLE, myLevel).create(newSize)
			result.setSlot(KEYS_HASH, 0)
			result.setSlot(VALUES_HASH_OR_ZERO, 0)
			result.setSlot(BIN_SIZE, 0)
			result.setSlot(BIT_VECTOR, bitVector)
			result.setSlot(BIN_KEY_UNION_KIND_OR_NIL, nil)
			result.setSlot(BIN_VALUE_UNION_KIND_OR_NIL, nil)
			result.fillSlots(
				SUB_BINS_,
				1,
				newSize,
				emptyLinearMapBin(myLevel + 1))
			checkHashedMapBin(result)
			return result
		}

		/**
		 * The number of distinct levels that my instances can occupy in a map's
		 * hash tree.
		 */
		private const val numberOfLevels: Int = 6

		/**
		 * Answer the appropriate [HashedMapBinDescriptor] to use for the given
		 * mutability and level.
		 *
		 * @param flag
		 *   The mutability of the object.
		 * @param level
		 *   The bin tree level that its objects should occupy.
		 * @return
		 *   A suitable [HashedSetBinDescriptor].
		 */
		private fun descriptorFor(
			flag: Mutability,
			level: Int
		): HashedMapBinDescriptor {
			assert(level in 0 until numberOfLevels)
			return descriptors[level * 3 + flag.ordinal]
		}

		/**
		 * [HashedMapBinDescriptor]s clustered by mutability and level.
		 */
		val descriptors = Array(numberOfLevels * 3) {
			val level = it / 3
			val mut = Mutability.values()[it - level * 3]
			HashedMapBinDescriptor(mut, level)
		}
	}

	override fun mutable() = descriptorFor(MUTABLE, level)

	override fun immutable() = descriptorFor(Mutability.IMMUTABLE, level)

	override fun shared() = descriptorFor(Mutability.SHARED, level)
}
