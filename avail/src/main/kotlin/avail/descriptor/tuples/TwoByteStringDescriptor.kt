/*
 * TwoByteStringDescriptor.kt
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
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObjectRepresentation.Companion.newLike
import avail.descriptor.representation.BitField
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.tuples.A_Tuple.Companion.compareFromToWithTwoByteStringStartingAt
import avail.descriptor.tuples.A_Tuple.Companion.concatenateWith
import avail.descriptor.tuples.A_Tuple.Companion.copyAsMutableObjectTuple
import avail.descriptor.tuples.A_Tuple.Companion.treeTupleLevel
import avail.descriptor.tuples.A_Tuple.Companion.tupleAtPuttingCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.tupleCodePointAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ByteStringDescriptor.Companion.generateByteString
import avail.descriptor.tuples.NybbleTupleDescriptor.Companion.mutableObjectOfSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.TreeTupleDescriptor.Companion.concatenateAtLeastOneTree
import avail.descriptor.tuples.TreeTupleDescriptor.Companion.createTwoPartTreeTuple
import avail.descriptor.tuples.TwoByteStringDescriptor.IntegerSlots
import avail.descriptor.tuples.TwoByteStringDescriptor.IntegerSlots.Companion.HASH_OR_ZERO
import avail.descriptor.tuples.TwoByteStringDescriptor.IntegerSlots.RAW_LONGS_
import avail.optimizer.jvm.CheckedMethod
import avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import avail.optimizer.jvm.ReferencedInGeneratedCode

/**
 * A [tuple][TupleDescriptor] implementation that consists entirely of two-byte characters.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property unusedShortsOfLastLong
 *   The number of shorts that are unused in the last [long
 *   slot][IntegerSlots.RAW_LONGS_]. Must be between 0 and 3.
 * @constructor
 *   Construct a new `TwoByteStringDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param unusedShortsOfLastLong
 *   The number of shorts that are unused in the last [RAW_LONGS_] slot. Must be
 *   between 0 and 3.
 */
class TwoByteStringDescriptor private constructor(
	mutability: Mutability,
	var unusedShortsOfLastLong: Int) : StringDescriptor(
		mutability, null, IntegerSlots::class.java)
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
		 * The raw 64-bit (`long`s) that constitute the representation of the
		 * string of two-byte characters.  Each long contains up to four
		 * characters occupying 16 bits each, in little-endian order.  Only the
		 * last long can be incomplete, and is required to have zeros for the
		 * unused elements.  The descriptor instances include the field
		 * [unusedShortsOfLastLong], which indicates how many (0-3) of the
		 * 16-bit subfields of the last long are unused.
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
		if (intValue and 0xFFFF.inv() != 0)
		{
			// Transition to a tree tuple.
			val singleton = tuple(newElement)
			return self.concatenateWith(singleton, canDestroy)
		}
		val newSize = originalSize + 1
		if (isMutable && canDestroy && originalSize and 3 != 0)
		{
			// Enlarge it in place, using more of the final partial int field.
			self.setDescriptor(descriptorFor(Mutability.MUTABLE, newSize))
			self.setShortSlot(RAW_LONGS_, newSize, intValue)
			self.setSlot(HASH_OR_ZERO, 0)
			return self
		}
		// Copy to a potentially larger TwoByteStringDescriptor.
		val result = newLike(
			descriptorFor(Mutability.MUTABLE, newSize),
			self,
			0,
			if (originalSize and 3 == 0) 1 else 0)
		result.setShortSlot(RAW_LONGS_, newSize, intValue)
		result.setSlot(HASH_OR_ZERO, 0)
		return result
	}

	override fun o_CompareFromToWithStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anotherObject: A_Tuple,
		startIndex2: Int): Boolean =
			anotherObject.compareFromToWithTwoByteStringStartingAt(
				startIndex2,
				startIndex2 + endIndex1 - startIndex1,
				self,
				startIndex1)

	override fun o_CompareFromToWithTwoByteStringStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aTwoByteString: A_String,
		startIndex2: Int): Boolean
	{
		if (self.sameAddressAs(aTwoByteString) && startIndex1 == startIndex2)
		{
			return true
		}
		val strongOtherString = aTwoByteString as AvailObject
		var index2 = startIndex2
		return (startIndex1..endIndex1).all { index1 ->
			self.shortSlot(RAW_LONGS_, index1) ==
				strongOtherString.shortSlot(RAW_LONGS_, index2++)
		}
	}

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsTwoByteString(self)

	override fun o_EqualsTwoByteString(
		self: AvailObject,
		aString: A_String): Boolean
	{
		// First, check for object-structure (address) identity.
		when
		{
			self.sameAddressAs(aString) -> return true
			self.tupleSize != aString.tupleSize -> return false
			self.hash() != aString.hash() -> return false
			// The longs array *must* be padded with zeros for the last 0-3
			// shorts. Compare long-by-long.
			!self.intSlotsCompare(aString as AvailObject, RAW_LONGS_) ->
				return false
			// They're equal, but occupy disjoint storage. If possible, replace one
			// with an indirection to the other to keep down the frequency of
			// character comparisons.
			!isShared ->
			{
				aString.makeImmutable()
				self.becomeIndirectionTo(aString)
			}
			!aString.descriptor().isShared ->
			{
				self.makeImmutable()
				aString.becomeIndirectionTo(self)
			}
		}
		return true
	}

	override fun o_IsTwoByteString(self: AvailObject): Boolean = true

	override fun o_TupleAt(self: AvailObject, index: Int): AvailObject
	{
		// Answer the element at the given index in the tuple object. It's a
		// two-byte character.
		assert(index >= 1 && index <= self.tupleSize)
		return fromCodePoint(self.shortSlot(RAW_LONGS_, index)) as AvailObject
	}

	override fun o_TupleAtPuttingCanDestroy(
		self: AvailObject,
		index: Int,
		newValueObject: A_BasicObject,
		canDestroy: Boolean): A_Tuple
	{
		// Answer a tuple with all the elements of object except at the given
		// index we should have newValueObject. This may destroy the original
		// tuple if canDestroy is true.
		assert(index >= 1 && index <= self.tupleSize)
		if ((newValueObject as A_Character).isCharacter)
		{
			val codePoint: Int = newValueObject.codePoint
			if (codePoint and 0xFFFF == codePoint)
			{
				if (canDestroy && isMutable)
				{
					self.setShortSlot(RAW_LONGS_, index, codePoint)
					self.setHashOrZero(0)
					return self
				}
				// Clone it then modify the copy in place.
				return copyAsMutableTwoByteString(self).tupleAtPuttingCanDestroy(
					index,
					newValueObject,
					true)
			}
		}
		// Convert to a general object tuple instead.
		return self.copyAsMutableObjectTuple().tupleAtPuttingCanDestroy(
			index, newValueObject, true)
	}

	override fun o_TupleCodePointAt(self: AvailObject, index: Int): Int
	{
		assert(index >= 1 && index <= self.tupleSize)
		return self.shortSlot(RAW_LONGS_, index)
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
			generateTwoByteString(size) {
				self.shortSlot(RAW_LONGS_, size + 1 - it).toUShort()
			}
		}

		// It's reasonably small, so just copy the characters.
		// Just copy the applicable two-byte characters in reverse.
	}

	override fun o_TupleSize(self: AvailObject): Int =
		((self.variableIntegerSlotsCount() shl 2) - unusedShortsOfLastLong)

	// Answer approximately how many bits per entry are taken up by this
	// object.
	override fun o_BitsPerEntry(self: AvailObject): Int = 16

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
				self.shortSlot(RAW_LONGS_, index))
				xor preToggle)
			hash = (hash + itemHash) * AvailObject.multiplier
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
		if (newSize <= maximumCopySize
			&& (otherTuple.isTwoByteString || otherTuple.isByteString))
		{
			// Copy the characters, extending otherTuple's characters to
			// two-byte characters if necessary.
			val newLongCount = newSize + 3 shr 2
			val deltaSlots = newLongCount - self.variableIntegerSlotsCount()
			val result: AvailObject
			if (canDestroy && isMutable && deltaSlots == 0)
			{
				// We can reuse the receiver; it has enough int slots.
				result = self
				result.setDescriptor(descriptorFor(Mutability.MUTABLE, newSize))
			}
			else
			{
				result = newLike(
					descriptorFor(Mutability.MUTABLE, newSize), self, 0, deltaSlots)
			}
			var dest = size1 + 1
			var src = 1
			while (src <= size2)
			{
				result.setShortSlot(
					RAW_LONGS_,
					dest,
					otherTuple.tupleCodePointAt(src))
				src++
				dest++
			}
			result.setSlot(HASH_OR_ZERO, 0)
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
			(start..end).all { self.shortSlot(RAW_LONGS_, it) <= 255 } ->
				// Can be a byte-string.
				generateByteString(size) {
					self.shortSlot(RAW_LONGS_, it + start - 1)
				}
			else ->
				// Copy the two-byte codepoints.
				generateTwoByteString(size) {
					self.shortSlot(RAW_LONGS_, it + start - 1).toUShort()
				}
		}
	}

	companion object
	{
		/**
		 * Defined threshold for making copies versus using
		 * [TreeTupleDescriptor]/using other forms of reference instead of
		 * creating a new tuple.
		 */
		private const val maximumCopySize = 32

		/**
		 * The static list of descriptors of this kind, organized in such a way
		 * that [descriptorFor] can find them by mutability and number of unused
		 * shorts in the last long.
		 */
		private val descriptors = arrayOfNulls<TwoByteStringDescriptor>(4 * 3)

		/**
		 * Create a new mutable two-byte string with the specified number of
		 * elements.
		 *
		 * @param size
		 *   The number of elements in the new tuple.
		 * @return
		 *   The new tuple, initialized to null characters (code point 0).
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun mutableTwoByteStringOfSize(size: Int): AvailObject =
			descriptorFor(Mutability.MUTABLE, size).create(size + 3 shr 2)


		/** The [CheckedMethod] for [mutableObjectOfSize]. */
		val createUninitializedTwoByteStringMethod =
			staticMethod(
				TwoByteStringDescriptor::class.java,
				::mutableTwoByteStringOfSize.name,
				AvailObject::class.java,
				Int::class.javaPrimitiveType!!)


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
		 *   A `TwoByteStringDescriptor` suitable for representing a two-byte
		 *   string of the given mutability and [size][A_Tuple.tupleSize].
		 */
		private fun descriptorFor(
			flag: Mutability,
			size: Int): TwoByteStringDescriptor =
				descriptors[(size and 3) * 3 + flag.ordinal]!!

		/**
		 * Create a mutable instance of `TwoByteStringDescriptor` with the
		 * specified Java [String]'s characters.
		 *
		 * @param aNativeTwoByteString
		 *   A Java String that may contain characters outside the Latin-1 range
		 *   (0-255), but not beyond 65535.
		 * @return
		 *   A two-byte string with the given content.
		 */
		fun mutableObjectFromNativeTwoByteString(
			aNativeTwoByteString: String
		): AvailObject = generateTwoByteString(aNativeTwoByteString.length) {
			aNativeTwoByteString.codePointAt(it - 1).toUShort()
		}

		/**
		 * Create an object of the appropriate size, whose descriptor is an
		 * instance of `TwoByteStringDescriptor`. Note that it can only store
		 * Unicode characters from the Basic Multilingual Plane (i.e., those
		 * having Unicode code points 0..65535). Run the generator for each
		 * position in ascending order to produce the code points with which to
		 * populate the string.
		 *
		 * @param size
		 *   The size of two-byte string to create.
		 * @param generator
		 *   A generator to provide code points to store.
		 * @return
		 *   The new Avail string.
		 */
		fun generateTwoByteString(
			size: Int,
			generator: (Int)->UShort): AvailObject
		{
			val result = mutableTwoByteStringOfSize(size)
			var counter = 1
			// Aggregate four writes at a time for the bulk of the string.
			for (slotIndex in 1..(size ushr 2))
			{
				result.setSlot(
					RAW_LONGS_,
					slotIndex,
					generator(counter++).toLong() +
						(generator(counter++).toLong() shl 16) +
						(generator(counter++).toLong() shl 32) +
						(generator(counter++).toLong() shl 48))
			}
			// Do the last 0-3 writes the slow way.
			for (index in (size and 3.inv()) + 1 .. size)
			{
				val c = generator(counter++).toLong()
				assert(c and 0xFFFF == c)
				result.setShortSlot(RAW_LONGS_, index, c.toInt())
			}
			return result
		}

		init
		{
			var i = 0
			for (excess in intArrayOf(0, 3, 2, 1))
			{
				descriptors[i++] =
					TwoByteStringDescriptor(Mutability.MUTABLE, excess)
				descriptors[i++] =
					TwoByteStringDescriptor(Mutability.IMMUTABLE, excess)
				descriptors[i++] =
					TwoByteStringDescriptor(Mutability.SHARED, excess)
			}
		}
	}

	override fun mutable(): TwoByteStringDescriptor =
		descriptors[(4 - unusedShortsOfLastLong and 3) * 3
			+ Mutability.MUTABLE.ordinal]!!

	override fun immutable(): TwoByteStringDescriptor =
		descriptors[(4 - unusedShortsOfLastLong and 3) * 3
			+ Mutability.IMMUTABLE.ordinal]!!

	override fun shared(): TwoByteStringDescriptor =
		descriptors[(4 - unusedShortsOfLastLong and 3) * 3
			+ Mutability.SHARED.ordinal]!!

	/**
	 * Answer a mutable copy of object that also only holds 16-bit characters.
	 *
	 * @param self
	 *   The two-byte string to copy.
	 * @return
	 *   A new two-byte string with the same content as the argument.
	 */
	private fun copyAsMutableTwoByteString(self: AvailObject): A_String =
		newLike(mutable(), self, 0, 0)
}
