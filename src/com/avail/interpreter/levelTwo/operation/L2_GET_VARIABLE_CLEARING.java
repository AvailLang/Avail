/**
 * L2_GET_VARIABLE_CLEARING.java
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

import com.avail.descriptor.A_Type;
import com.avail.descriptor.A_Variable;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.VariableTypeDescriptor;
import com.avail.exceptions.VariableGetException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;

import java.util.List;

import static com.avail.interpreter.levelTwo.L2OperandType.*;

/**
 * Extract the value of a variable, while simultaneously clearing it. If the
 * variable is unassigned, then branch to the specified {@linkplain
 * Interpreter#offset(int) offset}.
 */
public class L2_GET_VARIABLE_CLEARING extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_GET_VARIABLE_CLEARING().init(
			READ_POINTER.is("variable"),
			WRITE_POINTER.is("extracted value"),
			PC.is("if assigned"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final L2ObjectRegister variableReg =
			instruction.readObjectRegisterAt(0);
		final L2ObjectRegister destReg =
			instruction.writeObjectRegisterAt(1);
		final int ifAssigned = instruction.pcAt(2);

		final A_Variable var = variableReg.in(interpreter);
		try
		{
			final AvailObject value = var.getValue();
			if (var.traversed().descriptor().isMutable())
			{
				var.clearValue();
			}
			else
			{
				value.makeImmutable();
			}
			destReg.set(value, interpreter);
			interpreter.offset(ifAssigned);
		}
		catch (final VariableGetException e)
		{
			// Fall through to the next instruction.
		}
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Translator translator)
	{
		final L2ObjectRegister variableReg =
			instruction.readObjectRegisterAt(0);
		final L2ObjectRegister destReg =
			instruction.writeObjectRegisterAt(1);
		// Only update the *branching* register set; if control reaches the
		// next instruction, then no registers have changed.
		final RegisterSet registerSet = registerSets.get(1);
		// If we haven't already guaranteed that this is a variable then we
		// are probably not doing things right.
		assert registerSet.hasTypeAt(variableReg);
		final A_Type varType = registerSet.typeAt(variableReg);
		assert varType.isSubtypeOf(VariableTypeDescriptor.mostGeneralVariableType());
		registerSet.removeConstantAt(destReg);
		registerSet.typeAtPut(destReg, varType.readType(), instruction);
	}

	@Override
	public boolean hasSideEffect ()
	{
		// Subtle. Reading from a variable can fail, so don't remove this.
		// Also it clears the variable.
		return true;
	}

	@Override
	public boolean isVariableGet ()
	{
		return true;
	}
}
