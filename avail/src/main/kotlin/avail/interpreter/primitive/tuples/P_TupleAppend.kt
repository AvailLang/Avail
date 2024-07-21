/*
 * P_TupleAppend.kt
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

package avail.interpreter.primitive.tuples

import avail.descriptor.functions.A_RawFunction
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.isInt
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.A_Tuple.Companion.appendCanDestroy
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.TupleDescriptor
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.tupleOfTypesFromTo
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.ConcatenatedTupleTypeDescriptor.Companion.concatenatingAnd
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.singleInt
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForSizesTypesDefaultType
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypes
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restriction
import avail.interpreter.levelTwo.operation.L2_APPEND_TO_TUPLE
import avail.optimizer.L1Translator
import avail.utility.PrefixSharingList.Companion.append

/**
 * **Primitive:** Answer a new [tuple][TupleDescriptor] like the argument but
 * with the [element][AvailObject] appended to its right.
 */
@Suppress("unused")
object P_TupleAppend : Primitive(2, CannotFail, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val tuple = interpreter.argument(0)
		val newElement = interpreter.argument(1)

		return interpreter.primitiveSuccess(
			tuple.appendCanDestroy(newElement, true))
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction?,
		argumentTypes: List<A_Type>): A_Type
	{
		val (aTupleType, anElementType) = argumentTypes
		val anElementTupleType = tupleTypeForSizesTypesDefaultType(
			singleInt(1), emptyTuple, anElementType)
		return concatenatingAnd(aTupleType, anElementTupleType)
	}

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: L1Translator.CallSiteHelper): Boolean
	{
		assert(arguments.size == 2)
		val tupleRead = arguments[0]
		val newElementRead = arguments[1]

		val translator = callSiteHelper.translator
		val generator = translator.generator
		val tupleType = argumentTypes[0]
		val range = tupleType.sizeRange
		val size = range.lowerBound
		val explodedTupleElements = if (size.isInt && size == range.upperBound)
		{
			generator.explodeTupleIfPossible(
				tupleRead,
				tupleRead.type().tupleOfTypesFromTo(1, size.extractInt)
					.toList())
		}
		else null
		if (explodedTupleElements !== null)
		{
			callSiteHelper.useAnswer(
				generator.createTuple(
					explodedTupleElements.append(newElementRead)))
		}
		else
		{
			val resultType = concatenatingAnd(
				tupleType, tupleTypeForTypes(newElementRead.type()))
			val tempWrite =
				generator.boxedWriteTemp(restriction(resultType, null))
			generator.addInstruction(
				L2_APPEND_TO_TUPLE(tupleRead, newElementRead, tempWrite))
			callSiteHelper.useAnswer(translator.readBoxed(tempWrite))
		}
		return true
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralTupleType,
				ANY.o),
			mostGeneralTupleType)
}
