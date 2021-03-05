/*
 * P_BitwiseAnd.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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
import com.avail.descriptor.numbers.A_Number.Companion.bitwiseAnd
import com.avail.descriptor.numbers.A_Number.Companion.extractLong
import com.avail.descriptor.numbers.A_Number.Companion.greaterOrEqual
import com.avail.descriptor.numbers.IntegerDescriptor
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromLong
import com.avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.lowerBound
import com.avail.descriptor.types.A_Type.Companion.upperBound
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integerRangeType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integers
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.singleInt
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.CannotFail
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operation.L2_BIT_LOGIC_OP
import com.avail.optimizer.L1Translator
import kotlin.math.min

/**
 * **Primitive:** Compute the bitwise AND of the [arguments][IntegerDescriptor].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_BitwiseAnd : Primitive(2, CannotFail, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val a = interpreter.argument(0)
		val b = interpreter.argument(1)
		return interpreter.primitiveSuccess(a.bitwiseAnd(b, true))
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction, argumentTypes: List<A_Type>): A_Type
	{
		assert(argumentTypes.size == 2)
		val aRange = argumentTypes[0]
		val bRange = argumentTypes[1]

		// If either value is constrained to a positive range, then at least
		// guarantee the bit-wise and can't be greater than or equal to the next
		// higher power of two of that range's upper bound.
		val upper: Long =
			if (aRange.lowerBound().greaterOrEqual(zero)
			    && aRange.upperBound().isLong)
			{
				if (bRange.lowerBound().greaterOrEqual(zero)
				    && bRange.upperBound().isLong)
				{
					min(
						aRange.upperBound().extractLong(),
						bRange.upperBound().extractLong())
				}
				else
				{
					aRange.upperBound().extractLong()
				}
			}
			else if (bRange.lowerBound().greaterOrEqual(zero)
			         && bRange.upperBound().isLong)
			{
				bRange.upperBound().extractLong()
			}
			else
			{
				// Give up, as the result may be negative or exceed a long.
				return super.returnTypeGuaranteedByVM(
					rawFunction, argumentTypes)
			}
		// At least one value is positive, so the result is positive.
		// At least one is a long, so the result must be a long.
		val highOneBit = java.lang.Long.highestOneBit(upper)
		if (highOneBit == 0L)
		{
			// One of the ranges is constrained to be exactly zero.
			return singleInt(0)
		}
		val maxValue = highOneBit - 1 or highOneBit
		return integerRangeType(zero, true, fromLong(maxValue), true)
	}

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: L1Translator.CallSiteHelper
	): Boolean = L2_BIT_LOGIC_OP.bitwiseAnd.generateBinaryIntOperation(
		arguments,
		argumentTypes,
		callSiteHelper,
		typeGuaranteeFunction = {
			restrictedArgTypes ->
			returnTypeGuaranteedByVM(rawFunction, restrictedArgTypes)
		},
		fallbackBody = {
			generateGeneralFunctionInvocation(
				functionToCallReg, arguments, false, callSiteHelper)
		})

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(integers, integers), integers)
}
