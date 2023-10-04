/*
 * TwentyOneBitStringDescriptor.kt
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
package avail.descriptor.tuples

import avail.annotations.HideFieldInDebugger
import avail.descriptor.character.A_Character
import avail.descriptor.character.A_Character.Companion.codePoint
import avail.descriptor.character.A_Character.Companion.isCharacter
import avail.descriptor.character.CharacterDescriptor.Companion.computeHashOfCharacterWithCodePoint
import avail.descriptor.character.CharacterDescriptor.Companion.fromCodePoint
import avail.descriptor.character.CharacterDescriptor.Companion.maxCodePointInt
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.multiplier
import avail.descriptor.representation.AvailObjectRepresentation.Companion.newLike
import avail.descriptor.representation.BitField
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.Mutability.MUTABLE
import avail.descriptor.tuples.A_String.Companion.asNativeString
import avail.descriptor.tuples.A_Tuple.Companion.compareFromToWithTwentyOneBitStringStartingAt
import avail.descriptor.tuples.A_Tuple.Companion.concatenateWith
import avail.descriptor.tuples.A_Tuple.Companion.copyAsMutableObjectTuple
import avail.descriptor.tuples.A_Tuple.Companion.treeTupleLevel
import avail.descriptor.tuples.A_Tuple.Companion.tupleAtPuttingCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.tupleCodePointAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ByteStringDescriptor.Companion.generateByteString
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.TreeTupleDescriptor.Companion.concatenateAtLeastOneTree
import avail.descriptor.tuples.TreeTupleDescriptor.Companion.createTwoPartTreeTuple
import avail.descriptor.tuples.TwentyOneBitStringDescriptor.IntegerSlots.Companion.HASH_OR_ZERO
import avail.descriptor.tuples.TwentyOneBitStringDescriptor.IntegerSlots.RAW_LONGS_
import avail.descriptor.tuples.TwoByteStringDescriptor.Companion.generateTwoByteString
import kotlin.math.max
import kotlin.math.min

/**
 * A [tuple][TupleDescriptor] implementation that consists entirely of Unicode
 * characters.  Since each character is in the range U+0000..U+10FFFF, they're
 * encoded in 21 bits. which leaves room for three inside each 64-bit slot.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property unusedEntriesOfLastLong
 *   The number of entries that are unused in the last [Long] slot. Must be
 *   between 0 and 2.
 * @constructor
 *   Construct a new `TwoByteStringDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param unusedEntriesOfLastLong
 *   The number of entries that are unused in the last [Long] slot. Must be
 *   between 0 and 2.
 */
class TwentyOneBitStringDescriptor private constructor(
	mutability: Mutability,
	val unusedEntriesOfLastLong: Int
) : StringDescriptor(mutability, null, IntegerSlots::class.java)
{
	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum
	{
		/**
		 * The low 32 bits are used for the [HASH_OR_ZERO], but the upper 32 can
		 * be used by other [BitField]s in subclasses of [TupleDescriptor].
		 */
		@HideFieldInDebugger
		HASH_AND_MORE,

		/**
		 * The raw 64-bit ([Long]s) that constitute the representation of the
		 * string of 21-bit codepoints.  Each long contains up to three
		 * codepoints occupying 21 bits each, in little-endian order.  Only the
		 * last long can be incomplete, and is required to have zeros for the
		 * unused elements.  The descriptor instances include the field
		 * [unusedEntriesOfLastLong], which indicates how many (0-2) of the
		 * 21-bit subfields of the last long are unused.
		 */
		RAW_LONGS_;

		companion object
		{
			/**
			 * A slot to hold the cached hash value of a tuple.  If zero, then
			 * the hash value must be computed upon request.  Note that in the
			 * very rare case that the hash value actually equals zero, the hash
			 * value has to be computed every time it is requested.
			 */
			val HASH_OR_ZERO = BitField(HASH_AND_MORE, 0, 32) { null }

			init
			{
				assert(TupleDescriptor.IntegerSlots.HASH_AND_MORE.ordinal
					== HASH_AND_MORE.ordinal)
			}
		}
	}

	override fun o_AppendCanDestroy(
		self: AvailObject,
		newElement: A_BasicObject,
		canDestroy: Boolean): A_Tuple
	{
		val originalSize = self.tupleSize
		if (originalSize >= maximumCopySize ||
			!(newElement as A_Character).isCharacter)
		{
			// Transition to a tree tuple.
			val singleton = tuple(newElement)
			return self.concatenateWith(singleton, canDestroy)
		}
		val intValue: Int = newElement.codePoint
		val newSize = originalSize + 1
		if (isMutable && canDestroy && originalSize.mod3 != 0)
		{
			// Enlarge it in place, using more of the final partial long field.
			self.setDescriptor(descriptorFor(MUTABLE, newSize))
			set21BitSlot(self, newSize, intValue)
			self[HASH_OR_ZERO] = 0
			return self
		}
		// Copy to a potentially larger TwentyOneBitStringDescriptor.
		val result = newLike(
			descriptorFor(MUTABLE, newSize),
			self,
			0,
			if (originalSize and 3 == 0) 1 else 0)
		result.setShortSlot(RAW_LONGS_, newSize, intValue)
		result[HASH_OR_ZERO] = 0
		return result
	}

	override fun o_CompareFromToWithStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anotherObject: A_Tuple,
		startIndex2: Int
	): Boolean =
		anotherObject.compareFromToWithTwentyOneBitStringStartingAt(
			startIndex2,
			startIndex2 + endIndex1 - startIndex1,
			self,
			startIndex1)

	override fun o_CompareFromToWithTwentyOneBitStringStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aTwentyOneBitString: A_String,
		startIndex2: Int): Boolean
	{
		if (self.sameAddressAs(aTwentyOneBitString) && startIndex1 == startIndex2)
		{
			return true
		}
		val strongOtherString = aTwentyOneBitString as AvailObject
		var index2 = startIndex2
		return (startIndex1..endIndex1).all { index1 ->
			get21BitSlot(self, index1) ==
				get21BitSlot(strongOtherString, index2++)
		}
	}

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsTwentyOneBitString(self)

	override fun o_EqualsTwentyOneBitString(
		self: AvailObject,
		aTwentyOneBitString: A_String): Boolean
	{
		// First, check for object-structure (address) identity.
		when
		{
			self.sameAddressAs(aTwentyOneBitString) -> return true
			self.tupleSize != aTwentyOneBitString.tupleSize -> return false
			self.hash() != aTwentyOneBitString.hash() -> return false
			// The longs array *must* be padded with zeros for the last 0-2
			// entries. Compare long-by-long.
			!self.intSlotsCompare(aTwentyOneBitString as AvailObject, RAW_LONGS_) ->
				return false
			// They're equal, but occupy disjoint storage. If possible, replace
			// one with an indirection to the other to keep down the frequency
			// of character comparisons.
			!isShared ->
			{
				aTwentyOneBitString.makeImmutable()
				self.becomeIndirectionTo(aTwentyOneBitString)
			}
			!aTwentyOneBitString.descriptor().isShared ->
			{
				self.makeImmutable()
				aTwentyOneBitString.becomeIndirectionTo(self)
			}
		}
		return true
	}

	override fun o_FirstIndexOf(
		self: AvailObject,
		value: A_BasicObject,
		startIndex: Int,
		endIndex: Int): Int
	{
		if (!(value as A_Character).isCharacter) return 0
		val code = value.codePoint
		val codeLong = code.toLong()
		for (slotIndex in (startIndex + 2).div3 .. (endIndex + 2).div3)
		{
			val slot = self[RAW_LONGS_, slotIndex]
			// Use fast testing for efficiency.
			if (slot and twentyOneBitMask == codeLong
				|| (slot ushr 21) and twentyOneBitMask == codeLong
				|| (slot ushr 42) and twentyOneBitMask == codeLong)
			{
				// There's a hit in the slot, so do a slow scan.
				for (i in max(slotIndex * 3 - 2, startIndex)
					.. min(slotIndex * 3, endIndex))
				{
					if (get21BitSlot(self, i) == code) return i
				}
			}
		}
		return 0
	}

	override fun o_FirstIndexOfOr(
		self: AvailObject,
		value: A_BasicObject,
		otherValue: A_BasicObject,
		startIndex: Int,
		endIndex: Int): Int
	{
		if (!(value as A_Character).isCharacter)
			return o_FirstIndexOf(self, otherValue, startIndex, endIndex)
		if (!(otherValue as A_Character).isCharacter)
			return o_FirstIndexOf(self, value, startIndex, endIndex)
		val code1 = value.codePoint
		val code1Long = code1.toLong()
		val code2 = otherValue.codePoint
		val code2Long = code2.toLong()
		if (code1 == code2)
			return o_FirstIndexOf(self, value, startIndex, endIndex)
		for (slotIndex in (startIndex + 2).div3 .. (endIndex + 2).div3)
		{
			val slot = self[RAW_LONGS_, slotIndex]
			// Use fast testing for efficiency.
			if (slot and twentyOneBitMask == code1Long
				|| slot and twentyOneBitMask == code2Long
				|| (slot ushr 21) and twentyOneBitMask == code1Long
				|| (slot ushr 21) and twentyOneBitMask == code2Long
				|| (slot ushr 42) and twentyOneBitMask == code1Long
				|| (slot ushr 42) and twentyOneBitMask == code2Long)
			{
				// There's a hit in the slot, so do a slow scan.
				for (i in max(slotIndex * 3 - 2, startIndex)
					.. min(slotIndex * 3, endIndex))
				{
					if (get21BitSlot(self, i) == code1) return i
					if (get21BitSlot(self, i) == code2) return i
				}
			}
		}
		return 0
	}

	override fun o_LastIndexOf(
		self: AvailObject,
		value: A_BasicObject,
		startIndex: Int,
		endIndex: Int): Int
	{
		val strongValue = value as A_Character
		if (!strongValue.isCharacter) return 0
		val codePoint = strongValue.codePoint
		for (i in startIndex downTo endIndex)
		{
			if (get21BitSlot(self, i) == codePoint) return i
		}
		return 0
	}

	override fun o_TupleAt(self: AvailObject, index: Int): AvailObject
	{
		// Answer the element at the given index in the tuple object. It's a
		// character.
		assert(index >= 1 && index <= self.tupleSize)
		return fromCodePoint(get21BitSlot(self, index)) as AvailObject
	}

	override fun o_TupleAtPuttingCanDestroy(
		self: AvailObject,
		index: Int,
		newValueObject: A_BasicObject,
		canDestroy: Boolean
	): A_Tuple
	{
		// Answer a tuple with all the elements of object except at the given
		// index we should have newValueObject. This may destroy the original
		// tuple if canDestroy is true.
		assert(index >= 1 && index <= self.tupleSize)
		if ((newValueObject as A_Character).isCharacter)
		{
			val codePoint: Int = newValueObject.codePoint
			val result = if (canDestroy && isMutable)
			{
				self
			}
			else
			{
				// Clone it, then modify the copy in place.
				newLike(mutable(), self, 0, 0)
			}
			set21BitSlot(result, index, codePoint)
			result.setHashOrZero(0)
			return result
		}
		// Convert to a general object tuple instead.
		return self.copyAsMutableObjectTuple().tupleAtPuttingCanDestroy(
			index, newValueObject, true)
	}

	override fun o_TupleCodePointAt(self: AvailObject, index: Int): Int
	{
		assert(index >= 1 && index <= self.tupleSize)
		return get21BitSlot(self, index)
	}

	override fun o_TupleReverse(self: AvailObject): A_Tuple
	{
		val size = self.tupleSize
		return if (size > maximumCopySize)
		{
			super.o_TupleReverse(self)
		}
		else
		{
			// It's reasonably small, so just copy the characters.
			// Just copy the applicable two-byte characters in reverse.
			generateTwentyOneBitString(size) {
				get21BitSlot(self, size + 1 - it)
			}
		}
	}

	override fun o_TupleSize(self: AvailObject): Int =
		self.variableIntegerSlotsCount() * 3 - unusedEntriesOfLastLong

	// Answer approximately how many bits per entry are taken up by this
	// object.
	override fun o_BitsPerEntry(self: AvailObject): Int = 21

	override fun o_ComputeHashFromTo(
		self: AvailObject,
		start: Int,
		end: Int): Int
	{
		// See comment in superclass. This method must produce the same value.
		var hash = 0
		for (index in end downTo start)
		{
			val itemHash = (computeHashOfCharacterWithCodePoint(
				get21BitSlot(self, index))
				xor preToggle)
			hash = (hash + itemHash) * multiplier
		}
		return hash
	}

	override fun o_MarshalToJava(
		self: AvailObject,
		classHint: Class<*>?): Any = self.asNativeString()

	override fun o_ConcatenateWith(
		self: AvailObject,
		otherTuple: A_Tuple,
		canDestroy: Boolean): A_Tuple
	{
		val size1 = self.tupleSize
		if (size1 == 0)
		{
			if (!canDestroy)
			{
				otherTuple.makeImmutable()
			}
			return otherTuple
		}
		val size2 = otherTuple.tupleSize
		if (size2 == 0)
		{
			if (!canDestroy)
			{
				self.makeImmutable()
			}
			return self
		}
		val newSize = size1 + size2
		if (newSize <= maximumCopySize && (otherTuple.isString))
		{
			// Copy the characters.
			val newLongCount = (newSize + 2).div3
			val deltaSlots = newLongCount - self.variableIntegerSlotsCount()
			val result: AvailObject
			if (canDestroy && isMutable && deltaSlots == 0)
			{
				// We can reuse the receiver; it has enough int slots.
				result = self
				result.setDescriptor(descriptorFor(MUTABLE, newSize))
			}
			else
			{
				result = newLike(
					descriptorFor(MUTABLE, newSize), self, 0, deltaSlots)
			}
			var dest = size1 + 1
			var src = 1
			while (src <= size2)
			{
				set21BitSlot(result, dest++, otherTuple.tupleCodePointAt(src++))
			}
			result[HASH_OR_ZERO] = 0
			return result
		}
		if (!canDestroy)
		{
			self.makeImmutable()
			otherTuple.makeImmutable()
		}
		return if (otherTuple.treeTupleLevel == 0)
		{
			createTwoPartTreeTuple(self, otherTuple, 1, 0)
		}
		else
		{
			concatenateAtLeastOneTree(self, otherTuple, true)
		}
	}

	override fun o_CopyTupleFromToCanDestroy(
		self: AvailObject,
		start: Int,
		end: Int,
		canDestroy: Boolean): A_Tuple
	{
		val tupleSize = self.tupleSize
		assert(start in 1..end + 1 && end <= tupleSize)
		val size = end - start + 1
		return when {
			size == 0 -> emptyTuple
			size == tupleSize ->
				// It's the whole string.
				if (canDestroy) self else self.makeImmutable()
			size > maximumCopySize ->
				// Too big for copying, so let the super create a subrange.
				super.o_CopyTupleFromToCanDestroy(self, start, end, canDestroy)
			else ->
			{
				val firstNonByteIndex = (start..end).firstOrNull {
					get21BitSlot(self, it) > 255
				} ?: run {
					// The codepoints are all byte-sized.
					return generateByteString(size) {
						get21BitSlot(self, it + start - 1)
					}
				}
				if ((firstNonByteIndex..end).all {
					get21BitSlot(self, it) <= 0xFFFF
				})
				{
					// The codepoints are all two-byte-sized.
					return generateTwoByteString(size) {
						get21BitSlot(self, it + start - 1).toUShort()
					}
				}
				generateTwentyOneBitString(size) {
					get21BitSlot(self, it + start - 1)
				}
			}
		}
	}

	companion object
	{
		/**
		 * Defined threshold for making copies versus using
		 * [TreeTupleDescriptor] or other forms of reference instead of creating
		 * a new tuple.
		 */
		private const val maximumCopySize = 32

		/**
		 * The static list of descriptors of this kind, organized in such a way
		 * that [descriptorFor] can find them by mutability and number of unused
		 * entries within the last long slot.
		 */
		private val descriptors =
			arrayOfNulls<TwentyOneBitStringDescriptor>(3 * 3)

		/**
		 * Create a new mutable twenty-one-bit string with the specified number
		 * of elements.
		 *
		 * @param size
		 *   The number of elements in the new tuple.
		 * @return
		 *   The new tuple, initialized to null characters (code point 0).
		 */
		private fun mutableTwentyOneBitStringOfSize(size: Int): AvailObject =
			descriptorFor(MUTABLE, size).create((size + 2).div3)

		/**
		 * Answer the descriptor that has the specified mutability flag and is
		 * suitable to describe a tuple with the given number of elements.
		 *
		 * @param flag
		 *   Whether the requested descriptor should be mutable.
		 * @param size
		 *   How many elements are in a tuple to be represented by the
		 *   descriptor.
		 * @return
		 *   A [TwentyOneBitStringDescriptor] suitable for representing a string
		 *   of the given mutability and [size][A_Tuple.tupleSize].
		 */
		private fun descriptorFor(
			flag: Mutability,
			size: Int
		): TwentyOneBitStringDescriptor =
			descriptors[size.mod3 * 3 + flag.ordinal]!!

		/**
		 * Read a lone 21-bit Unicode code point at the specified code point
		 * index in this string.
		 */
		private fun get21BitSlot(
			self: AvailObject,
			index: Int
		): Int
		{
			val slotIndex = (index - 1).div3 + 1
			val shift = (index - 1).mod3 * 21
			val long = self[RAW_LONGS_, slotIndex]
			return (long shr shift).toInt() and ((1 shl 21) - 1)
		}

		/**
		 * Overwrite a lone 21-bit Unicode code point at the specified code
		 * point index in this string.
		 */
		private fun set21BitSlot(
			self: AvailObject,
			index: Int,
			intValue: Int)
		{
			val mask = (1L shl 21) - 1
			assert(intValue <= maxCodePointInt)
			val slotIndex = (index - 1).div3 + 1
			val shift = (index - 1).mod3 * 21
			var long = self[RAW_LONGS_, slotIndex]
			long = (long and mask.inv()) or (intValue.toLong() shl shift)
			self[RAW_LONGS_, slotIndex] = long
		}

		/**
		 * Create an object of the appropriate size, whose descriptor is an
		 * instance of [TwentyOneBitStringDescriptor]. This can store any
		 * Unicode character, including those from the Supplemental Planes. Run
		 * the generator for each position in ascending order to produce the
		 * code points with which to populate the string.
		 *
		 * @param size
		 *   The size of twenty-one-bit string to create.
		 * @param generator
		 *   A generator to provide code points to store.
		 * @return
		 *   The new Avail string.
		 */
		fun generateTwentyOneBitString(
			size: Int,
			generator: (Int)->Int): AvailObject
		{
			val result = mutableTwentyOneBitStringOfSize(size)
			var counter = 1
			val sizeDiv3 = size.div3
			// Aggregate three writes at a time for the bulk of the string.
			for (slotIndex in 1..sizeDiv3)
			{
				result[RAW_LONGS_, slotIndex] =
					generator(counter++).toLong() +
						(generator(counter++).toLong() shl 21) +
						(generator(counter++).toLong() shl 42)
			}
			if (counter <= size)
			{
				// Do the last 1-2 writes the slow way.
				var long = generator(counter++).toLong()
				if (counter <= size)
				{
					long += generator(counter).toLong() shl 21
				}
				result[RAW_LONGS_, sizeDiv3 + 1] = long
			}
			return result
		}

		/**
		 * Answer a mutable copy of object that also only holds 21-bit characters.
		 *
		 * @param self
		 *   A string to copy, in any representation.
		 * @return
		 *   A new twenty-one-bit string with the same content as the argument.
		 */
		fun copyAsMutableTwentyOneBitString(self: AvailObject): A_String =
			generateTwentyOneBitString(self.tupleSize) {
				self.tupleCodePointAt(it)
			}

		init
		{
			var i = 0
			for (excess in intArrayOf(0, 2, 1))
			{
				descriptors[i++] =
					TwentyOneBitStringDescriptor(MUTABLE, excess)
				descriptors[i++] =
					TwentyOneBitStringDescriptor(Mutability.IMMUTABLE, excess)
				descriptors[i++] =
					TwentyOneBitStringDescriptor(Mutability.SHARED, excess)
			}
		}
	}

	override fun mutable(): TwentyOneBitStringDescriptor =
		descriptors[(3 - unusedEntriesOfLastLong).mod3 * 3
			+ MUTABLE.ordinal]!!

	override fun immutable(): TwentyOneBitStringDescriptor =
		descriptors[(3 - unusedEntriesOfLastLong).mod3 * 3
			+ Mutability.IMMUTABLE.ordinal]!!

	override fun shared(): TwentyOneBitStringDescriptor =
		descriptors[(3 - unusedEntriesOfLastLong).mod3 * 3
			+ Mutability.SHARED.ordinal]!!
}

/**
 * The value 2^33 / 3, rounded up, as a [ULong].  This is used for fast division
 * by 3.
 */
private const val thirdMultiplier = 0x0000_0000_AAAA_AAABuL

/** Divide the given non-negative [Int] by 3. */
private val Int.div3: Int get() = (toULong() * thirdMultiplier shr 33).toInt()

/** Calculate the receiver mod 3.  The receiver must be non-negative. */
private val Int.mod3: Int get() = minus(div3 * 3)

/** A [Long] with the lower 21 bits set, and the rest zero. */
private const val twentyOneBitMask: Long = (1L shl 21) - 1L
