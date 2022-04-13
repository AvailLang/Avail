/*
 * AvailDebuggerModel.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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
import avail.descriptor.fiber.A_Fiber.Companion.fiberHelper
import avail.descriptor.fiber.A_Fiber.Companion.releaseFromDebugger
import avail.descriptor.fiber.FiberDescriptor
import avail.descriptor.fiber.FiberDescriptor.ExecutionState.PAUSED
import avail.interpreter.execution.Interpreter
import avail.performance.Statistic
import avail.performance.StatisticReport
import avail.utility.safeWrite

/**
 * [AvailDebuggerModel] controls the execution of a set of fibers, allowing
 * exploration of the fibers' state.  A separate user interface should drive
 * this model.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property runtime
 *   The [AvailRuntime] in which the debugged fibers are running.
 *
 * @constructor
 * Construct a new [AvailDebuggerModel].
 */
class AvailDebuggerModel constructor (
	val runtime: AvailRuntime)
{
	/**
	 * This gets set by [gatherFibersThen] to the [List] of all [A_Fiber]s that
	 * have not yet been captured by other debuggers.  The fibers likewise get a
	 * reference to this debugger, so they can determine how/whether to run
	 * during stepping operations.
	 */
	var debuggedFibers = emptyList<A_Fiber>()

	/**
	 * Allow the specified fiber to execute exactly one nybblecode.  Supersede
	 * any existing run/step mode for this fiber.  We must be in a safe point to
	 * change this mode.
	 */
	fun singleStep(fiber: A_Fiber)
	{
		runtime.assertInSafePoint()
		fiber.lock {
			fiber.fiberHelper.let { helper ->
				assert(fiber.fiberHelper.debugger == this)
				var allow = true
				helper.debuggerRunCondition = {
					allow.also { allow = false }
				}
			}
			Interpreter.resumeIfPausedByDebugger(fiber)
		}
	}

	//TODO Rework and wire in these actions.

	fun stepOverThen()
	{
		TODO("Not yet implemented")
	}

	fun stepOutThen()
	{
		TODO("Not yet implemented")
	}

	fun stepToLineThen()
	{
		TODO("Not yet implemented")
	}

	fun resumeThen()
	{
		TODO("Not yet implemented")
	}

	fun restartFrameThen()
	{
		TODO("Not yet implemented")
	}

	/**
	 * A publicly accessible list of functions to call when a fiber has reached
	 * its pause condition.
	 */
	val whenPausedActions = mutableListOf<(A_Fiber) -> Unit>()

	/**
	 * The given [fiber] just reached the pause condition, and has transitioned
	 * to the [PAUSED] state.  Enter a safe point and invoke each
	 * [whenPausedActions].
	 */
	fun justPaused(fiber: A_Fiber)
	{
		runtime.whenSafePointDo(FiberDescriptor.debuggerPriority) {
			runtime.runtimeLock.safeWrite {
				whenPausedActions.forEach { it(fiber) }
			}
		}
	}

	/**
	 * For every existing fiber that isn't already captured by another debugger,
	 * bind that fiber to this debugger.  Those fibers are not permitted to
	 * run unless *this* debugger says they may.  Any fibers launched after this
	 * point (say, to compute a print representation or evaluate an expression)
	 * will *not* be captured by this debugger.
	 *
	 * Note that this operation will block the current thread (which should be
	 * a UI-spawned thread) while holding the runtime at a safe point, to ensure
	 * no other fibers are running, and to ensure other debuggers don't conflict
	 * with this one.
	 *
	 * @param then
	 *   An action to execute (in a different [Thread]) after the fibers have
	 *   been gathered.
	 */
	fun gatherFibersThen(
		fibersProvider: () -> Collection<A_Fiber>,
		then: () -> Unit)
	{
		runtime.whenSafePointDo(FiberDescriptor.debuggerPriority) {
			// Prevent other debuggers from accessing the set of fibers.
			runtime.runtimeLock.safeWrite {
				debuggedFibers = fibersProvider()
					.filter {
						it.fiberHelper.debugger == null
							&& !it.executionState.indicatesTermination
					}
					.sortedBy { it.fiberHelper.debugUniqueId }
				debuggedFibers.forEach { fiber ->
					val helper = fiber.fiberHelper
					helper.debugger = this
					helper.debuggerRunCondition = {
						false
					}
				}
			}
			then()
		}
	}

	/**
	 * Enter a safe point, then for each captured fiber, un-capture it, and
	 * unpause it if it was paused for the debugger.  After all fibers have been
	 * released, invoke the callback, still within the safe point.
	 */
	fun releaseFibersThen(then: () -> Unit)
	{
		runtime.whenSafePointDo(FiberDescriptor.debuggerPriority) {
			runtime.runtimeLock.safeWrite {
				debuggedFibers.forEach {
					// Release each fiber from this debugger, allowing it to run
					// if it was runnable.
					it.releaseFromDebugger()
				}
				debuggedFibers = emptyList()
			}
			then()
		}
	}

	companion object
	{
		/** [Statistic] for debugger-triggered reification. */
		val reificationForDebuggerStat = Statistic(
			StatisticReport.REIFICATIONS, "Reification for debugger")
	}
}