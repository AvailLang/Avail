/*
 * ByteTupleDescriptor.kt
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
import com.avail.descriptor.numbers.A_Number
import com.avail.descriptor.numbers.A_Number.Companion.extractInt
import com.avail.descriptor.numbers.A_Number.Companion.extractUnsignedByte
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromUnsignedByte
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.hashOfUnsignedByte
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.AvailObjectRepresentation.Companion.newLike
import com.avail.descriptor.representation.BitField
import com.avail.descriptor.representation.IntegerSlotsEnum
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.tuples.A_Tuple.Companion.compareFromToWithByteTupleStartingAt
import com.avail.descriptor.tuples.A_Tuple.Companion.concatenateWith
import com.avail.descriptor.tuples.A_Tuple.Companion.copyAsMutableIntTuple
import com.avail.descriptor.tuples.A_Tuple.Companion.copyAsMutableObjectTuple
import com.avail.descriptor.tuples.A_Tuple.Companion.treeTupleLevel
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleAtPuttingCanDestroy
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleIntAt
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import com.avail.descriptor.tuples.ByteTupleDescriptor.IntegerSlots.Companion.HASH_OR_ZERO
import com.avail.descriptor.tuples.ByteTupleDescriptor.IntegerSlots.RAW_LONG_AT_
import com.avail.descriptor.tuples.NybbleTupleDescriptor.Companion.mutableObjectOfSize
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.defaultType
import com.avail.descriptor.types.A_Type.Companion.isSubtypeOf
import com.avail.descriptor.types.A_Type.Companion.isSupertypeOfPrimitiveTypeEnum
import com.avail.descriptor.types.A_Type.Companion.rangeIncludesLong
import com.avail.descriptor.types.A_Type.Companion.sizeRange
import com.avail.descriptor.types.A_Type.Companion.typeAtIndex
import com.avail.descriptor.types.A_Type.Companion.typeTuple
import com.avail.descriptor.types.IntegerRangeTypeDescriptor
import com.avail.descriptor.types.TypeDescriptor.Types
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import com.avail.utility.json.JSONWriter
import java.nio.ByteBuffer

/**
 * `ByteTupleDescriptor` represents a tuple of integers that happen to fall in the range 0..255.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property unusedBytesOfLastLong
 *   The number of bytes of the last `long` that do not participate in the
 *   representation of the [byte tuple][ByteTupleDescriptor]. Must be between 0
 *   and 7.
 *
 * @constructor
 * Construct a new `ByteTupleDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param unusedBytesOfLastLong
 *   The number of bytes of the last `long` that do not participate in the
 *   representation of the [byte tuple][ByteTupleDescriptor]. Must be between 0
 *   and 7.
 */
class ByteTupleDescriptor private constructor(
	mutability: Mutability,
	private val unusedBytesOfLastLong: Int) : NumericTupleDescriptor(
		mutability, null, IntegerSlots::class.java)
{
	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum
	{
		/**
		 * The low 32 bits are used for the [.HASH_OR_ZERO], but the upper 32
		 * can be used by other [BitField]s in subclasses of [TupleDescriptor].
		 */
		@HideFieldInDebugger
		HASH_AND_MORE,

		/**
		 * The raw 64-bit machine words that constitute the representation of
		 * the [byte tuple][ByteTupleDescriptor].
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
		val originalSize = self.tupleSize()
		if (originalSize >= maximumCopySize || !newElement.isInt)
		{
			// Transition to a tree tuple.
			return self.concatenateWith(tuple(newElement), canDestroy)
		}
		val intValue = (newElement as A_Number).extractInt()
		if (intValue and 255.inv() != 0)
		{
			// Transition to a tree tuple.
			return self.concatenateWith(tuple(newElement), canDestroy)
		}
		val newSize = originalSize + 1
		if (isMutable && canDestroy && originalSize and 7 != 0)
		{
			// Enlarge it in place, using more of the final partial int field.
			self.setDescriptor(descriptorFor(Mutability.MUTABLE, newSize))
			self.setByteSlot(RAW_LONG_AT_, newSize, intValue.toShort())
			self.setSlot(HASH_OR_ZERO, 0)
			return self
		}
		// Copy to a potentially larger ByteTupleDescriptor.
		val result = newLike(
			descriptorFor(Mutability.MUTABLE, newSize),
			self,
			0,
			if (originalSize and 7 == 0) 1 else 0)
		result.setByteSlot(
			RAW_LONG_AT_, newSize, intValue.toShort())
		result.setSlot(HASH_OR_ZERO, 0)
		return result
	}// Answer approximately how many bits per entry are taken up by this
	// object.
	override fun o_BitsPerEntry(self: AvailObject): Int = 8

	override fun o_CompareFromToWithByteTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aByteTuple: A_Tuple,
		startIndex2: Int): Boolean
	{
		if (self.sameAddressAs(aByteTuple) && startIndex1 == startIndex2)
		{
			return true
		}
		// Compare actual bytes.
		var index1 = startIndex1
		var index2 = startIndex2
		while (index1 <= endIndex1)
		{
			if (self.tupleIntAt(index1) != aByteTuple.tupleIntAt(index2))
			{
				return false
			}
			index1++
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
			anotherObject.compareFromToWithByteTupleStartingAt(
				startIndex2,
				startIndex2 + endIndex1 - startIndex1,
				self,
				startIndex1)

	override fun o_ComputeHashFromTo(
		self: AvailObject,
		start: Int,
		end: Int): Int
	{
		// See comment in superclass. This method must produce the same value.
		var hash = 0
		for (index in end downTo start)
		{
			val itemHash = hashOfUnsignedByte(
				self.tupleIntAt(index).toShort()) xor preToggle
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
		if (otherTuple.isByteTuple && newSize <= maximumCopySize)
		{
			// Copy the bytes.
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
					descriptorFor(Mutability.MUTABLE, newSize), self, 0, deltaSlots)
			}
			var destination = size1 + 1
			var source = 1
			while (source <= size2)
			{
				result.setByteSlot(
					RAW_LONG_AT_,
					destination,
					otherTuple.tupleIntAt(source).toShort())
				source++
				destination++
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
			// Just copy the applicable bytes out.  In theory we could use
			// newLike() if start is 1.  Make sure to mask the last word in that
			// case.
			val result = mutableObjectOfSize(size)
			var destination = 1
			var src = start
			while (src <= end)
			{
				result.setByteSlot(
					RAW_LONG_AT_,
					destination,
					self.byteSlot(RAW_LONG_AT_, src))
				src++
				destination++
			}
			if (canDestroy)
			{
				self.assertObjectUnreachableIfMutable()
			}
			return result
		}
		return super.o_CopyTupleFromToCanDestroy(
			self, start, end, canDestroy)
	}

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsByteTuple(self)

	override fun o_EqualsByteTuple(
		self: AvailObject,
		aByteTuple: A_Tuple): Boolean
	{
		// First, check for object-structure (address) identity.
		when
		{
			self.sameAddressAs(aByteTuple) -> return true
			self.tupleSize() != aByteTuple.tupleSize() -> return false
			self.hash() != aByteTuple.hash() -> return false
			!self.compareFromToWithByteTupleStartingAt(
				1,
				self.tupleSize(),
				aByteTuple,
				1) -> return false
			// They're equal (but occupy disjoint storage). If possible, then
			// replace one with an indirection to the other to keep down the
			// frequency of byte-wise comparisons.
			!isShared ->
			{
				aByteTuple.makeImmutable()
				self.becomeIndirectionTo(aByteTuple)
			}
			!aByteTuple.descriptor().isShared ->
			{
				self.makeImmutable()
				aByteTuple.becomeIndirectionTo(self)
			}
		}
		return true
	}

	override fun o_IsByteTuple(self: AvailObject): Boolean = true

	override fun o_IsInstanceOfKind(
		self: AvailObject,
		aType: A_Type): Boolean
	{
		when
		{
			aType.isSupertypeOfPrimitiveTypeEnum(Types.NONTYPE) ->
				return true
			!aType.isTupleType -> return false
			//  See if it's an acceptable size...
			!aType.sizeRange().rangeIncludesLong(self.tupleSize().toLong()) ->
				return false

			//  tuple's size is in range.
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
				if (IntegerRangeTypeDescriptor.bytes
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
			self.setDescriptor(
				descriptorFor(Mutability.IMMUTABLE, self.tupleSize()))
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

	override fun o_TransferIntoByteBuffer(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int,
		outputByteBuffer: ByteBuffer)
	{
		for (index in startIndex .. endIndex)
		{
			outputByteBuffer.put(
				self.byteSlot(RAW_LONG_AT_, index).toByte())
		}
	}

	override fun o_TupleAt(
		self: AvailObject,
		index: Int): AvailObject
	{
		//  Answer the element at the given index in the tuple object.
		assert(index >= 1 && index <= self.tupleSize())
		return fromUnsignedByte(self.byteSlot(RAW_LONG_AT_, index))
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
		if (!newValueObject.isUnsignedByte)
		{
			return if (newValueObject.isInt)
			{
				self.copyAsMutableIntTuple().tupleAtPuttingCanDestroy(
					index, newValueObject, true)
			}
			else self.copyAsMutableObjectTuple().tupleAtPuttingCanDestroy(
				index, newValueObject, true)
		}
		val result =
			if (canDestroy && isMutable) self
			else newLike(mutable(), self, 0, 0)
		result.setByteSlot(
			RAW_LONG_AT_,
			index,
			(newValueObject as A_Number).extractUnsignedByte())
		result.setHashOrZero(0)
		return result
	}

	override fun o_TupleElementsInRangeAreInstancesOf(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int,
		type: A_Type): Boolean =
			(IntegerRangeTypeDescriptor.bytes.isSubtypeOf(type)
				|| super.o_TupleElementsInRangeAreInstancesOf(
					self, startIndex, endIndex, type))

	override fun o_TupleIntAt(self: AvailObject, index: Int): Int
	{
		assert(index >= 1 && index <= self.tupleSize())
		return self.byteSlot(RAW_LONG_AT_, index).toInt()
	}

	override fun o_TupleLongAt(self: AvailObject, index: Int): Long
	{
		assert(index >= 1 && index <= self.tupleSize())
		return self.byteSlot(RAW_LONG_AT_, index).toLong()
	}

	override fun o_TupleReverse(self: AvailObject): A_Tuple
	{
		val tupleSize = self.tupleSize()
		if (tupleSize in 1 until maximumCopySize)
		{
			// It's not empty and it's reasonably small.
			var sourceIndex = tupleSize
			return generateByteTupleFrom(tupleSize) {
				self.byteSlot(
					RAW_LONG_AT_, sourceIndex--).toInt()
			}
		}
		return super.o_TupleReverse(self)
	}

	override fun o_TupleSize(self: AvailObject): Int =
		(self.variableIntegerSlotsCount() shl 3) - unusedBytesOfLastLong

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startArray()
		var i = 1
		val limit = self.tupleSize()
		while (i <= limit)
		{
			writer.write(self.tupleIntAt(i))
			i++
		}
		writer.endArray()
	}

	companion object
	{
		/**
		 * Defined threshold for making copies versus using
		 * [TreeTupleDescriptor]/using other forms of reference instead of
		 * creating a new tuple.
		 */
		private const val maximumCopySize = 64

		/** The [ByteTupleDescriptor] instances.  */
		private val descriptors = arrayOfNulls<ByteTupleDescriptor>(8 * 3)

		/**
		 * Answer the appropriate `ByteTupleDescriptor descriptor` to represent
		 * an [object][AvailObject] of the specified mutability and size.
		 *
		 * @param flag
		 *   The [mutability][Mutability] of the new descriptor.
		 * @param size
		 *   The desired number of elements.
		 * @return
		 *   A `ByteTupleDescriptor descriptor`.
		 */
		private fun descriptorFor(
			flag: Mutability,
			size: Int): ByteTupleDescriptor =
				descriptors[(size and 7) * 3 + flag.ordinal]!!

		/**
		 * Build a mutable byte tuple with the specified number of zeroed
		 * elements.
		 *
		 * @param size
		 *   The number of bytes in the resulting tuple.
		 * @return
		 *   A byte tuple with the specified number of bytes (initially zero).
		 */
		@JvmStatic
		@ReferencedInGeneratedCode
		fun mutableObjectOfSize(size: Int): AvailObject
		{
			val descriptor = descriptorFor(Mutability.MUTABLE, size)
			assert(size + descriptor.unusedBytesOfLastLong and 7 == 0)
			return descriptor.create(size + 7 shr 3)
		}

		/** The [CheckedMethod] for [mutableObjectOfSize]. */
		@JvmField
		val createUninitializedByteTupleMethod: CheckedMethod = staticMethod(
			ByteTupleDescriptor::class.java,
			::mutableObjectOfSize.name,
			AvailObject::class.java,
			Int::class.javaPrimitiveType!!)

		/**
		 * Create an object of the appropriate size, whose descriptor is an
		 * instance of `ByteTupleDescriptor`.  Run the generator for each
		 * position in ascending order to produce the unsigned bytes (as shorts
		 * in the range [0..15]) with which to populate the tuple.
		 *
		 * @param size
		 *   The size of byte tuple to create.
		 * @param generator
		 *   A generator to provide unsigned bytes to store.
		 * @return
		 *   The new tuple.
		 */
		@JvmStatic
		fun generateByteTupleFrom(
			size: Int,
			generator: (Int) -> Int): AvailObject
		{
			val result = mutableObjectOfSize(size)
			var tupleIndex = 1
			// Aggregate eight writes at a time for the bulk of the tuple.
			var slotIndex = 1
			val limit = size ushr 3
			while (slotIndex <= limit)
			{
				var combined: Long = 0
				var shift = 0
				while (shift < 64)
				{
					val c = generator(tupleIndex++).toLong()
					assert(c and 255 == c)
					combined += c shl shift
					shift += 8
				}
				result.setSlot(RAW_LONG_AT_, slotIndex, combined)
				slotIndex++
			}
			// Do the last 0-7 writes the slow way.
			for (index in (size and 7.inv()) + 1 .. size)
			{
				val c = generator(tupleIndex++).toLong()
				assert(c and 255 == c)
				result.setByteSlot(RAW_LONG_AT_, index, c.toShort())
			}
			assert(tupleIndex == size + 1)
			return result
		}

		init
		{
			var i = 0
			for (excess in intArrayOf(0, 7, 6, 5, 4, 3, 2, 1))
			{
				descriptors[i++] =
					ByteTupleDescriptor(Mutability.MUTABLE, excess)
				descriptors[i++] =
					ByteTupleDescriptor(Mutability.IMMUTABLE, excess)
				descriptors[i++] =
					ByteTupleDescriptor(Mutability.SHARED, excess)
			}
		}
	}

	override fun mutable(): ByteTupleDescriptor =
		descriptors[(8 - unusedBytesOfLastLong and 7) * 3
					+ Mutability.MUTABLE.ordinal]!!

	override fun immutable(): ByteTupleDescriptor =
		descriptors[(8 - unusedBytesOfLastLong and 7) * 3
					+ Mutability.IMMUTABLE.ordinal]!!

	override fun shared(): ByteTupleDescriptor =
		descriptors[(8 - unusedBytesOfLastLong and 7) * 3
					+ Mutability.SHARED.ordinal]!!
}
