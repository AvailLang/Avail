/*
 * P_BitShiftRight.kt
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

package avail.interpreter.primitive.integers

import avail.descriptor.functions.A_RawFunction
import avail.descriptor.numbers.A_Number
import avail.descriptor.numbers.A_Number.Companion.bitShift
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.greaterOrEqual
import avail.descriptor.numbers.A_Number.Companion.greaterThan
import avail.descriptor.numbers.A_Number.Companion.lessThan
import avail.descriptor.numbers.A_Number.Companion.minusCanDestroy
import avail.descriptor.numbers.InfinityDescriptor.Companion.negativeInfinity
import avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.numbers.IntegerDescriptor.Companion.negativeOne
import avail.descriptor.numbers.IntegerDescriptor.Companion.one
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integerRangeType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integers
import avail.exceptions.ArithmeticException
import avail.exceptions.AvailErrorCode.E_TOO_LARGE_TO_REPRESENT
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operation.L2_BIT_LOGIC_OP
import avail.interpreter.levelTwo.operation.L2_MOVE
import avail.optimizer.L1Translator
import avail.utility.notNullAnd

/**
 * **Primitive:** Given any integer B, and a shift factor S, compute
 * ⌊B÷2<sup>S</sup>⌋.  This is the right-shift operation, but when S is negative
 * it acts as a left-shift.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_BitShiftRight : Primitive(2, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val baseInteger = interpreter.argument(0)
		val shiftFactor = interpreter.argument(1)
		return try
		{
			interpreter.primitiveSuccess(
				baseInteger.bitShift(
					zero.minusCanDestroy(shiftFactor, true),
					true))
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
			shiftFactors.lowerBound.greaterOrEqual(zero) ->
			{
				// It's always a right shift by a non-negative amount, so it
				// can't exceed the limit if the base wasn't already in
				// violation.
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
		val lowBase = baseIntegers.lowerBound
		val highBase = baseIntegers.upperBound
		val leastRightShift = shiftFactors.lowerBound
		val mostRightShift = shiftFactors.upperBound
		val mostLeftShift = zero.minusCanDestroy(leastRightShift, false)
		val leastLeftShift = zero.minusCanDestroy(mostRightShift, false)
		if (baseIntegers.isSubtypeOf(inclusive(negativeOne, zero)))
		{
			// Shifting 0 or -1 by any finite amount, left or right, should
			// have no effect on the value.
			return baseIntegers
		}
		// Shifting is monotonic, so calculate the four potential boundaries and
		// use [min, max] of them, excluding infinities.  Also include whichever
		// of the fixed points {0, -1} are present.
		val bounds = mutableListOf<A_Number>()
		if (zero.isInstanceOf(baseIntegers)) bounds.add(zero)
		if (negativeOne.isInstanceOf(baseIntegers)) bounds.add(negativeOne)
		// Deal with the negatives below -1.
		if (lowBase.lessThan(negativeOne()))
		{
			// There are values < -1, which can grow in magnitude under shifts.
			// If the left shift would be huge, estimate it as -∞ instead.
			bounds.add(
				if (mostLeftShift.greaterThan(fromInt(64))) negativeInfinity
				else lowBase.bitShift(mostLeftShift, false))
			// Now find the negative output with least magnitude.
			val highBaseBelowNegativeOne =
				if (highBase.lessThan(negativeOne())) highBase
				else fromInt(-2)
			bounds.add(
				if (mostRightShift.equals(positiveInfinity)) negativeOne
				else highBaseBelowNegativeOne.bitShift(leastLeftShift, false))
		}
		// Now for the strictly positives (>0).
		if (highBase.greaterThan(zero))
		{
			// There are values > 0, which can grow in magnitude under shifts.
			// If the left shift would be huge, estimate it as ∞ instead.
			bounds.add(
				if (mostLeftShift.greaterThan(fromInt(64))) positiveInfinity
				else highBase.bitShift(mostLeftShift, false))
			// Now find the positive output with least magnitude.
			val lowBaseAboveZero =
				if (lowBase.greaterThan(zero)) lowBase
				else one
			bounds.add(
				if (mostRightShift.equals(positiveInfinity)) zero
				else lowBaseAboveZero.bitShift(leastLeftShift, false))
		}
		val min = bounds.reduce { a, b -> if (a.lessThan(b)) a else b }
		val max = bounds.reduce { a, b -> if (a.greaterThan(b)) a else b }
		return integerRangeType(min, min.isFinite, max, max.isFinite)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(integers, integers), integers)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_TOO_LARGE_TO_REPRESENT))

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: L1Translator.CallSiteHelper
	): Boolean = attemptToGenerateTwoIntToIntPrimitive(
		callSiteHelper,
		functionToCallReg,
		rawFunction,
		arguments,
		argumentTypes,
		ifOutputIsInt = {
			val outputType = intWrite.restriction().type
			when
			{
				outputType.lowerBound.equals(outputType.upperBound) ->
				{
					// The resulting value is known precisely.
					generator.moveRegister(
						L2_MOVE.unboxedInt,
						generator.unboxedIntConstant(
							outputType.lowerBound.extractInt
						).semanticValue(),
						intWrite.semanticValues())
				}
				intA.type().isSubtypeOf(inclusive(-1, 0)) ||
					intB.type().isSubtypeOf(inclusive(0, 0)) ->
				{
					// Either:
					//   1. The base is always in [-1, 0], so the shift, whether
					//      left or right, has no effect, or
					//   2. The shift is always zero, likewise having no effect.
					generator.moveRegister(
						L2_MOVE.unboxedInt,
						intA.semanticValue(),
						intWrite.semanticValues())
				}
				intB.type().isSubtypeOf(inclusive(0, 31)) ->
				{
					// The shift is in [0..31], so the JVM can directly handle
					// it.
					generator.addInstruction(
						L2_BIT_LOGIC_OP.bitwiseSignedShiftRight,
						intA,
						intB,
						intWrite)
				}
				intB.constantOrNull().notNullAnd { extractInt in -31..0 } ->
				{
					// The shift is a constant in [-31..0], so we can convert it
					// to a constant left shift that the JVM can handle.
					generator.addInstruction(
						L2_BIT_LOGIC_OP.bitwiseShiftLeft,
						intA,
						generator.unboxedIntConstant(
							0 - intB.constantOrNull()!!.extractInt),
						intWrite)
				}
				else ->
				{
					// This is already a rare situation, so just fall back, even
					// though we know the value would fit in an i32.  If we ever
					// need to optimize the remaining case, we'll have to emit
					// tests for the shift factors falling into [MIN_INT..-32],
					// [-31..-1], [0..31], and [32..MAX_INT], and generate
					// separate code to handle each reachable case separately.
					generator.jumpTo(this.intFailure)
				}
			}
		},
		ifOutputIsPossiblyInt = {
			// Fall back completely if the shift could overflow an i32.
			generator.jumpTo(intFailure)
		})
}
