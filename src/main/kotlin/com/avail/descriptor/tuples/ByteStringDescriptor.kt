/*
 * ByteStringDescriptor.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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
package com.avail.descriptor.tuples

import com.avail.annotations.HideFieldInDebugger
import com.avail.annotations.ThreadSafe
import com.avail.descriptor.character.A_Character
import com.avail.descriptor.character.A_Character.Companion.codePoint
import com.avail.descriptor.character.CharacterDescriptor.Companion.fromByteCodePoint
import com.avail.descriptor.character.CharacterDescriptor.Companion.hashOfByteCharacterWithCodePoint
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.AvailObjectRepresentation.Companion.newLike
import com.avail.descriptor.representation.BitField
import com.avail.descriptor.representation.IntegerSlotsEnum
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.tuples.A_Tuple.Companion.compareFromToWithByteStringStartingAt
import com.avail.descriptor.tuples.A_Tuple.Companion.concatenateWith
import com.avail.descriptor.tuples.A_Tuple.Companion.copyAsMutableObjectTuple
import com.avail.descriptor.tuples.A_Tuple.Companion.rawByteForCharacterAt
import com.avail.descriptor.tuples.A_Tuple.Companion.treeTupleLevel
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleAtPuttingCanDestroy
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import com.avail.descriptor.tuples.ByteStringDescriptor.IntegerSlots.Companion.HASH_OR_ZERO
import com.avail.descriptor.tuples.ByteStringDescriptor.IntegerSlots.RAW_LONGS_
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.TreeTupleDescriptor.Companion.concatenateAtLeastOneTree
import com.avail.descriptor.tuples.TreeTupleDescriptor.Companion.createTwoPartTreeTuple
import com.avail.descriptor.tuples.TwoByteStringDescriptor.Companion.generateTwoByteString
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import com.avail.serialization.SerializerOperation
import com.avail.utility.structures.EnumMap.Companion.enumMap

/**
 * `ByteStringDescriptor` represents a string of Latin-1 characters.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property unusedBytesOfLastLong
 *   The number of bytes of the last `long` that do not participate in the
 *   representation of the [byte string][ByteStringDescriptor]. Must be between
 *   0 and 7.
 *
 * @constructor
 * Construct a new `ByteStringDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param unusedBytesOfLastLong
 *   The number of bytes of the last `long` that do not participate in the
 *   representation of the [byte string][ByteStringDescriptor]. Must be between
 *   0 and 7.
 */
class ByteStringDescriptor private constructor(
	mutability: Mutability,
	private val unusedBytesOfLastLong: Int) : StringDescriptor(
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
		 * [byte string][ByteStringDescriptor].  The bytes occur in Little
		 * Endian order within each long.
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
			@JvmField
			val HASH_OR_ZERO = BitField(HASH_AND_MORE, 0, 32)

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
		val originalSize = self.tupleSize()
		if (originalSize >= maximumCopySize || !newElement.isCharacter)
		{
			// Transition to a tree tuple.
			val singleton = tuple(newElement)
			return self.concatenateWith(singleton, canDestroy)
		}
		val intValue: Int = (newElement as A_Character).codePoint()
		if (intValue and 255.inv() != 0)
		{
			// Transition to a tree tuple.
			val singleton = tuple(newElement)
			return self.concatenateWith(singleton, canDestroy)
		}
		val newSize = originalSize + 1
		if (isMutable && canDestroy && originalSize and 7 != 0)
		{
			// Enlarge it in place, using more of the final partial long field.
			self.setDescriptor(descriptorFor(Mutability.MUTABLE, newSize))
			self.setByteSlot(RAW_LONGS_, newSize, intValue.toShort())
			self.setSlot(HASH_OR_ZERO, 0)
			return self
		}
		// Copy to a potentially larger ByteTupleDescriptor.
		val result = newLike(
			descriptorFor(Mutability.MUTABLE, newSize),
			self,
			0,
			if (originalSize and 7 == 0) 1 else 0)
		result.setByteSlot(RAW_LONGS_, newSize, intValue.toShort())
		result.setSlot(HASH_OR_ZERO, 0)
		return result
	}

	override fun o_CompareFromToWithStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anotherObject: A_Tuple,
		startIndex2: Int): Boolean =
			anotherObject.compareFromToWithByteStringStartingAt(
				startIndex2,
				startIndex2 + endIndex1 - startIndex1,
				self,
				startIndex1)

	override fun o_CompareFromToWithByteStringStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aByteString: A_String,
		startIndex2: Int): Boolean
	{
		// Compare sections of two byte strings.
		if (self.sameAddressAs(aByteString) && startIndex1 == startIndex2)
		{
			return true
		}
		// Compare actual bytes.
		var index1 = startIndex1
		var index2 = startIndex2
		while (index1 <= endIndex1)
		{
			if (self.rawByteForCharacterAt(index1)
				!= aByteString.rawByteForCharacterAt(index2))
			{
				return false
			}
			index1++
			index2++
		}
		if (startIndex1 == 1
			&& startIndex2 == 1
			&& endIndex1 == self.tupleSize()
			&& endIndex1 == aByteString.tupleSize())
		{
			// They're *completely* equal (but occupy disjoint storage). If
			// possible, replace one with an indirection to the other to keep
			// down the frequency of byte-wise comparisons.
			if (!isShared)
			{
				aByteString.makeImmutable()
				self.becomeIndirectionTo(aByteString)
			}
			else if (!aByteString.descriptor().isShared)
			{
				self.makeImmutable()
				aByteString.becomeIndirectionTo(self)
			}
		}
		return true
	}

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsByteString(self)

	override fun o_EqualsByteString(
		self: AvailObject,
		aByteString: A_String): Boolean
	{
		// First, check for object-structure (address) identity.
		if (self.sameAddressAs(aByteString))
		{
			return true
		}
		val tupleSize = self.tupleSize()
		return tupleSize == aByteString.tupleSize()
			   && self.hash() == aByteString.hash()
			   && self.compareFromToWithByteStringStartingAt(
				1, tupleSize, aByteString, 1)
	}

	override fun o_IsByteString(self: AvailObject): Boolean = true

	override fun o_MakeImmutable(self: AvailObject): AvailObject
	{
		if (isMutable)
		{
			self.setDescriptor(immutable())
		}
		return self
	}

	override fun o_MakeShared(self: AvailObject): AvailObject
	{
		if (!isShared)
		{
			self.setDescriptor(shared())
		}
		return self
	}

	override fun o_RawByteForCharacterAt(self: AvailObject, index: Int): Short
	{
		//  Answer the byte that encodes the character at the given index.
		assert(index >= 1 && index <= self.tupleSize())
		return self.byteSlot(RAW_LONGS_, index)
	}

	override fun o_TupleAt(self: AvailObject, index: Int): AvailObject
	{
		// Answer the element at the given index in the tuple object.  It's a
		// one-byte character.
		assert(index >= 1 && index <= self.tupleSize())
		val codePoint = self.byteSlot(RAW_LONGS_, index)
		return fromByteCodePoint(codePoint) as AvailObject
	}

	override fun o_TupleAtPuttingCanDestroy(
		self: AvailObject,
		index: Int,
		newValueObject: A_BasicObject,
		canDestroy: Boolean): A_Tuple
	{
		// Answer a tuple with all the elements of object except at the given
		// index we should have newValueObject.  This may destroy the original
		// tuple if canDestroy is true.
		assert(index >= 1 && index <= self.tupleSize())
		if (newValueObject.isCharacter)
		{
			val codePoint: Int = (newValueObject as A_Character).codePoint()
			if (codePoint and 0xFF == codePoint)
			{
				val result =
					if (canDestroy && isMutable) self
					else newLike(mutable(), self, 0, 0)
				result.setByteSlot(RAW_LONGS_, index, codePoint.toShort())
				result.setHashOrZero(0)
				return result
			}
			if (codePoint and 0xFFFF == codePoint)
			{
				return copyAsMutableTwoByteString(self)
					.tupleAtPuttingCanDestroy(index, newValueObject, true)
			}
			// Fall through for SMP Unicode characters.
		}
		//  Convert to an arbitrary Tuple instead.
		return self.copyAsMutableObjectTuple().tupleAtPuttingCanDestroy(
			index, newValueObject, true)
	}

	override fun o_TupleCodePointAt(self: AvailObject, index: Int): Int
	{
		assert(index >= 1 && index <= self.tupleSize())
		return self.byteSlot(RAW_LONGS_, index).toInt()
	}

	override fun o_TupleReverse(self: AvailObject): A_Tuple
	{
		val size = self.tupleSize()
		return if (size > maximumCopySize)
		{
			super.o_TupleReverse(self)
		}
		else generateByteString(size) {
			self.byteSlot(RAW_LONGS_, size + 1 - it).toInt()
		}

		// It's not empty, it's not a total copy, and it's reasonably small.
		// Just copy the applicable bytes out.  In theory we could use
		// newLike() if start is 1.  Make sure to mask the last word in that
		// case.
	}

	// Answer the number of elements in the object.
	override fun o_TupleSize(self: AvailObject): Int =
		((self.variableIntegerSlotsCount() shl 3) - unusedBytesOfLastLong)

	// Answer approximately how many bits per entry are taken up by this
	// object.
	override fun o_BitsPerEntry(self: AvailObject): Int = 8

	/**
	 * {@inheritDoc}
	 *
	 * See comment in superclass. This overridden method must produce the same
	 * value.
	 */
	override fun o_ComputeHashFromTo(
		self: AvailObject,
		start: Int,
		end: Int): Int
	{
		var hash = 0
		for (index in end downTo start)
		{
			val itemHash = (hashOfByteCharacterWithCodePoint(
				self.byteSlot(RAW_LONGS_, index)) xor preToggle)
			hash = (hash + itemHash) * AvailObject.multiplier
		}
		return hash
	}

	@ThreadSafe
	override fun o_SerializerOperation(
		self: AvailObject): SerializerOperation =
			SerializerOperation.BYTE_STRING

	override fun o_MarshalToJava(
		self: AvailObject,
		classHint: Class<*>?): Any? = self.asNativeString()

	override fun o_CopyTupleFromToCanDestroy(
		self: AvailObject,
		start: Int,
		end: Int,
		canDestroy: Boolean): A_Tuple
	{
		val tupleSize = self.tupleSize()
		assert(1 <= start && start <= end + 1 && end <= tupleSize)
		val size = end - start + 1
		return if (size in 1 until tupleSize && size < maximumCopySize)
		{
			// It's not empty, it's not a total copy, and it's reasonably small.
			// Just copy the applicable bytes out.  In theory we could use
			// newLike() if start is 1.  Make sure to mask the last word in that
			// case.
			generateByteString(size) {
				self.byteSlot(RAW_LONGS_, it + start - 1).toInt()
			}
		}
		else
		{
			super.o_CopyTupleFromToCanDestroy(self, start, end, canDestroy)
		}
	}

	override fun o_ConcatenateWith(
		self: AvailObject,
		otherTuple: A_Tuple,
		canDestroy: Boolean): A_Tuple
	{
		val size1 = self.tupleSize()
		if (size1 == 0)
		{
			if (!canDestroy)
			{
				otherTuple.makeImmutable()
			}
			return otherTuple
		}
		val size2 = otherTuple.tupleSize()
		if (size2 == 0)
		{
			if (!canDestroy)
			{
				self.makeImmutable()
			}
			return self
		}
		val newSize = size1 + size2
		if (otherTuple.isByteString && newSize <= maximumCopySize)
		{
			// Copy the characters.
			val newLongCount = newSize + 7 ushr 3
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
					descriptorFor(Mutability.MUTABLE, newSize),
					self,
					0,
					deltaSlots)
			}
			var dest = size1 + 1
			var src = 1
			while (src <= size2)
			{
				result.setByteSlot(
					RAW_LONGS_,
				   	dest,
					otherTuple.rawByteForCharacterAt(src))
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
		return if (otherTuple.treeTupleLevel() == 0)
		{
			createTwoPartTreeTuple(self, otherTuple, 1, 0)
		}
		else
		{
			concatenateAtLeastOneTree(self, otherTuple, true)
		}
	}

	/**
	 * Answer a new byte string capacious enough to hold the specified number of
	 * elements.
	 *
	 * @param size
	 *   The desired number of elements.
	 * @return
	 *   A new mutable byte string.
	 */
	private fun mutableObjectOfSize(size: Int): AvailObject
	{
		assert(isMutable)
		assert(size + unusedBytesOfLastLong and 7 == 0)
		return create(size + 7 shr 3) { }
	}

	companion object
	{
		/**
		 * Defined threshold for making copies versus using
		 * [TreeTupleDescriptor]/using other forms of reference instead of
		 * creating a new tuple.
		 */
		private const val maximumCopySize = 64

		/**
		 * Create an object of the appropriate size, whose descriptor is an
		 * instance of `ByteStringDescriptor`.  Note that it can only store
		 * Latin-1 characters (i.e., those having Unicode code points 0..255).
		 * Run the generator for each position in ascending order to produce the
		 * code points with which to populate the string.
		 *
		 * @param size
		 *   The size of byte string to create.
		 * @param generator
		 *   A generator to provide code points to store.
		 * @return
		 *   The new Avail [A_String].
		 */
		@JvmStatic
		fun generateByteString(
			size: Int,
			generator: (Int) -> Int): AvailObject
		{
			val descriptor = descriptorFor(Mutability.MUTABLE, size)
			val result = descriptor.mutableObjectOfSize(size)
			var counter = 1
			// Aggregate eight writes at a time for the bulk of the string.
			for (slotIndex in 1..(size ushr 3))
			{
				var combined: Long = 0
				var shift = 0
				while (shift < 64)
				{
					val c = generator(counter++).toLong()
					assert(c and 255 == c)
					combined += c shl shift
					shift += 8
				}
				result.setSlot(RAW_LONGS_, slotIndex, combined)
			}
			// Do the last 0-7 writes the slow way.
			for (index in (size and 7.inv()) + 1 .. size)
			{
				val c = generator(counter++).toLong()
				assert(c and 255 == c)
				result.setByteSlot(RAW_LONGS_, index, c.toShort())
			}
			return result
		}

		/**
		 * Answer a mutable copy of the [receiver][AvailObject] that holds
		 * 16-bit characters.
		 *
		 * @param self
		 *   The [receiver][AvailObject].
		 * @return
		 *   A mutable copy of the [receiver][AvailObject].
		 */
		private fun copyAsMutableTwoByteString(
			self: AvailObject): A_String
		{
			val result = generateTwoByteString(self.tupleSize()) {
				self.byteSlot(RAW_LONGS_, it).toInt()
			}
			result.setHashOrZero(self.hashOrZero())
			return result
		}

		/**
		 * Convert the specified Java [String] of purely Latin-1 characters
		 * into an Avail [A_String].
		 *
		 * @param aNativeByteString
		 *   A Java [String] whose code points are all 0..255.
		 * @return
		 *   A corresponding Avail [A_String].
		 */
		fun mutableObjectFromNativeByteString(
			aNativeByteString: String
		): AvailObject =
			generateByteString(aNativeByteString.length) {
				aNativeByteString[it - 1].toInt()
			}

		/**
		 * Create a mutable byte string of the specified size, where all
		 * elements are the null byte (code point U+0000).
		 *
		 * @param size
		 *   The size of the byte string to construct.
		 * @return
		 *   An Avail [A_String].
		 */
		@JvmStatic
		@ReferencedInGeneratedCode
		fun createUninitializedByteString(size: Int): AvailObject =
			descriptorFor(Mutability.MUTABLE, size).create(size + 7 shr 3)

		/** The [CheckedMethod] for [createUninitializedByteString]. */
		@JvmField
		val createUninitializedByteStringMethod: CheckedMethod = staticMethod(
			ByteStringDescriptor::class.java,
			::createUninitializedByteString.name,
			AvailObject::class.java,
			Int::class.javaPrimitiveType!!)

		/**
		 * The [ByteStringDescriptor] instances.  Each [Array] is indexed by the
		 * number of spare bytes it contains, [0..7], which is *not* in the
		 * order of tuple sizes.
		 */
		private val descriptors = enumMap(Mutability.values()) { mut ->
			Array(8) {
				ByteStringDescriptor(mut, it)
			}
		}

		/**
		 * Answer the appropriate `ByteStringDescriptor` to represent an
		 * [object][AvailObject] of the specified mutability and size.
		 *
		 * @param mutability
		 *   The [mutability][Mutability] of the new descriptor.
		 * @param size
		 *   The desired number of elements.
		 * @return
		 *   A `ByteStringDescriptor` suitable for representing a byte string of
		 *   the given mutability and [size][A_Tuple.tupleSize].
		 */
		private fun descriptorFor(
			mutability: Mutability,
			size: Int
		): ByteStringDescriptor = descriptors[mutability][-size and 7]
	}

	override fun mutable(): ByteStringDescriptor =
		descriptors[Mutability.MUTABLE][unusedBytesOfLastLong]

	override fun immutable(): ByteStringDescriptor =
		descriptors[Mutability.IMMUTABLE][unusedBytesOfLastLong]

	override fun shared(): ByteStringDescriptor =
		descriptors[Mutability.SHARED][unusedBytesOfLastLong]
}
