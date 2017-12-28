/**
 * L2_RETURN_FROM_REIFICATION_HANDLER.java
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

import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.StackReifier;
import com.avail.utility.evaluation.Transformer1NotNullArg;

import java.util.List;

import static com.avail.interpreter.levelTwo.L2OperandType.READ_VECTOR;
import static com.avail.utility.Nulls.stripNull;

/**
 * Return from the reification clause of the current {@link L2Chunk}.  This
 * clause was invoked when a request for reification was detected inside an
 * invocation instruction.  The operand supplies a sequence of continuations
 * (each with nil callers), in the order in which the corresponding non-inlined
 * versions of the functions would have been invoked.
 */
public class L2_RETURN_FROM_REIFICATION_HANDLER extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_RETURN_FROM_REIFICATION_HANDLER().init(
			READ_VECTOR.is("returned continuations"));

	@Override
	public Transformer1NotNullArg<Interpreter, StackReifier> actionFor (
		final L2Instruction instruction)
	{
		// Chain the mutable continuations, and return the top one.
		final List<L2ReadPointerOperand> destinationRegs =
			instruction.readVectorRegisterAt(0);

		final int[] destinationIndices = new int[destinationRegs.size()];
		for (int i = 0; i < destinationIndices.length; i++)
		{
			destinationIndices[i] = destinationRegs.get(i).finalIndex();
		}

		return interpreter ->
		{
			final StackReifier reifier = stripNull(interpreter.activeReifier);
			for (final int index : destinationIndices)
			{
				reifier.pushContinuation(interpreter.pointerAt(index));
			}
			interpreter.returnNow = true;
			interpreter.returningFunction = null;
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
