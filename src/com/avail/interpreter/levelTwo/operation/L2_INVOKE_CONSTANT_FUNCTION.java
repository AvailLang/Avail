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

import com.avail.descriptor.A_Continuation;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.register.FixedRegister;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.Continuation1NotNullThrowsReification;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.L2Translator.L1NaiveTranslator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.ReifyStackThrowable;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.avail.interpreter.levelTwo.L2OperandType.*;

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
			/* Only used for reification. */
			PC.is("reification clause"));

	@Override
	public Continuation1NotNullThrowsReification<Interpreter> actionFor (
		final L2Instruction instruction)
	{
		final AvailObject constantFunction = instruction.constantAt(0);
		final List<L2ObjectRegister> argumentRegs =
			instruction.readVectorRegisterAt(1).registers();
		final boolean skipReturnCheck = instruction.immediateAt(2) != 0;
		final int reificationOffset = instruction.pcAt(3);

		// Pre-decode the argument registers as much as possible.
		final int[] argumentRegNumbers = new int[argumentRegs.size()];
		for (int i = 0; i < argumentRegNumbers.length; i++)
		{
			argumentRegNumbers[i] = argumentRegs.get(i).finalIndex();
		}

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

			final L2Chunk chunk = interpreter.chunk;
			final int offset = interpreter.offset;
			final AvailObject[] savedPointers = interpreter.pointers;
			final int[] savedInts = interpreter.integers;

			assert chunk != null;
			assert chunk.executableInstructions[offset] == instruction;

			interpreter.chunk = code.startingChunk();
			interpreter.offset = 0;
			try
			{
				interpreter.chunk.run(interpreter);
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
					interpreter.offset = reificationOffset;
					interpreter.pointers = savedPointers;
					interpreter.integers = savedInts;
					try
					{
						chunk.run(interpreter);
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
		};
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Translator translator)
	{
		// Fall-through for a return, or run the specified offramp.
		assert registerSets.size() == 2;
	}

	@Override
	public boolean hasSideEffect ()
	{
		// Never remove invocations -- but inlining might make them go away.
		return true;
	}

	@Override
	public boolean regenerate (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L1NaiveTranslator naiveTranslator)
	{
		//TODO MvG - Rewrite to agree with operands.
		final L2ObjectRegister continuationReg =
			instruction.readObjectRegisterAt(0);
		final A_Function function =
			instruction.constantAt(1);
//		final L2RegisterVector argumentsReg =
//			instruction.readVectorRegisterAt(2);
//		final boolean skipReturnCheck = instruction.immediateAt(3) != 0;

		// Extract information about the continuation's construction.  Hopefully
		// we'll switch to SSA form before implementing code movement, so assume
		// the registers populating the continuation are still live after the
		// type test and branch, whether taken or not.
		final List<L2Instruction> continuationCreationInstructions =
			registerSet.stateForReading(continuationReg).sourceInstructions();
		if (continuationCreationInstructions.size() != 1)
		{
			// We can't figure out where the continuation got built.  Give up.
			return false;
		}
		final L2Instruction continuationCreationInstruction =
			continuationCreationInstructions.get(0);
		if (!(continuationCreationInstruction.operation
			instanceof L2_CREATE_CONTINUATION))
		{
			// We found the origin of the continuation register, but not the
			// creation instruction itself.  Give up.
			return false;
		}

		final A_RawFunction code = function.code();
		final @Nullable Primitive primitive = code.primitive();
		if (primitive != null)
		{
			// It's a primitive, so maybe it can inline or fold or
			// invoke-reduce itself better than it already has, or even
			// rewrite itself as alternative L2Instructions.
			final boolean transformed = primitive.regenerate(
				instruction, naiveTranslator, registerSet);
			if (transformed)
			{
				return true;
			}
		}
		return super.regenerate(instruction, registerSet, naiveTranslator);
	}
}
