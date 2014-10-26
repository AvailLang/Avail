/**
 * P_203_ExceptionStackDump.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.ArrayList;
import java.util.List;
import com.avail.AvailRuntime;
import com.avail.annotations.Nullable;
import com.avail.descriptor.*;
import com.avail.exceptions.MapException;
import com.avail.interpreter.*;
import com.avail.utility.evaluation.*;

/**
 * <strong>Primitive 203</strong>: Get the {@linkplain
 * ObjectTypeDescriptor#stackDumpAtom() stack dump} associated with the
 * specified {@linkplain ObjectTypeDescriptor#exceptionType() exception}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_203_ExceptionStackDump
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_203_ExceptionStackDump().init(
			1, Unknown);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 1;
		final A_BasicObject exception = args.get(0);
		final AvailRuntime runtime = interpreter.runtime();
		final A_Fiber fiber = interpreter.fiber();
		final A_Continuation continuation;
		try
		{
			continuation = exception.fieldMap().mapAt(
				ObjectTypeDescriptor.stackDumpAtom());
		}
		catch (final MapException e)
		{
			assert e.numericCode().equals(E_KEY_NOT_FOUND.numericCode());
			return interpreter.primitiveFailure(E_INCORRECT_ARGUMENT_TYPE);
		}
		interpreter.primitiveSuspend();
		ContinuationDescriptor.dumpStackThen(
			runtime,
			fiber.textInterface(),
			continuation,
			new Continuation1<List<String>>()
			{
				@Override
				public void value (final @Nullable List<String> stack)
				{
					assert stack != null;
					final List<A_String> frames = new ArrayList<>(stack.size());
					for (int i = stack.size() - 1; i >= 0; i--)
					{
						frames.add(StringDescriptor.from(stack.get(i)));
					}
					final A_Tuple stackDump = TupleDescriptor.fromList(frames);
					Interpreter.resumeFromSuccessfulPrimitive(
						runtime,
						fiber,
						stackDump,
						skipReturnCheck);
				}
			});
		return Result.FIBER_SUSPENDED;
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				ObjectTypeDescriptor.exceptionType()),
			TupleTypeDescriptor.zeroOrMoreOf(
				TupleTypeDescriptor.stringType()));
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return InstanceTypeDescriptor.on(
			E_INCORRECT_ARGUMENT_TYPE.numericCode());
	}
}
