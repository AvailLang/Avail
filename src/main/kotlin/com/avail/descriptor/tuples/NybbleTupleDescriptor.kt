/*
 * NybbleTupleDescriptor.kt
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
import com.avail.descriptor.character.A_Character.Companion.codePoint
import com.avail.descriptor.functions.CompiledCodeDescriptor
import com.avail.descriptor.numbers.A_Number
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromUnsignedByte
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.hashOfUnsignedByte
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.AvailObjectRepresentation.Companion.newLike
import com.avail.descriptor.representation.BitField
import com.avail.descriptor.representation.IntegerSlotsEnum
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.tuples.A_Tuple.Companion.compareFromToWithNybbleTupleStartingAt
import com.avail.descriptor.tuples.A_Tuple.Companion.concatenateWith
import com.avail.descriptor.tuples.A_Tuple.Companion.copyAsMutableIntTuple
import com.avail.descriptor.tuples.A_Tuple.Companion.copyAsMutableObjectTuple
import com.avail.descriptor.tuples.A_Tuple.Companion.extractNybbleFromTupleAt
import com.avail.descriptor.tuples.A_Tuple.Companion.treeTupleLevel
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleAtPuttingCanDestroy
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import com.avail.descriptor.tuples.ByteStringDescriptor.Companion.generateByteString
import com.avail.descriptor.tuples.ByteTupleDescriptor.Companion.generateByteTupleFrom
import com.avail.descriptor.tuples.IntTupleDescriptor.Companion.generateIntTupleFrom
import com.avail.descriptor.tuples.LongTupleDescriptor.Companion.generateLongTupleFrom
import com.avail.descriptor.tuples.NybbleTupleDescriptor.IntegerSlots
import com.avail.descriptor.tuples.NybbleTupleDescriptor.IntegerSlots.Companion.HASH_OR_ZERO
import com.avail.descriptor.tuples.NybbleTupleDescriptor.IntegerSlots.RAW_LONG_AT_
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.TwoByteStringDescriptor.Companion.generateTwoByteString
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.IntegerRangeTypeDescriptor
import com.avail.descriptor.types.TypeDescriptor
import com.avail.descriptor.types.TypeDescriptor.Types
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import com.avail.serialization.SerializerOperation
import java.nio.ByteBuffer

/**
 * `NybbleTupleDescriptor` represents a tuple of integers that happen to fall in
 * the range 0..15. They are packed eight per `int`.
 *
 * This representation is particularly useful for
 * [compiled&#32;code][CompiledCodeDescriptor], which uses nybblecodes.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property unusedNybblesOfLastLong
 *   The number of nybbles of the last `long` [integer
 *   slot][IntegerSlots.RAW_LONG_AT_] that are not considered part of the tuple.
 *
 * @constructor
 * Construct a new `NybbleTupleDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param unusedNybblesOfLastLong
 *   The number of nybbles of the last `long` [integer
 *   slot][IntegerSlots.RAW_LONG_AT_] that are not considered part of the tuple.
 */
class NybbleTupleDescriptor private constructor(
	mutability: Mutability,
	private val unusedNybblesOfLastLong: Int) : NumericTupleDescriptor(
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
		 * The raw 64-bit machine words that constitute the representation of
		 * the [nybble tuple][NybbleTupleDescriptor].
		 */
		RAW_LONG_AT_;

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
				assert(TupleDescriptor.IntegerSlots.HASH_OR_ZERO.isSamePlaceAs(
					HASH_OR_ZERO))
			}
		}
	}

	override fun o_AppendCanDestroy(
		self: AvailObject,
		newElement: A_BasicObject,
		canDestroy: Boolean): A_Tuple
	{
		val strongNewElement = newElement as AvailObject
		val originalSize = self.tupleSize()
		if (originalSize == 0)
		{
			// When accumulating a tuple, use the first element as an indicator
			// of which kind of tuple to create.  It'll probably be a good
			// guess, and at worst we switch to a more general form when the
			// second element shows up.
			when
			{
				strongNewElement.isCharacter ->
					return when (val codePoint = strongNewElement.codePoint())
					{
						in 0 .. 0xFF -> generateByteString(1) { codePoint }
						in 0 .. 0xFFFF -> generateTwoByteString(1) { codePoint }
						else -> tuple(strongNewElement)
					}
				strongNewElement.isLong ->
					return when (val longValue = strongNewElement.extractLong())
					{
						in 0 .. 0xFF -> generateByteTupleFrom(1) {
							longValue.toInt()
						}
						in -0x8000_0000 .. 0x7FFF_FFFF ->
							generateIntTupleFrom(1) { longValue.toInt() }
						else -> generateLongTupleFrom(1) { longValue }
					}
			}
		}
		if (originalSize < maximumCopySize && strongNewElement.isInt)
		{
			val intValue = strongNewElement.extractInt()
			if (intValue and 15.inv() != 0)
			{
				// Transition to a tree tuple.
				val singleton = tuple(strongNewElement)
				return self.concatenateWith(singleton, canDestroy)
			}
			val newSize = originalSize + 1
			val result: AvailObject
			if (isMutable && canDestroy && originalSize and 15 != 0)
			{
				// Enlarge it in place, using the pad nybbles of the last long.
				result = self
				result.setDescriptor(descriptorFor(Mutability.MUTABLE, newSize))
			}
			else
			{
				result = newLike(
					descriptorFor(Mutability.MUTABLE, newSize),
					self,
					0,
					if (originalSize and 15 == 0) 1 else 0)
			}
			setNybble(result, newSize, intValue.toByte())
			result.setSlot(HASH_OR_ZERO, 0)
			return result
		}
		// Transition to a tree tuple.
		val singleton = tuple(strongNewElement)
		return self.concatenateWith(singleton, canDestroy)
	}

	// Answer approximately how many bits per entry are taken up by this
	// object.
	override fun o_BitsPerEntry(self: AvailObject): Int = 4

	override fun o_CompareFromToWithNybbleTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aNybbleTuple: A_Tuple,
		startIndex2: Int): Boolean
	{
		if (self.sameAddressAs(aNybbleTuple) && startIndex1 == startIndex2)
		{
			return true
		}
		if (endIndex1 < startIndex1)
		{
			return true
		}
		//  Compare actual nybbles.
		var index2 = startIndex2
		for (i in startIndex1 .. endIndex1)
		{
			if (getNybble(self, i)
				!= aNybbleTuple.extractNybbleFromTupleAt(index2))
			{
				return false
			}
			index2++
		}
		return true
	}

	override fun o_CompareFromToWithStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anotherObject: A_Tuple,
		startIndex2: Int): Boolean =
			anotherObject.compareFromToWithNybbleTupleStartingAt(
				startIndex2,
				startIndex2 + endIndex1 - startIndex1,
				self,
				startIndex1)

	override fun o_ComputeHashFromTo(
		self: AvailObject,
		start: Int,
		end: Int): Int
	{
		// See comment in superclass.  This method must produce the same value.
		// This could eventually be rewritten to do a byte at a time (table
		// lookup) and to use the square of the current multiplier.
		var hash = 0
		for (nybbleIndex in end downTo start)
		{
			val itemHash =
				hashOfUnsignedByte(getNybble(self, nybbleIndex).toShort()) xor
					preToggle
			hash = (hash + itemHash) * AvailObject.multiplier
		}
		return hash
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
		if (newSize <= maximumCopySize)
		{
			// Copy the nybbles.
			val newWordCount = newSize + 15 ushr 4
			val deltaSlots = newWordCount - self.variableIntegerSlotsCount()
			val copy: AvailObject
			copy = if (canDestroy && isMutable && deltaSlots == 0)
			{
				// We can reuse the receiver; it has enough int slots.
				self.setDescriptor(descriptorFor(Mutability.MUTABLE, newSize))
				self
			}
			else
			{
				newLike(descriptorFor(
					Mutability.MUTABLE, newSize), self, 0, deltaSlots)
			}
			copy.setSlot(HASH_OR_ZERO, 0)
			var dest = size1 + 1
			var result: A_Tuple = copy
			var src = 1
			while (src <= size2)
			{

				// If the slots we want are nybbles then we won't have to copy
				// into a bulkier representation.
				result = result.tupleAtPuttingCanDestroy(
					dest,
					otherTuple.tupleAt(src),
					canDestroy || src > 1)
				src++
				dest++
			}
			return result
		}
		if (!canDestroy)
		{
			self.makeImmutable()
			otherTuple.makeImmutable()
		}
		return if (otherTuple.treeTupleLevel() == 0)
		{
			TreeTupleDescriptor.createTwoPartTreeTuple(self, otherTuple, 1, 0)
		}
		else
		{
			TreeTupleDescriptor.concatenateAtLeastOneTree(self, otherTuple, true)
		}
	}

	override fun o_CopyTupleFromToCanDestroy(
		self: AvailObject,
		start: Int,
		end: Int,
		canDestroy: Boolean): A_Tuple
	{
		val tupleSize = self.tupleSize()
		assert(1 <= start && start <= end + 1 && end <= tupleSize)
		val size = end - start + 1
		if (size in 1 until tupleSize && size < maximumCopySize)
		{
			// It's not empty, it's not a total copy, and it's reasonably small.
			// Just copy the applicable nybbles out.  In theory we could use
			// newLike() if start is 1.  Make sure to mask the last word in that
			// case.
			val result = generateNybbleTupleFrom(size)
				{ getNybble(self, it + start - 1).toInt() }
			if (canDestroy)
			{
				self.assertObjectUnreachableIfMutable()
			}
			return result
		}
		return super.o_CopyTupleFromToCanDestroy(self, start, end, canDestroy)
	}

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsNybbleTuple(self)

	override fun o_EqualsNybbleTuple(
		self: AvailObject,
		aTuple: A_Tuple): Boolean
	{
		// First, check for object-structure (address) identity.
		when
		{
			self.sameAddressAs(aTuple) -> return true
			self.tupleSize() != aTuple.tupleSize() -> return false
			self.hash() != aTuple.hash() -> return false
			!self.compareFromToWithNybbleTupleStartingAt(
				1,
				self.tupleSize(),
				aTuple,
				1) -> return false
			// They're equal, but occupy disjoint storage. If, then replace one
			// with an indirection to the other to reduce storage costs and the
			// frequency of nybble-wise comparisons.
			!isShared ->
			{
				aTuple.makeImmutable()
				self.becomeIndirectionTo(aTuple)
			}
			!aTuple.descriptor().isShared ->
			{
				self.makeImmutable()
				aTuple.becomeIndirectionTo(self)
			}
		}
		return true
	}

	override fun o_ExtractNybbleFromTupleAt(self: AvailObject, index: Int): Byte
	{
		return getNybble(self, index)
	}

	// Given two objects that are known to be equal, is the first one in a
	// better form (more compact, more efficient, older generation) than the
	// second one? Currently there is no more desirable representation than
	// a nybble tuple.  [NB MvG 2015.09.24 - other than integer interval
	// tuples.  Update this at some point, perhaps.]
	override fun o_IsBetterRepresentationThan(
		self: AvailObject,
		anotherObject: A_BasicObject): Boolean = true

	override fun o_IsInstanceOfKind(
		self: AvailObject,
		aType: A_Type): Boolean
	{
		when
		{
			aType.isSupertypeOfPrimitiveTypeEnum(Types.NONTYPE) ->
				return true
			!aType.isTupleType -> return false
			// See if it's an acceptable size...
			!aType.sizeRange().rangeIncludesLong(self.tupleSize().toLong()) ->
				return false
			// Tuple's size is out of range.
			else ->
			{
				val typeTuple = aType.typeTuple()
				val breakIndex =
					self.tupleSize().coerceAtMost(typeTuple.tupleSize())
				for (i in 1 .. breakIndex)
				{
					if (!self.tupleAt(i).isInstanceOf(aType.typeAtIndex(i)))
					{
						return false
					}
				}
				val defaultTypeObject = aType.defaultType()
				if (IntegerRangeTypeDescriptor.nybbles()
						.isSubtypeOf(defaultTypeObject))
				{
					return true
				}
				var i = breakIndex + 1
				val end = self.tupleSize()
				while (i <= end)
				{
					if (!self.tupleAt(i).isInstanceOf(defaultTypeObject))
					{
						return false
					}
					i++
				}
				return true
			}
		}
	}

	override fun o_MakeImmutable(self: AvailObject): AvailObject
	{
		if (isMutable)
		{
			self.setDescriptor(descriptorFor(
				Mutability.IMMUTABLE, self.tupleSize()))
		}
		return self
	}

	override fun o_MakeShared(self: AvailObject): AvailObject
	{
		if (!isShared)
		{
			self.setDescriptor(
				descriptorFor(Mutability.SHARED, self.tupleSize()))
		}
		return self
	}

	override fun o_SerializerOperation(
		self: AvailObject): SerializerOperation =
			SerializerOperation.NYBBLE_TUPLE

	override fun o_TransferIntoByteBuffer(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int,
		outputByteBuffer: ByteBuffer)
	{
		for (index in startIndex .. endIndex)
		{
			outputByteBuffer.put(getNybble(self, index))
		}
	}

	// Answer the element at the given index in the nybble tuple object.
	override fun o_TupleAt(self: AvailObject, index: Int): AvailObject =
		fromUnsignedByte(getNybble(self, index).toShort())

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
		if (!newValueObject.isNybble)
		{
			if (newValueObject.isUnsignedByte)
			{
				return copyAsMutableByteTuple(self).tupleAtPuttingCanDestroy(
					index, newValueObject, true)
			}
			return if (newValueObject.isInt)
			{
				self.copyAsMutableIntTuple().tupleAtPuttingCanDestroy(
					index, newValueObject, true)
			}
			else
			{
				self.copyAsMutableObjectTuple().tupleAtPuttingCanDestroy(
					index, newValueObject, true)
			}
		}
		val result: AvailObject =
			if (canDestroy && isMutable)
			{
				self
			}
			else
			{
				newLike(mutable(), self, 0, 0)
			}
		// All clear.  Clobber the object in place...
		val newNybble = (newValueObject as A_Number).extractNybble()
		setNybble(result, index, newNybble)
		result.setHashOrZero(0)
		//  ...invalidate the hash value. Probably cheaper than computing the
		// difference or even testing for an actual change.
		return result
	}

	override fun o_TupleElementsInRangeAreInstancesOf(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int,
		type: A_Type): Boolean =
			(IntegerRangeTypeDescriptor.nybbles().isSubtypeOf(type)
				|| super.o_TupleElementsInRangeAreInstancesOf(
					self, startIndex, endIndex, type))

	override fun o_TupleIntAt(self: AvailObject, index: Int): Int =
		getNybble(self, index).toInt()

	override fun o_TupleLongAt(self: AvailObject, index: Int): Long =
		getNybble(self, index).toLong()

	override fun o_TupleReverse(self: AvailObject): A_Tuple
	{
		val size = self.tupleSize()
		return if (size >= maximumCopySize)
		{
			super.o_TupleReverse(self)
		}
		else generateNybbleTupleFrom(size)
			{ getNybble(self, size + 1 - it).toInt() }
	}

	override fun o_TupleSize(self: AvailObject): Int =
		((self.variableIntegerSlotsCount() shl 4) - unusedNybblesOfLastLong)

	companion object
	{
		/**
		 * Defined threshold for making copies versus using
		 * [TreeTupleDescriptor]/using other forms of reference instead of
		 * creating a new tuple.
		 */
		private const val maximumCopySize = 128

		/**
		 * Extract the nybble from the specified position of the nybble tuple.
		 *
		 * @param self
		 *   A nybble tuple.
		 * @param nybbleIndex
		 *   The index.
		 * @return
		 *   The nybble at that index.
		 */
		fun getNybble(self: AvailObject, nybbleIndex: Int): Byte
		{
			assert(nybbleIndex >= 1 && nybbleIndex <= self.tupleSize())
			val longIndex = nybbleIndex + 15 ushr 4
			val longValue = self.slot(RAW_LONG_AT_, longIndex)
			val shift = nybbleIndex - 1 and 15 shl 2
			return (longValue ushr shift and 0x0F).toByte()
		}

		/**
		 * Overwrite the specified position of the nybble tuple with a replacement
		 * nybble.
		 *
		 * @param self
		 *   The nybble tuple.
		 * @param nybbleIndex
		 *   The index.
		 * @param aNybble
		 *   The replacement value, a nybble.
		 */
		private fun setNybble(
			self: AvailObject,
			nybbleIndex: Int,
			aNybble: Byte)
		{
			assert(nybbleIndex >= 1 && nybbleIndex <= self.tupleSize())
			assert(aNybble.toInt() and 15 == aNybble.toInt())
			val longIndex = nybbleIndex + 15 ushr 4
			var longValue = self.slot(RAW_LONG_AT_, longIndex)
			val leftShift = nybbleIndex - 1 and 15 shl 2
			longValue = longValue and (0x0FL shl leftShift).inv()
			longValue = longValue or (aNybble.toLong() shl leftShift)
			self.setSlot(RAW_LONG_AT_, longIndex, longValue)
		}

		/**
		 * The static array of descriptors of this kind, organized in such a way
		 * that [descriptorFor] can find them by mutability and number of unused
		 * nybbles in the last word.
		 */
		private val descriptors = arrayOfNulls<NybbleTupleDescriptor>(16 * 3)

		/**
		 * Create an object of the appropriate size, whose descriptor is an
		 * instance of `NybbleTupleDescriptor`.  Run the generator for each
		 * position in ascending order to produce the nybbles with which to
		 * populate the tuple.
		 *
		 * @param size
		 *   The size of nybble tuple to create.
		 * @param generator
		 *   A generator to provide nybbles to store.
		 * @return
		 *   The new tuple of nybbles.
		 */
		fun generateNybbleTupleFrom(
			size: Int,
			generator: (Int) -> Int): AvailObject
		{
			val result = mutableObjectOfSize(size)
			var tupleIndex = 1
			// Aggregate sixteen writes at a time for the bulk of the tuple.
			var slotIndex = 1
			val limit = size ushr 4
			while (slotIndex <= limit)
			{
				var combined: Long = 0
				var shift = 0
				while (shift < 64)
				{
					val nybble = generator(tupleIndex++).toByte()
					assert(nybble.toInt() and 15 == nybble.toInt())
					combined = combined or (nybble.toLong() shl shift)
					shift += 4
				}
				result.setSlot(RAW_LONG_AT_, slotIndex, combined)
				slotIndex++
			}
			// Do the last 0-15 writes the slow way.
			for (index in (size and 15.inv()) + 1 .. size)
			{
				val nybble = generator(tupleIndex++).toByte()
				assert(nybble.toInt() and 15 == nybble.toInt())
				setNybble(result, index, nybble)
			}
			assert(tupleIndex == size + 1)
			return result
		}

		/**
		 * Answer a mutable copy of object that holds bytes, as opposed to just
		 * nybbles.
		 *
		 * @param self
		 *   A nybble tuple to copy as a [byte tuple][ByteTupleDescriptor].
		 * @return
		 *   A new [byte tuple][ByteTupleDescriptor] with the same sequence of
		 *   integers as the argument.
		 */
		private fun copyAsMutableByteTuple(self: AvailObject): A_Tuple
		{
			val result =
				generateByteTupleFrom(self.tupleSize())
					{ getNybble(self, it).toInt() }
			result.setHashOrZero(self.hashOrZero())
			return result
		}

		/**
		 * Build a new object instance with room for size elements.
		 *
		 * @param size
		 *   The number of elements for which to leave room.
		 * @return
		 *   A mutable nybble tuple.
		 */
		@JvmStatic
		@ReferencedInGeneratedCode
		fun mutableObjectOfSize(size: Int): AvailObject
		{
			val d = descriptorFor(Mutability.MUTABLE, size)
			assert(size + d.unusedNybblesOfLastLong and 15 == 0)
			return d.create(size + 15 ushr 4)
		}

		/** The [CheckedMethod] for [mutableObjectOfSize]. */
		@JvmField
		val createUninitializedNybbleTupleMethod: CheckedMethod = staticMethod(
			NybbleTupleDescriptor::class.java,
			::mutableObjectOfSize.name,
			AvailObject::class.java,
			Int::class.javaPrimitiveType!!)

		/**
		 * Answer the descriptor that has the specified mutability flag and is
		 * suitable to describe a tuple with the given number of elements.
		 *
		 * @param flag
		 *   The [mutability][Mutability] of the new descriptor.
		 * @param size
		 *   How many elements are in a tuple to be represented by the
		 *   descriptor.
		 * @return
		 *   A `NybbleTupleDescriptor` suitable for representing a nybble tuple
		 *   of the given mutability and [size][A_Tuple.tupleSize].
		 */
		private fun descriptorFor(
			flag: Mutability,
			size: Int): NybbleTupleDescriptor =
				descriptors[(size and 15) * 3 + flag.ordinal]!!

		init
		{
			var i = 0
			for (excess in intArrayOf(
				0, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1))
			{
				descriptors[i++] =
					NybbleTupleDescriptor(Mutability.MUTABLE, excess)
				descriptors[i++] =
					NybbleTupleDescriptor(Mutability.IMMUTABLE, excess)
				descriptors[i++] =
					NybbleTupleDescriptor(Mutability.SHARED, excess)
			}
		}
	}

	override fun mutable(): NybbleTupleDescriptor =
		descriptors[(16 - unusedNybblesOfLastLong and 15) * 3
					+ Mutability.MUTABLE.ordinal]!!

	override fun immutable(): NybbleTupleDescriptor =
		descriptors[(16 - unusedNybblesOfLastLong and 15) * 3
					+ Mutability.IMMUTABLE.ordinal]!!

	override fun shared(): NybbleTupleDescriptor =
		descriptors[(16 - unusedNybblesOfLastLong and 15) * 3
			 + Mutability.SHARED.ordinal]!!
}
