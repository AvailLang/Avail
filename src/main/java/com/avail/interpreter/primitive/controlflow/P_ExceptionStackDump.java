/*
 * P_ExceptionStackDump.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

package com.avail.interpreter.primitive.controlflow;

import com.avail.AvailRuntime;
import com.avail.descriptor.*;
import com.avail.exceptions.MapException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.util.ArrayList;
import java.util.List;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.ContinuationDescriptor.dumpStackThen;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.ObjectTupleDescriptor.tupleFromList;
import static com.avail.descriptor.ObjectTypeDescriptor.exceptionType;
import static com.avail.descriptor.ObjectTypeDescriptor.stackDumpAtom;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.descriptor.TupleTypeDescriptor.stringType;
import static com.avail.descriptor.TupleTypeDescriptor.zeroOrMoreOf;
import static com.avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE;
import static com.avail.exceptions.AvailErrorCode.E_KEY_NOT_FOUND;
import static com.avail.interpreter.Primitive.Flag.CanSuspend;
import static com.avail.interpreter.Primitive.Flag.Unknown;
import static com.avail.utility.Nulls.stripNull;

/**
 * <strong>Primitive:</strong> Get the {@linkplain
 * ObjectTypeDescriptor#stackDumpAtom() stack dump} associated with the
 * specified {@linkplain ObjectTypeDescriptor#exceptionType() exception}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_ExceptionStackDump
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_ExceptionStackDump().init(
			1, CanSuspend, Unknown);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(1);
		final A_BasicObject exception = interpreter.argument(0);
		final AvailRuntime runtime = interpreter.runtime();
		final A_Fiber fiber = interpreter.fiber();
		final A_Continuation continuation;
		try
		{
			continuation = exception.fieldMap().mapAt(stackDumpAtom());
		}
		catch (final MapException e)
		{
			assert e.numericCode().extractInt() == E_KEY_NOT_FOUND.nativeCode();
			return interpreter.primitiveFailure(E_INCORRECT_ARGUMENT_TYPE);
		}
		final A_Function primitiveFunction =
			stripNull(interpreter.function);
		interpreter.primitiveSuspend(primitiveFunction);
		dumpStackThen(
			runtime,
			fiber.textInterface(),
			continuation,
			stack ->
			{
				final List<A_String> frames = new ArrayList<>(stack.size());
				for (int i = stack.size() - 1; i >= 0; i--)
				{
					frames.add(stringFrom(stack.get(i)));
				}
				final A_Tuple stackDump = tupleFromList(frames);
				Interpreter.resumeFromSuccessfulPrimitive(
					runtime,
					fiber,
					this,
					stackDump);
			});
		return Result.FIBER_SUSPENDED;
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(tuple(exceptionType()), zeroOrMoreOf(stringType()));
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return
			enumerationWith(set(E_INCORRECT_ARGUMENT_TYPE));
	}
}
