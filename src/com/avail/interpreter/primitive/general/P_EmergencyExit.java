/*
 * P_EmergencyExit.java
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

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Continuation;
import com.avail.descriptor.A_Fiber;
import com.avail.descriptor.A_Number;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.FiberDescriptor;
import com.avail.descriptor.FiberDescriptor.ExecutionState;
import com.avail.exceptions.AvailEmergencyExitException;
import com.avail.exceptions.AvailErrorCode;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.optimizer.L1Translator;
import com.avail.optimizer.L1Translator.CallSiteHelper;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.util.List;

import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.ContinuationDescriptor.dumpStackThen;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.interpreter.Primitive.Flag.*;
import static com.avail.utility.Nulls.stripNull;
import static java.lang.String.format;

/**
 * <strong>Primitive:</strong> Exit the current {@linkplain
 * FiberDescriptor fiber}. The specified argument will be converted
 * internally into a {@code string} and used to report an error message.
 *
 * <p>It's marked with {@link Flag#CanSwitchContinuations} to force the stack to
 * be reified, for debugging convenience.</p>
 */
public final class P_EmergencyExit
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_EmergencyExit().init(
			1,
			Unknown,
			CanSwitchContinuations,
			AlwaysSwitchesContinuation,
			CanSuspend,
			CannotFail);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 1;
		final A_BasicObject errorMessageProducer = args.get(0);
		final A_Fiber fiber = interpreter.fiber();
		final A_Continuation continuation =
			stripNull(interpreter.reifiedContinuation);
		interpreter.primitiveSuspend(stripNull(interpreter.function));
		dumpStackThen(
			interpreter.runtime(),
			fiber.textInterface(),
			continuation,
			stack ->
			{
				final StringBuilder builder = new StringBuilder();
				builder.append(format(
					"A fiber (%s) has exited: %s",
					fiber.fiberName(),
					errorMessageProducer));
				if (errorMessageProducer.isInt())
				{
					final int intValue =
						((A_Number)errorMessageProducer).extractInt();
					if (intValue >= 0 &&
						intValue < AvailErrorCode.all().length)
					{
						builder.append(format(
							" (= %s)",
							AvailErrorCode.byNumericCode(intValue).name()));
					}
				}
				for (final String frame : stack)
				{
					builder.append(format("%n\t-- %s", frame));
				}
				builder.append("\n\n");
				final AvailEmergencyExitException killer =
					new AvailEmergencyExitException(builder.toString());
				killer.fillInStackTrace();
				fiber.executionState(ExecutionState.ABORTED);
				fiber.failureContinuation().value(killer);
				// If we're still here, the handler didn't do anything with the
				// exception.  Output it and throw it as a runtime exception.
				System.err.print(builder);
				throw new RuntimeException(killer);
			});
		return Result.FIBER_SUSPENDED;
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(ANY.o()),
			bottom());
	}

	@Override
	public boolean tryToGenerateSpecialPrimitiveInvocation (
		final L2ReadPointerOperand functionToCallReg,
		final A_RawFunction rawFunction,
		final List<L2ReadPointerOperand> arguments,
		final List<A_Type> argumentTypes,
		final L1Translator translator,
		final CallSiteHelper callSiteHelper)
	{
		// Never inline.  Ensure the caller reifies the stack before calling it.
		return false;
	}
}
