/*
 * A_Number.kt
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

import avail.descriptor.numbers.AbstractNumberDescriptor.Order
import avail.descriptor.numbers.AbstractNumberDescriptor.Sign
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_BasicObject.Companion.dispatch
import avail.descriptor.representation.AvailObject
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.int32
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.int64
import avail.exceptions.ArithmeticException
import avail.interpreter.primitive.numbers.P_LessOrEqual
import avail.optimizer.jvm.CheckedMethod
import avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import avail.optimizer.jvm.ReferencedInGeneratedCode
import java.math.BigInteger

/**
 * `A_Number` is an interface that specifies the number-specific operations that
 * an [AvailObject] must implement.  It's a sub-interface of [A_BasicObject],
 * the interface that defines the behavior that all AvailObjects are required to
 * support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_Number : A_BasicObject
{
	companion object
	{
		/**
		 * Determine if the receiver is an Avail integer equivalent to the
		 * specified Kotlin [Int].  Note that a non-integer should simply answer
		 * false, not fail.  This operation was placed in A_Number for
		 * organizational reasons, not type restriction.
		 *
		 * @param theInt
		 *   The Java int to compare against.
		 * @return
		 *   Whether the receiver represents that integer.
		 */
		fun A_Number.equalsInt(theInt: Int): Boolean =
			dispatch { o_EqualsInt(it, theInt) }

		/**
		 * Subtract the receiver from the given [integer][IntegerDescriptor],
		 * destroying one or the other if it's mutable and canDestroy is true.
		 *
		 * @param anInteger
		 *   The integer to subtract from.
		 * @param canDestroy
		 *   Whether a mutable receiver or argument may be destroyed and/or
		 *   recycled to hold the difference.
		 * @return
		 *   The difference, possibly recycling one of the inputs if canDestroy
		 *   is true.
		 */
		fun A_Number.subtractFromIntegerCanDestroy(
			anInteger: AvailObject,
			canDestroy: Boolean
		): A_Number = dispatch {
			o_SubtractFromIntegerCanDestroy(it, anInteger, canDestroy)
		}

		/**
		 * Multiply the receiver and the argument `aNumber` and answer the
		 * [result][AvailObject].
		 *
		 * Implementations may double-dispatch to [multiplyByIntegerCanDestroy]
		 * or [multiplyByInfinityCanDestroy], where actual implementations of
		 * the multiplication operation should reside.
		 *
		 * @param aNumber
		 *   An integral numeric.
		 * @param canDestroy
		 *   `true` if the operation may modify either operand, `false`
		 *   otherwise.
		 * @return
		 *   The [result][AvailObject] of multiplying the operands.
		 * @see IntegerDescriptor
		 * @see InfinityDescriptor
		 */
		fun A_Number.timesCanDestroy(
			aNumber: A_Number,
			canDestroy: Boolean
		): A_Number = dispatch {
			o_TimesCanDestroy(it, aNumber, canDestroy)
		}

		/**
		 * Normalize the integer to have the minimum number of base 2^32 digits.
		 */
		fun A_Number.trimExcessInts() = dispatch { o_TrimExcessInts(it) }

		/**
		 * Subtract the receiver from the [infinity][InfinityDescriptor] with
		 * the specified [sign][Sign], destroying one or the other if it's
		 * mutable and canDestroy is true.
		 *
		 * @param sign
		 *   The sign of the infinity to subtract from.
		 * @param canDestroy
		 *   Whether a mutable receiver or argument may be destroyed and/or
		 *   recycled to hold the difference.
		 * @return
		 *   The difference, possibly recycling one of the inputs if canDestroy
		 *   is true.
		 */
		fun A_Number.subtractFromInfinityCanDestroy(
			sign: Sign,
			canDestroy: Boolean
		): A_Number =
			dispatch { o_SubtractFromInfinityCanDestroy(it, sign, canDestroy) }

		/**
		 * Subtract the receiver from the given [float][FloatDescriptor],
		 * destroying one or the other if it's mutable and canDestroy is true.
		 * Because of the requirement that the argument be a float rather than
		 * an arbitrary [A_Number], this is usually only used for
		 * double-dispatching.
		 *
		 * @param floatObject
		 *   The float to subtract the receiver from.
		 * @param canDestroy
		 *   Whether a mutable receiver or argument may be destroyed and/or
		 *   recycled to hold the difference.
		 * @return
		 *   The difference, possibly recycling one of the inputs if canDestroy
		 *   is true.
		 */
		fun A_Number.subtractFromFloatCanDestroy(
			floatObject: A_Number,
			canDestroy: Boolean
		): A_Number = dispatch {
			o_SubtractFromFloatCanDestroy(it, floatObject, canDestroy)
		}

		/**
		 * Subtract the receiver from the given [double][DoubleDescriptor],
		 * destroying one or the other if it's mutable and canDestroy is true.
		 * Because of the requirement that the argument be a double rather than
		 * an arbitrary [A_Number], this is usually only used for
		 * double-dispatching.
		 *
		 * @param doubleObject
		 *   The double to subtract the receiver from.
		 * @param canDestroy
		 *   Whether a mutable receiver or argument may be destroyed and/or
		 *   recycled to hold the difference.
		 * @return
		 *   The difference, possibly recycling one of the inputs if canDestroy
		 *   is true.
		 */
		fun A_Number.subtractFromDoubleCanDestroy(
			doubleObject: A_Number,
			canDestroy: Boolean
		): A_Number = dispatch {
			o_SubtractFromDoubleCanDestroy(it, doubleObject, canDestroy)
		}

		/**
		 * Extract an unsigned base 2^32 digit from the integer.  The index must
		 * be in range for the integer's representation.
		 *
		 * @param index
		 *   The one-based, little-endian index of the digit to extract.  It
		 *   must be between 1 and the number of digits present.
		 * @return
		 *   The unsigned base 2^32 digit as a signed [Long] to avoid
		 *   misinterpreting the sign.
		 */
		fun A_Number.rawUnsignedIntegerAt(index: Int): Long =
			dispatch { o_RawUnsignedIntegerAt(it, index) }

		/**
		 * Replace an unsigned base 2^32 digit of the integer.  The index must
		 * be in range for the integer's representation, and the receiver must
		 * be mutable.
		 *
		 * @param index
		 *   The one-based, little-endian index of the digit to replace.  It
		 *   must be between 1 and the number of digits present.
		 * @param value
		 *   The replacement base 2^32 digit as an [Int].  This does the same
		 *   thing as [rawSignedIntegerAtPut].
		 */
		fun A_Number.rawUnsignedIntegerAtPut(index: Int, value: Int) =
			dispatch { o_RawUnsignedIntegerAtPut(it, index, value) }

		/**
		 * Replace a (signed) base 2^32 digit of the integer.  The index must be
		 * in range for the integer's representation, and the receiver must be
		 * mutable.
		 *
		 * @param index
		 *   The one-based, little-endian index of the digit to replace.  It
		 *   must be between 1 and the number of digits present.
		 * @param value
		 *   The replacement base 2^32 digit as a signed [Int].
		 */
		fun A_Number.rawSignedIntegerAtPut(index: Int, value: Int) =
			dispatch { o_RawSignedIntegerAtPut(it, index, value) }

		/**
		 * Extract a (signed) base 2^32 digit from the integer.  The index must
		 * be in range for the integer's representation.
		 *
		 * @param index
		 *   The one-based, little-endian index of the digit to extract.  It
		 *   must be between 1 and the number of digits present.
		 * @return
		 *   The base 2^32 digit as a signed [Int].
		 */
		fun A_Number.rawSignedIntegerAt(index: Int): Int =
			dispatch { o_RawSignedIntegerAt(it, index) }

		/**
		 * Add the receiver and the argument `aNumber` and answer the
		 * [result][AvailObject].
		 *
		 * Implementations may double-dispatch to [addToIntegerCanDestroy] or
		 * [addToInfinityCanDestroy], where actual implementations of the
		 * addition operation should reside.
		 *
		 * @param aNumber
		 *   An integral numeric.
		 * @param canDestroy
		 *   `true` if the operation may modify either operand, `false`
		 *   otherwise.
		 * @return
		 *   The [result][AvailObject] of adding the operands.
		 * @see IntegerDescriptor
		 * @see InfinityDescriptor
		 */
		fun A_Number.plusCanDestroy(
			aNumber: A_Number,
			canDestroy: Boolean
		): A_Number = dispatch { o_PlusCanDestroy(it, aNumber, canDestroy) }

		/**
		 * This produces the same value as [numericCompare], but the argument is
		 * known to be an [integer][IntegerDescriptor].
		 *
		 * @param anInteger
		 *   The `integer` to numerically compare against.
		 * @return
		 *   How the receiver compares to the specified integer.
		 */
		fun A_Number.numericCompareToInteger(anInteger: AvailObject): Order =
			dispatch { o_NumericCompareToInteger(it, anInteger) }

		/**
		 * This produces the same value as [numericCompare], but the argument is
		 * an unboxed `double` value.
		 *
		 * @param aDouble
		 *   The [Double] to numerically compare against.
		 * @return
		 *   How the receiver compares to the specified double.
		 */
		fun A_Number.numericCompareToDouble(aDouble: Double): Order =
			dispatch { o_NumericCompareToDouble(it, aDouble) }

		/**
		 * This produces the same value as [numericCompare], but the argument is
		 * known to be an [integral&#32;infinity][InfinityDescriptor] whose
		 * [Sign] is provided.
		 *
		 * @param sign
		 *   The sign of the integral infinity to compare against.
		 * @return
		 *   How the receiver compares to the specified infinity.
		 */
		fun A_Number.numericCompareToInfinity(sign: Sign): Order =
			dispatch { o_NumericCompareToInfinity(it, sign) }

		/**
		 * Answer an ordering between the receiver and the argument.  This
		 * compares the underlying real numeric values of the two [A_Number]s,
		 * which does not necessarily agree with the [equals] semantics.  In
		 * particular, under numerical ordering, 5 = 5.0 = 5.0f, and 0.0/0.0 is
		 * incomparable to every number, even itself.  Under ordinary equality
		 * (the [equals] method), an integer never equals a float, and neither
		 * ever equals a double.  However, 0.0/0.0 is equal to 0.0/0.0, since
		 * they have the same kind (double) and the same bit pattern.  Note that
		 * [hash] agrees with general equality, not the numeric ordering.
		 *
		 * The numeric order is not directly exposed to Avail, but it can be
		 * reconstructed by computing the [P_LessOrEqual] predicate on two
		 * values and also on the two values interchanged.  If one but not the
		 * other is true, the order is either [Order.LESS] or [Order.MORE].  If
		 * both are true, the values are [Order.EQUAL], and if neither is true
		 * then the values are [Order.INCOMPARABLE], which is only the case if
		 * one or both values are float or double not-a-numbers (easily produced
		 * via 0.0/0.0).
		 *
		 * @param another
		 *   The value to numerically compare the receiver to.
		 * @return
		 *   The numeric [Order] between the receiver and the argument.
		 */
		fun A_Number.numericCompare(another: A_Number): Order =
			dispatch { o_NumericCompare(it, another) }

		/**
		 * Multiply the receiver and the argument `aNumber` and answer the
		 * [result][AvailObject]. The operation is not allowed to fail, so the
		 * caller must ensure that the arguments are valid, i.e. not
		 * [zero][IntegerDescriptor.zero] and [infinity][InfinityDescriptor].
		 *
		 * @param aNumber
		 *   An integral numeric.
		 * @param canDestroy
		 *   `true` if the operation may modify either operand, `false`
		 *   otherwise.
		 * @return
		 *   The [result][AvailObject] of adding the operands.
		 * @see IntegerDescriptor
		 * @see InfinityDescriptor
		 */
		fun A_Number.noFailTimesCanDestroy(
			aNumber: A_Number,
			canDestroy: Boolean
		): A_Number =
			try
			{
				dispatch { o_TimesCanDestroy(it, aNumber, canDestroy) }
			}
			catch (e: ArithmeticException)
			{
				// This had better not happen, otherwise the caller has violated
				// the intention of this method.
				AvailObject.error("noFailTimesCanDestroy failed!")
			}

		/**
		 * Add the receiver and the argument `aNumber` and answer the
		 * [result][AvailObject]. The operation is not allowed to fail, so the
		 * caller must ensure that the arguments are valid, i.e. not
		 * [infinities][InfinityDescriptor] of unlike sign.
		 *
		 * @param aNumber
		 *   An integral numeric.
		 * @param canDestroy
		 *   `true` if the operation may modify either operand, `false`
		 *   otherwise.
		 * @return
		 *   The [result][AvailObject] of adding the operands.
		 * @see IntegerDescriptor
		 * @see InfinityDescriptor
		 */
		fun A_Number.noFailPlusCanDestroy(
			aNumber: A_Number,
			canDestroy: Boolean
		): A_Number =
			try
			{
				dispatch { o_PlusCanDestroy(it, aNumber, canDestroy) }
			}
			catch (e: ArithmeticException)
			{
				// This had better not happen, otherwise the caller has violated
				// the intention of this method.
				AvailObject.error("noFailPlusCanDestroy failed!")
			}

		/**
		 * Difference the receiver and the argument `aNumber` and answer the
		 * [result][AvailObject]. The operation is not allowed to fail, so the
		 * caller must ensure that the arguments are valid, i.e. not
		 * [infinities][InfinityDescriptor] of like sign.
		 *
		 * @param aNumber
		 *   An integral numeric.
		 * @param canDestroy
		 *   `true` if the operation may modify either operand, `false`
		 *   otherwise.
		 * @return
		 *   The [result][AvailObject] of differencing the operands.
		 * @see IntegerDescriptor
		 * @see InfinityDescriptor
		 */
		fun A_Number.noFailMinusCanDestroy(
			aNumber: A_Number,
			canDestroy: Boolean
		): A_Number =
			try
			{
				dispatch { o_MinusCanDestroy(it, aNumber, canDestroy) }
			}
			catch (e: ArithmeticException)
			{
				// This had better not happen, otherwise the caller has violated
				// the intention of this method.
				AvailObject.error("noFailMinusCanDestroy failed!")
			}

		/**
		 * Divide the receiver by the argument `aNumber` and answer the
		 * [result][AvailObject]. The operation is not allowed to fail, so the
		 * caller must ensure that the arguments are valid, i.e. the divisor is
		 * not [zero][IntegerDescriptor.zero].
		 *
		 * @param aNumber
		 *   An integral numeric.
		 * @param canDestroy
		 *   `true` if the operation may modify either operand, `false`
		 *   otherwise.
		 * @return
		 *   The [result][AvailObject] of dividing the operands.
		 * @see IntegerDescriptor
		 * @see InfinityDescriptor
		 */
		fun A_Number.noFailDivideCanDestroy(
			aNumber: A_Number,
			canDestroy: Boolean
		): A_Number =
			try
			{
				dispatch { o_DivideCanDestroy(it, aNumber, canDestroy) }
			}
			catch (e: ArithmeticException)
			{
				// This had better not happen, otherwise the caller has violated
				// the intention of this method.
				AvailObject.error("noFailDivideCanDestroy failed!")
			}

		/**
		 * Multiply the receiver by the given [integer][IntegerDescriptor],
		 * destroying one or the other if it's mutable and canDestroy is true.
		 * Because of the requirement that the argument be an integer rather
		 * than an arbitrary [A_Number], this is usually only used for
		 * double-dispatching.
		 *
		 * @param anInteger
		 *   The integer to multiply the receiver by.
		 * @param canDestroy
		 *   Whether a mutable receiver or argument may be destroyed and/or
		 *   recycled to hold the product.
		 * @return
		 *   The product, possibly recycling one of the inputs if canDestroy is
		 *   true.
		 */
		fun A_Number.multiplyByIntegerCanDestroy(
			anInteger: AvailObject,
			canDestroy: Boolean
		): A_Number = dispatch {
			o_MultiplyByIntegerCanDestroy(it, anInteger, canDestroy)
		}

		/**
		 * Multiply the receiver by the [infinity][InfinityDescriptor] with the
		 * given [sign][Sign], potentially destroying the receiver if it's
		 * mutable and canDestroy is true.
		 *
		 * @param sign
		 *   The sign of the infinity by which to multiply the receiver.
		 * @param canDestroy
		 *   Whether a mutable receiver may be destroyed and/or recycled to hold
		 *   the product.
		 * @return
		 *   The product, possibly recycling the receiver if canDestroy is true.
		 */
		fun A_Number.multiplyByInfinityCanDestroy(
			sign: Sign,
			canDestroy: Boolean
		): A_Number =
			dispatch { o_MultiplyByInfinityCanDestroy(it, sign, canDestroy) }

		/**
		 * Multiply the receiver by the given [float][FloatDescriptor],
		 * destroying one or the other if it's mutable and `canDestroy` is true.
		 * Because of the requirement that the argument be a float rather than
		 * an arbitrary [A_Number], this is usually only used for
		 * double-dispatching.
		 *
		 * @param floatObject
		 *   The float to multiply the receiver by.
		 * @param canDestroy
		 *   Whether a mutable receiver or argument may be destroyed and/or
		 *   recycled to hold the product.
		 * @return
		 *   The product, possibly recycling one of the inputs if canDestroy is
		 *   true.
		 */
		fun A_Number.multiplyByFloatCanDestroy(
			floatObject: A_Number,
			canDestroy: Boolean
		): A_Number = dispatch {
			o_MultiplyByFloatCanDestroy(it, floatObject, canDestroy)
		}

		/**
		 * Multiply the receiver by the given [double][DoubleDescriptor],
		 * destroying one or the other if it's mutable and canDestroy is true.
		 * Because of the requirement that the argument be a double rather than
		 * an arbitrary [A_Number], this is usually only used for
		 * double-dispatching.
		 *
		 * @param doubleObject
		 *   The double to multiply the receiver by.
		 * @param canDestroy
		 *   Whether a mutable receiver or argument may be destroyed and/or
		 *   recycled to hold the product.
		 * @return
		 *   The product, possibly recycling one of the inputs if canDestroy is
		 *   true.
		 */
		fun A_Number.multiplyByDoubleCanDestroy(
			doubleObject: A_Number,
			canDestroy: Boolean
		): A_Number = dispatch {
			o_MultiplyByDoubleCanDestroy(it, doubleObject, canDestroy)
		}

		/**
		 * Subtract the argument `aNumber` from a receiver and answer the
		 * [result][AvailObject].
		 *
		 * Implementations may double-dispatch to
		 * [subtractFromIntegerCanDestroy] or [subtractFromInfinityCanDestroy],
		 * where actual implementations of the subtraction operation should
		 * reside.
		 *
		 * @param aNumber
		 *   An integral numeric.
		 * @param canDestroy
		 *   `true` if the operation may modify either operand, `false`
		 *   otherwise.
		 * @return
		 *   The [result][AvailObject] of differencing the operands.
		 * @see IntegerDescriptor
		 * @see InfinityDescriptor
		 */
		fun A_Number.minusCanDestroy(
			aNumber: A_Number,
			canDestroy: Boolean
		): A_Number = dispatch { o_MinusCanDestroy(it, aNumber, canDestroy) }

		/**
		 * Answer whether the receiver is numerically less than the argument.
		 *
		 * @param another
		 *   A [numeric&#32;object][AbstractNumberDescriptor].
		 * @return
		 *   Whether the receiver is strictly less than the argument.
		 */
		fun A_Number.lessThan(another: A_Number): Boolean =
			numericCompare(another).isLess()

		/**
		 * Answer whether the receiver is numerically less than or equivalent to
		 * the argument.
		 *
		 * @param another
		 *   A [numeric&#32;object][AbstractNumberDescriptor].
		 * @return
		 *   Whether the receiver is less than or equivalent to the argument.
		 */
		fun A_Number.lessOrEqual(another: A_Number): Boolean =
			numericCompare(another).isLessOrEqual()

		/**
		 * Answer whether this integral [infinity][InfinityDescriptor] is
		 * positive.
		 *
		 * @return
		 *   `true` if the receiver is positive integral infinity, or `false` if
		 *   the receiver is negative integral infinity. No other values are
		 *   permitted.
		 */
		val A_Number.isPositive: Boolean get() = dispatch { o_IsPositive(it) }

		/**
		 * Answer whether this value is an integer in the [int32] range.
		 */
		@get:ReferencedInGeneratedCode
		val A_Number.isInt: Boolean get() = dispatch { o_IsInt(it) }

		/**
		 * Answer whether this value is an integer in the [int64] range.
		 */
		val A_Number.isLong get() = dispatch { o_IsLong(it) }

		/**
		 * Answer whether this value is an integer in the range `[0..65535]`.
		 */
		val A_Number.isUnsignedShort get() = dispatch { o_IsUnsignedShort(it) }

		/**
		 * Answer whether this value is a [single][FloatDescriptor]-precision
		 * floating point number.
		 */
		val A_Number.isFloat get() = dispatch { o_IsFloat(it) }

		/**
		 * Answer whether this value is a [double][DoubleDescriptor]-precision
		 * floating point number.
		 */
		val A_Number.isDouble get() = dispatch { o_IsDouble(it) }

		/**
		 * Answer whether this value is an integer in the range `[-128..127]`.
		 */
		val A_Number.isSignedByte get() = dispatch { o_IsSignedByte(it) }

		/**
		 * Answer whether this value is an integer in the range
		 * `[-32768..32767]`.
		 */
		val A_Number.isSignedShort get() = dispatch { o_IsSignedShort(it) }

		/**
		 * Answer whether this number is numerically equal to some finite
		 * integer.
		 *
		 * @return
		 *   A boolean indicating finiteness and a fractional part of zero.
		 */
		val A_Number.isNumericallyIntegral: Boolean
			get() = dispatch { o_IsNumericallyIntegral(it) }

		/**
		 * Extract an unsigned nybble from the [receiver][AvailObject]. Return
		 * it as a Java [Byte].
		 *
		 * @return
		 *   A [Byte] in the range `[0..15]`.
		 */
		val A_Number.extractNybble: Byte
			get() = dispatch { o_ExtractNybble(it) }

		/**
		 * Extract a 64-bit signed Java [Long] from the [receiver][AvailObject].
		 *
		 * @return
		 *   A 64-bit signed Java [Long].
		 */
		val A_Number.extractLong: Long get() = dispatch { o_ExtractLong(it) }

		/**
		 * Extract a 32-bit signed Kotlin [Int] from the
		 * [receiver][AvailObject].
		 *
		 * @return
		 *   A 32-bit signed Kotlin [Int].
		 */
		val A_Number.extractInt: Int get() = dispatch { o_ExtractInt(it) }

		/**
		 * Extract a Java float from the [receiver][AvailObject].
		 *
		 * @return
		 *   A Java `float`.
		 */
		val A_Number.extractFloat: Float get() = dispatch { o_ExtractFloat(it) }

		/**
		 * Extract a Kotlin [Double] from the [receiver][AvailObject].
		 *
		 * @return
		 *   A Kotlin [Double].
		 */
		val A_Number.extractDouble: Double
			get() = dispatch { o_ExtractDouble(it) }

		/**
		 * Extract an unsigned short from the [receiver][AvailObject]. Return it
		 * in a Kotlin [Int] to avoid sign bit reinterpretation.
		 *
		 * @return
		 *   An [Int] in the range `[0..65535]`.
		 */
		val A_Number.extractUnsignedShort: Int
			get() = dispatch { o_ExtractUnsignedShort(it) }

		/**
		 * Extract an unsigned byte from the [receiver][AvailObject].
		 * Return it in a Java [Short] to avoid sign bit reinterpretation.
		 *
		 * @return
		 *   A [Short] in the range `[0..255]`.
		 */
		val A_Number.extractUnsignedByte: Short
			get() = dispatch { o_ExtractUnsignedByte(it) }

		/**
		 * Extract a signed short from the [receiver][AvailObject].
		 *
		 * @return
		 *   A [Short], which has the range `[-32768..32767]`.
		 */
		val A_Number.extractSignedShort: Short
			get() = dispatch { o_ExtractSignedShort(it) }

		/**
		 * Extract a signed byte from the [receiver][AvailObject].
		 *
		 * @return
		 *   A Java [Byte], which has the range `[-128..127]`.
		 */
		val A_Number.extractSignedByte: Byte
			get() = dispatch { o_ExtractSignedByte(it) }

		/**
		 * Divide the receiver by the argument `aNumber` and answer the
		 * [result][AvailObject].
		 *
		 * Implementations may double-dispatch to [divideIntoIntegerCanDestroy]
		 * or [divideIntoInfinityCanDestroy] (or others), where actual
		 * implementations of the division operation should reside.
		 *
		 * @param aNumber
		 *   An integral numeric.
		 * @param canDestroy
		 *   `true` if the operation may modify either operand, `false`
		 *   otherwise.
		 * @return
		 *   The [result][AvailObject] of dividing the operands.
		 * @see IntegerDescriptor
		 * @see InfinityDescriptor
		 */
		fun A_Number.divideCanDestroy(
			aNumber: A_Number,
			canDestroy: Boolean
		): A_Number = dispatch {
			o_DivideCanDestroy(it, aNumber, canDestroy)
		}

		/**
		 * Divide the Avail [integer][IntegerDescriptor] argument by the
		 * receiver, destroying one or the other if it's mutable and canDestroy
		 * is true. Because of the requirement that the argument be an integer
		 * rather than an arbitrary [A_Number], this is usually only used for
		 * double-dispatching.
		 *
		 * @param anInteger
		 *   The integer to divide by the receiver.
		 * @param canDestroy
		 *   Whether a mutable receiver or argument may be destroyed and/or
		 *   recycled to hold the quotient.
		 * @return
		 *   The quotient, possibly recycling one of the inputs if canDestroy is
		 *   true.
		 */
		fun A_Number.divideIntoIntegerCanDestroy(
			anInteger: AvailObject,
			canDestroy: Boolean
		): A_Number = dispatch {
			o_DivideIntoIntegerCanDestroy(it, anInteger, canDestroy)
		}

		/**
		 * Divide the [infinity][InfinityDescriptor] having the specified [Sign]
		 * by the receiver, destroying one or the other if it's mutable and
		 * canDestroy is true.  Because of the requirement that the argument be
		 * an infinity rather than an arbitrary [A_Number], this is usually only
		 * used for double-dispatching.
		 *
		 * @param sign
		 *   The [Sign] of the infinity to divide by the receiver.
		 * @param canDestroy
		 *   Whether a mutable receiver or argument may be destroyed and/or
		 *   recycled to hold the quotient.
		 * @return
		 *   The quotient, possibly recycling one of the inputs if canDestroy is
		 *   true.
		 */
		fun A_Number.divideIntoInfinityCanDestroy(
			sign: Sign,
			canDestroy: Boolean
		): A_Number =
			dispatch { o_DivideIntoInfinityCanDestroy(it, sign, canDestroy) }

		/**
		 * Divide the [float][FloatDescriptor] argument by the receiver,
		 * destroying one or the other if it's mutable and canDestroy is true.
		 * Because of the requirement that the argument be a float rather than
		 * an arbitrary [A_Number], this is usually only used for
		 * double-dispatching.
		 *
		 * @param floatObject
		 *   The float to divide by the receiver.
		 * @param canDestroy
		 *   Whether a mutable receiver or argument may be destroyed and/or
		 *   recycled to hold the quotient.
		 * @return
		 *   The quotient, possibly recycling one of the inputs if canDestroy is
		 *   true.
		 */
		fun A_Number.divideIntoFloatCanDestroy(
			floatObject: A_Number,
			canDestroy: Boolean
		): A_Number = dispatch {
			o_DivideIntoFloatCanDestroy(it, floatObject, canDestroy)
		}

		/**
		 * Divide the [double][DoubleDescriptor] argument by the receiver,
		 * destroying one or the other if it's mutable and canDestroy is true.
		 * Because of the requirement that the argument be a double rather than
		 * an arbitrary [A_Number], this is usually only used for
		 * double-dispatching.
		 *
		 * @param doubleObject
		 *   The double to divide by the receiver.
		 * @param canDestroy
		 *   Whether a mutable receiver or argument may be destroyed and/or
		 *   recycled to hold the quotient.
		 * @return
		 *   The quotient, possibly recycling one of the inputs if canDestroy is
		 *   true.
		 */
		fun A_Number.divideIntoDoubleCanDestroy(
			doubleObject: A_Number,
			canDestroy: Boolean
		): A_Number = dispatch {
			o_DivideIntoDoubleCanDestroy(it, doubleObject, canDestroy)
		}

		/**
		 * Compute the boolean `exclusive-or` operation for the corresponding
		 * bits of the receiver and anInteger.  Both values are signed 2's
		 * complement integers.
		 *
		 * For example, if ...11001₂ (negative seven) and ...01010₂ (positive
		 * ten) are provided, the result will be ...10011 (negative thirteen).
		 *
		 * @param anInteger
		 *   The integer to combine with the receiver using the bitwise
		 *   `exclusive-or` operation.
		 * @param canDestroy
		 *   Whether the receiver or anInteger can be recycled or destroyed if
		 *   it happens to be mutable.
		 * @return
		 *   The bitwise `exclusive-or` of the receiver and anInteger.
		 */
		fun A_Number.bitwiseXor(
			anInteger: A_Number,
			canDestroy: Boolean
		): A_Number = dispatch { o_BitwiseXor(it, anInteger, canDestroy) }

		/**
		 * Compute the boolean `or` operation for the corresponding bits of the
		 * receiver and anInteger.  Both values are signed 2's complement
		 * integers.
		 *
		 * For example, if ...11001₂ (negative seven) and ...01010₂ (positive
		 * ten) are provided, the result will be ...11011 (negative five).
		 *
		 * @param anInteger
		 *   The integer to combine with the receiver using the bitwise `or`
		 *   operation.
		 * @param canDestroy
		 *   Whether the receiver or anInteger can be recycled or destroyed if
		 *   it happens to be mutable.
		 * @return
		 *   The bitwise `or` of the receiver and anInteger.
		 */
		fun A_Number.bitwiseOr(
			anInteger: A_Number,
			canDestroy: Boolean
		): A_Number = dispatch { o_BitwiseOr(it, anInteger, canDestroy) }

		/**
		 * Compute the boolean `and` operation for the corresponding bits of the
		 * receiver and anInteger.  Both values are signed 2's complement
		 * integers.
		 *
		 * For example, if ...11001₂ (negative seven) and ...01010₂ (positive
		 * ten) are provided, the result will be ...01000 (eight).
		 *
		 * @param anInteger
		 *   The integer to combine with the receiver using the bitwise `and`
		 *   operation.
		 * @param canDestroy
		 *   Whether the receiver or anInteger can be recycled or destroyed if
		 *   it happens to be mutable.
		 * @return
		 *   The bitwise `and` of the receiver and anInteger.
		 */
		fun A_Number.bitwiseAnd(
			anInteger: A_Number,
			canDestroy: Boolean
		): A_Number = dispatch { o_BitwiseAnd(it, anInteger, canDestroy) }

		/**
		 * Extract the bit with value 2^[bitPosition] from the integer, as a
		 * [Boolean].
		 *
		 * @receiver
		 *   The [integer][IntegerDescriptor] to query.
		 * @param bitPosition
		 *   Which bit to query.  0 indicates the low bit (even/odd), and in
		 *   general, the bit representing 2^[bitPosition] is being queried.
		 *   This must be non-negative.
		 * @return
		 *   True if the indicated bit was set, otherwise false.
		 */
		fun A_Number.bitTest(
			bitPosition: Int
		): Boolean = dispatch { o_BitTest(it, bitPosition) }

		/**
		 * Produce an integer like the receiver, but with the bit corresponding
		 * to 2^[bitPosition] either set or cleared, depending on [value].  The
		 * receiver may be recycled or destroyed if [canDestroy] is true.
		 *
		 * @receiver
		 *   The integer[IntegerDescriptor] to update or replace.
		 * @param bitPosition
		 *   The bit position to update.  If it's 0, update the lowest bit, and
		 *   so on, updating the bit representing the value 2^[bitPosition].
		 *   This must be non-negative, and be within reasonable VM limits
		 *   related to representing the output value.
		 * @param value
		 *   If true, set the indicated bit in the result, otherwise clear it.
		 * @param canDestroy
		 *   If true, the receiver may be recycled or destroyed if it happens to
		 *   be mutable.
		 * @return
		 *   The resulting integer.
		 */
		fun A_Number.bitSet(
			bitPosition: Int,
			value: Boolean,
			canDestroy: Boolean
		): A_Number = dispatch { o_BitSet(it, bitPosition, value, canDestroy) }

		/**
		 * Shift the non-negative integer to the left by the specified number of
		 * bits, then truncate the representation to force bits above the
		 * specified position to be zeroed.  The shift factor may be negative,
		 * indicating a right shift by the corresponding positive amount, in
		 * which case truncation will still happen.
		 *
		 * For example, shifting the binary number 1011₂ to the left by 2
		 * positions will produce 101100₂, then truncating it to, say 5 bits,
		 * would produce 01100₂.  For a second example, the positive number
		 * 110101₂ can be shifted left by -2 positions, which is a right shift
		 * of 2, to get 1101₂, and a subsequent truncation to 10 bits would
		 * leave it unaffected.
		 *
		 * @param shiftFactor
		 *   How much to shift the receiver left (may be negative to indicate a
		 *   right shift).
		 * @param truncationBits
		 *   A positive integer indicating how many low-order bits of the
		 *   shifted value should be preserved.
		 * @param canDestroy
		 *   Whether it is permitted to alter the receiver if it happens to be
		 *   mutable.
		 * @return
		 *   (object &times; 2^shiftFactor) mod 2^truncationBits
		 */
		fun A_Number.bitShiftLeftTruncatingToBits(
			shiftFactor: A_Number,
			truncationBits: A_Number,
			canDestroy: Boolean
		): A_Number = dispatch {
			o_BitShiftLeftTruncatingToBits(
				it, shiftFactor, truncationBits, canDestroy)
		}

		/**
		 * Shift this integer left by the specified number of bits.  If the
		 * shift amount is negative, perform a right shift instead (of the
		 * negation of the specified amount).  In the case that the receiver is
		 * negative, shift in zeroes on the right or ones on the left.
		 *
		 * @param shiftFactor
		 *   How much to shift left, or if negative, the negation of how much to
		 *   shift right.
		 * @param canDestroy
		 *   Whether either input can be destroyed or recycled if it's mutable.
		 * @return
		 *   The shifted Avail [integer][IntegerDescriptor].
		 */
		fun A_Number.bitShift(
			shiftFactor: A_Number,
			canDestroy: Boolean
		): A_Number = dispatch { o_BitShift(it, shiftFactor, canDestroy) }

		/**
		 * Convert the receiver, which must be an integer, into a Java
		 * [BigInteger].
		 *
		 * @return a Java [BigInteger].
		 */
		fun A_Number.asBigInteger(): BigInteger =
			dispatch { o_AsBigInteger(it) }

		/**
		 * Add the receiver to the given finite integer, destroying one or the
		 * other if it's mutable and canDestroy is true.  Because of the
		 * requirement that the argument be an integer rather than an arbitrary
		 * [A_Number], this is usually only used for double-dispatching.
		 *
		 * @param anInteger
		 *   The finite integer to add to the receiver.
		 * @param canDestroy
		 *   Whether a mutable receiver or argument may be destroyed and/or
		 *   recycled to hold the sum.
		 * @return
		 *   The sum, possibly recycling one of the inputs if canDestroy is
		 *   true.
		 */
		fun A_Number.addToIntegerCanDestroy(
			anInteger: AvailObject,
			canDestroy: Boolean
		): A_Number =
			dispatch { o_AddToIntegerCanDestroy(it, anInteger, canDestroy) }

		/**
		 * Add the receiver to the integral infinity with the given [Sign],
		 * destroying one or the other if it's mutable and canDestroy is true.
		 * Because of the requirement that the argument be an integral infinity
		 * rather than an arbitrary [A_Number], this is usually only used for
		 * double-dispatching.
		 *
		 * @param sign
		 *   The sign of integral infinity to add to the receiver.
		 * @param canDestroy
		 *   Whether a mutable receiver or argument may be destroyed and/or
		 *   recycled to hold the sum.
		 * @return
		 *   The sum, possibly recycling one of the inputs if canDestroy is
		 *   true.
		 */
		fun A_Number.addToInfinityCanDestroy(
			sign: Sign,
			canDestroy: Boolean
		): A_Number =
			dispatch { o_AddToInfinityCanDestroy(it, sign, canDestroy) }

		/**
		 * Add the receiver to the given [float][FloatDescriptor], destroying
		 * one or the other if it's mutable and canDestroy is true. Because of
		 * the requirement that the argument be a float rather than an arbitrary
		 * [A_Number], this is usually only used for double-dispatching.
		 *
		 * @param floatObject
		 *   The float to add to the receiver.
		 * @param canDestroy
		 *   Whether a mutable receiver or argument may be destroyed and/or
		 *   recycled to hold the sum.
		 * @return
		 *   The sum, possibly recycling one of the inputs if canDestroy is
		 *   true.
		 */
		fun A_Number.addToFloatCanDestroy(
			floatObject: A_Number,
			canDestroy: Boolean
		): A_Number = dispatch {
			o_AddToFloatCanDestroy(it, floatObject, canDestroy)
		}

		/**
		 * Add the receiver to the given [double][DoubleDescriptor], destroying
		 * one or the other if it's mutable and canDestroy is true. Because of
		 * the requirement that the argument be a double rather than an
		 * arbitrary [A_Number], this is usually only used for
		 * double-dispatching.
		 *
		 * @param doubleObject
		 *   The double to add to the receiver.
		 * @param canDestroy
		 *   Whether a mutable receiver or argument may be destroyed and/or
		 *   recycled to hold the sum.
		 * @return
		 *   The sum, possibly recycling one of the inputs if canDestroy is
		 *   true.
		 */
		fun A_Number.addToDoubleCanDestroy(
			doubleObject: A_Number,
			canDestroy: Boolean
		): A_Number = dispatch {
			o_AddToDoubleCanDestroy(it, doubleObject, canDestroy)
		}

		/**
		 * Answer whether the receiver is numerically greater than or equivalent
		 * to the argument.
		 *
		 * @param another
		 *   A [numeric&#32;object][AbstractNumberDescriptor].
		 * @return
		 *   Whether the receiver is greater than or equivalent to the argument.
		 */
		fun A_Number.greaterOrEqual(another: A_Number): Boolean =
			numericCompare(another).isMoreOrEqual()

		/**
		 * Answer whether the receiver is numerically greater than the argument.
		 *
		 * @param another
		 *   A [numeric&#32;object][AbstractNumberDescriptor].
		 * @return
		 *   Whether the receiver is strictly greater than the argument.
		 */
		fun A_Number.greaterThan(another: A_Number): Boolean =
			numericCompare(another).isMore()

		fun A_Number.equalsDouble(aDouble: Double) =
			dispatch { o_EqualsDouble(it, aDouble) }

		fun A_Number.equalsFloat(aFloat: Float) =
			dispatch { o_EqualsFloat(it, aFloat) }

		/**
		 * Answer whether the receiver is an [infinity][InfinityDescriptor] with
		 * the specified [Sign].
		 *
		 * @param sign
		 *   The type of infinity for comparison.
		 * @return
		 *   `true` if the receiver is an infinity of the specified sign,
		 *   `false` otherwise.
		 */
		fun A_Number.equalsInfinity(sign: Sign) =
			dispatch { o_EqualsInfinity(it, sign) }

		fun A_Number.equalsInteger(anAvailInteger: AvailObject) =
			dispatch { o_EqualsInteger(it, anAvailInteger) }


		@ReferencedInGeneratedCode
		@JvmStatic
		fun divideStatic(
			self: AvailObject,
			aNumber: AvailObject,
			canDestroy: Boolean
		): AvailObject =
			self.descriptor().o_DivideCanDestroy(
				self, aNumber, canDestroy
			) as AvailObject

		/**
		 * The [CheckedMethod] for [divideStatic].
		 */
		val divideMethod = staticMethod(
			A_Number::class.java,
			::divideStatic.name,
			AvailObject::class.java,
			AvailObject::class.java,
			AvailObject::class.java,
			Boolean::class.javaPrimitiveType!!)


		@ReferencedInGeneratedCode
		@JvmStatic
		fun extractDoubleStatic(self: AvailObject): Double =
			self.dispatch { o_ExtractDouble(it) }

		/**
		 * The [CheckedMethod] for [extractDoubleStatic].
		 */
		val extractDoubleMethod = staticMethod(
			A_Number::class.java,
			::extractDoubleStatic.name,
			Double::class.javaPrimitiveType!!,
			AvailObject::class.java)

		@ReferencedInGeneratedCode
		@JvmStatic
		fun extractIntStatic(self: AvailObject): Int =
			self.dispatch { o_ExtractInt(it) }

		/**
		 * The [CheckedMethod] for [extractInt].
		 */
		val extractIntStaticMethod = staticMethod(
			A_Number::class.java,
			::extractIntStatic.name,
			Integer::class.javaPrimitiveType!!,
			AvailObject::class.java)

		@ReferencedInGeneratedCode
		@JvmStatic
		fun minusStatic(
			self: AvailObject,
			aNumber: AvailObject,
			canDestroy: Boolean
		): AvailObject =
			self.descriptor().o_MinusCanDestroy(
				self, aNumber, canDestroy
			) as AvailObject

		/**
		 * The [CheckedMethod] for [minusStatic].
		 */
		val minusCanDestroyMethod = staticMethod(
			A_Number::class.java,
			::minusStatic.name,
			AvailObject::class.java,
			AvailObject::class.java,
			AvailObject::class.java,
			Boolean::class.javaPrimitiveType!!)

		@ReferencedInGeneratedCode
		@JvmStatic
		fun numericCompareStatic(
			self: AvailObject,
			another: AvailObject
		): Order = self.descriptor().o_NumericCompare(self, another)

		/**
		 * The [CheckedMethod] for [numericCompare].
		 */
		val numericCompareMethod = staticMethod(
			A_Number::class.java,
			::numericCompareStatic.name,
			Order::class.java,
			AvailObject::class.java,
			AvailObject::class.java)

		@ReferencedInGeneratedCode
		@JvmStatic
		fun timesStatic(
			self: AvailObject,
			aNumber: AvailObject,
			canDestroy: Boolean
		): AvailObject =
			self.descriptor().o_TimesCanDestroy(
				self, aNumber, canDestroy
			) as AvailObject

		/**
		 * The [CheckedMethod] for [timesCanDestroy].
		 */
		val timesCanDestroyMethod = staticMethod(
			A_Number::class.java,
			::timesStatic.name,
			AvailObject::class.java,
			AvailObject::class.java,
			AvailObject::class.java,
			Boolean::class.javaPrimitiveType!!)

		@ReferencedInGeneratedCode
		@JvmStatic
		fun isIntStatic(self: AvailObject): Boolean =
			self.descriptor().o_IsInt(self)

		/** The [CheckedMethod] for [isInt]. */
		val isIntMethod = staticMethod(
			A_Number::class.java,
			::isIntStatic.name,
			Boolean::class.javaPrimitiveType!!,
			AvailObject::class.java)

		@ReferencedInGeneratedCode
		@JvmStatic
		fun isDoubleStatic(self: AvailObject): Boolean =
			self.descriptor().o_IsDouble(self)

		/** The [CheckedMethod] for [isDouble]. */
		val isDoubleMethod = staticMethod(
			A_Number::class.java,
			::isDoubleStatic.name,
			Boolean::class.javaPrimitiveType!!,
			AvailObject::class.java)
	}
}
