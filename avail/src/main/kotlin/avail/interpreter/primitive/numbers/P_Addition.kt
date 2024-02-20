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
import avail.descriptor.numbers.A_Number.Companion.equalsInt
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.isInt
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
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.extendedIntegers
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
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operand.L2WriteBoxedOperand
import avail.interpreter.levelTwo.operation.L2_ADD_INT_TO_INT
import avail.interpreter.levelTwo.operation.L2_BIT_LOGIC_OP
import avail.interpreter.levelTwo.operation.L2_BOX_INT
import avail.interpreter.levelTwo.operation.L2_MOVE
import avail.interpreter.levelTwo.operation.L2_RUN_INFALLIBLE_PRIMITIVE
import avail.optimizer.L1Translator.CallSiteHelper
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2Generator.Companion.edgeTo
import avail.optimizer.reoptimizer.L2Regenerator
import avail.optimizer.values.L2SemanticUnboxedInt
import avail.utility.notNullAnd

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
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>
	): A_Type
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
				if (aInstances.setSize.toLong() * bInstances.setSize.toLong()
					< 100)
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
		callSiteHelper: CallSiteHelper
	): Boolean = attemptToGenerateTwoIntToIntPrimitive(
		callSiteHelper,
		functionToCallReg,
		rawFunction,
		arguments,
		argumentTypes,
		ifOutputIsInt = {
			generator.addInstruction(
				L2_BIT_LOGIC_OP.wrappedAdd, intA, intB, intWrite)
		},
		ifOutputIsPossiblyInt = {
			generator.addInstruction(
				L2_ADD_INT_TO_INT,
				intA,
				intB,
				intWrite,
				edgeTo(intFailure),
				edgeTo(intSuccess))
		})

	override fun emitTransformedInfalliblePrimitive(
		operation: L2_RUN_INFALLIBLE_PRIMITIVE,
		rawFunction: A_RawFunction,
		arguments: L2ReadBoxedVectorOperand,
		result: L2WriteBoxedOperand,
		regenerator: L2Regenerator)
	{
		val generator = regenerator.targetGenerator
		val manifest = generator.currentManifest
		val (arg1, arg2) = arguments.elements
		val restriction1 = manifest.restrictionFor(arg1.semanticValue())
		val restriction2 = manifest.restrictionFor(arg2.semanticValue())

		// See if both inputs are constant first.
		val const1 = restriction1.constantOrNull
		val const2 = restriction2.constantOrNull

		val resultRestriction = result.restriction().intersectionWithType(
			returnTypeGuaranteedByVM(
				rawFunction,
				listOf(arg1.type(), arg2.type())))
		val resultType = resultRestriction.type
		if (resultType.isIntegerRangeType &&
			resultType.lowerBound.equals(resultType.upperBound))
		{
			// The restriction already narrowed it down to a single value.
			try
			{
				val sum = resultType.lowerBound
				generator.moveRegister(
					L2_MOVE.boxed,
					generator.boxedConstant(sum).semanticValue(),
					result.semanticValues())
				if (sum.isInt)
				{
					// It's an i32, so put it in the int semantic value, so that
					// code downstream may use it without unboxing.
					generator.moveRegister(
						L2_MOVE.unboxedInt,
						generator.unboxedIntConstant(sum.extractInt)
							.semanticValue(),
						result.semanticValues().map(::L2SemanticUnboxedInt))
				}
				return
			}
			catch (e: ArithmeticException)
			{
				// This was supposed to be an infallible primitive invocation,
				// so the code is wrong.
				throw AssertionError(
					"Infallible addition of constants failed ($const1, $const2)",
					e)
			}
		}

		// Only check for identities if the inputs are extended integers.
		if (!restriction1.containedByType(extendedIntegers)
			|| !restriction2.containedByType(extendedIntegers))
		{
			if (const1.notNullAnd { equalsInt(0) })
			{
				// 0 + x = x  (since x is an extended integer).
				generator.moveRegister(
            		L2_MOVE.boxed,
					arg2.semanticValue(),
					result.semanticValues())
				return
			}
			if (const2.notNullAnd { equalsInt(0) })
			{
				// x + 0 = x  (since x is an extended integer).
				generator.moveRegister(
					L2_MOVE.boxed,
					arg1.semanticValue(),
					result.semanticValues())
				return
			}
			// TODO We could look for chains of additions and subtractions where
			//  every value was an integer, and commute/associate all the
			//  constants together, folding them.
		}

		// Only further optimize if the inputs and output are ints.
		if (!restriction1.containedByType(i32)
			|| !restriction2.containedByType(i32)
			|| !resultRestriction.containedByType(i32))
		{
			super.emitTransformedInfalliblePrimitive(
				operation, rawFunction, arguments, result, regenerator)
			return
		}

		// Replace with a non-overflowing i32 addition.
		val unreachable = L2BasicBlock("should not reach")
		val intWrite = generator.intWrite(
			result.semanticValues().map(::L2SemanticUnboxedInt).toSet(),
			resultRestriction.forUnboxedInt())
		generator.addInstruction(
			L2_BIT_LOGIC_OP.wrappedAdd,
			generator.readInt(
				L2SemanticUnboxedInt(arg1.semanticValue()),
				unreachable),
			generator.readInt(
				L2SemanticUnboxedInt(arg2.semanticValue()),
				unreachable),
			intWrite)
		// Unbox it, in case something needs it unboxed downstream.
		generator.addInstruction(
			L2_BOX_INT,
			manifest.readInt(intWrite.pickSemanticValue()),
			result)
		assert(unreachable.currentlyReachable())
	}
}
