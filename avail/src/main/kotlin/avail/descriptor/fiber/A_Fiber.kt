/*
 * A_Fiber.kt
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
package avail.descriptor.fiber

import avail.AvailDebuggerModel
import avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom.IS_STYLING
import avail.descriptor.fiber.FiberDescriptor.Companion.newStylerFiber
import avail.descriptor.fiber.FiberDescriptor.ExecutionState
import avail.descriptor.fiber.FiberDescriptor.GeneralFlag
import avail.descriptor.fiber.FiberDescriptor.InterruptRequestFlag
import avail.descriptor.fiber.FiberDescriptor.SynchronizationFlag
import avail.descriptor.fiber.FiberDescriptor.TraceFlag
import avail.descriptor.functions.A_Continuation
import avail.descriptor.functions.A_Function
import avail.descriptor.functions.ContinuationDescriptor
import avail.descriptor.maps.A_Map
import avail.descriptor.maps.A_Map.Companion.mapAtOrNull
import avail.descriptor.parsing.A_Lexer
import avail.descriptor.pojos.PojoDescriptor
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_BasicObject.Companion.dispatch
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.A_Set
import avail.descriptor.sets.SetDescriptor
import avail.descriptor.tuples.A_String
import avail.descriptor.types.A_Type
import avail.descriptor.variables.A_Variable
import avail.descriptor.variables.VariableDescriptor
import avail.interpreter.execution.AvailLoader
import avail.io.TextInterface
import avail.utility.notNullAnd
import java.util.TimerTask

/**
 * [A_Fiber] is an interface that specifies the fiber-specific operations that
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
interface A_Fiber : A_BasicObject
{
	companion object
	{
		/**
		 * Answer the [loader][AvailLoader] bound to the
		 * [receiver][FiberDescriptor], or `null` if the receiver is not a
		 * loader fiber.
		 */
		var A_Fiber.availLoader: AvailLoader?
			get() = dispatch { o_AvailLoader(it) }
			set(value) = dispatch { o_SetAvailLoader(it, value) }

		/**
		 * @param flag
		 */
		fun A_Fiber.clearGeneralFlag(flag: GeneralFlag) =
			dispatch { o_ClearGeneralFlag(it, flag) }

		/**
		 * The fiber's current [A_Continuation] if suspended, otherwise [nil].
		 */
		var A_Fiber.continuation: A_Continuation
			get() = dispatch { o_Continuation(it) }
			set(value) = dispatch { o_SetContinuation(it, value) }

		/**
		 * Dispatch to the descriptor.
		 */
		val A_Fiber.debugLog: StringBuilder
			get() = dispatch { o_DebugLog(it) }

		/**
		 * Dispatch to the descriptor.
		 */
		var A_Fiber.executionState: ExecutionState
			get() = dispatch { o_ExecutionState(it) }
			set(value) = dispatch { o_SetExecutionState(it, value) }

		/**
		 * Answer the continuation that accepts the [Throwable] responsible for
		 * abnormal termination of this fiber.
		 *
		 * @return
		 *   A continuation.
		 */
		val A_Fiber.failureContinuation: (Throwable)->Unit
			get() = dispatch { o_FailureContinuation(it) }

		/**
		 * Dispatch to the descriptor.
		 */
		var A_Fiber.fiberGlobals: A_Map
			get() = dispatch { o_FiberGlobals(it) }
			set(value) = dispatch { o_SetFiberGlobals(it, value) }

		/**
		 * @return
		 */
		val A_Fiber.fiberName: A_String
			get() = dispatch { o_FiberName(it) }

		/**
		 * @param supplier
		 */
		fun A_Fiber.fiberNameSupplier(supplier: ()->A_String) =
			dispatch { o_FiberNameSupplier(it, supplier) }

		/**
		 * @return
		 */
		var A_Fiber.fiberResult: AvailObject
			get() = dispatch { o_FiberResult(it) }
			set(value) = dispatch { o_SetFiberResult(it, value) }

		/**
		 * @return
		 */
		val A_Fiber.fiberResultType: A_Type
			get() = dispatch { o_FiberResultType(it) }

		/**
		 * @param flag
		 * @return
		 */
		fun A_Fiber.generalFlag(flag: GeneralFlag): Boolean =
			dispatch { o_GeneralFlag(it, flag) }

		/**
		 * @param flag
		 * @return
		 */
		fun A_Fiber.getAndClearInterruptRequestFlag(
			flag: InterruptRequestFlag
		): Boolean =
			dispatch { o_GetAndClearInterruptRequestFlag(it, flag) }

		/**
		 * @param flag
		 * @param value
		 * @return
		 */
		fun A_Fiber.getAndSetSynchronizationFlag(
			flag: SynchronizationFlag,
			value: Boolean
		): Boolean =
			dispatch { o_GetAndSetSynchronizationFlag(it, flag, value) }

		/**
		 * @param flag
		 * @return
		 */
		fun A_Fiber.traceFlag(flag: TraceFlag): Boolean =
			dispatch { o_TraceFlag(it, flag) }

		/**
		 * @param flag
		 */
		fun A_Fiber.setTraceFlag(flag: TraceFlag) =
			dispatch { o_SetTraceFlag(it, flag) }

		/**
		 * @param flag
		 */
		fun A_Fiber.clearTraceFlag(flag: TraceFlag) =
			dispatch { o_ClearTraceFlag(it, flag) }

		/**
		 * @return
		 */
		var A_Fiber.heritableFiberGlobals: A_Map
			get() = dispatch { o_HeritableFiberGlobals(it) }
			set(value) = dispatch { o_SetHeritableFiberGlobals(it, value) }

		/**
		 * Is the specified interrupt request [flag][InterruptRequestFlag] set
		 * for the [receiver][FiberDescriptor]?
		 *
		 * @param flag
		 *   An interrupt request flag.
		 * @return
		 *   `true` if the interrupt request flag is set, `false` otherwise.
		 */
		fun A_Fiber.interruptRequestFlag(flag: InterruptRequestFlag): Boolean =
			dispatch { o_InterruptRequestFlag(it, flag) }

		/**
		 * The [A_Set] of [A_Fiber]s waiting for this one to end.
		 */
		var A_Fiber.joiningFibers: A_Set
			get() = dispatch { o_JoiningFibers(it) }
			set(value) = dispatch { o_SetJoiningFibers(it, value) }

		/**
		 * Answer this fiber's current priority.
		 *
		 * @return
		 *   The priority.
		 */
		var A_Fiber.priority: Int
			get() = dispatch { o_Priority(it) }
			set(value) = dispatch { o_SetPriority(it, value) }

		/**
		 * Answer the continuation that accepts the result produced by the
		 * [receiver][FiberDescriptor]'s successful completion.
		 *
		 * @return
		 *   A continuation.
		 */
		val A_Fiber.resultContinuation: (AvailObject)->Unit
			get() = dispatch { o_ResultContinuation(it) }

		/**
		 * @param flag
		 */
		fun A_Fiber.setGeneralFlag(flag: GeneralFlag) =
			dispatch { o_SetGeneralFlag(it, flag) }

		/**
		 * Set the success and failure actions of this fiber.  The former runs
		 * if the fiber succeeds, passing the resulting [AvailObject], and also
		 * stashing it in the fiber.  The latter runs if the fiber fails,
		 * passing the [Throwable] that caused the failure.
		 *
		 * @param onSuccess
		 *   The action to invoke with the fiber's result value.
		 * @param onFailure
		 *   The action to invoke with the responsible throwable.
		 */
		fun A_Fiber.setSuccessAndFailure(
			onSuccess: (AvailObject)->Unit,
			onFailure: (Throwable)->Unit
		) = dispatch { o_SetSuccessAndFailure(it, onSuccess, onFailure) }

		/**
		 * @param flag
		 */
		fun A_Fiber.setInterruptRequestFlag(flag: InterruptRequestFlag) =
			dispatch { o_SetInterruptRequestFlag(it, flag) }

		/**
		 * @return
		 */
		var A_Fiber.wakeupTask: TimerTask?
			get() = dispatch { o_WakeupTask(it) }
			set(value) = dispatch { o_SetWakeupTask(it, value) }

		/**
		 * Record access of the specified [variable][VariableDescriptor] by this
		 * [fiber][FiberDescriptor].
		 *
		 * @param variable
		 *   A variable.
		 * @param wasRead
		 *   `true` if the variable was read, `false` otherwise.
		 */
		fun A_Fiber.recordVariableAccess(
			variable: A_Variable,
			wasRead: Boolean
		) = dispatch { o_RecordVariableAccess(it, variable, wasRead) }

		/**
		 * The [set][SetDescriptor] of [variables][VariableDescriptor] that were
		 * read before written. Only variables still live are included in this
		 * set; the [trace][TraceFlag.TRACE_VARIABLE_READS_BEFORE_WRITES]
		 * mechanism retains variables only weakly.
		 */
		val A_Fiber.variablesReadBeforeWritten: A_Set
			get() = dispatch { o_VariablesReadBeforeWritten(it) }

		/**
		 * Answer the [set][SetDescriptor] of [variables][VariableDescriptor]
		 * that were written. Only variables still live are included in this
		 * set; the [trace][TraceFlag.TRACE_VARIABLE_READS_BEFORE_WRITES]
		 * mechanism retains variables only weakly.
		 *
		 * @return
		 *   The requested variables.
		 */
		val A_Fiber.variablesWritten: A_Set
			get() = dispatch { o_VariablesWritten(it) }

		/**
		 * Ensure the specified action is invoked with this fiber's reified
		 * [continuation][ContinuationDescriptor] as soon as it's available.
		 * Note that this triggers an interrupt on the fiber to ensure a timely
		 * capture of the stack.
		 *
		 * @param whenReified
		 *   What to run with the Avail [continuation][ContinuationDescriptor].
		 */
		fun A_Fiber.whenContinuationIsAvailableDo(
			whenReified: (A_Continuation)->Unit
		) = dispatch { o_WhenContinuationIsAvailableDo(it, whenReified) }

		/**
		 * Extract the current [A_Set] of [pojo][PojoDescriptor]-wrapped actions
		 * to perform when this fiber is next reified.  Replace it with the
		 * empty set.
		 *
		 * @return
		 *   The set of outstanding actions, prior to clearing it.
		 */
		fun A_Fiber.getAndClearReificationWaiters(
		): List<(A_Continuation)->Unit> =
			dispatch { o_GetAndClearReificationWaiters(it) }

		/**
		 * The [TextInterface] for routing I/O to and from this fiber.
		 */
		var A_Fiber.textInterface: TextInterface
			get() = dispatch { o_TextInterface(it) }
			set(value) = dispatch { o_SetTextInterface(it, value) }

		/**
		 * Answer the unique identifier of this `A_Fiber fiber`.
		 *
		 * @return
		 *   The unique identifier, a [Long].
		 */
		val A_Fiber.uniqueId: Long
			get() = dispatch { o_UniqueId(it) }

		/**
		 * The primitive [A_Function] that's suspending the fiber.
		 */
		var A_Fiber.suspendingFunction: A_Function
			get() = dispatch { o_SuspendingFunction(it) }
			set(value) = dispatch { o_SetSuspendingFunction(it, value) }

		/**
		 * Answer the [FiberDescriptor.FiberHelper] associated with this
		 * [A_Fiber].
		 */
		val A_Fiber.fiberHelper: FiberDescriptor.FiberHelper
			get() = dispatch { o_FiberHelper(it) }

		/**
		 * Attach the given debugger to this fiber.  Do nothing if the fiber is
		 * already attached to another debugger.
		 */
		fun A_Fiber.captureInDebugger(debugger: AvailDebuggerModel) =
			dispatch { o_CaptureInDebugger(it, debugger) }

		/**
		 * This fiber was captured by a debugger.  Release it from that
		 * debugger, allowing it to continue running freely when/if its state
		 * indicates it should.
		 */
		fun A_Fiber.releaseFromDebugger() =
			dispatch { o_ReleaseFromDebugger(it) }

		val A_Fiber.currentLexer: A_Lexer
			get() = dispatch { o_CurrentLexer(it) }


		/**
		 * Check if this fiber is allowed to be performing styling operations.
		 * For it to be allowed, the fiber must have been launched in the VM by
		 * [newStylerFiber].  Answer `true` if the fiber is permitted to style,
		 * otherwise `false`.
		 */
		val A_Fiber.canStyle: Boolean
			get() {
				availLoader ?: return false
				return fiberGlobals.mapAtOrNull(IS_STYLING.atom).notNullAnd {
					equals(trueObject)
				}
			}
	}
}
