/*
 * ByteBufferTupleDescriptor.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.extractUnsignedByte
import avail.descriptor.numbers.A_Number.Companion.isInt
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromUnsignedByte
import avail.descriptor.numbers.IntegerDescriptor.Companion.hashOfUnsignedByte
import avail.descriptor.pojos.RawPojoDescriptor
import avail.descriptor.pojos.RawPojoDescriptor.Companion.identityPojo
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AbstractDescriptor
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.BitField
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.tuples.A_Tuple.Companion.byteBuffer
import avail.descriptor.tuples.A_Tuple.Companion.compareFromToWithByteBufferTupleStartingAt
import avail.descriptor.tuples.A_Tuple.Companion.concatenateWith
import avail.descriptor.tuples.A_Tuple.Companion.copyAsMutableIntTuple
import avail.descriptor.tuples.A_Tuple.Companion.copyAsMutableObjectTuple
import avail.descriptor.tuples.A_Tuple.Companion.transferIntoByteBuffer
import avail.descriptor.tuples.A_Tuple.Companion.treeTupleLevel
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleAtPuttingCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ByteBufferTupleDescriptor.IntegerSlots.Companion.HASH_OR_ZERO
import avail.descriptor.tuples.ByteBufferTupleDescriptor.ObjectSlots.BYTE_BUFFER
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.defaultType
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.isSupertypeOfPrimitiveTypeEnum
import avail.descriptor.types.A_Type.Companion.rangeIncludesLong
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.A_Type.Companion.typeTuple
import avail.descriptor.types.IntegerRangeTypeDescriptor
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import org.availlang.json.JSONWriter
import java.nio.ByteBuffer
import kotlin.experimental.and

/**
 * `ByteBufferTupleDescriptor` represents a tuple of integers that happen to
 * fall in the range `[0..255]`. Unlike [ByteTupleDescriptor], it is backed by a
 * [thinly wrapped][RawPojoDescriptor] [byte buffer][ByteBuffer].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct a new `ByteBufferTupleDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 */
class ByteBufferTupleDescriptor constructor(mutability: Mutability)
	: NumericTupleDescriptor(
		mutability, ObjectSlots::class.java, IntegerSlots::class.java)
{
	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum
	{
		/**
		 * The low 32 bits are used for the [HASH_OR_ZERO], but the upper 32
		 * can be used by other [BitField]s in subclasses of [TupleDescriptor].
		 */
		@HideFieldInDebugger
		HASH_AND_MORE;

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

	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * A [raw pojo][RawPojoDescriptor] wrapping the
		 * [byte&#32;buffer][ByteBuffer] that backs this
		 * [tuple][ByteBufferTupleDescriptor].
		 */
		BYTE_BUFFER
	}

	override fun o_AppendCanDestroy(
		self: AvailObject,
		newElement: A_BasicObject,
		canDestroy: Boolean): A_Tuple
	{
		val originalSize = self.tupleSize
		val newElementStrong = newElement as AvailObject
		if (originalSize < maximumCopySize && newElementStrong.isInt)
		{
			val intValue = newElementStrong.extractInt
			if (intValue and 255.inv() == 0)
			{
				// Convert to a ByteTupleDescriptor.
				val buffer = self.slot(BYTE_BUFFER)
					.javaObjectNotNull<ByteBuffer>()
				val newSize = originalSize + 1
				return ByteTupleDescriptor.generateByteTupleFrom(newSize) {
					when
					{
						it < newSize -> buffer[it - 1].toInt() and 255
						else -> intValue
					}
				}
			}
		}
		// Transition to a tree tuple.
		val singleton = tuple(newElement)
		return self.concatenateWith(singleton, canDestroy)
	}

	override fun o_ByteBuffer(self: AvailObject): ByteBuffer =
		self.slot(BYTE_BUFFER).javaObjectNotNull()

	override fun o_ComputeHashFromTo(
		self: AvailObject,
		start: Int,
		end: Int): Int
	{
		// See comment in superclass. This method must produce the same value.
		val buffer = self.slot(BYTE_BUFFER)
			.javaObjectNotNull<ByteBuffer>()
		var hash = 0
		var index = end - 1
		val first = start - 1
		while (index >= first)
		{
			val itemHash = hashOfUnsignedByte(
				buffer[index].toShort() and 0xFF) xor preToggle
			hash = (hash + itemHash) * AvailObject.multiplier
			index--
		}
		return hash
	}

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsByteBufferTuple(self)

	override fun o_EqualsByteBufferTuple(
		self: AvailObject,
		aByteBufferTuple: A_Tuple): Boolean
	{
		// First, check for object-structure (address) identity.
		when
		{
			self.sameAddressAs(aByteBufferTuple) -> return true
			self.byteBuffer === aByteBufferTuple.byteBuffer -> return true
			self.tupleSize != aByteBufferTuple.tupleSize -> return false
			self.hash() != aByteBufferTuple.hash() -> return false
			!self.compareFromToWithByteBufferTupleStartingAt(
				1,
				self.tupleSize,
				aByteBufferTuple,
				1) -> return false
			// They're equal, but occupy disjoint storage. If possible, then
			// replace one with an indirection to the other to keep down the
			// frequency of byte-wise comparisons.
			!isShared ->
			{
				aByteBufferTuple.makeImmutable()
				self.becomeIndirectionTo(aByteBufferTuple)
			}
			!aByteBufferTuple.descriptor().isShared ->
			{
				self.makeImmutable()
				aByteBufferTuple.becomeIndirectionTo(self)
			}
		}
		return true
	}

	override fun o_CompareFromToWithStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		anotherObject: A_Tuple,
		startIndex2: Int): Boolean =
			anotherObject.compareFromToWithByteBufferTupleStartingAt(
				startIndex2,
				startIndex2 + endIndex1 - startIndex1,
				self,
				startIndex1)

	override fun o_CompareFromToWithByteBufferTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aByteBufferTuple: A_Tuple,
		startIndex2: Int): Boolean
	{
		if (self.sameAddressAs(aByteBufferTuple)
			&& startIndex1 == startIndex2)
		{
			return true
		}
		val buffer1 = self.byteBuffer.slice()
		val buffer2 = aByteBufferTuple.byteBuffer.slice()
		buffer1.position(startIndex1 - 1)
		buffer1.limit(endIndex1)
		buffer2.position(startIndex2 - 1)
		buffer2.limit(startIndex2 + endIndex1 - startIndex1)
		return buffer1 == buffer2
	}

	override fun o_IsByteTuple(self: AvailObject): Boolean = true

	override fun o_IsByteBufferTuple(self: AvailObject): Boolean = true

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
			!aType.sizeRange.rangeIncludesLong(self.tupleSize.toLong()) ->
				return false
			// tuple's size is in range.
			else ->
			{
				val typeTuple = aType.typeTuple
				val breakIndex =
					self.tupleSize.coerceAtMost(typeTuple.tupleSize)
				for (i in 1 .. breakIndex)
				{
					if (!self.tupleAt(i).isInstanceOf(aType.typeAtIndex(i)))
					{
						return false
					}
				}
				val defaultTypeObject = aType.defaultType
				if (IntegerRangeTypeDescriptor.bytes
						.isSubtypeOf(defaultTypeObject))
				{
					return true
				}
				var i = breakIndex + 1
				val end = self.tupleSize
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

	override fun o_TupleAt(self: AvailObject, index: Int): AvailObject
	{
		// Answer the element at the given index in the tuple object.
		val buffer =
			self.slot(BYTE_BUFFER).javaObjectNotNull<ByteBuffer>()
		return fromUnsignedByte(buffer[index - 1].toShort() and 0xFF)
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
		assert(index in 1..self.tupleSize)
		val newValueStrong = newValueObject as AvailObject
		if (!newValueStrong.isUnsignedByte)
		{
			return if (newValueStrong.isInt)
			{
				self.copyAsMutableIntTuple().tupleAtPuttingCanDestroy(
					index, newValueObject, true)
			}
			else self.copyAsMutableObjectTuple().tupleAtPuttingCanDestroy(
				index, newValueObject, true)
		}
		if (!canDestroy || !isMutable)
		{
			return copyAsMutableByteBufferTuple(self).tupleAtPuttingCanDestroy(
				index, newValueObject, true)
		}
		// Clobber the object in place...
		val theByte = newValueStrong.extractUnsignedByte.toByte()
		val buffer = self.slot(BYTE_BUFFER).javaObjectNotNull<ByteBuffer>()
		buffer.put(index - 1, theByte)
		self.setHashOrZero(0)
		//  ...invalidate the hash value.
		return self
	}

	override fun o_TupleIntAt(self: AvailObject, index: Int): Int
	{
		assert(index >= 1 && index <= self.tupleSize)
		val buffer = self.slot(BYTE_BUFFER).javaObjectNotNull<ByteBuffer>()
		return (buffer[index - 1].toInt() and 0xFF)
	}

	override fun o_TupleLongAt(self: AvailObject, index: Int): Long
	{
		assert(index >= 1 && index <= self.tupleSize)
		val buffer = self.slot(BYTE_BUFFER).javaObjectNotNull<ByteBuffer>()
		return (buffer[index - 1].toLong() and 0xFF)
	}

	override fun o_TupleReverse(self: AvailObject): A_Tuple
	{
		val size = self.tupleSize
		if (size >= maximumCopySize)
		{
			return super.o_TupleReverse(self)
		}

		// It's not empty, it's not a total copy, and it's reasonably small.
		// Just copy the applicable bytes out.  In theory we could use
		// newLike() if start is 1.  Make sure to mask the last word in that
		// case.
		val originalBuffer = self.byteBuffer
		val result = ByteTupleDescriptor.generateByteTupleFrom(size)
			{ originalBuffer[size - it].toInt() and 255 }
		result.setHashOrZero(0)
		return result
	}

	// Answer approximately how many bits per entry are taken up by this
	// object.
	override fun o_BitsPerEntry(self: AvailObject): Int = 8

	override fun o_TupleSize(self: AvailObject): Int =
		self.slot(BYTE_BUFFER).javaObjectNotNull<ByteBuffer>().limit()

	override fun o_MakeImmutable(self: AvailObject): AvailObject
	{
		if (isMutable)
		{
			self.setDescriptor(immutable)
			self.slot(BYTE_BUFFER).makeImmutable()
		}
		return self
	}

	override fun o_MakeShared(self: AvailObject): AvailObject
	{
		if (!isShared)
		{
			self.setDescriptor(shared)
			self.slot(BYTE_BUFFER).makeShared()
		}
		return self
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
		return if (otherTuple.treeTupleLevel == 0)
		{
			TreeTupleDescriptor.createTwoPartTreeTuple(self, otherTuple, 1, 0)
		}
		else
		{
			TreeTupleDescriptor.concatenateAtLeastOneTree(
				self, otherTuple, canDestroy)
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
		if (size in 1 until tupleSize && size < maximumCopySize)
		{
			// It's not empty, it's not a total copy, and it's reasonably small.
			// Just copy the applicable bytes out.  In theory we could use
			// newLike() if start is 1.  Make sure to mask the last word in that
			// case.
			val originalBuffer = self.byteBuffer
			val result = ByteTupleDescriptor.generateByteTupleFrom(size)
				{ originalBuffer[it + start - 2].toInt() and 255 }
			if (canDestroy)
			{
				self.assertObjectUnreachableIfMutable()
			}
			result.setHashOrZero(0)
			return result
		}
		return super.o_CopyTupleFromToCanDestroy(self, start, end, canDestroy)
	}

	override fun o_TransferIntoByteBuffer(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int,
		outputByteBuffer: ByteBuffer)
	{
		val sourceBuffer = self.byteBuffer.duplicate()
		sourceBuffer.position(startIndex - 1)
		sourceBuffer.limit(endIndex)
		assert(sourceBuffer.remaining() == endIndex - startIndex + 1)
		outputByteBuffer.put(sourceBuffer)
	}

	override fun o_TupleElementsInRangeAreInstancesOf(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int,
		type: A_Type): Boolean =
			(IntegerRangeTypeDescriptor.bytes.isSubtypeOf(type)
				|| super.o_TupleElementsInRangeAreInstancesOf(
					self, startIndex, endIndex, type))

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter)
	{
		val buffer =
			self.slot(BYTE_BUFFER).javaObjectNotNull<ByteBuffer>()
		writer.startArray()
		for (i in 0 until buffer.limit())
		{
			writer.write(buffer[i].toInt())
		}
		writer.endArray()
	}

	override fun mutable(): AbstractDescriptor = mutable

	override fun immutable(): AbstractDescriptor = immutable

	override fun shared(): AbstractDescriptor = shared

	companion object
	{
		/**
		 * Defined threshold for making copies versus using
		 * [TreeTupleDescriptor]/using other forms of reference instead of
		 * creating a new tuple.
		 */
		private const val maximumCopySize = 64

		/** The mutable [ByteBufferTupleDescriptor]. */
		private val mutable = ByteBufferTupleDescriptor(Mutability.MUTABLE)

		/** The immutable [ByteBufferTupleDescriptor]. */
		private val immutable = ByteBufferTupleDescriptor(Mutability.IMMUTABLE)

		/** The shared [ByteBufferTupleDescriptor]. */
		private val shared = ByteBufferTupleDescriptor(Mutability.SHARED)

		/**
		 * Answer a mutable copy of object that also only holds bytes.
		 *
		 * @param self
		 *   The byte tuple to copy.
		 * @return
		 *   The new mutable byte tuple.
		 */
		private fun copyAsMutableByteBufferTuple(self: AvailObject): AvailObject
		{
			val size = self.tupleSize
			val newBuffer = ByteBuffer.allocate(size)
			self.transferIntoByteBuffer(1, size, newBuffer)
			assert(newBuffer.limit() == size)
			val result = tupleForByteBuffer(newBuffer)
			result.setSlot(HASH_OR_ZERO, self.hashOrZero())
			return result
		}

		/**
		 * Create a new `ByteBufferTupleDescriptor` for the specified
		 * [ByteBuffer].
		 *
		 * @param buffer
		 *   A byte buffer.
		 * @return
		 *   The requested byte buffer tuple.
		 */
		fun tupleForByteBuffer(buffer: ByteBuffer): AvailObject =
			mutable.create {
				assert(buffer.position() == 0)
				setSlot(HASH_OR_ZERO, 0)
				setSlot(BYTE_BUFFER, identityPojo(buffer))
			}
	}
}
