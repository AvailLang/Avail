/**
 * L2_CREATE_CONTINUATION.java
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
import com.avail.descriptor.ContinuationDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.optimizer.StackReifier;
import com.avail.utility.evaluation.Transformer1NotNullArg;

import java.util.List;

import static com.avail.descriptor.ContinuationDescriptor
	.createContinuationExceptFrame;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import static com.avail.utility.Nulls.stripNull;

/**
 * Create a continuation from scratch, using the specified caller, function,
 * constant level one program counter, constant stack pointer, continuation
 * slot values, and level two program counter.  Write the new continuation
 * into the specified register.
 */
public class L2_CREATE_CONTINUATION extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_CREATE_CONTINUATION().init(
			READ_POINTER.is("caller"),
			READ_POINTER.is("function"),
			IMMEDIATE.is("level one pc"),
			IMMEDIATE.is("stack pointer"),
			READ_INT.is("skip return check"),
			READ_VECTOR.is("slot values"),
			WRITE_POINTER.is("destination"),
			PC.is("on-ramp"),
			PC.is("fall through after creation"));

	@Override
	public Transformer1NotNullArg<Interpreter, StackReifier> actionFor (
		final L2Instruction instruction)
	{
		final int callerRegIndex =
			instruction.readObjectRegisterAt(0).finalIndex();
		final int functionRegIndex =
			instruction.readObjectRegisterAt(1).finalIndex();
		final int levelOnePC = instruction.immediateAt(2);
		final int levelOneStackp = instruction.immediateAt(3);
		final int skipReturnRegIndex =
			instruction.readIntRegisterAt(4).finalIndex();
		final List<L2ReadPointerOperand> slots =
			instruction.readVectorRegisterAt(5);
		final int destRegIndex =
			instruction.writeObjectRegisterAt(6).finalIndex();
		final int onRampOffset = instruction.pcOffsetAt(7);
		final int fallThroughOffset = instruction.pcOffsetAt(8);

		final int[] slotRegIndices = new int[slots.size()];
		for (int i = 0; i < slotRegIndices.length; i++)
		{
			slotRegIndices[i] = slots.get(i).finalIndex();
		}

		return interpreter ->
		{
			final A_Continuation continuation =
				createContinuationExceptFrame(
					interpreter.pointerAt(functionRegIndex),
					interpreter.pointerAt(callerRegIndex),
					levelOnePC,
					levelOneStackp,
					interpreter.integerAt(skipReturnRegIndex) != 0,
					stripNull(interpreter.chunk),
					onRampOffset);
			for (int i = 0; i < slotRegIndices.length; i++)
			{
				continuation.argOrLocalOrStackAtPut(
					i + 1, interpreter.pointerAt(slotRegIndices[i]));
			}
			interpreter.pointerAtPut(destRegIndex, continuation);
			interpreter.offset(fallThroughOffset);
			return null;
		};
	}

	/**
	 * Extract the {@link List} of slot registers ({@link
	 * L2ReadPointerOperand}s) that fed the given {@link L2Instruction} whose
	 * {@link L2Operation} is an {@code L2_CREATE_CONTINUATION}.
	 *
	 * @param instruction
	 *        The create-continuation instruction.
	 * @return The slots that were provided to the instruction for populating an
	 *         {@link ContinuationDescriptor continuation}.
	 */
	public static List<L2ReadPointerOperand> slotRegistersFor (
		final L2Instruction instruction)
	{
		assert instruction.operation == instance;
		return instruction.readVectorRegisterAt(5);
	}
}
