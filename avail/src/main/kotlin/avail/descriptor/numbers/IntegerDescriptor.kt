/*
 * IntegerDescriptor.kt
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
package avail.descriptor.numbers

import avail.descriptor.numbers.DoubleDescriptor.Companion.addDoubleAndIntegerCanDestroy
import avail.descriptor.numbers.DoubleDescriptor.Companion.compareDoubleAndInteger
import avail.descriptor.numbers.DoubleDescriptor.Companion.fromDoubleRecycling
import avail.descriptor.numbers.FloatDescriptor.Companion.fromFloatRecycling
import avail.descriptor.numbers.InfinityDescriptor.Companion.negativeInfinity
import avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import avail.descriptor.numbers.IntegerDescriptor.Companion.combineWord
import avail.descriptor.numbers.IntegerDescriptor.Companion.computeHashOfIntegerObject
import avail.descriptor.numbers.IntegerDescriptor.Companion.hashesOfSmallIntegers
import avail.descriptor.numbers.IntegerDescriptor.Companion.integerHashSalt
import avail.descriptor.numbers.IntegerDescriptor.Companion.smallIntegers
import avail.descriptor.numbers.IntegerDescriptor.Companion.squaresOfQuintillionLock
import avail.descriptor.numbers.IntegerDescriptor.IntegerSlots.RAW_LONG_SLOTS_
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Number
import avail.descriptor.representation.A_Number.Companion.addToIntegerCanDestroy
import avail.descriptor.representation.A_Number.Companion.asBigInteger
import avail.descriptor.representation.A_Number.Companion.bitShift
import avail.descriptor.representation.A_Number.Companion.divideCanDestroy
import avail.descriptor.representation.A_Number.Companion.divideIntoIntegerCanDestroy
import avail.descriptor.representation.A_Number.Companion.equalsInteger
import avail.descriptor.representation.A_Number.Companion.extractDouble
import avail.descriptor.representation.A_Number.Companion.extractInt
import avail.descriptor.representation.A_Number.Companion.extractLong
import avail.descriptor.representation.A_Number.Companion.extractSignedByte
import avail.descriptor.representation.A_Number.Companion.extractSignedShort
import avail.descriptor.representation.A_Number.Companion.extractUnsignedByte
import avail.descriptor.representation.A_Number.Companion.greaterThan
import avail.descriptor.representation.A_Number.Companion.isInt
import avail.descriptor.representation.A_Number.Companion.isLong
import avail.descriptor.representation.A_Number.Companion.isSignedByte
import avail.descriptor.representation.A_Number.Companion.isSignedShort
import avail.descriptor.representation.A_Number.Companion.lessOrEqual
import avail.descriptor.representation.A_Number.Companion.lessThan
import avail.descriptor.representation.A_Number.Companion.minusCanDestroy
import avail.descriptor.representation.A_Number.Companion.multiplyByIntegerCanDestroy
import avail.descriptor.representation.A_Number.Companion.noFailMinusCanDestroy
import avail.descriptor.representation.A_Number.Companion.numericCompareToInteger
import avail.descriptor.representation.A_Number.Companion.subtractFromIntegerCanDestroy
import avail.descriptor.representation.A_Number.Companion.timesCanDestroy
import avail.descriptor.representation.A_Number.Companion.trimExcessInts
import avail.descriptor.representation.A_Number.Companion.whichPowerOfTwo
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.isSupertypeOfPrimitiveTypeEnum
import avail.descriptor.representation.A_Type.Companion.lowerBound
import avail.descriptor.representation.A_Type.Companion.lowerInclusive
import avail.descriptor.representation.A_Type.Companion.upperBound
import avail.descriptor.representation.A_Type.Companion.upperInclusive
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.AvailObject.Companion.combine4
import avail.descriptor.representation.AvailObjectFieldHelper
import avail.descriptor.representation.AvailObjectRepresentation.Companion.newLike
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.IntegerSlotsEnum.Companion.describeLong
import avail.descriptor.representation.Mutability
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.singleInteger
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.TypeTag
import avail.exceptions.ArithmeticException
import avail.exceptions.AvailErrorCode
import avail.exceptions.AvailErrorCode.E_CANNOT_DIVIDE_BY_ZERO
import avail.exceptions.AvailErrorCode.E_CANNOT_MULTIPLY_ZERO_AND_INFINITY
import avail.exceptions.AvailSystemLimitException
import avail.exceptions.MarshalingException
import avail.interpreter.JavaLibrary.int
import avail.optimizer.jvm.CheckedMethod
import avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import avail.optimizer.jvm.ReferencedInGeneratedCode
import avail.serialization.SerializerOperation
import avail.utility.carryOut
import avail.utility.isInt
import avail.utility.safeWrite
import org.availlang.json.JSONWriter
import java.lang.Double.doubleToRawLongBits
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
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class IntegerDescriptor private constructor(
	mutability: Mutability
) : ExtendedIntegerDescriptor(
	mutability,
	TypeTag.UNKNOWN_TAG,
	null,
	IntegerSlots::class.java)
{
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
		recursionMap: IdentityHashMap<A_BasicObject, Unit>,
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
	): Array<AvailObjectFieldHelper> = when
	{
		self.isLong -> emptyArray()
		else -> super.o_DescribeForDebugger(self)
	}

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		(another as A_Number).equalsInteger(self)

	/**
	 * Compare two integers for equality.
	 */
	override fun o_EqualsInteger(
		self: AvailObject,
		anAvailInteger: AvailObject
	): Boolean {
		val intCount = intCount(self)
		// Assume integers being compared are always normalized (trimmed).
		if (intCount != intCount(anAvailInteger)) return false
		return self.compareLongSlots(
			RAW_LONG_SLOTS_,
			1,
			self.variableIntegerSlotsCount(),
			anAvailInteger)
	}

	/**
	 * Check if this is an integer whose value equals the given int.
	 *
	 * Assume it's normalized (trimmed).
	 */
	override fun o_EqualsLong(
		self: AvailObject,
		theLong: Long
	) = intCount(self) <= 2 && self.extractLong == theLong

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
		self.isLong && self.extractLong in 0L..15L

	override fun o_IsSignedByte(self: AvailObject) =
		self.isLong && self.extractLong in -128L..127L

	override fun o_IsUnsignedByte(self: AvailObject) =
		self.isLong && self.extractLong in 0L..255L

	override fun o_IsSignedShort(self: AvailObject) =
		self.isLong && self.extractLong in -32768L..32767L

	override fun o_IsUnsignedShort(self: AvailObject) =
		self.isLong && self.extractLong in 0L..65535L

	override fun o_IsInt(self: AvailObject) =
		self.isLong && self.extractLong.isInt

	override fun o_IsLong(self: AvailObject) = intCount(self) <= 2

	override fun o_ExtractNybble(self: AvailObject): Byte {
		val value = self.extractLong
		assert(value == value and 15L) { "Value is out of range for a nybble" }
		return value.toByte()
	}

	override fun o_ExtractSignedByte(self: AvailObject): Byte {
		val value = self.extractLong
		assert(value == value.toByte().toLong()) {
			"Value is out of range for a signed byte"
		}
		return value.toByte()
	}

	override fun o_ExtractUnsignedByte(self: AvailObject): Short {
		val value = self.extractLong
		assert(value == value and 255L) {
			"Value is out of range for an unsigned byte"
		}
		return value.toShort()
	}

	override fun o_ExtractSignedShort(self: AvailObject): Short {
		val value = self.extractLong
		assert(value == value.toShort().toLong()) {
			"Value is out of range for a signed short"
		}
		return value.toShort()
	}

	override fun o_ExtractUnsignedShort(self: AvailObject): Int {
		val value = self.extractLong
		assert(value == value and 65535L) {
			"Value is out of range for an unsigned short"
		}
		return value.toInt()
	}

	override fun o_ExtractInt(self: AvailObject): Int {
		assert(intCount(self) == 1) { "Integer value out of bounds" }
		val value = self.extractLong
		val truncated = (value shl 32) shr 32
		assert(value == truncated) { "Integer value out of bounds" }
		return truncated.toInt()
	}

	override fun o_ExtractLong(self: AvailObject): Long {
		assert(intCount(self) <= 2) { "Integer value out of bounds" }
		// A value in long range is a single, canonical long.
		return self.longSlot(RAW_LONG_SLOTS_, 1)
	}

	override fun o_ExtractFloat(self: AvailObject): Float =
		extractDoubleScaled(self, 0).toFloat()

	override fun o_ExtractDouble(self: AvailObject): Double =
		extractDoubleScaled(self, 0)

	override fun o_TrimExcessInts(self: AvailObject) {
		// Remove any redundant top longs.  A top long that is just the sign
		// word of the long below it adds nothing, so the storage can simply be
		// truncated, with no need to shift data around.
		assert(isMutable)
		val originalLongs = longCount(self)
		var longs = originalLongs
		while (longs > 1
			&& self.longSlot(RAW_LONG_SLOTS_, longs)
				== self.longSlot(RAW_LONG_SLOTS_, longs - 1) shr 63)
		{
			longs--
		}
		if (longs < originalLongs)
		{
			self.truncateWithFillerForNewIntegerSlotsCount(longs)
			self.descriptor = mutable
		}
	}

	override fun o_AddToInfinityCanDestroy(
		self: AvailObject,
		sign: Sign,
		canDestroy: Boolean
	): A_Number
	{
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
	 * @param selfIntCount
	 *   The size of self in ints.
	 * @param anotherIntCount
	 *   The number of ints in the representation of another.
	 * @param canDestroy
	 *   A boolean indicating whether one of the arguments can actually be
	 *   reused if it's mutable.
	 * @return
	 *   One of the arguments, or `null` if neither argument was suitable.
	 */
	private fun largerMutableOf(
		self: AvailObject,
		another: AvailObject,
		selfIntCount: Int,
		anotherIntCount: Int,
		canDestroy: Boolean
	): AvailObject? {
		return when {
			!canDestroy -> null
			selfIntCount == anotherIntCount -> when {
				isMutable -> self
				another.descriptor.isMutable -> another
				else -> null
			}
			selfIntCount > anotherIntCount -> if (isMutable) self else null
			another.descriptor.isMutable -> another
			else -> null
		}
	}

	override fun o_AddToIntegerCanDestroy(
		self: AvailObject,
		anInteger: AvailObject,
		canDestroy: Boolean
	): A_Number
	{
		if (self.isLong && anInteger.isLong)
		{
			val x = anInteger.extractLong
			val y = self.extractLong
			val sum = x + y
			// Detect 64-bit overflow for addition.  It occurs only when both
			// inputs have the same sign and the sum's sign differs.
			if (((x xor sum) and (y xor sum)) >= 0)
				return fromLongRecycling(sum, self, anInteger, canDestroy)
		}
		// At least one input is beyond the long range, or the sum overflows.
		return chainedAddOrSubtract(self, anInteger, 0L, canDestroy)
	}

	/**
	 * Answer the `i`th 64-bit word of the [integer], which has [words] words.
	 * Beyond its last word, the value is sign-extended.
	 *
	 * @param integer
	 *   The integer whose word is wanted.
	 * @param words
	 *   The number of 64-bit words that [integer] holds.
	 * @param i
	 *   The one-based index of the word.
	 * @return
	 *   The word, as a signed [Long].
	 */
	private fun longWordOrSign(integer: AvailObject, words: Int, i: Int): Long =
		if (i <= words) integer.longSlot(RAW_LONG_SLOTS_, i)
		else integer.longSlot(RAW_LONG_SLOTS_, words) shr 63

	/**
	 * Answer an object to hold a result of [words] 64-bit words.  That's one of
	 * the operands if [canDestroy] allows it, the operand is mutable, and it
	 * already has exactly [words] words.  Otherwise, it's a fresh object.
	 *
	 * @param a
	 *   The first operand.
	 * @param b
	 *   The second operand.
	 * @param words
	 *   The number of 64-bit words in the result.
	 * @param canDestroy
	 *   Whether an operand may be overwritten.
	 * @return
	 *   A mutable integer with room for [words] words.
	 */
	private fun outputFor(
		a: AvailObject,
		b: AvailObject,
		words: Int,
		canDestroy: Boolean
	): AvailObject = when {
		canDestroy && a.descriptor.isMutable && a.integerSlotsCount() == words
			-> a
		canDestroy && b.descriptor.isMutable && b.integerSlotsCount() == words
			-> b
		else -> createUninitializedLongInteger(words)
	}

	private fun chainedAddOrSubtract(
		a: AvailObject,
		b: AvailObject,
		complementMask: Long,
		canDestroy: Boolean
	): AvailObject
	{
		val aWords = a.integerSlotsCount()
		val bWords = b.integerSlotsCount()
		val words = max(aWords, bWords)
		// The operands' top words are read before the output is written, since
		// the output may be one of the operands.
		val aTop = longWordOrSign(a, aWords, words)
		val bTop = longWordOrSign(b, bWords, words) xor complementMask
		val output = outputFor(a, b, words, canDestroy)
		var carry = complementMask and 1L
		for (i in 1..words) {
			val x = longWordOrSign(a, aWords, i)
			val y = longWordOrSign(b, bWords, i) xor complementMask
			output.setLongSlot(RAW_LONG_SLOTS_, i, x + y + carry)
			carry = carryOut(x, y, carry)
		}
		// The true value is the unsigned words plus (carry - aSign - bSign)
		// times 2^(64*words), where aSign and bSign are the sign bits of the
		// operands' top words (after complementing b).  The result's top word
		// is read as signed, so the value needs growth exactly when that
		// extension differs from the top word's sign bit.  The extension is
		// always -1, 0, or 1.
		val resultTop = output.longSlot(RAW_LONG_SLOTS_, words)
		val extension = carry - (aTop ushr 63) - (bTop ushr 63)
		if (extension != resultTop shr 63)
		{
			// Grow by one word.  The copied top word is now unsigned, so the
			// new top word is the extension itself.
			checkLongCount(words + 1)
			val grown = newLike(mutable, output, 0, 1)
			grown.setLongSlot(RAW_LONG_SLOTS_, words + 1, extension)
			grown.trimExcessInts()
			return grown
		}
		output.trimExcessInts()
		return output
	}

	override fun o_AddToDoubleCanDestroy(
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean
	): A_Number
	{
		val d: Double = addDoubleAndIntegerCanDestroy(
			doubleObject.extractDouble, self, canDestroy)
		return fromDoubleRecycling(d, doubleObject, canDestroy)
	}

	override fun o_AddToFloatCanDestroy(
		self: AvailObject,
		floatObject: A_Number,
		canDestroy: Boolean
	): A_Number
	{
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
				throw ArithmeticException(E_CANNOT_DIVIDE_BY_ZERO)
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
					val quotient = if (numerator < 0)
					{
						// n/d for n<0, d>0:  use -1-(-1-n)/d
						// e.g., -9/5  = -1-(-1+9)/5  = -1-8/5 = -2
						// e.g., -10/5 = -1-(-1+10)/5 = -1-9/5 = -2
						// e.g., -11/5 = -1-(-1+11)/5 = -1-10/5 = -3
						-1 - (-1 - numerator) / denominator
					}
					else
					{
						// This won't overflow, because the numerator isn't
						// Long.MIN_VALUE.
						numerator / denominator
					}
					// Overflow can't have happened, since Long.MIN_VALUE was
					// already excluded from the numerator.
					return fromLongRecycling(
						quotient, self, anInteger, canDestroy)
				}
			}
		}
		// Longs won't suffice, so fall back to BigInteger.  Its division
		// truncates toward zero, so round toward negative infinity by hand: a
		// nonzero remainder with the opposite sign of the divisor means the
		// truncated quotient is one too large.  The divisor is nonzero here,
		// since a zero divisor is always a long.
		val denominatorBigInt = self.asBigInteger()
		val qr = anInteger.asBigInteger().divideAndRemainder(denominatorBigInt)
		val remainderSign = qr[1].signum()
		val quotient = if (remainderSign != 0
			&& remainderSign != denominatorBigInt.signum())
		{
			qr[0].subtract(BigInteger.ONE)
		}
		else qr[0]
		return fromBigInteger(quotient)
	}

	override fun o_DivideIntoDoubleCanDestroy(
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean
	): A_Number
	{
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
	): A_Number
	{
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
	): A_Number
	{
		if (self.isLong && anInteger.isLong)
		{
			val x = self.extractLong
			val y = anInteger.extractLong
			val low = x * y
			val high = Math.multiplyHigh(x, y)
			if (high == low shr 63)
			{
				// High is just a repetition of low's sign bit.
				return fromLongRecycling(low, self, anInteger, canDestroy)
			}
			// We know it fits in two longs, so go ahead and use that knowledge.
			return createUninitializedLongInteger(2).apply {
				setLongSlot(RAW_LONG_SLOTS_, 1, low)
				setLongSlot(RAW_LONG_SLOTS_, 2, high)
			}
		}
		if (self.equals(zero) || anInteger.equals(zero)) return zero
		var shift = self.whichPowerOfTwo
		if (shift >= 0) return anInteger.bitShift(fromInt(shift), canDestroy)
		shift = anInteger.whichPowerOfTwo
		if (shift >= 0) return self.bitShift(fromInt(shift), canDestroy)
		//TODO Tune this threshold, which is the number of word multiplications
		// required with the naive method, which avoids the (linear) cost of
		// conversion to BigIntegers.
		if (self.integerSlotsCount() > 20 && anInteger.integerSlotsCount() > 20)
		{
			return fromBigInteger(
				self.asBigInteger() * anInteger.asBigInteger())
		}
		return multiplyWords(self, anInteger)
	}

	/**
	 * Answer the product of the two integers, computed column by column over
	 * 64-bit words.  The product of an m-word and an n-word integer fits in m+n
	 * words, so both operands are sign-extended to that many words and the
	 * product is taken modulo 2^(64(m+n)).  Each column adds the 128-bit
	 * products of its word pairs into a 192-bit accumulator.
	 *
	 * @param a
	 *   The first integer.
	 * @param b
	 *   The second integer.
	 * @return
	 *   The product, in canonical form.
	 */
	private fun multiplyWords(a: AvailObject, b: AvailObject): AvailObject
	{
		val aWords = a.integerSlotsCount()
		val bWords = b.integerSlotsCount()
		val words = aWords + bWords
		val output = createUninitializedLongInteger(words)
		// The 192-bit accumulator for the current column is low, mid, high.
		var low = 0L
		var mid = 0L
		var high = 0L
		for (k in 1..words)
		{
			// Column k collects the word pairs (i, j) with i + j = k + 1.
			for (i in 1..k)
			{
				val x = longWordOrSign(a, aWords, i)
				val y = longWordOrSign(b, bWords, k + 1 - i)
				val product = x * y
				val productHigh = Math.unsignedMultiplyHigh(x, y)
				val carryLow = carryOut(low, product, 0L)
				low += product
				val carryMid = carryOut(mid, productHigh, carryLow)
				mid += productHigh + carryLow
				high += carryMid
			}
			output.setLongSlot(RAW_LONG_SLOTS_, k, low)
			low = mid
			mid = high
			high = 0L
		}
		output.trimExcessInts()
		return output
	}

	override fun o_MultiplyByDoubleCanDestroy(
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean
	): A_Number
	{
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
	): A_Number
	{
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
	): A_Number
	{
		if (self.isLong && anInteger.isLong)
		{
			val x = anInteger.extractLong
			val y = self.extractLong
			val diff = x - y
			// Detect 64-bit overflow for subtraction.
			if (((x xor y) and (x xor diff)) >= 0)
				return fromLongRecycling(diff, self, anInteger, canDestroy)
		}
		// At least one input is beyond the long range, or the difference
		// overflows.  The result is anInteger - self.
		return chainedAddOrSubtract(anInteger, self, -1L, canDestroy)
	}

	override fun o_SubtractFromDoubleCanDestroy(
		self: AvailObject,
		doubleObject: A_Number,
		canDestroy: Boolean
	): A_Number
	{
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
	): A_Number
	{
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
		val selfWords = self.integerSlotsCount()
		val anIntegerWords = anInteger.integerSlotsCount()
		val words = max(selfWords, anIntegerWords)
		// The top word is signed, so it's compared on its own.
		val selfTop = longWordOrSign(self, selfWords, words)
		val anIntegerTop = longWordOrSign(anInteger, anIntegerWords, words)
		if (selfTop != anIntegerTop) {
			return if (selfTop < anIntegerTop) Order.LESS else Order.MORE
		}
		// The lower words are unsigned.
		for (i in words - 1 downTo 1) {
			val a = longWordOrSign(self, selfWords, i)
			val b = longWordOrSign(anInteger, anIntegerWords, i)
			if (a != b) {
				return if (java.lang.Long.compareUnsigned(a, b) < 0) Order.LESS
					else Order.MORE
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
	 * A helper function for bitwise operations.  Apply the given operation to
	 * the 64-bit words of the two operands, sign-extending the shorter one.  A
	 * bitwise operation never grows its result past the longer operand, so no
	 * fixup is needed.  If both operands are in long range, the result is a
	 * single long, and [canDestroy] allows recycling an operand.
	 *
	 * @param self
	 *   The receiver.
	 * @param anInteger
	 *   The other operand.
	 * @param canDestroy
	 *   Whether an operand may be clobbered by the result.
	 * @param operation
	 *   The bitwise operation to apply to each pair of words.
	 * @return
	 *   The result, in canonical form.
	 */
	private inline fun bitwiseOperation(
		self: AvailObject,
		anInteger: A_Number,
		canDestroy: Boolean,
		operation: (Long, Long) -> Long
	): AvailObject
	{
		val other = anInteger.traversed()
		if (self.isLong && other.isLong)
		{
			return fromLongRecycling(
				operation(self.extractLong, other.extractLong),
				self,
				other,
				canDestroy)
		}
		val selfWords = self.integerSlotsCount()
		val otherWords = other.integerSlotsCount()
		val words = max(selfWords, otherWords)
		// Each word is read before the same index of the output is written.
		val output = outputFor(self, other, words, canDestroy)
		for (i in 1..words)
		{
			output.setLongSlot(
				RAW_LONG_SLOTS_,
				i,
				operation(
					longWordOrSign(self, selfWords, i),
					longWordOrSign(other, otherWords, i)))
		}
		output.trimExcessInts()
		return output
	}

	override fun o_BitwiseAnd(
		self: AvailObject,
		anInteger: A_Number,
		canDestroy: Boolean
	): A_Number = bitwiseOperation(self, anInteger, canDestroy, Long::and)

	override fun o_BitwiseOr(
		self: AvailObject,
		anInteger: A_Number,
		canDestroy: Boolean
	): A_Number = bitwiseOperation(self, anInteger, canDestroy, Long::or)

	override fun o_BitwiseXor(
		self: AvailObject,
		anInteger: A_Number,
		canDestroy: Boolean
	): A_Number = bitwiseOperation(self, anInteger, canDestroy, Long::xor)

	override fun o_BitTest(
		self: AvailObject,
		bitPosition: Long
	): Boolean
	{
		assert (bitPosition >= 0)
		// Beyond the stored longs, the sign bit of the top long is the bit.
		val boundedBitPosition =
			min(bitPosition, (longCount(self).toLong() shl 6) - 1)
		val longValue = self.longSlot(
			RAW_LONG_SLOTS_, (boundedBitPosition ushr 6).toInt() + 1)
		val bit = (boundedBitPosition and 63L).toInt()
		return (longValue and (1L shl bit)) != 0L
	}

	override fun o_BitSet(
		self: AvailObject,
		bitPosition: Long,
		value: Boolean,
		canDestroy: Boolean
	) : A_Number
	{
		assert (bitPosition >= 0)
		// Longs are addressed by an Int, so a bit beyond the largest integer
		// can't be represented.
		val longIndexLong = (bitPosition ushr 6) + 1
		if (longIndexLong > MAX_LONG_COUNT)
		{
			throw AvailSystemLimitException(
				"Bit position $bitPosition is beyond the largest integer")
		}
		val longIndex = longIndexLong.toInt()
		val oldLongCount = longCount(self)
		val mask = 1L shl (bitPosition and 63L).toInt()
		val oldLong = when
		{
			longIndex <= oldLongCount -> self.longSlot(RAW_LONG_SLOTS_, longIndex)
			self.lessThan(zero) -> -1L
			else -> 0L
		}
		val newLong = when
		{
			value -> oldLong or mask
			else -> oldLong and mask.inv()
		}
		if (oldLong == newLong)
		{
			// No change.
			if (isMutable && !canDestroy) self.makeImmutable()
			return self
		}
		// The bit changed.  If it's beyond the stored longs, add a long.
		val newLongCount = max(oldLongCount, longIndex)
		if (oldLongCount == newLongCount && isMutable && canDestroy)
		{
			// Clobber it in place.  Writing a whole long keeps the spare half
			// of any odd int count canonical, so only the length may change.
			self.setLongSlot(RAW_LONG_SLOTS_, longIndex, newLong)
			self.trimExcessInts()
			return self
		}
		// It must be copied.
		checkLongCount(newLongCount)
		val result = newLike(mutable, self, 0, newLongCount - oldLongCount)
		if (self.lessThan(zero))
		{
			for (i in oldLongCount + 1 .. newLongCount)
			{
				result.setLongSlot(RAW_LONG_SLOTS_, i, -1L)
			}
		}
		result.setLongSlot(RAW_LONG_SLOTS_, longIndex, newLong)
		result.trimExcessInts()
		return result
	}

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
	): A_Number
	{
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
		if (self.isLong && truncationInt < 63)
		{
			// The low 64 bits of an arithmetic shift are exact, so the low
			// truncationInt bits are too.  Zero bits gives zero.
			val resultLong = arithmeticBitShiftLong(self.extractLong, shiftInt) and
				((1L shl truncationInt) - 1)
			if (canDestroy && isMutable && self.integerSlotsCount() == 1)
			{
				self.setLongSlot(RAW_LONG_SLOTS_, 1, resultLong)
				return self
			}
			return fromLong(resultLong)
		}
		if (truncationInt == 0) return zero
		return truncatedShift(self, shiftInt, truncationInt)
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
	): A_Number
	{
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
				else -> negativeOne
			}
		}
		val shiftInt = shiftFactor.extractInt
		if (self.isLong) {
			val baseLong = self.extractLong
			val shiftedLong = arithmeticBitShiftLong(baseLong, shiftInt)
			// Either a right shift, or a left shift that didn't lose bits, so the
			// result is still a long.
			if (shiftInt < 0
				|| arithmeticBitShiftLong(shiftedLong, -shiftInt) == baseLong)
			{
				if (canDestroy && isMutable && self.integerSlotsCount() == 1)
				{
					self.setLongSlot(RAW_LONG_SLOTS_, 1, shiftedLong)
					return self
				}
				return fromLong(shiftedLong)
			}
		}
		return arithmeticShift(self, shiftInt)
	}

	/**
	 * Answer the one-based [j]th 64-bit word of ⌊[integer] × 2<sup>[shift]</sup>⌋,
	 * where [integer] holds [wordCount] words.  The result's bit k comes from
	 * the source bit k − [shift], so each result word combines two adjacent
	 * source words.  Below the source, bits are zero; above it, they're the
	 * sign.
	 *
	 * @param integer
	 *   The integer being shifted.
	 * @param wordCount
	 *   The number of 64-bit words in [integer].
	 * @param shift
	 *   The shift amount, which may be negative for an arithmetic right shift.
	 * @param j
	 *   The one-based index of the result word.
	 * @return
	 *   The word, as a signed [Long].
	 */
	private fun extractShiftedLong(
		integer: AvailObject,
		wordCount: Int,
		shift: Int,
		j: Int
	): Long
	{
		// The shift is q words and b bits, with 0 ≤ b < 64.  A nonzero b makes
		// the low source word one further back, and the bit offset r is 64 − b.
		val q = shift shr 6
		val b = shift and 63
		val low = j - q - ((b + 63) ushr 6)
		val r = (-b) and 63
		// Shifting in two steps avoids Java's shift-count modulus at r = 0.
		val lowPart = wordOrZeroOrSign(integer, wordCount, low) ushr r
		val highPart =
			(wordOrZeroOrSign(integer, wordCount, low + 1) shl 1) shl (63 - r)
		return lowPart or highPart
	}

	/**
	 * Answer the one-based [i]th 64-bit word of [integer], which holds
	 * [wordCount] words.  Below the first word the value is zero, and beyond the
	 * last word it is sign-extended.
	 *
	 * @param integer
	 *   The integer whose word is wanted.
	 * @param wordCount
	 *   The number of 64-bit words that [integer] holds.
	 * @param i
	 *   The one-based index of the word.
	 * @return
	 *   The word, as a signed [Long].
	 */
	private fun wordOrZeroOrSign(
		integer: AvailObject,
		wordCount: Int,
		i: Int
	): Long
	{
		if (i < 1) return 0L
		if (i <= wordCount) return integer.longSlot(RAW_LONG_SLOTS_, i)
		return integer.longSlot(RAW_LONG_SLOTS_, wordCount) shr 63
	}

	/**
	 * Answer ⌊[self] × 2<sup>[shift]</sup>⌋ in canonical form, computed over
	 * 64-bit words.  The shift may be negative for an arithmetic right shift.
	 *
	 * @param self
	 *   The integer to shift.
	 * @param shift
	 *   The shift amount.
	 * @return
	 *   The shifted integer.
	 */
	private fun arithmeticShift(self: AvailObject, shift: Int): A_Number
	{
		val words = self.integerSlotsCount()
		val resultWords = max(words + (shift shr 6) + 1, 1)
		val output = createUninitializedLongInteger(resultWords)
		for (j in 1..resultWords)
		{
			output.setLongSlot(
				RAW_LONG_SLOTS_, j, extractShiftedLong(self, words, shift, j))
		}
		output.trimExcessInts()
		return output
	}

	/**
	 * Answer [self] × 2<sup>[shift]</sup>, truncated to the low [bits] bits, so
	 * the result is in `[0, 2^bits)`.  The shift is applied as an arithmetic
	 * shift first, so the truncated bits are those of the infinite two's
	 * complement value.
	 *
	 * @param self
	 *   The integer to shift.
	 * @param shift
	 *   The shift amount.
	 * @param bits
	 *   The number of low bits to keep, which must be positive.
	 * @return
	 *   The truncated result, in canonical form.
	 */
	private fun truncatedShift(self: AvailObject, shift: Int, bits: Int): A_Number
	{
		val words = self.integerSlotsCount()
		val topWord = (bits + 63) ushr 6
		val topBits = bits - 64 * (topWord - 1)
		val output = createUninitializedLongInteger(topWord + 1)
		for (j in 1..topWord)
		{
			output.setLongSlot(
				RAW_LONG_SLOTS_, j, extractShiftedLong(self, words, shift, j))
		}
		// Keep only the low topBits of the top word, and append a zero word so
		// the result is nonnegative.
		output.setLongSlot(
			RAW_LONG_SLOTS_,
			topWord,
			output.longSlot(RAW_LONG_SLOTS_, topWord) and (-1L ushr (64 - topBits)))
		output.setLongSlot(RAW_LONG_SLOTS_, topWord + 1, 0L)
		output.trimExcessInts()
		return output
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

	@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
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
		if (self.isLong) {
			return BigInteger.valueOf(self.extractLong)
		}
		val words = self.integerSlotsCount()
		val bytes = ByteArray(words shl 3)
		var b = 0
		for (i in words downTo 1) {
			val word = self.longSlot(RAW_LONG_SLOTS_, i)
			for (shift in 56 downTo 0 step 8) {
				bytes[b++] = (word shr shift).toByte()
			}
		}
		return BigInteger(bytes)
	}

	override fun o_IsNumericallyIntegral(self: AvailObject): Boolean = true

	override fun o_ComputeTypeTag(self: AvailObject): TypeTag =
		when (self.numericCompareToInteger(zero))
		{
			Order.MORE -> TypeTag.NATURAL_NUMBER_TAG
			Order.EQUAL -> TypeTag.WHOLE_NUMBER_TAG
			else -> TypeTag.INTEGER_TAG
		}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter) = when {
		self.isLong -> writer.write(self.extractLong)
		else -> writer.write(self.asBigInteger())
	}

	override fun o_WhichPowerOfTwo(self: AvailObject): Int
	{
		if (self.lessOrEqual(zero)) return -1
		var nonZeroIndex = 1
		var theLong: Long
		while (true)
		{
			theLong = self.longSlot(RAW_LONG_SLOTS_, nonZeroIndex)
			if (theLong != 0L) break
			nonZeroIndex++  // We ensured they can't all be zero.
		}
		if (theLong.countOneBits() > 1) return -1
		// Check that the rest of the longs are zero.
		if ((nonZeroIndex + 1 .. longCount(self))
				.any { self.longSlot(RAW_LONG_SLOTS_, it) != 0L })
		{
			return -1
		}
		return ((nonZeroIndex - 1) shl 6) + theLong.countTrailingZeroBits()
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
		): Int
		{
			val longs = self.integerSlotsCount()
			// The top int is redundant when the top long fits in an int.
			val topIntRedundant = self.longSlot(RAW_LONG_SLOTS_, longs).isInt
			return (longs shl 1) - if (topIntRedundant) 1 else 0
		}

		/**
		 * Answer the number of 64-bit `long` slots occupied by the given integer
		 * object.  The receiver must not be an indirection.
		 *
		 * @param self
		 *   The integer object.
		 * @return
		 *   The number of occupied long slots in the object.
		 */
		fun longCount(self: AvailObject): Int = self.integerSlotsCount()

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
				repeat(minDigits - digits.length) {
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
			do
			{
				previousDivisor = nextDivisor
				nextDivisor = cachedSquareOfQuintillion(++n)
			}
			while (nextDivisor.lessThan(magnitude))
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
					createSharedOfSize(1) {
						setLongSlot(RAW_LONG_SLOTS_, 1, anInteger.toLong())
					}.also {
						// Harmless race.
						smallIntegers[anInteger] = it
					}
				else -> createUninitializedInteger(1).apply {
					setLongSlot(RAW_LONG_SLOTS_, 1, anInteger.toLong())
				}
			}

		/**
		 * Overwrite the mutable [output] so that it holds the given [Int].
		 *
		 * @param anInteger
		 *   The [Int] to store.
		 * @param output
		 *   A mutable [AvailObject] with an [IntegerDescriptor] and an
		 *   [intCount] <= 2.
		 * @return
		 *   The [output], now holding [anInteger].
		 */
		private fun recycleInt(anInteger: Int, output: AvailObject): AvailObject
		{
			// The spare half holds the sign extension of the int, as equality
			// requires of an odd number of ints.
			output.setLongSlot(RAW_LONG_SLOTS_, 1, anInteger.toLong())
			output.descriptor = mutable
			return output
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
				createSharedOfSize(1) {
					setLongSlot(RAW_LONG_SLOTS_, 1, aLong)
				}.also {
					smallIntegers[aLong.toInt()] = it
				}
			aLong.toInt().toLong() -> createUninitializedInteger(1).apply {
				setLongSlot(RAW_LONG_SLOTS_, 1, aLong)
			}
			else -> createUninitializedInteger(2).apply {
				setLongSlot(RAW_LONG_SLOTS_, 1, aLong)
			}
		}

		/**
		 * Create an Avail boxed integer with the given [Long] as its value. One
		 * of the two recyclable values, which *must* have an
		 * [IntegerDescriptor], can be reused to hold the value if it's mutable
		 * and its [intCount] is <= 2.
		 */
		fun fromLongRecycling(
			aLong: Long,
			recyclable1: AvailObject,
			recyclable2: AvailObject,
			canDestroy: Boolean
		): AvailObject
		{
			if (!canDestroy) return fromLong(aLong)
			if (aLong in 0 until smallIntegerLimit) return fromLong(aLong)
			val output = when
			{
				(recyclable1.descriptor.isMutable
					&& intCount(recyclable1) <= 2
				) -> recyclable1
				(recyclable2.descriptor.isMutable
					&& intCount(recyclable1) <= 2
				) -> recyclable2
				else -> return fromLong(aLong)
			}
			// A whole long already holds the sign extension of a one-int value.
			output.setLongSlot(RAW_LONG_SLOTS_, 1, aLong)
			output.descriptor = mutable
			return output
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
		fun fromBigInteger(bigInteger: BigInteger): A_Number
		{
			val bytes = bigInteger.toByteArray()
			if (bytes.size <= 8) {
				return fromLong(bigInteger.toLong())
			}
			// Pad at the front, to whole words, with the sign of the value.
			val wordCount = (bytes.size + 7) ushr 3
			val padByte: Byte = if (bytes[0] < 0) -1 else 0
			val padded = ByteArray(wordCount shl 3) { padByte }
			bytes.copyInto(padded, destinationOffset = padded.size - bytes.size)
			val result = createUninitializedLongInteger(wordCount)
			// Word 1 is the least significant, so it comes from the last eight
			// bytes.
			for (i in 1..wordCount) {
				var word = 0L
				val start = (wordCount - i) shl 3
				for (k in start until start + 8) {
					word = (word shl 8) or (padded[k].toLong() and 0xFFL)
				}
				result.setLongSlot(RAW_LONG_SLOTS_, i, word)
			}
			result.trimExcessInts()
			return result
		}

		/**
		 * The exact `double` value 2^63, one more than [Long.MAX_VALUE].
		 * [Long.MAX_VALUE] itself converts to this value, so a `double` that
		 * fits in a `long` must be compared against this constant, not against
		 * the converted [Long] bounds.
		 */
		const val TWO_TO_THE_63 = 9.223372036854775808E18

		/**
		 * Answer an Avail integer that holds the truncation of the `double`
		 * argument, rounded towards zero.
		 *
		 * @param aDouble
		 *   The object whose truncation should be encoded as an Avail integer.
		 * @return
		 *   An Avail integer.
		 */
		fun truncatedFromDouble(aDouble: Double): A_Number
		{
			if (aDouble >= -TWO_TO_THE_63 && aDouble < TWO_TO_THE_63)
			{
				// Common case -- it fits in a long.
				return fromLong(aDouble.toLong())
			}
			val magnitude = fromLargeDoubleMagnitude(abs(aDouble))
			return when {
				aDouble < 0.0 -> zero.noFailMinusCanDestroy(magnitude, true)
				else -> magnitude
			}
		}

		/**
		 * Convert a finite, nonnegative `double` of at least 2^63 into an Avail
		 * integer.  Such a `double` is exactly a 53-bit mantissa times a power
		 * of two, so its 64-bit words are formed by shifting the mantissa into
		 * place.  No rounding or 32-bit digits are involved.
		 *
		 * @param magnitude
		 *   The finite, nonnegative `double`, at least 2^63.
		 * @return
		 *   An Avail integer equal to the `double`'s integral value.
		 */
		fun fromLargeDoubleMagnitude(magnitude: Double): A_Number
		{
			assert(magnitude.isFinite() && magnitude >= TWO_TO_THE_63)
			val bits = doubleToRawLongBits(magnitude)
			// Restore the implicit leading bit above the 52 stored fraction bits.
			val mantissa = (bits and ((1L shl 52) - 1)) or (1L shl 52)
			val exponent = getExponent(magnitude)
			// The mantissa's lowest bit has weight 2^shift, and shift >= 11.
			val shift = exponent - 52
			// Room for the top bit and a sign bit, rounded up to whole words.
			val wordCount = exponent + 65 shr 6
			val out = createUninitializedLongInteger(wordCount)
			for (i in 1..wordCount) {
				out.setLongSlot(RAW_LONG_SLOTS_, i, 0L)
			}
			val low = shift ushr 6
			val bitOffset = shift and 63
			out.setLongSlot(RAW_LONG_SLOTS_, low + 1, mantissa shl bitOffset)
			val high = if (bitOffset == 0) 0L else mantissa ushr (64 - bitOffset)
			if (high != 0L) {
				out.setLongSlot(RAW_LONG_SLOTS_, low + 2, high)
			}
			out.trimExcessInts()
			return out
		}

		/** The [CheckedMethod] for [fromInt]. */
		val fromIntMethod = staticMethod(::fromInt, int)


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
			// Extract and scale the top two longs.  Together they hold at least
			// 64 correct upper bits, which is more than a double can represent.
			val words = self.integerSlotsCount()
			var d = scalb(
				self.longSlot(RAW_LONG_SLOTS_, words).toDouble(),
				64 * (words - 1) - exponentBias)
			if (words > 1) {
				// Don't sign-extend this component, since our initial estimate
				// can only be too low, never too high.  Adding in the lower
				// word should increase the number, even if the first was
				// negative.
				val next = self.longSlot(RAW_LONG_SLOTS_, words - 1)
					.toULong().toDouble()
				d += scalb(next, 64 * (words - 2) - exponentBias)
			}
			return d
		}

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

		/** The initialization value for computing the hash of an integer. */
		private const val initialHashValue = 0x13592884


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
		fun computeHashOfLong(aLong: Long): Int =
			combineWord(aLong, initialHashValue)

		/**
		 * Mix one 64-bit word into a running hash, a whole word at a time, using
		 * [combine4] with [integerHashSalt].  The salt is used only for integers,
		 * so integer hashes don't systematically collide with other types.
		 *
		 * @param aLong
		 *   The word to mix in.
		 * @param accumulator
		 *   The running hash.
		 * @return
		 *   The updated running hash.
		 */
		private fun combineWord(aLong: Long, accumulator: Int): Int =
			combine4(
				aLong.toInt(),
				(aLong ushr 32).toInt(),
				accumulator,
				integerHashSalt)

		/** The salt that [combineWord] uses, reserved for integer hashing. */
		private const val integerHashSalt = -0x45AC471B

		/**
		 * Compute the hash of the given Avail integer object, folding its words
		 * from the top.  A value in long range has a single word, so it hashes
		 * as [computeHashOfLong] does.
		 *
		 * @param anIntegerObject
		 *   An Avail integer to be hashed.
		 * @return
		 *   The hash of the given Avail integer.
		 */
		private fun computeHashOfIntegerObject(
			anIntegerObject: AvailObject
		): Int {
			var output = initialHashValue
			for (i in anIntegerObject.integerSlotsCount() downTo 1)
			{
				output = combineWord(
					anIntegerObject.longSlot(RAW_LONG_SLOTS_, i), output)
			}
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
			mutable.create(checkLongCount(size + 1 shr 1))

		/**
		 * Create a mutable Avail integer with the specified number of
		 * uninitialized [Long] slots.
		 *
		 * @param longCount
		 *   The number of [Long] slots to have in the result.
		 * @return
		 *   An uninitialized, mutable integer.
		 */
		fun createUninitializedLongInteger(longCount: Int): AvailObject =
			mutable.create(checkLongCount(longCount))

		/**
		 * The largest number of [Long] words that an integer may occupy.  It is
		 * below the JVM's array length limit, and it keeps every bit position
		 * within a [Long]: such an integer has fewer than 2^37 bits.
		 */
		const val MAX_LONG_COUNT = Int.MAX_VALUE - 8

		/**
		 * Check that an integer may occupy the given number of [Long] words.
		 *
		 * @param longCount
		 *   The proposed number of [Long] words.
		 * @return
		 *   The same number, for convenience.
		 * @throws AvailSystemLimitException
		 *   If the number of words exceeds [MAX_LONG_COUNT].
		 */
		fun checkLongCount(longCount: Int): Int
		{
			if (longCount > MAX_LONG_COUNT)
			{
				throw AvailSystemLimitException(
					"Integer would need $longCount longs, beyond the limit of " +
						"$MAX_LONG_COUNT")
			}
			return longCount
		}

		/**
		 * Create an integer object with the given number of [Long] slots,
		 * initialized in little-Endian order with values produced by the
		 * [generator].
		 *
		 * @param longCount
		 *   The number of [Long] slots to allocate in the boxed integer.
		 * @param generator
		 *   A generator to provide [Long]s to store, starting with the least
		 *   significant one first.  The generator is passed the current long
		 *   slot's index.
		 * @return
		 *   The new boxed integer.
		 */
		fun generateIntegerFromLimbs(
			longCount: Int,
			generator: (Int) -> Long
		): AvailObject = mutable.create(checkLongCount(longCount)) {
			for (i in 1..longCount)
			{
				setLongSlot(RAW_LONG_SLOTS_, i, generator(i))
			}
		}

		/**
		 * Answer an iterator over the [Long] slots of an Avail boxed integer,
		 * starting with the least significant [Long].
		 */
		fun limbIterator(
			integer: AvailObject
		): Iterator<Long> = object: Iterator<Long>
		{
			val limit = longCount(integer)

			var current = 1

			override fun hasNext(): Boolean = current <= limit

			override fun next(): Long =
				integer.longSlot(RAW_LONG_SLOTS_, current++)
		}

		/** The mutable [IntegerDescriptor]. */
		private val mutable = IntegerDescriptor(Mutability.MUTABLE)

		/** The immutable [IntegerDescriptor]. */
		private val immutable = IntegerDescriptor(Mutability.IMMUTABLE)

		/** The shared [IntegerDescriptor]. */
		private val shared = IntegerDescriptor(Mutability.SHARED)

		/**
		 * Values below this limit can be looked up in [smallIntegers], and
		 * their hashes can also be looked up in [hashesOfSmallIntegers].  This
		 * value MUST be at least 256.
		 */
		const val smallIntegerLimit = 16384

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
					array[i] = createSharedOfSize(1) {
						setLongSlot(RAW_LONG_SLOTS_, 1, i.toLong())
					}
				}
			}


		/**
		 * Create a mutable integer with the given number of int slots, run the
		 * setup function on it, and make it shared.
		 *
		 * @param size
		 *   The number of int slots to reserve.
		 * @param setup
		 *   How to initialize the object while it's mutable.
		 * @return
		 *   The initialized [AvailObject], made shared.
		 */
		private inline fun createSharedOfSize(
			size: Int,
			setup: AvailObject.()->Unit
		): AvailObject =
			createUninitializedInteger(size).apply(setup).makeShared()

		/**
		 * An array of size [smallIntegerLimit] of [Int]s, corresponding to the
		 * hashes of the integers at the corresponding indices.
		 */
		private val hashesOfSmallIntegers =
			IntArray(smallIntegerLimit) { computeHashOfLong(it.toLong()) }

		/** An Avail integer representing zero (0). */
		val zero: AvailObject = smallIntegers[0]!!

		/** An Avail integer representing one (1). */
		val one: AvailObject = smallIntegers[1]!!

		/** An Avail integer representing two (2). */
		val two: AvailObject = smallIntegers[2]!!

		/** The Avail integer negative one (-1). */
		val negativeOne: AvailObject = createSharedOfSize(1) {
			setLongSlot(RAW_LONG_SLOTS_, 1, -1L)
		}

		/**
		 * One (U.S.) quintillion, which is 10^18.  This is the largest power of
		 * ten representable as a signed long.
		 */
		private const val quintillionLong = 1_000_000_000_000_000_000L

		/**
		 * One (U.S.) quintillion, which is 10^18.  This is the largest power of
		 * ten for which [isLong] returns true.
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
		fun cachedSquareOfQuintillion(n: Int): A_Number
		{
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

	override fun mutable() = mutable

	override fun immutable() = immutable

	override fun shared() = shared

	/**
	 * A stack of dead, mutable boxed integers that may be reused to hold a new
	 * [Int] value.  The entries live in a circular array, so a push past
	 * [capacity] overwrites the oldest entry.  A popped slot is nulled, so a
	 * null top slot means the stack is empty.
	 *
	 * @property capacity
	 *   The number of slots.  Must be a power of two.
	 */
	class RecyclePool(private val capacity: Int = 128)
	{
		init
		{
			assert(capacity > 0 && capacity and (capacity - 1) == 0)
		}

		/** The mask that wraps an index into [slots]. */
		private val mask = capacity - 1

		/** The circular storage for the recycled integers. */
		private val slots = arrayOfNulls<AvailObject>(capacity)

		/** The index of the top entry.  The top slot is null when empty. */
		private var top = 0

		/**
		 * Push [anObject] for later reuse, if it's mutable and has room for any
		 * [Int] value.  The caller must guarantee that nothing else references
		 * [anObject].
		 *
		 * @param anObject
		 *   A boxed integer that has lost its last reference.
		 */
		@ReferencedInGeneratedCode
		fun recycleIfMutable(anObject: AvailObject)
		{
			if (anObject.descriptor.isMutable && intCount(anObject) <= 2)
			{
				top = (top + 1) and mask
				slots[top] = anObject
			}
		}

		/**
		 * Answer an [AvailObject] holding [anInteger], reusing the most recently
		 * recycled one if there is one.
		 *
		 * @param anInteger
		 *   The [Int] to box.
		 * @return
		 *   An [AvailObject].
		 */
		@ReferencedInGeneratedCode
		fun boxInt(anInteger: Int): AvailObject
		{
			if (anInteger in 0 until smallIntegerLimit)
			{
				return fromInt(anInteger)
			}
			val recycled = slots[top] ?: return fromInt(anInteger)
			slots[top] = null
			top = (top - 1) and mask
			return recycleInt(anInteger, recycled)
		}
	}
}
