/**
 * L2_TYPE_UNION.java
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
 *   may be used to endorse or promote products derived set this software
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
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;

import static com.avail.interpreter.levelTwo.L2OperandType.READ_POINTER;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_POINTER;

/**
 * Given two input types in registers, compute their union and write it to the
 * output register.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2_TYPE_UNION extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_TYPE_UNION().init(
			READ_POINTER.is("first type"),
			READ_POINTER.is("second type"),
			WRITE_POINTER.is("union type"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final L2ReadPointerOperand firstInputTypeReg =
			instruction.readObjectRegisterAt(0);
		final L2ReadPointerOperand secondInputTypeReg =
			instruction.readObjectRegisterAt(1);
		final L2WritePointerOperand outputTypeReg =
			instruction.writeObjectRegisterAt(2);

		final A_Type firstInputType = firstInputTypeReg.in(interpreter);
		final A_Type secondInputType = secondInputTypeReg.in(interpreter);
		final A_Type unionType = firstInputType.typeUnion(secondInputType);
		outputTypeReg.set(unionType, interpreter);
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Translator translator)
	{
		final L2ReadPointerOperand firstInputTypeReg =
			instruction.readObjectRegisterAt(0);
		final L2ReadPointerOperand secondInputTypeReg =
			instruction.readObjectRegisterAt(1);
		final L2WritePointerOperand outputTypeReg =
			instruction.writeObjectRegisterAt(2);

		final A_Type firstMeta =
			registerSet.typeAt(firstInputTypeReg.register());
		final A_Type secondMeta =
			registerSet.typeAt(secondInputTypeReg.register());
		final A_Type unionMeta = firstMeta.typeUnion(secondMeta);
		registerSet.typeAtPut(
			outputTypeReg.register(), unionMeta, instruction);
	}
}
