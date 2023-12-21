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
import avail.descriptor.numbers.A_Number.Companion.bitShift
import avail.descriptor.numbers.A_Number.Companion.greaterOrEqual
import avail.descriptor.numbers.A_Number.Companion.minusCanDestroy
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.lowerInclusive
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.A_Type.Companion.upperInclusive
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integerRangeType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.exceptions.ArithmeticException
import avail.exceptions.AvailErrorCode.E_TOO_LARGE_TO_REPRESENT
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_INT_FLAG
import avail.interpreter.levelTwo.operation.L2_BIT_LOGIC_OP
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_COMPARE_INT
import avail.optimizer.L1Translator
import avail.optimizer.L2Generator.Companion.edgeTo
import avail.optimizer.values.L2SemanticUnboxedInt
import avail.optimizer.values.L2SemanticValue.Companion.primitiveInvocation

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
		val leastShift = shiftFactors.lowerBound
		return when
		{
			shiftFactors.upperBound.equals(leastShift) ->
			{
				// Shifting by a constant amount is a common case.
				val negatedShift = zero.minusCanDestroy(leastShift, false)
				integerRangeType(
					baseIntegers.lowerBound.bitShift(negatedShift, false),
					baseIntegers.lowerInclusive,
					baseIntegers.upperBound.bitShift(negatedShift, false),
					baseIntegers.upperInclusive)
			}
			baseIntegers.lowerBound.greaterOrEqual(zero) ->
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

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: L1Translator.CallSiteHelper): Boolean
	{
		val (a, b) = arguments
		val (aType, bType) = argumentTypes

		// If either of the argument types does not intersect with int32, then
		// fall back to the primitive invocation.
		val aIntersectInt32 = aType.typeIntersection(i32)
		val bIntersectInt32 = bType.typeIntersection(i32)
		if (aIntersectInt32.isBottom || bIntersectInt32.isBottom)
		{
			return false
		}
		if (!bType.isSubtypeOf(wholeNumbers))
		{
			// Right shift of an int32 is also always an int32.  If it could be
			// a left shift, then fall back.  In theory we could refine this to
			// check if overflows from a left shift are actually possible.
			return false
		}

		// Attempt to unbox the arguments.
		val generator = translator.generator
		val fallback = generator.createBasicBlock(
			"fall back to boxed right-shift")
		val fallbackToLargeRightShift = generator.createBasicBlock(
			"fall back to large right-shift (to 0 or -1)")
		val intA = generator.readInt(
			L2SemanticUnboxedInt(a.semanticValue()), fallback)
		// B is non-negative here, so a non-int32 here means it's > MAX_INT.
		val intB = generator.readInt(
			L2SemanticUnboxedInt(b.semanticValue()), fallbackToLargeRightShift)
		val semanticTemp = primitiveInvocation(
			this, listOf(a.semanticValue(), b.semanticValue()))
		val returnTypeIfInts = returnTypeGuaranteedByVM(
			rawFunction,
			listOf(aIntersectInt32, bIntersectInt32))
		val tempIntWriter = generator.intWrite(
			setOf(L2SemanticUnboxedInt(semanticTemp)),
			restrictionForType(returnTypeIfInts, UNBOXED_INT_FLAG))
		if (generator.currentlyReachable())
		{
			// The happy path is reachable.  Generate the most efficient
			// available unboxed arithmetic.  Java shifts ignore the high bits
			// of the shift amount, so we compensate if necessary.
			val smallShift = generator.createBasicBlock("shift is small")
			L2_JUMP_IF_COMPARE_INT.greaterOrEqual.compareAndBranch(
				generator,
				intB,
				generator.unboxedIntConstant(32),
				edgeTo(fallbackToLargeRightShift),
				edgeTo(smallShift))
			generator.startBlock(smallShift)
			if (generator.currentlyReachable())
			{
				generator.addInstruction(
					L2_BIT_LOGIC_OP.bitwiseSignedShiftRight,
					intA,
					intB,
					tempIntWriter)
				callSiteHelper.useAnswer(generator.readBoxed(semanticTemp))
			}
		}
		generator.startBlock(fallbackToLargeRightShift)
		if (generator.currentlyReachable())
		{
			// Shifting an int32 right by 31 replicates the sign bit into
			// all of the bits.
			generator.addInstruction(
				L2_BIT_LOGIC_OP.bitwiseSignedShiftRight,
				intA,
				generator.unboxedIntConstant(31),
				tempIntWriter)
			callSiteHelper.useAnswer(generator.readBoxed(semanticTemp))
		}
		generator.startBlock(fallback)
		if (generator.currentlyReachable())
		{
			// Fall back to the slower approach.
			translator.generateGeneralFunctionInvocation(
				functionToCallReg, arguments, false, callSiteHelper)
		}
		return true
	}
}
