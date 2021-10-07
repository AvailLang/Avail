/*
 * P_DisableTraceVariableWrites.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

import com.avail.descriptor.fiber.FiberDescriptor
import com.avail.descriptor.fiber.FiberDescriptor.TraceFlag
import com.avail.descriptor.functions.A_Function
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.sets.A_Set.Companion.setUnionCanDestroy
import com.avail.descriptor.sets.SetDescriptor.Companion.emptySet
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import com.avail.descriptor.types.SetTypeDescriptor.Companion.setTypeForSizesContentType
import com.avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import com.avail.descriptor.variables.A_Variable
import com.avail.descriptor.variables.VariableDescriptor.VariableAccessReactor
import com.avail.exceptions.AvailErrorCode.E_ILLEGAL_TRACE_MODE
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.HasSideEffect
import com.avail.interpreter.Primitive.Flag.WritesToHiddenGlobalState
import com.avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Disable
 * [variable&#32;write&#32;tracing][TraceFlag.TRACE_VARIABLE_WRITES] for
 * the [current&#32;fiber][FiberDescriptor.currentFiber]. For each
 * [variable][A_Variable] that survived tracing, accumulate the variable's
 * [write&#32;reactor][VariableAccessReactor] [functions][A_Function] into a
 * [set][A_Set]. Clear the write reactors for each variable written. Answer the
 * set of functions.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_DisableTraceVariableWrites : Primitive(
	0, HasSideEffect, WritesToHiddenGlobalState)
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
		var functions = emptySet
		for (variable in written)
		{
			functions = functions.setUnionCanDestroy(
				variable.validWriteReactorFunctions(), true)
		}
		return interpreter.primitiveSuccess(functions)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			emptyTuple,
			setTypeForSizesContentType(
				wholeNumbers,
				functionType(
					emptyTuple,
					TOP.o)))

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_ILLEGAL_TRACE_MODE))
}
