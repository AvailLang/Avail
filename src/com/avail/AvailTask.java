/**
 * AvailTask.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

package com.avail;

import static com.avail.descriptor.FiberDescriptor.ExecutionState.*;
import static com.avail.descriptor.FiberDescriptor.SynchronizationFlag.*;

import com.avail.exceptions.PrimitiveThrownException;
import javax.annotation.Nullable;
import com.avail.descriptor.*;
import com.avail.descriptor.FiberDescriptor.*;
import com.avail.interpreter.Interpreter;
import com.avail.utility.*;
import com.avail.utility.evaluation.*;

/**
 * An {@code AvailTask} extends {@link Runnable} with a priority. Instances are
 * intended to be executed only by {@linkplain AvailThread Avail threads}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public abstract class AvailTask
implements Comparable<AvailTask>, Runnable
{
	/**
	 * Answer a {@linkplain AvailTask task} suitable for resuming the specified
	 * {@linkplain FiberDescriptor fiber} using the specified {@linkplain
	 * Continuation0 Java continuation}. If the continuation fails for any
	 * reason, then it {@linkplain Interpreter#abortFiber() aborts} the fiber
	 * and invokes the {@linkplain AvailObject#failureContinuation() failure
	 * continuation} with the terminal {@linkplain Throwable throwable}.
	 *
	 * @param fiber
	 *        A fiber.
	 * @param transformer
	 *        What to do to resume execution of the fiber.  The fiber's {@link
	 *        ExecutionState} is returned by it.
	 * @return A task that sets the execution state of the fiber to {@linkplain
	 *         ExecutionState#RUNNING running}, binds it to this {@linkplain
	 *         AvailThread thread}'s {@linkplain Interpreter interpreter}, and
	 *         then runs the specified continuation.
	 */
	public static AvailTask forFiberResumption (
		final A_Fiber fiber,
		final Transformer0<ExecutionState> transformer)
	{
		assert fiber.executionState().indicatesSuspension();
		final boolean scheduled =
			fiber.getAndSetSynchronizationFlag(SCHEDULED, true);
		assert !scheduled;
		return new AvailTask(fiber.priority())
		{
			@Override
			public void value ()
			{
				final Interpreter interpreter = Interpreter.current();
				fiber.lock(() ->
				{
					assert fiber.executionState().indicatesSuspension();
					final boolean bound =
						fiber.getAndSetSynchronizationFlag(BOUND, true);
					assert !bound;
					final boolean wasScheduled =
						fiber.getAndSetSynchronizationFlag(
							SCHEDULED, false);
					assert wasScheduled;
					fiber.executionState(RUNNING);
					interpreter.fiber(fiber);
				});
				final MutableOrNull<Continuation0> postExitContinuation =
					new MutableOrNull<>();
				try
				{
					transformer.value();
				}
				catch (final PrimitiveThrownException e)
				{
					// If execution failed, terminate the fiber and invoke its
					// failure continuation with the throwable.
					if (!fiber.executionState().indicatesTermination())
					{
						assert interpreter.fiberOrNull() == fiber;
						interpreter.abortFiber();
					}
					else
					{
						fiber.executionState(ABORTED);
					}
					fiber.failureContinuation().value(e);
					fiber.executionState(RETIRED);
					interpreter.runtime().unregisterFiber(fiber);
				}
				finally
				{
					postExitContinuation.value =
						interpreter.postExitContinuation();
					interpreter.postExitContinuation(null);
				}
				final @Nullable Continuation0 con = postExitContinuation.value;
				// This is the first point at which *some other* Thread may have
				// had a chance to resume the fiber and update its state.
				if (con != null)
				{
					con.value();
				}
				// If the fiber has terminated, then report its
				// result via its result continuation.
				fiber.lock(() ->
				{
					if (fiber.executionState() == TERMINATED)
					{
						fiber.resultContinuation().value(
							fiber.fiberResult());
						fiber.executionState(RETIRED);
						interpreter.runtime().unregisterFiber(fiber);
					}
				});
			}
		};
	}

	/**
	 * Answer a {@linkplain AvailTask task} suitable for performing {@linkplain
	 * Continuation0 activities} on behalf of an unbound {@linkplain
	 * ExecutionState#SUSPENDED} {@linkplain FiberDescriptor fiber}. If the
	 * continuation fails for any reason, then it transitions the fiber to the
	 * {@linkplain ExecutionState#ABORTED aborted} state and invokes the fiber's
	 * {@linkplain AvailObject#failureContinuation() failure continuation} with
	 * the terminal {@linkplain Throwable throwable}.
	 *
	 * @param fiber
	 *        A fiber.
	 * @param continuation
	 *        What to do to on behalf of the unbound fiber.
	 * @return A task that runs the specified continuation, and handles any
	 *         errors appropriately.
	 */
	public static AvailTask forUnboundFiber (
		final A_Fiber fiber,
		final Continuation0 continuation)
	{
		assert fiber.executionState() == SUSPENDED;
		final boolean scheduled =
			fiber.getAndSetSynchronizationFlag(SCHEDULED, true);
		assert !scheduled;
		return new AvailTask(fiber.priority())
		{
			@Override
			public void value ()
			{
				final boolean wasScheduled = fiber.getAndSetSynchronizationFlag(
					SCHEDULED, false);
				assert wasScheduled;
				try
				{
					continuation.value();
				}
				catch (final Throwable e)
				{
					// If execution failed for any reason, then terminate the
					// fiber and invoke its failure continuation with the
					// throwable.
					fiber.executionState(ABORTED);
					fiber.failureContinuation().value(e);
				}
			}
		};
	}

	/** The priority of the {@linkplain AvailTask task}. */
	public final int priority;

	/**
	 * Construct a new {@link AvailTask}.
	 *
	 * @param priority The desired priority, a nonnegative integer.
	 */
	public AvailTask (
		final int priority)
	{
		assert priority >= 0;
		this.priority = priority;
	}

	@Override
	public int compareTo (final @Nullable AvailTask other)
	{
		assert other != null;
		return Integer.compare(priority, other.priority);
	}

	@Override
	public final void run ()
	{
		try
		{
			value();
		}
		catch (final Throwable e)
		{
			// Report the exception immediately, then suppress the error.  If we
			// allowed the error to propagate, it would cause an Interpreter to
			// be silently lost, and the next creation could overflow the
			// maximum number of Interpreters.
			System.err.println("Unexpected internal failure in AvailTask:");
			e.printStackTrace();
		}
	}

	/** Subclasses must override this to provide specific behavior. */
	public abstract void value ();
}
