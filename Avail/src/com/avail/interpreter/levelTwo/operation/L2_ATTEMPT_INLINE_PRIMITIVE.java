/**
 * L2_ATTEMPT_INLINE_PRIMITIVE.java
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
import static com.avail.interpreter.Primitive.Result.*;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.Primitive.Result;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.optimizer.RegisterSet;

/**
 * Attempt to perform the specified primitive, using the provided arguments.
 * If successful, check that the resulting object's type agrees with the
 * provided expected type (TODO [MvG] currently stopping the VM if not),
 * writing the result to some register and then jumping to the success
 * label.  If the primitive fails, capture the primitive failure value in
 * some register then continue to the next instruction.
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
public class L2_ATTEMPT_INLINE_PRIMITIVE extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance =
		new L2_ATTEMPT_INLINE_PRIMITIVE();

	static
	{
		instance.init(
			PRIMITIVE.is("primitive to attempt"),
			READ_VECTOR.is("arguments"),
			READ_POINTER.is("expected type"),
			WRITE_POINTER.is("primitive result"),
			WRITE_POINTER.is("primitive failure value"),
			READWRITE_VECTOR.is("preserved fields"),
			PC.is("if primitive succeeds"));
	}

	@Override
	public void step (final L2Interpreter interpreter)
	{
		final int primNumber = interpreter.nextWord();
		final int argsVector = interpreter.nextWord();
		final int expectedTypeRegister = interpreter.nextWord();
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
		if (res == SUCCESS)
		{
			final AvailObject expectedType =
				interpreter.pointerAt(expectedTypeRegister);
			final long start = System.nanoTime();
			final boolean checkOk =
				interpreter.primitiveResult.isInstanceOf(expectedType);
			final long checkTimeNanos = System.nanoTime() - start;
			Primitive.byPrimitiveNumberOrFail(primNumber)
				.addMicrosecondsCheckingResultType(checkTimeNanos / 1000L);
			if (!checkOk)
			{
				// TODO [MvG] This will have to be handled better some day.
				error(
					"primitive %s's result (%s) did not agree with"
					+ " semantic restriction's expected type (%s)",
					Primitive.byPrimitiveNumberOrFail(primNumber).name(),
					interpreter.primitiveResult,
					expectedType);
			}
			interpreter.pointerAtPut(
				resultRegister,
				interpreter.primitiveResult);
			interpreter.offset(successOffset);
		}
		else if (res == FAILURE)
		{
			interpreter.pointerAtPut(
				failureValueRegister,
				interpreter.primitiveResult);
		}
		else if (res == CONTINUATION_CHANGED)
		{
			error(
				"attemptPrimitive wordcode should never set up "
				+ "a new continuation",
				primNumber);
		}
		else
		{
			error("Unrecognized return type from attemptPrimitive()");
		}
	}

	@Override
	public void propagateTypesInFor (
		final L2Instruction instruction,
		final RegisterSet registers)
	{
		final L2WritePointerOperand result =
			(L2WritePointerOperand) instruction.operands[3];
		final L2WritePointerOperand failureValue =
			(L2WritePointerOperand) instruction.operands[4];
		registers.removeTypeAt(result.register);
		registers.removeConstantAt(result.register);
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