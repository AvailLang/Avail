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
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.A_Set.Companion.setSize
import avail.descriptor.sets.A_Set.Companion.setWithElementCanDestroy
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.instances
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
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.BitOperation.Shl
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.BitOperation.Shr
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive2
import avail.optimizer.CallSiteHelper
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
object P_BitShiftRight : Primitive2(CanFold, CanInline)
{
	override fun attempt2(
		interpreter: Interpreter,
		arg1: AvailObject,
		arg2: AvailObject
	): A_BasicObject?
	{
		val baseInteger = arg1
		val shiftFactor = arg2
		try
		{
			return baseInteger.bitShift(
				zero.minusCanDestroy(shiftFactor, true),
				true)
		}
		catch (e: ArithmeticException)
		{
			return interpreter.fail(e.errorCode)
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
		rawFunction: A_RawFunction?,
		argumentTypes: List<A_Type>
	): A_Type
	{
		val (baseIntegers: A_Type, shiftFactors: A_Type) = argumentTypes
		if (baseIntegers.isEnumeration
			&& shiftFactors.isEnumeration)
		{
			val bases = baseIntegers.instances
			val rightShifts = shiftFactors.instances
			// If there are sufficiently few combinations, compute them all.
			if (bases.setSize.toLong() * rightShifts.setSize.toLong() <= 256L)
			{
				var results = emptySet
				rightShifts.forEach { rightShift ->
					val leftShift = zero.minusCanDestroy(rightShift, false)
					bases.forEach { base ->
						results = results.setWithElementCanDestroy(
							base.bitShift(leftShift, false), true)
					}
				}
				return enumerationWith(results)
			}
		}
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

	override fun L1Translator.tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: CallSiteHelper
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
					moveIntRegister(
						unboxedIntConstant(
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
					moveIntRegister(
						intA.semanticValue(), intWrite.semanticValues())
				}
				intB.type().isSubtypeOf(inclusive(0, 31)) ->
				{
					// The shift is in [0..31], so the JVM can directly handle
					// it.
					+L2_BIT_LOGIC_OP(Shr, intA, intB, intWrite)
				}
				intB.constantOrNull.notNullAnd { extractInt in -31..0 } ->
				{
					// The shift is a constant in [-31..0], so we can convert it
					// to a constant left shift that the JVM can handle.
					+L2_BIT_LOGIC_OP(
						Shl,
						intA,
						unboxedIntConstant(-intB.constantOrNull!!.extractInt),
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
					jumpTo(this.intFailure)
				}
			}
		},
		ifOutputIsPossiblyInt = {
			// Fall back completely if the shift could overflow an i32.
			jumpTo(intFailure)
		})

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(integers, integers), integers)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_TOO_LARGE_TO_REPRESENT))

	override val semanticInfixOperatorString: String? get() = "Shr"
}
