/**
 * P_623_DelayedFork.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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
import java.util.*;
import com.avail.AvailRuntime;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 623</strong>: Schedule a new {@linkplain FiberDescriptor
 * fiber} to execute the specified {@linkplain FunctionDescriptor function} with
 * the supplied arguments. The fiber will begin running after at least the
 * specified number of milliseconds have elapsed. Answer the new fiber.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_623_DelayedFork
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_623_DelayedFork().init(
			4, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 4;
		final A_Number sleepMillis = args.get(0);
		final A_Function function = args.get(1);
		final A_Tuple argTuple = args.get(2);
		final A_Number priority = args.get(3);
		// Ensure that the function is callable with the specified arguments.
		final int numArgs = argTuple.tupleSize();
		if (function.code().numArgs() != numArgs)
		{
			return interpreter.primitiveFailure(
				E_INCORRECT_NUMBER_OF_ARGUMENTS);
		}
		final List<AvailObject> callArgs =
			new ArrayList<>(numArgs);
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
		for (final A_BasicObject arg : callArgs)
		{
			arg.makeShared();
		}
		final A_Fiber current = interpreter.fiber();
		final A_Fiber newFiber = FiberDescriptor.newFiber(
			function.kind().returnType(),
			priority.extractInt(),
			StringDescriptor.format(
				"Delayed fork (prim 623), %s, %s:%d",
				function.code().methodName(),
				function.code().module().moduleName(),
				function.code().startingLineNumber()));
		// If the current fiber is an Avail fiber, then the new one should be
		// also.
		newFiber.availLoader(current.availLoader());
		// Share and inherit any heritable variables.
		newFiber.heritableFiberGlobals(
			current.heritableFiberGlobals().makeShared());
		// Inherit the fiber's text interface.
		newFiber.textInterface(current.textInterface());
		// Share the fiber, since it will be visible in the caller.
		newFiber.makeShared();
		// If the requested sleep time is 0 milliseconds, then fork immediately.
		if (sleepMillis.equals(IntegerDescriptor.zero()))
		{
			Interpreter.runOutermostFunction(
				AvailRuntime.current(),
				newFiber,
				function,
				callArgs);
		}
		// Otherwise, if the delay time isn't colossal, then schedule the fiber
		// to start later.
		else if (sleepMillis.isLong())
		{
			final AvailRuntime runtime = AvailRuntime.current();
			AvailRuntime.current().timer.schedule(
				new TimerTask()
				{
					@Override
					public void run ()
					{
						Interpreter.runOutermostFunction(
							runtime,
							newFiber,
							function,
							callArgs);
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
				IntegerRangeTypeDescriptor.inclusive(
					IntegerDescriptor.zero(),
					InfinityDescriptor.positiveInfinity()),
				FunctionTypeDescriptor.forReturnType(TOP.o()),
				TupleTypeDescriptor.mostGeneralType(),
				IntegerRangeTypeDescriptor.bytes()),
			FiberTypeDescriptor.mostGeneralType());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			SetDescriptor.fromCollection(Arrays.asList(
				E_INCORRECT_NUMBER_OF_ARGUMENTS.numericCode(),
				E_INCORRECT_ARGUMENT_TYPE.numericCode())));
	}
}
