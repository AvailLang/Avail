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
import avail.descriptor.representation.A_BasicObject
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
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction.Companion.restriction
import avail.interpreter.levelTwo.operation.tuples.L2_APPEND_TO_TUPLE
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive2
import avail.optimizer.CallSiteHelper
import avail.optimizer.L1Translator
import avail.utility.PrefixSharingList.Companion.append

/**
 * **Primitive:** Answer a new [tuple][TupleDescriptor] like the argument but
 * with the [element][AvailObject] appended to its right.
 */
@Suppress("unused")
object P_TupleAppend : Primitive2(CannotFail, CanFold, CanInline)
{
	override fun attempt2(
		interpreter: Interpreter,
		arg1: AvailObject,
		arg2: AvailObject
	): A_BasicObject?
	{
		val tuple = arg1
		val newElement = arg2

		return tuple.appendCanDestroy(newElement, true, true)
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

	override fun L1Translator.tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: CallSiteHelper
	): Boolean
	{
		assert(arguments.size == 2)
		val tupleRead = arguments[0]
		val newElementRead = arguments[1]

		val tupleType = argumentTypes[0]
		val range = tupleType.sizeRange
		val size = range.lowerBound
		val explodedTupleElements =
			if (size.isInt && size.equals(range.upperBound))
			{
				explodeTupleIfPossible(
					tupleRead,
					tupleRead.type().tupleOfTypesFromTo(1, size.extractInt)
						.toList())
			}
			else null
		if (explodedTupleElements !== null)
		{
			callSiteHelper.useAnswer(
				createTuple(explodedTupleElements.append(newElementRead)),
				false)
			return true
		}
		val resultType = concatenatingAnd(
			tupleType, tupleTypeForTypes(newElementRead.type()))
		val tempWrite = boxedWriteTemp(
			"tuple after append",
			restriction(resultType, null))
		+L2_APPEND_TO_TUPLE(tupleRead, newElementRead, tempWrite)
		callSiteHelper.useAnswer(readBoxed(tempWrite), false)
		return true
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralTupleType,
				ANY()),
			mostGeneralTupleType)
}
