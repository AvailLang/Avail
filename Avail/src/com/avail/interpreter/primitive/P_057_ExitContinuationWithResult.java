/**
 * P_057_ExitContinuationWithResult.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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
import static com.avail.exceptions.AvailErrorCode.E_CONTINUATION_EXPECTED_STRONGER_TYPE;
import static com.avail.interpreter.Primitive.Flag.SwitchesContinuation;
import static com.avail.interpreter.Primitive.Result.CONTINUATION_CHANGED;
import java.util.List;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 57:</strong> Exit the given {@linkplain
 * ContinuationDescriptor continuation} (returning result to its caller).
 */
public class P_057_ExitContinuationWithResult extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance = new P_057_ExitContinuationWithResult().init(
		2, SwitchesContinuation);

	@Override
	public @NotNull Result attempt (
		final @NotNull List<AvailObject> args,
		final @NotNull Interpreter interpreter)
	{
		assert args.size() == 2;
		final AvailObject con = args.get(0);
		final AvailObject result = args.get(1);
		assert con.stackp() ==
				con.objectSlotsCount()
				+ 1
				- con.descriptor().numberOfFixedObjectSlots()
			: "Outer continuation should have been a label- rather than "
				+ "call- continuation";
		assert con.pc() == 1
			: "Labels must only occur at the start of a block.  "
				+ "Only exit that kind of continuation.";
		// No need to make it immutable because current continuation's
		// reference is lost by this.  We go ahead and make a mutable copy
		// (if necessary) because the interpreter requires the current
		// continuation to always be mutable...
		final AvailObject expectedType = con.function().kind().returnType();
		final AvailObject caller = con.caller();
		if (caller.equalsNull())
		{
			interpreter.exitProcessWith(result);
			return CONTINUATION_CHANGED;
		}
		final AvailObject linkStrengthenedType = caller.stackAt(
			caller.stackp());
		assert linkStrengthenedType.isSubtypeOf(expectedType);
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
		final AvailObject targetCon = caller.ensureMutable();
		targetCon.stackAtPut(targetCon.stackp(), result);
		interpreter.prepareToResumeContinuation(targetCon);
		return CONTINUATION_CHANGED;
	}

	@Override
	protected @NotNull AvailObject privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				ContinuationTypeDescriptor.mostGeneralType(),
				ANY.o()),
			BottomTypeDescriptor.bottom());
	}
}