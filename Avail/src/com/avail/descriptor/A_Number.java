/**
 * A_Number.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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
	 * Answer whether the receiver is numerically less than the argument.
	 *
	 * @param another A {@linkplain AbstractNumberDescriptor numeric object}.
	 * @return Whether the receiver is strictly less than the argument.
	 */
	boolean lessThan (A_Number another);

	/**
	 * Answer whether the receiver is numerically less than or equivalent to
	 * the argument.
	 *
	 * @param another A {@linkplain AbstractNumberDescriptor numeric object}.
	 * @return Whether the receiver is less than or equivalent to the argument.
	 */
	boolean lessOrEqual (A_Number another);

	/**
	 * @param another
	 * @return
	 */
	Order numericCompare (A_Number another);

	/**
	 * @param sign
	 * @return
	 */
	Order numericCompareToInfinity (Sign sign);

	/**
	 * @param aDouble
	 * @return
	 */
	Order numericCompareToDouble (double aDouble);

	/**
	 * @param anInteger
	 * @return
	 */
	Order numericCompareToInteger (A_Number anInteger);

	/**
	 * @param doubleObject
	 * @param canDestroy
	 * @return
	 */
	A_Number addToDoubleCanDestroy (
		A_Number doubleObject,
		boolean canDestroy);

	/**
	 * @param floatObject
	 * @param canDestroy
	 * @return
	 */
	A_Number addToFloatCanDestroy (
		A_Number floatObject,
		boolean canDestroy);

	/**
	 * @param doubleObject
	 * @param canDestroy
	 * @return
	 */
	A_Number subtractFromDoubleCanDestroy (
		A_Number doubleObject,
		boolean canDestroy);

	/**
	 * @param floatObject
	 * @param canDestroy
	 * @return
	 */
	A_Number subtractFromFloatCanDestroy (
		A_Number floatObject,
		boolean canDestroy);

	/**
	 * @param doubleObject
	 * @param canDestroy
	 * @return
	 */
	A_Number multiplyByDoubleCanDestroy (
		A_Number doubleObject,
		boolean canDestroy);

	/**
	 * @param floatObject
	 * @param canDestroy
	 * @return
	 */
	A_Number multiplyByFloatCanDestroy (
		A_Number floatObject,
		boolean canDestroy);

	/**
	 * @param doubleObject
	 * @param canDestroy
	 * @return
	 */
	A_Number divideIntoDoubleCanDestroy (
		A_Number doubleObject,
		boolean canDestroy);

	/**
	 * @param floatObject
	 * @param canDestroy
	 * @return
	 */
	A_Number divideIntoFloatCanDestroy (
		A_Number floatObject,
		boolean canDestroy);

	/**
	 * Divide the receiver by the argument {@code aNumber} and answer the
	 * {@linkplain AvailObject result}.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * #divideIntoIntegerCanDestroy(A_Number, boolean)
	 * divideIntoIntegerCanDestroy} or {@link
	 * #divideIntoInfinityCanDestroy(Sign, boolean)
	 * divideIntoInfinityCanDestroy}, where actual implementations of the
	 * division operation should reside.</p>
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
	A_Number divideCanDestroy (
		A_Number aNumber,
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
	 * Subtract the argument {@code aNumber} from a receiver and answer
	 * the {@linkplain AvailObject result}.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * #subtractFromIntegerCanDestroy(A_Number, boolean)
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
	A_Number minusCanDestroy (
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
	 * {@linkplain AvailObject result}.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * #addToIntegerCanDestroy(A_Number, boolean) addToIntegerCanDestroy} or
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
	 * {@linkplain AvailObject result}.
	 *
	 * <p>Implementations may double-dispatch to {@link
	 * #multiplyByIntegerCanDestroy(A_Number, boolean)
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
	A_Number timesCanDestroy (
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
	 * @return
	 */
	byte extractSignedByte ();

	/**
	 * @return
	 */
	short extractSignedShort ();

	/**
	 * Dispatch to the descriptor.
	 */
	short extractUnsignedByte ();

	/**
	 * Dispatch to the descriptor.
	 */
	double extractDouble ();

	/**
	 * Dispatch to the descriptor.
	 */
	float extractFloat ();

	/**
	 * Dispatch to the descriptor.
	 */
	int extractInt ();

	/**
	 * Extract a 64-bit signed Java {@code long} from the {@linkplain
	 * AvailObject receiver}.
	 *
	 * @return A 64-bit signed Java {@code long}
	 * @author Todd L Smith &lt;todd@availlang.org&gt;
	 */
	long extractLong ();

	/**
	 * Dispatch to the descriptor.
	 */
	byte extractNybble ();

	/**
	 * @param sign
	 * @param canDestroy
	 * @return
	 */
	A_Number addToInfinityCanDestroy (Sign sign, boolean canDestroy);

	/**
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	A_Number addToIntegerCanDestroy (A_Number anInteger, boolean canDestroy);

	/**
	 * @param sign
	 * @param canDestroy
	 * @return
	 */
	A_Number divideIntoInfinityCanDestroy (Sign sign, boolean canDestroy);

	/**
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	A_Number divideIntoIntegerCanDestroy (
		A_Number anInteger,
		boolean canDestroy);

	/**
	 * @param sign
	 * @param canDestroy
	 * @return
	 */
	A_Number subtractFromInfinityCanDestroy (Sign sign, boolean canDestroy);

	/**
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	A_Number subtractFromIntegerCanDestroy (
		A_Number anInteger,
		boolean canDestroy);

	/**
	 * @param sign
	 * @param canDestroy
	 * @return
	 */
	A_Number multiplyByInfinityCanDestroy (Sign sign, boolean canDestroy);

	/**
	 * @param anInteger
	 * @param canDestroy
	 * @return
	 */
	A_Number multiplyByIntegerCanDestroy (
		A_Number anInteger,
		boolean canDestroy);

	/**
	 * Dispatch to the descriptor.
	 */
	int rawSignedIntegerAt (int index);

	/**
	 * Dispatch to the descriptor.
	 */
	void rawSignedIntegerAtPut (int index, int value);

	/**
	 * Dispatch to the descriptor.
	 */
	long rawUnsignedIntegerAt (int index);

	/**
	 * Dispatch to the descriptor.
	 */
	void rawUnsignedIntegerAtPut (int index, int value);
}
