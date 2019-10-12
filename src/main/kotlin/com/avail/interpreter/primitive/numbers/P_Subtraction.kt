/*
 * P_Subtraction.java
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
package com.avail.interpreter.primitive.numbers

import com.avail.descriptor.A_RawFunction
import com.avail.descriptor.A_Type
import com.avail.exceptions.ArithmeticException
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operation.L2_SUBTRACT_INT_MINUS_INT
import com.avail.interpreter.levelTwo.operation.L2_SUBTRACT_INT_MINUS_INT_MOD_32_BITS
import com.avail.optimizer.L1Translator
import com.avail.optimizer.L1Translator.CallSiteHelper

import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.AbstractNumberDescriptor.binaryNumericOperationTypeBound
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.InfinityDescriptor.negativeInfinity
import com.avail.descriptor.InfinityDescriptor.positiveInfinity
import com.avail.descriptor.IntegerDescriptor.one
import com.avail.descriptor.IntegerRangeTypeDescriptor.int32
import com.avail.descriptor.IntegerRangeTypeDescriptor.integerRangeType
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SetDescriptor.emptySet
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.TypeDescriptor.Types.NUMBER
import com.avail.exceptions.AvailErrorCode.E_CANNOT_SUBTRACT_LIKE_INFINITIES
import com.avail.interpreter.Primitive.Fallibility.CallSiteCanFail
import com.avail.interpreter.Primitive.Fallibility.CallSiteCannotFail
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_INT
import com.avail.interpreter.levelTwo.operand.TypeRestriction.restrictionForType
import com.avail.optimizer.L2Generator.edgeTo
import java.util.stream.Collectors.toList

/**
 * **Primitive:** Subtract [ ] b from a.
 */
object P_Subtraction : Primitive(2, CanFold, CanInline)
{

	override fun attempt(
		interpreter: Interpreter): Primitive.Result
	{
		interpreter.checkArgumentCount(2)
		val a = interpreter.argument(0)
		val b = interpreter.argument(1)
		try
		{
			return interpreter.primitiveSuccess(a.minusCanDestroy(b, true))
		}
		catch (e: ArithmeticException)
		{
			return interpreter.primitiveFailure(e)
		}

	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(
			tuple(
				NUMBER.o(),
				NUMBER.o()),
			NUMBER.o())
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		val aType = argumentTypes[0]
		val bType = argumentTypes[1]

		try
		{
			if (aType.isEnumeration && bType.isEnumeration)
			{
				val aInstances = aType.instances()
				val bInstances = bType.instances()
				// Compute the Cartesian product as an enumeration if there will
				// be few enough entries.
				if (aInstances.setSize() * bInstances.setSize().toLong() < 100)
				{
					var answers = emptySet()
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
				val low = aType.lowerBound().minusCanDestroy(
					bType.upperBound(),
					false)
				val high = aType.upperBound().minusCanDestroy(
					bType.lowerBound(),
					false)
				val includesNegativeInfinity = negativeInfinity().isInstanceOf(aType) || positiveInfinity().isInstanceOf(bType)
				val includesInfinity = positiveInfinity().isInstanceOf(aType) || negativeInfinity().isInstanceOf(bType)
				return integerRangeType(
					low.minusCanDestroy(one(), false),
					includesNegativeInfinity,
					high.plusCanDestroy(one(), false),
					includesInfinity)
			}
		}
		catch (e: ArithmeticException)
		{
			// $FALL-THROUGH$
		}

		return binaryNumericOperationTypeBound(aType, bType)
	}

	override fun fallibilityForArgumentTypes(
		argumentTypes: List<A_Type>): Primitive.Fallibility
	{
		val aType = argumentTypes[0]
		val bType = argumentTypes[1]

		val aTypeIncludesNegativeInfinity = negativeInfinity().isInstanceOf(aType)
		val aTypeIncludesInfinity = positiveInfinity().isInstanceOf(aType)
		val bTypeIncludesNegativeInfinity = negativeInfinity().isInstanceOf(bType)
		val bTypeIncludesInfinity = positiveInfinity().isInstanceOf(bType)
		return if (aTypeIncludesNegativeInfinity && bTypeIncludesNegativeInfinity || aTypeIncludesInfinity && bTypeIncludesInfinity)
		{
			CallSiteCanFail
		}
		else CallSiteCannotFail
	}

	override fun privateFailureVariableType(): A_Type
	{
		return enumerationWith(set(E_CANNOT_SUBTRACT_LIKE_INFINITIES))
	}

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: CallSiteHelper): Boolean
	{
		val a = arguments[0]
		val b = arguments[1]
		val aType = argumentTypes[0]
		val bType = argumentTypes[1]

		// If either of the argument types does not intersect with int32, then
		// fall back to the primitive invocation.
		if (aType.typeIntersection(int32()).isBottom || bType.typeIntersection(int32()).isBottom)
		{
			return false
		}

		// Attempt to unbox the arguments.
		val generator = translator.generator
		val fallback = generator.createBasicBlock(
			"fall back to boxed subtraction")
		val intA = generator.readInt(a.semanticValue(), fallback)
		val intB = generator.readInt(b.semanticValue(), fallback)
		if (generator.currentlyReachable())
		{
			// The happy path is reachable.  Generate the most efficient
			// available unboxed arithmetic.
			val returnTypeIfInts = returnTypeGuaranteedByVM(
				rawFunction,
				argumentTypes.map { it.typeIntersection(int32()) })
			val semanticTemp = generator.topFrame.temp(generator.nextUnique())
			val tempWriter = generator.intWrite(
				semanticTemp,
				restrictionForType(returnTypeIfInts, UNBOXED_INT))
			if (returnTypeIfInts.isSubtypeOf(int32()))
			{
				// The result is guaranteed not to overflow, so emit an
				// instruction that won't bother with an overflow check.  Note
				// that both the unboxed and boxed registers end up in the same
				// synonym, so subsequent uses of the result might use either
				// register, depending whether an unboxed value is desired.
				translator.addInstruction(
					L2_SUBTRACT_INT_MINUS_INT_MOD_32_BITS.instance,
					intA,
					intB,
					tempWriter)
			}
			else
			{
				// The result could exceed an int32.
				val success = generator.createBasicBlock("difference is in range")
				translator.addInstruction(
					L2_SUBTRACT_INT_MINUS_INT.instance,
					intA,
					intB,
					tempWriter,
					edgeTo(success),
					edgeTo(fallback))
				generator.startBlock(success)
			}
			// Even though we're just using the boxed value again, the unboxed
			// form is also still available for use by subsequent primitives,
			// which could allow the boxing instruction to evaporate.
			callSiteHelper.useAnswer(generator.readBoxed(semanticTemp))
		}
		if (fallback.predecessorEdgesCount() > 0)
		{
			// The fallback block is reachable, so generate the slow case within
			// it.  Fallback may happen from conversion of non-int32 arguments,
			// or from int32 overflow calculating the difference.
			generator.startBlock(fallback)
			translator.generateGeneralFunctionInvocation(
				functionToCallReg, arguments, false, callSiteHelper)
		}
		return true
	}

}