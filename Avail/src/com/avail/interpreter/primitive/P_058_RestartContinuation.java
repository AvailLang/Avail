/**
 * P_058_RestartContinuation.java
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

import static com.avail.interpreter.Primitive.Flag.SwitchesContinuation;
import static com.avail.interpreter.Primitive.Result.CONTINUATION_CHANGED;
import java.util.List;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 58:</strong> Restart the given {@linkplain
 * ContinuationDescriptor continuation}. Make sure it's a label-like
 * continuation rather than a call-like, because a call-like continuation
 * requires a value to be stored on its stack in order to resume it,
 * something this primitive does not do.
 */
public class P_058_RestartContinuation extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance = new P_058_RestartContinuation().init(
		1, SwitchesContinuation);

	@Override
	public @NotNull Result attempt (
		final @NotNull List<AvailObject> args,
		final @NotNull Interpreter interpreter)
	{
		assert args.size() == 1;
		final AvailObject con = args.get(0);
		final AvailObject code = con.function().code();
		assert con.stackp() == code.numArgsAndLocalsAndStack() + 1
			: "Outer continuation should have been a label- rather than "
				+ "call-continuation";
		assert con.pc() == 1
			: "Labels must only occur at the start of a block.  "
				+ "Only restart that kind of continuation.";
		// The current continuation's reference is lost by this operation,
		// so we don't need to mark the continuation as immutable.
		interpreter.prepareToRestartContinuation(con);
		return CONTINUATION_CHANGED;
	}

	@Override
	protected @NotNull AvailObject privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				ContinuationTypeDescriptor.mostGeneralType()),
			BottomTypeDescriptor.bottom());
	}
}