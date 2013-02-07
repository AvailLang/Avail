/**
 * L2_RUN_INFALLIBLE_PRIMITIVE_NO_CHECK.java
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

import static com.avail.interpreter.Primitive.Result.SUCCESS;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import java.util.*;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.Primitive.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.RegisterSet;

/**
 * Execute a primitive with the provided arguments, writing the result into
 * the specified register.  The primitive must not fail.  Don't check the result
 * type, since the VM has already guaranteed it is correct.
 *
 * <p>
 * Unlike for {@link L2_INVOKE} and related operations, we do not provide
 * the calling continuation here.  That's because by inlining the primitive
 * attempt we have avoided (or at worst postponed) construction of the
 * continuation that reifies the current function execution.  This is a Good
 * Thing, performance-wise.
 * </p>
 */
public class L2_RUN_INFALLIBLE_PRIMITIVE_NO_CHECK extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance =
		new L2_RUN_INFALLIBLE_PRIMITIVE_NO_CHECK();

	static
	{
		instance.init(
			PRIMITIVE.is("primitive to run"),
			READ_VECTOR.is("arguments"),
			WRITE_POINTER.is("primitive result"));
	}

	@Override
	public void step (final L2Interpreter interpreter)
	{
		final int primNumber = interpreter.nextWord();
		final int argsVector = interpreter.nextWord();
		final int resultRegister = interpreter.nextWord();

		final A_Tuple argsVect = interpreter.vectorAt(argsVector);
		interpreter.argsBuffer.clear();
		for (int i = 1; i <= argsVect.tupleSize(); i++)
		{
			interpreter.argsBuffer.add(
				interpreter.pointerAt(argsVect.tupleIntAt(i)));
		}
		// Only primitive 340 needs the compiledCode argument, and it's
		// always folded.  In the case that primitive 340 is known to
		// produce the wrong type at some site (potentially dead code due to
		// inlining of an unreachable branch), it is converted to an
		// explicit failure instruction.  Thus we can pass null.
		final Result res = interpreter.attemptPrimitive(
			primNumber,
			null,
			interpreter.argsBuffer);

		assert res == SUCCESS;
		interpreter.pointerAtPut(
			resultRegister,
			interpreter.primitiveResult);
	}

	@Override
	public void propagateTypesInFor (
		final L2Instruction instruction,
		final RegisterSet registers)
	{
		final L2PrimitiveOperand primitiveOperand =
			(L2PrimitiveOperand) instruction.operands[0];
		final L2ReadVectorOperand argumentsVector =
			(L2ReadVectorOperand) instruction.operands[1];
		final L2WritePointerOperand destinationOperand =
			(L2WritePointerOperand) instruction.operands[2];

		final List<A_Type> argTypes = new ArrayList<A_Type>(3);
		for (final L2ObjectRegister arg : argumentsVector.vector)
		{
			assert registers.hasTypeAt(arg);
			argTypes.add(registers.typeAt(arg));
		}
		// We can at least believe what the primitive itself says it returns.
		final A_Type guaranteedType =
			primitiveOperand.primitive.returnTypeGuaranteedByVM(
				argTypes);
		registers.removeTypeAt(destinationOperand.register);
		registers.removeConstantAt(destinationOperand.register);
		registers.typeAtPut(destinationOperand.register, guaranteedType);
		registers.propagateWriteTo(destinationOperand.register);
	}

	@Override
	public boolean hasSideEffect (final L2Instruction instruction)
	{
		// It depends on the primitive.
		assert instruction.operation == this;
		final L2PrimitiveOperand primitiveOperand =
			(L2PrimitiveOperand) instruction.operands[0];
		final Primitive primitive = primitiveOperand.primitive;
		assert primitive.hasFlag(Flag.CannotFail);
		final boolean mustKeep = primitive.hasFlag(Flag.HasSideEffect)
			|| primitive.hasFlag(Flag.CatchException)
			|| primitive.hasFlag(Flag.Invokes)
			|| primitive.hasFlag(Flag.SwitchesContinuation)
			|| primitive.hasFlag(Flag.Unknown);
		return mustKeep;
	}
}