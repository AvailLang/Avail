/*
 * P_Addition.kt
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

import avail.descriptor.functions.A_RawFunction
import avail.descriptor.numbers.A_Number.Companion.extractLong
import avail.descriptor.numbers.A_Number.Companion.minusCanDestroy
import avail.descriptor.numbers.A_Number.Companion.plusCanDestroy
import avail.descriptor.numbers.AbstractNumberDescriptor
import avail.descriptor.numbers.AbstractNumberDescriptor.Companion.binaryNumericOperationTypeBound
import avail.descriptor.numbers.InfinityDescriptor.Companion.negativeInfinity
import avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import avail.descriptor.numbers.IntegerDescriptor.Companion.one
import avail.descriptor.sets.A_Set.Companion.setSize
import avail.descriptor.sets.A_Set.Companion.setWithElementCanDestroy
import avail.descriptor.sets.SetDescriptor.Companion.emptySet
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.instances
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.typeIntersection
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.i32
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integerRangeType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.NUMBER
import avail.exceptions.ArithmeticException
import avail.exceptions.AvailErrorCode.E_CANNOT_ADD_UNLIKE_INFINITIES
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Fallibility.CallSiteCanFail
import avail.interpreter.Primitive.Fallibility.CallSiteCannotFail
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restrictionForType
import avail.interpreter.levelTwo.operand.TypeRestriction.RestrictionFlagEncoding.UNBOXED_INT_FLAG
import avail.interpreter.levelTwo.operation.L2_ADD_INT_TO_INT
import avail.interpreter.levelTwo.operation.L2_BIT_LOGIC_OP
import avail.optimizer.L1Translator
import avail.optimizer.L1Translator.CallSiteHelper
import avail.optimizer.L2Generator.Companion.edgeTo
import avail.optimizer.values.L2SemanticUnboxedInt
import avail.optimizer.values.L2SemanticValue.Companion.primitiveInvocation

/**
 * **Primitive:** Add two [numbers][AbstractNumberDescriptor].
 */
@Suppress("unused")
object P_Addition : Primitive(2, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val a = interpreter.argument(0)
		val b = interpreter.argument(1)
		return try
		{
			interpreter.primitiveSuccess(a.plusCanDestroy(b, true))
		}
		catch (e: ArithmeticException)
		{
			interpreter.primitiveFailure(e)
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(NUMBER.o, NUMBER.o), NUMBER.o)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_CANNOT_ADD_UNLIKE_INFINITIES))

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction, argumentTypes: List<A_Type>): A_Type
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
								aInstance.plusCanDestroy(bInstance, false),
								false)
						}
					}
					return enumerationWith(answers)
				}
			}
			if (aType.isIntegerRangeType && bType.isIntegerRangeType)
			{
				val low = aType.lowerBound.plusCanDestroy(
					bType.lowerBound, false)
				val high = aType.upperBound.plusCanDestroy(
					bType.upperBound, false)
				val includesNegativeInfinity =
					negativeInfinity.isInstanceOf(aType)
						|| negativeInfinity.isInstanceOf(bType)
				val includesInfinity =
					positiveInfinity.isInstanceOf(aType)
						|| positiveInfinity.isInstanceOf(bType)
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

	override fun fallibilityForArgumentTypes(
		argumentTypes: List<A_Type>): Fallibility
	{
		val (aType, bType) = argumentTypes

		val aTypeIncludesNegativeInfinity = negativeInfinity.isInstanceOf(aType)
		val aTypeIncludesInfinity = positiveInfinity.isInstanceOf(aType)
		val bTypeIncludesNegativeInfinity = negativeInfinity.isInstanceOf(bType)
		val bTypeIncludesInfinity = positiveInfinity.isInstanceOf(bType)
		return if (aTypeIncludesInfinity && bTypeIncludesNegativeInfinity
			|| aTypeIncludesNegativeInfinity && bTypeIncludesInfinity)
		{
			CallSiteCanFail
		}
		else
		{
			CallSiteCannotFail
		}
	}

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: CallSiteHelper
	): Boolean
	{
		val (a, b) = arguments
		val (aType, bType) = argumentTypes

		// If either of the argument types does not intersect with int32, then
		// fall back to the primitive invocation.
		val aIntersectInt32 = aType.typeIntersection(i32)
		val bIntersectInt32 = bType.typeIntersection(i32)
		if (aType.typeIntersection(i32).isBottom
			|| bType.typeIntersection(i32).isBottom)
		{
			return false
		}
		// lowest and highest can be at most ±2^32, so there's lots of room in
		// a long.
		val lowest = aIntersectInt32.lowerBound.extractLong +
			bIntersectInt32.lowerBound.extractLong
		val highest = aIntersectInt32.lowerBound.extractLong +
			bIntersectInt32.lowerBound.extractLong
		if (lowest > Int.MAX_VALUE || highest < Int.MIN_VALUE)
		{
			// The sum is definitely out of range, so don't bother switching to
			// int math.
			return false
		}

		// Attempt to unbox the arguments.
		val generator = translator.generator
		val fallback = generator.createBasicBlock("fall back to boxed addition")
		val intA = generator.readInt(
			L2SemanticUnboxedInt(a.semanticValue()), fallback)
		val intB = generator.readInt(
			L2SemanticUnboxedInt(b.semanticValue()), fallback)
		if (generator.currentlyReachable())
		{
			// The happy path is reachable.  Generate the most efficient
			// available unboxed arithmetic.
			val returnTypeIfInts = returnTypeGuaranteedByVM(
				rawFunction,
				listOf(aIntersectInt32, bIntersectInt32))
			val semanticTemp = primitiveInvocation(
				this, listOf(a.semanticValue(), b.semanticValue()))
			val tempWriter = generator.intWrite(
				setOf(L2SemanticUnboxedInt(semanticTemp)),
				restrictionForType(returnTypeIfInts, UNBOXED_INT_FLAG))
			if (returnTypeIfInts.isSubtypeOf(i32))
			{
				// The result is guaranteed not to overflow, so emit an
				// instruction that won't bother with an overflow check.  Note
				// that both the unboxed and boxed registers end up in the same
				// synonym, so subsequent uses of the result might use either
				// register, depending whether an unboxed value is desired.
				translator.addInstruction(
					L2_BIT_LOGIC_OP.wrappedAdd, intA, intB, tempWriter)
			}
			else
			{
				// The result could exceed an int32.
				val success = generator.createBasicBlock("sum is in range")
				translator.addInstruction(
					L2_ADD_INT_TO_INT,
					intA,
					intB,
					tempWriter,
					edgeTo(fallback),
					edgeTo(success))
				generator.startBlock(success)
			}
			// Even though we're just using the boxed value again, the unboxed
			// form is also still available for use by subsequent primitives,
			// which could allow the boxing instruction to evaporate.
			callSiteHelper.useAnswer(generator.readBoxed(semanticTemp))
		}
		if (fallback.predecessorEdges().isNotEmpty())
		{
			// The fallback block is reachable, so generate the slow case within
			// it.  Fallback may happen from conversion of non-int32 arguments,
			// or from int32 overflow calculating the sum.
			generator.startBlock(fallback)
			translator.generateGeneralFunctionInvocation(
				functionToCallReg, arguments, false, callSiteHelper)
		}
		return true
	}
}
