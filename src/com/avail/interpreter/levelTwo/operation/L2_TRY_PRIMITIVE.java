/**
 * L2_TRY_PRIMITIVE.java
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

import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.Primitive.Result;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.ReifyStackThrowable;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.avail.interpreter.Primitive.Flag.CanInline;
import static com.avail.interpreter.Primitive.Result.FAILURE;
import static com.avail.interpreter.Primitive.Result.SUCCESS;

/**
 * Expect the AvailObject (pointers) array and int array to still reflect the
 * caller.  Expect argsBuffer to have been loaded with the arguments to this
 * primitive function, and expect the code/function/chunk to have been updated
 * for this primitive function.  Try to execute the primitive, setting the
 * returnNow flag and latestResult if successful.  The caller always has the
 * responsibility of checking the return value, if applicable at that call site.
 */
public class L2_TRY_PRIMITIVE
extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_TRY_PRIMITIVE().init();

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	throws ReifyStackThrowable
	{
		final Primitive primitive = interpreter.function.code().primitive();
		if (primitive == null)
		{
			// Not a primitive.  Exit quickly, having done nothing.
			return;
		}

		if (primitive.hasFlag(CanInline))
		{
			// It can succeed or fail, but it can't mess with the fiber's stack.
			final Result result = primitive.attempt(
				interpreter.argsBuffer,
				interpreter,
				interpreter.skipReturnCheck);
			if (result == SUCCESS)
			{
				interpreter.returnNow = true;
				return;
			}
			assert result == FAILURE;
			// The failure value was set up, and the next L2 instruction will
			// set up the frame, including capturing that value in a local.
		}

		// The primitive can't be safely inlined, so reify the stack and try the
		// primitive.
		interpreter.skipReturnCheck = false;
		final L2Chunk savedChunk = interpreter.chunk;
		final int savedOffset = interpreter.offset;
		final AvailObject[] savedPointers = interpreter.pointers;
		final int[] savedInts = interpreter.integers;
		final A_Function savedFunction = interpreter.function;

		throw interpreter.reifyThen(() ->
		{
			interpreter.chunk = savedChunk;
			interpreter.offset = savedOffset;
			interpreter.pointers = savedPointers;
			interpreter.integers = savedInts;
			interpreter.function = savedFunction;

			final Result result = primitive.attempt(
				interpreter.argsBuffer,
				interpreter,
				interpreter.skipReturnCheck);
			switch (result)
			{
				case SUCCESS:
				{
					interpreter.returnNow = true;
					break;
				}
				case FAILURE:
				{
					// Continue in this frame where it left off, right after
					// the L2_TRY_PRIMITIVE instruction.
					assert !interpreter.returnNow;
					break;
				}
				case CONTINUATION_CHANGED:
				{
					// Continue in whatever frame was set up by the primitive.
					// It's not really a return, but the returnNow flag still
					// indicates it should reenter (or continue) the next outer
					// frame.
					interpreter.returnNow = true;
					break;
				}
				case FIBER_SUSPENDED:
				{
					// Set the exitNow flag to ensure the interpreter will wind
					// down correctly.  It should be in a state where all frames
					// have been reified, so returnNow would be unnessary.
					assert !interpreter.returnNow;
					interpreter.exitNow = true;
					break;
				}
			}

		});
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Translator translator)
	{
		final Primitive primitive = instruction.primitiveAt(0);
		final A_Type expectedType = instruction.constantAt(3);
		final L2ObjectRegister resultReg = instruction.writeObjectRegisterAt(4);
		final L2ObjectRegister failureReg =
			instruction.writeObjectRegisterAt(5);

		final RegisterSet successRegisterSet = registerSets.get(1);
		final RegisterSet failRegisterSet = registerSets.get(2);

		// Figure out what the primitive failure values are allowed to be.
		final A_Type failureType = primitive.failureVariableType();
		failRegisterSet.removeTypeAt(failureReg);
		failRegisterSet.removeConstantAt(failureReg);
		failRegisterSet.typeAtPut(failureReg, failureType, instruction);

		successRegisterSet.removeTypeAt(resultReg);
		successRegisterSet.removeConstantAt(resultReg);
		successRegisterSet.typeAtPut(resultReg, expectedType, instruction);
	}

	@Override
	public boolean hasSideEffect ()
	{
		// It could fail and jump.
		return true;
	}

	/**
	 * Answer the register that will hold the top-of-stack register of the given
	 * continuation creation
	 * instruction's stack pointer.
	 *
	 * @param instruction
	 *        The continuation creation instruction.
	 * @return The stack pointer of the continuation to be created by the
	 *         give instruction.
	 */
	@Override
	public final @Nullable L2ObjectRegister primitiveResultRegister (
		final L2Instruction instruction)
	{
		assert instruction.operation == instance;
		return instruction.writeObjectRegisterAt(4);
	}
}
