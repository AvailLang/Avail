/**
 * P_Sleep.java
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

package com.avail.interpreter.primitive.fibers;

import com.avail.AvailRuntime;
import com.avail.descriptor.A_Fiber;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.FiberDescriptor;
import com.avail.descriptor.FiberDescriptor.ExecutionState;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;

import java.util.List;
import java.util.TimerTask;

import static com.avail.descriptor.FiberDescriptor.ExecutionState.ASLEEP;
import static com.avail.descriptor.FiberDescriptor.ExecutionState.SUSPENDED;
import static com.avail.descriptor.FiberDescriptor.InterruptRequestFlag
	.TERMINATION_REQUESTED;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InfinityDescriptor.positiveInfinity;
import static com.avail.descriptor.IntegerDescriptor.zero;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.inclusive;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.interpreter.Primitive.Flag.CannotFail;
import static com.avail.interpreter.Primitive.Flag.Unknown;
import static com.avail.utility.Nulls.stripNull;

/**
 * <strong>Primitive:</strong> Put the {@linkplain FiberDescriptor#currentFiber()
 * current} {@linkplain FiberDescriptor fiber} to {@linkplain
 * ExecutionState#ASLEEP sleep} for at least the specified number of
 * milliseconds. If the sleep time is zero ({@code 0}), then return immediately.
 * If the sleep time is too big (i.e., greater than the maximum delay supported
 * by the operating system), then sleep forever.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_Sleep
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_Sleep().init(
			1, CannotFail, Unknown);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 1;
		final AvailObject sleepMillis = args.get(0);
		// If the requested sleep time is 0 milliseconds, then return
		// immediately. We could have chosen to yield here, but it was better to
		// make sleep and yield behave differently.
		if (sleepMillis.equalsInt(0))
		{
			return interpreter.primitiveSuccess(nil);
		}
		final A_Fiber fiber = interpreter.fiber();
		// If the requested sleep time isn't colossally big, then arrange for
		// the fiber to resume later. If the delay is too big, then the fiber
		// will only awaken due to interruption.
		final AvailRuntime runtime = interpreter.runtime();
		final A_RawFunction primitiveRawFunction =
			stripNull(interpreter.function).code();
		if (sleepMillis.isLong())
		{
			// Otherwise, delay the resumption of this task.
			final TimerTask task = new TimerTask()
			{
				@Override
				public void run ()
				{
					fiber.lock(() ->
					{
						// Only resume the fiber if it's still asleep. A
						// termination request may have already woken the
						// fiber up, but so recently that it didn't manage
						// to cancel this timer task.
						if (fiber.executionState() == ASLEEP)
						{
							fiber.wakeupTask(null);
							fiber.executionState(SUSPENDED);
							Interpreter.resumeFromSuccessfulPrimitive(
								runtime,
								fiber,
								nil,
								primitiveRawFunction,
								true);
						}
					});
				}
			};
			// Once the fiber has been unbound, transition it to sleeping and
			// start the timer task.
			interpreter.postExitContinuation(() -> fiber.lock(() ->
			{
				// If termination has been requested, then schedule
				// the resumption of this fiber.
				if (fiber.interruptRequestFlag(
					TERMINATION_REQUESTED))
				{
					assert fiber.executionState() == SUSPENDED;
					Interpreter.resumeFromSuccessfulPrimitive(
						runtime,
						fiber,
						nil,
						primitiveRawFunction,
						true);
					return;
				}
				fiber.wakeupTask(task);
				fiber.executionState(ASLEEP);
				runtime.timer.schedule(
					task,
					sleepMillis.extractLong());
			}));
		}
		// The delay was too big, so put the fiber to sleep forever.
		else
		{
			// Once the fiber has been unbound, transition it to sleeping.
			interpreter.postExitContinuation(() -> fiber.lock(() ->
			{
				// If termination has been requested, then schedule
				// the resumption of this fiber.
				if (fiber.interruptRequestFlag(
					TERMINATION_REQUESTED))
				{
					assert fiber.executionState() == SUSPENDED;
					Interpreter.resumeFromSuccessfulPrimitive(
						runtime,
						fiber,
						nil,
						primitiveRawFunction,
						true);
					return;
				}
				fiber.executionState(ASLEEP);
			}));
		}
		// Don't actually transition the fiber to the sleeping state, which
		// can only occur at task-scheduling time. This happens after the
		// fiber is unbound from the interpreter. Instead, suspend the fiber.
		return interpreter.primitiveSuspend(primitiveRawFunction);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(tuple(inclusive(
			zero(),
			positiveInfinity())), TOP.o());
	}
}
