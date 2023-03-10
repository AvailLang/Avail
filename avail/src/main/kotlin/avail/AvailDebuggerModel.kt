/*
 * AvailDebuggerModel.kt
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

import avail.descriptor.atoms.AtomDescriptor.SpecialAtom.DONT_DEBUG_KEY
import avail.descriptor.fiber.A_Fiber
import avail.descriptor.fiber.A_Fiber.Companion.captureInDebugger
import avail.descriptor.fiber.A_Fiber.Companion.executionState
import avail.descriptor.fiber.A_Fiber.Companion.fiberHelper
import avail.descriptor.fiber.A_Fiber.Companion.heritableFiberGlobals
import avail.descriptor.fiber.A_Fiber.Companion.releaseFromDebugger
import avail.descriptor.fiber.FiberDescriptor
import avail.descriptor.fiber.FiberDescriptor.ExecutionState.PAUSED
import avail.descriptor.fiber.FiberDescriptor.FiberKind
import avail.descriptor.maps.A_Map.Companion.hasKey
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
	 *
	 * If [installFiberCapture] (with the argument `true`) has happened, all
	 * newly launched fibers get added to this list as well.
	 */
	val debuggedFibers = mutableListOf<A_Fiber>()

	/**
	 * Determine whether the current capture function hook for the runtime is
	 * for this debugger.
	 */
	fun isCapturingNewFibers(fiberKind: FiberKind) =
		runtime.newFiberHandlers[fiberKind]!!.get() === fiberCaptureFunction

	/**
	 * Allow the specified fiber to execute exactly one nybblecode.  Supersede
	 * any existing run/step mode for this fiber.  We must be in a safe point to
	 * change this mode.
	 */
	fun singleStep(fiber: A_Fiber)
	{
		runtime.assertInSafePoint()
		runtime.runtimeLock.safeWrite {
			fiber.lock {
				fiber.fiberHelper.let { helper ->
					assert(fiber.fiberHelper.debugger.get() == this)
					var allow = true
					helper.debuggerRunCondition = {
						allow.also { allow = false }
					}
				}
				runtime.resumeIfPausedByDebugger(fiber)
			}
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

	fun resume(fiber: A_Fiber)
	{
		runtime.runtimeLock.safeWrite {
			fiber.releaseFromDebugger()
			debuggedFibers.remove(fiber)
			whenAddedFiberActions.forEach { it(fiber) }
		}
	}

	fun restartFrameThen()
	{
		TODO("Not yet implemented")
	}

	/**
	 * An EnumMap from each [FiberKind] to a function for capturing newly
	 * launched fibers, if it has that FiberKind, and if installed in the
	 * [AvailRuntime.newFiberHandlers].  Note that this function is compared *by
	 * identity* when setting/clearing the new fiber handlers.
	 */
	private val fiberCaptureFunction = { fiber: A_Fiber ->
		// Do nothing if the debugger that launched this fiber (or a creation
		// ancestor) indicated that this fiber should not itself be debugged.
		if (!fiber.heritableFiberGlobals.hasKey(DONT_DEBUG_KEY.atom))
		{
			runtime.whenSafePointDo(FiberDescriptor.debuggerPriority) {
				runtime.runtimeLock.safeWrite {
					debuggedFibers.removeIf {
						it.executionState.indicatesTermination
					}
					if (fiber.fiberHelper.debugger.get() == null)
					{
						fiber.captureInDebugger(this)
						debuggedFibers.add(fiber)
						whenAddedFiberActions.forEach { it(fiber) }
					}
				}
			}
		}
	}

	fun installFiberCapture(kind: FiberKind, install: Boolean): Boolean = when
	{
		install -> runtime.compareAndSetFiberCaptureFunction(
			kind, null, fiberCaptureFunction)
		else -> runtime.compareAndSetFiberCaptureFunction(
			kind, fiberCaptureFunction, null)
	}

	/**
	 * A publicly accessible list of functions to call when a fiber has reached
	 * its pause condition.
	 */
	val whenPausedActions = mutableListOf<(A_Fiber) -> Unit>()

	/**
	 * A publicly accessible list of functions to call when a new fiber is to be
	 * added to the list of tracked fibers.
	 */
	val whenAddedFiberActions = mutableListOf<(A_Fiber) -> Unit>()

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
				val newFibers = fibersProvider()
					.filter {
						it.fiberHelper.debugger.get() === null
							&& !it.executionState.indicatesTermination
							&& !it.heritableFiberGlobals.hasKey(
								DONT_DEBUG_KEY.atom)
					}
					.sortedBy { it.fiberHelper.debugUniqueId }
				debuggedFibers.addAll(newFibers)
				newFibers.forEach { fiber ->
					assert(fiber.fiberHelper.debugger.get() === null)
					fiber.captureInDebugger(this)
					fiber.fiberHelper.debuggerRunCondition = {
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
				// Release each fiber from this debugger, allowing it to run if
				// it was runnable.
				debuggedFibers.forEach { it.releaseFromDebugger() }
				debuggedFibers.clear()
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
