/**
 * L2_ATTEMPT_INLINE_PRIMITIVE.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import java.util.List;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.*;
import com.avail.interpreter.Primitive.Result;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2RegisterVector;
import com.avail.optimizer.L2Translator;
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
 * fields list any such registers whose values are preserved across both
 * successful primitive invocations and failed invocations.
 * </p>
 */
public class L2_ATTEMPT_INLINE_PRIMITIVE extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance =
		new L2_ATTEMPT_INLINE_PRIMITIVE().init(
			PRIMITIVE.is("primitive to attempt"),
			CONSTANT.is("function"),
			READ_VECTOR.is("arguments"),
			CONSTANT.is("expected type"),
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
		final A_Type expectedType = instruction.constantAt(3);
		final L2ObjectRegister resultReg = instruction.writeObjectRegisterAt(4);
		final L2ObjectRegister failureReg =
			instruction.writeObjectRegisterAt(5);
		final int successOffset = instruction.pcAt(7);

		interpreter.argsBuffer.clear();
		for (final L2ObjectRegister register : argumentsVector)
		{
			interpreter.argsBuffer.add(register.in(interpreter));
		}
		assert function.code().primitiveNumber() == primitive.primitiveNumber;
		// We'll check the return type on success, below.
		final Result res = interpreter.attemptPrimitive(
			primitive.primitiveNumber,
			function,
			interpreter.argsBuffer,
			true);
		switch (res)
		{
			case SUCCESS:
				final long before = System.nanoTime();
				final AvailObject result = interpreter.latestResult();
				final boolean checkOk = result.isInstanceOf(expectedType);
				final long after = System.nanoTime();
				primitive.addNanosecondsCheckingResultType(after - before);
				if (!checkOk)
				{
					// TODO [MvG] This will have to be handled better some day.
					error(
						"primitive %s's result (%s) did not agree with"
						+ " semantic restriction's expected type (%s)",
						primitive.name(),
						result,
						expectedType);
				}
				resultReg.set(result, interpreter);
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
		final A_Type expectedType = instruction.constantAt(3);
		final L2ObjectRegister resultReg = instruction.writeObjectRegisterAt(4);
		final L2ObjectRegister failureReg =
			instruction.writeObjectRegisterAt(5);

		final RegisterSet failRegisterSet = registerSets.get(0);
		final RegisterSet successRegisterSet = registerSets.get(1);

		// Figure out what the primitive failure values are allowed to be.
		final A_Type failureType = primitive.failureVariableType();
		failRegisterSet.removeTypeAt(failureReg);
		failRegisterSet.removeConstantAt(failureReg);
		failRegisterSet.typeAtPut(failureReg, failureType, instruction);

		successRegisterSet.removeTypeAt(resultReg);
		successRegisterSet.removeConstantAt(resultReg);
		successRegisterSet.typeAtPut(resultReg, expectedType, instruction);
	}

	@Override
	public boolean hasSideEffect ()
	{
		// It could fail and jump.
		return true;
	}
}
