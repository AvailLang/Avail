/*
 * LinearMapBinDescriptor.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.descriptor.maps
import avail.descriptor.maps.A_MapBin.Companion.mapBinAtHashPutLevelCanDestroy
import avail.descriptor.maps.A_MapBin.Companion.mapBinKeysHash
import avail.descriptor.maps.A_MapBin.Companion.mapBinSize
import avail.descriptor.maps.HashedMapBinDescriptor.Companion.checkHashedMapBin
import avail.descriptor.maps.LinearMapBinDescriptor.IntegerSlots.COMBINED_HASHES
import avail.descriptor.maps.LinearMapBinDescriptor.IntegerSlots.Companion.KEYS_HASH
import avail.descriptor.maps.LinearMapBinDescriptor.IntegerSlots.Companion.VALUES_HASH_OR_ZERO
import avail.descriptor.maps.LinearMapBinDescriptor.IntegerSlots.KEY_HASHES_AREA_
import avail.descriptor.maps.LinearMapBinDescriptor.ObjectSlots.BIN_KEY_UNION_KIND_OR_NIL
import avail.descriptor.maps.LinearMapBinDescriptor.ObjectSlots.BIN_SLOT_AT_
import avail.descriptor.maps.LinearMapBinDescriptor.ObjectSlots.BIN_VALUE_UNION_KIND_OR_NIL
import avail.descriptor.maps.MapDescriptor.MapIterable
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_BasicObject.Companion.synchronizeIf
import avail.descriptor.representation.AbstractSlotsEnum
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.newObjectIndexedIntegerIndexedDescriptor
import avail.descriptor.representation.AvailObjectRepresentation.Companion.newLike
import avail.descriptor.representation.BitField
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.Mutability.IMMUTABLE
import avail.descriptor.representation.Mutability.MUTABLE
import avail.descriptor.representation.Mutability.SHARED
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.TypeTag
import avail.utility.structures.EnumMap

/**
 * A [LinearMapBinDescriptor] is a leaf bin in a [map][MapDescriptor]'s
 * hierarchy of bins.  It consists of a (usually) small number of keys and
 * associated values, in no particular order.  If more elements need to be
 * stored, a [hashed&#32;bin][HashedMapBinDescriptor] will be used instead.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param level
 *   The depth of the bin in the hash tree.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
internal class LinearMapBinDescriptor private constructor(
	mutability: Mutability,
	level: Int
) : MapBinDescriptor(
	mutability,
	TypeTag.UNKNOWN_TAG,
	ObjectSlots::class.java,
	IntegerSlots::class.java,
	level
) {
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
		 * The hash values of the keys present in this bin.  These are recorded
		 * separately here to reduce the cost of locating a particular key.
		 */
		KEY_HASHES_AREA_;

		companion object {
			/**
			 * The sum of the hashes of the elements recursively within this
			 * bin.
			 */
			val KEYS_HASH = BitField(COMBINED_HASHES, 0, 32) { null }

			/**
			 * The sum of the hashes of the elements recursively within this
			 * bin, or zero if not computed.
			 */
			val VALUES_HASH_OR_ZERO = BitField(COMBINED_HASHES, 32, 32) { null }

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
		 * The union of the types of all values recursively within this bin.
		 * If this is [nil], then it can be recomputed when needed and cached.
		 */
		BIN_VALUE_UNION_KIND_OR_NIL,

		/**
		 * The elements of this bin. The elements are never sub-bins, since
		 * this is a [linear&#32;bin][LinearMapBinDescriptor], which is always a
		 * leaf bin.
		 */
		BIN_SLOT_AT_
	}

	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum
	) = e === COMBINED_HASHES
		|| e === BIN_KEY_UNION_KIND_OR_NIL
		|| e === BIN_VALUE_UNION_KIND_OR_NIL

	override fun o_BinElementAt(self: AvailObject, index: Int) =
		self.slot(BIN_SLOT_AT_, index)

	override fun o_ForEachInMapBin(
		self: AvailObject,
		action: (AvailObject, AvailObject) -> Unit
	) {
		(1..(entryCount(self) shl 1) step 2).forEach {
			action(
				self.slot(BIN_SLOT_AT_, it),
				self.slot(BIN_SLOT_AT_, it + 1))
		}
	}

	/** Answer how many (key,value) pairs this bin contains. */
	override fun o_MapBinSize(self: AvailObject) = entryCount(self)

	override fun o_MapBinAtHash(
		self: AvailObject,
		key: A_BasicObject,
		keyHash: Int
	): AvailObject? {
		val limit = entryCount(self)
		var index = 1
		while (true)
		{
			index = self.intLinearSearch(
				KEY_HASHES_AREA_, index, limit, keyHash)
			if (index == 0) return null
			if (self.slot(BIN_SLOT_AT_, (index shl 1) - 1).equals(key))
				return self.slot(BIN_SLOT_AT_, index shl 1)
			index++
		}
	}

	override fun o_MapBinAtHashPutLevelCanDestroy(
		self: AvailObject,
		key: A_BasicObject,
		keyHash: Int,
		value: A_BasicObject,
		myLevel: Int,
		canDestroy: Boolean
	): A_MapBin {
		// Associate the key and value in this bin, potentially modifying it if
		// canDestroy and it's mutable.  Answer the new bin.  Note that the
		// client is responsible for marking the key and value as immutable if
		// other references exist.
		assert(myLevel == level)
		val oldSize = entryCount(self)
		var index = 1
		while (true)
		{
			index = self.intLinearSearch(
				KEY_HASHES_AREA_, index, oldSize, keyHash)
			if (index == 0)
			{
				// It's not present, so grow the list.  Keep it simple for now
				// by always replacing the list.
				if (myLevel < numberOfLevels - 1 && oldSize >= thresholdToHash)
				{
					// Convert to a hashed bin.
					var bitPosition = (keyHash ushr (6 * myLevel)) and 63
					var bitVector = 1L shl bitPosition
					for (i in 1 .. oldSize)
					{
						val anotherKeyHash = self.intSlot(KEY_HASHES_AREA_, i)
						bitPosition = (anotherKeyHash ushr (6 * myLevel)) and 63
						bitVector = bitVector or (1L shl bitPosition)
					}
					val result = HashedMapBinDescriptor.createLevelBitVector(
						myLevel, bitVector)
					for (i in 0 .. oldSize)
					{
						val eachKey: A_BasicObject
						val eachHash: Int
						val eachValue: A_BasicObject
						if (i == 0)
						{
							eachKey = key
							eachHash = keyHash
							eachValue = value
						}
						else
						{
							eachKey = self.slot(BIN_SLOT_AT_, (i shl 1) - 1)
							eachHash = self.intSlot(KEY_HASHES_AREA_, i)
							eachValue = self.slot(BIN_SLOT_AT_, i shl 1)
						}
						assert(result.descriptor().isMutable)
						val localAddResult =
							result.mapBinAtHashPutLevelCanDestroy(
								eachKey, eachHash, eachValue, myLevel, true)
						assert(localAddResult.sameAddressAs(result)) {
							"The element should have been added without copying"
						}
					}
					assert(result.mapBinSize == oldSize + 1)
					checkHashedMapBin(result)
					return result
				}
				//  Make a slightly larger linear bin and populate it.
				val result = newLike(
					descriptorFor(MUTABLE, myLevel),
					self,
					2,
					// Grow if it had an even number of ints
					oldSize and 1 xor 1)
				result.setSlot(KEYS_HASH, self.mapBinKeysHash + keyHash)
				result.setSlot(VALUES_HASH_OR_ZERO, 0)
				result.setIntSlot(KEY_HASHES_AREA_, oldSize + 1, keyHash)
				result.setSlot(BIN_SLOT_AT_, (oldSize shl 1) + 1, key)
				result.setSlot(BIN_SLOT_AT_, (oldSize shl 1) + 2, value)

				// Clear the key/value kind fields.  We could be more precise,
				// but that has a cost that's probably not worthwhile.
				result.setSlot(BIN_KEY_UNION_KIND_OR_NIL, nil)
				result.setSlot(BIN_VALUE_UNION_KIND_OR_NIL, nil)
				when
				{
					canDestroy && isMutable ->
						// Ensure destruction of the old object doesn't drag
						// along anything shared, but don't go to the expense of
						// marking anything in common as shared.
						self.setToInvalidDescriptor()
					isMutable -> self.makeSubobjectsImmutable()
				}
				check(result)
				return result
			}
			if (self.slot(BIN_SLOT_AT_, (index shl 1) - 1).equals(key))
			{
				val oldValue = self.slot(BIN_SLOT_AT_, index shl 1)
				if (oldValue.equals(value))
				{
					// The (key,value) pair is present.
					if (isMutable)
					{
						// This may seem silly, but a common usage pattern is to
						// have a map whose values are sets.  Some key is looked
						// up, the value (set) is modified destructively, and
						// the resulting set is written back to the map.  If we
						// didn't clear the values hash here, it would stay
						// wrong after this compound operation.
						self.setSlot(VALUES_HASH_OR_ZERO, 0)
						self.setSlot(BIN_VALUE_UNION_KIND_OR_NIL, nil)
						// No need to clear the key union kind, since the keys
						// didn't change.
						if (!canDestroy)
						{
							self.makeImmutable()
						}
					}
					check(self)
					return self
				}
				// The key is present with a different value.
				val newBin: AvailObject =
					when
					{
						canDestroy && isMutable -> self
						else ->
						{
							if (isMutable) self.makeSubobjectsImmutable()
							newLike(descriptorFor(MUTABLE, level), self, 0, 0)
						}
					}
				newBin.setSlot(BIN_SLOT_AT_, index shl 1, value)
				newBin.setSlot(VALUES_HASH_OR_ZERO, 0)
				// The keys didn't change.
				// newBin.setSlot(BIN_KEY_UNION_KIND_OR_NIL, nil);
				newBin.setSlot(BIN_VALUE_UNION_KIND_OR_NIL, nil)
				check(newBin)
				return newBin
			}
			index++
		}
	}

	/**
	 * Remove the key from the bin object, if present. Answer the resulting bin.
	 * The bin may be modified if it's mutable and canDestroy.
	 */
	override fun o_MapBinRemoveKeyHashCanDestroy(
		self: AvailObject,
		key: A_BasicObject,
		keyHash: Int,
		canDestroy: Boolean
	): A_MapBin {
		check(self)
		val oldSize = entryCount(self)
		var index = 1
		while (true)
		{
			index = self.intLinearSearch(
				KEY_HASHES_AREA_, index, oldSize, keyHash)
			if (index == 0)
			{
				// The key was not found.
				if (!canDestroy) self.makeImmutable()
				check(self)
				return self
			}
			if (self.slot(BIN_SLOT_AT_, (index shl 1) - 1).equals(key))
			{
				// Found the key.
				if (oldSize == 1) return emptyLinearMapBin(level)
				val result = newLike(
					descriptorFor(MUTABLE, level),
					self,
					-2,
					-(oldSize and 1)) // Reduce size only if it was odd
				if (index < oldSize) {
					result.setIntSlot(
						KEY_HASHES_AREA_,
						index,
						self.intSlot(KEY_HASHES_AREA_, oldSize))
					result.setSlot(
						BIN_SLOT_AT_,
						(index shl 1) - 1,
						self.slot(BIN_SLOT_AT_, (oldSize shl 1) - 1))
					result.setSlot(
						BIN_SLOT_AT_,
						index shl 1,
						self.slot(BIN_SLOT_AT_, oldSize shl 1))
				}
				// Adjust keys hash by the removed key.
				result.setSlot(KEYS_HASH, self.slot(KEYS_HASH) - keyHash)
				result.setSlot(VALUES_HASH_OR_ZERO, 0)
				result.setSlot(BIN_KEY_UNION_KIND_OR_NIL, nil)
				result.setSlot(BIN_VALUE_UNION_KIND_OR_NIL, nil)
				if (!canDestroy) {
					result.makeSubobjectsImmutable()
				}
				check(result)
				return result
			}
			index++
		}
	}

	override fun o_MapBinAtHashReplacingLevelCanDestroy(
		self: AvailObject,
		key: AvailObject,
		keyHash: Int,
		notFoundValue: AvailObject,
		myLevel: Int,
		canDestroy: Boolean,
		transformer: (AvailObject, AvailObject) -> A_BasicObject
	): A_MapBin {
		// Associate the key and value in this bin, potentially modifying it if
		// canDestroy and it's mutable.  Answer the new bin.  Note that the
		// client is responsible for marking the key and value as immutable if
		// other references exist.
		assert(myLevel == level)
		val oldSize = entryCount(self)
		var index = 0
		do
		{
			index = self.intLinearSearch(
				KEY_HASHES_AREA_, index + 1, oldSize, keyHash)
			if (index == 0)
			{
				// It's not present, so grow the list.  Keep it simple for now
				// by always replacing the list.  This call will scan the linear
				// bin one more time, but at least it's a leaf.
				return self.mapBinAtHashPutLevelCanDestroy(
					key,
					keyHash,
					transformer(key, notFoundValue),
					myLevel,
					canDestroy)
			}
		}
		while (!self.slot(BIN_SLOT_AT_, (index shl 1) - 1).equals(key))

		// The key is present.
		val oldValue = self.slot(BIN_SLOT_AT_, index shl 1)
		val newValue = transformer(key, oldValue)
		if (newValue === oldValue)
		{
			if (isMutable)
			{
				self.setSlot(VALUES_HASH_OR_ZERO, 0)
				// The keys didn't change.
				// newBin.setSlot(BIN_KEY_UNION_KIND_OR_NIL, nil);
				self.setSlot(BIN_VALUE_UNION_KIND_OR_NIL, nil)
				if (!canDestroy) self.makeImmutable()
			}
			return self
		}
		val newBin: AvailObject = when
		{
			canDestroy && isMutable -> self
			else ->
			{
				if (isMutable) self.makeSubobjectsImmutable()
				newLike(descriptorFor(MUTABLE, level), self, 0, 0)
			}
		}
		newBin.setSlot(BIN_SLOT_AT_, index shl 1, newValue)
		newBin.setSlot(VALUES_HASH_OR_ZERO, 0)
		// The keys didn't change.
		// newBin.setSlot(BIN_KEY_UNION_KIND_OR_NIL, nil);
		newBin.setSlot(BIN_VALUE_UNION_KIND_OR_NIL, nil)
		check(newBin)
		return newBin
	}

	/**
	 * Compute this bin's key type, hoisted up to the nearest kind.
	 *
	 * @param self
	 *   The linear map bin to scan.
	 * @return
	 *   The union of the kinds of this bin's keys.
	 */
	private fun computeKeyKind(self: AvailObject): A_Type {
		var keyType = (1 until (entryCount(self) shl 1) step 2).fold(bottom) {
			union, i ->
			union.typeUnion(self.slot(BIN_SLOT_AT_, i).kind())
		}
		if (isShared) {
			keyType = keyType.makeShared()
		}
		return keyType
	}

	/**
	 * Compute and install the bin key union kind for the specified linear map
	 * bin.
	 *
	 * @param self
	 *   A linear map bin.
	 * @return
	 *   The union of the key types as a kind.
	 */
	private fun mapBinKeyUnionKind(self: AvailObject): A_Type {
		var keyType: A_Type = self.slot(BIN_KEY_UNION_KIND_OR_NIL)
		if (keyType.isNil) {
			keyType = computeKeyKind(self)
			self.setSlot(BIN_KEY_UNION_KIND_OR_NIL, keyType)
		}
		return keyType
	}

	override fun o_MapBinKeyUnionKind(self: AvailObject): A_Type =
		self.synchronizeIf(isShared) { mapBinKeyUnionKind(self) }

	/**
	 * Compute this bin's value type, hoisted up to the nearest kind.
	 *
	 * @param self
	 *   The linear map bin to scan.
	 * @return
	 *   The union of the kinds of this bin's values.
	 */
	private fun computeValueKind(self: AvailObject): A_Type {
		var valueType = (2 .. (entryCount(self) shl 1) step 2).fold(bottom) {
			union, i ->
			union.typeUnion(self.slot(BIN_SLOT_AT_, i).kind())
		}
		if (isShared) {
			valueType = valueType.makeShared()
		}
		return valueType
	}

	/**
	 * Compute and install the bin value union kind for the specified linear map
	 * bin.
	 *
	 * @param self
	 *   The linear map bin.
	 * @return
	 *   The union of the kinds of this bin's values.
	 */
	private fun mapBinValueUnionKind(self: AvailObject): A_Type {
		var valueType: A_Type = self.slot(BIN_VALUE_UNION_KIND_OR_NIL)
		if (valueType.isNil) {
			valueType = computeValueKind(self)
			self.setSlot(BIN_VALUE_UNION_KIND_OR_NIL, valueType)
		}
		return valueType
	}

	override fun o_MapBinValueUnionKind(self: AvailObject): A_Type =
		self.synchronizeIf(isShared) { mapBinValueUnionKind(self) }

	override fun o_MapBinValuesHash(self: AvailObject): Int =
		self.synchronizeIf(isShared) { mapBinValuesHash(self) }

	override fun o_MapBinIterable(self: AvailObject): MapIterable
	{
		return object : MapIterable() {
			/** A countdown of entry indices. */
			var index = entryCount(self)

			override fun next(): MapDescriptor.Entry {
				if (index < 1) {
					throw NoSuchElementException()
				}
				entry.setKeyAndHashAndValue(
					self.slot(BIN_SLOT_AT_, (index shl 1) - 1),
					self.intSlot(KEY_HASHES_AREA_, index),
					self.slot(BIN_SLOT_AT_, index shl 1))
				index--
				return entry
			}

			override fun hasNext() = index >= 1
		}
	}

	companion object {
		/**
		 * Debugging flag to force deep, expensive consistency checks.
		 */
		private const val shouldCheckConsistency = false

		/**
		 * When a [linear&#32;bin][LinearMapBinDescriptor] reaches this many
		 * entries and it's not already at the bottom allowable level
		 * ([numberOfLevels] - 1) of the hash tree, then convert it to a hashed
		 * bin.
		 */
		const val thresholdToHash = 50

		/**
		 * Check this linear map bin for internal consistency.
		 *
		 * @param self
		 *   A linear map bin.
		 */
		fun check(self: AvailObject) {
			@Suppress("ConstantConditionIf")
			if (shouldCheckConsistency) {
				assert(self.descriptor() is LinearMapBinDescriptor)
				val numObjectSlots = self.variableObjectSlotsCount()
				assert(numObjectSlots and 1 == 0)
				val numEntries = numObjectSlots shr 1
				assert(numEntries == entryCount(self))
				val numIntegerSlots = self.variableIntegerSlotsCount()
				assert(numIntegerSlots == numEntries + 1 shr 1)
				var computedKeyHashSum = 0
				var computedValueHashSum = 0
				for (i in 1..numEntries) {
					val keyHash = self.intSlot(KEY_HASHES_AREA_, i)
					val key = self.slot(BIN_SLOT_AT_, (i shl 1) - 1)
					val value = self.slot(BIN_SLOT_AT_, i shl 1)
					assert(key.hash() == keyHash)
					computedKeyHashSum += keyHash
					computedValueHashSum += value.hash()
				}
				assert(self.slot(KEYS_HASH) == computedKeyHashSum)
				val storedValueHashSum = self.slot(VALUES_HASH_OR_ZERO)
				assert(storedValueHashSum == 0
					|| storedValueHashSum == computedValueHashSum)
			}
		}

		/**
		 * Answer how many <key, value> pairs are present in the given linear
		 * map bin.
		 *
		 * @param self
		 *   An [AvailObject] whose descriptor is a [LinearMapBinDescriptor].
		 * @return
		 *   The number of entries in the bin.
		 */
		fun entryCount(self: AvailObject) =
			self.variableObjectSlotsCount() shr 1

		/**
		 * Lazily compute and install the hash of the values within the
		 * specified linear map bin.
		 *
		 * @param self
		 *   The linear map bin.
		 * @return
		 *   The bin's value hash.
		 */
		private fun mapBinValuesHash(self: AvailObject): Int {
			var valuesHash = self.slot(VALUES_HASH_OR_ZERO)
			if (valuesHash == 0) {
				(2..(entryCount(self) shl 1) step 2).forEach {
					valuesHash += self.slot(BIN_SLOT_AT_, it).hash()
				}
				self.setSlot(VALUES_HASH_OR_ZERO, valuesHash)
			}
			return valuesHash
		}

		/**
		 * Create a map bin with nothing in it.
		 *
		 * @param myLevel
		 *   The level at which to label the bin.
		 * @return
		 *   The new bin with only <key,value> in it.
		 */
		private fun createEmptyLinearMapBin(
			myLevel: Int
		): AvailObject {
			val bin = newObjectIndexedIntegerIndexedDescriptor(
				0, 0, descriptorFor(MUTABLE, myLevel))
			bin.setSlot(KEYS_HASH, 0)
			bin.setSlot(VALUES_HASH_OR_ZERO, 0)
			bin.setSlot(BIN_KEY_UNION_KIND_OR_NIL, bottom)
			bin.setSlot(BIN_VALUE_UNION_KIND_OR_NIL, bottom)
			check(bin)
			return bin
		}

		/**
		 * Create a bin with a single (key,value) pair in it.
		 *
		 * @param key
		 *   The key to include in the bin.
		 * @param keyHash
		 *   The hash of the key, precomputed for performance.
		 * @param value
		 *   The value to include in the bin.
		 * @param myLevel
		 *   The level at which to label the bin.
		 * @return
		 *   The new bin with only <key,value> in it.
		 */
		fun createSingleLinearMapBin(
			key: A_BasicObject,
			keyHash: Int,
			value: A_BasicObject,
			myLevel: Int
		): AvailObject {
			val bin = newObjectIndexedIntegerIndexedDescriptor(
				2, 1, descriptorFor(MUTABLE, myLevel))
			bin.setSlot(KEYS_HASH, keyHash)
			bin.setSlot(VALUES_HASH_OR_ZERO, 0)
			bin.setSlot(BIN_KEY_UNION_KIND_OR_NIL, nil)
			bin.setSlot(BIN_VALUE_UNION_KIND_OR_NIL, nil)
			bin.setIntSlot(KEY_HASHES_AREA_, 1, keyHash)
			bin.setSlot(BIN_SLOT_AT_, 1, key)
			bin.setSlot(BIN_SLOT_AT_, 2, value)
			check(bin)
			return bin
		}

		/**
		 * The number of distinct levels at which
		 * [linear&#32;bins][LinearMapBinDescriptor] may occur.
		 */
		private const val numberOfLevels: Int = 8

		/**
		 * The [LinearMapBinDescriptor] instances.  Each [Array] is indexed by
		 * level.
		 */
		private val descriptors = EnumMap.enumMap { mut: Mutability ->
			Array(numberOfLevels) { level ->
				LinearMapBinDescriptor(mut, level)
			}
		}

		/**
		 * Answer a suitable descriptor for a linear bin with the specified
		 * mutability and at the specified level.
		 *
		 * @param flag
		 *   The desired [mutability][Mutability].
		 * @param level
		 *   The level for the bins using the descriptor.
		 * @return
		 *   The descriptor with the requested properties.
		 */
		private fun descriptorFor(
			flag: Mutability,
			level: Int
		): LinearMapBinDescriptor {
			assert(level in 0 until numberOfLevels)
			return descriptors[flag]!![level]
		}

		/**
		 * The canonical array of empty linear map bins, one for each level.
		 */
		private val emptyBins = Array(numberOfLevels) {
			createEmptyLinearMapBin(it).makeShared()
		}

		/**
		 * Answer an empty linear map bin for the specified level.
		 *
		 * @param level
		 *   The level at which this map bin occurs.
		 * @return
		 *   An empty map bin.
		 */
		fun emptyLinearMapBin(level: Int) = emptyBins[level]
	}

	override fun mutable() = descriptorFor(MUTABLE, level)

	override fun immutable() = descriptorFor(IMMUTABLE, level)

	override fun shared() = descriptorFor(SHARED, level)
}
