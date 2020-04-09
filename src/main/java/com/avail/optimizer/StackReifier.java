/*
 * StackReifier.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.optimizer;

import com.avail.AvailThread;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.functions.A_Continuation;
import com.avail.interpreter.Interpreter;
import com.avail.optimizer.jvm.CheckedMethod;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import com.avail.performance.Statistic;
import com.avail.utility.evaluation.Continuation0;
import com.avail.utility.evaluation.Continuation1NotNull;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;

import static com.avail.AvailRuntimeSupport.captureNanos;
import static com.avail.interpreter.Interpreter.debugL2;
import static com.avail.interpreter.Interpreter.traceL2;
import static com.avail.optimizer.jvm.CheckedMethod.instanceMethod;
import static com.avail.utility.Nulls.stripNull;

/**
 * The level two execution machinery allows limited use of the Java stack during
 * ordinary execution, but when exceptional conditions arise, the Java stack is
 * unwound with a {@code StackReifier} and converted into level one
 * continuations.  This happens when the stack gets too deep, when tricky code
 * like exceptions and backtracking happen, or when running a suspending
 * primitive, including to add or remove methods.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class StackReifier
{
	/**
	 * Whether to actually reify continuations during unwinding.  If false, the
	 * frames are simply dropped, on the assumption that the {@link
	 * #postReificationAction} will replace the entire stack anyhow.
	 */
	private final boolean actuallyReify;

	/**
	 * A {@link Continuation0} that should be executed once the {@link
	 * Interpreter}'s stack has been fully reified.  For example, this might set
	 * up a function/chunk/offset in the interpreter.  The interpreter will then
	 * determine if it should continue running.
	 */
	public final Continuation0 postReificationAction;

	/**
	 * The stack of lambdas that's accumulated as the call stack is popped.
	 * After the call stack is empty, the outer {@link Interpreter} loop will
	 * execute them in reverse order.  The typical action is to invoke some
	 * L2Chunk at an entry point, and the L2 code will cause one or more stack
	 * frames to be generated and pushed onto the {@link
	 * Interpreter#setReifiedContinuation(A_Continuation)}.
	 */
	private final Deque<Continuation1NotNull<Interpreter>> actionStack =
		new ArrayDeque<>();

	/** The {@link System#nanoTime()} when this stack reifier was created. */
	public final long startNanos;

	/** The {@link Statistic} under which to record this reification. */
	public final Statistic reificationStatistic;

	/**
	 * Construct a new {@code StackReifier}.
	 *
	 * @param actuallyReify
	 *        Whether to reify the Java frames (rather than simply drop them).
	 * @param reificationStatistic
	 *        The {@link Statistic} under which to record this reification once
	 *        it completes.  The timing of this event spans from this creation
	 *        until just before the {@link #postReificationAction} action runs.
	 * @param postReificationAction
	 *        The action to perform after the Java stack has been fully reified.
	 */
	public StackReifier (
		final boolean actuallyReify,
		final Statistic reificationStatistic,
		final Continuation0 postReificationAction)
	{
		this.actuallyReify = actuallyReify;
		this.postReificationAction = postReificationAction;
		this.startNanos = captureNanos();
		this.reificationStatistic = reificationStatistic;
	}

	/**
	 * Answer whether this {@code StackReifier} should cause reification (rather
	 * than just clearing the Java stack).
	 *
	 * @return An indicator whether to reify versus discard the Java stack.
	 */
	public boolean actuallyReify ()
	{
		return actuallyReify;
	}

	/**
	 * Run the actions in <em>reverse</em> order to populate the
	 * {@link Interpreter#getReifiedContinuation()} stack.
	 *
	 * @param interpreter
	 *        The {@link Interpreter} with which to run the actions, in reverse
	 *        order.
	 */
	public void runActions (
		final Interpreter interpreter)
	{
		while (!actionStack.isEmpty())
		{
			actionStack.removeLast().value(interpreter);
		}
	}

	/**
	 * Push an action on the {@link #actionStack}.  These will be executed in
	 * reverse order, after the Java call stack has been emptied.
	 *
	 * @param action The {@link Continuation1NotNull} to push.
	 */
	public void pushAction (
		final Continuation1NotNull<Interpreter> action)
	{
		actionStack.addLast(action);
	}

	/**
	 * Push an action on the reifier's stack of actions.  The action should run
	 * after previously run (but subsequently pushed) actions have had a chance
	 * to set up a caller's reified state.  Take the supplied dummy continuation
	 * and push it on the reified stack, then run it.  The run must complete
	 * normally – i.e., it must not trigger more reifications, or try to fall
	 * back to the default chunk.
	 *
	 * <p>The code in the dummy continuation will restore register state, pop
	 * the dummy continuation, then assemble and push whatever new
	 * continuation(s) are needed to make the stack reflect some new state,
	 * prior to running any previously pushed actions.</p>
	 *
	 * @param dummyContinuation
	 *        A mutable continuation to add to the stack when more recently
	 *        pushed actions have completed (thereby fully reifying the caller).
	 * @return The receiver (a {@code StackReifier}), as a convenience.
	 */
	@ReferencedInGeneratedCode
	public StackReifier pushContinuationAction (
		final AvailObject dummyContinuation)
	{
		assert dummyContinuation.caller().equalsNil();
		actionStack.addLast(
			interpreter ->
			{
				if (debugL2)
				{
					traceL2(
						dummyContinuation.levelTwoChunk(),
						dummyContinuation.levelTwoOffset(),
						"Starting a reifier action",
						"");
				}
				// The call stack reflects what the dummyContinuation expects to
				// see reified so far.  Push the dummyContinuation.
				final A_Continuation newDummy =
					dummyContinuation.replacingCaller(
						stripNull(interpreter.getReifiedContinuation()));
				interpreter.setReifiedContinuation(newDummy);
				// Now run it, which will pop itself and push anything that it
				// is supposed to.
				interpreter.function = newDummy.function();
				interpreter.chunk = newDummy.levelTwoChunk();
				interpreter.offset = newDummy.levelTwoOffset();

				final @Nullable StackReifier result =
					interpreter.chunk.runChunk(interpreter, interpreter.offset);

				assert result == null : "Must not reify in dummy continuation!";
				// The dummy's code will have cleaned up the stack.  Let the
				// next action run, or if exhausted, run the reifier's
				// postReificationAction, or resume the top continuation.
				if (debugL2)
				{
					traceL2(
						dummyContinuation.levelTwoChunk(),
						dummyContinuation.levelTwoOffset(),
						"Finished a reifier action (offset is for instruction "
							+ "that queued it)",
						"");
				}
			});
		return this;
	}

	/** Access the {@link #pushContinuationAction(AvailObject)} method. */
	public static final CheckedMethod pushContinuationActionMethod =
		instanceMethod(
			StackReifier.class,
			"pushContinuationAction",
			StackReifier.class,
			AvailObject.class);

	/**
	 * Record the fact that a reification has completed.  The specific {@link
	 * Statistic} under which to record it was provided to the constructor.
	 *
	 * @param interpreterIndex
	 *        The current {@link AvailThread}'s {@link Interpreter}'s index,
	 *        used for contention-free statistics gathering.
	 */
	public void recordCompletedReification (final int interpreterIndex)
	{
		final long endNanos = captureNanos();
		reificationStatistic.record(endNanos - startNanos, interpreterIndex);
	}
}
