/**
 * L2_INVOKE_CONSTANT_FUNCTION.java
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

import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.optimizer.Continuation1NotNullThrowsReification;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.ReifyStackThrowable;

import java.util.List;

import static com.avail.interpreter.levelTwo.L2OperandType.*;
import static com.avail.utility.Nulls.stripNull;

/**
 * The given immediate function is invoked. The function may be a primitive, and
 * the primitive may succeed, fail, or change the current continuation. The
 * given continuation is the caller. An immediate, if true ({@code 1}),
 * indicates that the VM can elide the check of the return type (because it has
 * already been proven for this circumstance).
 */
public class L2_INVOKE_CONSTANT_FUNCTION extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_INVOKE_CONSTANT_FUNCTION().init(
			CONSTANT.is("constant function"),
			READ_VECTOR.is("arguments"),
			IMMEDIATE.is("skip return check"),
			PC.is("on return"),
			PC.is("on reification"));

	@Override
	public Continuation1NotNullThrowsReification<Interpreter> actionFor (
		final L2Instruction instruction)
	{
		final AvailObject constantFunction = instruction.constantAt(0);
		final List<L2ReadPointerOperand> argumentRegs =
			instruction.readVectorRegisterAt(1);
		final boolean skipReturnCheck = instruction.immediateAt(2) != 0;
		final int onNormalReturn = instruction.pcOffsetAt(3);
		final int onReification = instruction.pcOffsetAt(4);

		// Pre-decode the argument registers as much as possible.
		final int[] argumentRegNumbers = argumentRegs.stream()
			.mapToInt(L2ReadPointerOperand::finalIndex)
			.toArray();
		final A_RawFunction code = constantFunction.code();

		return interpreter ->
		{
			interpreter.argsBuffer.clear();
			for (final int argumentRegNumber : argumentRegNumbers)
			{
				interpreter.argsBuffer.add(
					interpreter.pointerAt(argumentRegNumber));
			}
			interpreter.skipReturnCheck = skipReturnCheck;

			final L2Chunk chunk = stripNull(interpreter.chunk);
			final int offset = interpreter.offset;
			final AvailObject[] savedPointers = interpreter.pointers;
			final int[] savedInts = interpreter.integers;
			assert chunk.instructions[offset - 1] == instruction;

			interpreter.chunk = code.startingChunk();
			interpreter.offset = 0;
			try
			{
				interpreter.runChunk();
			}
			catch (final ReifyStackThrowable reifier)
			{
				if (reifier.actuallyReify())
				{
					// We were somewhere inside the callee and received a
					// reification throwable.  Run the steps at the reification
					// off-ramp to produce this continuation, ending at an
					// L2_Return.  None of the intervening instructions may
					// cause reification or Avail function invocation.  The
					// resulting continuation is "returned" by the L2_Return
					// instruction.  That continuation should know how to be
					// reentered when the callee returns.
					interpreter.chunk = chunk;
					interpreter.offset = onReification;
					interpreter.pointers = savedPointers;
					interpreter.integers = savedInts;
					try
					{
						interpreter.runChunk();
					}
					catch (final ReifyStackThrowable innerReifier)
					{
						assert false : "Off-ramp must not cause reification!";
					}
					// The off-ramp "returned" the callerless continuation that
					// captures this frame.
					reifier.pushContinuation(interpreter.latestResult());
				}
				throw reifier;
			}
			// We just returned normally.
			interpreter.offset = onNormalReturn;
		};
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Translator translator)
	{
		// Successful return or a reification off-ramp.
		assert registerSets.size() == 2;
	}

	@Override
	public boolean hasSideEffect ()
	{
		// Never remove invocations -- but inlining might make them go away.
		return true;
	}
}
