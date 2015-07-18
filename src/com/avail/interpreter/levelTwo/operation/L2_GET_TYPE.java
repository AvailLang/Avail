/**
 * L2_GET_TYPE.java
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
import com.avail.descriptor.*;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.L2Translator;
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
	public final static L2Operation instance =
		new L2_GET_TYPE().init(
			READ_POINTER.is("value"),
			WRITE_POINTER.is("value's type"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final L2ObjectRegister valueReg = instruction.readObjectRegisterAt(0);
		final L2ObjectRegister typeReg = instruction.writeObjectRegisterAt(1);

		final AvailObject value = valueReg.in(interpreter);
		final A_Type type =
			AbstractEnumerationTypeDescriptor.withInstance(value);
		typeReg.set(type, interpreter);
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Translator translator)
	{
		final L2ObjectRegister valueReg = instruction.readObjectRegisterAt(0);
		final L2ObjectRegister typeReg = instruction.writeObjectRegisterAt(1);

		registerSet.removeConstantAt(typeReg);
		if (registerSet.hasTypeAt(valueReg))
		{
			final A_Type type = registerSet.typeAt(valueReg);
			// Apply the rule of metacovariance. It says that given types T1
			// and T2, T1 <= T2 implies T1 type <= T2 type. It is guaranteed
			// true for all types in Avail.
			final A_Type meta = InstanceMetaDescriptor.on(type);
			registerSet.typeAtPut(
				typeReg,
				meta,
				instruction);
		}
		else
		{
			registerSet.typeAtPut(
				typeReg,
				InstanceMetaDescriptor.topMeta(),
				instruction);
		}

		if (registerSet.hasConstantAt(valueReg)
			&& !registerSet.constantAt(valueReg).isType())
		{
			registerSet.constantAtPut(
				typeReg,
				AbstractEnumerationTypeDescriptor.withInstance(
					registerSet.constantAt(valueReg)),
				instruction);
		}
	}
}
