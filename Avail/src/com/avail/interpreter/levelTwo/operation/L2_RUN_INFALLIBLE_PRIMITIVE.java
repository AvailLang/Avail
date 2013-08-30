/**
 * L2_RUN_INFALLIBLE_PRIMITIVE.java
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
import static com.avail.interpreter.Primitive.Result.SUCCESS;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.*;
import com.avail.interpreter.Primitive.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2RegisterVector;
import com.avail.optimizer.RegisterSet;

/**
 * Execute a primitive with the provided arguments, writing the result into
 * the specified register.  The primitive must not fail.  Check that the
 * resulting object's type agrees with the provided expected type
 * (TODO [MvG] currently stopping the VM if not).
 *
 * <p>
 * Unlike for {@link L2_INVOKE} and related operations, we do not provide
 * the calling continuation here.  That's because by inlining the primitive
 * attempt we have avoided (or at worst postponed) construction of the
 * continuation that reifies the current function execution.  This is a Good
 * Thing, performance-wise.
 * </p>
 */
public class L2_RUN_INFALLIBLE_PRIMITIVE extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public final static L2Operation instance =
		new L2_RUN_INFALLIBLE_PRIMITIVE().init(
			PRIMITIVE.is("primitive to run"),
			READ_VECTOR.is("arguments"),
			CONSTANT.is("expected type"),
			WRITE_POINTER.is("primitive result"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final Primitive primitive = instruction.primitiveAt(0);
		final L2RegisterVector argsVector = instruction.readVectorRegisterAt(1);
		final A_Type expectedType = instruction.constantAt(2);
		final L2ObjectRegister resultReg = instruction.writeObjectRegisterAt(3);

		interpreter.argsBuffer.clear();
		for (final L2ObjectRegister argumentRegister : argsVector.registers())
		{
			interpreter.argsBuffer.add(argumentRegister.in(interpreter));
		}
		// Only primitive 340 is infallible and yet needs the function,
		// and it's always folded.  In the case that primitive 340 is known to
		// produce the wrong type at some site (potentially dead code due to
		// inlining of an unreachable branch), it is converted to an
		// explicit failure instruction.  Thus we can pass null.
		// Note also that primitives which have to suspend the fiber (to perform
		// a level one unsafe operation and then switch back to level one safe
		// mode) must *never* be inlined, otherwise they couldn't reach a safe
		// inter-nybblecode position.
		// Also, the skipReturnCheck flag doesn't come into play for infallible
		// primitives, since we check it below instead.
		final Result res = interpreter.attemptPrimitive(
			primitive.primitiveNumber,
			null,
			interpreter.argsBuffer,
			false);

		assert res == SUCCESS;

		final AvailObject result = interpreter.latestResult();
		final long before = System.nanoTime();
		final boolean checkOk = result.isInstanceOf(expectedType);
		final long after = System.nanoTime();
		primitive.addNanosecondsCheckingResultType(after - before);
		if (!checkOk)
		{
			// TODO [MvG] - This will have to be handled better some day.
			error(
				"primitive %s's result (%s) did not agree with"
				+ " semantic restriction's expected type (%s)",
				primitive.name(),
				result,
				expectedType);
		}
		resultReg.set(result, interpreter);
	}

	@Override
	public void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet)
	{
		final A_Type expectedType = instruction.constantAt(2);
		final L2ObjectRegister resultReg = instruction.writeObjectRegisterAt(3);

		// This operation *checks* that the returned object is of the specified
		// expectedType, so if the operation completes normally, the resultReg
		// *will* have the expected type.
		registerSet.removeTypeAt(resultReg);
		registerSet.removeConstantAt(resultReg);
		registerSet.typeAtPut(resultReg, expectedType, instruction);
	}

	@Override
	public boolean hasSideEffect (final L2Instruction instruction)
	{
		// It depends on the primitive.
		assert instruction.operation == this;
		final L2PrimitiveOperand primitiveOperand =
			(L2PrimitiveOperand) instruction.operands[0];
		final Primitive primitive = primitiveOperand.primitive;
		final boolean mustKeep = primitive.hasFlag(Flag.HasSideEffect)
			|| primitive.hasFlag(Flag.CatchException)
			|| primitive.hasFlag(Flag.Invokes)
			|| primitive.hasFlag(Flag.SwitchesContinuation)
			|| primitive.hasFlag(Flag.Unknown);
		return mustKeep;
	}
}
