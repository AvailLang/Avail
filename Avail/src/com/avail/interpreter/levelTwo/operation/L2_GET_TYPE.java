/**
 * L2_GET_TYPE.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.RegisterSet;

/**
 * Extract the {@link InstanceTypeDescriptor exact type} of an object in a
 * register, writing the type to another register.
 */
public class L2_GET_TYPE extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance = new L2_GET_TYPE();

	static
	{
		instance.init(
			READ_POINTER.is("value"),
			WRITE_POINTER.is("value's type"));
	}

	@Override
	public void step (final L2Interpreter interpreter)
	{
		final int srcIndex = interpreter.nextWord();
		final int destIndex = interpreter.nextWord();
		interpreter.pointerAtPut(
			destIndex,
			InstanceTypeDescriptor.on(interpreter.pointerAt(srcIndex)));
	}

	@Override
	public void propagateTypesInFor (
		final L2Instruction instruction,
		final RegisterSet registers)
	{
		final L2ReadPointerOperand sourceOperand =
			(L2ReadPointerOperand) instruction.operands[0];
		final L2WritePointerOperand destinationOperand =
			(L2WritePointerOperand) instruction.operands[1];

		final L2ObjectRegister sourceRegister = sourceOperand.register;
		final L2ObjectRegister destinationRegister =
			destinationOperand.register;
		if (registers.hasTypeAt(sourceRegister))
		{
			final AvailObject type = registers.typeAt(sourceRegister);
			// Apply the rule of metacovariance. It says that given types T1
			// and T2, T1 <= T2 implies T1 type <= T2 type. It is guaranteed
			// true for all types in Avail.
			final AvailObject meta = InstanceMetaDescriptor.on(type);
			registers.typeAtPut(destinationRegister, meta);
		}
		else
		{
			registers.typeAtPut(
				destinationRegister,
				InstanceMetaDescriptor.topMeta());
		}

	if (registers.hasConstantAt(sourceRegister))
		{
			registers.constantAtPut(
				destinationRegister,
				registers.constantAt(sourceRegister).kind());
		}
		else
		{
			registers.removeConstantAt(destinationRegister);
		}
		registers.propagateWriteTo(destinationRegister);
	}
}
