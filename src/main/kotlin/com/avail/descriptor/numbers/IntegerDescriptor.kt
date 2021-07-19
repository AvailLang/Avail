/*
 * IntegerDescriptor.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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
package com.avail.descriptor.numbers

import com.avail.descriptor.numbers.A_Number.Companion.addToIntegerCanDestroy
import com.avail.descriptor.numbers.A_Number.Companion.asBigInteger
import com.avail.descriptor.numbers.A_Number.Companion.bitwiseXor
import com.avail.descriptor.numbers.A_Number.Companion.divideCanDestroy
import com.avail.descriptor.numbers.A_Number.Companion.divideIntoIntegerCanDestroy
import com.avail.descriptor.numbers.A_Number.Companion.equalsInteger
import com.avail.descriptor.numbers.A_Number.Companion.extractDouble
import com.avail.descriptor.numbers.A_Number.Companion.extractInt
import com.avail.descriptor.numbers.A_Number.Companion.extractLong
import com.avail.descriptor.numbers.A_Number.Companion.extractSignedByte
import com.avail.descriptor.numbers.A_Number.Companion.extractSignedShort
import com.avail.descriptor.numbers.A_Number.Companion.extractUnsignedByte
import com.avail.descriptor.numbers.A_Number.Companion.greaterThan
import com.avail.descriptor.numbers.A_Number.Companion.lessOrEqual
import com.avail.descriptor.numbers.A_Number.Companion.lessThan
import com.avail.descriptor.numbers.A_Number.Companion.minusCanDestroy
import com.avail.descriptor.numbers.A_Number.Companion.multiplyByIntegerCanDestroy
import com.avail.descriptor.numbers.A_Number.Companion.noFailMinusCanDestroy
import com.avail.descriptor.numbers.A_Number.Companion.numericCompareToInteger
import com.avail.descriptor.numbers.A_Number.Companion.rawSignedIntegerAt
import com.avail.descriptor.numbers.A_Number.Companion.rawSignedIntegerAtPut
import com.avail.descriptor.numbers.A_Number.Companion.rawUnsignedIntegerAt
import com.avail.descriptor.numbers.A_Number.Companion.subtractFromIntegerCanDestroy
import com.avail.descriptor.numbers.A_Number.Companion.timesCanDestroy
import com.avail.descriptor.numbers.A_Number.Companion.trimExcessInts
import com.avail.descriptor.numbers.DoubleDescriptor.Companion.addDoubleAndIntegerCanDestroy
import com.avail.descriptor.numbers.DoubleDescriptor.Companion.compareDoubleAndInteger
import com.avail.descriptor.numbers.DoubleDescriptor.Companion.fromDoubleRecycling
import com.avail.descriptor.numbers.FloatDescriptor.Companion.fromFloatRecycling
import com.avail.descriptor.numbers.InfinityDescriptor.Companion.negativeInfinity
import com.avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import com.avail.descriptor.numbers.IntegerDescriptor.IntegerSlots.RAW_LONG_SLOTS_
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.AvailObject.Companion.multiplier
import com.avail.descriptor.representation.AvailObjectFieldHelper
import com.avail.descriptor.representation.IntegerSlotsEnum
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.Mutability.IMMUTABLE
import com.avail.descriptor.representation.Mutability.MUTABLE
import com.avail.descriptor.representation.Mutability.SHARED
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.isSupertypeOfPrimitiveTypeEnum
import com.avail.descriptor.types.A_Type.Companion.lowerBound
import com.avail.descriptor.types.A_Type.Companion.lowerInclusive
import com.avail.descriptor.types.A_Type.Companion.upperBound
import com.avail.descriptor.types.A_Type.Companion.upperInclusive
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.singleInteger
import com.avail.descriptor.types.TypeDescriptor.Types
import com.avail.descriptor.types.TypeTag
import com.avail.exceptions.ArithmeticException
import com.avail.exceptions.AvailErrorCode
import com.avail.exceptions.AvailErrorCode.E_CANNOT_DIVIDE_BY_ZERO
import com.avail.exceptions.AvailErrorCode.E_CANNOT_MULTIPLY_ZERO_AND_INFINITY
import com.avail.exceptions.MarshalingException
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import com.avail.serialization.SerializerOperation
import com.avail.utility.json.JSONWriter
import com.avail.utility.safeWrite
import com.avail.utility.structures.EnumMap
import java.lang.Double.isInfinite
import java.lang.Math.getExponent
import java.lang.Math.scalb
import java.math.BigInteger
import java.util.IdentityHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.annotation.concurrent.GuardedBy
import kotlin.concurrent.read
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * An Avail [integer][IntegerDescriptor] is represented by a little endian
 * series of [Int] slots.  The slots are really treated as unsigned, except for
 * the final slot which is considered signed.  The high bit of the final slot
 * (i.e., its sign bit) is the sign bit of the entire object.
 *
 * Avail integers should always occupy the fewest number of slots to
 * unambiguously indicate the represented integer.  A zero integer is
 * represented by a single [Int] slot containing a zero [Int].  Any [Int] can be
 * converted to an Avail integer by using a single slot, and any [Long] can be
 * represented with at most two slots.
 *
 * Avail transitioned to [Long] slots in 2015, but integers continue to use
 * legacy 32-bit [Int] slots, with two packed in each underlying 64-bit [Long]
 * slot.
 *
 * @property
 *   The number of [Int]s of the last [Long] that do not participate in the
 *   representation of the [integer][IntegerDescriptor]. Must be 0 or 1.
 *
 * @constructor
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param unusedIntsOfLastLong
 *   The number of unused [Int]s in the last `long`.  Must be 0 or 1.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class IntegerDescriptor private constructor(
	mutability: Mutability,
	val unusedIntsOfLastLong: Byte
) : ExtendedIntegerDescriptor(
	mutability, TypeTag.INTEGER_TAG, null, IntegerSlots::class.java
) {
	init {
		assert((unusedIntsOfLastLong.toInt() and 1.inv()) == 0)
	}
	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum {
		/**
		 * Avail integers should always occupy the fewest number of slots to
		 * unambiguously indicate the represented integer.  A zero integer is
		 * represented by a single slot containing a zero [Int].  Any [Int] can
		 * be converted to an Avail integer by using a single slot, and any
		 * `long` can be represented with at most two slots.
		 *
		 * Thus, if the top slot is zero (`0`), the second-from-top slot must
		 * have its upper bit set (fall in the range `-0x80000000..-1`),
		 * otherwise the last slot would be redundant.  Likewise, if the top
		 * slot is minus one (`-1`), the second-from-top slot must have its
		 * upper bit clear (fall in the range `0..0x7FFFFFFF`).
		 */
		RAW_LONG_SLOTS_
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int
	) {
		if (self.isLong)
		{
			// The *vast* majority of uses, extends beyond 9 quintillion.
			builder.append(self.extractLong)
		}
		else
		{
			var magnitude: A_Number = self
			if (self.lessThan(zero)) {
				builder.append('-')
				magnitude = zero.minusCanDestroy(self, false)
			}
			printBigInteger(magnitude, builder, 0)
		}
	}

	override fun o_NameForDebugger(self: AvailObject): String =
		if (self.isLong)
		{
			buildString {
				append("(Integer")
				append(mutability.suffix)
				append(") = ")
				val longValue = self.extractLong
				describeLong(
					longValue,
					if (intCount(self) == 1) 32 else 64,
					this)
				append(" = ")
				append(longValue)
			}
		}
		else
		{
			super.o_NameForDebugger(self)
		}

	override fun o_DescribeForDebugger(
		self: AvailObject
	): Array<AvailObjectFieldHelper> = when {
		self.isLong -> emptyArray()
		else -> super.o_DescribeForDebugger(self)
	}

	override fun o_RawSignedIntegerAt(self: AvailObject, index: Int): Int =
		self.intSlot(RAW_LONG_SLOTS_, index)

	override fun o_RawSignedIntegerAtPut(
		self: AvailObject,
		index: Int,
		value: Int
	) = self.setIntSlot(RAW_LONG_SLOTS_, index, value)

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		(another as A_Number).equalsInteger(self)

	/**
	 * Compare two integers for equality.
	 */
	override fun o_EqualsInteger(
		self: AvailObject,
		anAvailInteger: AvailObject
	): Boolean {
		val slotsCount = intCount(self)
		// Assume integers being compared are always normalized (trimmed).
		return slotsCount == intCount(anAvailInteger) &&
			(1..slotsCount).all {
				self.intSlot(RAW_LONG_SLOTS_, it) ==
					anAvailInteger.rawSignedIntegerAt(it)
			}
	}

	/**
	 * Check if this is an integer whose value equals the given int.
	 *
	 * Assume it's normalized (trimmed).
	 */
	override fun o_EqualsInt(
		self: AvailObject,
		theInt: Int
	) = intCount(self) == 1 && self.intSlot(RAW_LONG_SLOTS_, 1) == theInt

	override fun o_IsInstanceOfKind(
		self: AvailObject,
		aType: A_Type
	) = when {
		aType.isSupertypeOfPrimitiveTypeEnum(Types.NUMBER) -> true
		!aType.isIntegerRangeType -> false
		(if (aType.upperInclusive) !self.lessOrEqual(aType.upperBound)
			else !self.lessThan(aType.upperBound)) -> false
		(if (aType.lowerInclusive) !aType.lowerBound.lessOrEqual(self)
			else !aType.lowerBound.lessThan(self)) -> false
		else -> true
	}

	override fun o_NumericCompare(
		self: AvailObject,
		another: A_Number
	): Order = another.numericCompareToInteger(self).reverse()

	override fun o_Hash(self: AvailObject): Int =
		if (self.isUnsignedByte) hashOfUnsignedByte(self.extractUnsignedByte)
		else computeHashOfIntegerObject(self)

	override fun o_IsFinite(self: AvailObject) = true

	override fun o_Kind(self: AvailObject): A_Type =
		singleInteger(self.makeImmutable())

	override fun o_DivideCanDestroy(
		self: AvailObject,
		aNumber: A_Number,
		canDestroy: Boolean
	): A_Number = aNumber.divideIntoIntegerCanDestroy(self, canDestroy)

	override fun o_MinusCanDestroy(
		self: AvailObject,
		aNumber: A_Number,
		canDestroy: Boolean
	): A_Number = aNumber.subtractFromIntegerCanDestroy(self, canDestroy)

	override fun o_PlusCanDestroy(
		self: AvailObject,
		aNumber: A_Number,
		canDestroy: Boolean
	): A_Number = aNumber.addToIntegerCanDestroy(self, canDestroy)

	override fun o_TimesCanDestroy(
		self: AvailObject,
		aNumber: A_Number,
		canDestroy: Boolean
	): A_Number = aNumber.multiplyByIntegerCanDestroy(self, canDestroy)

	override fun o_IsNybble(self: AvailObject) =
		intCount(self) == 1 && self.extractInt in 0..15

	override fun o_IsSignedByte(self: AvailObject) =
		intCount(self) == 1 && self.extractInt in -128..127

	override fun o_IsUnsignedByte(self: AvailObject) =
		intCount(self) == 1 && self.extractInt in 0..255

	override fun o_IsSignedShort(self: AvailObject) =
		intCount(self) == 1 && self.extractInt in -32768..32767

	override fun o_IsUnsignedShort(self: AvailObject) =
		intCount(self) == 1 && self.extractInt in 0..65535

	override fun o_IsInt(self: AvailObject) = intCount(self) == 1

	override fun o_IsLong(self: AvailObject) = intCount(self) <= 2

	override fun o_ExtractNybble(self: AvailObject): Byte {
		assert(intCount(self) == 1)
		val value = self.rawSignedIntegerAt(1)
		assert(value == value and 15) { "Value is out of range for a nybble" }
		return value.toByte()
	}

	override fun o_ExtractSignedByte(self: AvailObject): Byte {
		assert(intCount(self) == 1)
		val value = self.rawSignedIntegerAt(1)
		assert(value == value.toByte().toInt()) {
			"Value is out of range for a signed byte"
		}
		return value.toByte()
	}

	override fun o_ExtractUnsignedByte(self: AvailObject): Short {
		assert(intCount(self) == 1)
		val value = self.rawSignedIntegerAt(1)
		assert(value == value and 255) {
			"Value is out of range for an unsigned byte"
		}
		return value.toShort()
	}

	override fun o_ExtractSignedShort(self: AvailObject): Short {
		assert(intCount(self) == 1)
		val value = self.rawSignedIntegerAt(1)
		assert(value == value.toShort().toInt()) {
			"Value is out of range for a signed short"
		}
		return value.toShort()
	}

	override fun o_ExtractUnsignedShort(self: AvailObject): Int {
		assert(intCount(self) == 1)
		val value = self.rawSignedIntegerAt(1)
		assert(value == value and 65535) {
			"Value is out of range for an unsigned short"
		}
		return value
	}

	override fun o_ExtractInt(self: AvailObject): Int {
		assert(intCount(self) == 1) { "Integer value out of bounds" }
		return self.rawSignedIntegerAt(1)
	}

	override fun o_ExtractLong(self: AvailObject): Long {
		assert(intCount(self) <= 2) { "Integer value out of bounds" }
		if (intCount(self) == 1) {
			return self.rawSignedIntegerAt(1).toLong()
		}
		val low = self.rawSignedIntegerAt(1).toLong() and 0xffffffffL
		return low or (self.rawSignedIntegerAt(2).toLong() shl 32)
	}

	override fun o_ExtractFloat(self: AvailObject): Float =
		extractDoubleScaled(self, 0).toFloat()

	override fun o_ExtractDouble(self: AvailObject): Double =
		extractDoubleScaled(self, 0)

	/**
	 * Manually constructed accessor method.  Access the quad-byte using the
	 * native byte-ordering, but using little endian between quad-bytes (i.e.,
	 * least significant quad comes first).
	 */
	override fun o_RawUnsignedIntegerAt(
		self: AvailObject,
		index: Int
	): Long {
		val signedInt = self.intSlot(RAW_LONG_SLOTS_, index)
		return (signedInt.toLong() and 0xFFFFFFFFL)
	}

	/**
	 * Manually constructed accessor method.  Overwrite the quad-byte using the
	 * native byte-ordering, but using little endian between quad-bytes (i.e.,
	 * least significant quad comes first).
	 */
	override fun o_RawUnsignedIntegerAtPut(
		self: AvailObject,
		index: Int,
		value: Int
	) = self.setIntSlot(RAW_LONG_SLOTS_, index, value)

	override fun o_TrimExcessInts(self: AvailObject) {
		// Remove any redundant ints from my end.  Since I'm stored in Little
		// Endian representation, I can simply be truncated with no need to
		// shift data around.
		assert(isMutable)
		var size = intCount(self)
		if (size > 1) {
			if (self.intSlot(RAW_LONG_SLOTS_, size) >= 0) {
				while (size > 1 && self.intSlot(RAW_LONG_SLOTS_, size) == 0
					&& self.intSlot(RAW_LONG_SLOTS_, size - 1) >= 0)
				{
					size--
					if (size and 1 == 0)
					{
						// Remove an entire long.
						self.truncateWithFillerForNewIntegerSlotsCount(
							size + 1 shr 1)
					}
					else
					{
						// Safety: Zero the bytes if the size is now odd.
						self.setIntSlot(RAW_LONG_SLOTS_, size + 1, 0)
					}
				}
			}
			else
			{
				while (size > 1 && self.intSlot(RAW_LONG_SLOTS_, size) == -1
					&& self.intSlot(RAW_LONG_SLOTS_, size - 1) < 0)
				{
					size--
					if (size and 1 == 0)
					{
						// Remove an entire long.
						self.truncateWithFillerForNewIntegerSlotsCount(
							size + 1 shr 1)
					}
					else
					{
						// Safety: Zero the bytes if the size is now odd.
						self.setIntSlot(RAW_LONG_SLOTS_, size + 1, 0)
					}
				}
			}
			self.setDescriptor(mutableFor(size))
		}
	}

	override fun o_AddToInfinityCanDestroy(
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean
	): A_Number {
		return if (sign == Sign.POSITIVE) positiveInfinity
		else negativeInfinity
	}

	/**
	 * Choose the most spacious mutable [argument][AvailObject].
	 *
	 * @param self
	 *   An Avail integer.
	 * @param another
	 *   The number of ints in the representation of object.
	 * @param objectIntCount
	 *   The size of object in ints.
	 * @param anotherIntCount
	 *   The number of ints in the representation of another.
	 * @return
	 *   One of the arguments, or `null` if neither argument was suitable.
	 */
	private fun largerMutableOf(
		self: AvailObject,
		another: AvailObject,
		objectIntCount: Int,
		anotherIntCount: Int
	): AvailObject? {
		return when {
			objectIntCount == anotherIntCount -> when {
				isMutable -> self
				another.descriptor().isMutable -> another
				else -> null
			}
			objectIntCount > anotherIntCount -> if (isMutable) self else null
			another.descriptor().isMutable -> another
			else -> null
		}
	}

	override fun o_AddToIntegerCanDestroy(
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean
	): A_Number {
		// This routine would be much quicker with access to machine carry
		// flags, but Java doesn't let us actually go down to the metal (nor do
		// C and C++). Our best recourse without reverting to assembly language
		// is to use 64-bit.
		val objectSize = intCount(self)
		val anIntegerSize = intCount(anInteger)
		var output =
			if (canDestroy)
			{
				largerMutableOf(self, anInteger, objectSize, anIntegerSize)
			}
			else null
		if (objectSize == 1 && anIntegerSize == 1) {
			// See if the (signed) sum will fit in 32 bits, the most common case
			// by far.
			val sum = self.extractLong + anInteger.extractLong
			if (sum == sum.toInt().toLong()) {
				// Yes, it fits.  Clobber one of the inputs, or create a new
				// object if they were both immutable...
				output = output ?: createUninitializedInteger(1)
				assert(intCount(output) == 1)
				output.rawSignedIntegerAtPut(1, sum.toInt())
				return output
			}
			// Doesn't fit in 32 bits; use two 32-bit words.
			return fromLong(sum)
		}
		// Set estimatedSize to the max of the input sizes. There will only
		// rarely be an overflow and at most by one cell. Underflows should also
		// be pretty rare, and they're handled by output.trimExcessInts().
		output = output ?:
			createUninitializedInteger(max(objectSize, anIntegerSize))

		val outputSize = intCount(output)
		val extendedObject =
			((self.rawSignedIntegerAt(objectSize) shr 31).toLong()
				and 0xFFFFFFFFL)
		val extendedAnInteger =
			((anInteger.rawSignedIntegerAt(anIntegerSize) shr 31).toLong()
				and 0xFFFFFFFFL)
		var partial: Long = 0
		var lastInt = 0
		// The object is always big enough to store the max of the number of
		// quads from each input, so after the loop we can check partial and the
		// upper bit of the result to see if another quad needs to be appended.
		for (i in 1..outputSize) {
			partial += if (i > objectSize) extendedObject
				else self.rawUnsignedIntegerAt(i)
			partial += if (i > anIntegerSize) extendedAnInteger
				else anInteger.rawUnsignedIntegerAt(i)
			lastInt = partial.toInt()
			output.rawSignedIntegerAtPut(i, lastInt)
			partial = partial ushr 32
		}
		partial += extendedObject + extendedAnInteger
		if (lastInt shr 31 != partial.toInt()) {
			// Top bit of last word no longer agrees with sign of result. Extend
			// it.
			val newOutput = createUninitializedInteger(outputSize + 1)
			for (i in 1..outputSize) {
				newOutput.setIntSlot(
					RAW_LONG_SLOTS_,
					i,
					output.intSlot(RAW_LONG_SLOTS_, i))
			}
			newOutput.rawSignedIntegerAtPut(outputSize + 1, partial.toInt())
			// No need to truncate it in this case.
			return newOutput
		}
		output.trimExcessInts()
		return output
	}

	override fun o_AddToDoubleCanDestroy(
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean
	): A_Number {
		val d: Double = addDoubleAndIntegerCanDestroy(
			doubleObject.extractDouble, self, canDestroy)
		return fromDoubleRecycling(d, doubleObject, canDestroy)
	}

	override fun o_AddToFloatCanDestroy(
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean
	): A_Number {
		val d: Double = addDoubleAndIntegerCanDestroy(
			floatObject.extractDouble, self, canDestroy)
		return fromFloatRecycling(d.toFloat(), floatObject, canDestroy)
	}

	override fun o_DivideIntoInfinityCanDestroy(
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean
	): A_Number = when {
		self.equals(zero) ->
			throw ArithmeticException(E_CANNOT_DIVIDE_BY_ZERO)
		self.greaterThan(zero) xor (sign == Sign.POSITIVE) ->
			negativeInfinity
		else -> positiveInfinity
	}

	/**
	 * Choose a mutable [argument][AvailObject].
	 *
	 * @param self
	 *   An Avail integer whose descriptor is the receiver.
	 * @param another
	 *   An integer.
	 * @return
	 *   One of the arguments, or `null` if neither argument is mutable.
	 */
	private fun mutableOf(
		self: AvailObject,
		another: AvailObject
	): AvailObject? = when {
		isMutable -> self
		another.descriptor().isMutable -> another
		else -> null
	}

	override fun o_DivideIntoIntegerCanDestroy(
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean
	): A_Number
	{
		// Compute anInteger / self. Round towards negative infinity.
		if (self.isLong)
		{
			var denominator = self.extractLong
			if (denominator == 0L)
			{
				throw ArithmeticException(E_CANNOT_DIVIDE_BY_ZERO)
			}
			if (anInteger.isLong)
			{
				// Two longs - by far the most common case.
				var numerator = anInteger.extractLong
				if (numerator != Long.MIN_VALUE
					&& denominator != Long.MIN_VALUE)
				{
					// It's safe to negate either value without overflow.
					if (denominator < 0)
					{
						// n/d for d<0:  use (-n/-d)
						denominator = -denominator
						numerator = -numerator
					}
					// assert(denominator > 0)
					if (numerator < 0)
					{
						// n/d for n<0, d>0:  use -1-(-1-n)/d
						// e.g., -9/5  = -1-(-1+9)/5  = -1-8/5 = -2
						// e.g., -10/5 = -1-(-1+10)/5 = -1-9/5 = -2
						// e.g., -11/5 = -1-(-1+11)/5 = -1-10/5 = -3
						return fromLong(-1 - (-1 - numerator) / denominator)
					}
				}
			}
		}
		// Rare - fall back to slower math.
		var numerator: A_Number = anInteger
		var denominator: A_Number = self
		if (denominator.lessThan(zero))
		{
			// n/d for d<0:  Compute (-n/-d) instead.
			numerator =
				numerator.subtractFromIntegerCanDestroy(zero, canDestroy)
			denominator =
				denominator.subtractFromIntegerCanDestroy(zero, canDestroy)
		}
		var invertResult = false
		if (numerator.lessThan(zero))
		{
			// n/d for n<0, d>0:  use -1-(-1-n)/d
			// e.g., -9/5  = -1-(-1+9)/5  = -1-8/5 = -2
			// e.g., -10/5 = -1-(-1+10)/5 = -1-9/5 = -2
			// e.g., -11/5 = -1-(-1+11)/5 = -1-10/5 = -3
			numerator = numerator.bitwiseXor(negativeOne, canDestroy)
			invertResult = true
		}
		// At this point, neither numerator nor denominator is negative.
		// For simplicity and efficiency, fall back on Java's BigInteger
		// implementation.
		val numeratorBigInt = numerator.asBigInteger()
		val denominatorBigInt = denominator.asBigInteger()
		val quotient = numeratorBigInt.divide(denominatorBigInt)
		var result = fromBigInteger(quotient)
		if (invertResult)
		{
			result = result.bitwiseXor(negativeOne, true)
		}
		return result
	}

	override fun o_DivideIntoDoubleCanDestroy(
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean
	): A_Number {
		// This one is tricky.  The integer might be bigger than the maximum
		// double, but the product with a very small double may produce a value
		// that is still in range.  Avoid the overflow in that case by working
		// with a scaled down version of the integer: target a "significand"
		// below about 2^96.
		val scale = max(intCount(self) - 4, 0) shl 5
		val scaledIntAsDouble = extractDoubleScaled(self, scale)
		assert(!isInfinite(scaledIntAsDouble))
		val scaledQuotient = doubleObject.extractDouble / scaledIntAsDouble
		val quotient = scalb(scaledQuotient, scale)
		return fromDoubleRecycling(quotient, doubleObject, canDestroy)
	}

	override fun o_DivideIntoFloatCanDestroy(
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean
	): A_Number {
		// This one is tricky.  The integer might be bigger than the maximum
		// double, but the product with a very small double may produce a value
		// that is still in range of a float.  Actually, I doubt this is
		// possible, but it's easier to just make it match the double case.
		// Avoid the overflow by working with a scaled down version of the
		// integer: target a "significand" below about 2^96.
		val scale = max(intCount(self) - 4, 0) shl 5
		val scaledIntAsDouble = extractDoubleScaled(self, scale)
		assert(!isInfinite(scaledIntAsDouble))
		val scaledQuotient = floatObject.extractDouble / scaledIntAsDouble
		val quotient = scalb(scaledQuotient, scale)
		return fromFloatRecycling(quotient.toFloat(), floatObject, canDestroy)
	}

	override fun o_MultiplyByInfinityCanDestroy(
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean
	): A_Number = when {
		self.equals(zero) ->
			throw ArithmeticException(E_CANNOT_MULTIPLY_ZERO_AND_INFINITY)
		self.greaterThan(zero) xor (sign == Sign.POSITIVE) ->
			negativeInfinity
		else -> positiveInfinity
	}

	override fun o_MultiplyByIntegerCanDestroy(
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean
	): A_Number {
		var output: AvailObject?
		if (self.isInt && anInteger.isInt) {
			// See if the (signed) product will fit in 32 bits, the most common
			// case by far.
			val prod = (self.extractInt.toLong()
				* anInteger.extractInt.toLong())
			if (prod == prod.toInt().toLong())
			{
				// Yes, it fits.  Clobber one of the inputs, or create a new
				// int-sized object if they were both immutable...
				output = if (canDestroy) mutableOf(self, anInteger) else null
				output = output ?: createUninitializedInteger(1)
				assert(intCount(output) == 1)
				output.rawSignedIntegerAtPut(1, prod.toInt())
				return output
			}
			// Doesn't fit.  Worst case: -2^31 * -2^31 = +2^62, which fits in 64
			// bits, even with the sign.
			return fromLong(prod)
		}
		val size1 = intCount(self)
		val size2 = intCount(anInteger)
		// The following is a safe upper bound.  See below.
		val targetSize = size1 + size2
		output = createUninitializedInteger(targetSize)
		val extension1 =
			self.rawSignedIntegerAt(size1).toLong() shr 31 and 0xFFFFFFFFL
		val extension2 =
			anInteger.rawSignedIntegerAt(size2).toLong() shr 31 and 0xFFFFFFFFL

		// We can't recycle storage quite as easily here as we did for addition
		// and subtraction, because the intermediate sum would be clobbering one
		// of the multiplicands that we (may) need to scan again.  So always
		// allocate the new object.  The product will always fit in N+M cells if
		// the multiplicands fit in sizes N and M cells.  For proof, consider
		// the worst case (using bytes for the example).  In hex,
		// -80 * -80 = +4000, which fits.  Also, 7F*7F = 3F01, and
		// 7F*-80 = -3F80.  So no additional padding is necessary.  The scheme
		// we will use is to compute each word of the result, low to high, using
		// a carry of two words.  All quantities are treated as unsigned, but
		// the multiplicands are sign-extended as needed.  Multiplying two
		// one-word multiplicands yields a two word result, so we need to use
		// three words to properly hold the carry (it would take more than four
		// billion words to overflow this, and only one billion words are
		// addressable on a 32-bit machine).  The three-word intermediate value
		// is handled as two two-word accumulators, A and B.  B is considered
		// shifted by a word (to the left).  The high word of A is added to the
		// low word of B (with carry to the high word of B), and then the high
		// word of A is cleared.  This "shifts the load" to B for holding big
		// numbers without affecting their sum.  When a new two-word value needs
		// to be added in, this trick is employed, followed by directly adding
		// the two-word value to A, as long as we can ensure the *addition*
		// won't overflow, which is the case if the two-word value is an
		// unsigned product of two one-word values.  Since FF*FF=FE01, we can
		// safely add this to 00FF (getting FF00) without overflow.  Pretty
		// slick, huh?
		var low: Long = 0
		var high: Long = 0
		for (i in 1..targetSize) {
			var k = 1
			var m = i
			while (k <= i) {
				val multiplicand1 =
					if (k > size1) extension1
					else self.rawUnsignedIntegerAt(k)
				val multiplicand2 =
					if (m > size2) extension2
					else anInteger.rawUnsignedIntegerAt(m)
				low += multiplicand1 * multiplicand2
				// Add upper of low to high.
				high += low ushr 32
				// Subtract the same amount from low (clear upper word).
				low = low and 0xFFFFFFFFL
				k++
				m--
			}
			output.rawSignedIntegerAtPut(i, low.toInt())
			low = high and 0xFFFFFFFFL
			high = high ushr 32
		}
		// We can safely ignore any remaining bits from the partial products.
		output.trimExcessInts()
		return output
	}

	override fun o_MultiplyByDoubleCanDestroy(
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean
	): A_Number {
		// This one is tricky.  The integer might be bigger than the maximum
		// double, but the product with a very small double may produce a value
		// that is still in range.  Avoid the overflow in that case by working
		// with a scaled down version of the integer: target a "significand"
		// below about 2^96.
		val scale = max(intCount(self) - 4, 0) shl 5
		val scaledIntAsDouble = extractDoubleScaled(self, scale)
		assert(!isInfinite(scaledIntAsDouble))
		val scaledProduct = doubleObject.extractDouble * scaledIntAsDouble
		val product = scalb(scaledProduct, scale)
		return fromDoubleRecycling(product, doubleObject, canDestroy)
	}

	override fun o_MultiplyByFloatCanDestroy(
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean
	): A_Number {
		// This one is tricky.  The integer might be bigger than the maximum
		// double, but the product with a very small double may produce a value
		// that is still in range of a float.  Actually, I doubt this is
		// possible, but it's easier to just make it match the double case.
		// Avoid the overflow by working with a scaled down version of the
		// integer: target a "significand" below about 2^96.
		val scale = max(intCount(self) - 4, 0) shl 5
		val scaledIntAsDouble = extractDoubleScaled(self, scale)
		assert(!isInfinite(scaledIntAsDouble))
		val scaledProduct = floatObject.extractDouble * scaledIntAsDouble
		val product = scalb(scaledProduct, scale)
		return fromFloatRecycling(product.toFloat(), floatObject, canDestroy)
	}

	override fun o_SubtractFromInfinityCanDestroy(
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean
	): A_Number =
		if (sign == Sign.POSITIVE) positiveInfinity else negativeInfinity

	override fun o_SubtractFromIntegerCanDestroy(
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean
	): A_Number {
		// This routine would be much quicker with access to machine carry
		// flags, but Java doesn't let us actually go down to the metal (nor do
		// C and C++). Our best recourse without reverting to assembly language
		// is to use 64-bit arithmetic.
		val objectSize = intCount(self)
		val anIntegerSize = intCount(anInteger)
		var output =
			if (canDestroy)
			{
				largerMutableOf(self, anInteger, objectSize, anIntegerSize)
			}
			else null
		if (objectSize == 1 && anIntegerSize == 1) {
			// See if the (signed) difference will fit in 32 bits, the most
			// common case by far.
			val diff = anInteger.extractLong - self.extractLong
			if (diff == diff.toInt().toLong()){
				// Yes, it fits. Clobber one of the inputs, or create a new
				// object if they were both immutable...
				output = output ?: createUninitializedInteger(1)
				assert(intCount(output) == 1)
				output.rawSignedIntegerAtPut(1, diff.toInt())
				return output
			}
			// Doesn't fit in 32 bits; use two 32-bit words.
			output = createUninitializedInteger(2)
			output.rawSignedIntegerAtPut(1, diff.toInt())
			output.rawSignedIntegerAtPut(2, (diff shr 32).toInt())
			return output
		}
		// Set estimatedSize to the max of the input sizes. There will only
		// rarely be an overflow and at most by one cell. Underflows should also
		// be pretty rare, and they're handled by output.trimExcessInts().
		output = output ?:
			createUninitializedInteger(max(objectSize, anIntegerSize))
		val outputSize = intCount(output)
		val extendedObject =
			self.rawSignedIntegerAt(objectSize).toLong() shr 31 and 0xFFFFFFFFL
		val extendedAnInteger =
			(anInteger.rawSignedIntegerAt(anIntegerSize).toLong() shr 31
				and 0xFFFFFFFFL)
		var partial: Long = 1
		var lastInt = 0
		// The object is always big enough to store the max of the number of
		// quads from each input, so after the loop we can check partial and the
		// upper bit of the result to see if another quad needs to be appended.
		for (i in 1..outputSize) {
			partial +=
				if (i > anIntegerSize) extendedAnInteger
				else anInteger.rawUnsignedIntegerAt(i)
			partial += 0xFFFFFFFFL xor
				if (i > objectSize) extendedObject
				else self.rawUnsignedIntegerAt(i)
			lastInt = partial.toInt()
			output.rawSignedIntegerAtPut(i, lastInt)
			partial = partial ushr 32
		}
		partial += extendedAnInteger + (extendedObject xor 0xFFFFFFFFL)
		if (lastInt shr 31 != partial.toInt()) {
			// Top bit of last word no longer agrees with sign of result.
			// Extend it.
			val newOutput = createUninitializedInteger(outputSize + 1)
			for (i in 1..outputSize) {
				newOutput.rawSignedIntegerAtPut(i, output.rawSignedIntegerAt(i))
			}
			newOutput.rawSignedIntegerAtPut(outputSize + 1, partial.toInt())
			// No need to truncate it in this case.
			return newOutput
		}
		output.trimExcessInts()
		return output
	}

	override fun o_SubtractFromDoubleCanDestroy(
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean
	): A_Number {
		// Compute the negative (i.e., int-double)
		val d: Double = addDoubleAndIntegerCanDestroy(
			-doubleObject.extractDouble, self, canDestroy)
		// Negate it to produce (double-int).
		return fromDoubleRecycling(-d, doubleObject, canDestroy)
	}

	override fun o_SubtractFromFloatCanDestroy(
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean
	): A_Number {
		// Compute the negative (i.e., int-float)
		val d: Double = addDoubleAndIntegerCanDestroy(
			-floatObject.extractDouble, self, canDestroy)
		// Negate it to produce (float-int).
		return fromFloatRecycling((-d).toFloat(), floatObject, canDestroy)
	}

	override fun o_NumericCompareToInteger(
		self: AvailObject,
		anInteger: AvailObject
	): Order {
		val size1 = intCount(self)
		val size2 = intCount(anInteger)
		val high1 = self.intSlot(RAW_LONG_SLOTS_, size1)
		val high2 = anInteger.rawSignedIntegerAt(size2)
		val composite1 = if (high1 >= 0) size1 else -size1
		val composite2 = if (high2 >= 0) size2 else -size2
		if (composite1 != composite2) {
			return if (composite1 < composite2) Order.LESS else Order.MORE
		}
		assert(size1 == size2)
		assert(high1 >= 0 == high2 >= 0)
		if (high1 != high2) {
			return if (high1 < high2) Order.LESS else Order.MORE
		}
		for (i in size1 - 1 downTo 1) {
			val a = self.intSlot(RAW_LONG_SLOTS_, i)
			val b = anInteger.rawSignedIntegerAt(i)
			if (a != b) {
				return when {
					a.toLong() and 0xFFFFFFFFL < b.toLong() and 0xFFFFFFFFL ->
						Order.LESS
					else -> Order.MORE
				}
			}
		}
		return Order.EQUAL
	}

	override fun o_NumericCompareToInfinity(
		self: AvailObject,
		sign: Sign
	): Order {
		return if (sign == Sign.POSITIVE) Order.LESS else Order.MORE
	}

	override fun o_NumericCompareToDouble(
		self: AvailObject,
		aDouble: Double
	): Order = compareDoubleAndInteger(aDouble, self).reverse()

	/**
	 * A helper function for bitwise operations.  Perform the given operation on
	 * [Int]s from the incoming operands, writing the resulting [Int] to the
	 * resulting object, which is either one of the operands if large enough and
	 * mutable and [canDestroy], or a new object.
	 */
	private fun bitwiseOperation(
		self: AvailObject,
		anInteger: A_Number,
		canDestroy: Boolean,
		operation: (Int, Int) -> Int
	): AvailObject {
		val objectSize = intCount(self)
		val anIntegerTraversed = anInteger.traversed()
		val anIntegerSize = intCount(anIntegerTraversed)
		var output = if (canDestroy) {
			largerMutableOf(self, anIntegerTraversed, objectSize, anIntegerSize)
		}
		else null
		// Both integers are 32 bits. This is by far the most common case.
		if (objectSize == 1 && anIntegerSize == 1) {
			val result = operation(
				self.rawSignedIntegerAt(1),
				anIntegerTraversed.rawSignedIntegerAt(1))
			output = output ?: createUninitializedInteger(1)
			output.rawSignedIntegerAtPut(1, result)
			return output
		}
		// If neither of the inputs were suitable for destruction, then allocate
		// a new one whose size is that of the larger input.
		output = output ?: createUninitializedInteger(
			max(objectSize, anIntegerSize))
		// Handle larger integers.
		val outputSize = intCount(output)
		val extendedObject = self.rawSignedIntegerAt(objectSize) shr 31
		val extendedAnInteger =
			anIntegerTraversed.rawSignedIntegerAt(anIntegerSize) shr 31
		for (i in 1..outputSize) {
			val objectWord = if (i > objectSize) {
				extendedObject
			}
			else
			{
				self.rawSignedIntegerAt(i)
			}
			val anIntegerWord = if (i > anIntegerSize) {
				extendedAnInteger
			}
			else
			{
				anIntegerTraversed.rawSignedIntegerAt(i)
			}
			val result = operation(objectWord, anIntegerWord)
			output.rawSignedIntegerAtPut(i, result)
		}
		output.trimExcessInts()
		return output
	}

	override fun o_BitwiseAnd(
		self: AvailObject,
		anInteger: A_Number,
		canDestroy: Boolean
	): A_Number = bitwiseOperation(self, anInteger, canDestroy, Int::and)

	override fun o_BitwiseOr(
		self: AvailObject,
		anInteger: A_Number,
		canDestroy: Boolean
	): A_Number = bitwiseOperation(self, anInteger, canDestroy, Int::or)

	override fun o_BitwiseXor(
		self: AvailObject,
		anInteger: A_Number,
		canDestroy: Boolean
	): A_Number = bitwiseOperation(self, anInteger, canDestroy, Int::xor)

	/**
	 * Shift the given positive number to the left by the specified shift factor
	 * (number of bits), then truncate the representation to force bits above
	 * the specified position to be zeroed.  The shift factor may be negative,
	 * indicating a right shift by the corresponding positive amount, in which
	 * case truncation will still happen.
	 *
	 * For example, shifting the binary number 1011₂ to the left by 2 positions
	 * will produce 101100₂, then truncating it to, say 5 bits, would produce
	 * 01100₂.  For a second example, the positive number 110101₂ can be shifted
	 * left by -2 positions (which is a right shift of 2) to get 1101₂, and a
	 * subsequent truncation to ten bits would leave it unaffected.
	 *
	 * @param self
	 *   The non-negative integer to shift and mask.
	 * @param shiftFactor
	 *   How much to shift left (may be negative to indicate a right shift).
	 * @param truncationBits
	 *   A positive integer indicating how many low-order bits of the shifted
	 *   value should be preserved.
	 * @param canDestroy
	 *   Whether it is permitted to alter the original object if it happens to
	 *   be mutable.
	 * @return
	 *   ⌊object &times; 2^shiftFactor⌋ mod 2^truncationBits
	 */
	override fun o_BitShiftLeftTruncatingToBits(
		self: AvailObject,
		shiftFactor: A_Number,
		truncationBits: A_Number,
		canDestroy: Boolean
	): A_Number {
		if (!truncationBits.isInt) {
			throw ArithmeticException(AvailErrorCode.E_TOO_LARGE_TO_REPRESENT)
		}
		val truncationInt = truncationBits.extractInt
		assert (truncationInt >= 0)
		val sign = self.numericCompareToInteger(zero)
		assert (sign != Order.LESS)
		if (sign == Order.EQUAL) {
			if (!canDestroy || isMutable) {
				self.makeImmutable()
			}
			// 0*2^n = 0
			return self
		}
		if (!shiftFactor.isInt) {
			// e.g., 123 >> 999999999999999999 is 0
			// also 123 << 999999999999999999 truncated to N bits (N<2^31) is 0.
			return zero
		}
		val shiftInt = shiftFactor.extractInt
		if (self.isLong) {
			val baseLong = self.extractLong
			val shiftedLong = bitShiftLong(baseLong, shiftInt)
			if (shiftInt < 0
				|| truncationInt < 64
				|| bitShiftLong(shiftedLong, -shiftInt) == baseLong)
			{
				// Either a right shift, or a left shift that didn't lose bits,
				// or a left shift that will fit in a long after the truncation.
				// In these cases the result will still be a long.
				var resultLong = shiftedLong
				if (truncationInt < 64)
				{
					resultLong = resultLong and (1L shl truncationInt) - 1
				}
				if (canDestroy && isMutable)
				{
					if (resultLong == resultLong.toInt().toLong())
					{
						// Fits in an int.  Try to recycle.
						if (intCount(self) == 1)
						{
							self.rawSignedIntegerAtPut(1, resultLong.toInt())
							return self
						}
					}
					else
					{
						// *Fills* a long.  Try to recycle.
						if (intCount(self) == 2)
						{
							self.rawSignedIntegerAtPut(
								1, resultLong.toInt())
							self.rawSignedIntegerAtPut(
								2, (resultLong shr 32).toInt())
							return self
						}
					}
				}
				// Fall back and create a new integer object.
				return fromLong(resultLong)
			}
		}
		// Answer doesn't (necessarily) fit in a long.
		val sourceSlots = intCount(self)
		var estimatedBits = (sourceSlots shl 5) + shiftInt
		estimatedBits = min(estimatedBits, truncationInt + 1)
		estimatedBits = max(estimatedBits, 1)
		val slotCount = estimatedBits + 31 shr 5
		val result: A_Number = createUninitializedInteger(slotCount)
		val shortShift = shiftInt and 31
		var sourceIndex = slotCount - (shiftInt shr 5)
		var accumulator = 0x5EADCAFEBABEBEEFL
		// We range from slotCount+1 to 1 to pre-load the accumulator.
		for (destIndex in slotCount + 1 downTo 1) {
			val nextWord = if (sourceIndex in 1..sourceSlots)
			{
				self.rawSignedIntegerAt(sourceIndex)
			}
			else 0
			accumulator = accumulator shl 32
			accumulator = accumulator or (nextWord.toLong() shl shortShift)
			if (destIndex <= slotCount) {
				result.rawSignedIntegerAtPut(
					destIndex,
					(accumulator shr 32).toInt())
			}
			sourceIndex--
		}
		// Mask it if necessary to truncate some upper bits.
		var mask = (1 shl (truncationInt and 31)) - 1
		for (destIndex in (truncationInt shr 5)..slotCount) {
			result.rawSignedIntegerAtPut(
				destIndex, result.rawSignedIntegerAt(destIndex) and mask)
			// Completely wipe any higher ints.
			mask = 0
		}
		result.trimExcessInts()
		return result
	}

	/**
	 * Shift the given integer to the left by the specified shift factor (number
	 * of bits).  The shift factor may be negative, indicating a right shift by
	 * the corresponding positive amount.
	 *
	 * @param self
	 *   The integer to shift.
	 * @param shiftFactor
	 *   How much to shift left (may be negative to indicate a right shift).
	 * @param canDestroy
	 *   Whether it is permitted to alter the original object if it happens to
	 *   be mutable.
	 * @return
	 *   ⌊self &times; 2^shiftFactor⌋
	 */
	override fun o_BitShift(
		self: AvailObject,
		shiftFactor: A_Number,
		canDestroy: Boolean
	): A_Number {
		if (self.equals(zero)) {
			if (!canDestroy || isMutable) {
				self.makeImmutable()
			}
			// 0*2^n = 0
			return self
		}
		if (!shiftFactor.isInt) {
			if (shiftFactor.numericCompareToInteger(zero) == Order.MORE) {
				// e.g., 123 << 999999999999999999 is too big
				throw ArithmeticException(
					AvailErrorCode.E_TOO_LARGE_TO_REPRESENT)
			}
			return when (Order.MORE) {
				// e.g., 123 >> 999999999999999999 is 0
				self.numericCompareToInteger(zero) -> zero
				// e.g., -123 >> 999999999999999999 is -1
				else -> negativeOne()
			}
		}
		val shiftInt = shiftFactor.extractInt
		if (self.isLong) {
			val baseLong = self.extractLong
			val shiftedLong = arithmeticBitShiftLong(baseLong, shiftInt)
			if (shiftInt < 0
				|| arithmeticBitShiftLong(shiftedLong, -shiftInt) == baseLong)
			{
				// Either a right shift, or a left shift that didn't lose bits.
				// In these cases the result will still be a long.
				if (canDestroy && isMutable)
				{
					if (shiftedLong == shiftedLong.toInt().toLong())
					{
						// Fits in an int.  Try to recycle.
						if (intCount(self) == 1)
						{
							self.rawSignedIntegerAtPut(1, shiftedLong.toInt())
							return self
						}
					}
					else
					{
						// *Fills* a long.  Try to recycle.
						if (intCount(self) == 2)
						{
							self.rawSignedIntegerAtPut(
								1, shiftedLong.toInt())
							self.rawSignedIntegerAtPut(
								2, (shiftedLong shr 32).toInt())
							return self
						}
					}
				}
				// Fall back and create a new integer object.
				return fromLong(shiftedLong)
			}
		}
		// Answer doesn't (necessarily) fit in a long.
		val sourceSlots = intCount(self)
		val estimatedBits = max((sourceSlots shl 5) + shiftInt, 1)
		val intSlotCount = estimatedBits + 31 shr 5
		val result: A_Number = createUninitializedInteger(intSlotCount)
		val shortShift = shiftInt and 31
		var sourceIndex = intSlotCount - (shiftInt shr 5)
		var accumulator = 0x5EADCAFEBABEBEEFL
		val signExtension = when (Order.LESS) {
			self.numericCompareToInteger(zero) -> -1
			else -> 0
		}
		// We range from slotCount+1 to 1 to pre-load the accumulator.
		for (destIndex in intSlotCount + 1 downTo 1) {
			val nextWord = when {
				sourceIndex < 1 -> 0
				sourceIndex > sourceSlots -> signExtension
				else -> self.rawSignedIntegerAt(sourceIndex)
			}
			accumulator = accumulator shl 32
			accumulator = accumulator or
				(nextWord.toLong() and 0xFFFFFFFFL shl shortShift)
			if (destIndex <= intSlotCount) {
				result.rawSignedIntegerAtPut(
					destIndex, (accumulator shr 32).toInt())
			}
			sourceIndex--
		}
		result.trimExcessInts()
		return result
	}

	override fun o_SerializerOperation(
		self: AvailObject
	): SerializerOperation {
		if (self.isInt) {
			return when (self.extractInt) {
				0 -> SerializerOperation.ZERO_INTEGER
				1 -> SerializerOperation.ONE_INTEGER
				2 -> SerializerOperation.TWO_INTEGER
				3 -> SerializerOperation.THREE_INTEGER
				4 -> SerializerOperation.FOUR_INTEGER
				5 -> SerializerOperation.FIVE_INTEGER
				6 -> SerializerOperation.SIX_INTEGER
				7 -> SerializerOperation.SEVEN_INTEGER
				8 -> SerializerOperation.EIGHT_INTEGER
				9 -> SerializerOperation.NINE_INTEGER
				10 -> SerializerOperation.TEN_INTEGER
				in 0..0xFF -> SerializerOperation.BYTE_INTEGER
				in 0..0xFFFF -> SerializerOperation.SHORT_INTEGER
				else -> SerializerOperation.INT_INTEGER
			}
		}
		return SerializerOperation.BIG_INTEGER
	}

	override fun o_MarshalToJava(
		self: AvailObject,
		classHint: Class<*>?
	): Any = when (classHint) {
		// Force marshaling to java.math.BigInteger.
		java.math.BigInteger::class.java -> self.asBigInteger()
		// Force marshaling to Java's primitive long type.
		java.lang.Long::class.javaPrimitiveType,
		java.lang.Long::class.java -> when {
			self.isLong -> self.extractLong
			else -> throw MarshalingException()
		}
		// Force marshaling to Java's primitive int type.
		java.lang.Integer::class.javaPrimitiveType,
		java.lang.Integer::class.java -> when {
			self.isInt -> self.extractInt
			else -> throw MarshalingException()
		}
		// Force marshaling to Java's primitive short type.
		java.lang.Short::class.javaPrimitiveType,
		java.lang.Short::class.java -> when {
			self.isSignedShort -> self.extractSignedShort
			else -> throw MarshalingException()
		}
		// Force marshaling to Java's primitive byte type.
		java.lang.Byte::class.javaPrimitiveType,
		java.lang.Byte::class.java -> when {
			self.isSignedByte -> self.extractSignedByte
			else -> throw MarshalingException()
		}
		// No useful hint was provided, so marshal to the smallest primitive
		// integral type able to express object's value.
		else -> {
			if (!self.isLong) self.asBigInteger()
			else when (val longValue = self.extractLong) {
				longValue.toByte().toLong() -> longValue.toByte()
				longValue.toShort().toLong() -> longValue.toShort()
				longValue.toInt().toLong() -> longValue.toInt()
				else -> longValue
			}
		}
	}

	/**
	 * Answer a [BigInteger] that is the numerical equivalent of the given
	 * object, which is an Avail integer.
	 */
	override fun o_AsBigInteger(self: AvailObject): BigInteger
	{
		val integerCount = intCount(self)
		if (integerCount <= 2) {
			return BigInteger.valueOf(self.extractLong)
		}
		val bytes = ByteArray(integerCount shl 2)
		var b = 0
		for (i in integerCount downTo 1) {
			val integer = self.intSlot(RAW_LONG_SLOTS_, i)
			bytes[b++] = (integer shr 24).toByte()
			bytes[b++] = (integer shr 16).toByte()
			bytes[b++] = (integer shr 8).toByte()
			bytes[b++] = integer.toByte()
		}
		return BigInteger(bytes)
	}

	override fun o_IsNumericallyIntegral(self: AvailObject): Boolean = true

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) = when {
		self.isLong -> writer.write(self.extractLong)
		else -> writer.write(self.asBigInteger())
	}

	companion object {
		/**
		 * Answer the number of int slots in the passed integer object, which
		 * must not be an indirection.
		 *
		 * @param self
		 *   The integer object.
		 * @return
		 *   The number of occupied int slots in the object.
		 */
		fun intCount(
			self: AvailObject
		) = ((self.integerSlotsCount() shl 1)
			- (self.descriptor() as IntegerDescriptor).unusedIntsOfLastLong)

		/**
		 * A helper function for printing very large integers.
		 *
		 * @param magnitude
		 *   A positive integer to print.
		 * @param aStream
		 *   The [StringBuilder] on which to write this integer.
		 * @param minDigits
		 *   The minimum number of digits that must be printed, padding on the
		 *   left with zeroes as needed.
		 */
		private fun printBigInteger(
			magnitude: A_Number,
			aStream: StringBuilder,
			minDigits: Int
		) {
			assert(minDigits >= 0)
			if (magnitude.isLong) {
				// Use native printing.
				val value = magnitude.extractLong
				if (minDigits == 0) {
					aStream.append(value)
					return
				}
				val digits = value.toString()
				for (i in digits.length until minDigits) {
					aStream.append('0')
				}
				aStream.append(digits)
				return
			}
			// It's bigger than a long, so divide and conquer.  Find the largest
			// (10^18)^(2^n) still less than the number, divide it to produce a
			// quotient and divisor, and print those recursively.
			var n = 0
			var nextDivisor = quintillionInteger
			var previousDivisor: A_Number
			do {
				previousDivisor = nextDivisor
				nextDivisor = cachedSquareOfQuintillion(++n)
			} while (nextDivisor.lessThan(magnitude))
			// We went one too far.  Decrement n and use previousDivisor.
			// Splitting the number by dividing by previousDivisor assigns the
			// low 18*(2^n) digits to the remainder, and the rest to the
			// quotient.
			n--
			val remainderDigits = 18 shl n
			assert(minDigits == 0 || remainderDigits < minDigits)
			val quotient = magnitude.divideCanDestroy(previousDivisor, false)
			val remainder = magnitude.minusCanDestroy(
				quotient.timesCanDestroy(previousDivisor, false), false)
			printBigInteger(
				quotient,
				aStream,
				if (minDigits == 0) 0 else minDigits - remainderDigits)
			printBigInteger(remainder, aStream, remainderDigits)
		}

		/**
		 * Convert the specified Kotlin [Int] into an Avail integer.
		 *
		 * @param anInteger
		 *   A Java [Int] (Kotlin [Int]).
		 * @return
		 *   An [AvailObject].
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun fromInt(anInteger: Int): AvailObject =
			when (anInteger) {
				in 0..255 -> smallIntegers[anInteger]!!
				in 0 until smallIntegerLimit -> smallIntegers[anInteger] ?:
					createUninitializedInteger(1).apply {
						setIntSlot(RAW_LONG_SLOTS_, 1, anInteger)
						makeShared()
						smallIntegers[anInteger] = this
					}
				else -> createUninitializedInteger(1).apply {
					setIntSlot(RAW_LONG_SLOTS_, 1, anInteger)
				}
			}

		/**
		 * Convert the specified [Long] into a boxed Avail integer.
		 *
		 * @param aLong
		 *   A Java `long` (Kotlin [Long]).
		 * @return
		 *   An [AvailObject].
		 */
		fun fromLong(aLong: Long): AvailObject = when (aLong) {
			in 0..255 -> smallIntegers[aLong.toInt()]!!
			in 0 until smallIntegerLimit -> smallIntegers[aLong.toInt()] ?:
				createUninitializedInteger(1).apply {
					setIntSlot(RAW_LONG_SLOTS_, 1, aLong.toInt())
					makeShared()
					smallIntegers[aLong.toInt()] = this
				}
			aLong.toInt().toLong() -> createUninitializedInteger(1).apply {
				setIntSlot(RAW_LONG_SLOTS_, 1, aLong.toInt())
			}
			else -> createUninitializedInteger(2).apply {
				setIntSlot(RAW_LONG_SLOTS_, 1, aLong.toInt())
				setIntSlot(RAW_LONG_SLOTS_, 2, (aLong shr 32).toInt())
			}
		}

		/**
		 * Create an Avail integer that is the numerical equivalent of the given
		 * Java [BigInteger].
		 *
		 * @param bigInteger
		 *   The [BigInteger] to convert.
		 * @return
		 *   An Avail integer representing the same number as the argument.
		 */
		fun fromBigInteger(bigInteger: BigInteger): A_Number {
			val bytes = bigInteger.toByteArray()
			if (bytes.size <= 8) {
				return fromLong(bigInteger.toLong())
			}
			val intCount = bytes.size + 3 shr 2
			val result = createUninitializedInteger(intCount)
			// Start with the least significant bits.
			var byteIndex = bytes.size
			var destIndex = 1
			while (destIndex < intCount) {
				byteIndex -= 4 // Zero-based index to start of four byte run
				val intValue: Int = ((bytes[byteIndex].toInt() and 255 shl 24)
					+ (bytes[byteIndex + 1].toInt() and 255 shl 16)
					+ (bytes[byteIndex + 2].toInt() and 255 shl 8)
					+ (bytes[byteIndex + 3].toInt() and 255))
				result.setIntSlot(RAW_LONG_SLOTS_, destIndex, intValue)
				destIndex++
			}
			// There are at least 4 bytes present (<=8 was special-cased), so
			// create an int from the leading (most significant) 4 bytes.  Some
			// of these bytes may have already been included in the previous
			// int, but at least one and at most four bytes will survive to be
			// written to the most significant int.
			var highInt: Int = ((bytes[0].toInt() and 255 shl 24)
				+ (bytes[1].toInt() and 255 shl 16)
				+ (bytes[2].toInt() and 255 shl 8)
				+ (bytes[3].toInt() and 255))
			assert(byteIndex in 1..4)
			// Now shift away the bytes already consumed.
			highInt = highInt shr (4 - byteIndex shl 3)
			result.setIntSlot(RAW_LONG_SLOTS_, destIndex, highInt)
			// There should be no need to trim it...
			result.trimExcessInts()
			assert(intCount(result) == intCount)
			return result
		}

		/**
		 * Answer an Avail integer that holds the truncation of the `double`
		 * argument, rounded towards zero.
		 *
		 * @param aDouble
		 *   The object whose truncation should be encoded as an Avail integer.
		 * @return
		 *   An Avail integer.
		 */
		fun truncatedFromDouble(aDouble: Double): A_Number {
			// Extract the top three 32-bit sections.  That guarantees 65 bits
			// of mantissa, which is more than a double actually captures.
			var truncated = aDouble
			if (truncated >= Long.MIN_VALUE && truncated <= Long.MAX_VALUE) {
				// Common case -- it fits in a long.
				return fromLong(truncated.toLong())
			}
			val neg = truncated < 0.0
			truncated = abs(truncated)
			val exponent = getExponent(truncated)
			val slots = exponent + 31 shr 5 // probably needs work
			val out = createUninitializedInteger(slots)
			truncated = scalb(truncated, (1 - slots) shl 5)
			for (i in slots downTo 1) {
				val intSlice: Int = truncated.toInt()
				out.setIntSlot(RAW_LONG_SLOTS_, i, intSlice)
				truncated -= intSlice.toDouble()
				truncated = scalb(truncated, 32)
			}
			out.trimExcessInts()
			return when {
				neg -> zero.noFailMinusCanDestroy(out, true)
				else -> out
			}
		}

		/** The [CheckedMethod] for [IntegerDescriptor.fromInt].  */
		val fromIntMethod: CheckedMethod = staticMethod(
			IntegerDescriptor::class.java,
			::fromInt.name,
			AvailObject::class.java,
			Int::class.javaPrimitiveType!!)

		/**
		 * Convert the specified byte-valued Java `short` into an Avail
		 * integer.
		 *
		 * @param anInteger
		 *   A Java `short` (Kotlin [Short]) in the range 0..255.
		 * @return
		 *   An [AvailObject].
		 */
		fun fromUnsignedByte(anInteger: Short): AvailObject {
			assert(anInteger in 0..255)
			return smallIntegers[anInteger.toInt()]!!
		}

		/**
		 * Extract a `double` from this integer, but scale it down by the
		 * specified power of two in the process.  If the integer is beyond the
		 * scale of a double but the scale would bring it in range, don't
		 * overflow.
		 *
		 * @param self
		 *   The integer to convert to a double.
		 * @param exponentBias
		 *   The scale by which the result's exponent should be adjusted (a
		 *   positive number scales the result downward).
		 * @return
		 *   The integer times 2^-exponentBias, as a double.
		 */
		fun extractDoubleScaled(
			self: AvailObject,
			exponentBias: Int
		): Double {
			// Extract and scale the top three ints from anInteger, if
			// available. This guarantees at least 64 correct upper bits were
			// extracted (if available), which is better than a double can
			// represent.
			val slotsCount = intCount(self)
			val high = self.rawSignedIntegerAt(slotsCount).toLong()
			var d = scalb(
				high.toDouble(),
				(slotsCount - 1 shl 5) - exponentBias)
			if (slotsCount > 1) {
				// Don't sign-extend this component, since our initial estimate
				// can only be too low, never too high.  Adding in the lower
				// ints should increase the number, even if the first value was
				// negative.
				val med = self.rawUnsignedIntegerAt(slotsCount - 1)
				d += scalb(
					med.toDouble(),
					(slotsCount - 2 shl 5) - exponentBias)
				if (slotsCount > 2) {
					// Again, don't sign-extend this component.
					val low = self.rawUnsignedIntegerAt(slotsCount - 2)
					d += scalb(
						low.toDouble(),
						(slotsCount - 3 shl 5) - exponentBias)
				}
			}
			return d
		}

		/**
		 * Answer an [AvailObject] representing the integer negative one (-1).
		 *
		 * @return
		 *   The Avail integer negative one (-1).
		 */
		fun negativeOne(): A_Number = negativeOne

		/**
		 * Answer the [Int] hash value of the given [Short] in the range
		 * `0..255` inclusive.
		 *
		 * @param anInteger
		 *   The [Short] to be hashed.  It must be in the range `0..255`
		 *   inclusive.
		 * @return
		 *   The hash of the passed unsigned byte.
		 */
		fun hashOfUnsignedByte(anInteger: Short) =
			hashesOfSmallIntegers[anInteger.toInt()]

		/** The initialization value for computing the hash of an integer.  */
		private const val initialHashValue = 0x13592884

		/**
		 * The value to xor with after multiplying by the
		 * [AvailObject.multiplier] for each [Int] slot of the integer.
		 */
		private const val postMultiplyHashToggle = -0x6a004a61

		/**
		 * The value to add after performing a final extra multiply by
		 * [AvailObject.multiplier].
		 */
		private const val finalHashAddend = 0x5127ee66

		/**
		 * A table of random values.  This has different content on every run.
		 */
		private val byteHashes = IntArray(256) {
			Random.nextBits(32)
		}

		private fun combineHash(currentHash: Int, nextInt: Int): Int
		{
			var hash = currentHash xor nextInt
			var index = hash xor (hash shr 16)
			index -= (index shr 8)
			hash += byteHashes[index and 0xFF]
			hash -= byteHashes[(index shr 8) and 0xFF]
			return hash xor postMultiplyHashToggle
		}

		/**
		 * Hash the passed [Int].  Note that it must have the same value as what
		 * [computeHashOfIntegerObject] would return, given an encoding of the
		 * [Int] as an Avail integer.
		 *
		 * @param anInt
		 *   The [Int] to hash.
		 * @return
		 *   The hash of the given [Int], consistent with Avail integer hashing.
		 */
		fun computeHashOfInt(anInt: Int): Int {
			var output = combineHash(initialHashValue, anInt)
			output *= multiplier
			output += finalHashAddend
			return output
		}

		/**
		 * Hash the passed [Long].  Note that it must have the same value as
		 * what [computeHashOfIntegerObject] would return, given an encoding of
		 * the [Long] as an Avail integer.  Even if that integer is within the
		 * 32-bit [Int] range.
		 *
		 * @param aLong
		 *   The [Long] to hash.
		 * @return
		 *   The hash of the given [Long], consistent with Avail integer hashing.
		 */
		fun computeHashOfLong(aLong: Long): Int {
			val lowInt = aLong.toInt()
			if (aLong == lowInt.toLong()) return computeHashOfInt(lowInt)
			var output = initialHashValue

			// First unrolled loop iteration (high 32).
			output = combineHash(output, (aLong shr 32).toInt())

			// Second unrolled loop iteration (low 32)
			output = combineHash(output, lowInt)

			// After loop.
			output *= multiplier
			output += finalHashAddend
			return output
		}

		/**
		 * Compute the hash of the given Avail integer object.  Note that if the
		 * input is within the range of an [Int], it should produce the same
		 * value as the equivalent invocation of [.computeHashOfInt].
		 *
		 * @param anIntegerObject
		 * An Avail integer to be hashed.
		 * @return The hash of the given Avail integer.
		 */
		private fun computeHashOfIntegerObject(
			anIntegerObject: AvailObject
		): Int {
			var output = initialHashValue
			for (i in intCount(anIntegerObject) downTo 1) {
				output = combineHash(
					output, anIntegerObject.rawSignedIntegerAt(i))
			}
			output *= multiplier
			output += finalHashAddend
			return output
		}

		/**
		 * Create a mutable Avail integer with the specified number of
		 * uninitialized [Int] slots.
		 *
		 * @param size
		 *   The number of [Int] slots to have in the result.
		 * @return
		 *   An uninitialized, mutable integer.
		 */
		fun createUninitializedInteger(size: Int): AvailObject =
			mutableFor(size).create(size + 1 shr 1)

		/**
		 * The static list of descriptors of this kind, organized by mutability
		 * and then the number of unused ints in the last long.  This is mostly
		 * a vestigial effect of having used 32-bit integer slots in the past.
		 * There is still some merit to the technique, since for example integer
		 * multiplication has to use u32 x u32 -> u64 for speed.
		 */
		private val descriptors = EnumMap.enumMap { mut: Mutability ->
			Array(2) { unusedInts ->
				IntegerDescriptor(mut, unusedInts.toByte())
			}
		}

		/**
		 * Values below this limit can be looked up in [smallIntegers], and
		 * their hashes can also be looked up in [hashesOfSmallIntegers].  This
		 * value MUST be at least 256.
		 */
		private const val smallIntegerLimit = 16384

		/**
		 * A cache that maps from an index to a corresponding [A_Number].  The
		 * elements `[0..255]` are pre-populated here.  The remaining elements
		 * are only populated as needed.  No lock is needed, since the
		 * descriptor field of AvailObject is volatile, and set to the shared
		 * descriptor just prior to being put in the array.  If another thread
		 * sees the value, it can be assured that the content of the object was
		 * written at a `happens-before` time.
		 */
		private val smallIntegers =
			arrayOfNulls<AvailObject>(smallIntegerLimit).also { array ->
				(0..255).forEach { i ->
					array[i] = createUninitializedInteger(1).apply {
						rawSignedIntegerAtPut(1, i)
						makeShared()
					}
				}
			}

		/**
		 * Answer the mutable descriptor that is suitable to describe an integer
		 * with the given number of int slots.
		 *
		 * @param size
		 *   How many int slots are in the large integer to be represented by
		 *   the descriptor.
		 * @return
		 *   An `IntegerDescriptor` suitable for representing an integer of the
		 *   given mutability and int slot count.
		 */
		private fun mutableFor(size: Int) = descriptors[MUTABLE]!![size and 1]

		/**
		 * An array of size [smallIntegerLimit] of [Int]s, corresponding to the
		 * hashes of the integers at the corresponding indices.
		 */
		private val hashesOfSmallIntegers = IntArray(smallIntegerLimit) {
			computeHashOfInt(it)
		}

		/** An Avail integer representing zero (0).  */
		val zero: AvailObject = smallIntegers[0]!!

		/** An Avail integer representing one (1).  */
		val one: AvailObject = smallIntegers[1]!!

		/** An Avail integer representing two (2).  */
		val two: AvailObject = smallIntegers[2]!!

		/** The Avail integer negative one (-1).  */
		var negativeOne: AvailObject =
			createUninitializedInteger(1).apply {
				rawSignedIntegerAtPut(1, -1)
				makeShared()
			}

		/**
		 * One (U.S.) quintillion, which is 10^18.  This is the largest power of
		 * ten representable as a signed long.
		 */
		private const val quintillionLong = 1_000_000_000_000_000_000L

		/**
		 * One (U.S.) quintillion, which is 10^18.  This is the largest power of
		 * ten for which [A_BasicObject.isLong] returns true.
		 */
		private val quintillionInteger: A_Number =
			fromLong(quintillionLong).makeShared()

		/**
		 * Successive squares of one (U.S.) quintillion, 10^18.  Element #n is
		 * equal to (10^18)^(2^n).  List access is protected by
		 * [squaresOfQuintillionLock].
		 */
		@GuardedBy("squaresOfQuintillionLock")
		private val squaresOfQuintillion = mutableListOf(quintillionInteger)

		/** The lock that protects access to squaresOfQuintillion. */
		private val squaresOfQuintillionLock = ReentrantReadWriteLock()

		/**
		 * Answer the nth successive square of a (U.S.) quintillion, which will
		 * be `(10^18)^(2^n)`.  N must be ≥ 0.  Cache this number for
		 * performance.
		 *
		 * @param n
		 *   The number of times to successively square a quintillion.
		 * @return
		 *   The value `(10^18)^(2^n)`.
		 */
		fun cachedSquareOfQuintillion(n: Int): A_Number {
			// Use a safe double-check mechanism.  Use a read-lock first.
			squaresOfQuintillionLock.read {
				if (n < squaresOfQuintillion.size) {
					return squaresOfQuintillion[n]
				}
			}
			// Otherwise, hold the write-lock and try again.
			squaresOfQuintillionLock.safeWrite {
				// Note that the list may have changed between releasing the
				// read-lock and acquiring the write-lock.  The for-loop should
				// accommodate that situation.
				for (size in squaresOfQuintillion.size..n) {
					val last = squaresOfQuintillion[size - 1]
					val next = last.timesCanDestroy(last, false).makeShared()
					squaresOfQuintillion.add(next)
				}
				return squaresOfQuintillion[n]
			}
		}
	}

	override fun mutable() = descriptors[MUTABLE]!![unusedIntsOfLastLong.toInt()]

	override fun immutable() =
		descriptors[IMMUTABLE]!![unusedIntsOfLastLong.toInt()]

	override fun shared() = descriptors[SHARED]!![unusedIntsOfLastLong.toInt()]
}
