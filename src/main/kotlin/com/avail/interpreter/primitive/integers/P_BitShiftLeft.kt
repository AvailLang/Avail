/*
 * P_BitShiftLeft.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.interpreter.primitive.integers

import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integerRangeType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integers
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import com.avail.exceptions.ArithmeticException
import com.avail.exceptions.AvailErrorCode.E_TOO_LARGE_TO_REPRESENT
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Given any integer B, and a shift factor S, compute
 * ⌊B×2<sup>S</sup>⌋.  This is the left-shift operation, but when S is negative
 * it acts as a right-shift.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_BitShiftLeft : Primitive(2, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val baseInteger = interpreter.argument(0)
		val shiftFactor = interpreter.argument(1)
		return try
		{
			interpreter.primitiveSuccess(
				baseInteger.bitShift(shiftFactor, true))
		}
		catch (e: ArithmeticException)
		{
			interpreter.primitiveFailure(e)
		}
	}

	override fun fallibilityForArgumentTypes(
		argumentTypes: List<A_Type>
	): Fallibility
	{
		val (_, shiftFactors) = argumentTypes
		return when
		{
			shiftFactors.upperBound().lessOrEqual(zero()) ->
			{
				// A left shift by a non-positive amount is a right shift by a
				// non-negative amount, so it can't exceed the limit if the base
				// wasn't already in violation.
				Fallibility.CallSiteCannotFail
			}
			else -> super.fallibilityForArgumentTypes(argumentTypes)
		}
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>
	): A_Type
	{
		val (baseIntegers: A_Type, shiftFactors: A_Type) = argumentTypes
		val leastShift = shiftFactors.lowerBound()
		return when
		{
			shiftFactors.upperBound().equals(leastShift) ->
			{
				// Shifting by a constant amount is a common case.
				integerRangeType(
					baseIntegers.lowerBound().bitShift(leastShift, false),
					baseIntegers.lowerInclusive(),
					baseIntegers.upperBound().bitShift(leastShift, false),
					baseIntegers.upperInclusive())
			}
			baseIntegers.lowerBound().greaterOrEqual(zero()) ->
			{
				// Be conservative for simplicity.
				wholeNumbers
			}
			else -> super.returnTypeGuaranteedByVM(rawFunction, argumentTypes)
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(integers, integers), integers)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_TOO_LARGE_TO_REPRESENT))
}
