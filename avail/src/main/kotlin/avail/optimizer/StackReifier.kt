/*
 * StackReifier.kt
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
package avail.optimizer

import avail.AvailRuntimeSupport
import avail.AvailThread
import avail.descriptor.representation.A_Continuation
import avail.descriptor.representation.A_Continuation.Companion.caller
import avail.descriptor.representation.A_Continuation.Companion.function
import avail.descriptor.representation.A_Continuation.Companion.levelTwoChunk
import avail.descriptor.representation.A_Continuation.Companion.levelTwoOffset
import avail.descriptor.representation.A_Continuation.Companion.replacingCaller
import avail.descriptor.representation.AvailObject
import avail.interpreter.JavaLibrary.void
import avail.interpreter.execution.Interpreter
import avail.interpreter.execution.Interpreter.Companion.currentInterpreter
import avail.interpreter.execution.Interpreter.Companion.debugL2
import avail.interpreter.execution.Interpreter.Companion.log
import avail.interpreter.execution.Interpreter.Companion.traceL2
import avail.optimizer.jvm.CheckedMethod.Companion.instanceMethod
import avail.optimizer.jvm.JVMTranslator.Companion.emptyArrayOfObject
import avail.optimizer.jvm.ReferencedInGeneratedCode
import avail.performance.Statistic
import java.util.ArrayDeque
import java.util.Deque
import java.util.logging.Level

/**
 * The level two execution machinery allows limited use of the Java stack during
 * ordinary execution, but when exceptional conditions arise, the Java stack is
 * unwound with a `StackReifier` and converted into level one
 * continuations.  This happens when the stack gets too deep, when tricky code
 * like exceptions and backtracking happen, or when running a suspending
 * primitive, including to add or remove methods.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property actuallyReify
 *   Whether to actually reify continuations during unwinding.  If false, the
 *   frames are simply dropped, on the assumption that the
 *   [postReificationAction] will replace the entire stack anyhow.
 * @property reificationStatistic
 *   The [Statistic] under which to record this reification.
 * @property postReificationAction
 *   A lambda that should be executed once the [Interpreter]'s stack has been
 *   fully reified.  For example, this might set up a function/chunk/offset in
 *   the interpreter.  The interpreter will then determine if it should continue
 *   running.
 *
 * @constructor
 * Construct a new `StackReifier`.
 *
 * @param actuallyReify
 *   Whether to reify the Java frames (rather than simply drop them).
 * @param reificationStatistic
 *   The [Statistic] under which to record this reification once it completes.
 *   The timing of this event spans from this creation until just before the
 *   [postReificationAction] action runs.
 * @param postReificationAction
 *   The action to perform after the Java stack has been fully reified.
 */
class StackReifier constructor(
	val actuallyReify: Boolean,
	private val reificationStatistic: Statistic,
	val postReificationAction: ()->AfterReification)
{
	/**
	 * An enumeration of the possible actions that can be taken after a
	 * reification completes.  This is handled in the [Interpreter.run] loop,
	 * which is where the execution ends up when there are no Avail function
	 * invocations on the JVM call stack.
	 */
	enum class AfterReification
	{
		/**
		 * This reifier's [postReificationAction] has completed, and the fate of
		 * the current fiber has already been accounted for, whether parked,
		 * terminated, or re-queued as ready-to-run as a result of a timeslice
		 * context switch.
		 */
		SWITCH_FROM_FIBER,

		/**
		 * The reifier's [postReificationAction] has completed, and the current
		 * fiber's fully reified [A_Continuation] should be continued by the
		 * [Interpreter.run] loop.
		 */
		CONTINUE_FIBER
	}

	/**
	 * The stack of lambdas that's accumulated as the call stack is popped.
	 * After the call stack is empty, the outer [Interpreter] loop will execute
	 * them in reverse order.  The typical action is to invoke some L2Chunk at
	 * an entry point, and the L2 code will cause one or more stack frames to be
	 * generated and pushed onto the [Interpreter.setReifiedContinuation].
	 */
	private val actionStack:
			Deque<Interpreter.() -> Unit> =
		ArrayDeque()

	/** The [System.nanoTime] when this stack reifier was created. */
	val startNanos: Long = AvailRuntimeSupport.captureNanos()

	/**
	 * Run the actions in *reverse* order to populate the
	 * [Interpreter.getReifiedContinuation] stack.
	 *
	 * @param interpreter
	 *   The [Interpreter] with which to run the actions, in reverse order.
	 */
	fun runActions(interpreter: Interpreter)
	{
		if (debugL2)
		{
			log(
				currentInterpreter.fiberOrNull(),
				Interpreter.loggerDebugL2,
				// Force logging when the switches are enabled.
				Level.SEVERE,
				"\nvvvvvv Starting runActions to build continuations")
		}
		while (!actionStack.isEmpty())
		{
			interpreter.run {
				actionStack.removeLast()()
			}
		}
		if (debugL2)
		{
			log(
				currentInterpreter.fiberOrNull(),
				Interpreter.loggerDebugL2,
				// Force logging when the switches are enabled.
				Level.SEVERE,
				"^^^^^^ Completed runActions to build continuations\n")
		}
	}

	/**
	 * Push an action on the [actionStack].  These will be executed in reverse
	 * order, after the Java call stack has been emptied.
	 *
	 * @param action
	 *   The lambda to push.
	 */
	fun pushAction(action: Interpreter.() -> Unit)
	{
		actionStack.addLast(action)
	}

	/**
	 * Push an action on the reifier's stack of actions.  The action should run
	 * after previously run (but subsequently pushed) actions have had a chance
	 * to set up a caller's reified state.  Take the supplied dummy continuation
	 * and push it on the reified stack, then run it.  The run must complete
	 * normally – i.e., it must not trigger more reifications, or try to fall
	 * back to the default chunk.
	 *
	 * The code in the dummy continuation will restore register state, pop the
	 * dummy continuation, and then assemble and push whatever new
	 * continuation(s) are needed to make the stack reflect some new state,
	 * prior to running any previously pushed actions.
	 *
	 * @param dummyContinuation
	 *   A mutable continuation to add to the stack when more recently pushed
	 *   actions have completed (thereby fully reifying the caller).
	 */
	@ReferencedInGeneratedCode
	fun pushContinuationAction(dummyContinuation: AvailObject): Unit
	{
		assert(dummyContinuation.caller.isNil)
		actionStack.addLast {
			if (Interpreter.debugL2)
			{
				traceL2(
					dummyContinuation.levelTwoChunk.executableChunk,
					this,
					dummyContinuation.levelTwoOffset,
					"Starting a reifier action",
					emptyArrayOfObject)
			}
			// The call stack reflects what the dummyContinuation expects to
			// see reified so far.  Push the dummyContinuation.
			val newDummy =
				dummyContinuation.replacingCaller(getReifiedContinuation()!!)
			setReifiedContinuation(newDummy)
			// Now run it, which will pop itself and push anything that it
			// is supposed to.

			function = newDummy.function
			chunk = newDummy.levelTwoChunk
			setOffset(newDummy.levelTwoOffset)
			chunk!!.beforeRunChunk(offset)
			val result = chunk!!.executableChunk.runChunk(this, offset)
			assert(result === null) { "Must not reify in dummy continuation!" }
			// The dummy's code will have cleaned up the stack.  Let the
			// next action run, or if exhausted, run the reifier's
			// postReificationAction, or resume the top continuation.
			if (Interpreter.debugL2)
			{
				traceL2(
					dummyContinuation.levelTwoChunk.executableChunk,
					this,
					dummyContinuation.levelTwoOffset,
					"Finished a reifier action (offset is for "
						+ "instruction that queued it)",
					emptyArrayOfObject)
			}
		}
	}

	/**
	 * Record the fact that a reification has completed.  The specific
	 * [Statistic] under which to record it was provided to the constructor.
	 *
	 * @param interpreterIndex
	 *   The current [AvailThread]'s [Interpreter]'s index, used for
	 *   contention-free statistics gathering.
	 */
	fun recordCompletedReification(interpreterIndex: Int)
	{
		val endNanos = AvailRuntimeSupport.captureNanos()
		reificationStatistic.record(endNanos - startNanos, interpreterIndex)
	}

	companion object
	{
		/** Access the [pushContinuationAction] method. */
		val pushContinuationActionMethod = instanceMethod(
			StackReifier::class.java,
			StackReifier::pushContinuationAction.name,
			void,
			AvailObject::class.java)
	}
}
