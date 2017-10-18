/**
 * L2_TUPLE_AT_CONSTANT.java
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

import com.avail.descriptor.A_Type;
import com.avail.descriptor.TupleDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.optimizer.Continuation1NotNullThrowsReification;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;

import static com.avail.interpreter.levelTwo.L2OperandType.*;

/**
 * Extract an element at a fixed subscript from a {@link TupleDescriptor tuple}
 * that is known to be long enough, writing the element into a register.
 */
public class L2_TUPLE_AT_CONSTANT extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_TUPLE_AT_CONSTANT().init(
			READ_POINTER.is("tuple"),
			IMMEDIATE.is("immediate subscript"),
			WRITE_POINTER.is("destination"));

	@Override
	public Continuation1NotNullThrowsReification<Interpreter> actionFor (
		final L2Instruction instruction)
	{
		final int tupleRegIndex =
			instruction.readObjectRegisterAt(0).finalIndex();
		final int subscript = instruction.immediateAt(1);
		final int destinationRegIndex =
			instruction.writeObjectRegisterAt(2).finalIndex();

		return interpreter ->
			interpreter.pointerAtPut(
				destinationRegIndex,
				interpreter.pointerAt(tupleRegIndex).tupleAt(subscript));
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Translator translator)
	{
		final L2ReadPointerOperand tupleReg =
			instruction.readObjectRegisterAt(0);
		final int subscript = instruction.immediateAt(1);
		final L2WritePointerOperand destinationReg =
			instruction.writeObjectRegisterAt(2);

		final A_Type tupleType = tupleReg.type();
		final int minSize = tupleType.sizeRange().lowerBound().extractInt();
		assert minSize >= subscript;
		registerSet.typeAtPut(
			destinationReg.register(),
			tupleType.typeAtIndex(subscript),
			instruction);
	}
}
