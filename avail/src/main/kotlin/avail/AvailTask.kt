/*
 * AvailTask.kt
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
package avail

import avail.descriptor.fiber.A_Fiber
import avail.descriptor.fiber.A_Fiber.Companion.executionState
import avail.descriptor.fiber.A_Fiber.Companion.failureContinuation
import avail.descriptor.fiber.A_Fiber.Companion.fiberHelper
import avail.descriptor.fiber.A_Fiber.Companion.fiberResult
import avail.descriptor.fiber.A_Fiber.Companion.getAndSetSynchronizationFlag
import avail.descriptor.fiber.A_Fiber.Companion.resultContinuation
import avail.descriptor.fiber.FiberDescriptor
import avail.descriptor.fiber.FiberDescriptor.ExecutionState
import avail.descriptor.fiber.FiberDescriptor.ExecutionState.ABORTED
import avail.descriptor.fiber.FiberDescriptor.ExecutionState.RUNNING
import avail.descriptor.fiber.FiberDescriptor.SynchronizationFlag
import avail.exceptions.PrimitiveThrownException
import avail.interpreter.execution.Interpreter
import avail.interpreter.execution.Interpreter.Companion.current

/**
 * An `AvailTask` extends [Runnable] with a priority. Instances are intended to
 * be executed only by [Avail&#32;threads][AvailThread].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property priority
 *   The priority of the [task][AvailTask].  It must be a value in the range
 *   `[0..255]`.  See [quasiDeadline].
 * @constructor
 *   Construct a new `AvailTask`.
 *
 * @param priority
 *   The priority, a value in `[0..255]`.  Tasks with higher numerical values
 *   typically run before those with lower numerical values.  See
 *   [quasiDeadline].
 * @param body
 *   The action to execute for this task.
 */
class AvailTask constructor(
	val priority: Int,
	body: () -> Unit) : Comparable<AvailTask>, Runnable
{
	/** The action to perform for this task. */
	private val body: () -> Unit

	/**
	 * The quasi-deadline of the task.  This is the moment that the task can no
	 * longer be preceded by a new task.  Given a priority in the range 0..255,
	 * the quasi-deadline is System.currentTimeMillis() plus a time delta.  The
	 * delta is 1000ms * (1 - (priority / 256)).  That places it one second in
	 * the future for priority = 0, and places it essentially at the current
	 * moment for priority = 255.
	 */
	private val quasiDeadline: Long

	override fun compareTo(other: AvailTask): Int =
		quasiDeadline.compareTo(other.quasiDeadline)

	override fun run()
	{
		try
		{
			body()
		}
		catch (e: Throwable)
		{
			// Report the exception immediately, then suppress the error.  If we
			// allowed the error to propagate, it would cause an Interpreter to
			// be silently lost, and the next creation could overflow the
			// maximum number of Interpreters.
			System.err.println("Unexpected internal failure in AvailTask:\n")
			e.printStackTrace()
		}
	}

	companion object
	{
		/**
		 * Answer an `AvailTask` suitable for resuming the specified
		 * [fiber][FiberDescriptor] using the specified action. If the
		 * continuation fails for any reason, then it
		 * [aborts][Interpreter.abortFiber] the fiber and invokes the [failure
		 * continuation][A_Fiber.failureContinuation] with the terminal
		 * [throwable][Throwable].
		 *
		 * @param fiber
		 *   A fiber.
		 * @param body
		 *   What to do to resume execution of the fiber.
		 * @return
		 *   An action that sets the execution state of the fiber to
		 *   [running][ExecutionState.RUNNING], binds it to the running
		 *   [thread][AvailThread]'s [interpreter][Interpreter], and then runs
		 *   the specified continuation.
		 */
		fun forFiberResumption(
			fiber: A_Fiber,
			body: Interpreter.() -> Unit): () -> Unit
		{
			assert(fiber.executionState.indicatesSuspension)
			val scheduled = fiber.getAndSetSynchronizationFlag(
				SynchronizationFlag.SCHEDULED, true)
			assert(!scheduled)
			return {
				val interpreter = current()
				assert(interpreter.fiberOrNull() === null)
				fiber.lock {
					assert(fiber.executionState.indicatesSuspension)
					val bound = fiber.getAndSetSynchronizationFlag(
						SynchronizationFlag.BOUND, true)
					fiber.fiberHelper.startCountingCPU()
					assert(!bound)
					val wasScheduled = fiber.getAndSetSynchronizationFlag(
						SynchronizationFlag.SCHEDULED, false)
					assert(wasScheduled)
					fiber.executionState = RUNNING
					interpreter.fiber(fiber, "forFiberResumption")
				}
				try
				{
					interpreter.body()
				}
				catch (e: PrimitiveThrownException)
				{
					// If execution failed, terminate the fiber and invoke its
					// failure continuation with the throwable.
					interpreter.adjustUnreifiedCallDepthBy(
						-interpreter.unreifiedCallDepth())
					if (!fiber.executionState.indicatesTermination)
					{
						assert(interpreter.fiberOrNull() === fiber)
						interpreter.abortFiber()
					}
					else
					{
						fiber.executionState = ABORTED
					}
					(fiber.failureContinuation)(e)
					fiber.executionState = ExecutionState.RETIRED
					interpreter.runtime.unregisterFiber(fiber)
				}
				catch (e: Throwable)
				{
					System.err.println(
						"An unrecoverable VM error has occurred.")
					throw e
				}
				finally
				{
					// This is the first point at which *some other* Thread may
					// have had a chance to resume the fiber and update its
					// state.
					val postExit = interpreter.postExitContinuation
					if (postExit !== null)
					{
						interpreter.postExitContinuation(null)
						postExit()
					}
				}
				// If the fiber has terminated, then report its result via its
				// result continuation.
				fiber.lock {
					if (fiber.executionState === ExecutionState.TERMINATED)
					{
						(fiber.resultContinuation)(fiber.fiberResult)
						fiber.executionState = ExecutionState.RETIRED
						interpreter.runtime.unregisterFiber(fiber)
					}
				}
				assert(interpreter.fiberOrNull() === null)
			}
		}

		/**
		 * Answer an `AvailTask` suitable for performing activities on behalf of
		 * an unbound [ExecutionState.SUSPENDED] [fiber][FiberDescriptor]. If
		 * the continuation fails for any reason, then it transitions the fiber
		 * to the [aborted][ExecutionState.ABORTED] state and invokes the
		 * fiber's [failure continuation][A_Fiber.failureContinuation] with
		 * the terminal [throwable][Throwable].
		 *
		 * @param fiber
		 *   A fiber.
		 * @param action
		 *   What to do to on behalf of the unbound fiber.
		 * @return
		 *   An action that runs the provided continuation and handles any
		 *   errors appropriately.
		 */
		fun forUnboundFiber(
			fiber: A_Fiber,
			action: () -> Unit): () -> Unit
		{
			assert(fiber.executionState === ExecutionState.SUSPENDED)
			val scheduled = fiber.getAndSetSynchronizationFlag(
				SynchronizationFlag.SCHEDULED, true)
			assert(!scheduled)
			return {
				val wasScheduled = fiber.getAndSetSynchronizationFlag(
					SynchronizationFlag.SCHEDULED, false)
				assert(wasScheduled)
				try
				{
					action()
				}
				catch (e: Throwable)
				{
					// If execution failed for any reason, then terminate the
					// fiber and invoke its failure continuation with the
					// throwable.
					fiber.executionState = ABORTED
					(fiber.failureContinuation)(e)
				}
				assert(current().fiberOrNull() === null)
			}
		}
	}
	init
	{
		assert(priority in 0 .. 255)
		val deltaNanos = 1_000_000_000L * (255 - priority) shr 8
		quasiDeadline = System.nanoTime() + deltaNanos
		this.body = body
	}
}
