/**
 * L2_GET_TYPE.java
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
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.InstanceTypeDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.StackReifier;

import javax.annotation.Nullable;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.instanceTypeOrMetaOn;
import static com.avail.descriptor.InstanceMetaDescriptor.instanceMeta;
import static com.avail.descriptor.InstanceMetaDescriptor.topMeta;
import static com.avail.interpreter.levelTwo.L2OperandType.READ_POINTER;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_POINTER;

/**
 * Extract the {@link InstanceTypeDescriptor exact type} of an object in a
 * register, writing the type to another register.
 */
public class L2_GET_TYPE extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_GET_TYPE().init(
			READ_POINTER.is("value"),
			WRITE_POINTER.is("value's type"));

	@Override
	public @Nullable StackReifier step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final L2ReadPointerOperand valueReg =
			instruction.readObjectRegisterAt(0);
		final L2WritePointerOperand typeReg =
			instruction.writeObjectRegisterAt(1);

		final AvailObject value = valueReg.in(interpreter);
		final A_Type type = instanceTypeOrMetaOn(value);
		typeReg.set(type, interpreter);
		return null;
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Translator translator)
	{
		final L2ReadPointerOperand valueReg =
			instruction.readObjectRegisterAt(0);
		final L2WritePointerOperand typeReg =
			instruction.writeObjectRegisterAt(1);

		registerSet.removeConstantAt(typeReg.register());
		if (registerSet.hasTypeAt(valueReg.register()))
		{
			final A_Type type = registerSet.typeAt(valueReg.register());
			// Apply the rule of metacovariance. It says that given types T1
			// and T2, T1 <= T2 implies T1 type <= T2 type. It is guaranteed
			// true for all types in Avail.
			final A_Type meta = instanceMeta(type);
			registerSet.typeAtPut(typeReg.register(), meta, instruction);
		}
		else
		{
			registerSet.typeAtPut(
				typeReg.register(), topMeta(), instruction);
		}

		if (registerSet.hasConstantAt(valueReg.register())
			&& !registerSet.constantAt(valueReg.register()).isType())
		{
			registerSet.constantAtPut(
				typeReg.register(),
				instanceTypeOrMetaOn(
					registerSet.constantAt(valueReg.register())),
				instruction);
		}
	}
}
