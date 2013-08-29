/**
 * P_256_EmergencyExit.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.List;
import com.avail.annotations.Nullable;
import com.avail.descriptor.*;
import com.avail.descriptor.FiberDescriptor.ExecutionState;
import com.avail.exceptions.AvailErrorCode;
import com.avail.interpreter.*;
import com.avail.utility.Continuation1;

/**
 * <strong>Primitive 256:</strong> Exit the current {@linkplain
 * FiberDescriptor fiber}. The specified argument will be converted
 * internally into a {@code string} and used to report an error message.
 */
public final class P_256_EmergencyExit extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance = new P_256_EmergencyExit().init(
		1, Unknown, CannotFail);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 1;
		final A_BasicObject errorMessageProducer = args.get(0);
		final A_Fiber fiber = interpreter.fiber();
		final A_Continuation continuation = interpreter.currentContinuation();
		interpreter.primitiveSuspend();
		ContinuationDescriptor.dumpStackThen(
			interpreter.runtime(),
			continuation,
			new Continuation1<List<String>>()
			{
				@Override
				public void value (final @Nullable List<String> stack)
				{
					assert stack != null;
					final StringBuilder builder = new StringBuilder();
					builder.append(String.format(
						"A fiber (%s) has exited: %s",
						fiber.fiberName(),
						errorMessageProducer));
					if (errorMessageProducer.isInt())
					{
						final int intValue =
							((A_Number)errorMessageProducer).extractInt();
						if (intValue >= 0 &&
							intValue < AvailErrorCode.values().length)
						{
							builder.append(String.format(
								" (= %s)",
								AvailErrorCode.values()[intValue].name()));
						}
					}
					for (final String frame : stack)
					{
						builder.append(String.format("%n\t-- %s", frame));
					}
					builder.append("\n\n");
					final RuntimeException killer =
						new RuntimeException(builder.toString());
					killer.fillInStackTrace();
					fiber.executionState(ExecutionState.ABORTED);
					fiber.failureContinuation().value(killer);
				}
			});
		return Result.FIBER_SUSPENDED;
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				ANY.o()),
			BottomTypeDescriptor.bottom());
	}
}