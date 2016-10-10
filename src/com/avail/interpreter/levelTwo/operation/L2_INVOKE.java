/**
 * L2_INVOKE.java
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

import static com.avail.interpreter.levelTwo.L2OperandType.*;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import com.avail.descriptor.A_Continuation;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_RawFunction;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.FixedRegister;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2RegisterVector;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.L2Translator.L1NaiveTranslator;
import com.avail.optimizer.RegisterSet;

/**
 * The given function is invoked. The function may be a primitive, and the
 * primitive may succeed, fail, or change the current continuation. The given
 * continuation is the caller. An immediate, if true ({@code 1}), indicates that
 * the VM can elide the check of the return type (because it has already been
 * proven for this circumstance).
 */
public class L2_INVOKE extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance =
		new L2_INVOKE().init(
			READ_POINTER.is("continuation"),
			READ_POINTER.is("function"),
			READ_VECTOR.is("arguments"),
			IMMEDIATE.is("skip return check"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		// The continuation is required to have been reified already.
		final L2ObjectRegister continuationReg =
			instruction.readObjectRegisterAt(0);
		final L2ObjectRegister functionReg =
			instruction.readObjectRegisterAt(1);
		final L2RegisterVector argumentsReg =
			instruction.readVectorRegisterAt(2);
		final boolean skipReturnCheck = instruction.immediateAt(3) != 0;

		final A_Continuation caller = continuationReg.in(interpreter);
		final A_Function function = functionReg.in(interpreter);
		interpreter.argsBuffer.clear();
		for (final L2ObjectRegister argumentReg : argumentsReg.registers())
		{
			interpreter.argsBuffer.add(argumentReg.in(interpreter));
		}
		interpreter.clearPointerAt(FixedRegister.CALLER.ordinal());  // safety
		interpreter.invokePossiblePrimitiveWithReifiedCaller(
			function,
			caller,
			skipReturnCheck);
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Translator translator)
	{
		// An invoke doesn't mention where it might return to -- this was
		// already dealt with when the reified continuation was created.
		assert registerSets.size() == 0;
	}

	@Override
	public boolean hasSideEffect ()
	{
		// Never remove invocations -- but inlining might make them go away.
		return true;
	}

	@Override
	public boolean reachesNextInstruction ()
	{
		// Eventually returns to the level two pc saved in the continuation.
		return false;
	}

	@Override
	public boolean regenerate (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L1NaiveTranslator naiveTranslator)
	{
		final L2ObjectRegister continuationReg =
			instruction.readObjectRegisterAt(0);
		final L2ObjectRegister functionReg =
			instruction.readObjectRegisterAt(1);
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

		if (registerSet.hasConstantAt(functionReg))
		{
			final A_Function function = registerSet.constantAt(functionReg);
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
		}
		return super.regenerate(instruction, registerSet, naiveTranslator);
	}
}
