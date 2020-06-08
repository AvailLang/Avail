/*
 * P_FiberResult.kt
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

package com.avail.interpreter.primitive.fibers

import com.avail.descriptor.fiber.FiberDescriptor
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.FiberTypeDescriptor.Companion.mostGeneralFiberType
import com.avail.descriptor.types.FunctionTypeDescriptor.functionType
import com.avail.descriptor.types.TypeDescriptor.Types.ANY
import com.avail.exceptions.AvailErrorCode.E_FIBER_PRODUCED_INCORRECTLY_TYPED_RESULT
import com.avail.exceptions.AvailErrorCode.E_FIBER_RESULT_UNAVAILABLE
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.ReadsFromHiddenGlobalState
import com.avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Answer the result of the specified [fiber][FiberDescriptor].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_FiberResult : Primitive(
	1, CanInline, ReadsFromHiddenGlobalState)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val fiber = interpreter.argument(0)
		return with(fiber) {
			lock {
				when {
					!executionState().indicatesTermination()
						|| fiberResult().equalsNil() ->
						interpreter.primitiveFailure(
							E_FIBER_RESULT_UNAVAILABLE)
					!fiberResult().isInstanceOf(kind().resultType()) ->
						interpreter.primitiveFailure(
							E_FIBER_PRODUCED_INCORRECTLY_TYPED_RESULT)
					else -> interpreter.primitiveSuccess(fiberResult())
				}
			}
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(mostGeneralFiberType()), ANY.o())

	override fun privateFailureVariableType(): A_Type =
		 enumerationWith(set(
			E_FIBER_RESULT_UNAVAILABLE,
			E_FIBER_PRODUCED_INCORRECTLY_TYPED_RESULT))
}
