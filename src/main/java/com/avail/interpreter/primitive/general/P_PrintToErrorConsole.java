/*
 * P_PrintToErrorConsole.java
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

package com.avail.interpreter.primitive.general;

import com.avail.AvailRuntime;
import com.avail.descriptor.A_Fiber;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.FiberDescriptor.ExecutionState;
import com.avail.descriptor.StringDescriptor;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.io.TextInterface;
import com.avail.io.TextOutputChannel;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import javax.annotation.Nullable;
import java.nio.channels.CompletionHandler;
import java.util.ArrayList;
import java.util.List;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleTypeDescriptor.stringType;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.exceptions.AvailErrorCode.E_IO_ERROR;
import static com.avail.interpreter.Primitive.Flag.CanSuspend;
import static com.avail.interpreter.Primitive.Flag.Unknown;
import static com.avail.utility.Nulls.stripNull;

/**
 * <strong>Primitive:</strong> Print the specified {@linkplain
 * StringDescriptor string} to the {@linkplain Interpreter#fiber() current
 * fiber}'s {@linkplain TextOutputChannel standard error channel}, {@linkplain
 * ExecutionState#SUSPENDED suspending} the current fiber until the string can
 * be queued for writing.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_PrintToErrorConsole
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_PrintToErrorConsole().init(
			1, CanSuspend, Unknown);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(1);
		final A_String string = interpreter.argument(0);

		final @Nullable AvailLoader loader = interpreter.availLoaderOrNull();
		if (loader != null)
		{
			loader.statementCanBeSummarized(false);
		}

		final AvailRuntime runtime = interpreter.runtime();
		final A_Fiber fiber = interpreter.fiber();
		final TextInterface textInterface = fiber.textInterface();
		final A_Function primitiveFunction = stripNull(interpreter.function);
		assert primitiveFunction.code().primitive() == this;
		final List<AvailObject> copiedArgs =
			new ArrayList<>(interpreter.argsBuffer);
		interpreter.postExitContinuation(
			() -> textInterface.errorChannel().write(
				string.asNativeString(),
				fiber,
				new CompletionHandler<Integer, A_Fiber>()
				{
					@Override
					public void completed (
						final @Nullable Integer result,
						final @Nullable A_Fiber unused)
					{
						Interpreter.resumeFromSuccessfulPrimitive(
							runtime,
							fiber,
							P_PrintToErrorConsole.this,
							nil);
					}

					@Override
					public void failed (
						final @Nullable Throwable exc,
						final @Nullable A_Fiber unused)
					{
						Interpreter.resumeFromFailedPrimitive(
							runtime,
							fiber,
							E_IO_ERROR.numericCode(),
							primitiveFunction,
							copiedArgs);
					}
				}));
		return interpreter.primitiveSuspend(primitiveFunction);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(tuple(stringType()), TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(set(E_IO_ERROR));
	}
}
