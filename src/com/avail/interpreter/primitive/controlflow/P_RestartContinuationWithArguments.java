/**
 * P_RestartContinuationWithArguments.java
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
package com.avail.interpreter.primitive.controlflow;

import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.Primitive.Flag.SwitchesContinuation;
import static com.avail.interpreter.Primitive.Result.CONTINUATION_CHANGED;

import java.util.List;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive:</strong> Restart the given {@linkplain
 * ContinuationDescriptor continuation}, but passing in the given
 * {@linkplain TupleDescriptor tuple} of arguments. Make sure it's a
 * label-like continuation rather than a call-like, because a call-like
 * continuation has the expected return type already pushed on the stack,
 * and requires the return value, after checking against that type, to
 * overwrite the type in the stack (without affecting the stack depth). Fail
 * if the continuation's {@linkplain FunctionDescriptor function} is not
 * capable of accepting the given arguments.
 */
public final class P_RestartContinuationWithArguments extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public static final Primitive instance =
		new P_RestartContinuationWithArguments().init(
			2, CanInline, SwitchesContinuation);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_Continuation originalCon = args.get(0);
		final A_Tuple arguments = args.get(1);

		final A_RawFunction code = originalCon.function().code();
		//TODO MvG - This should be a primitive failure.
		assert originalCon.stackp() == code.numArgsAndLocalsAndStack() + 1
			: "Outer continuation should have been a label- rather than "
				+ "call-continuation";
		assert originalCon.pc() == 1
			: "Labels must only occur at the start of a block.  "
				+ "Only restart that kind of continuation.";
		final int numArgs = code.numArgs();
		if (numArgs != arguments.tupleSize())
		{
			return interpreter.primitiveFailure(
				E_INCORRECT_NUMBER_OF_ARGUMENTS);
		}
		// No need to make it immutable because current continuation's
		// reference is lost by this.  We make a mutable copy if necessary, to
		// allow the new arguments to be injected.
		final A_Continuation conCopy = originalCon.ensureMutable();
		if (!code.functionType().acceptsTupleOfArguments(arguments))
		{
			return interpreter.primitiveFailure(E_INCORRECT_ARGUMENT_TYPE);
		}
		for (int i = 1; i <= numArgs; i++)
		{
			conCopy.argOrLocalOrStackAtPut(i, arguments.tupleAt(i));
		}

		interpreter.reifiedContinuation = conCopy;
		interpreter.chunk = conCopy.levelTwoChunk();
		interpreter.offset = conCopy.levelTwoOffset();
		interpreter.returnNow = false;
		interpreter.latestResult(null);
		return CONTINUATION_CHANGED;
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				ContinuationTypeDescriptor.mostGeneralType(),
				TupleTypeDescriptor.mostGeneralType()),
			BottomTypeDescriptor.bottom());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			SetDescriptor.from(
				E_INCORRECT_NUMBER_OF_ARGUMENTS,
				E_INCORRECT_ARGUMENT_TYPE));
	}
}
