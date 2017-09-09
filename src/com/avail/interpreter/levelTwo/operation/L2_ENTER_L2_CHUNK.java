/**
 * L2_ENTER_L2_CHUNK.java
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

import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Type;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.Primitive.Flag;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2RegisterVector;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;

import javax.annotation.Nullable;
import java.util.List;

import static com.avail.descriptor.ContinuationTypeDescriptor
	.mostGeneralContinuationType;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_VECTOR;
import static com.avail.interpreter.levelTwo.register.FixedRegister.*;

/**
 * This marks the entry point into optimized (level two) code.  At entry,
 * the arguments are expected to be in the specified architectural
 * registers.  This operation is a place-holder and is not actually emitted.
 */
public class L2_ENTER_L2_CHUNK extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_ENTER_L2_CHUNK().init(
			WRITE_VECTOR.is("fixed and arguments"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		// Do nothing.
	}

	@Override
	public boolean shouldEmit ()
	{
		return false;
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Translator translator)
	{
		final L2RegisterVector writeVector =
			instruction.writeVectorRegisterAt(0);

		final List<L2ObjectRegister> regs = writeVector.registers();
		final A_RawFunction code = translator.codeOrFail();
		assert regs.size() == fixedRegisterCount() + code.numArgs();
		assert regs.get(NULL.ordinal())
			== translator.fixed(NULL);
		assert regs.get(CALLER.ordinal())
			== translator.fixed(CALLER);
		assert regs.get(FUNCTION.ordinal())
			== translator.fixed(FUNCTION);
		assert regs.get(PRIMITIVE_FAILURE.ordinal())
			== translator.fixed(PRIMITIVE_FAILURE);
		for (int i = 1, end = code.numArgs(); i <= end; i++)
		{
			assert regs.get(fixedRegisterCount() + i - 1)
				== translator.continuationSlot(i);
		}
		registerSet.constantAtPut(
			translator.fixed(NULL),
			nil(),
			instruction);
		registerSet.typeAtPut(
			translator.fixed(CALLER),
			mostGeneralContinuationType(),
			instruction);
		registerSet.typeAtPut(
			translator.fixed(FUNCTION),
			code.functionType(),
			instruction);
		final @Nullable Primitive primitive = code.primitive();
		if (primitive != null)
		{
			if (!primitive.hasFlag(Flag.CannotFail))
			{
				registerSet.typeAtPut(
					translator.fixed(PRIMITIVE_FAILURE),
					primitive.failureVariableType(),
					instruction);
			}
		}
		final A_Type argsType = code.functionType().argsTupleType();
		for (int i = 1, end = code.numArgs(); i <= end; i++)
		{
			final L2ObjectRegister argRegister = translator.continuationSlot(i);
			registerSet.propagateWriteTo(argRegister, instruction);
			registerSet.typeAtPut(
				argRegister,
				argsType.typeAtIndex(i),
				instruction);
		}
	}

	@Override
	public boolean hasSideEffect ()
	{
		return true;
	}
}
