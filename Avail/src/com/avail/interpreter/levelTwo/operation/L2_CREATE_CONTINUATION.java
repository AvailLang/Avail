/**
 * L2_CREATE_CONTINUATION.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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
import com.avail.descriptor.*;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.RegisterSet;

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
	public final static L2Operation instance =
		new L2_CREATE_CONTINUATION().init(
			READ_POINTER.is("caller"),
			READ_POINTER.is("function"),
			IMMEDIATE.is("level one pc"),
			IMMEDIATE.is("stack pointer"),
			READ_VECTOR.is("slot values"),
			PC.is("level two pc"),
			WRITE_POINTER.is("destination"));

	@Override
	public void step (final Interpreter interpreter)
	{
		final int senderIndex = interpreter.nextWord();
		final int functionIndex = interpreter.nextWord();
		final int pcIndex = interpreter.nextWord();
		final int stackpIndex = interpreter.nextWord();
		final int slotsIndex = interpreter.nextWord();
		final int wordcodeOffset = interpreter.nextWord();
		final int destIndex = interpreter.nextWord();

		final A_Function function = interpreter.pointerAt(functionIndex);
		final A_RawFunction code = function.code();
		final int frameSize = code.numArgsAndLocalsAndStack();

		final A_Continuation continuation =
			ContinuationDescriptor.createExceptFrame(
				function,
				interpreter.pointerAt(senderIndex),
				pcIndex,
				frameSize - code.maxStackDepth() + stackpIndex,
				interpreter.chunk(),
				wordcodeOffset);

		final A_Tuple slots = interpreter.vectorAt(slotsIndex);
		final int size = slots.tupleSize();
		for (int i = 1; i <= size; i++)
		{
			continuation.argOrLocalOrStackAtPut(
				i,
				interpreter.pointerAt(slots.tupleIntAt(i)));
		}
		interpreter.pointerAtPut(destIndex, continuation);
	}

	@Override
	public void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets)
	{
		final L2ReadPointerOperand functionOperand =
			(L2ReadPointerOperand) instruction.operands[1];
		final L2WritePointerOperand destinationOperand =
			(L2WritePointerOperand) instruction.operands[6];
		// Propagate information differently to the code just after creating the
		// continuation and the code after the continuation resumes.
		final RegisterSet afterCreation = registerSets.get(0);
		final RegisterSet afterResumption = registerSets.get(1);
		final L2ObjectRegister destinationRegister =
			destinationOperand.register;
		final A_Type functionType = afterCreation.typeAt(
			functionOperand.register);
		assert functionType != null;
		assert functionType.isSubtypeOf(
			FunctionTypeDescriptor.mostGeneralType());
		afterCreation.removeConstantAt(destinationRegister);
		afterCreation.typeAtPut(
			destinationRegister,
			ContinuationTypeDescriptor.forFunctionType(functionType),
			instruction);
		afterResumption.clearEverythingFor(instruction);
	}
}
