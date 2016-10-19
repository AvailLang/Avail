/**
 * P_ExitContinuationWithResult.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

import static com.avail.descriptor.TypeDescriptor.Types.ANY;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.SwitchesContinuation;
import static com.avail.interpreter.Primitive.Result.*;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive:</strong> Exit the given {@linkplain
 * ContinuationDescriptor continuation} (returning result to its caller).
 */
public final class P_ExitContinuationWithResult extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_ExitContinuationWithResult().init(
			2, SwitchesContinuation);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_Continuation con = args.get(0);
		final AvailObject result = args.get(1);
		final A_Function function = con.function();
		assert con.stackp() == function.code().numArgsAndLocalsAndStack() + 1
			: "Outer continuation should have been a label- rather than "
				+ "call- continuation";
		assert con.pc() == 1
			: "Labels must only occur at the start of a block.  "
				+ "Only exit that kind of continuation.";
		// No need to make it immutable because current continuation's
		// reference is lost by this.  We go ahead and make a mutable copy
		// (if necessary) because the interpreter requires the current
		// continuation to always be mutable...
		final A_Type expectedType = function.kind().returnType();
		final A_Continuation caller = con.caller();
		if (caller.equalsNil())
		{
			interpreter.terminateFiber(result);
			// This looks strange, but it is correct.
			return CONTINUATION_CHANGED;
		}
		final A_Type linkStrengthenedType = caller.stackAt(
			caller.stackp());
//		(not guaranteed by VM, just by semantic restriction on Exit_with_).
//		assert linkStrengthenedType.isSubtypeOf(expectedType);
		if (!result.isInstanceOf(expectedType))
		{
			// Wasn't strong enough to meet the block's declared type.
			return interpreter.primitiveFailure(
				E_CONTINUATION_EXPECTED_STRONGER_TYPE);
		}
		if (!result.isInstanceOf(linkStrengthenedType))
		{
			// Wasn't strong enough to meet the *call site's* type.
			// A useful distinction when we start to record primitive
			// failure reason codes.
			return interpreter.primitiveFailure(
				E_CONTINUATION_EXPECTED_STRONGER_TYPE);
		}
		final A_Continuation targetCon = caller.ensureMutable();
		targetCon.stackAtPut(targetCon.stackp(), result);
		interpreter.prepareToResumeContinuation(targetCon);
		return CONTINUATION_CHANGED;
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				ContinuationTypeDescriptor.mostGeneralType(),
				ANY.o()),
			BottomTypeDescriptor.bottom());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstance(
			E_CONTINUATION_EXPECTED_STRONGER_TYPE.numericCode());
	}
}
