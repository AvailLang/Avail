/**
 * L2_RESTART_CONTINUATION.java
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

import com.avail.descriptor.A_Continuation;
import com.avail.descriptor.A_RawFunction;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.primitive.controlflow.P_RestartContinuation;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.StackReifier;

import javax.annotation.Nullable;
import java.util.List;

import static com.avail.interpreter.levelTwo.L2OperandType.READ_POINTER;

/**
 * Restart the given {@link A_Continuation continuation}, which already has the
 * correct program counter and level two offset (in case the {@link L2Chunk} is
 * still valid).  This operation does the same thing as running {@link
 * P_RestartContinuation}, but avoids the need for a reified calling
 * continuation.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2_RESTART_CONTINUATION extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_RESTART_CONTINUATION().init(
			READ_POINTER.is("continuation to restart"));

	@Override
	public @Nullable StackReifier step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final L2ReadPointerOperand continuationReg =
			instruction.readObjectRegisterAt(0);

		final A_Continuation continuation = continuationReg.in(interpreter);
		final A_RawFunction code = continuation.function().code();
		//TODO MvG - Continuations should have two statically distinguishable
		// varieties.
		assert continuation.stackp() == code.numArgsAndLocalsAndStack() + 1
			: "Outer continuation should have been a label- rather than "
				+ "call-continuation";
		//TODO MvG - Continuations should have two statically distinguishable
		// varieties.
		assert continuation.pc() == 0
			: "Labels must only occur at the start of a block.  "
				+ "Only restart that kind of continuation.";

		// Move the (original) arguments from the continuation into
		// interpreter.argsBuffer.
		final int numArgs = code.numArgs();
		interpreter.argsBuffer.clear();
		for (int i = 1; i <= numArgs; i++)
		{
			interpreter.argsBuffer.add(continuation.argOrLocalOrStackAt(i));
		}
		// The restart entry point expects the interpreter's reifiedContinuation
		// to be the label continuation's *caller*.
		interpreter.reifiedContinuation = continuation.caller();
		interpreter.function = continuation.function();
		interpreter.chunk = continuation.levelTwoChunk();
		interpreter.offset = continuation.levelTwoOffset();
		interpreter.returnNow = false;
		interpreter.latestResult(null);
		return null;
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Translator translator)
	{
		// Do nothing; there are no destinations reached from here within the
		// current chunk.  Technically the restart might be to somewhere in the
		// current chunk, but that's not a requirement.
		assert registerSets.isEmpty();
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
