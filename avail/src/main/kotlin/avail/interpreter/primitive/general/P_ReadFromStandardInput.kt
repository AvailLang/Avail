/*
 * P_ReadFromStandardInput.kt
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
package avail.interpreter.primitive.general

import avail.descriptor.character.CharacterDescriptor.Companion.fromCodePoint
import avail.descriptor.fiber.A_Fiber.Companion.textInterface
import avail.descriptor.fiber.FiberDescriptor.ExecutionState
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.CHARACTER
import avail.exceptions.AvailErrorCode.E_IO_ERROR
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.Primitive.Flag.CanSuspend
import avail.interpreter.primitive.Primitive.Flag.Unknown
import avail.interpreter.primitive.Primitive0
import avail.io.SimpleCompletionHandler
import java.nio.CharBuffer

/**
 * **Primitive:** Read one character from the standard input stream,
 * [suspending][ExecutionState.SUSPENDED] the [fiber][Interpreter.fiber] until
 * data becomes available.
 */
@Suppress("unused")
object P_ReadFromStandardInput : Primitive0(CanSuspend, Unknown)
{
	override fun Interpreter.attempt0(): A_BasicObject?
	{
		val fiber = fiber()
		return suspendThen {
			runtime.ioSystem.executeFileTask {
				val buffer = CharBuffer.allocate(1)
				SimpleCompletionHandler<Int>(
					{ succeed(fromCodePoint(buffer.get(0).code)) },
					{ fail(E_IO_ERROR) }
				).guardedDo {
					fiber.textInterface.inputChannel.read(
						buffer, Unit, handler)
				}
			}
		}
	}

	override fun privateBlockTypeRestriction (): A_Type =
		functionType(emptyTuple, CHARACTER())

	override fun privateFailureVariableType (): A_Type =
		enumerationWith(set(E_IO_ERROR))
}
