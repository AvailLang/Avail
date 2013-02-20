/**
 * P_623_DelayedFork.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

package com.avail.interpreter.primitive;

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import static com.avail.descriptor.FiberDescriptor.InterruptRequestFlag.TERMINATION_REQUESTED;
import java.util.*;
import com.avail.AvailRuntime;
import com.avail.annotations.*;
import com.avail.descriptor.*;
import com.avail.descriptor.FiberDescriptor.InterruptRequestFlag;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 623</strong>: Schedule a new {@linkplain FiberDescriptor
 * fiber} to execute the specified {@linkplain FunctionDescriptor function} with
 * the supplied arguments. The fiber will begin running after at least the
 * specified number of milliseconds have elapsed. If the fiber discovers, just
 * before it would start running for the first time, that a {@linkplain
 * InterruptRequestFlag#TERMINATION_REQUESTED termination requested interrupt}
 * has been signaled, then don't start the fiber after all. Answer the new
 * fiber.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_623_DelayedFork
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final @NotNull static Primitive instance =
		new P_623_DelayedFork().init(4, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 4;
		final AvailObject sleepMillis = args.get(0);
		final AvailObject function = args.get(1);
		final AvailObject argTuple = args.get(2);
		final AvailObject priority = args.get(3);
		// Ensure that the function is callable with the specified arguments.
		final int numArgs = argTuple.tupleSize();
		if (function.code().numArgs() != numArgs)
		{
			return interpreter.primitiveFailure(
				E_INCORRECT_NUMBER_OF_ARGUMENTS);
		}
		final List<AvailObject> callArgs =
			new ArrayList<AvailObject>(numArgs);
		final A_Type tupleType = function.kind().argsTupleType();
		for (int i = 1; i <= numArgs; i++)
		{
			final AvailObject anArg = argTuple.tupleAt(i);
			if (!anArg.isInstanceOf(tupleType.typeAtIndex(i)))
			{
				return interpreter.primitiveFailure(
					E_INCORRECT_ARGUMENT_TYPE);
			}
			callArgs.add(anArg);
		}
		// Now that we know that the call will really happen, share the function
		// and the arguments.
		function.makeShared();
		for (final AvailObject arg : callArgs)
		{
			arg.makeShared();
		}
		final A_BasicObject current = FiberDescriptor.current();
		final A_BasicObject newFiber =
			FiberDescriptor.newFiber(priority.extractInt());
		// If the current fiber is an Avail fiber, then the new one should be
		// also.
		newFiber.availLoader(current.availLoader());
		// Don't inherit the success continuation, but inherit the failure
		// continuation. Only loader fibers should have something real plugged
		// into this field, and none of them should fail because of a Java
		// exception.
		newFiber.failureContinuation(current.failureContinuation());
		// Share the fiber, since it will be visible in the caller.
		newFiber.makeShared();
		// If the requested sleep time is 0 milliseconds, then fork immediately.
		if (sleepMillis.equals(IntegerDescriptor.zero()))
		{
			Interpreter.runOutermostFunction(
				newFiber,
				function,
				callArgs);
		}
		// Otherwise, if the delay time isn't colossal, then schedule the fiber
		// to start later.
		else if (sleepMillis.lessOrEqual(IntegerDescriptor.fromLong(
			Long.MAX_VALUE)))
		{
			AvailRuntime.current().timer.schedule(
				new TimerTask()
				{
					@Override
					public void run ()
					{
						// If termination has been requested, then don't start
						// this fiber after all.
						if (!newFiber.getAndClearInterruptRequestFlag(
							TERMINATION_REQUESTED))
						{
							Interpreter.runOutermostFunction(
								newFiber,
								function,
								callArgs);
						}
					}
				},
				sleepMillis.extractLong());
		}
		return interpreter.primitiveSuccess(newFiber);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				IntegerRangeTypeDescriptor.create(
					IntegerDescriptor.zero(),
					true,
					InfinityDescriptor.positiveInfinity(),
					true),
				FunctionTypeDescriptor.forReturnType(TOP.o()),
				TupleTypeDescriptor.mostGeneralType(),
				IntegerRangeTypeDescriptor.bytes()),
			FIBER.o());
	}
}
