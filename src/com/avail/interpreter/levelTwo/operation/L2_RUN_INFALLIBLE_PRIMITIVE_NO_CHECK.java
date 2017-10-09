/**
 * L2_RUN_INFALLIBLE_PRIMITIVE_NO_CHECK.java
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

import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_Type;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.Primitive.Flag;
import com.avail.interpreter.Primitive.Result;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2PrimitiveOperand;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.Continuation1NotNullThrowsReification;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;

import java.util.ArrayList;
import java.util.List;

import static com.avail.interpreter.Primitive.Result.SUCCESS;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import static com.avail.utility.Nulls.stripNull;

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
	public static final L2Operation instance =
		new L2_RUN_INFALLIBLE_PRIMITIVE_NO_CHECK().init(
			PRIMITIVE.is("primitive to run"),
			READ_VECTOR.is("arguments"),
			WRITE_POINTER.is("primitive result"));

	@Override
	public Continuation1NotNullThrowsReification<Interpreter> actionFor (
		final L2Instruction instruction)
	{
		final Primitive primitive = instruction.primitiveAt(0);
		final List<L2ObjectRegister> argumentRegs =
			instruction.readVectorRegisterAt(1).registers();
		final int resultRegNumber =
			instruction.writeObjectRegisterAt(2).finalIndex();

		// Pre-decode the argument registers as much as possible.
		final int[] argumentRegNumbers = new int[argumentRegs.size()];
		for (int i = 0; i < argumentRegNumbers.length; i++)
		{
			argumentRegNumbers[i] = argumentRegs.get(i).finalIndex();
		}

		return interpreter ->
		{
			interpreter.argsBuffer.clear();
			for (int i = 0; i < argumentRegNumbers.length; i++)
			{
				interpreter.argsBuffer.add(
					interpreter.pointerAt(argumentRegNumbers[i]));
			}
			// Only primitive 340 is infallible and yet needs the function, and
			// it's always folded.  In the case that primitive 340 is known to
			// produce the wrong type at some site (potentially dead code due to
			// inlining of an unreachable branch), it is converted to an
			// explicit failure instruction.  Thus we can pass null.  Note also
			// that primitives which have to suspend the fiber (to perform a
			// level one unsafe operation and then switch back to level one safe
			// mode) must *never* be inlined, otherwise they couldn't reach a
			// safe inter-nybblecode position.  Also, the skipReturnCheck flag
			// doesn't come into play for infallible primitives, since we would
			// check it after it runs -- but this is the no-check version
			// anyhow, so we don't check it at all.
			final A_Function savedFunction = stripNull(interpreter.function);
			// Eligible primitives MUST NOT access this.
			interpreter.function = null;
			final Result res = interpreter.attemptPrimitive(
				primitive, interpreter.argsBuffer, false);
			assert res == SUCCESS;
			interpreter.function = savedFunction;
			interpreter.pointerAtPut(
				resultRegNumber, interpreter.latestResult());
		};
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Translator translator)
	{
		final Primitive primitive = instruction.primitiveAt(0);
		final List<L2ReadPointerOperand> argsVector = instruction.readVectorRegisterAt(1);
		final L2ObjectRegister resultReg = instruction.writeObjectRegisterAt(2);

		final List<A_Type> argTypes =
			new ArrayList<>(argsVector.registers().size());
		for (final L2ObjectRegister arg : argsVector.registers())
		{
			assert registerSet.hasTypeAt(arg);
			argTypes.add(registerSet.typeAt(arg));
		}
		// We can at least believe what the primitive itself says it returns.
		final A_Type guaranteedType =
			primitive.returnTypeGuaranteedByVM(argTypes);
		registerSet.removeTypeAt(resultReg);
		registerSet.removeConstantAt(resultReg);
		if (!guaranteedType.isBottom())
		{
			registerSet.typeAtPut(resultReg, guaranteedType, instruction);
		}
	}

	@Override
	public boolean hasSideEffect (final L2Instruction instruction)
	{
		// It depends on the primitive.
		assert instruction.operation == this;
		final L2PrimitiveOperand primitiveOperand =
			(L2PrimitiveOperand) instruction.operands[0];
		final Primitive primitive = primitiveOperand.primitive;
		return primitive.hasFlag(Flag.HasSideEffect)
			|| primitive.hasFlag(Flag.CatchException)
			|| primitive.hasFlag(Flag.Invokes)
			|| primitive.hasFlag(Flag.SwitchesContinuation)
			|| primitive.hasFlag(Flag.Unknown);
	}

	@Override
	public L2ObjectRegister primitiveResultRegister (
		final L2Instruction instruction)
	{
		assert instruction.operation == instance;
		return instruction.writeObjectRegisterAt(2);
	}
}
