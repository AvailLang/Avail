/*
 * LongTupleDescriptor.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.descriptor.tuples

import avail.annotations.HideFieldInDebugger
import avail.descriptor.numbers.A_Number.Companion.extractLong
import avail.descriptor.numbers.A_Number.Companion.greaterThan
import avail.descriptor.numbers.A_Number.Companion.isLong
import avail.descriptor.numbers.A_Number.Companion.lessThan
import avail.descriptor.numbers.IntegerDescriptor.Companion.computeHashOfLong
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromLong
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.multiplier
import avail.descriptor.representation.AvailObjectRepresentation.Companion.newLike
import avail.descriptor.representation.BitField
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.tuples.A_Tuple.Companion.compareFromToWithByteTupleStartingAt
import avail.descriptor.tuples.A_Tuple.Companion.compareFromToWithIntTupleStartingAt
import avail.descriptor.tuples.A_Tuple.Companion.concatenateWith
import avail.descriptor.tuples.A_Tuple.Companion.copyAsMutableObjectTuple
import avail.descriptor.tuples.A_Tuple.Companion.treeTupleLevel
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleAtPuttingCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.tupleLongAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.IntTupleDescriptor.Companion.generateIntTupleFrom
import avail.descriptor.tuples.LongTupleDescriptor.IntegerSlots.Companion.HASH_OR_ZERO
import avail.descriptor.tuples.LongTupleDescriptor.IntegerSlots.LONG_AT_
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.TreeTupleDescriptor.Companion.concatenateAtLeastOneTree
import avail.descriptor.tuples.TreeTupleDescriptor.Companion.createTwoPartTreeTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.defaultType
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.isSupertypeOfPrimitiveTypeEnum
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.rangeIncludesLong
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.typeTuple
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.int64
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.optimizer.jvm.CheckedMethod
import avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import avail.optimizer.jvm.ReferencedInGeneratedCode
import org.availlang.json.JSONWriter
import java.nio.ByteBuffer
import kotlin.math.min

/**
 * `LongTupleDescriptor` efficiently represents a tuple of integers that happen
 * to fall in the range of a [Long], which is `[-2^31..2^31-1]`.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *   Construct a new `LongTupleDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 */
class LongTupleDescriptor
private constructor(
	mutability: Mutability
) : NumericTupleDescriptor(mutability, null, IntegerSlots::class.java)
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
		 * the [long&#32;tuple][LongTupleDescriptor].
		 */
		LONG_AT_;

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
		val newElementStrong = newElement as AvailObject
		val originalSize = self.tupleSize
		if (!newElementStrong.isLong)
		{
			// Transition to a tree tuple because it's not a long.
			val singleton = tuple(newElement)
			return self.concatenateWith(singleton, canDestroy)
		}
		val longValue = newElementStrong.extractLong
		if (originalSize >= maximumCopySize)
		{
			// Transition to a tree tuple because it's too big.
			val singleton: A_Tuple = generateLongTupleFrom(1) { longValue }
			return self.concatenateWith(singleton, canDestroy)
		}
		val newSize = originalSize + 1
		// Copy to a larger LongTupleDescriptor.
		val result = newLike(mutable, self, 0, 1)
		result.setSlot(LONG_AT_, newSize, longValue)
		result.setSlot(HASH_OR_ZERO, 0)
		return result
	}

	// Answer approximately how many bits per entry are taken up by this
	// object.
	override fun o_BitsPerEntry(self: AvailObject): Int = 64

	override fun o_CompareFromToWithByteTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aByteTuple: A_Tuple,
		startIndex2: Int): Boolean
	{
		// Compare the argument's bytes to my longs.
		var index2 = startIndex2
		return (startIndex1..endIndex1).all {
			self.slot(LONG_AT_, it) == aByteTuple.tupleLongAt(index2++)
		}
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
		// Compare the argument's bytes to my longs.
		var index2 = startIndex2
		return (startIndex1..endIndex1).all {
			(self.slot(LONG_AT_, it) == anIntTuple.tupleLongAt(index2++))
		}
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
				computeHashOfLong(self.slot(LONG_AT_, index)) xor preToggle
			hash = (hash + itemHash) * multiplier
		}
		return hash
	}

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
		if (otherTuple.isLongTuple && newSize <= maximumCopySize)
		{
			// Copy the longs.
			val deltaSlots = newSize - self.variableIntegerSlotsCount()
			val result: AvailObject = newLike(mutable, self, 0, deltaSlots)
			var destination = size1 + 1
			(1..size2).forEach {
				result.setSlot(
					LONG_AT_, destination++, otherTuple.tupleLongAt(it))
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

	/**
	 * Answer a mutable copy of object that holds int objects.
	 */
	override fun o_CopyAsMutableIntTuple(self: AvailObject): A_Tuple
	{
		// Verify that the values are in range.
		return generateIntTupleFrom(self.tupleSize) {
			self.slot(LONG_AT_, it).toInt()
		}
	}

	/**
	 * Answer a mutable copy of object that holds int objects.
	 */
	override fun o_CopyAsMutableLongTuple(self: AvailObject): A_Tuple
	{
		return newLike(mutable(), self, 0, 0)
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
		if (size in 1 until tupleSize && size < maximumCopySize)
		{
			// It's not empty, it's not a total copy, and it's reasonably small.
			// Just copy the applicable longs out.  In theory we could use
			// newLike() if start is 1.
			var source = start
			val result = generateLongTupleFrom(size) {
				self.slot(LONG_AT_, source++)
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
		another: A_BasicObject
	): Boolean = another.equalsLongTuple(self)

	override fun o_EqualsByteTuple(
		self: AvailObject,
		aByteTuple: A_Tuple): Boolean
	{
		when
		{
			self.tupleSize != aByteTuple.tupleSize -> return false
			self.hash() != aByteTuple.hash() -> return false
			!self.compareFromToWithByteTupleStartingAt(
				1, self.tupleSize, aByteTuple, 1) -> return false
			// They're equal (but occupy disjoint storage). If possible, replace
			// one with an indirection to the other to keep down the frequency
			// of byte/long-wise comparisons.  Prefer the byte representation if
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
		when
		{
			self.tupleSize != anIntTuple.tupleSize -> return false
			self.hash() != anIntTuple.hash() -> return false
			!self.compareFromToWithIntTupleStartingAt(
				1, self.tupleSize, anIntTuple, 1) -> return false
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
		// First, check for object-structure (address) identity.
		val strongLongTuple = aLongTuple as AvailObject
		when
		{
			self.sameAddressAs(aLongTuple) -> return true
			self.tupleSize != aLongTuple.tupleSize -> return false
			self.hash() != aLongTuple.hash() -> return false
			(1..self.tupleSize).any {
				self.slot(LONG_AT_, it) != strongLongTuple.slot(LONG_AT_, it)
			} -> return false
			// They're equal (but occupy disjoint storage). If possible, then
			// replace one with an indirection to the other to keep down the
			// frequency of long-wise comparisons.
			!isShared ->
			{
				aLongTuple.makeImmutable()
				self.becomeIndirectionTo(aLongTuple)
			}
			!aLongTuple.descriptor().isShared ->
			{
				self.makeImmutable()
				aLongTuple.becomeIndirectionTo(self)
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
		val tupleSize = self.tupleSize
		if (tupleSize <= 10)
		{
			for (i in 1 .. tupleSize)
			{
				val element = self.slot(LONG_AT_, i)
				if (element != element and 255)
				{
					return false
				}
			}
			return true
		}
		return false
	}

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
		if (!aType.sizeRange.rangeIncludesLong(self.tupleSize.toLong()))
		{
			return false
		}
		//  tuple's size is in range.
		val typeTuple = aType.typeTuple
		val breakIndex = min(self.tupleSize, typeTuple.tupleSize)
		for (i in 1 .. breakIndex)
		{
			if (!typeTuple.tupleAt(i).rangeIncludesLong(self.slot(LONG_AT_, i)))
				return false
		}
		if (!(1 .. breakIndex).all {
				self.tupleAt(it).isInstanceOf(typeTuple.tupleAt(it))
			})
		{
			return false
		}
		val defaultTypeObject = aType.defaultType
		if (int64.isSubtypeOf(defaultTypeObject))
		{
			return true
		}
		return (breakIndex + 1 .. self.tupleSize).all {
			self.tupleAt(it).isInstanceOf(defaultTypeObject)
		}
	}

	override fun o_IsLongTuple(self: AvailObject): Boolean = true

	override fun o_TransferIntoByteBuffer(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int,
		outputByteBuffer: ByteBuffer)
	{
		for (index in startIndex .. endIndex)
		{
			val mustBeByte = self.slot(LONG_AT_, index)
			assert(mustBeByte == mustBeByte and 255)
			outputByteBuffer.put(mustBeByte.toByte())
		}
	}

	override fun o_TupleAt(
		self: AvailObject,
		index: Int): AvailObject
	{
		// Answer the element at the given index in the tuple object.
		return fromLong(self.slot(LONG_AT_, index))
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
		assert(index >= 1 && index <= self.tupleSize)
		val newValueStrong = newValueObject as AvailObject
		if (!newValueStrong.isLong)
		{
			return self.copyAsMutableObjectTuple().tupleAtPuttingCanDestroy(
				index, newValueObject, true)
		}
		val result =
			if (canDestroy && isMutable) self
			else newLike(mutable(), self, 0, 0)
		result.setSlot(LONG_AT_, index, newValueStrong.extractLong)
		result.setSlot(HASH_OR_ZERO, 0)
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
			int64.isSubtypeOf(type) -> true
			startIndex > endIndex -> true
			type.isEnumeration -> super.o_TupleElementsInRangeAreInstancesOf(
				self, startIndex, endIndex, type)
			!type.isIntegerRangeType -> false
			// It must be an integer range kind.  Find the bounds.
			else ->
			{
				val lowerObject = type.lowerBound
				val lower = when
				{
					lowerObject.isLong -> lowerObject.extractLong
					lowerObject.lessThan(zero) -> Long.MIN_VALUE
					else -> return false
				}
				val upperObject = type.upperBound
				val upper = when
				{
					upperObject.isLong -> upperObject.extractLong
					upperObject.greaterThan(zero) -> Long.MAX_VALUE
					else -> return false
				}
				(startIndex .. endIndex).all {
					self.slot(LONG_AT_, it) in lower .. upper
				}
			}
		}
	}

	override fun o_TupleIntAt(self: AvailObject, index: Int): Int
	{
		val longValue = self.slot(LONG_AT_, index)
		val intValue = longValue.toInt()
		assert(intValue.toLong() == longValue)
		return intValue
	}

	override fun o_TupleLongAt(self: AvailObject, index: Int): Long =
		self.slot(LONG_AT_, index)

	override fun o_TupleReverse(self: AvailObject): A_Tuple
	{
		val tupleSize = self.tupleSize
		if (tupleSize <= 1)
		{
			return self
		}
		if (tupleSize < maximumCopySize)
		{
			// It's not empty or singular, but it's reasonably small.
			var i = tupleSize
			return generateLongTupleFrom(tupleSize) {
				self.slot(LONG_AT_, i--)
			}
		}
		return super.o_TupleReverse(self)
	}

	override fun o_TupleSize(self: AvailObject): Int =
		self.variableIntegerSlotsCount()

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startArray()
		self.forEach(writer::write)
		writer.endArray()
	}

	override fun mutable() = mutable

	override fun immutable() = immutable

	override fun shared() = shared

	companion object
	{
		/**
		 * Defined threshold for making copies versus using
		 * [TreeTupleDescriptor]/using other forms of reference instead of
		 * creating a new tuple.
		 */
		private const val maximumCopySize = 32

		/**
		 * Build a mutable long tuple with room for the specified number of
		 * elements.
		 *
		 * @param size
		 *   The number of longs in the resulting tuple.
		 * @return
		 *   A long tuple with the specified number of longs (initially zero).
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun mutableObjectOfSize(size: Int): AvailObject = mutable.create(size)

		/** The [CheckedMethod] for [mutableObjectOfSize]. */
		val createUninitializedLongTupleMethod = staticMethod(
			LongTupleDescriptor::class.java,
			::mutableObjectOfSize.name,
			AvailObject::class.java,
			Int::class.javaPrimitiveType!!)

		/**
		 * Create an object of the appropriate size, whose descriptor is an
		 * instance of [LongTupleDescriptor].  Run the generator for each
		 * position in ascending order to produce the [Long]s with which to
		 * populate the tuple.
		 *
		 * @param size
		 *   The size of long-tuple to create.
		 * @param generator
		 *   A generator to provide [Long]s to store.
		 * @return
		 *   The new [A_Tuple].
		 */
		fun generateLongTupleFrom(
			size: Int,
			generator: (Int) -> Long): AvailObject
		{
			return mutable.create(size) {
				for (i in 1..size)
				{
					setSlot(LONG_AT_, i, generator(i))
				}
			}
		}

		/** The mutable [LongTupleDescriptor]. */
		private val mutable = LongTupleDescriptor(Mutability.MUTABLE)

		/** The immutable [LongTupleDescriptor]. */
		private val immutable = LongTupleDescriptor(Mutability.IMMUTABLE)

		/** The shared [LongTupleDescriptor]. */
		private val shared = LongTupleDescriptor(Mutability.SHARED)
	}
}
