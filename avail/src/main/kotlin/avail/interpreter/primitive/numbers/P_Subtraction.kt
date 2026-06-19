/*
 * P_Subtraction.kt
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
package avail.interpreter.primitive.numbers

import avail.descriptor.numbers.AbstractNumberDescriptor
import avail.descriptor.numbers.AbstractNumberDescriptor.Companion.binaryNumericOperationTypeBound
import avail.descriptor.numbers.InfinityDescriptor.Companion.negativeInfinity
import avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import avail.descriptor.numbers.IntegerDescriptor.Companion.one
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Number.Companion.minusCanDestroy
import avail.descriptor.representation.A_Number.Companion.plusCanDestroy
import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.A_Set.Companion.setSize
import avail.descriptor.representation.A_Set.Companion.setWithElementCanDestroy
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.instances
import avail.descriptor.representation.A_Type.Companion.lowerBound
import avail.descriptor.representation.A_Type.Companion.upperBound
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integerRangeType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.NUMBER
import avail.exceptions.ArithmeticException
import avail.exceptions.AvailErrorCode.E_CANNOT_SUBTRACT_LIKE_INFINITIES
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP
import avail.interpreter.levelTwo.operation.numbers.L2_BIT_LOGIC_OP.BitOperation.Sub
import avail.interpreter.levelTwo.operation.numbers.L2_SUBTRACT_INT_MINUS_INT
import avail.interpreter.primitive.Primitive.Fallibility.CallSiteCanFail
import avail.interpreter.primitive.Primitive.Fallibility.CallSiteCannotFail
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive2
import avail.optimizer.CallSiteHelper
import avail.optimizer.L1Translator
import avail.optimizer.L2Generator.Companion.edgeTo

/**
 * **Primitive:** Subtract [number][AbstractNumberDescriptor] b from a.
 */
@Suppress("unused")
object P_Subtraction : Primitive2(CanFold, CanInline)
{
	override fun Interpreter.attempt2(
		arg1: AvailObject,
		arg2: AvailObject
	): A_BasicObject?
	{
		val a = arg1
		val b = arg2
		return try
		{
			a.minusCanDestroy(b, true)
		}
		catch (e: ArithmeticException)
		{
			fail(e.errorCode)
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(NUMBER(), NUMBER()), NUMBER())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_CANNOT_SUBTRACT_LIKE_INFINITIES))

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction?, argumentTypes: List<A_Type>): A_Type
	{
		val (aType, bType) = argumentTypes
		try
		{
			if (aType.isEnumeration && bType.isEnumeration)
			{
				val aInstances = aType.instances
				val bInstances = bType.instances
				// Compute the Cartesian product as an enumeration if there will
				// be few enough entries.
				if (aInstances.setSize * bInstances.setSize.toLong() < 100)
				{
					var answers = emptySet
					for (aInstance in aInstances)
					{
						for (bInstance in bInstances)
						{
							answers = answers.setWithElementCanDestroy(
								aInstance.minusCanDestroy(bInstance, false),
								false)
						}
					}
					return enumerationWith(answers)
				}
			}
			if (aType.isIntegerRangeType && bType.isIntegerRangeType)
			{
				val low = aType.lowerBound.minusCanDestroy(
					bType.upperBound, false)
				val high = aType.upperBound.minusCanDestroy(
					bType.lowerBound, false)
				val includesNegativeInfinity =
					negativeInfinity.isInstanceOf(aType)
						|| positiveInfinity.isInstanceOf(bType)
				val includesInfinity =
					positiveInfinity.isInstanceOf(aType)
						|| negativeInfinity.isInstanceOf(bType)
				return integerRangeType(
					low.minusCanDestroy(one, false),
					includesNegativeInfinity,
					high.plusCanDestroy(one, false),
					includesInfinity)
			}
		}
		catch (e: ArithmeticException)
		{
			// $FALL-THROUGH$
		}

		return binaryNumericOperationTypeBound(aType, bType)
	}

	override fun fallibilityForArgumentTypes(argumentTypes: List<A_Type>)
		: Fallibility
	{
		val (aType, bType) = argumentTypes
		val aTypeIncludesNegativeInfinity = negativeInfinity.isInstanceOf(aType)
		val aTypeIncludesInfinity = positiveInfinity.isInstanceOf(aType)
		val bTypeIncludesNegativeInfinity = negativeInfinity.isInstanceOf(bType)
		val bTypeIncludesInfinity = positiveInfinity.isInstanceOf(bType)
		return if (aTypeIncludesNegativeInfinity && bTypeIncludesNegativeInfinity
			|| aTypeIncludesInfinity && bTypeIncludesInfinity)
		{
			CallSiteCanFail
		}
		else
		{
			CallSiteCannotFail
		}
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
			+L2_BIT_LOGIC_OP(Sub, intA, intB, intWrite)
		},
		ifOutputIsPossiblyInt = {
			+L2_SUBTRACT_INT_MINUS_INT(
				intA,
				intB,
				intWrite,
				edgeTo(intFailure),
				edgeTo(intSuccess))
		})

	override val semanticInfixOperatorString: String? get() = "Sub"
}
