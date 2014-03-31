/**
 * P_260_Assert.java
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

import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.List;
import com.avail.annotations.Nullable;
import com.avail.descriptor.*;
import com.avail.descriptor.FiberDescriptor.ExecutionState;
import com.avail.exceptions.AvailAssertionFailedException;
import com.avail.interpreter.*;
import com.avail.utility.evaluation.*;

/**
 * <strong>Primitive 260:</strong> Assert the specified {@linkplain
 * EnumerationTypeDescriptor#booleanObject() predicate} or raise an
 * {@link AvailAssertionFailedException} (in Java) that contains the
 * provided {@linkplain TupleTypeDescriptor#stringType() message}.
 */
public final class P_260_Assert extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance = new P_260_Assert().init(
		2, Unknown, CannotFail);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_Atom predicate = args.get(0);
		final A_String failureMessage = args.get(1);
		if (!predicate.extractBoolean())
		{
			final A_Fiber fiber = interpreter.fiber();
			final A_Continuation continuation =
				interpreter.currentContinuation();
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
						builder.append(failureMessage.asNativeString());
						for (final String frame : stack)
						{
							builder.append(String.format("%n\t-- %s", frame));
						}
						builder.append("\n\n");
						final AvailAssertionFailedException killer =
							new AvailAssertionFailedException(
								builder.toString());
						killer.fillInStackTrace();
						fiber.executionState(ExecutionState.ABORTED);
						fiber.failureContinuation().value(killer);
					}
				});
			return Result.FIBER_SUSPENDED;
		}
		return interpreter.primitiveSuccess(NilDescriptor.nil());
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				EnumerationTypeDescriptor.booleanObject(),
				TupleTypeDescriptor.stringType()),
			TOP.o());
	}
}