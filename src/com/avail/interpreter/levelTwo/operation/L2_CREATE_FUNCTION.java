/**
 * L2_CREATE_FUNCTION.java
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
import com.avail.interpreter.levelTwo.register.L2RegisterVector;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.L2Translator.L1NaiveTranslator;
import com.avail.optimizer.RegisterSet;

/**
 * Synthesize a new {@link FunctionDescriptor function} from the provided
 * constant compiled code and the vector of captured ("outer") variables.
 */
public class L2_CREATE_FUNCTION extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance =
		new L2_CREATE_FUNCTION().init(
			CONSTANT.is("compiled code"),
			READ_VECTOR.is("captured variables"),
			WRITE_POINTER.is("new function"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final A_RawFunction code = instruction.constantAt(0);
		final L2RegisterVector outersVector =
			instruction.readVectorRegisterAt(1);
		final L2ObjectRegister newFunctionReg =
			instruction.writeObjectRegisterAt(2);

		final int numOuters = outersVector.registers().size();
		assert numOuters == code.numOuters();
		final List<L2ObjectRegister> outerRegs = outersVector.registers();
		final A_Function function = FunctionDescriptor.createExceptOuters(
			code,
			numOuters);
		for (int i = 1; i <= numOuters; i++)
		{
			function.outerVarAtPut(i, outerRegs.get(i - 1).in(interpreter));
		}
		newFunctionReg.set(function, interpreter);
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Translator translator)
	{
		final A_RawFunction code = instruction.constantAt(0);
		final L2RegisterVector outersVector =
			instruction.readVectorRegisterAt(1);
		final L2ObjectRegister newFunctionReg =
			instruction.writeObjectRegisterAt(2);

		registerSet.typeAtPut(newFunctionReg, code.functionType(), instruction);
		if (outersVector.allRegistersAreConstantsIn(registerSet))
		{
			// This can be replaced with a statically constructed function
			// during regeneration, but for now capture the exact function that
			// will be constructed.
			final List<L2ObjectRegister> outerRegs = outersVector.registers();
			final int numOuters = outerRegs.size();
			assert numOuters == code.numOuters();
			final A_Function function = FunctionDescriptor.createExceptOuters(
				code,
				numOuters);
			for (int i = 1; i <= numOuters; i++)
			{
				function.outerVarAtPut(
					i,
					registerSet.constantAt(outerRegs.get(i - 1)));
			}
			registerSet.constantAtPut(newFunctionReg, function, instruction);
		}
		else
		{
			registerSet.removeConstantAt(newFunctionReg);
		}
	}

	@Override
	public boolean extractFunctionOuterRegister (
		final L2Instruction instruction,
		final L2ObjectRegister functionRegister,
		final int outerIndex,
		final A_Type outerType,
		final RegisterSet registerSet,
		final L2ObjectRegister targetRegister,
		final L1NaiveTranslator naiveTranslator)
	{
//TODO[MvG] - Get back to this primitive cancellation stuff
//		final L2RegisterVector outersVector =
//			instruction.readVectorRegisterAt(1);
//		final L2ObjectRegister outerRegister =
//			outersVector.registers().get(outerIndex - 1);
//
//		*** don't we have to determine if the register supplied for this outer
//		*** to the function creation operation still holds the same value?
		return super.extractFunctionOuterRegister(
			instruction,
			functionRegister,
			outerIndex,
			outerType,
			registerSet,
			targetRegister,
			naiveTranslator);
	}
}
