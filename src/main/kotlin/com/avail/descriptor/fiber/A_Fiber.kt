/*
 * A_Fiber.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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
package com.avail.descriptor.fiber

import com.avail.descriptor.fiber.FiberDescriptor.ExecutionState
import com.avail.descriptor.fiber.FiberDescriptor.GeneralFlag
import com.avail.descriptor.fiber.FiberDescriptor.InterruptRequestFlag
import com.avail.descriptor.fiber.FiberDescriptor.SynchronizationFlag
import com.avail.descriptor.fiber.FiberDescriptor.TraceFlag
import com.avail.descriptor.functions.A_Continuation
import com.avail.descriptor.functions.A_Function
import com.avail.descriptor.functions.ContinuationDescriptor
import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.pojos.PojoDescriptor
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.sets.SetDescriptor
import com.avail.descriptor.tuples.A_String
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.variables.A_Variable
import com.avail.descriptor.variables.VariableDescriptor
import com.avail.interpreter.execution.AvailLoader
import com.avail.io.TextInterface
import java.util.*

/**
 * `A_Fiber` is an interface that specifies the fiber-specific operations that
 * an [AvailObject] must implement.  It's a sub-interface of [A_BasicObject],
 * the interface that defines the behavior that all AvailObjects are required to
 * support.
 *
 * The purpose for A_BasicObject and its sub-interfaces is to allow sincere type
 * annotations about the basic kinds of objects that support or may be passed as
 * arguments to various operations.  The VM is free to always declare objects as
 * AvailObject, but in cases where it's clear that a particular object must
 * always be a fiber, a declaration of A_Fiber ensures that only the basic
 * object capabilities plus fiber-like capabilities are to be allowed.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_Fiber : A_BasicObject {
	/**
	 * @return
	 */
	fun availLoader(): AvailLoader?

	/**
	 * @param loader
	 */
	fun setAvailLoader(loader: AvailLoader?)

	/**
	 * Dispatch to the descriptor.
	 */
	fun breakpointBlock(): A_BasicObject

	/**
	 * Dispatch to the descriptor.
	 */
	fun setBreakpointBlock(value: AvailObject)

	/**
	 * @param flag
	 */
	fun clearGeneralFlag(flag: GeneralFlag)

	/**
	 * Dispatch to the descriptor.
	 */
	fun continuation(): A_Continuation

	/**
	 * Dispatch to the descriptor.
	 */
	fun setContinuation(value: A_Continuation)

	/**
	 * Dispatch to the descriptor.
	 */
	fun debugLog(): StringBuilder

	/**
	 * Dispatch to the descriptor.
	 */
	fun executionState(): ExecutionState

	/**
	 * Dispatch to the descriptor.
	 */
	fun setExecutionState(value: ExecutionState)

	/**
	 * @return
	 */
	fun failureContinuation(): (Throwable) -> Unit

	/**
	 * Dispatch to the descriptor.
	 */
	fun fiberGlobals(): A_Map

	/**
	 * Dispatch to the descriptor.
	 */
	fun setFiberGlobals(value: A_Map)

	/**
	 * @return
	 */
	fun fiberName(): A_String

	/**
	 * @param supplier
	 */
	fun fiberNameSupplier(supplier: () -> A_String)

	/**
	 * @return
	 */
	fun fiberResult(): AvailObject

	/**
	 * @param result
	 */
	fun setFiberResult(result: A_BasicObject)

	/**
	 * @return
	 */
	fun fiberResultType(): A_Type

	/**
	 * @param flag
	 * @return
	 */
	fun generalFlag(flag: GeneralFlag): Boolean

	/**
	 * @param flag
	 * @return
	 */
	fun getAndClearInterruptRequestFlag(flag: InterruptRequestFlag): Boolean

	/**
	 * @param flag
	 * @param value
	 * @return
	 */
	fun getAndSetSynchronizationFlag(
		flag: SynchronizationFlag,
		value: Boolean): Boolean

	/**
	 * @param flag
	 * @return
	 */
	fun traceFlag(flag: TraceFlag): Boolean

	/**
	 * @param flag
	 */
	fun setTraceFlag(flag: TraceFlag)

	/**
	 * @param flag
	 */
	fun clearTraceFlag(flag: TraceFlag)

	/**
	 * @return
	 */
	fun heritableFiberGlobals(): A_Map

	/**
	 * @param globals
	 */
	fun setHeritableFiberGlobals(globals: A_Map)

	/**
	 * @param flag
	 * @return
	 */
	fun interruptRequestFlag(flag: InterruptRequestFlag): Boolean

	/**
	 * @return
	 */
	fun joiningFibers(): A_Set

	/**
	 * @param joiners
	 */
	fun setJoiningFibers(joiners: A_Set)

	/**
	 * Answer this fiber's current priority.
	 *
	 * @return
	 *   The priority.
	 */
	fun priority(): Int

	/**
	 * Change this fiber's current priority.
	 *
	 * @param value
	 *   The new priority.
	 */
	fun setPriority(value: Int)

	/**
	 * @return
	 */
	fun resultContinuation(): (AvailObject) -> Unit

	/**
	 * @param flag
	 */
	fun setGeneralFlag(flag: GeneralFlag)

	/**
	 * Set the success and failure actions of this fiber.  The former
	 * runs if the fiber succeeds, passing the resulting [AvailObject], and also
	 * stashing it in the fiber.  The latter runs if the fiber fails, passing
	 * the [Throwable] that caused the failure.
	 *
	 * @param onSuccess
	 *   The action to invoke with the fiber's result value.
	 * @param onFailure
	 *   The action to invoke with the responsible throwable.
	 */
	fun setSuccessAndFailureContinuations(
		onSuccess: (AvailObject) -> Unit,
		onFailure: (Throwable) -> Unit)

	/**
	 * @param flag
	 */
	fun setInterruptRequestFlag(flag: InterruptRequestFlag)

	/**
	 * @return
	 */
	fun wakeupTask(): TimerTask?

	/**
	 * @param task
	 */
	fun setWakeupTask(task: TimerTask?)

	/**
	 * Record access of the specified [variable][VariableDescriptor] by this
	 * [fiber][FiberDescriptor].
	 *
	 * @param variable
	 *   A variable.
	 * @param wasRead
	 *   `true` if the variable was read, `false` otherwise.
	 */
	fun recordVariableAccess(
		variable: A_Variable,
		wasRead: Boolean)

	/**
	 * Answer the [set][SetDescriptor] of [variables][VariableDescriptor] that
	 * were read before written. Only variables still live are included in this
	 * set; the [trace][TraceFlag.TRACE_VARIABLE_READS_BEFORE_WRITES] mechanism
	 * retains variables only weakly.
	 *
	 * @return
	 *   The requested variables.
	 */
	fun variablesReadBeforeWritten(): A_Set

	/**
	 * Answer the [set][SetDescriptor] of [variables][VariableDescriptor] that
	 * were written. Only variables still live are included in this set; the
	 * [trace][TraceFlag.TRACE_VARIABLE_READS_BEFORE_WRITES] mechanism retains
	 * variables only weakly.
	 *
	 * @return
	 *   The requested variables.
	 */
	fun variablesWritten(): A_Set

	/**
	 * Ensure the specified action is invoked with this fiber's reified
	 * [continuation][ContinuationDescriptor] as soon as it's available.  Note
	 * that this triggers an interrupt on the fiber to ensure a timely capture
	 * of the stack.
	 *
	 * @param whenReified
	 *   What to run with the Avail [continuation][ContinuationDescriptor].
	 */
	fun whenContinuationIsAvailableDo(
		whenReified: (A_Continuation) -> Unit)

	/**
	 * Extract the current [A_Set] of [pojo][PojoDescriptor]-wrapped
	 * actions to perform when this fiber is next reified.  Replace it with the
	 * empty set.
	 *
	 * @return
	 *   The set of outstanding actions, prior to clearing it.
	 */
	fun getAndClearReificationWaiters(): A_Set

	/**
	 * Answer the [TextInterface] for this fiber.
	 *
	 * @return
	 *   The fiber's text interface.
	 */
	fun textInterface(): TextInterface

	/**
	 * Set the [TextInterface] for this fiber.
	 *
	 * @param textInterface
	 *   A text interface.
	 */
	fun setTextInterface(textInterface: TextInterface)

	/**
	 * Answer the unique identifier of this `A_Fiber fiber`.
	 *
	 * @return
	 *   The unique identifier, a `long`.
	 */
	fun uniqueId(): Long

	/**
	 * Set the [A_Function] that's suspending the fiber.
	 *
	 * @param suspendingFunction
	 *   The function that's suspending the fiber.
	 */
	fun setSuspendingFunction(suspendingFunction: A_Function)

	/**
	 * Answer the [A_Function] that was saved in the fiber when it was
	 * suspended.
	 *
	 * @return
	 *   The function that suspended the fiber.
	 */
	fun suspendingFunction(): A_Function
}
