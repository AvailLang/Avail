/*
 * P_ReadFromStandardInput.kt
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
package com.avail.interpreter.primitive.general

import com.avail.descriptor.A_Type
import com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.CharacterDescriptor.fromCodePoint
import com.avail.descriptor.FiberDescriptor.ExecutionState
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.SetDescriptor.set
import com.avail.descriptor.TupleDescriptor.emptyTuple
import com.avail.descriptor.TypeDescriptor.Types.CHARACTER
import com.avail.exceptions.AvailErrorCode.E_IO_ERROR
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanSuspend
import com.avail.interpreter.Primitive.Flag.Unknown
import com.avail.io.SimpleCompletionHandler
import java.nio.CharBuffer

/**
 * **Primitive:** Read one character from the standard input stream,
 * [suspending][ExecutionState.SUSPENDED] the [fiber][Interpreter.fiber] until
 * data becomes available.
 */
@Suppress("unused")
object P_ReadFromStandardInput : Primitive(0, CanSuspend, Unknown)
{

	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(0)
		val fiber = interpreter.fiber()
		return interpreter.suspendAndDo { toSucceed, toFail ->
			val buffer = CharBuffer.allocate(1)
			fiber.textInterface().inputChannel.read(
				buffer,
				fiber,
				SimpleCompletionHandler(
					{ _ ->
						toSucceed.value(fromCodePoint(buffer.get(0).toInt()))
						Unit
					},
					{ _ ->
						toFail.value(E_IO_ERROR)
						Unit
					}))
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(emptyTuple(), CHARACTER.o())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(E_IO_ERROR))
}