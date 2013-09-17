/**
 * L2_ATTEMPT_INLINE_PRIMITIVE_NO_CHECK.java
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
import java.util.*;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_Type;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.Primitive.Flag;
import com.avail.interpreter.Primitive.Result;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2RegisterVector;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;

/**
 * Attempt to perform the specified primitive, using the provided arguments.
 * If successful, don't bother to check that the resulting object's type agrees
 * with the expected type; just write the result to some register and then
 * jump to the success label.  If the primitive fails, capture the primitive
 * failure value in some register then continue to the next instruction.
 *
 * <p>
 * Unlike for {@link L2_INVOKE} and related operations, we do not provide
 * the calling continuation here.  That's because by inlining the primitive
 * attempt we have avoided (or at worst postponed) construction of the
 * continuation that reifies the current function execution.  This is a Good
 * Thing, performance-wise.
 * </p>
 *
 * <p>
 * A collection of preserved fields is provided.  Since tampering with a
 * continuation switches it to use the default level one interpreting chunk,
 * we can rest assured that anything written to a continuation by optimized
 * level two code will continue to be free from tampering.  The preserved
 * fields list any such registers whose values are preserved across both
 * successful primitive invocations and failed invocations.
 * </p>
 */
public class L2_ATTEMPT_INLINE_PRIMITIVE_NO_CHECK
extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance =
		new L2_ATTEMPT_INLINE_PRIMITIVE_NO_CHECK().init(
			PRIMITIVE.is("primitive to attempt"),
			CONSTANT.is("function"),
			READ_VECTOR.is("arguments"),
			WRITE_POINTER.is("primitive result"),
			WRITE_POINTER.is("primitive failure value"),
			READWRITE_VECTOR.is("preserved fields"),
			PC.is("if primitive succeeds"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final Primitive primitive = instruction.primitiveAt(0);
		final A_Function function = instruction.constantAt(1);
		final L2RegisterVector argumentsVector =
			instruction.readVectorRegisterAt(2);
		final L2ObjectRegister resultReg = instruction.writeObjectRegisterAt(3);
		final L2ObjectRegister failureReg =
			instruction.writeObjectRegisterAt(4);
		final int successOffset = instruction.pcAt(6);

		interpreter.argsBuffer.clear();
		for (final L2ObjectRegister register : argumentsVector)
		{
			interpreter.argsBuffer.add(register.in(interpreter));
		}
		assert function.code().primitiveNumber() == primitive.primitiveNumber;
		final Result res = interpreter.attemptPrimitive(
			primitive.primitiveNumber,
			function,
			interpreter.argsBuffer,
			true);

		switch (res)
		{
			case SUCCESS:
				resultReg.set(interpreter.latestResult(), interpreter);
				interpreter.offset(successOffset);
				break;
			case FAILURE:
				failureReg.set(interpreter.latestResult(), interpreter);
				break;
			default:
				assert false;
		}
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Translator translator)
	{
		final Primitive primitive = instruction.primitiveAt(0);
		final L2RegisterVector argumentsVector =
			instruction.readVectorRegisterAt(2);
		final L2ObjectRegister resultReg = instruction.writeObjectRegisterAt(3);
		final L2ObjectRegister failureReg =
			instruction.writeObjectRegisterAt(4);

		final RegisterSet failRegisterSet = registerSets.get(0);
		final RegisterSet successRegisterSet = registerSets.get(1);

		final List<A_Type> argTypes =
			new ArrayList<>(argumentsVector.registers().size());
		for (final L2ObjectRegister arg : argumentsVector.registers())
		{
			// Use either register set -- they start the same.
			assert failRegisterSet.hasTypeAt(arg);
			argTypes.add(failRegisterSet.typeAt(arg));
		}
		// We can at least believe what the primitive itself says it returns.
		final A_Type guaranteedType =
			primitive.returnTypeGuaranteedByVM(argTypes);

		// Figure out what the primitive failure values are allowed to be.
		final A_Type failureType = primitive.failureVariableType();
		failRegisterSet.removeTypeAt(failureReg);
		failRegisterSet.removeConstantAt(failureReg);
		failRegisterSet.typeAtPut(failureReg, failureType, instruction);

		successRegisterSet.removeTypeAt(resultReg);
		successRegisterSet.removeConstantAt(resultReg);
		successRegisterSet.typeAtPut(resultReg, guaranteedType, instruction);
	}

	@Override
	public boolean hasSideEffect (final L2Instruction instruction)
	{
		// Since it can't fail, we should only have to keep the instruction
		// around if its result is needed by a kept instruction (not dealt with
		// here), or if the primitive has a side effect.
		final Primitive primitive = instruction.primitiveAt(0);
		return primitive.hasFlag(Flag.HasSideEffect);
	}
}
