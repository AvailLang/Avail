/*
 * P_DisableTraceVariableWrites.kt
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

package com.avail.interpreter.primitive.variables

import com.avail.descriptor.*
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.FiberDescriptor.TraceFlag
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.IntegerRangeTypeDescriptor.wholeNumbers
import com.avail.descriptor.SetDescriptor.emptySet
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.SetTypeDescriptor.setTypeForSizesContentType
import com.avail.descriptor.TupleDescriptor.emptyTuple
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.descriptor.VariableDescriptor.VariableAccessReactor
import com.avail.exceptions.AvailErrorCode.E_ILLEGAL_TRACE_MODE
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.HasSideEffect

/**
 * **Primitive:** Disable [variable write tracing
 * ][TraceFlag.TRACE_VARIABLE_WRITES] for the [current fiber
 * ][FiberDescriptor.currentFiber]. For each [variable][A_Variable] that
 * survived tracing, accumulate the variable's [write
 * reactor][VariableAccessReactor] [functions][A_Function] into a [set][A_Set].
 * Clear the write reactors for each variable written. Answer the set of
 * functions.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_DisableTraceVariableWrites : Primitive(0, HasSideEffect)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(0)
		val fiber = interpreter.fiber()
		if (!fiber.traceFlag(TraceFlag.TRACE_VARIABLE_WRITES))
		{
			return interpreter.primitiveFailure(E_ILLEGAL_TRACE_MODE)
		}
		interpreter.setTraceVariableWrites(false)
		val written = fiber.variablesWritten()
		var functions = emptySet()
		for (variable in written)
		{
			functions = functions.setUnionCanDestroy(
				variable.validWriteReactorFunctions(), true)
		}
		return interpreter.primitiveSuccess(functions)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			emptyTuple(),
			setTypeForSizesContentType(
				wholeNumbers(),
				functionType(
					emptyTuple(),
					TOP.o())))

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_ILLEGAL_TRACE_MODE))
}