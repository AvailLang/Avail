/*
 * ByteArrayTupleDescriptor.kt
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
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.extractUnsignedByte
import avail.descriptor.numbers.A_Number.Companion.isInt
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromUnsignedByte
import avail.descriptor.numbers.IntegerDescriptor.Companion.hashOfUnsignedByte
import avail.descriptor.pojos.RawPojoDescriptor
import avail.descriptor.pojos.RawPojoDescriptor.Companion.identityPojo
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.BitField
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.tuples.A_Tuple.Companion.byteArray
import avail.descriptor.tuples.A_Tuple.Companion.compareFromToWithByteArrayTupleStartingAt
import avail.descriptor.tuples.A_Tuple.Companion.concatenateWith
import avail.descriptor.tuples.A_Tuple.Companion.copyAsMutableIntTuple
import avail.descriptor.tuples.A_Tuple.Companion.copyAsMutableObjectTuple
import avail.descriptor.tuples.A_Tuple.Companion.treeTupleLevel
import avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import avail.descriptor.tuples.A_Tuple.Companion.tupleAtPuttingCanDestroy
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ByteArrayTupleDescriptor.IntegerSlots.Companion.HASH_OR_ZERO
import avail.descriptor.tuples.ByteArrayTupleDescriptor.ObjectSlots.BYTE_ARRAY_POJO
import avail.descriptor.tuples.ByteTupleDescriptor.Companion.generateByteTupleFrom
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.TreeTupleDescriptor.Companion.concatenateAtLeastOneTree
import avail.descriptor.tuples.TreeTupleDescriptor.Companion.createTwoPartTreeTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.defaultType
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.isSupertypeOfPrimitiveTypeEnum
import avail.descriptor.types.A_Type.Companion.rangeIncludesLong
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.descriptor.types.A_Type.Companion.typeTuple
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.u8
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import org.availlang.json.JSONWriter
import java.nio.ByteBuffer
import kotlin.experimental.and
import kotlin.math.min

/**
 * `ByteArrayTupleDescriptor` represents a tuple of integers that happen to fall
 * in the range `[0..255]`. Unlike [ByteTupleDescriptor], it is backed by a
 * [thinly wrapped][RawPojoDescriptor] byte array.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct a new `ByteArrayTupleDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 */
class ByteArrayTupleDescriptor private constructor(mutability: Mutability)
	: NumericTupleDescriptor(
		mutability, ObjectSlots::class.java, IntegerSlots::class.java)
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
		HASH_AND_MORE;

		companion object
		{
			/**
			 * A slot to hold the cached hash value of a tuple.  If zero, then
			 * the hash value must be computed upon request.  Note that in the
			 * very rare * case that the hash value actually equals zero, the
			 * hash value has to  be computed every time it is requested.
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

	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * A [raw pojo][RawPojoDescriptor] wrapping the byte array that backs
		 * this [tuple][ByteArrayTupleDescriptor].
		 */
		BYTE_ARRAY_POJO
	}

	override fun o_AppendCanDestroy(
		self: AvailObject,
		newElement: A_BasicObject,
		canDestroy: Boolean): A_Tuple
	{
		val newElementStrong = newElement as AvailObject
		val originalSize = self.tupleSize
		if (originalSize < maximumCopySize && newElementStrong.isInt)
		{
			val intValue = newElementStrong.extractInt
			if (intValue and 255.inv() == 0)
			{
				// Convert to a ByteTupleDescriptor.
				val array =
					self[BYTE_ARRAY_POJO].javaObjectNotNull<ByteArray>()
				return generateByteTupleFrom(originalSize + 1) { index: Int ->
					when
					{
						index <= originalSize ->
							array[index - 1].toInt() and 255
						else -> intValue
					}
				}
			}
		}
		// Transition to a tree tuple.
		return self.concatenateWith(tuple(newElement), canDestroy)
	}

	// Answer approximately how many bits per entry are taken up by this
	// object.
	override fun o_BitsPerEntry(self: AvailObject): Int = 8

	override fun o_ByteArray(self: AvailObject): ByteArray =
		self[BYTE_ARRAY_POJO].javaObjectNotNull()

	override fun o_CompareFromToWithByteArrayTupleStartingAt(
		self: AvailObject,
		startIndex1: Int,
		endIndex1: Int,
		aByteArrayTuple: A_Tuple,
		startIndex2: Int): Boolean
	{
		if (self.sameAddressAs(aByteArrayTuple) && startIndex1 == startIndex2)
		{
			return true
		}
		val array1 = self.byteArray
		val array2 = aByteArrayTuple.byteArray
		var index1 = startIndex1 - 1
		var index2 = startIndex2 - 1
		val lastIndex = endIndex1 - 1
		while (index1 <= lastIndex)
		{
			if (array1[index1] != array2[index2])
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
			anotherObject.compareFromToWithByteArrayTupleStartingAt(
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
		val array =
			self[BYTE_ARRAY_POJO].javaObjectNotNull<ByteArray>()
		var hash = 0
		var index = end - 1
		val first = start - 1
		while (index >= first)
		{
			val itemHash =
				hashOfUnsignedByte(array[index].toShort() and 0xFF) xor
					preToggle
			hash = (hash + itemHash) * AvailObject.multiplier
			index--
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
		if (otherTuple.isByteArrayTuple && size1 + size2 <= 128)
		{
			val bytes = ByteArray(size1 + size2)
			System.arraycopy(self.byteArray, 0, bytes, 0, size1)
			System.arraycopy(otherTuple.byteArray, 0, bytes, size1, size2)
			return tupleForByteArray(bytes)
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
		if (size in 1 until tupleSize && size < maximumCopySize)
		{
			// It's not empty, it's not a total copy, and it's reasonably small.
			// Just copy the applicable bytes out.  In theory we could use
			// newLike() if start is 1.  Make sure to mask the last word in that
			// case.
			val originalBytes = self.byteArray
			val result = generateByteTupleFrom(size) {
					originalBytes[it + start - 2].toInt() and 255
				}
			if (canDestroy)
			{
				self.assertObjectUnreachableIfMutable()
			}
			result.setHashOrZero(0)
			return result
		}
		return super.o_CopyTupleFromToCanDestroy(self, start, end, canDestroy)
	}

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsByteArrayTuple(self)

	override fun o_EqualsByteArrayTuple(
		self: AvailObject,
		aByteArrayTuple: A_Tuple): Boolean
	{
		// First, check for object-structure (address) identity.
		if (self.sameAddressAs(aByteArrayTuple))
		{
			return true
		}
		if (self.byteArray.contentEquals(aByteArrayTuple.byteArray))
		{
			return true
		}
		if (self.tupleSize != aByteArrayTuple.tupleSize)
		{
			return false
		}
		if (self.hash() != aByteArrayTuple.hash())
		{
			return false
		}
		if (!self.compareFromToWithByteArrayTupleStartingAt(
				1, self.tupleSize, aByteArrayTuple, 1))
		{
			return false
		}
		// They're equal, but occupy disjoint storage. If possible, then
		// replace one with an indirection to the other to keep down the
		// frequency of byte-wise comparisons.
		if (!isShared)
		{
			aByteArrayTuple.makeImmutable()
			self.becomeIndirectionTo(aByteArrayTuple)
		}
		else if (!aByteArrayTuple.descriptor().isShared)
		{
			self.makeImmutable()
			aByteArrayTuple.becomeIndirectionTo(self)
		}
		return true
	}

	override fun o_IsByteArrayTuple(self: AvailObject): Boolean = true

	override fun o_IsByteTuple(self: AvailObject): Boolean = true

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
		// See if it's an acceptable size...
		if (!aType.sizeRange.rangeIncludesLong(self.tupleSize.toLong()))
		{
			return false
		}
		// tuple's size is in range.
		val typeTuple = aType.typeTuple
		val breakIndex = min(self.tupleSize, typeTuple.tupleSize)
		for (i in 1 .. breakIndex)
		{
			if (!self.tupleAt(i).isInstanceOf(aType.typeAtIndex(i)))
			{
				return false
			}
		}
		val defaultTypeObject = aType.defaultType
		if (u8.isSubtypeOf(defaultTypeObject))
		{
			return true
		}
		for (i in breakIndex + 1 .. self.tupleSize)
		{
			if (!self.tupleAt(i).isInstanceOf(defaultTypeObject))
			{
				return false
			}
		}
		return true
	}

	override fun o_TransferIntoByteBuffer(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int,
		outputByteBuffer: ByteBuffer)
	{
		val byteArray = self.byteArray
		outputByteBuffer.put(
			byteArray,
			startIndex - 1,
			endIndex - startIndex + 1)
	}

	override fun o_TupleAt(self: AvailObject, index: Int): AvailObject
	{
		// Answer the element at the given index in the tuple object.
		val array =
			self[BYTE_ARRAY_POJO].javaObjectNotNull<ByteArray>()
		return fromUnsignedByte(array[index - 1].toShort() and 0xFF)
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
		val newValueStrong = newValueObject as AvailObject
		if (!newValueObject.isUnsignedByte)
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
			return copyAsMutableByteArrayTuple(self).tupleAtPuttingCanDestroy(
				index, newValueObject, true)
		}
		// Clobber the object in place...
		val theByte = newValueStrong.extractUnsignedByte.toByte()
		val array = self[BYTE_ARRAY_POJO].javaObjectNotNull<ByteArray>()
		array[index - 1] = theByte
		self.setHashOrZero(0)
		//  ...invalidate the hash value.
		return self
	}

	override fun o_TupleElementsInRangeAreInstancesOf(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int,
		type: A_Type): Boolean =
			(u8.isSubtypeOf(type)
					|| super.o_TupleElementsInRangeAreInstancesOf(
						self, startIndex, endIndex, type))

	override fun o_TupleIntAt(self: AvailObject, index: Int): Int
	{
		assert(index >= 1 && index <= self.tupleSize)
		val array =
			self[BYTE_ARRAY_POJO].javaObjectNotNull<ByteArray>()
		return array[index - 1].toInt() and 0xFF
	}

	override fun o_TupleLongAt(self: AvailObject, index: Int): Long
	{
		assert(index >= 1 && index <= self.tupleSize)
		val array = self[BYTE_ARRAY_POJO].javaObjectNotNull<ByteArray>()
		return array[index - 1].toLong() and 0xFF
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
		val originalBytes = self.byteArray
		val result = generateByteTupleFrom(size) {
			originalBytes[size - it].toInt() and 255
		}
		result.setHashOrZero(0)
		return result
	}

	override fun o_TupleSize(self: AvailObject): Int =
		self[BYTE_ARRAY_POJO].javaObjectNotNull<ByteArray>().size

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter)
	{
		val bytes =
			self[BYTE_ARRAY_POJO].javaObjectNotNull<ByteArray>()
		writer.startArray()
		for (aByte in bytes)
		{
			writer.write(aByte.toInt())
		}
		writer.endArray()
	}

	override fun mutable(): ByteArrayTupleDescriptor = mutable

	override fun immutable(): ByteArrayTupleDescriptor = immutable

	override fun shared(): ByteArrayTupleDescriptor = shared

	companion object
	{
		/**
		 * Defined threshold for making copies versus using
		 * [TreeTupleDescriptor]/using other forms of reference instead of
		 * creating a new tuple.
		 */
		private const val maximumCopySize = 64

		/** The mutable [ByteArrayTupleDescriptor]. */
		private val mutable = ByteArrayTupleDescriptor(Mutability.MUTABLE)

		/** The immutable [ByteArrayTupleDescriptor]. */
		private val immutable = ByteArrayTupleDescriptor(Mutability.IMMUTABLE)

		/** The shared [ByteArrayTupleDescriptor]. */
		private val shared = ByteArrayTupleDescriptor(Mutability.SHARED)

		/**
		 * Answer a mutable copy of object that also only holds bytes.
		 *
		 * @param `object`
		 *   The byte tuple to copy.
		 * @return
		 *   The new mutable byte tuple.
		 */
		private fun copyAsMutableByteArrayTuple(self: AvailObject): A_Tuple
		{
			val array =
				self[BYTE_ARRAY_POJO]
					.javaObjectNotNull<ByteArray>()
			val copy = array.copyOf(array.size)
			val result = tupleForByteArray(copy)
			result[HASH_OR_ZERO] = self.hashOrZero()
			return result
		}

		/**
		 * Create a new `ByteArrayTupleDescriptor` instance for the specified
		 * byte array.
		 *
		 * @param array
		 *   A Java byte array.
		 * @return
		 *   The requested tuple.
		 */
		fun tupleForByteArray(array: ByteArray): AvailObject =
			mutable.create {
				setSlot(HASH_OR_ZERO, 0)
				setSlot(BYTE_ARRAY_POJO, identityPojo(array))
			}
	}
}
