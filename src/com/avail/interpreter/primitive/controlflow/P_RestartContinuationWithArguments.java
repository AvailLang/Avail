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

import com.avail.descriptor.A_Continuation;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ContinuationDescriptor;
import com.avail.descriptor.FunctionDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;

import java.util.List;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.enumerationWith;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.ContinuationTypeDescriptor
	.mostGeneralContinuationType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleDescriptor.toList;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TupleTypeDescriptor.mostGeneralTupleType;
import static com.avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE;
import static com.avail.exceptions.AvailErrorCode
	.E_INCORRECT_NUMBER_OF_ARGUMENTS;
import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.Primitive.Flag.SwitchesContinuation;
import static com.avail.interpreter.Primitive.Result.CONTINUATION_CHANGED;

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
		final Interpreter interpreter)
	{
		assert args.size() == 2;
		final A_Continuation originalCon = args.get(0);
		final A_Tuple arguments = args.get(1);

		final A_RawFunction code = originalCon.function().code();
		//TODO MvG - This should be a primitive failure.
		assert originalCon.stackp() == code.numSlots() + 1
			: "Continuation should have been a label- rather than "
				+ "call-continuation";
		assert originalCon.pc() == 0
			: "Continuation should have been a label- rather than "
				+ "call-continuation";

		final int numArgs = code.numArgs();
		if (numArgs != arguments.tupleSize())
		{
			return interpreter.primitiveFailure(
				E_INCORRECT_NUMBER_OF_ARGUMENTS);
		}
		// Check the argument types.
		if (!code.functionType().acceptsTupleOfArguments(arguments))
		{
			return interpreter.primitiveFailure(E_INCORRECT_ARGUMENT_TYPE);
		}
		// Move the arguments into interpreter.argsBuffer.
		interpreter.argsBuffer.clear();
		interpreter.argsBuffer.addAll(toList(arguments));
		// The restart entry point expects the interpreter's reifiedContinuation
		// to be the label continuation's *caller*.
		interpreter.reifiedContinuation = originalCon.caller();
		interpreter.function = originalCon.function();
		interpreter.chunk = originalCon.levelTwoChunk();
		interpreter.offset = originalCon.levelTwoOffset();
		interpreter.returnNow = false;
		interpreter.latestResult(null);
		return CONTINUATION_CHANGED;
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				mostGeneralContinuationType(),
				mostGeneralTupleType()),
			bottom());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(
			set(
				E_INCORRECT_NUMBER_OF_ARGUMENTS,
				E_INCORRECT_ARGUMENT_TYPE));
	}
}
