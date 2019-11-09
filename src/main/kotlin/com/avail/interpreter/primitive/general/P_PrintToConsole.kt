/*
 * P_PrintToConsole.kt
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
package com.avail.interpreter.primitive.general

import com.avail.descriptor.A_Type
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.AvailObject
import com.avail.descriptor.FiberDescriptor.ExecutionState
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.StringDescriptor
import com.avail.descriptor.TupleTypeDescriptor.stringType
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.E_IO_ERROR
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanSuspend
import com.avail.interpreter.Primitive.Flag.Unknown
import com.avail.io.SimpleCompletionHandler
import com.avail.io.TextOutputChannel

/**
 * **Primitive:** Print the specified [string][StringDescriptor] to the
 * [current][Interpreter.fiber]'s [standard output channel][TextOutputChannel],
 * [ ][ExecutionState.SUSPENDED] the current fiber until the string can be
 * queued for writing.
 */
@Suppress("unused")
object P_PrintToConsole : Primitive(1, CanSuspend, Unknown)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val string = interpreter.argument(0)

		val loader = interpreter.availLoaderOrNull()
		loader?.statementCanBeSummarized(false)

		val textInterface = interpreter.fiber().textInterface()
		return interpreter.suspendAndDo { toSucceed, toFail ->
			textInterface.outputChannel.write(
				string.asNativeString(),
				nil,
				SimpleCompletionHandler(
					{ _ ->
						toSucceed.value(nil)
						Unit
					},
					{ _ ->
						toFail.value(E_IO_ERROR)
						Unit
					}))
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(stringType()), TOP.o())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_IO_ERROR))
}
