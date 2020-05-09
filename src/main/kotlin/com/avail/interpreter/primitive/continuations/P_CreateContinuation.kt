/*
 * P_CreateContinuation.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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
package com.avail.interpreter.primitive.continuations

import com.avail.descriptor.representation.NilDescriptor.Companion.nil
import com.avail.descriptor.functions.ContinuationDescriptor
import com.avail.descriptor.functions.ContinuationDescriptor.Companion.createContinuationWithFrame
import com.avail.descriptor.sets.SetDescriptor.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple
import com.avail.descriptor.tuples.TupleDescriptor.toList
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.types.ContinuationTypeDescriptor.mostGeneralContinuationType
import com.avail.descriptor.types.FunctionTypeDescriptor.functionType
import com.avail.descriptor.types.FunctionTypeDescriptor.mostGeneralFunctionType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.naturalNumbers
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.wholeNumbers
import com.avail.descriptor.types.TupleTypeDescriptor.mostGeneralTupleType
import com.avail.descriptor.types.VariableTypeDescriptor.variableTypeFor
import com.avail.exceptions.AvailErrorCode.E_CANNOT_CREATE_CONTINUATION_FOR_INFALLIBLE_PRIMITIVE_FUNCTION
import com.avail.exceptions.AvailErrorCode.E_INCORRECT_CONTINUATION_STACK_SIZE
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*
import com.avail.interpreter.levelTwo.L2Chunk
import com.avail.interpreter.levelTwo.L2Chunk.ChunkEntryPoint.TO_RETURN_INTO
import com.avail.interpreter.levelTwo.L2Chunk.unoptimizedChunk

/**
 * **Primitive:** Create a [continuation][ContinuationDescriptor]. It will
 * execute as unoptimized code via the [L2Chunk.unoptimizedChunk].  Fail if the
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
		val primitive = rawFunction.primitive()
		if (primitive !== null && primitive.hasFlag(CannotFail))
		{
			return interpreter.primitiveFailure(
				E_CANNOT_CREATE_CONTINUATION_FOR_INFALLIBLE_PRIMITIVE_FUNCTION)
		}
		if (stack.tupleSize() != rawFunction.numSlots())
		{
			return interpreter.primitiveFailure(
				E_INCORRECT_CONTINUATION_STACK_SIZE)
		}
		val cont = createContinuationWithFrame(
			function,
			callerHolder.value(),
			nil,
			pc.extractInt(),
			stackp.extractInt(),
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
				wholeNumbers(),
				mostGeneralTupleType(),
				naturalNumbers(),
				variableTypeFor(
					mostGeneralContinuationType())),
			mostGeneralContinuationType())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(
			E_CANNOT_CREATE_CONTINUATION_FOR_INFALLIBLE_PRIMITIVE_FUNCTION,
			E_INCORRECT_CONTINUATION_STACK_SIZE))
}
