/*
 * P_BitwiseXor.kt
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

import avail.descriptor.numbers.InfinityDescriptor.Companion.negativeInfinity
import avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import avail.descriptor.numbers.IntegerDescriptor
import avail.descriptor.numbers.IntegerDescriptor.Companion.negativeOne
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Number.Companion.bitwiseXor
import avail.descriptor.representation.A_Number.Companion.equalsInt
import avail.descriptor.representation.A_Number.Companion.extractLong
import avail.descriptor.representation.A_Number.Companion.greaterOrEqual
import avail.descriptor.representation.A_Number.Companion.lessThan
import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.isSubtypeOf
import avail.descriptor.representation.A_Type.Companion.lowerBound
import avail.descriptor.representation.A_Type.Companion.upperBound
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i64
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integerRangeType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integers
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operation.L2_MOVE_BOXED
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.BitOperation.Xor
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive2
import avail.optimizer.CallSiteHelper
import avail.optimizer.L1Translator
import avail.optimizer.L2GeneratorInterface
import avail.utility.notNullAnd
import kotlin.math.min

/**
 * **Primitive:** Compute the bitwise EXCLUSIVE OR of the
 * [arguments][IntegerDescriptor].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_BitwiseXor : Primitive2(CannotFail, CanFold, CanInline)
{
	override fun Interpreter.attempt2(
		arg1: AvailObject,
		arg2: AvailObject
	): A_BasicObject?
	{
		val a = arg1
		val b = arg2
		return a.bitwiseXor(b, true)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(integers, integers), integers)

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction?,
		argumentTypes: List<A_Type>
	): A_Type
	{
		assert(argumentTypes.size == 2)
		val (aRange, bRange) = argumentTypes

		val neg1 = aRange.lowerBound.lessThan(zero)
		val pos1 = aRange.upperBound.greaterOrEqual(zero)
		val neg2 = bRange.lowerBound.lessThan(zero)
		val pos2 = bRange.upperBound.greaterOrEqual(zero)
		val canBeNegative = (neg1 and pos2) || (neg2 and pos1)
		val canBePositive = (neg1 and neg2) || (pos1 and pos2)
		// While we could produce a suitable theory and algorithm for computing
		// the tight bounds for xor, most of the time there's only real benefit
		// in knowing how many bits the output's representation will take.  If
		// the inputs are both longs (i64), compute this.
		if (aRange.isSubtypeOf(i64) && bRange.isSubtypeOf(i64))
		{
			// It fits in a long, so try to refine it.  Use longs for the
			// boundaries for simplicity.
			val low1 = aRange.lowerBound.extractLong
			val high1 = aRange.upperBound.extractLong
			val low2 = bRange.lowerBound.extractLong
			val high2 = bRange.upperBound.extractLong

			// Calculate how many unused leading bits they have.
			fun Long.lead() = xor(shr(63)).countLeadingZeroBits()
			val lead1 = min(low1.lead(), high1.lead())
			val lead2 = min(low2.lead(), high2.lead())
			// We now know how many bits are needed to represent the largest or
			// smallest of the inputs, so we know how many bits are needed for
			// the xor.
			val minLead = min(lead1, lead2)
			// Now figure out the possible signs of the result.

			val max = (1L shl (64 - minLead)) - 1
			val low = if (canBeNegative) max.inv() else 0
			val high = if (canBePositive) max else -1
			return inclusive(low, high)
		}
		// Fall back on figuring out whether to reply (-∞..0), [0..∞), or the
		// union of the two.
		return integerRangeType(
			if (canBeNegative) negativeInfinity else negativeOne(),
			false,
			if (canBePositive) positiveInfinity else zero,
			false)
	}

	override fun L2GeneratorInterface.emitTransformedInfalliblePrimitive(
		rawFunction: A_RawFunction,
		arguments: L2ReadBoxedVectorOperand,
		result: L2WriteBoxedOperand)
	{
		val (x, y) = arguments.elements
		when
		{
			// x ⊕ 0 = x
			(y.constantOrNull.notNullAnd { equalsInt(0) }) ->
				moveBoxedRegister(
					x.semanticValue(), result.semanticValues())
			// 0 ⊕ y = y
			(x.constantOrNull.notNullAnd { equalsInt(0) }) ->
				moveBoxedRegister(
					y.semanticValue(), result.semanticValues())
			// x ⊕ x = 0
			currentManifest.isEquivalentSemanticValue(
				x.semanticValue(), y.semanticValue()
			) -> +L2_MOVE_BOXED(boxedConstant(zero), result)
			else -> emitBasicInfalliblePrimitive(rawFunction, arguments, result)
		}
	}

	override fun L1Translator.tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: CallSiteHelper
	): Boolean = Xor.run {
		generateBinaryIntOperation(
			this@P_BitwiseXor,
			arguments,
			argumentTypes,
			callSiteHelper,
			typeGuaranteeFunction = { restrictedArgTypes ->
				returnTypeGuaranteedByVM(rawFunction, restrictedArgTypes)
			},
			fallbackBody = {
				generateGeneralFunctionInvocation(
					functionToCallReg, false, callSiteHelper, arguments)
			})
	}

	override val semanticInfixOperatorString: String? get() = "Xor"
}
