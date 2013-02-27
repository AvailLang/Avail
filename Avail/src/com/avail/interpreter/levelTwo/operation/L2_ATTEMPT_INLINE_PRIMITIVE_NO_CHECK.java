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
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive.Result;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
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
 * fields lists any such registers whose values are preserved across both
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
			READ_VECTOR.is("arguments"),
			WRITE_POINTER.is("primitive result"),
			WRITE_POINTER.is("primitive failure value"),
			READWRITE_VECTOR.is("preserved fields"),
			PC.is("if primitive succeeds"));

	@Override
	public void step (final Interpreter interpreter)
	{
		final int primNumber = interpreter.nextWord();
		final int argsVector = interpreter.nextWord();
		final int resultRegister = interpreter.nextWord();
		final int failureValueRegister = interpreter.nextWord();
		@SuppressWarnings("unused")
		final int unusedPreservedVector = interpreter.nextWord();
		final int successOffset = interpreter.nextWord();

		final A_Tuple argsVect = interpreter.vectorAt(argsVector);
		interpreter.argsBuffer.clear();
		for (int i = 1; i <= argsVect.tupleSize(); i++)
		{
			interpreter.argsBuffer.add(
				interpreter.pointerAt(argsVect.tupleIntAt(i)));
		}
		// Only primitive 340 needs the compiledCode argument, and it's
		// infallible.  Thus, we can pass null.
		final Result res = interpreter.attemptPrimitive(
			primNumber,
			null,
			interpreter.argsBuffer);

		switch (res)
		{
			case SUCCESS:
				interpreter.pointerAtPut(
					resultRegister, interpreter.latestResult());
				interpreter.offset(successOffset);
				break;
			case FAILURE:
				interpreter.pointerAtPut(
					failureValueRegister, interpreter.latestResult());
				break;
			default:
				assert false;
		}
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
		final L2WritePointerOperand result =
			(L2WritePointerOperand) instruction.operands[2];
		final L2WritePointerOperand failureValue =
			(L2WritePointerOperand) instruction.operands[3];

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

		registers.removeTypeAt(result.register);
		registers.removeConstantAt(result.register);
		registers.typeAtPut(result.register, guaranteedType);
		registers.propagateWriteTo(result.register);
		registers.removeTypeAt(failureValue.register);
		registers.removeConstantAt(failureValue.register);
		registers.propagateWriteTo(failureValue.register);

	}

	@Override
	public boolean hasSideEffect ()
	{
		// It could fail and jump.
		return true;
	}
}
