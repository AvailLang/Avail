/*
 * A_Number.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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

import com.avail.descriptor.numbers.AbstractNumberDescriptor.Order
import com.avail.descriptor.numbers.AbstractNumberDescriptor.Sign
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.interpreter.primitive.numbers.P_LessOrEqual
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.CheckedMethod.Companion.instanceMethod
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import java.math.BigInteger

/**
 * `A_Number` is an interface that specifies the number-specific operations that
 * an [AvailObject] must implement.  It's a sub-interface of [A_BasicObject],
 * the interface that defines the behavior that all AvailObjects are required to
 * support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_Number : A_BasicObject {
	/**
	 * Answer whether the receiver is numerically greater than the argument.
	 *
	 * @param another
	 *   A [numeric&#32;object][AbstractNumberDescriptor].
	 * @return
	 *   Whether the receiver is strictly greater than the argument.
	 */
	fun greaterThan(another: A_Number): Boolean

	/**
	 * Answer whether the receiver is numerically greater than or equivalent to
	 * the argument.
	 *
	 * @param another
	 *   A [numeric&#32;object][AbstractNumberDescriptor].
	 * @return
	 *   Whether the receiver is greater than or equivalent to the argument.
	 */
	fun greaterOrEqual(another: A_Number): Boolean

	/**
	 * Add the receiver to the given [double][DoubleDescriptor], destroying one
	 * or the other if it's mutable and canDestroy is true. Because of the
	 * requirement that the argument be a double rather than an arbitrary
	 * [A_Number], this is usually only used for double-dispatching.
	 *
	 * @param doubleObject
	 *   The double to add to the receiver.
	 * @param canDestroy
	 *   Whether a mutable receiver or argument may be destroyed and/or recycled
	 *   to hold the sum.
	 * @return
	 *   The sum, possibly recycling one of the inputs if canDestroy is true.
	 */
	fun addToDoubleCanDestroy(
		doubleObject: A_Number,
		canDestroy: Boolean
	): A_Number

	/**
	 * Add the receiver to the given [float][FloatDescriptor], destroying one or
	 * the other if it's mutable and canDestroy is true. Because of the
	 * requirement that the argument be a float rather than an arbitrary
	 * [A_Number], this is usually only used for double-dispatching.
	 *
	 * @param floatObject
	 *   The float to add to the receiver.
	 * @param canDestroy
	 *   Whether a mutable receiver or argument may be destroyed and/or recycled
	 *   to hold the sum.
	 * @return
	 *   The sum, possibly recycling one of the inputs if canDestroy is true.
	 */
	fun addToFloatCanDestroy(
		floatObject: A_Number,
		canDestroy: Boolean
	): A_Number

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
	 *   Whether a mutable receiver or argument may be destroyed and/or recycled
	 *   to hold the sum.
	 * @return
	 *   The sum, possibly recycling one of the inputs if canDestroy is true.
	 */
	fun addToInfinityCanDestroy(sign: Sign, canDestroy: Boolean): A_Number

	/**
	 * Add the receiver to the given finite integer, destroying one or the other
	 * if it's mutable and canDestroy is true.  Because of the requirement that
	 * the argument be an integer rather than an arbitrary [A_Number], this is
	 * usually only used for double-dispatching.
	 *
	 * @param anInteger
	 *   The finite integer to add to the receiver.
	 * @param canDestroy
	 *   Whether a mutable receiver or argument may be destroyed and/or recycled
	 *   to hold the sum.
	 * @return
	 *   The sum, possibly recycling one of the inputs if canDestroy is true.
	 */
	fun addToIntegerCanDestroy(
		anInteger: AvailObject,
		canDestroy: Boolean
	): A_Number

	/**
	 * Convert the receiver, which must be an integer, into a Java [BigInteger].
	 *
	 * @return a Java [BigInteger].
	 */
	fun asBigInteger(): BigInteger

	/**
	 * Shift this integer left by the specified number of bits.  If the shift
	 * amount is negative, perform a right shift instead (of the negation of the
	 * specified amount).  In the case that the receiver is negative, shift in
	 * zeroes on the right or ones on the left.
	 *
	 * @param shiftFactor
	 *   How much to shift left, or if negative, the negation of how much to
	 *   shift right.
	 * @param canDestroy
	 *   Whether either input can be destroyed or recycled if it's mutable.
	 * @return
	 *   The shifted Avail [integer][IntegerDescriptor].
	 */
	fun bitShift(
		shiftFactor: A_Number,
		canDestroy: Boolean
	): A_Number

	/**
	 * Shift the non-negative integer to the left by the specified number of
	 * bits, then truncate the representation to force bits above the specified
	 * position to be zeroed.  The shift factor may be negative, indicating a
	 * right shift by the corresponding positive amount, in which case
	 * truncation will still happen.
	 *
	 * For example, shifting the binary number 1011₂ to the left by 2 positions
	 * will produce 101100₂, then truncating it to, say 5 bits, would produce
	 * 01100₂.  For a second example, the positive number 110101₂ can be shifted
	 * left by -2 positions, which is a right shift of 2, to get 1101₂, and a
	 * subsequent truncation to 10 bits would leave it unaffected.
	 *
	 * @param shiftFactor
	 *   How much to shift the receiver left (may be negative to indicate a
	 *   right shift).
	 * @param truncationBits
	 *   A positive integer indicating how many low-order bits of the shifted
	 *   value should be preserved.
	 * @param canDestroy
	 *   Whether it is permitted to alter the receiver if it happens to be
	 *   mutable.
	 * @return
	 *   (object &times; 2^shiftFactor) mod 2^truncationBits
	 */
	fun bitShiftLeftTruncatingToBits(
		shiftFactor: A_Number,
		truncationBits: A_Number,
		canDestroy: Boolean
	): A_Number

	/**
	 * Compute the boolean `and` operation for the corresponding bits of the
	 * receiver and anInteger.  Both values are signed 2's complement integers.
	 *
	 * For example, if ...11001₂ (negative seven) and ...01010₂ (positive ten)
	 * are provided, the result will be ...01000 (eight).
	 *
	 * @param anInteger
	 *   The integer to combine with the receiver using the bitwise `and`
	 *   operation.
	 * @param canDestroy
	 *   Whether the receiver or anInteger can be recycled or destroyed if it
	 *   happens to be mutable.
	 * @return
	 *   The bitwise `and` of the receiver and anInteger.
	 */
	fun bitwiseAnd(
		anInteger: A_Number,
		canDestroy: Boolean
	): A_Number

	/**
	 * Compute the boolean `or` operation for the corresponding bits of the
	 * receiver and anInteger.  Both values are signed 2's complement integers.
	 *
	 * For example, if ...11001₂ (negative seven) and ...01010₂ (positive ten)
	 * are provided, the result will be ...11011 (negative five).
	 *
	 * @param anInteger
	 *   The integer to combine with the receiver using the bitwise `or`
	 *   operation.
	 * @param canDestroy
	 *   Whether the receiver or anInteger can be recycled or destroyed if it
	 *   happens to be mutable.
	 * @return
	 *   The bitwise `or` of the receiver and anInteger.
	 */
	fun bitwiseOr(
		anInteger: A_Number,
		canDestroy: Boolean
	): A_Number

	/**
	 * Compute the boolean `exclusive-or` operation for the corresponding bits
	 * of the receiver and anInteger.  Both values are signed 2's complement
	 * integers.
	 *
	 * For example, if ...11001₂ (negative seven) and ...01010₂ (positive ten)
	 * are provided, the result will be ...10011 (negative thirteen).
	 *
	 * @param anInteger
	 *   The integer to combine with the receiver using the bitwise
	 *   `exclusive-or` operation.
	 * @param canDestroy
	 *   Whether the receiver or anInteger can be recycled or destroyed if it
	 *   happens to be mutable.
	 * @return
	 *   The bitwise `exclusive-or` of the receiver and anInteger.
	 */
	fun bitwiseXor(
		anInteger: A_Number,
		canDestroy: Boolean
	): A_Number

	/**
	 * Divide the [double][DoubleDescriptor] argument by the receiver,
	 * destroying one or the other if it's mutable and canDestroy is true.
	 * Because of the requirement that the argument be a double rather than an
	 * arbitrary [A_Number], this is usually only used for double-dispatching.
	 *
	 * @param doubleObject
	 *   The double to divide by the receiver.
	 * @param canDestroy
	 *   Whether a mutable receiver or argument may be destroyed and/or recycled
	 *   to hold the quotient.
	 * @return
	 *   The quotient, possibly recycling one of the inputs if canDestroy is
	 *   true.
	 */
	fun divideIntoDoubleCanDestroy(
		doubleObject: A_Number,
		canDestroy: Boolean
	): A_Number

	/**
	 * Divide the [float][FloatDescriptor] argument by the receiver, destroying
	 * one or the other if it's mutable and canDestroy is true. Because of the
	 * requirement that the argument be a float rather than an arbitrary
	 * [A_Number], this is usually only used for double-dispatching.
	 *
	 * @param floatObject
	 *   The float to divide by the receiver.
	 * @param canDestroy
	 *   Whether a mutable receiver or argument may be destroyed and/or recycled
	 *   to hold the quotient.
	 * @return
	 *   The quotient, possibly recycling one of the inputs if canDestroy is
	 *   true.
	 */
	fun divideIntoFloatCanDestroy(
		floatObject: A_Number,
		canDestroy: Boolean
	): A_Number

	/**
	 * Divide the [infinity][InfinityDescriptor] having the specified [Sign] by
	 * the receiver, destroying one or the other if it's mutable and canDestroy
	 * is true.  Because of the requirement that the argument be an infinity
	 * rather than an arbitrary [A_Number], this is usually only used for
	 * double-dispatching.
	 *
	 * @param sign
	 *   The [Sign] of the infinity to divide by the receiver.
	 * @param canDestroy
	 *   Whether a mutable receiver or argument may be destroyed and/or recycled
	 *   to hold the quotient.
	 * @return
	 *   The quotient, possibly recycling one of the inputs if canDestroy is
	 *   true.
	 */
	fun divideIntoInfinityCanDestroy(sign: Sign, canDestroy: Boolean): A_Number

	/**
	 * Divide the Avail [integer][IntegerDescriptor] argument by the receiver,
	 * destroying one or the other if it's mutable and canDestroy is true.
	 * Because of the requirement that the argument be an integer rather than an
	 * arbitrary [A_Number], this is usually only used for double-dispatching.
	 *
	 * @param anInteger
	 *   The integer to divide by the receiver.
	 * @param canDestroy
	 *   Whether a mutable receiver or argument may be destroyed and/or recycled
	 *   to hold the quotient.
	 * @return
	 *   The quotient, possibly recycling one of the inputs if canDestroy is
	 *   true.
	 */
	fun divideIntoIntegerCanDestroy(
		anInteger: AvailObject,
		canDestroy: Boolean
	): A_Number

	/**
	 * Divide the receiver by the argument `aNumber` and answer the
	 * [result][AvailObject].
	 *
	 * Implementations may double-dispatch to [divideIntoIntegerCanDestroy] or
	 * [divideIntoInfinityCanDestroy] (or others), where actual implementations
	 * of the division operation should reside.
	 *
	 * @param aNumber
	 *   An integral numeric.
	 * @param canDestroy
	 *   `true` if the operation may modify either operand, `false` otherwise.
	 * @return
	 *   The [result][AvailObject] of dividing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@ReferencedInGeneratedCode
	fun divideCanDestroy(
		aNumber: A_Number,
		canDestroy: Boolean
	): A_Number

	/**
	 * Extract a signed byte from the [receiver][AvailObject].
	 *
	 * @return
	 *   A Java [Byte], which has the range `[-128..127]`.
	 */
	fun extractSignedByte(): Byte

	/**
	 * Extract a signed short from the [receiver][AvailObject].
	 *
	 * @return
	 *   A [Short], which has the range `[-32768..32767]`.
	 */
	fun extractSignedShort(): Short

	/**
	 * Extract an unsigned byte from the [receiver][AvailObject].
	 * Return it in a Java [Short] to avoid sign bit reinterpretation.
	 *
	 * @return
	 *   A [Short] in the range `[0..255]`.
	 */
	fun extractUnsignedByte(): Short

	/**
	 * Extract an unsigned short from the [receiver][AvailObject]. Return it in
	 * a Kotlin [Int] to avoid sign bit reinterpretation.
	 *
	 * @return
	 *   An [Int] in the range `[0..65535]`.
	 */
	fun extractUnsignedShort(): Int

	/**
	 * Extract a Kotlin [Double] from the [receiver][AvailObject].
	 *
	 * @return
	 *   A Kotlin [Double].
	 */
	@ReferencedInGeneratedCode
	fun extractDouble(): Double

	/**
	 * Extract a Java float from the [receiver][AvailObject].
	 *
	 * @return
	 *   A Java `float`.
	 */
	fun extractFloat(): Float

	/**
	 * Extract a 32-bit signed Kotlin [Int] from the [receiver][AvailObject].
	 *
	 * @return
	 *   A 32-bit signed Kotlin [Int].
	 */
	@ReferencedInGeneratedCode
	fun extractInt(): Int

	/**
	 * Extract a 64-bit signed Java [Long] from the [receiver][AvailObject].
	 *
	 * @return
	 *   A 64-bit signed Java [Long].
	 */
	fun extractLong(): Long

	/**
	 * Extract an unsigned nybble from the [receiver][AvailObject]. Return it as
	 * a Java [Byte].
	 *
	 * @return
	 *   A [Byte] in the range `[0..15]`.
	 */
	fun extractNybble(): Byte

	/**
	 * Answer whether this number is numerically equal to some finite integer.
	 *
	 * @return
	 *   A boolean indicating finiteness and a fractional part of zero.
	 */
	fun isNumericallyIntegral(): Boolean

	/**
	 * Answer whether this integral [infinity][InfinityDescriptor] is positive.
	 *
	 * @return
	 *   `true` if the receiver is positive integral infinity, or `false` if the
	 *   receiver is negative integral infinity. No other values are permitted.
	 */
	fun isPositive(): Boolean

	/**
	 * Answer whether the receiver is numerically less than or equivalent to the
	 * argument.
	 *
	 * @param another
	 *   A [numeric&#32;object][AbstractNumberDescriptor].
	 * @return
	 *   Whether the receiver is less than or equivalent to the argument.
	 */
	fun lessOrEqual(another: A_Number): Boolean

	/**
	 * Answer whether the receiver is numerically less than the argument.
	 *
	 * @param another
	 *   A [numeric&#32;object][AbstractNumberDescriptor].
	 * @return
	 *   Whether the receiver is strictly less than the argument.
	 */
	fun lessThan(another: A_Number): Boolean

	/**
	 * Subtract the argument `aNumber` from a receiver and answer the
	 * [result][AvailObject].
	 *
	 * Implementations may double-dispatch to [subtractFromIntegerCanDestroy] or
	 * [subtractFromInfinityCanDestroy], where actual implementations of the
	 * subtraction operation should reside.
	 *
	 * @param aNumber
	 *   An integral numeric.
	 * @param canDestroy
	 *   `true` if the operation may modify either operand, `false` otherwise.
	 * @return
	 *   The [result][AvailObject] of differencing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@ReferencedInGeneratedCode
	fun minusCanDestroy(aNumber: A_Number, canDestroy: Boolean): A_Number

	/**
	 * Multiply the receiver by the given [double][DoubleDescriptor], destroying
	 * one or the other if it's mutable and canDestroy is true.  Because of the
	 * requirement that the argument be a double rather than an arbitrary
	 * [A_Number], this is usually only used for double-dispatching.
	 *
	 * @param doubleObject
	 *   The double to multiply the receiver by.
	 * @param canDestroy
	 *   Whether a mutable receiver or argument may be destroyed and/or recycled
	 *   to hold the product.
	 * @return
	 *   The product, possibly recycling one of the inputs if canDestroy is
	 *   true.
	 */
	fun multiplyByDoubleCanDestroy(
		doubleObject: A_Number,
		canDestroy: Boolean
	): A_Number

	/**
	 * Multiply the receiver by the given [float][FloatDescriptor], destroying
	 * one or the other if it's mutable and `canDestroy` is true. Because of the
	 * requirement that the argument be a float rather than an arbitrary
	 * [A_Number], this is usually only used for double-dispatching.
	 *
	 * @param floatObject
	 *   The float to multiply the receiver by.
	 * @param canDestroy
	 *   Whether a mutable receiver or argument may be destroyed and/or recycled
	 *   to hold the product.
	 * @return
	 *   The product, possibly recycling one of the inputs if canDestroy is
	 *   true.
	 */
	fun multiplyByFloatCanDestroy(
		floatObject: A_Number,
		canDestroy: Boolean
	): A_Number

	/**
	 * Multiply the receiver by the [infinity][InfinityDescriptor] with the
	 * given [sign][Sign], potentially destroying the receiver if it's mutable
	 * and canDestroy is true.
	 *
	 * @param sign
	 *   The sign of the infinity by which to multiply the receiver.
	 * @param canDestroy
	 *   Whether a mutable receiver may be destroyed and/or recycled to hold the
	 *   product.
	 * @return
	 *   The product, possibly recycling the receiver if canDestroy is true.
	 */
	fun multiplyByInfinityCanDestroy(sign: Sign, canDestroy: Boolean): A_Number

	/**
	 * Multiply the receiver by the given [integer][IntegerDescriptor],
	 * destroying one or the other if it's mutable and canDestroy is true.
	 * Because of the requirement that the argument be an integer rather than an
	 * arbitrary [A_Number], this is usually only used for double-dispatching.
	 *
	 * @param anInteger
	 *   The integer to multiply the receiver by.
	 * @param canDestroy
	 *   Whether a mutable receiver or argument may be destroyed and/or recycled
	 *   to hold the product.
	 * @return
	 *   The product, possibly recycling one of the inputs if canDestroy is
	 *   true.
	 */
	fun multiplyByIntegerCanDestroy(
		anInteger: AvailObject,
		canDestroy: Boolean
	): A_Number

	/**
	 * Divide the receiver by the argument `aNumber` and answer the
	 * [result][AvailObject]. The operation is not allowed to fail, so the
	 * caller must ensure that the arguments are valid, i.e. the divisor is not
	 * [zero][IntegerDescriptor.zero].
	 *
	 * @param aNumber
	 *   An integral numeric.
	 * @param canDestroy
	 *   `true` if the operation may modify either operand, `false` otherwise.
	 * @return
	 *   The [result][AvailObject] of dividing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	fun noFailDivideCanDestroy(
		aNumber: A_Number,
		canDestroy: Boolean
	): A_Number

	/**
	 * Difference the receiver and the argument `aNumber` and answer the
	 * [result][AvailObject]. The operation is not allowed to fail, so the
	 * caller must ensure that the arguments are valid, i.e. not
	 * [infinities][InfinityDescriptor] of like sign.
	 *
	 * @param aNumber
	 *   An integral numeric.
	 * @param canDestroy
	 *   `true` if the operation may modify either operand, `false` otherwise.
	 * @return
	 *   The [result][AvailObject] of differencing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	fun noFailMinusCanDestroy(
		aNumber: A_Number,
		canDestroy: Boolean
	): A_Number

	/**
	 * Add the receiver and the argument `aNumber` and answer the
	 * [result][AvailObject]. The operation is not allowed to fail, so the
	 * caller must ensure that the arguments are valid, i.e. not
	 * [infinities][InfinityDescriptor] of unlike sign.
	 *
	 * @param aNumber
	 *   An integral numeric.
	 * @param canDestroy
	 *   `true` if the operation may modify either operand, `false` otherwise.
	 * @return
	 *   The [result][AvailObject] of adding the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	fun noFailPlusCanDestroy(
		aNumber: A_Number,
		canDestroy: Boolean
	): A_Number

	/**
	 * Multiply the receiver and the argument `aNumber` and answer the
	 * [result][AvailObject]. The operation is not allowed to fail, so the
	 * caller must ensure that the arguments are valid, i.e. not
	 * [zero][IntegerDescriptor.zero] and [infinity][InfinityDescriptor].
	 *
	 * @param aNumber
	 *   An integral numeric.
	 * @param canDestroy
	 *   `true` if the operation may modify either operand, `false` otherwise.
	 * @return
	 *   The [result][AvailObject] of adding the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	fun noFailTimesCanDestroy(
		aNumber: A_Number,
		canDestroy: Boolean
	): A_Number

	/**
	 * Answer an ordering between the receiver and the argument.  This compares
	 * the underlying real numeric values of the two [A_Number]s, which does not
	 * necessarily agree with the [equals] semantics.  In particular, under
	 * numerical ordering, 5 = 5.0 = 5.0f, and 0.0/0.0 is incomparable to every
	 * number, even itself.  Under ordinary equality (the [equals] method), an
	 * integer never equals a float, and neither ever equals a double.  However,
	 * 0.0/0.0 is equal to 0.0/0.0, since they have the same kind (double) and
	 * the same bit pattern.  Note that [hash] agrees with general equality, not
	 * the numeric ordering.
	 *
	 * The numeric order is not directly exposed to Avail, but it can be
	 * reconstructed by computing the [P_LessOrEqual] predicate on two values
	 * and also on the two values interchanged.  If one but not the other is
	 * true, the order is either [Order.LESS] or [Order.MORE].  If both are
	 * true, the values are [Order.EQUAL], and if neither is true then the
	 * values are [Order.INCOMPARABLE], which is only the case if one or both
	 * values are float or double not-a-numbers (easily produced via 0.0/0.0).
	 *
	 * @param another
	 *   The value to numerically compare the receiver to.
	 * @return
	 *   The numeric [Order] between the receiver and the argument.
	 */
	@ReferencedInGeneratedCode
	fun numericCompare(another: A_Number): Order

	/**
	 * This produces the same value as [numericCompare], but the argument is
	 * known to be an [integral&#32;infinity][InfinityDescriptor] whose [Sign]
	 * is provided.
	 *
	 * @param sign
	 *   The sign of the integral infinity to compare against.
	 * @return
	 *   How the receiver compares to the specified infinity.
	 */
	fun numericCompareToInfinity(sign: Sign): Order

	/**
	 * This produces the same value as [numericCompare], but the argument is
	 * an unboxed `double` value.
	 *
	 * @param aDouble
	 *   The [Double] to numerically compare against.
	 * @return
	 *   How the receiver compares to the specified double.
	 */
	fun numericCompareToDouble(aDouble: Double): Order

	/**
	 * This produces the same value as [numericCompare], but the argument is
	 * known to be an [integer][IntegerDescriptor].
	 *
	 * @param anInteger
	 *   The `integer` to numerically compare against.
	 * @return
	 *   How the receiver compares to the specified integer.
	 */
	fun numericCompareToInteger(anInteger: AvailObject): Order

	/**
	 * Add the receiver and the argument `aNumber` and answer the
	 * [result][AvailObject].
	 *
	 * Implementations may double-dispatch to [addToIntegerCanDestroy] or
	 * [addToInfinityCanDestroy], where actual implementations of the addition
	 * operation should reside.
	 *
	 * @param aNumber
	 *   An integral numeric.
	 * @param canDestroy
	 *   `true` if the operation may modify either operand, `false` otherwise.
	 * @return
	 *   The [result][AvailObject] of adding the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	fun plusCanDestroy(
		aNumber: A_Number,
		canDestroy: Boolean
	): A_Number

	/**
	 * Extract a (signed) base 2^32 digit from the integer.  The index must be
	 * in range for the integer's representation.
	 *
	 * @param index
	 *   The one-based, little-endian index of the digit to extract.  It must be
	 *   between 1 and the number of digits present.
	 * @return
	 *   The base 2^32 digit as a signed [Int].
	 */
	fun rawSignedIntegerAt(index: Int): Int

	/**
	 * Replace a (signed) base 2^32 digit of the integer.  The index must be in
	 * range for the integer's representation, and the receiver must be mutable.
	 *
	 * @param index
	 *   The one-based, little-endian index of the digit to replace.  It must be
	 *   between 1 and the number of digits present.
	 * @param value
	 *   The replacement base 2^32 digit as a signed [Int].
	 */
	fun rawSignedIntegerAtPut(index: Int, value: Int)

	/**
	 * Extract an unsigned base 2^32 digit from the integer.  The index must be
	 * in range for the integer's representation.
	 *
	 * @param index
	 *   The one-based, little-endian index of the digit to extract.  It must be
	 *   between 1 and the number of digits present.
	 * @return
	 *   The unsigned base 2^32 digit as a signed [Long] to avoid
	 *   misinterpreting the sign.
	 */
	fun rawUnsignedIntegerAt(index: Int): Long

	/**
	 * Replace an unsigned base 2^32 digit of the integer.  The index must be in
	 * range for the integer's representation, and the receiver must be mutable.
	 *
	 * @param index
	 *   The one-based, little-endian index of the digit to replace.  It must be
	 *   between 1 and the number of digits present.
	 * @param value
	 *   The replacement base 2^32 digit as an [Int].  This does the same thing
	 *   as [rawSignedIntegerAtPut].
	 */
	fun rawUnsignedIntegerAtPut(index: Int, value: Int)

	/**
	 * Subtract the receiver from the given [double][DoubleDescriptor],
	 * destroying one or the other if it's mutable and canDestroy is true.
	 * Because of the requirement that the argument be a double rather than an
	 * arbitrary [A_Number], this is usually only used for double-dispatching.
	 *
	 * @param doubleObject
	 *   The double to subtract the receiver from.
	 * @param canDestroy
	 *   Whether a mutable receiver or argument may be destroyed and/or recycled
	 *   to hold the difference.
	 * @return
	 *   The difference, possibly recycling one of the inputs if canDestroy is
	 *   true.
	 */
	fun subtractFromDoubleCanDestroy(
		doubleObject: A_Number,
		canDestroy: Boolean
	): A_Number

	/**
	 * Subtract the receiver from the given [float][FloatDescriptor], destroying
	 * one or the other if it's mutable and canDestroy is true. Because of the
	 * requirement that the argument be a float rather than an arbitrary
	 * [A_Number], this is usually only used for double-dispatching.
	 *
	 * @param floatObject
	 *   The float to subtract the receiver from.
	 * @param canDestroy
	 *   Whether a mutable receiver or argument may be destroyed and/or recycled
	 *   to hold the difference.
	 * @return
	 *   The difference, possibly recycling one of the inputs if canDestroy is
	 *   true.
	 */
	fun subtractFromFloatCanDestroy(
		floatObject: A_Number,
		canDestroy: Boolean
	): A_Number

	/**
	 * Subtract the receiver from the [infinity][InfinityDescriptor] with the
	 * specified [sign][Sign], destroying one or the other if it's mutable and
	 * canDestroy is true.
	 *
	 * @param sign
	 *   The sign of the infinity to subtract from.
	 * @param canDestroy
	 *   Whether a mutable receiver or argument may be destroyed and/or recycled
	 *   to hold the difference.
	 * @return
	 *   The difference, possibly recycling one of the inputs if canDestroy is
	 *   true.
	 */
	fun subtractFromInfinityCanDestroy(
		sign: Sign,
		canDestroy: Boolean
	): A_Number

	/**
	 * Subtract the receiver from the given [integer][IntegerDescriptor],
	 * destroying one or the other if it's mutable and canDestroy is true.
	 *
	 * @param anInteger
	 *   The integer to subtract from.
	 * @param canDestroy
	 *   Whether a mutable receiver or argument may be destroyed and/or recycled
	 *   to hold the difference.
	 * @return
	 *   The difference, possibly recycling one of the inputs if canDestroy is
	 *   true.
	 */
	fun subtractFromIntegerCanDestroy(
		anInteger: AvailObject,
		canDestroy: Boolean
	): A_Number

	/**
	 * Multiply the receiver and the argument `aNumber` and answer the
	 * [result][AvailObject].
	 *
	 * Implementations may double-dispatch to [multiplyByIntegerCanDestroy] or
	 * [multiplyByInfinityCanDestroy], where actual implementations of the
	 * multiplication operation should reside.
	 *
	 * @param aNumber
	 *   An integral numeric.
	 * @param canDestroy
	 *   `true` if the operation may modify either operand, `false` otherwise.
	 * @return
	 *   The [result][AvailObject] of multiplying the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@ReferencedInGeneratedCode
	fun timesCanDestroy(aNumber: A_Number, canDestroy: Boolean): A_Number

	/**
	 * Normalize the integer to have the minimum number of base 2^32 digits.
	 */
	fun trimExcessInts()

	/**
	 * Determine if the receiver is an Avail integer equivalent to the specified
	 * Kotlin [Int].  Note that a non-integer should simply answer false, not
	 * fail.  This operation was placed in A_Number for organizational reasons,
	 * not type restriction.
	 *
	 * @param theInt
	 *   The Java int to compare against.
	 * @return
	 *   Whether the receiver represents that integer.
	 */
	fun equalsInt(theInt: Int): Boolean

	companion object {
		/**
		 * The [CheckedMethod] for [divideCanDestroy].
		 */
		@JvmField
		val divideCanDestroyMethod: CheckedMethod = instanceMethod(
			A_Number::class.java,
			A_Number::divideCanDestroy.name,
			A_Number::class.java,
			A_Number::class.java,
			Boolean::class.javaPrimitiveType!!)

		/**
		 * The [CheckedMethod] for [extractDouble].
		 */
		@JvmField
		val extractDoubleMethod: CheckedMethod = instanceMethod(
			A_Number::class.java,
			A_Number::extractDouble.name,
			Double::class.javaPrimitiveType!!)

		/**
		 * The [CheckedMethod] for [extractInt].
		 */
		@JvmField
		val extractIntMethod: CheckedMethod = instanceMethod(
			A_Number::class.java,
			A_Number::extractInt.name,
			Integer::class.javaPrimitiveType!!)

		/**
		 * The [CheckedMethod] for [minusCanDestroy].
		 */
		@JvmField
		val minusCanDestroyMethod: CheckedMethod = instanceMethod(
			A_Number::class.java,
			A_Number::minusCanDestroy.name,
			A_Number::class.java,
			A_Number::class.java,
			Boolean::class.javaPrimitiveType!!)

		/**
		 * The [CheckedMethod] for [numericCompare].
		 */
		@JvmField
		val numericCompareMethod: CheckedMethod = instanceMethod(
			A_Number::class.java,
			A_Number::numericCompare.name,
			Order::class.java,
			A_Number::class.java)

		/**
		 * The [CheckedMethod] for [timesCanDestroy].
		 */
		@JvmField
		val timesCanDestroyMethod: CheckedMethod = instanceMethod(
			A_Number::class.java,
			A_Number::timesCanDestroy.name,
			A_Number::class.java,
			A_Number::class.java,
			Boolean::class.javaPrimitiveType!!)
	}
}
