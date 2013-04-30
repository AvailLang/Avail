/**
 * L2_ENTER_L2_CHUNK.java
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

import static com.avail.descriptor.AvailObject.error;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_VECTOR;
import static com.avail.interpreter.levelTwo.register.FixedRegister.*;
import java.util.List;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.ContinuationTypeDescriptor;
import com.avail.descriptor.NilDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.Primitive.Flag;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.operand.L2WriteVectorOperand;
import com.avail.interpreter.levelTwo.register.FixedRegister;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.RegisterSet;

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
	public final static L2Operation instance =
		new L2_ENTER_L2_CHUNK().init(
			WRITE_VECTOR.is("fixed and arguments"));

	@Override
	public void step (final Interpreter interpreter)
	{
		error("Enter chunk wordcode is not executable\n");
	}

	@Override
	public boolean shouldEmit ()
	{
		return false;
	}

	@Override
	public void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet)
	{
		final L2WriteVectorOperand writeVector =
			(L2WriteVectorOperand) instruction.operands[0];

		final List<L2ObjectRegister> regs = writeVector.vector.registers();
		final A_RawFunction code = registerSet.codeOrFail();
		assert regs.size() == FixedRegister.values().length + code.numArgs();
		assert regs.get(FixedRegister.NULL.ordinal())
			== registerSet.fixed(NULL);
		assert regs.get(FixedRegister.CALLER.ordinal())
			== registerSet.fixed(CALLER);
		assert regs.get(FixedRegister.FUNCTION.ordinal())
			== registerSet.fixed(FUNCTION);
		assert regs.get(FixedRegister.PRIMITIVE_FAILURE.ordinal())
			== registerSet.fixed(PRIMITIVE_FAILURE);
		for (int i = 1, end = code.numArgs(); i <= end; i++)
		{
			assert regs.get(FixedRegister.values().length + i - 1)
				== registerSet.continuationSlot(i);
		}
		registerSet.constantAtPut(
			registerSet.fixed(NULL),
			NilDescriptor.nil(),
			instruction);
		registerSet.typeAtPut(
			registerSet.fixed(CALLER),
			ContinuationTypeDescriptor.mostGeneralType(),
			instruction);
		registerSet.typeAtPut(
			registerSet.fixed(FUNCTION),
			code.functionType(),
			instruction);
		final int prim = code.primitiveNumber();
		if (prim != 0)
		{
			final Primitive primitive = Primitive.byPrimitiveNumberOrFail(prim);
			if (!primitive.hasFlag(Flag.CannotFail))
			{
				registerSet.typeAtPut(
					registerSet.fixed(PRIMITIVE_FAILURE),
					primitive.failureVariableType(),
					instruction);
			}
		}
		final A_Type argsType = code.functionType().argsTupleType();
		for (int i = 1, end = code.numArgs(); i <= end; i++)
		{
			final L2ObjectRegister argRegister = registerSet.continuationSlot(i);
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
