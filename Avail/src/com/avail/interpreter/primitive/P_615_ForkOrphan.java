/**
 * P_615_ForkOrphan.java
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
import java.util.*;
import com.avail.AvailRuntime;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 615</strong>: Fork a new {@linkplain FiberDescriptor fiber}
 * to execute the specified {@linkplain FunctionDescriptor function} with the
 * supplied arguments. Do not retain a reference to the new fiber; it is
 * created as an orphan fiber.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_615_ForkOrphan
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_615_ForkOrphan().init(3, CanInline, HasSideEffect);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 3;
		final A_Function function = args.get(0);
		final A_Tuple argTuple = args.get(1);
		final A_Number priority = args.get(2);
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
		for (final AvailObject arg : callArgs)
		{
			arg.makeShared();
		}
		final A_Fiber current = FiberDescriptor.current();
		final A_Fiber orphan = FiberDescriptor.newFiber(
			function.kind().returnType(),
			priority.extractInt());
		// If the current fiber is an Avail fiber, then the new one should be
		// also.
		orphan.availLoader(current.availLoader());
		// Share and inherit any heritable variables.
		orphan.heritableFiberGlobals(
			current.heritableFiberGlobals().makeShared());
		// Schedule the fiber to run the specified function.
		Interpreter.runOutermostFunction(
			AvailRuntime.current(),
			orphan,
			function,
			callArgs);
		return interpreter.primitiveSuccess(NilDescriptor.nil());
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				FunctionTypeDescriptor.forReturnType(TOP.o()),
				TupleTypeDescriptor.mostGeneralType(),
				IntegerRangeTypeDescriptor.bytes()),
			TOP.o());
	}
}
