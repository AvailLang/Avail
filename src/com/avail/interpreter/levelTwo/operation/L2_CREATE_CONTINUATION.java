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

import static com.avail.interpreter.levelTwo.L2OperandType.*;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.Continuation1NotNullThrowsReification;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;

/**
 * Create a continuation from scratch, using the specified caller, function,
 * constant level one program counter, constant stack pointer, continuation
 * slot values, and level two program counter.  Write the new continuation
 * into the specified register.
 */
@Deprecated
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
			PC.is("level two pc"),
			WRITE_POINTER.is("destination"));

	@Override
	public Continuation1NotNullThrowsReification<Interpreter> actionFor (
		final L2Instruction instruction)
	{
		final int callerRegNumber =
			instruction.readObjectRegisterAt(0).finalIndex();
		final int functionRegNumber =
			instruction.readObjectRegisterAt(1).finalIndex();
		final int levelOnePC = instruction.immediateAt(2);
		final int levelOneStackp = instruction.immediateAt(3);
		final int skipReturnRegNumber =
			instruction.readIntRegisterAt(4).finalIndex();
		final List<L2ObjectRegister> slots =
			instruction.readVectorRegisterAt(5).registers();
		final int[] slotRegNumbers = new int[slots.size()];
		for (int i = 0; i < slotRegNumbers.length; i++)
		{
			slotRegNumbers[i] = slots.get(i).finalIndex();
		}
		final int levelTwoOffset = instruction.pcAt(6);
		final int destRegNumber =
			instruction.writeObjectRegisterAt(7).finalIndex();

		return interpreter ->
		{
			final A_Function function =
				interpreter.pointerAt(functionRegNumber);
			final A_RawFunction code = function.code();
			final int frameSize = code.numArgsAndLocalsAndStack();
			final boolean skipReturnCheck =
				interpreter.integerAt(skipReturnRegNumber) != 0;
			final A_Continuation continuation =
				ContinuationDescriptor.createExceptFrame(
					function,
					interpreter.pointerAt(callerRegNumber),
					levelOnePC,
					frameSize - code.maxStackDepth() + levelOneStackp,
					skipReturnCheck,
					interpreter.chunk,
					levelTwoOffset);
			for (int i = 0; i < slotRegNumbers.length; i++)
			{
				continuation.argOrLocalOrStackAtPut(
					i + 1, interpreter.pointerAt(slotRegNumbers[i]));
			}
			interpreter.pointerAtPut(destRegNumber, continuation);
		};
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Translator translator)
	{
		final L2ObjectRegister functionReg =
			instruction.readObjectRegisterAt(1);
		final L2ObjectRegister destReg = instruction.writeObjectRegisterAt(7);

		// Propagate information differently to the code just after creating the
		// continuation and the code after the continuation resumes.
		final RegisterSet afterCreation = registerSets.get(0);
		final RegisterSet afterResumption = registerSets.get(1);
		final A_Type functionType = afterCreation.typeAt(functionReg);
		assert functionType != null;
		assert functionType.isSubtypeOf(
			FunctionTypeDescriptor.mostGeneralFunctionType());
		afterCreation.removeConstantAt(destReg);
		afterCreation.typeAtPut(
			destReg,
			ContinuationTypeDescriptor.forFunctionType(functionType),
			instruction);
		afterResumption.clearEverythingFor(instruction);
	}
}
