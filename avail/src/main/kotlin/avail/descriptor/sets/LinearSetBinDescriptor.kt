/*
 * LinearSetBinDescriptor.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.descriptor.sets

import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObjectRepresentation.Companion.newLike
import avail.descriptor.representation.BitField
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.sets.A_SetBin.Companion.binHasElementWithHash
import avail.descriptor.sets.A_SetBin.Companion.isBinSubsetOf
import avail.descriptor.sets.A_SetBin.Companion.setBinAddingElementHashLevelCanDestroy
import avail.descriptor.sets.A_SetBin.Companion.setBinHash
import avail.descriptor.sets.A_SetBin.Companion.setBinSize
import avail.descriptor.sets.HashedSetBinDescriptor.Companion.createInitializedHashSetBin
import avail.descriptor.sets.LinearSetBinDescriptor.IntegerSlots.Companion.BIN_HASH
import avail.descriptor.sets.LinearSetBinDescriptor.ObjectSlots.BIN_ELEMENT_AT_
import avail.descriptor.sets.SetDescriptor.SetIterator
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.typeUnion
import avail.descriptor.types.TypeTag
import avail.utility.structures.EnumMap.Companion.enumMap

/**
 * A LinearSetBinDescriptor] is a leaf bin in a [set][SetDescriptor]'s hierarchy
 * of bins.  It consists of a small number of distinct elements in no particular
 * order.  If more elements need to be stored, a
 * [hashed][HashedSetBinDescriptor] bin will be used instead.
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
class LinearSetBinDescriptor private constructor(
	mutability: Mutability,
	level: Int
) : SetBinDescriptor(
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
		 * The low 32 bits are used for the [BIN_HASH], but the upper 32 can be
		 * used by other [BitField]s in subclasses.
		 */
		BIN_HASH_AND_MORE;

		companion object {
			/**
			 * A slot to hold the bin's hash value, or zero if it has not been
			 * computed.
			 */
			val BIN_HASH = BitField(BIN_HASH_AND_MORE, 0, 32) { null }

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
		 * The elements of this bin.  The elements are never sub-bins, since
		 * this is a [linear&#32;bin][LinearSetBinDescriptor], a leaf bin.
		 */
		BIN_ELEMENT_AT_
	}

	override fun o_BinElementAt(
		self: AvailObject,
		index: Int
	): AvailObject = self.slot(BIN_ELEMENT_AT_, index)

	override fun o_SetBinAddingElementHashLevelCanDestroy(
		self: AvailObject,
		elementObject: A_BasicObject,
		elementObjectHash: Int,
		myLevel: Int,
		canDestroy: Boolean
	): A_SetBin
	{
		// Add the given element to this bin, potentially modifying it if
		// canDestroy and it's mutable.  Answer the new bin.  Note that the
		// client is responsible for marking elementObject as immutable if
		// another reference exists.
		checkBinHash(self)
		assert(myLevel == level)
		if (self.binHasElementWithHash(elementObject, elementObjectHash)) {
			if (!canDestroy) {
				self.makeImmutable()
			}
			return self
		}
		// It's not present, so grow the list.  Keep it simple for now by always
		// replacing the list.
		val oldSize = self.variableObjectSlotsCount()
		if (oldSize == 0) {
			// Bin transitioned from empty to single, but every object can act
			// as a singleton set bin.
			return elementObject as A_SetBin
		}
		val oldHash = self.setBinHash
		val result: AvailObject
		if (myLevel >= HashedSetBinDescriptor.numberOfLevels - 1
			|| oldSize < thresholdToHash)
		{
			// Make a slightly larger linear bin and populate it.
			result = newLike(
				descriptorFor(Mutability.MUTABLE, level), self, 1, 0)
			result.setSlot(BIN_ELEMENT_AT_, oldSize + 1, elementObject)
			result.setSlot(BIN_HASH, oldHash + elementObjectHash)
			when {
				!isMutable -> { }
				canDestroy -> self.destroy()
				else -> self.makeSubobjectsImmutable()
			}
			checkBinHash(result)
			return result
		}
		val shift = 6 * myLevel
		assert(shift < 32)
		var bitPosition = elementObjectHash ushr shift and 63
		var bitVector = 1L shl bitPosition
		for (i in 1..oldSize) {
			val element: A_BasicObject = self.slot(BIN_ELEMENT_AT_, i)
			bitPosition = element.hash() ushr shift and 63
			bitVector = bitVector or (1L shl bitPosition)
		}
		val newLocalSize: Int = java.lang.Long.bitCount(bitVector)
		result = createInitializedHashSetBin(myLevel, newLocalSize, bitVector)
		result.setBinAddingElementHashLevelCanDestroy(
			elementObject, elementObjectHash, myLevel, true)
		for (i in 1..oldSize) {
			val eachElement: A_BasicObject = self.slot(BIN_ELEMENT_AT_, i)
			val localAddResult = result.setBinAddingElementHashLevelCanDestroy(
				eachElement, eachElement.hash(), myLevel, true)
			assert(localAddResult.sameAddressAs(result)) {
				"The element should have been added without reallocation"
			}
		}
		assert(result.setBinSize == oldSize + 1)
		assert(self.setBinHash == oldHash)
		val newHash = oldHash + elementObjectHash
		assert(result.setBinHash == newHash)
		HashedSetBinDescriptor.checkHashedSetBin(result)
		return result
	}

	override fun o_BinHasElementWithHash(
		self: AvailObject,
		elementObject: A_BasicObject,
		elementObjectHash: Int
	): Boolean = (1..self.variableObjectSlotsCount()).any {
		val element = self.slot(BIN_ELEMENT_AT_, it)
		element.hash() == elementObjectHash && elementObject.equals(element)
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
		val oldSize = self.variableObjectSlotsCount()
		(1..oldSize).forEach { searchIndex ->
			if (self.slot(BIN_ELEMENT_AT_, searchIndex).equals(elementObject)) {
				if (oldSize == 2) {
					val survivor = self.slot(BIN_ELEMENT_AT_, 3 - searchIndex)
					if (!canDestroy) {
						survivor.makeImmutable()
					}
					return survivor
				}
				// Produce a smaller copy, truncating the last entry, then
				// replace the found element with the last entry of the original
				// bin.  Note that this changes the (irrelevant) order.
				val oldHash = self.slot(BIN_HASH)
				val result = newLike(
					descriptorFor(Mutability.MUTABLE, level), self, -1, 0)
				if (searchIndex != oldSize) {
					result.setSlot(
						BIN_ELEMENT_AT_,
						searchIndex,
						self.slot(BIN_ELEMENT_AT_, oldSize))
				}
				result.setSlot(BIN_HASH, oldHash - elementObjectHash)
				if (!canDestroy) {
					result.makeSubobjectsImmutable()
				}
				checkBinHash(result)
				return result
			}
		}
		if (!canDestroy) {
			self.makeImmutable()
		}
		return self
	}

	/**
	 * Check if object, a bin, holds a subset of aSet's elements.
	 */
	override fun o_IsBinSubsetOf(
		self: AvailObject,
		potentialSuperset: A_Set
	): Boolean {
		return (1..self.variableObjectSlotsCount()).all {
			self.slot(BIN_ELEMENT_AT_, it).isBinSubsetOf(potentialSuperset)
		}
	}

	/**
	 * Answer how many elements this bin contains.
	 */
	override fun o_SetBinSize(self: AvailObject): Int =
		self.variableObjectSlotsCount()

	override fun o_BinUnionKind(self: AvailObject): A_Type {
		// Answer the nearest kind of the union of the types of this bin's
		// elements. I'm supposed to be small, so recalculate it per request.
		var unionKind = self.slot(BIN_ELEMENT_AT_, 1).kind()
		for (index in 2..self.variableObjectSlotsCount()) {
			unionKind = unionKind.typeUnion(
				self.slot(BIN_ELEMENT_AT_, index).kind())
		}
		return unionKind
	}

	override fun o_BinElementsAreAllInstancesOfKind(
		self: AvailObject,
		kind: A_Type
	): Boolean = (1..self.variableObjectSlotsCount()).all {
		self.slot(BIN_ELEMENT_AT_, it).isInstanceOfKind(kind)
	}

	override fun o_SetBinIterator(self: AvailObject): SetIterator =
		object : SetIterator() {
			/** The next one-based index to visit.  Start at the high index. */
			var index = self.variableObjectSlotsCount()

			override fun next(): AvailObject {
				if (index < 1)
					throw NoSuchElementException()
				return self.slot(BIN_ELEMENT_AT_, index--)
			}

			override fun hasNext() = index >= 1
		}

	override fun mutable() =
		descriptorFor(Mutability.MUTABLE, level)

	override fun immutable() =
		descriptorFor(Mutability.IMMUTABLE, level)

	override fun shared() =
		descriptorFor(Mutability.SHARED, level)

	companion object {
		/**
		 * When a [linear&#32;bin][LinearSetBinDescriptor] reaches this many
		 * entries and it's not already at the bottom allowable level
		 * ([HashedSetBinDescriptor.numberOfLevels] - 1) of the hash tree, then
		 * convert it to a hashed bin.
		 */
		const val thresholdToHash = 10

		/** Whether to do sanity checks on linear set bins' hashes. */
		private const val checkBinHashes = false

		/**
		 * Check that this linear bin is hashed correctly.
		 *
		 * @param self
		 *   A linear set bin.
		 */
		private fun checkBinHash(self: AvailObject) {
			@Suppress("ConstantConditionIf")
			if (checkBinHashes) {
				assert(self.descriptor() is LinearSetBinDescriptor)
				val stored = self.setBinHash
				var calculated = 0
				for (i in self.variableObjectSlotsCount() downTo 1) {
					val subBin = self.slot(BIN_ELEMENT_AT_, i)
					val subBinHash = subBin.hash()
					calculated += subBinHash
				}
				assert(calculated == stored) { "Failed bin hash cross-check" }
			}
		}

		/**
		 * Create a mutable 2-element linear bin at the specified level and with
		 * the specified elements. The caller is responsible for making the
		 * elements immutable if necessary.  The caller should also ensure the
		 * values are not equal.
		 *
		 * @param level
		 *   The level of the new bin.
		 * @param firstElement
		 *   The first element of the new bin.
		 * @param secondElement
		 *   The second element of the new bin.
		 * @return
		 *   A 2-element set bin.
		 */
		fun createLinearSetBinPair(
			level: Int,
			firstElement: A_BasicObject,
			secondElement: A_BasicObject
		): AvailObject =
			descriptorFor(Mutability.MUTABLE, level).create(2) {
				setSlot(BIN_ELEMENT_AT_, 1, firstElement)
				setSlot(BIN_ELEMENT_AT_, 2, secondElement)
				setSlot(BIN_HASH, firstElement.hash() + secondElement.hash())
				checkBinHash(this)
			}

		/**
		 * The [LinearSetBinDescriptor] instances.  Each [Array] is indexed by
		 * level.
		 */
		private val descriptors = enumMap { mut: Mutability ->
			Array(HashedSetBinDescriptor.numberOfLevels) { level ->
				LinearSetBinDescriptor(mut, level)
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
		): LinearSetBinDescriptor {
			assert(level in 0 until HashedSetBinDescriptor.numberOfLevels)
			return descriptors[flag]!![level]
		}

		/**
		 * The canonical array of empty bins, one for each level.
		 */
		private val emptyBins =
			Array(HashedSetBinDescriptor.numberOfLevels) {
				descriptorFor(Mutability.MUTABLE, it).createShared(0) {
					setSlot(BIN_HASH, 0)
				}
			}

		/**
		 * Answer an empty bin for the specified level.
		 *
		 * @param level
		 *   The level at which this bin occurs.
		 * @return
		 *   An empty bin.
		 */
		fun emptyLinearSetBin(level: Int): AvailObject = emptyBins[level]

		/**
		 * Generate a linear set bin by extracting the specified number of
		 * values from the generator.  The values might not necessarily be
		 * unique, so reduce them.  If they are all the same value, answer the
		 * value itself as a singleton bin.
		 *
		 * Each element is compared against all the others to detect duplicates
		 * while populating the bin.  If any duplicates are detected, a copy is
		 * made of the populated prefix of the bin.
		 *
		 * @param level
		 *   The level of the bin to create.
		 * @param size
		 *   The number of values to extract from the generator.
		 * @param generator
		 *   A generator to provide [AvailObject]s to store.
		 * @return
		 *   A top-level linear set bin.
		 */
		fun generateLinearSetBinFrom(
			level: Int,
			size: Int,
			generator: (Int)->A_BasicObject
		): AvailObject {
			val hashes = IntArray(size)
			var written = 0
			val bin = descriptorFor(Mutability.MUTABLE, level).create(size) {
				var hash = 0
				outer@ for (i in 1 .. size)
				{
					val element = generator(i)
					val elementHash = element.hash()
					for (j in 1 .. written)
					{
						if (hashes[j - 1] == elementHash
							&& element.equals(slot(BIN_ELEMENT_AT_, j)))
						{
							continue@outer
						}
					}
					hashes[written] = elementHash  //zero-based.
					setSlot(BIN_ELEMENT_AT_, ++written, element)
					hash += elementHash
				}
				setSlot(BIN_HASH, hash)
			}
			return when (written) {
				1 -> bin.slot(BIN_ELEMENT_AT_, 1)
				size -> bin
				else -> newLike(bin.descriptor(), bin, written - size, 0)
			}
		}
	}
}
