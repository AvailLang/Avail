/*
 * P_RequestTermination.kt
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

import avail.descriptor.fiber.A_Fiber.Companion.executionState
import avail.descriptor.fiber.A_Fiber.Companion.getAndSetSynchronizationFlag
import avail.descriptor.fiber.A_Fiber.Companion.setInterruptRequestFlag
import avail.descriptor.fiber.A_Fiber.Companion.suspendingFunction
import avail.descriptor.fiber.A_Fiber.Companion.wakeupTask
import avail.descriptor.fiber.FiberDescriptor
import avail.descriptor.fiber.FiberDescriptor.ExecutionState
import avail.descriptor.fiber.FiberDescriptor.ExecutionState.ASLEEP
import avail.descriptor.fiber.FiberDescriptor.ExecutionState.PARKED
import avail.descriptor.fiber.FiberDescriptor.ExecutionState.SUSPENDED
import avail.descriptor.fiber.FiberDescriptor.InterruptRequestFlag.TERMINATION_REQUESTED
import avail.descriptor.fiber.FiberDescriptor.SynchronizationFlag.PERMIT_UNAVAILABLE
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.FiberTypeDescriptor.Companion.mostGeneralFiberType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.Primitive.Flag.HasSideEffect
import avail.interpreter.Primitive.Flag.WritesToHiddenGlobalState
import avail.interpreter.execution.Interpreter

/**
 * **Primitive:** Request termination of the given [fiber][FiberDescriptor]. If
 * the fiber is currently [parked][ExecutionState.PARKED] or
 * [asleep][ExecutionState.ASLEEP], then unpark it.
 */
@Suppress("unused")
object P_RequestTermination : Primitive(
	1, CanInline, CannotFail, HasSideEffect, WritesToHiddenGlobalState)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val fiber = interpreter.argument(0)
		with (fiber) {
			lock {
				// Set the interrupt request flag.
				setInterruptRequestFlag(TERMINATION_REQUESTED)
				val oldState = executionState
				val hadPermit =
					!getAndSetSynchronizationFlag(PERMIT_UNAVAILABLE, false)
				when (oldState) {
					ASLEEP ->
					{
						// Try to cancel the task (if any). This is best
						// effort only.
						val task = wakeupTask
						if (task !== null)
						{
							task.cancel()
							wakeupTask = null
						}
						executionState = SUSPENDED
						val fiberSuspendingPrimitive =
							suspendingFunction.code().codePrimitive()!!
						interpreter.runtime.resumeFromSuccessfulPrimitive(
							fiber, fiberSuspendingPrimitive, nil)
					}
					PARKED ->
					{
						// Resume the fiber.
						assert(!hadPermit) {
							"Should not have been parked with a permit"
						}
						executionState = SUSPENDED
						val suspendingPrimitive =
							suspendingFunction.code().codePrimitive()!!
						assert(
							suspendingPrimitive === P_ParkCurrentFiber
								|| suspendingPrimitive === P_AttemptJoinFiber)
						interpreter.runtime.resumeFromSuccessfulPrimitive(
							fiber, suspendingPrimitive, nil)
					}
					else -> { }
				}
			}
		}
		return interpreter.primitiveSuccess(nil)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(mostGeneralFiberType()), TOP.o)
}
