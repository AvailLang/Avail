/**
 * L2_EXPLODE_CONTINUATION.java
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
 *   may be used to endorse or promote products derived set this software
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

import com.avail.descriptor.A_Continuation;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteIntOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;

import java.util.List;

import static com.avail.interpreter.levelTwo.L2OperandType.*;

/**
 * Given a continuation, extract its caller, function, and all of its slots
 * into the specified registers.  The level one program counter and stack
 * pointer are ignored, since they're always implicitly correlated with the
 * level two program counter.
 */
public class L2_EXPLODE_CONTINUATION extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_EXPLODE_CONTINUATION().init(
			READ_POINTER.is("continuation to explode"),
			WRITE_VECTOR.is("exploded continuation slots"),
			WRITE_INT.is("skip return check"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		// Expand the current continuation's slots into the specified vector
		// of destination registers.  Also explode the current function and the
		// caller.  Ignore the level one program counter and stack pointer since
		// they're implicit in level two code.
		final L2ReadPointerOperand continuationToExplodeReg =
			instruction.readObjectRegisterAt(0);
		final List<L2WritePointerOperand> explodedSlots =
			instruction.writeVectorRegisterAt(1);
		final L2WriteIntOperand skipReturnCheckReg =
			instruction.writeIntRegisterAt(2);

		final int slotsCount = explodedSlots.size();
		final A_Continuation continuation =
			continuationToExplodeReg.in(interpreter);
		assert continuation.numArgsAndLocalsAndStack() == slotsCount;
		for (int i = 1; i <= slotsCount; i++)
		{
			final AvailObject slotValue = continuation.argOrLocalOrStackAt(i);
			explodedSlots.get(i - 1).set(slotValue, interpreter);
		}
		skipReturnCheckReg.set(
			continuation.skipReturnFlag() ? 1 : 0,
			interpreter);
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Translator translator)
	{
		final L2ReadPointerOperand continuationToExplodeReg =
			instruction.readObjectRegisterAt(0);
		final List<L2WritePointerOperand> explodedSlots =
			instruction.writeVectorRegisterAt(1);
//		final L2WriteIntOperand skipReturnCheckReg =
//			instruction.writeIntRegisterAt(2);

		// Update the type and value information to agree with the types and
		// values known to be in the slots of the continuation being exploded
		// into registers.
		final L2Instruction creationInstruction =
			continuationToExplodeReg.register().definition();
		//TODO MvG - Also deal with phi operations here, producing type unions.
		if (creationInstruction.operation instanceof L2_CREATE_CONTINUATION)
		{
			final List<L2ReadPointerOperand> originalSlotRegs =
				L2_CREATE_CONTINUATION.slotRegistersFor(creationInstruction);
			final int slotCount = explodedSlots.size();
			assert slotCount == originalSlotRegs.size();
			for (int i = 0; i < slotCount; i++)
			{
				final L2ReadPointerOperand originalSlot =
					originalSlotRegs.get(i);
				final L2WritePointerOperand explodedSlot = explodedSlots.get(i);
				registerSet.typeAtPut(
					explodedSlot.register(),
					originalSlot.register().restriction().type,
					instruction);
			}
		}
	}
}
