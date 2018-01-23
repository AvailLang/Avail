/**
 * P_ReadFromStandardInput.java
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
package com.avail.interpreter.primitive.general;

import com.avail.descriptor.A_Fiber;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.FiberDescriptor.ExecutionState;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import javax.annotation.Nullable;
import java.nio.CharBuffer;
import java.nio.channels.CompletionHandler;
import java.util.List;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.enumerationWith;
import static com.avail.descriptor.CharacterDescriptor.fromCodePoint;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TypeDescriptor.Types.CHARACTER;
import static com.avail.exceptions.AvailErrorCode.E_IO_ERROR;
import static com.avail.interpreter.Primitive.Flag.CanSuspend;
import static com.avail.interpreter.Primitive.Flag.Unknown;

/**
 * <strong>Primitive:</strong> Read one character from the standard input
 * stream, {@linkplain ExecutionState#SUSPENDED suspending} the {@linkplain
 * Interpreter#fiber() current fiber} until data becomes available.
 */
public final class P_ReadFromStandardInput
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_ReadFromStandardInput().init(
			0, CanSuspend, Unknown);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 0;
		final A_Fiber fiber = interpreter.fiber();
		return interpreter.suspendAndDo(
			args,
			(toSucceed, toFail) ->
			{
				final CharBuffer buffer = CharBuffer.allocate(1);
				fiber.textInterface().inputChannel().read(
					buffer,
					fiber,
					new CompletionHandler<Integer, A_Fiber>()
					{
						@Override
						public void completed (
							final @Nullable Integer result,
							final @Nullable A_Fiber unused)
						{
							toSucceed.value(fromCodePoint(buffer.get(0)));
						}

						@Override
						public void failed (
							final @Nullable Throwable exc,
							final @Nullable A_Fiber unused)
						{
							toFail.value(E_IO_ERROR);
						}
					});
			});
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			emptyTuple(),
			CHARACTER.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(set(E_IO_ERROR));
	}
}
