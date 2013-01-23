/**
 * L2_GET_VARIABLE_CLEARING.java
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
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.optimizer.RegisterSet;

/**
 * Extract the value of a variable, while simultaneously clearing it.
 *
 * <p>
 * TODO [MvG] - Currently stops the VM if the variable did not have a value
 * assigned.  This needs a better mechanism.
 * </p>
 */
public class L2_GET_VARIABLE_CLEARING extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance = new L2_GET_VARIABLE_CLEARING();

	static
	{
		instance.init(
			READ_POINTER.is("variable"),
			WRITE_POINTER.is("extracted value"));
	}

	@Override
	public void step (final L2Interpreter interpreter)
	{
		final int getIndex = interpreter.nextWord();
		final int destIndex = interpreter.nextWord();
		final AvailObject var = interpreter.pointerAt(getIndex);
		final AvailObject value = var.getValue();
		if (var.traversed().descriptor().isMutable())
		{
			var.clearValue();
		}
		else
		{
			value.makeImmutable();
		}
		interpreter.pointerAtPut(destIndex, value);
	}

	@Override
	public void propagateTypesInFor (
		final L2Instruction instruction,
		final RegisterSet registers)
	{
		final L2ReadPointerOperand variableOperand =
			(L2ReadPointerOperand) instruction.operands[0];
		final L2WritePointerOperand destinationOperand =
			(L2WritePointerOperand) instruction.operands[1];

		// If we haven't already guaranteed that this is a variable then we
		// are probably not doing things right.
		assert registers.hasTypeAt(variableOperand.register);
		final AvailObject varType = registers.typeAt(
			variableOperand.register);
		assert varType.isSubtypeOf(
			VariableTypeDescriptor.mostGeneralType());
		registers.typeAtPut(
			destinationOperand.register,
			varType.readType());
		registers.removeConstantAt(destinationOperand.register);
		registers.propagateWriteTo(destinationOperand.register);
	}

	@Override
	public boolean hasSideEffect ()
	{
		// Subtle. Reading from a variable can fail, so don't remove this.
		// Also it clears the variable.
		return true;
	}
}