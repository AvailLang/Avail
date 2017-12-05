/**
 * L2_RETURN_NO_CHECK.java
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
package com.avail.interpreter.levelTwo.operation;

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.StackReifier;
import com.avail.utility.evaluation.Transformer1NotNullArg;

import java.util.List;

import static com.avail.interpreter.levelTwo.L2OperandType.READ_POINTER;
import static com.avail.utility.Nulls.stripNull;

/**
 * Return from the current {@link L2Chunk} with the given return value.  The
 * value to return will be stored in {@link Interpreter#latestResult(
 * A_BasicObject)}, so the caller will need to look there.  Suppress checking of
 * the return value, which is only possible when returning a continuation to the
 * reification handler.
 */
public class L2_RETURN_NO_CHECK extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_RETURN_NO_CHECK().init(
			READ_POINTER.is("return value"));

	@Override
	public Transformer1NotNullArg<Interpreter, StackReifier> actionFor (
		final L2Instruction instruction)
	{
		// Return to the calling continuation with the given value.
		final int valueRegIndex =
			instruction.readObjectRegisterAt(0).finalIndex();

		return interpreter ->
		{
			final AvailObject value = interpreter.pointerAt(valueRegIndex);
			interpreter.latestResult(value);
			interpreter.skipReturnCheck = true;
			interpreter.returnNow = true;
			interpreter.returningFunction = stripNull(interpreter.function);
			return null;
		};
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Translator translator)
	{
		// A return instruction doesn't mention where it might end up.
		assert registerSets.size() == 0;
	}

	@Override
	public boolean hasSideEffect ()
	{
		// Never remove this.
		return true;
	}

	@Override
	public boolean reachesNextInstruction ()
	{
		return false;
	}
}
