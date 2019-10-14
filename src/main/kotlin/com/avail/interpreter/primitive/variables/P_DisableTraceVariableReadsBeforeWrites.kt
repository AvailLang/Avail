/*
 * P_DisableTraceVariableReadsBeforeWrites.java
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
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.TupleDescriptor.emptyTuple
import com.avail.descriptor.TypeDescriptor.Types.ATOM
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.descriptor.VariableDescriptor.VariableAccessReactor
import com.avail.exceptions.AvailErrorCode.E_ILLEGAL_TRACE_MODE
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.HasSideEffect

/**
 * **Primitive:** Disable [ ][TraceFlag.TRACE_VARIABLE_READS_BEFORE_WRITES] for the [current fiber][FiberDescriptor.currentFiber]. To
 * each [variable][VariableDescriptor] that survived tracing, add a
 * [write reactor][VariableAccessReactor] that wraps the specified
 * [function][FunctionDescriptor], associating it with the specified
 * [atom][AtomDescriptor] (for potential pre-activation removal).
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object P_DisableTraceVariableReadsBeforeWrites : Primitive(2, HasSideEffect)
{

	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val key = interpreter.argument(0)
		val reactorFunction = interpreter.argument(1)
		if (!interpreter.traceVariableReadsBeforeWrites())
		{
			return interpreter.primitiveFailure(E_ILLEGAL_TRACE_MODE)
		}
		interpreter.setTraceVariableReadsBeforeWrites(false)
		val fiber = interpreter.fiber()
		val readBeforeWritten = fiber.variablesReadBeforeWritten()
		val reactor = VariableAccessReactor(reactorFunction.makeShared())
		for (`var` in readBeforeWritten)
		{
			`var`.addWriteReactor(key, reactor)
		}
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type
	{
		return functionType(tuple(ATOM.o(), functionType(
			emptyTuple(),
			TOP.o())), TOP.o())
	}

	override fun privateFailureVariableType(): A_Type
	{
		return enumerationWith(set(E_ILLEGAL_TRACE_MODE))
	}

}