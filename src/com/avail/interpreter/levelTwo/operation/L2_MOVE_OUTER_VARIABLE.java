/**
 * L2_MOVE_OUTER_VARIABLE.java
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
import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.VariableDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.L2Translator.L1NaiveTranslator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.RegisterState;

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
	public final static L2Operation instance =
		new L2_MOVE_OUTER_VARIABLE().init(
			IMMEDIATE.is("outer index"),
			READ_POINTER.is("function"),
			WRITE_POINTER.is("destination"),
			CONSTANT.is("outer type"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final int outerIndex = instruction.immediateAt(0);
		final L2ObjectRegister functionReg =
			instruction.readObjectRegisterAt(1);
		final L2ObjectRegister destinationReg =
			instruction.writeObjectRegisterAt(2);
//		final A_Type outerType = instruction.constantAt(3);

		final A_Function function = functionReg.in(interpreter);
		final AvailObject value = function.outerVarAt(outerIndex);
//		assert value.isInstanceOf(outerType);
		destinationReg.set(value, interpreter);
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Translator translator)
	{
		final int outerIndex = instruction.immediateAt(0);
		final L2ObjectRegister functionReg =
			instruction.readObjectRegisterAt(1);
		final L2ObjectRegister destinationReg =
			instruction.writeObjectRegisterAt(2);
		final A_Type outerType = instruction.constantAt(3);

		if (registerSet.hasConstantAt(functionReg))
		{
			// The exact function is known.
			final A_Function function = registerSet.constantAt(functionReg);
			final AvailObject value = function.outerVarAt(outerIndex);
			registerSet.constantAtPut(destinationReg, value, instruction);
		}
		else
		{
			registerSet.removeTypeAt(destinationReg);
			registerSet.removeConstantAt(destinationReg);
			registerSet.typeAtPut(destinationReg, outerType, instruction);
		}
	}

	/**
	 * If the function was created by exactly one instruction then see if the
	 * outer variable can be accessed more economically.
	 */
	@Override
	public boolean regenerate (
		final L2Instruction instruction,
		final L1NaiveTranslator naiveTranslator,
		final RegisterSet registerSet)
	{
		assert instruction.operation == this;
		final L2ObjectRegister functionRegister =
			instruction.readObjectRegisterAt(1);
		final RegisterState state =
			registerSet.stateForReading(functionRegister);
		final List<L2Instruction> functionCreationInstructions =
			state.sourceInstructions();
		if (functionCreationInstructions.size() == 1)
		{
			// Exactly one instruction produced the function.
			final L2Instruction functionCreationInstruction =
				functionCreationInstructions.get(0);
			final int outerIndex = instruction.immediateAt(0);
			final L2ObjectRegister destinationRegister =
				instruction.writeObjectRegisterAt(2);
			final A_Type outerType = instruction.constantAt(3);
			return functionCreationInstruction.operation
				.extractFunctionOuterRegister(
					functionCreationInstruction,
					functionRegister,
					outerIndex,
					outerType,
					registerSet,
					destinationRegister,
					naiveTranslator);
		}
		return super.regenerate(instruction, naiveTranslator, registerSet);
	}
}
