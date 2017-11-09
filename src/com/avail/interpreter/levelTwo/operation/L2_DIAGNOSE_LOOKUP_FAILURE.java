/**
 * L2_DIAGNOSE_LOOKUP_FAILURE.java
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

import com.avail.descriptor.A_Definition;
import com.avail.descriptor.A_Set;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.StackReifier;

import javax.annotation.Nullable;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor
	.enumerationWith;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.levelTwo.L2OperandType.CONSTANT;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_POINTER;

/**
 * A method lookup failed. Write the appropriate error code into the supplied
 * {@linkplain L2ObjectRegister object register}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class L2_DIAGNOSE_LOOKUP_FAILURE
extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_DIAGNOSE_LOOKUP_FAILURE().init(
			CONSTANT.is("matching definitions"),
			WRITE_POINTER.is("error code"));

	@Override
	public @Nullable StackReifier step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final A_Set definitions = instruction.constantAt(0);
		final L2WritePointerOperand errorCodeReg =
			instruction.writeObjectRegisterAt(1);
		if (definitions.setSize() == 0)
		{
			errorCodeReg.set(E_NO_METHOD_DEFINITION.numericCode(), interpreter);
			return null;
		}
		if (definitions.setSize() > 1)
		{
			errorCodeReg.set(
				E_AMBIGUOUS_METHOD_DEFINITION.numericCode(), interpreter);
			return null;
		}
		final A_Definition definition = definitions.iterator().next();
		if (definition.isAbstractDefinition())
		{
			errorCodeReg.set(
				E_ABSTRACT_METHOD_DEFINITION.numericCode(), interpreter);
		}
		else if (definition.isForwardDefinition())
		{
			errorCodeReg.set(
				E_FORWARD_METHOD_DEFINITION.numericCode(), interpreter);
		}
		return null;
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Translator translator)
	{
//		final A_Set definitions = instruction.constantAt(0);
		final L2WritePointerOperand errorCodeReg =
			instruction.writeObjectRegisterAt(1);
		registerSet.typeAtPut(
			errorCodeReg.register(),
			enumerationWith(
				set(
					E_NO_METHOD,
					E_NO_METHOD_DEFINITION,
					E_AMBIGUOUS_METHOD_DEFINITION,
					E_FORWARD_METHOD_DEFINITION,
					E_ABSTRACT_METHOD_DEFINITION)),
			instruction);
	}
}
