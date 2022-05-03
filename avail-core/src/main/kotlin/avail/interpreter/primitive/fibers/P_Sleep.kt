/*
 * P_Sleep.kt
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

package avail.interpreter.primitive.fibers

import avail.descriptor.fiber.A_Fiber.Companion.executionState
import avail.descriptor.fiber.A_Fiber.Companion.interruptRequestFlag
import avail.descriptor.fiber.A_Fiber.Companion.wakeupTask
import avail.descriptor.fiber.FiberDescriptor
import avail.descriptor.fiber.FiberDescriptor.ExecutionState
import avail.descriptor.fiber.FiberDescriptor.ExecutionState.ASLEEP
import avail.descriptor.fiber.FiberDescriptor.ExecutionState.SUSPENDED
import avail.descriptor.fiber.FiberDescriptor.InterruptRequestFlag.TERMINATION_REQUESTED
import avail.descriptor.numbers.A_Number.Companion.equalsInt
import avail.descriptor.numbers.A_Number.Companion.extractLong
import avail.descriptor.numbers.A_Number.Companion.isLong
import avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import avail.descriptor.numbers.IntegerDescriptor.Companion.zero
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanSuspend
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.Primitive.Flag.Unknown
import avail.interpreter.execution.Interpreter
import java.util.TimerTask

/**
 * **Primitive:** Put the [current][FiberDescriptor.currentFiber]
 * [fiber][FiberDescriptor] to [sleep][ExecutionState.ASLEEP] for at least the
 * specified number of milliseconds. If the sleep time is zero (`0`), then
 * return immediately. If the sleep time is too big (i.e., greater than the
 * maximum delay supported by the operating system), then sleep forever.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@Suppress("unused")
object P_Sleep : Primitive(1, CannotFail, CanSuspend, Unknown)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val sleepMillis = interpreter.argument(0)
		// If the requested sleep time is 0 milliseconds, then return
		// immediately. We could have chosen to yield here, but it was better to
		// make sleep and yield behave differently.
		if (sleepMillis.equalsInt(0))
		{
			return interpreter.primitiveSuccess(nil)
		}
		val fiber = interpreter.fiber()
		// If the requested sleep time isn't colossally big, then arrange for
		// the fiber to resume later. If the delay is too big, then the fiber
		// will only awaken due to interruption.
		val runtime = interpreter.runtime
		val primitiveFunction = interpreter.function!!
		if (sleepMillis.isLong)
		{
			// Otherwise, delay the resumption of this task.
			val task = object : TimerTask()
			{
				override fun run()
				{
					fiber.lock {
						// Only resume the fiber if it's still asleep. A
						// termination request may have already woken the
						// fiber up, but so recently that it didn't manage
						// to cancel this timer task.
						if (fiber.executionState === ASLEEP)
						{
							fiber.wakeupTask = null
							fiber.executionState = SUSPENDED
							runtime.resumeFromSuccessfulPrimitive(
								fiber, this@P_Sleep, nil)
						}
					}
				}
			}
			// Once the fiber has been unbound, transition it to sleeping and
			// start the timer task.
			interpreter.postExitContinuation {
				fiber.lock {
					// If termination has been requested, then schedule
					// the resumption of this fiber.
					when {
						fiber.interruptRequestFlag(TERMINATION_REQUESTED) -> {
							assert(fiber.executionState === SUSPENDED)
							runtime.resumeFromSuccessfulPrimitive(
								fiber, this, nil)
						}
						else -> {
							fiber.wakeupTask = task
							fiber.executionState = ASLEEP
							runtime.timer.schedule(
								task, sleepMillis.extractLong)
						}
					}
				}
			}
		}
		else
		{
			// Once the fiber has been unbound, transition it to sleeping.
			interpreter.postExitContinuation {
				fiber.lock {
					// If termination has been requested, then schedule
					// the resumption of this fiber.
					when {
						fiber.interruptRequestFlag(TERMINATION_REQUESTED) -> {
							assert(fiber.executionState === SUSPENDED)
							runtime.resumeFromSuccessfulPrimitive(
								fiber, this, nil)
						}
						else -> fiber.executionState = ASLEEP
					}
				}
			}
		}// The delay was too big, so put the fiber to sleep forever.
		// Don't actually transition the fiber to the sleeping state, which
		// can only occur at task-scheduling time. This happens after the
		// fiber is unbound from the interpreter. Instead, suspend the fiber.
		return interpreter.primitiveSuspend(primitiveFunction)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(inclusive(zero, positiveInfinity)), TOP.o)
}
