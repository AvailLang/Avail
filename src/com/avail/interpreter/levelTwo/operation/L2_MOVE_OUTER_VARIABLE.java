/**
 * L2_MOVE_OUTER_VARIABLE.java
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
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.VariableDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.optimizer.Continuation1NotNullThrowsReification;
import com.avail.optimizer.L1Translator;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;

import static com.avail.interpreter.levelTwo.L2OperandType.*;

/**
 * Extract a captured "outer" variable from a function.  If the outer
 * variable is an actual {@linkplain VariableDescriptor variable} then the
 * variable itself is what gets moved into the destination register.
 */
public class L2_MOVE_OUTER_VARIABLE extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_MOVE_OUTER_VARIABLE().init(
			IMMEDIATE.is("outer index"),
			READ_POINTER.is("function"),
			WRITE_POINTER.is("destination"));

	@Override
	public Continuation1NotNullThrowsReification<Interpreter> actionFor (
		final L2Instruction instruction)
	{
		final int outerIndex = instruction.immediateAt(0);
		final int functionRegNumber =
			instruction.readObjectRegisterAt(1).finalIndex();
		final int destinationRegNumber =
			instruction.writeObjectRegisterAt(2).finalIndex();

		return interpreter ->
		{
			final A_Function function =
				interpreter.pointerAt(functionRegNumber);
			final AvailObject value = function.outerVarAt(outerIndex);
			// assert value.isInstanceOf(outerType);
			interpreter.pointerAtPut(destinationRegNumber, value);
		};
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Translator translator)
	{
		final int outerIndex = instruction.immediateAt(0);
		final L2ReadPointerOperand functionReg =
			instruction.readObjectRegisterAt(1);
		final L2WritePointerOperand destinationReg =
			instruction.writeObjectRegisterAt(2);

		if (registerSet.hasConstantAt(functionReg.register()))
		{
			// The exact function is known.
			final A_Function function =
				registerSet.constantAt(functionReg.register());
			final AvailObject value = function.outerVarAt(outerIndex);
			registerSet.constantAtPut(
				destinationReg.register(), value, instruction);
		}
	}

	/**
	 * If the function was created by exactly one instruction then see if the
	 * outer variable can be accessed more economically.
	 */
	@Override
	public boolean regenerate (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L1Translator translator)
	{
		assert instruction.operation == this;
		final int outerIndex = instruction.immediateAt(0);
		final L2ReadPointerOperand functionReg =
			instruction.readObjectRegisterAt(1);
		final L2WritePointerOperand destinationReg =
			instruction.writeObjectRegisterAt(2);

		final L2Instruction functionCreationInstruction =
			functionReg.register().definition();
		if (!(functionCreationInstruction.operation.isPhi()))
		{
			// Exactly one instruction produced the function.
			return functionCreationInstruction.operation
				.extractFunctionOuterRegister(
					functionCreationInstruction,
					functionReg,
					outerIndex,
					destinationReg,
					translator);
		}
		return super.regenerate(instruction, registerSet, translator);
	}
}
