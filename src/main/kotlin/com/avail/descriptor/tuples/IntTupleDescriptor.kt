/*
 * IntTupleDescriptor.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
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
import com.avail.descriptor.numbers.A_Number.Companion.greaterThan
import com.avail.descriptor.numbers.A_Number.Companion.lessThan
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.computeHashOfInt
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.AvailObject.Companion.newIndexedDescriptor
import com.avail.descriptor.representation.AvailObjectRepresentation.Companion.newLike
import com.avail.descriptor.representation.BitField
import com.avail.descriptor.representation.IntegerSlotsEnum
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.tuples.A_Tuple.Companion.compareFromToWithByteTupleStartingAt
import com.avail.descriptor.tuples.A_Tuple.Companion.compareFromToWithIntTupleStartingAt
import com.avail.descriptor.tuples.A_Tuple.Companion.concatenateWith
import com.avail.descriptor.tuples.A_Tuple.Companion.copyAsMutableObjectTuple
import com.avail.descriptor.tuples.A_Tuple.Companion.treeTupleLevel
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleAtPuttingCanDestroy
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleIntAt
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import com.avail.descriptor.tuples.IntTupleDescriptor.IntegerSlots.Companion.HASH_OR_ZERO
import com.avail.descriptor.tuples.IntTupleDescriptor.IntegerSlots.RAW_LONG_AT_
import com.avail.descriptor.tuples.LongTupleDescriptor.Companion.generateLongTupleFrom
import com.avail.descriptor.tuples.NybbleTupleDescriptor.Companion.mutableObjectOfSize
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.TreeTupleDescriptor.Companion.concatenateAtLeastOneTree
import com.avail.descriptor.tuples.TreeTupleDescriptor.Companion.createTwoPartTreeTuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.defaultType
import com.avail.descriptor.types.A_Type.Companion.isSubtypeOf
import com.avail.descriptor.types.A_Type.Companion.isSupertypeOfPrimitiveTypeEnum
import com.avail.descriptor.types.A_Type.Companion.lowerBound
import com.avail.descriptor.types.A_Type.Companion.rangeIncludesLong
import com.avail.descriptor.types.A_Type.Companion.sizeRange
import com.avail.descriptor.types.A_Type.Companion.typeTuple
import com.avail.descriptor.types.A_Type.Companion.upperBound
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.int32
import com.avail.descriptor.types.TypeDescriptor.Types
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import com.avail.utility.json.JSONWriter
import java.nio.ByteBuffer
import kotlin.math.min

/**
 * `IntTupleDescriptor` efficiently represents a tuple of integers that happen
 * to fall in the range of a Java `int`, which is
 * [-2<sup>31</sup>..2<sup>31</sup>-1].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property unusedIntsOfLastLong
 *   The number of ints of the last `long` that do not participate in the
 *   representation of the [tuple][IntTupleDescriptor]. Must be 0 or 1.
 *
 * @constructor
 * Construct a new `IntTupleDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param unusedIntsOfLastLong
 *   The number of ints of the last `long` that do not participate in the
 *   representation of the [tuple][IntTupleDescriptor]. Must be 0 or 1.
 */
class IntTupleDescriptor private constructor(
	mutability: Mutability,
	private val unusedIntsOfLastLong: Int) : NumericTupleDescriptor(
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
		 * the [int32&#32;tuple][IntTupleDescriptor].
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
		if (!newElement.isInt)
		{
			// Transition to a tree tuple because it's not an int.
			val singleton = tuple(newElement)
			return self.concatenateWith(singleton, canDestroy)
		}
		val intValue = (newElement as AvailObject).extractInt()
		if (originalSize >= maximumCopySize)
		{
			// Transition to a tree tuple because it's too big.
			val singleton: A_Tuple = generateIntTupleFrom(1) { intValue }
			return self.concatenateWith(singleton, canDestroy)
		}
		val newSize = originalSize + 1
		if (isMutable && canDestroy && originalSize and 1 != 0)
		{
			// Enlarge it in place, using more of the final partial int field.
			self.setDescriptor(descriptorFor(Mutability.MUTABLE, newSize))
			self.setIntSlot(RAW_LONG_AT_, newSize, intValue)
			self.setSlot(HASH_OR_ZERO, 0)
			return self
		}
		// Copy to a potentially larger IntTupleDescriptor.
		val result = newLike(
			descriptorFor(Mutability.MUTABLE, newSize),
			self,
			0,
			if (originalSize and 1 == 0) 1 else 0)
		result.setIntSlot(RAW_LONG_AT_, newSize, intValue)
		result.setSlot(HASH_OR_ZERO, 0)
		return result
	}

	// Answer approximately how many bits per entry are taken up by this
	// object.
	override fun o_BitsPerEntry(self: AvailObject): Int = 32

	override fun o_CompareFromToWithByteTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aByteTuple: A_Tuple,
		startIndex2: Int): Boolean
	{
		// Compare the argument's bytes to my ints.
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

	override fun o_CompareFromToWithIntTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anIntTuple: A_Tuple,
		startIndex2: Int): Boolean
	{
		if (self.sameAddressAs(anIntTuple) && startIndex1 == startIndex2)
		{
			return true
		}
		// Compare the argument's bytes to my ints.
		var index1 = startIndex1
		var index2 = startIndex2
		while (index1 <= endIndex1)
		{
			if (self.tupleIntAt(index1) != anIntTuple.tupleIntAt(index2))
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
			anotherObject.compareFromToWithIntTupleStartingAt(
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
			val itemHash =
				computeHashOfInt(self.tupleIntAt(index)) xor preToggle
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
		if (otherTuple.isIntTuple && newSize <= maximumCopySize)
		{
			// Copy the ints.
			val newLongCount = newSize + 1 ushr 1
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
					descriptorFor(
						Mutability.MUTABLE, newSize), self, 0, deltaSlots)
			}
			var destination = size1 + 1
			var source = 1
			while (source <= size2)
			{
				result.setIntSlot(
					RAW_LONG_AT_,
					destination,
					otherTuple.tupleIntAt(source))
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
			createTwoPartTreeTuple(self, otherTuple, 1, 0)
		}
		else
		{
			concatenateAtLeastOneTree(self, otherTuple, true)
		}
	}

	override fun o_CopyAsMutableIntTuple(self: AvailObject): A_Tuple
	{
		return newLike(mutable(), self, 0, 0)
	}

	override fun o_CopyAsMutableLongTuple(self: AvailObject): A_Tuple =
		generateLongTupleFrom(self.tupleSize()) {
			self.tupleIntAt(it).toLong()
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
			// Just copy the applicable ints out.  In theory we could use
			// newLike() if start is 1.  Make sure to mask the last long in that
			// case.
			var source = start
			val result = generateIntTupleFrom(size) {
				self.intSlot(RAW_LONG_AT_, source++)
			}
			if (canDestroy)
			{
				self.assertObjectUnreachableIfMutable()
			}
			return result
		}
		return super.o_CopyTupleFromToCanDestroy(self, start, end, canDestroy)
	}

	override fun o_Equals(
		self: AvailObject,
		another: A_BasicObject): Boolean = another.equalsIntTuple(self)

	override fun o_EqualsByteTuple(
		self: AvailObject,
		aByteTuple: A_Tuple): Boolean
	{
		when
		{
			self.tupleSize() != aByteTuple.tupleSize() -> return false
			self.hash() != aByteTuple.hash() -> return false
			!self.compareFromToWithByteTupleStartingAt(
				1,
				self.tupleSize(),
				aByteTuple,
				1) -> return false
			// They're equal (but occupy disjoint storage). If possible, replace
			// one with an indirection to the other to keep down the frequency
			// of byte/int-wise comparisons.  Prefer the byte representation if
			// there's a choice.
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

	override fun o_EqualsIntTuple(
		self: AvailObject,
		anIntTuple: A_Tuple): Boolean
	{
		// First, check for object-structure (address) identity.
		when
		{
			self.sameAddressAs(anIntTuple) -> return true
			self.tupleSize() != anIntTuple.tupleSize() -> return false
			self.hash() != anIntTuple.hash() -> return false
			!self.compareFromToWithIntTupleStartingAt(
				1,
				self.tupleSize(),
				anIntTuple,
				1) -> return false
			// They're equal (but occupy disjoint storage). If possible, then
			// replace one with an indirection to the other to keep down the
			// frequency of int-wise comparisons.
			!isShared ->
			{
				anIntTuple.makeImmutable()
				self.becomeIndirectionTo(anIntTuple)
			}
			!anIntTuple.descriptor().isShared ->
			{
				self.makeImmutable()
				anIntTuple.becomeIndirectionTo(self)
			}
		}
		return true
	}

	override fun o_EqualsLongTuple(
		self: AvailObject,
		aLongTuple: A_Tuple): Boolean
	{
		when
		{
			self.tupleSize() != aLongTuple.tupleSize() -> return false
			self.hash() != aLongTuple.hash() -> return false
			(1..self.tupleSize()).any {
				self.intSlot(RAW_LONG_AT_, it) != aLongTuple.tupleIntAt(it)
			} -> return false
			// They're equal (but occupy disjoint storage). If possible, then
			// replace one with an indirection to the other to keep down the
			// frequency of long-wise comparisons.
			!aLongTuple.descriptor().isShared ->
			{
				self.makeImmutable()
				aLongTuple.becomeIndirectionTo(self)
			}
			!isShared ->
			{
				aLongTuple.makeImmutable()
				self.becomeIndirectionTo(aLongTuple)
			}
		}
		return true
	}

	override fun o_IsByteTuple(self: AvailObject): Boolean
	{
		// If it's cheap to check my elements, just do it.  This can help keep
		// representations smaller and faster when concatenating short, quickly
		// built int tuples that happen to only contain bytes onto the start
		// or end of other byte tuples.
		val tupleSize = self.tupleSize()
		if (tupleSize <= 10)
		{
			for (i in 1 .. tupleSize)
			{
				val element = self.intSlot(RAW_LONG_AT_, i)
				if (element != element and 255)
				{
					return false
				}
			}
			return true
		}
		return false
	}

	override fun o_IsIntTuple(self: AvailObject): Boolean = true

	override fun o_IsInstanceOfKind(
		self: AvailObject,
		aType: A_Type): Boolean
	{
		if (aType.isSupertypeOfPrimitiveTypeEnum(Types.NONTYPE))
		{
			return true
		}
		if (!aType.isTupleType)
		{
			return false
		}
		//  See if it's an acceptable size...
		if (!aType.sizeRange().rangeIncludesLong(self.tupleSize().toLong()))
		{
			return false
		}
		//  tuple's size is in range.
		val typeTuple = aType.typeTuple()
		val breakIndex = min(self.tupleSize(), typeTuple.tupleSize())
		for (i in 1 .. breakIndex)
		{
			if (!self.tupleAt(i).isInstanceOf(typeTuple.tupleAt(i)))
			{
				return false
			}
		}
		val defaultTypeObject = aType.defaultType()
		if (int32.isSubtypeOf(defaultTypeObject))
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
			val mustBeByte = self.intSlot(RAW_LONG_AT_, index)
			assert(mustBeByte == mustBeByte and 255)
			outputByteBuffer.put(mustBeByte.toByte())
		}
	}

	override fun o_TupleAt(
		self: AvailObject,
		index: Int): AvailObject
	{
		// Answer the element at the given index in the tuple object.
		assert(index >= 1 && index <= self.tupleSize())
		return fromInt(self.intSlot(RAW_LONG_AT_, index))
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
		if (!newValueObject.isInt)
		{
			return self.copyAsMutableObjectTuple().tupleAtPuttingCanDestroy(
				index,
				newValueObject,
				true)
		}
		val result =
			if (canDestroy && isMutable) self
			else newLike(mutable(), self, 0, 0)
		result.setIntSlot(
			RAW_LONG_AT_,
			index,
			(newValueObject as A_Number).extractInt())
		result.setHashOrZero(0)
		return result
	}

	override fun o_TupleElementsInRangeAreInstancesOf(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int,
		type: A_Type): Boolean
	{
		return when
		{
			int32.isSubtypeOf(type) -> true
			startIndex > endIndex -> true
			type.isEnumeration -> super.o_TupleElementsInRangeAreInstancesOf(
				self, startIndex, endIndex, type)
			!type.isIntegerRangeType -> false
			// It must be an integer range kind.  Find the bounds.
			else ->
			{
				val lowerObject = type.lowerBound()
				val lower = when
				{
					lowerObject.isInt -> lowerObject.extractInt()
					lowerObject.lessThan(zero) -> Int.MIN_VALUE
					else -> return false
				}
				val upperObject = type.upperBound()
				val upper = when
				{
					upperObject.isInt -> upperObject.extractInt()
					upperObject.greaterThan(zero) -> Int.MAX_VALUE
					else -> return false
				}
				(startIndex .. endIndex).all {
					self.intSlot(RAW_LONG_AT_, it) in lower .. upper
				}
			}
		}
	}

	override fun o_TupleIntAt(
		self: AvailObject,
		index: Int): Int
	{
		assert(index >= 1 && index <= self.tupleSize())
		return self.intSlot(RAW_LONG_AT_, index)
	}

	override fun o_TupleLongAt(
		self: AvailObject,
		index: Int): Long
	{
		assert(index >= 1 && index <= self.tupleSize())
		return self.intSlot(RAW_LONG_AT_, index).toLong()
	}

	override fun o_TupleReverse(self: AvailObject): A_Tuple
	{
		val tupleSize = self.tupleSize()
		if (tupleSize <= 1)
		{
			return self
		}
		if (tupleSize < maximumCopySize)
		{
			// It's not empty or singular, but it's reasonably small.
			var i = tupleSize
			return generateIntTupleFrom(tupleSize) {
				self.intSlot(RAW_LONG_AT_, i--)
			}
		}
		return super.o_TupleReverse(self)
	}

	override fun o_TupleSize(self: AvailObject): Int =
		(self.variableIntegerSlotsCount() shl 1) - unusedIntsOfLastLong

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
		private const val maximumCopySize = 32

		/** The [IntTupleDescriptor] instances.  */
		private val descriptors = arrayOfNulls<IntTupleDescriptor>(2 * 3)

		/**
		 * Answer the appropriate `IntTupleDescriptor descriptor` to represent
		 * an [object][AvailObject] of the specified mutability and size.
		 *
		 * @param flag
		 *   The [mutability][Mutability] of the new descriptor.
		 * @param size
		 *   The desired number of elements.
		 * @return
		 *   An `IntTupleDescriptor`.
		 */
		private fun descriptorFor(
			flag: Mutability,
			size: Int): IntTupleDescriptor =
				descriptors[(size and 1) * 3 + flag.ordinal]!!

		/**
		 * Build a mutable int tuple with room for the specified number of
		 * elements.
		 *
		 * @param size
		 *   The number of ints in the resulting tuple.
		 * @return
		 *   An int tuple with the specified number of ints (initially zero).
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun mutableObjectOfSize(size: Int): AvailObject
		{
			val descriptor = descriptorFor(Mutability.MUTABLE, size)
			assert(size + descriptor.unusedIntsOfLastLong and 1 == 0)
			return descriptor.create(size + 1 ushr 1)
		}

		/** The [CheckedMethod] for [mutableObjectOfSize]. */
		val createUninitializedIntTupleMethod: CheckedMethod = staticMethod(
			IntTupleDescriptor::class.java,
			::mutableObjectOfSize.name,
			AvailObject::class.java,
			Int::class.javaPrimitiveType!!)

		/**
		 * Create an object of the appropriate size, whose descriptor is an
		 * instance of `IntTupleDescriptor`.  Run the generator for each
		 * position in ascending order to produce the `int`s with which to
		 * populate the tuple.
		 *
		 * @param size
		 *   The size of int-tuple to create.
		 * @param generator
		 *   A generator to provide ints to store.
		 * @return
		 *   The new tuple.
		 */
		fun generateIntTupleFrom(
			size: Int,
			generator: (Int) -> Int): AvailObject
		{
			val descriptor = descriptorFor(Mutability.MUTABLE, size)
			val result = newIndexedDescriptor(size + 1 ushr 1, descriptor)
			var tupleIndex = 1
			// Aggregate two writes at a time for the bulk of the tuple.
			var slotIndex = 1
			val limit = size ushr 1
			while (slotIndex <= limit)
			{
				var combined =
					(generator(tupleIndex++).toLong() and 0xFFFFFFFFL)
				combined += generator(tupleIndex++).toLong() shl 32
				result.setSlot(RAW_LONG_AT_, slotIndex, combined)
				slotIndex++
			}
			if (size and 1 == 1)
			{
				// Do the last (odd) write the slow way.  Assume the upper int
				// was zeroed.
				result.setIntSlot(
					RAW_LONG_AT_, size, generator(tupleIndex++))
			}
			assert(tupleIndex == size + 1)
			return result
		}

		init
		{
			var i = 0
			for (excess in intArrayOf(0, 1))
			{
				for (mut in Mutability.values())
				{
					descriptors[i++] =
						IntTupleDescriptor(mut, excess)
				}
			}
		}
	}

	override fun mutable(): IntTupleDescriptor
	{
		return descriptors[(unusedIntsOfLastLong and 1) * 3 + Mutability.MUTABLE.ordinal]!!
	}

	override fun immutable(): IntTupleDescriptor
	{
		return descriptors[(unusedIntsOfLastLong and 1) * 3 + Mutability.IMMUTABLE.ordinal]!!
	}

	override fun shared(): IntTupleDescriptor
	{
		return descriptors[(unusedIntsOfLastLong and 1) * 3 + Mutability.SHARED.ordinal]!!
	}
}
