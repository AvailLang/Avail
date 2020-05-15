/*
 * P_MarkGuardVariable.kt
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

package com.avail.interpreter.primitive.controlflow

import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor.functionType
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
import com.avail.descriptor.types.VariableTypeDescriptor.variableTypeFor
import com.avail.exceptions.AvailErrorCode.*
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.Unknown

/**
 * **Primitive:** Mark a primitive failure variable from a nearby invocation of
 * [P_CatchException].  The three states are (1) unmarked and running the body,
 * (2) handling an exception, and (3) running the final ensure clause.  If the
 * current state is 1, it can be marked as either 2 or 3. If the state is 2, it
 * can be marked as 3.  No other transitions are allowed.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_MarkGuardVariable : Primitive(2, CanInline, Unknown)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val variable = interpreter.argument(0)
		val mark = interpreter.argument(1)
		return interpreter.markGuardVariable(variable, mark)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				variableTypeFor(
					enumerationWith(
						set(
							E_REQUIRED_FAILURE,
							E_INCORRECT_ARGUMENT_TYPE,
							E_HANDLER_SENTINEL,
							E_UNWIND_SENTINEL))),
				enumerationWith(
					set(E_HANDLER_SENTINEL, E_UNWIND_SENTINEL))),
			TOP.o())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_CANNOT_MARK_HANDLER_FRAME))
}
