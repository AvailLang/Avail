/*
 * P_ParkCurrentFiber.kt
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

package avail.interpreter.primitive.fibers

import avail.descriptor.fiber.A_Fiber.Companion.getAndSetSynchronizationFlag
import avail.descriptor.fiber.FiberDescriptor
import avail.descriptor.fiber.FiberDescriptor.ExecutionState
import avail.descriptor.fiber.FiberDescriptor.SynchronizationFlag
import avail.descriptor.fiber.FiberDescriptor.SynchronizationFlag.PERMIT_UNAVAILABLE
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanSuspend
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.Primitive.Flag.Unknown
import avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Attempt to acquire the
 * [permit][SynchronizationFlag.PERMIT_UNAVAILABLE] associated with the
 * [current][FiberDescriptor.currentFiber] [fiber][FiberDescriptor]. If the
 * permit is available, then consume it and return immediately. If the permit is
 * not available, then [park][ExecutionState.PARKED] the current fiber. A fiber
 * suspended in this fashion may be resumed only by calling [P_UnparkFiber]. A
 * newly unparked fiber should always recheck the basis for its having parked,
 * to see if it should park again. Low-level synchronization mechanisms may
 * require the ability to spuriously unpark in order to ensure correctness.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_ParkCurrentFiber : Primitive(0, CannotFail, CanSuspend, Unknown)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(0)
		val fiber = interpreter.fiber()
		return fiber.lock {
			// If permit is not available, then park this fiber.
			when {
				fiber.getAndSetSynchronizationFlag(PERMIT_UNAVAILABLE, true) ->
					interpreter.primitivePark(interpreter.function!!)
				else -> interpreter.primitiveSuccess(nil)
			}
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(emptyTuple, TOP.o)
}
