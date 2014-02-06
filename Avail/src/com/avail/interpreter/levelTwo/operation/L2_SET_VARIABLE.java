/**
 * L2_SET_VARIABLE.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

import static com.avail.interpreter.levelTwo.L2OperandType.READ_POINTER;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;

/**
 * Assign a value to a {@linkplain VariableDescriptor variable}.
 */
public class L2_SET_VARIABLE extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance =
		new L2_SET_VARIABLE().init(
			READ_POINTER.is("variable"),
			READ_POINTER.is("value to write"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final L2ObjectRegister variableReg =
			instruction.readObjectRegisterAt(0);
		final L2ObjectRegister valueReg = instruction.readObjectRegisterAt(1);

		final AvailObject value = valueReg.in(interpreter);
		final A_Variable variable = variableReg.in(interpreter);
		variable.setValue(value);
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Translator translator)
	{
		final L2ObjectRegister variableReg =
			instruction.readObjectRegisterAt(0);

		// If we haven't already guaranteed that this is a variable then we
		// are probably not doing things right.
		assert registerSet.hasTypeAt(variableReg);
		final A_Type varType = registerSet.typeAt(variableReg);
		assert varType.isSubtypeOf(VariableTypeDescriptor.mostGeneralType());
	}

	@Override
	public boolean hasSideEffect ()
	{
		return true;
	}

	@Override
	public boolean regenerate (
		final L2Instruction instruction,
		final List<L2Instruction> newInstructions,
		final RegisterSet registerSet)
	{
		final L2ObjectRegister variableReg =
			instruction.readObjectRegisterAt(0);
		final L2ObjectRegister valueReg = instruction.readObjectRegisterAt(1);

		final A_Type varType = registerSet.typeAt(variableReg);
		final A_Type valueType = registerSet.typeAt(valueReg);
		if (valueType.isSubtypeOf(varType.writeType()))
		{
			// Type propagation has strengthened the value's type enough to
			// be able to avoid the check.
			newInstructions.add(new L2Instruction(
				L2_SET_VARIABLE_NO_CHECK.instance,
				instruction.operands[0],
				instruction.operands[1]));
			return true;

		}
		return super.regenerate(instruction, newInstructions, registerSet);
	}
}
