/*
 * P_CreateContinuation.kt
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
package avail.interpreter.primitive.continuations

import avail.descriptor.functions.A_RawFunction.Companion.numSlots
import avail.descriptor.functions.ContinuationDescriptor
import avail.descriptor.functions.ContinuationDescriptor.Companion.createContinuationWithFrame
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.TupleDescriptor.Companion.toList
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.ContinuationTypeDescriptor.Companion.mostGeneralContinuationType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.mostGeneralFunctionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import avail.descriptor.types.VariableTypeDescriptor.Companion.variableTypeFor
import avail.exceptions.AvailErrorCode.E_CANNOT_CREATE_CONTINUATION_FOR_INFALLIBLE_PRIMITIVE_FUNCTION
import avail.exceptions.AvailErrorCode.E_INCORRECT_CONTINUATION_STACK_SIZE
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.L2JVMChunk.ChunkEntryPoint.TO_RETURN_INTO
import avail.interpreter.levelTwo.L2JVMChunk.Companion.unoptimizedChunk

/**
 * **Primitive:** Create a [continuation][ContinuationDescriptor]. It will
 * execute as unoptimized code via the [unoptimizedChunk].  Fail if the
 * provided function is an infallible primitive.
 */
@Suppress("unused")
object P_CreateContinuation : Primitive(5, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(5)
		val function = interpreter.argument(0)
		val pc = interpreter.argument(1)
		val stack = interpreter.argument(2)
		val stackp = interpreter.argument(3)
		val callerHolder = interpreter.argument(4)

		val rawFunction = function.code()
		val primitive = rawFunction.codePrimitive()
		if (primitive !== null && primitive.hasFlag(CannotFail))
		{
			return interpreter.primitiveFailure(
				E_CANNOT_CREATE_CONTINUATION_FOR_INFALLIBLE_PRIMITIVE_FUNCTION)
		}
		if (stack.tupleSize != rawFunction.numSlots)
		{
			return interpreter.primitiveFailure(
				E_INCORRECT_CONTINUATION_STACK_SIZE)
		}
		val cont = createContinuationWithFrame(
			function,
			callerHolder.value(),
			nil,
			pc.extractInt,
			stackp.extractInt,
			unoptimizedChunk,
			TO_RETURN_INTO.offsetInDefaultChunk,
			toList(stack),
			0)
		return interpreter.primitiveSuccess(cont)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralFunctionType(),
				wholeNumbers,
				mostGeneralTupleType,
				naturalNumbers,
				variableTypeFor(
					mostGeneralContinuationType)),
			mostGeneralContinuationType)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(
			E_CANNOT_CREATE_CONTINUATION_FOR_INFALLIBLE_PRIMITIVE_FUNCTION,
			E_INCORRECT_CONTINUATION_STACK_SIZE))
}
