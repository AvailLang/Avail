/*
 * A_Number.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

package com.avail.descriptor;

import com.avail.descriptor.AbstractNumberDescriptor.Order;
import com.avail.descriptor.AbstractNumberDescriptor.Sign;
import com.avail.interpreter.primitive.numbers.P_LessOrEqual;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.math.BigInteger;

/**
 * {@code A_Number} is an interface that specifies the number-specific
 * operations that an {@link AvailObject} must implement.  It's a sub-interface
 * of {@link A_BasicObject}, the interface that defines the behavior that all
 * AvailObjects are required to support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public interface A_Number
extends A_BasicObject
{
	/**
	 * Answer whether the receiver is numerically greater than the argument.
	 *
	 * @param another A {@linkplain AbstractNumberDescriptor numeric object}.
	 * @return Whether the receiver is strictly greater than the argument.
	 */
	boolean greaterThan (A_Number another);

	/**
	 * Answer whether the receiver is numerically greater than or equivalent to
	 * the argument.
	 *
	 * @param another A {@linkplain AbstractNumberDescriptor numeric object}.
	 * @return Whether the receiver is greater than or equivalent to the
	 *         argument.
	 */
	boolean greaterOrEqual (A_Number another);

	/**
	 * Add the receiver to the given {@linkplain DoubleDescriptor double},
	 * destroying one or the other if it's mutable and canDestroy is true.
	 * Because of the requirement that the argument be a double rather than an
	 * arbitrary {@link A_Number}, this is usually only used for
	 * double-dispatching.
	 *
	 * @param doubleObject
	 *        The double to add to the receiver.
	 * @param canDestroy
	 *        Whether a mutable receiver or argument may be destroyed and/or
	 *        recycled to hold the sum.
	 * @return The sum, possibly recycling one of the inputs if canDestroy
	 *         is true.
	 */
	A_Number addToDoubleCanDestroy (
		A_Number doubleObject,
		boolean canDestroy);

	/**
	 * Add the receiver to the given {@linkplain FloatDescriptor float},
	 * destroying one or the other if it's mutable and canDestroy is true.
	 * Because of the requirement that the argument be a float rather than an
	 * arbitrary {@link A_Number}, this is usually only used for
	 * double-dispatching.
	 *
	 * @param floatObject
	 *        The float to add to the receiver.
	 * @param canDestroy
	 *        Whether a mutable receiver or argument may be destroyed and/or
	 *        recycled to hold the sum.
	 * @return The sum, possibly recycling one of the inputs if canDestroy
	 *         is true.
	 */
	A_Number addToFloatCanDestroy (
		A_Number floatObject,
		boolean canDestroy);

	/**
	 * Add the receiver to the integral infinity with the given {@link Sign},
	 * destroying one or the other if it's mutable and canDestroy is true.
	 * Because of the requirement that the argument be an integral infinity
	 * rather than an arbitrary {@link A_Number}, this is usually only used for
	 * double-dispatching.
	 *
	 * @param sign
	 *        The sign of integral infinity to add to the receiver.
	 * @param canDestroy
	 *        Whether a mutable receiver or argument may be destroyed and/or
	 *        recycled to hold the sum.
	 * @return The sum, possibly recycling one of the inputs if canDestroy
	 *         is true.
	 */
	A_Number addToInfinityCanDestroy (Sign sign, boolean canDestroy);

	/**
	 * Add the receiver to the given finite integer, destroying one or the other
	 * if it's mutable and canDestroy is true.  Because of the requirement that
	 * the argument be an integer rather than an arbitrary {@link A_Number},
	 * this is usually only used for double-dispatching.
	 *
	 * @param anInteger
	 *        The finite integer to add to the receiver.
	 * @param canDestroy
	 *        Whether a mutable receiver or argument may be destroyed and/or
	 *        recycled to hold the sum.
	 * @return The sum, possibly recycling one of the inputs if canDestroy
	 *         is true.
	 */
	A_Number addToIntegerCanDestroy (AvailObject anInteger, boolean canDestroy);

	/**
	 * Convert the receiver, which must be an integer, into a Java {@link
	 * BigInteger}.
	 *
	 * @return a Java {@code BigInteger}.
	 */
	BigInteger asBigInteger ();

	/**
	 * Shift this integer left by the specified number of bits.  If the shift
	 * amount is negative, perform a right shift instead (of the negation of the
	 * specified amount).  In the case that the receiver is negative, shift in
	 * zeroes on the right or ones on the left.
	 *
	 * @param shiftFactor
	 *        How much to shift left, or if negative, the negation of how much
	 *        to shift right.
	 * @param canDestroy
	 *        Whether either input can be destroyed or recycled if it's mutable.
	 * @return
	 *        The shifted Avail {@link IntegerDescriptor integer}.
	 */
	A_Number bitShift (
		A_Number shiftFactor,
		boolean canDestroy);

	/**
	 * Shift the non-negative integer to the left by the specified number of
	 * bits, then truncate the representation to force bits above the specified
	 * position to be zeroed.  The shift factor may be negative, indicating a
	 * right shift by the corresponding positive amount, in which case
	 * truncation will still happen.
	 *
	 * <p>
	 * For example, shifting the binary number 1011<sub>2</sub> to the left by 2
	 * positions will produce 101100<sub>2</sub>, then truncating it to, say 5
	 * bits, would produce 01100<sub>2</sub>.  For a second example, the
	 * positive number 110101 can be shifted left by -2 positions (which is a
	 * right shift of 2) to get 1101, and a subsequent truncation to 10 bits
	 * would leave it unaffected.
	 * </p>
	 *
	 * @param shiftFactor
	 *        How much to shift the receiver left (may be negative to indicate a
	 *        right shift).
	 * @param truncationBits
	 *        A positive integer indicating how many low-order bits of the
	 *        shifted value should be preserved.
	 * @param canDestroy
	 *        Whether it is permitted to alter the receiver if it happens to be
	 *        mutable.
	 * @return &#40;object &times; 2<sup>shiftFactor</sup>)
	 *         mod 2<sup>truncationBits</sup>
	 */
	A_Number bitShiftLeftTruncatingToBits (
		A_Number shiftFactor,
		A_Number truncationBits,
		boolean canDestroy);

	/**
	 * Compute the boolean {@code and} operation for the corresponding bits
	 * of the receiver and anInteger.  Both values are signed 2's complement
	 * integers.
	 *
	 * <p>For example, if ...11001<sub>2</sub> (negative seven) and
	 * ...01010<sub>2</sub> (ten) are provided, the result will be ...01000
	 * (eight).
	 *
	 * @param anInteger
	 *        The integer to combine with the receiver using the bitwise
	 *        {@code and} operation.
	 * @param canDestroy
	 *        Whether the receiver or anInteger can be recycled or destroyed if
	 *        it happens to be mutable.
	 * @return The bitwise {@code and} of the receiver and anInteger.
	 */
	A_Number bitwiseAnd (
		A_Number anInteger,
		boolean canDestroy);

	/**
	 * Compute the boolean {@code or} operation for the corresponding bits
	 * of the receiver and anInteger.  Both values are signed 2's complement
	 * integers.
	 *
	 * <p>For example, if ...11001<sub>2</sub> (negative seven) and
	 * ...01010<sub>2</sub> (ten) are provided, the result will be ...11011
	 * (negative five).
	 *
	 * @param anInteger
	 *        The integer to combine with the receiver using the bitwise
	 *        {@code or} operation.
	 * @param canDestroy
	 *        Whether the receiver or anInteger can be recycled or destroyed if
	 *        it happens to be mutable.
	 * @return The bitwise {@code or} of the receiver and anInteger.
	 */
	A_Number bitwiseOr (
		A_Number anInteger,
		boolean canDestroy);

	/**
	 * Compute the boolean {@code exclusive-or} operation for the
	 * corresponding bits of the receiver and anInteger.  Both values are
	 * signed 2's complement integers.
	 *
	 * <p>For example, if ...11001<sub>2</sub> (negative seven) and
	 * ...01010<sub>2</sub> (ten) are provided, the result will be ...10011
	 * (negative thirteen).
	 *
	 * @param anInteger
	 *        The integer to combine with the receiver using the bitwise
	 *        {@code exclusive-or} operation.
	 * @param canDestroy
	 *        Whether the receiver or anInteger can be recycled or destroyed if
	 *        it happens to be mutable.
	 * @return The bitwise {@code exclusive-or} of the receiver and
	 *         anInteger.
	 */
	A_Number bitwiseXor (
		A_Number anInteger,
		boolean canDestroy);

	/**
	 * Divide the {@linkplain DoubleDescriptor double} argument by the receiver,
	 * destroying one or the other if it's mutable and canDestroy is true.
	 * Because of the requirement that the argument be a double rather than an
	 * arbitrary {@link A_Number}, this is usually only used for
	 * double-dispatching.
	 *
	 * @param doubleObject
	 *        The double to divide by the receiver.
	 * @param canDestroy
	 *        Whether a mutable receiver or argument may be destroyed and/or
	 *        recycled to hold the quotient.
	 * @return The quotient, possibly recycling one of the inputs if
	 *         canDestroy is true.
	 */
	A_Number divideIntoDoubleCanDestroy (
		A_Number doubleObject,
		boolean canDestroy);

	/**
	 * Divide the {@linkplain FloatDescriptor float} argument by the receiver,
	 * destroying one or the other if it's mutable and canDestroy is true.
	 * Because of the requirement that the argument be a float rather than an
	 * arbitrary {@link A_Number}, this is usually only used for
	 * double-dispatching.
	 *
	 * @param floatObject
	 *        The float to divide by the receiver.
	 * @param canDestroy
	 *        Whether a mutable receiver or argument may be destroyed and/or
	 *        recycled to hold the quotient.
	 * @return The quotient, possibly recycling one of the inputs if
	 *         canDestroy is true.
	 */
	A_Number divideIntoFloatCanDestroy (
		A_Number floatObject,
		boolean canDestroy);

	/**
	 * Divide the {@linkplain InfinityDescriptor infinity} having the specified
	 * {@link Sign} by the receiver, destroying one or the other if it's mutable
	 * and canDestroy is true.  Because of the requirement that the argument be
	 * an infinity rather than an arbitrary {@link A_Number}, this is usually
	 * only used for double-dispatching.
	 *
	 * @param sign
	 *        The {@link Sign} of the infinity to divide by the receiver.
	 * @param canDestroy
	 *        Whether a mutable receiver or argument may be destroyed and/or
	 *        recycled to hold the quotient.
	 * @return The quotient, possibly recycling one of the inputs if
	 *         canDestroy is true.
	 */
	A_Number divideIntoInfinityCanDestroy (Sign sign, boolean canDestroy);

	/**
	 * Divide the Avail {@linkplain IntegerDescriptor integer} argument by the
	 * receiver, destroying one or the other if it's mutable and canDestroy is
	 * true.  Because of the requirement that the argument be an integer rather
	 * than an arbitrary {@link A_Number}, this is usually only used for
	 * double-dispatching.
	 *
	 * @param anInteger
	 *        The integer to divide by the receiver.
	 * @param canDestroy
	 *        Whether a mutable receiver or argument may be destroyed and/or
	 *        recycled to hold the quotient.
	 * @return The quotient, possibly recycling one of the inputs if
	 *         canDestroy is true.
	 */
	A_Number divideIntoIntegerCanDestroy (
		AvailObject anInteger,
		boolean canDestroy);

	/**
	 * Divide the receiver by the argument {@code aNumber} and answer the
	 * {@linkplain AvailObject result}.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * #divideIntoIntegerCanDestroy(AvailObject, boolean)
	 * divideIntoIntegerCanDestroy} or {@link
	 * #divideIntoInfinityCanDestroy(Sign, boolean)
	 * divideIntoInfinityCanDestroy} (or others), where actual implementations
	 * of the division operation should reside.</p>
	 *
	 * @param aNumber
	 *        An integral numeric.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of dividing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@ReferencedInGeneratedCode
	A_Number divideCanDestroy (
		A_Number aNumber,
		boolean canDestroy);

	/**
	 * Extract a signed byte from the {@linkplain AvailObject receiver}.
	 *
	 * @return A {@code byte}, which has the range [-128..127].
	 */
	byte extractSignedByte ();

	/**
	 * Extract a signed short from the {@linkplain AvailObject receiver}.
	 *
	 * @return A {@code short}, which has the range [-32768..32767].
	 */
	short extractSignedShort ();

	/**
	 * Extract an unsigned byte from the {@linkplain AvailObject receiver}.
	 * Return it in a Java {@code short} to avoid sign bit reinterpretation.
	 *
	 * @return A {@code short} in the range [0..255].
	 */
	short extractUnsignedByte ();

	/**
	 * Extract an unsigned short from the {@linkplain AvailObject receiver}.
	 * Return it in a Java {@code int} to avoid sign bit reinterpretation.
	 *
	 * @return An {@code int} in the range [0..65535].
	 */
	int extractUnsignedShort ();

	/**
	 * Extract a Java {@code double} from the {@linkplain AvailObject receiver}.
	 *
	 * @return A Java {@code double}.
	 */
	@ReferencedInGeneratedCode
	double extractDouble ();

	/**
	 * Extract a Java float from the {@linkplain AvailObject receiver}.
	 *
	 * @return A Java {@code float}.
	 */
	float extractFloat ();

	/**
	 * Extract a 32-bit signed Java {@code int} from the {@linkplain
	 * AvailObject receiver}.
	 *
	 * @return A 32-bit signed Java {@code int}.
	 */
	@ReferencedInGeneratedCode
	int extractInt ();

	/**
	 * Extract a 64-bit signed Java {@code long} from the {@linkplain
	 * AvailObject receiver}.
	 *
	 * @return A 64-bit signed Java {@code long}.
	 */
	long extractLong ();

	/**
	 * Extract an unsigned nybble from the {@linkplain AvailObject receiver}.
	 * Return it as a Java {@code byte}.
	 *
	 * @return A {@code byte} in the range [0..15].
	 */
	byte extractNybble ();

	/**
	 * Answer whether this number is numerically equal to some finite integer.
	 *
	 * @return A boolean indicating finiteness and a fractional part of zero.
	 */
	boolean isNumericallyIntegral ();

	/**
	 * Answer whether this integral {@linkplain InfinityDescriptor infinity} is
	 * positive.
	 *
	 * @return {@code true} if the receiver is positive integral infinity, or
	 *         {@code false} if the receiver is negative integral infinity.
	 *         No other values are permitted.
	 */
	boolean isPositive ();

	/**
	 * Answer whether the receiver is numerically less than or equivalent to
	 * the argument.
	 *
	 * @param another A {@linkplain AbstractNumberDescriptor numeric object}.
	 * @return Whether the receiver is less than or equivalent to the argument.
	 */
	boolean lessOrEqual (A_Number another);

	/**
	 * Answer whether the receiver is numerically less than the argument.
	 *
	 * @param another A {@linkplain AbstractNumberDescriptor numeric object}.
	 * @return Whether the receiver is strictly less than the argument.
	 */
	boolean lessThan (A_Number another);

	/**
	 * Subtract the argument {@code aNumber} from a receiver and answer
	 * the {@linkplain AvailObject result}.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * #subtractFromIntegerCanDestroy(AvailObject, boolean)
	 * subtractFromIntegerCanDestroy} or {@link
	 * #subtractFromInfinityCanDestroy(Sign, boolean)
	 * subtractFromInfinityCanDestroy}, where actual implementations of the
	 * subtraction operation should reside.</p>
	 *
	 * @param aNumber
	 *        An integral numeric.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of differencing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@ReferencedInGeneratedCode
	A_Number minusCanDestroy (A_Number aNumber, boolean canDestroy);

	/**
	 * Multiply the receiver by the given {@linkplain DoubleDescriptor
	 * double}, destroying one or the other if it's mutable and canDestroy is
	 * true.  Because of the requirement that the argument be a double rather
	 * than an arbitrary {@link A_Number}, this is usually only used for
	 * double-dispatching.
	 *
	 * @param doubleObject
	 *        The double to multiply the receiver by.
	 * @param canDestroy
	 *        Whether a mutable receiver or argument may be destroyed and/or
	 *        recycled to hold the product.
	 * @return The product, possibly recycling one of the inputs if
	 *         canDestroy is true.
	 */
	A_Number multiplyByDoubleCanDestroy (
		A_Number doubleObject,
		boolean canDestroy);

	/**
	 * Multiply the receiver by the given {@linkplain FloatDescriptor float},
	 * destroying one or the other if it's mutable and canDestroy is true.
	 * Because of the requirement that the argument be a float rather than an
	 * arbitrary {@link A_Number}, this is usually only used for
	 * double-dispatching.
	 *
	 * @param floatObject
	 *        The float to multiply the receiver by.
	 * @param canDestroy
	 *        Whether a mutable receiver or argument may be destroyed and/or
	 *        recycled to hold the product.
	 * @return The product, possibly recycling one of the inputs if
	 *         canDestroy is true.
	 */
	A_Number multiplyByFloatCanDestroy (
		A_Number floatObject,
		boolean canDestroy);

	/**
	 * Multiply the receiver by the {@linkplain InfinityDescriptor infinity}
	 * with the given {@linkplain Sign sign}, potentially destroying the
	 * receiver if it's mutable and canDestroy is true.
	 *
	 * @param sign
	 *        The sign of the infinity by which to multiply the receiver.
	 * @param canDestroy
	 *        Whether a mutable receiver may be destroyed and/or recycled to
	 *        hold the product.
	 * @return The product, possibly recycling the receiver if canDestroy is
	 *         true.
	 */
	A_Number multiplyByInfinityCanDestroy (Sign sign, boolean canDestroy);

	/**
	 * Multiply the receiver by the given {@linkplain IntegerDescriptor
	 * integer}, destroying one or the other if it's mutable and canDestroy is
	 * true.  Because of the requirement that the argument be an integer rather
	 * than an arbitrary {@link A_Number}, this is usually only used for
	 * double-dispatching.
	 *
	 * @param anInteger
	 *        The integer to multiply the receiver by.
	 * @param canDestroy
	 *        Whether a mutable receiver or argument may be destroyed and/or
	 *        recycled to hold the product.
	 * @return The product, possibly recycling one of the inputs if
	 *         canDestroy is true.
	 */
	A_Number multiplyByIntegerCanDestroy (
		AvailObject anInteger,
		boolean canDestroy);

	/**
	 * Divide the receiver by the argument {@code aNumber} and answer the
	 * {@linkplain AvailObject result}. The operation is not allowed to fail,
	 * so the caller must ensure that the arguments are valid, i.e. the divisor
	 * is not {@linkplain IntegerDescriptor#zero() zero}.
	 *
	 * @param aNumber
	 *        An integral numeric.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of dividing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	A_Number noFailDivideCanDestroy (
		A_Number aNumber,
		boolean canDestroy);

	/**
	 * Difference the receiver and the argument {@code aNumber} and answer the
	 * {@linkplain AvailObject result}. The operation is not allowed to fail,
	 * so the caller must ensure that the arguments are valid, i.e. not
	 * {@linkplain InfinityDescriptor infinities} of like sign.
	 *
	 * @param aNumber
	 *        An integral numeric.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of differencing the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	A_Number noFailMinusCanDestroy (
		A_Number aNumber,
		boolean canDestroy);

	/**
	 * Add the receiver and the argument {@code aNumber} and answer the
	 * {@linkplain AvailObject result}. The operation is not allowed to fail,
	 * so the caller must ensure that the arguments are valid, i.e. not
	 * {@linkplain InfinityDescriptor infinities} of unlike sign.
	 *
	 * @param aNumber
	 *        An integral numeric.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of adding the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	A_Number noFailPlusCanDestroy (
		A_Number aNumber,
		boolean canDestroy);

	/**
	 * Multiply the receiver and the argument {@code aNumber} and answer the
	 * {@linkplain AvailObject result}. The operation is not allowed to fail,
	 * so the caller must ensure that the arguments are valid, i.e. not
	 * {@linkplain IntegerDescriptor#zero() zero} and {@linkplain
	 * InfinityDescriptor infinity}.
	 *
	 * @param aNumber
	 *        An integral numeric.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of adding the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	A_Number noFailTimesCanDestroy (
		A_Number aNumber,
		boolean canDestroy);

	/**
	 * Answer an ordering between the receiver and the argument.  This compares
	 * the underlying real numeric values of the two {@link A_Number}s, which
	 * does not necessarily agree with the {@link #equals(A_BasicObject)}
	 * semantics.  In particular, under numerical ordering, 5 = 5.0 = 5.0f,
	 * and 0.0/0.0 is incomparable to every number, even itself.  Under ordinary
	 * equality (the {@link #equals(A_BasicObject)} method), an integer never
	 * equals a float, and neither ever equals a double.  However, 0.0/0.0 is
	 * equal to 0.0/0.0, since they have the same kind (double) and the same bit
	 * pattern.  Note that {@link #hash()} agrees with general equality, not the
	 * numeric ordering.
	 *
	 * <p>The numeric order is not directly exposed to Avail, but it can be
	 * reconstructed by computing the {@link P_LessOrEqual} predicate on
	 * two values and also on the two values interchanged.  If one but not the
	 * other is true, the order is either {@link Order#LESS} or {@link
	 * Order#MORE}.  If both are true, the values are {@link Order#EQUAL}, and
	 * if neither is true then the values are {@link Order#INCOMPARABLE}, which
	 * is only the case if one or both values are float or double not-a-numbers
	 * (easily produced via 0.0/0.0).
	 *
	 * @param another The value to numerically compare the receiver to.
	 * @return The numeric {@link Order} between the receiver and the argument.
	 */
	@ReferencedInGeneratedCode
	Order numericCompare (A_Number another);

	/**
	 * This produces the same value as {@link #numericCompare(A_Number)}, but
	 * the argument is known to be an {@linkplain InfinityDescriptor integral
	 * infinity} whose {@link Sign} is provided.
	 *
	 * @param sign The sign of the integral infinity to compare against.
	 * @return How the receiver compares to the specified infinity.
	 */
	Order numericCompareToInfinity (Sign sign);

	/**
	 * This produces the same value as {@link #numericCompare(A_Number)}, but
	 * the argument is known to be a {@linkplain DoubleDescriptor double} with
	 * the given unboxed value.
	 *
	 * @param aDouble The {@code double} to numerically compare against.
	 * @return How the receiver compares to the specified double.
	 */
	Order numericCompareToDouble (double aDouble);

	/**
	 * This produces the same value as {@link #numericCompare(A_Number)}, but
	 * the argument is known to be an {@linkplain IntegerDescriptor integer}.
	 *
	 * @param anInteger The {@code integer} to numerically compare against.
	 * @return How the receiver compares to the specified integer.
	 */
	Order numericCompareToInteger (AvailObject anInteger);

	/**
	 * Add the receiver and the argument {@code aNumber} and answer the
	 * {@linkplain AvailObject result}.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * #addToIntegerCanDestroy(AvailObject, boolean) addToIntegerCanDestroy} or
	 * {@link #addToInfinityCanDestroy(Sign, boolean)
	 * addToInfinityCanDestroy}, where actual implementations of the addition
	 * operation should reside.</p>
	 *
	 * @param aNumber
	 *        An integral numeric.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of adding the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	A_Number plusCanDestroy (
		A_Number aNumber,
		boolean canDestroy);

	/**
	 * Extract a (signed) base 2^32 digit from the integer.  The index must be
	 * in range for the integer's representation.
	 *
	 * @param index
	 *        The one-based, little-endian index of the digit to extract.  It
	 *        must be between 1 and the number of digits present.
	 * @return The base 2^32 digit as a signed {@code int}.
	 */
	int rawSignedIntegerAt (int index);

	/**
	 * Replace a (signed) base 2^32 digit of the integer.  The index must be
	 * in range for the integer's representation, and the receiver must be
	 * mutable.
	 *
	 * @param index
	 *        The one-based, little-endian index of the digit to replace.  It
	 *        must be between 1 and the number of digits present.
	 * @param value
	 *        The replacement base 2^32 digit as a signed {@code int}.
	 */
	void rawSignedIntegerAtPut (int index, int value);

	/**
	 * Extract an unsigned base 2^32 digit from the integer.  The index must be
	 * in range for the integer's representation.
	 *
	 * @param index
	 *        The one-based, little-endian index of the digit to extract.  It
	 *        must be between 1 and the number of digits present.
	 * @return The unsigned base 2^32 digit as a signed {@code long} to avoid
	 *         misinterpreting the sign.
	 */
	long rawUnsignedIntegerAt (int index);

	/**
	 * Replace an unsigned base 2^32 digit of the integer.  The index must be
	 * in range for the integer's representation, and the receiver must be
	 * mutable.
	 *
	 * @param index
	 *        The one-based, little-endian index of the digit to replace.  It
	 *        must be between 1 and the number of digits present.
	 * @param value
	 *        The replacement base 2^32 digit as an {@code int}.  This does the
	 *        same thing as {@link #rawSignedIntegerAtPut(int, int)}.
	 */
	void rawUnsignedIntegerAtPut (int index, int value);

	/**
	 * Subtract the receiver from the given {@linkplain DoubleDescriptor
	 * double}, destroying one or the other if it's mutable and canDestroy is
	 * true.  Because of the requirement that the argument be a double rather
	 * than an arbitrary {@link A_Number}, this is usually only used for
	 * double-dispatching.
	 *
	 * @param doubleObject
	 *        The double to subtract the receiver from.
	 * @param canDestroy
	 *        Whether a mutable receiver or argument may be destroyed and/or
	 *        recycled to hold the difference.
	 * @return The difference, possibly recycling one of the inputs if
	 *         canDestroy is true.
	 */
	A_Number subtractFromDoubleCanDestroy (
		A_Number doubleObject,
		boolean canDestroy);

	/**
	 * Subtract the receiver from the given {@linkplain FloatDescriptor float},
	 * destroying one or the other if it's mutable and canDestroy is true.
	 * Because of the requirement that the argument be a float rather than an
	 * arbitrary {@link A_Number}, this is usually only used for
	 * double-dispatching.
	 *
	 * @param floatObject
	 *        The float to subtract the receiver from.
	 * @param canDestroy
	 *        Whether a mutable receiver or argument may be destroyed and/or
	 *        recycled to hold the difference.
	 * @return The difference, possibly recycling one of the inputs if
	 *         canDestroy is true.
	 */
	A_Number subtractFromFloatCanDestroy (
		A_Number floatObject,
		boolean canDestroy);

	/**
	 * Subtract the receiver from the {@linkplain InfinityDescriptor infinity}
	 * with the specified {@linkplain Sign sign}, destroying one or the other if
	 * it's mutable and canDestroy is true.
	 *
	 * @param sign
	 *        The sign of the infinity to subtract from.
	 * @param canDestroy
	 *        Whether a mutable receiver or argument may be destroyed and/or
	 *        recycled to hold the difference.
	 * @return The difference, possibly recycling one of the inputs if
	 *         canDestroy is true.
	 */
	A_Number subtractFromInfinityCanDestroy (Sign sign, boolean canDestroy);

	/**
	 * Subtract the receiver from the given {@linkplain IntegerDescriptor
	 * integer}, destroying one or the other if it's mutable and canDestroy is
	 * true.
	 *
	 * @param anInteger
	 *        The integer to subtract from.
	 * @param canDestroy
	 *        Whether a mutable receiver or argument may be destroyed and/or
	 *        recycled to hold the difference.
	 * @return The difference, possibly recycling one of the inputs if
	 *         canDestroy is true.
	 */
	A_Number subtractFromIntegerCanDestroy (
		AvailObject anInteger,
		boolean canDestroy);

	/**
	 * Multiply the receiver and the argument {@code aNumber} and answer the
	 * {@linkplain AvailObject result}.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * #multiplyByIntegerCanDestroy(AvailObject, boolean)
	 * multiplyByIntegerCanDestroy} or {@linkplain
	 * #multiplyByInfinityCanDestroy(Sign, boolean)
	 * multiplyByInfinityCanDestroy}, where actual implementations of the
	 * multiplication operation should reside.</p>
	 *
	 * @param aNumber
	 *        An integral numeric.
	 * @param canDestroy
	 *        {@code true} if the operation may modify either {@linkplain
	 *        AvailObject operand}, {@code false} otherwise.
	 * @return The {@linkplain AvailObject result} of multiplying the operands.
	 * @see IntegerDescriptor
	 * @see InfinityDescriptor
	 */
	@ReferencedInGeneratedCode
	A_Number timesCanDestroy (A_Number aNumber, boolean canDestroy);

	/**
	 * Normalize the integer to have the minimum number of base 2^32 digits.
	 */
	void trimExcessInts ();

	/**
	 * Determine if the receiver is an Avail integer equivalent to the specified
	 * Java {@code int}.  Note that a non-integer should simply answer false,
	 * not fail.  This operation was placed in A_Number for organizational
	 * reasons, not type restriction.
	 *
	 * @param theInt The Java int to compare against.
	 * @return Whether the receiver represents that integer.
	 */
	boolean equalsInt (int theInt);
}
