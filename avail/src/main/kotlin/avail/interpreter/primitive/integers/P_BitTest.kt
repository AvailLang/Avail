/*
 * P_BitTest.kt
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

import avail.descriptor.atoms.AtomDescriptor.Companion.falseObject
import avail.descriptor.atoms.AtomDescriptor.Companion.objectFromBoolean
import avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.numbers.A_Number.Companion.bitShift
import avail.descriptor.numbers.A_Number.Companion.bitTest
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.isInt
import avail.descriptor.numbers.A_Number.Companion.minusCanDestroy
import avail.descriptor.numbers.IntegerDescriptor.Companion.intCount
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.lowerInclusive
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.A_Type.Companion.upperInclusive
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.falseType
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.trueType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.u1
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.intRestrictionForType
import avail.interpreter.levelTwo.operation.L2_BIT_LOGIC_OP
import avail.interpreter.levelTwo.operation.NumericComparator
import avail.optimizer.L1Translator
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2Generator.Companion.edgeTo
import avail.optimizer.values.L2SemanticUnboxedInt
import kotlin.math.min

/**
 * **Primitive:** Given an integer and a whole number, use the whole number to
 * index a bit from the integer's 2's complement representation, and return it
 * as a [boolean][booleanType].
 *
 * Bit zero tests the low (2^0) bit.  Bit tests beyond the positions explicitly
 * allocated for the integer answer the sign bit, as 2's complement implies.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_BitTest : Primitive(2, CannotFail, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val a = interpreter.argument(0)
		val b = interpreter.argument(1)

		val bInt = when
		{
			b.isInt -> b.extractInt
			else -> Integer.MAX_VALUE
		}
		return interpreter.primitiveSuccess(objectFromBoolean(a.bitTest(bInt)))
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction, argumentTypes: List<A_Type>): A_Type
	{
		assert(argumentTypes.size == 2)
		val (aRange, bRange) = argumentTypes

		if (!aRange.lowerInclusive || !aRange.upperInclusive)
		{
			// It can be either arbitrarily large or arbitrarily small, or both.
			// Therefore, the bit position at *any* finite index will vary.
			return booleanType
		}
		// If the integer's bounds look the same at and to the left of the
		// lowest potentially tested bit, then we know the range won't have
		// any overflows into the bit, which means it's constant.
		val bLowValue = bRange.lowerBound
		val bLowValueNeg = zero.minusCanDestroy(bLowValue, false)
		val shiftedLow = aRange.lowerBound.bitShift(bLowValueNeg, false)
		val shiftedHigh = aRange.upperBound.bitShift(bLowValueNeg, false)
		if (shiftedLow.equals(shiftedHigh))
		{
			// The ranges look the same at and to the left of the rightmost bit
			// that could be queried.  See if the bits that might be accessed
			// are all the same.
			// We want to know if all the accessible bits are the same.
			val firstBit = shiftedLow.bitTest(0)
			if (!bLowValue.equals(bRange.upperBound))
			{
				// Check the other potential bits.  No need to check bits left
				// of the shifted representation.
				val limit = intCount(shiftedLow.traversed()) shl 5
				val maxShiftedBit =
					bRange.upperBound.minusCanDestroy(bLowValue, false)
				val maxShiftedBitInt =
					if (maxShiftedBit.isInt) maxShiftedBit.extractInt
					else limit
				for (i in 1 .. min(maxShiftedBitInt, limit))
				{
					if (shiftedLow.bitTest(i) != firstBit) return booleanType
				}
			}
			// The potentially addressed bits of a all have the same value.
			return if (firstBit) trueType else falseType
		}
		// Counting up from the low integer to the high integer had to cause the
		// low potentially accessed bit to change at least once.
		return booleanType
	}

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: L1Translator.CallSiteHelper
	): Boolean
	{
		val (a, b) = arguments
		val (aType, bType) = argumentTypes

		// Only bother with specialized code if we know the values are int32's.
		if (aType.typeIntersection(i32).isBottom
			|| bType.typeIntersection(inclusive(0, 31)).isBottom)
		{
			// One of the arguments is never within range, so fall back.
			return false
		}
		val translator = callSiteHelper.translator
		val generator = callSiteHelper.generator
		val fallback = L2BasicBlock("fallback for bit test")
		val aInt = generator.readInt(
			L2SemanticUnboxedInt(a.semanticValue()), fallback)
		val bInt = generator.readInt(
			L2SemanticUnboxedInt(b.semanticValue()), fallback)
		// Fall back if bInt is > 31.  We already know it's non-negative.
		val inRange = L2BasicBlock("bit position is in 0..31")
		NumericComparator.LessOrEqual.compareAndBranchInt(
			generator,
			bInt,
			generator.unboxedIntConstant(31),
			edgeTo(inRange),
			edgeTo(fallback))
		generator.startBlock(inRange)
		val shifted: L2ReadIntOperand = if (bType.upperBound.equals(zero))
		{
			// No need to shift it.
			aInt
		}
		else
		{
			val shiftedWrite = generator.intWriteTemp(
				intRestrictionForType(i32))
			generator.addInstruction(
				L2_BIT_LOGIC_OP.bitwiseSignedShiftRight,
				aInt,
				bInt,
				shiftedWrite)
			L2ReadIntOperand(
				shiftedWrite.pickSemanticValue(),
				shiftedWrite.restriction(),
				generator.currentManifest)
		}
		val maskedWrite = generator.intWriteTemp(
			intRestrictionForType(u1))
		generator.addInstruction(
			L2_BIT_LOGIC_OP.bitwiseAnd,
			shifted,
			generator.unboxedIntConstant(1),
			maskedWrite)
		val isZeroLabel = generator.createBasicBlock("bit is zero")
		val isOneLabel = generator.createBasicBlock("bit is one")
		NumericComparator.Equal.compareAndBranchInt(
			generator,
			L2ReadIntOperand(
				maskedWrite.pickSemanticValue(),
				maskedWrite.restriction(),
				generator.currentManifest),
			generator.unboxedIntConstant(0),
			edgeTo(isZeroLabel),
			edgeTo(isOneLabel))
		generator.startBlock(isZeroLabel)
		callSiteHelper.useAnswer(
			translator.generator.boxedConstant(falseObject))
		generator.startBlock(isOneLabel)
		callSiteHelper.useAnswer(
			translator.generator.boxedConstant(trueObject))
		generator.startBlock(fallback)
		// If the fallback is reachable, return false to indicate general
		// infallible primitive invocation code should be generated for it,
		// otherwise return true to suppress this.
		return !generator.currentlyReachable()
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				integers,
				wholeNumbers),
			booleanType)
}
