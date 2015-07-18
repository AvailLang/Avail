/**
 * L2_LABEL.java
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
package com.avail.interpreter.levelTwo.operation;

import static com.avail.interpreter.levelTwo.L2OperandType.COMMENT;
import java.util.List;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.*;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;

/**
 * A label can be the target of a branching instruction.  It is not actually
 * emitted in the instruction stream, but it acts as a place holder during
 * code generation and optimization.
 */
public class L2_LABEL extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance =
		new L2_LABEL().init(
			COMMENT.is("Name of label"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		// Do nothing.
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Translator translator)
	{
		// It's a label.  It doesn't affect registers itself.
	}

	@Override
	public boolean hasSideEffect ()
	{
		// Never remove a reachable label.
		return true;
	}

	@Override
	public boolean shouldEmit ()
	{
		return false;
	}

	@Override
	public boolean regenerate (
		final L2Instruction instruction,
		final List<L2Instruction> newInstructions,
		final RegisterSet registerSet)
	{
		if (!newInstructions.isEmpty())
		{
			final L2Instruction previousInstruction =
				newInstructions.get(newInstructions.size() - 1);
			final L2Operation previousOperation = previousInstruction.operation;
			if (previousOperation instanceof L2_JUMP)
			{
				final List<L2Instruction> targetLabels =
					previousInstruction.targetLabels();
				assert targetLabels.size() == 1;
				if (targetLabels.get(0) == instruction)
				{
					// The previous instruction was a jump to the succeeding
					// instruction, this very label.  Remove the jump but not
					// this label, but indicate that another pass isn't needed
					// due to this removal, since it didn't really affect the
					// control flow.
					newInstructions.remove(newInstructions.size() - 1);
					newInstructions.add(instruction);
					return true;
				}
			}
		}
		return super.regenerate(instruction, newInstructions, registerSet);
	}
}
