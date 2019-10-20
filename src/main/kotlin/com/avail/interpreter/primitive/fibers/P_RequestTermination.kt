/*
 * P_RequestTermination.kt
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
package com.avail.interpreter.primitive.fibers

import com.avail.AvailRuntime.currentRuntime
import com.avail.descriptor.A_Type
import com.avail.descriptor.FiberDescriptor
import com.avail.descriptor.FiberDescriptor.ExecutionState
import com.avail.descriptor.FiberDescriptor.ExecutionState.*
import com.avail.descriptor.FiberDescriptor.InterruptRequestFlag.TERMINATION_REQUESTED
import com.avail.descriptor.FiberDescriptor.SynchronizationFlag.PERMIT_UNAVAILABLE
import com.avail.descriptor.FiberTypeDescriptor.mostGeneralFiberType
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Interpreter.resumeFromSuccessfulPrimitive
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*
import com.avail.utility.Nulls.stripNull

/**
 * **Primitive:** Request termination of the given [fiber][FiberDescriptor]. If
 * the fiber is currently [parked][ExecutionState.PARKED] or
 * [asleep][ExecutionState.ASLEEP], then unpark it.
 */
@Suppress("unused")
object P_RequestTermination : Primitive(1, CanInline, CannotFail, HasSideEffect)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val fiber = interpreter.argument(0)
		fiber.lock {
			// Set the interrupt request flag.
			fiber.setInterruptRequestFlag(TERMINATION_REQUESTED)
			val oldState = fiber.executionState()
			val hadPermit = !fiber.getAndSetSynchronizationFlag(
				PERMIT_UNAVAILABLE, false)
			when (oldState)
			{
				ASLEEP ->
				{
					// Try to cancel the task (if any). This is best
					// effort only.
					val task = fiber.wakeupTask()
					if (task !== null)
					{
						task.cancel()
						fiber.wakeupTask(null)
					}
					fiber.executionState(SUSPENDED)
					val fiberSuspendingPrimitive = stripNull(
						fiber.suspendingFunction().code().primitive())
					resumeFromSuccessfulPrimitive(
						currentRuntime(),
						fiber,
						fiberSuspendingPrimitive,
						nil)
				}
				PARKED ->
				{
					// Resume the fiber.
					assert(!hadPermit) {
						"Should not have been parked with a permit"
					}
					fiber.executionState(SUSPENDED)
					val suspendingPrimitive = stripNull(
						fiber.suspendingFunction().code().primitive())
					assert(suspendingPrimitive === P_ParkCurrentFiber
						|| suspendingPrimitive === P_AttemptJoinFiber)
					resumeFromSuccessfulPrimitive(
						currentRuntime(),
						fiber,
						suspendingPrimitive,
						nil)
				}
				else -> { }
			}
		}
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(mostGeneralFiberType()), TOP.o())
}