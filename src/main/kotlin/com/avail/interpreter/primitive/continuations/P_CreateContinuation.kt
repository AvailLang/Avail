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

import com.avail.descriptor.A_Type
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.ContinuationDescriptor
import com.avail.descriptor.ContinuationDescriptor.createContinuationWithFrame
import com.avail.descriptor.ContinuationTypeDescriptor.mostGeneralContinuationType
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.FunctionTypeDescriptor.mostGeneralFunctionType
import com.avail.descriptor.IntegerRangeTypeDescriptor.naturalNumbers
import com.avail.descriptor.IntegerRangeTypeDescriptor.wholeNumbers
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.TupleDescriptor.toList
import com.avail.descriptor.TupleTypeDescriptor.mostGeneralTupleType
import com.avail.descriptor.VariableTypeDescriptor.variableTypeFor
import com.avail.descriptor.objects.A_BasicObject
import com.avail.exceptions.AvailErrorCode.E_CANNOT_CREATE_CONTINUATION_FOR_INFALLIBLE_PRIMITIVE_FUNCTION
import com.avail.exceptions.AvailErrorCode.E_INCORRECT_CONTINUATION_STACK_SIZE
import com.avail.interpreter.Interpreter
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
			pc.extractInt(),
			stackp.extractInt(),
			unoptimizedChunk,
			TO_RETURN_INTO.offsetInDefaultChunk,
			toList<A_BasicObject>(stack),
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
