/**
 * L2_EXPLODE_CONTINUATION.java
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
import java.util.List;
import com.avail.descriptor.A_Continuation;
import com.avail.descriptor.A_Map;
import com.avail.descriptor.A_Number;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ContinuationTypeDescriptor;
import com.avail.descriptor.MapDescriptor;
import com.avail.descriptor.NilDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand;
import com.avail.interpreter.levelTwo.operand.L2WritePointerOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteVectorOperand;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.RegisterSet;

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
	public final static L2Operation instance =
		new L2_EXPLODE_CONTINUATION().init(
			READ_POINTER.is("continuation to explode"),
			WRITE_VECTOR.is("exploded continuation slots"),
			WRITE_POINTER.is("exploded caller"),
			WRITE_POINTER.is("exploded function"),
			CONSTANT.is("slot types tuple"),
			CONSTANT.is("slot constants map"),
			CONSTANT.is("null slots set"),
			CONSTANT.is("function type"));

	@Override
	public void step (final Interpreter interpreter)
	{
		// Expand the current continuation's slots into the specified vector
		// of destination registers.  Also explode the current function and the
		// caller.  Ignore the level one program counter and stack pointer since
		// they're implicit in level two code.
		final int continuationToExplodeIndex = interpreter.nextWord();
		final int explodedSlotsVectorIndex = interpreter.nextWord();
		final int explodedCallerIndex = interpreter.nextWord();
		final int explodedFunctionIndex = interpreter.nextWord();
		@SuppressWarnings("unused")
		final int ignoredSlotTypesTuple = interpreter.nextWord();
		@SuppressWarnings("unused")
		final int ignoredSlotConstantsMap = interpreter.nextWord();
		@SuppressWarnings("unused")
		final int ignoredNullSlotsMap = interpreter.nextWord();
		@SuppressWarnings("unused")
		final int ignoredFunctionTypeMap = interpreter.nextWord();

		final A_Tuple slots = interpreter.vectorAt(explodedSlotsVectorIndex);
		final int slotsCount = slots.tupleSize();
		final A_Continuation continuation =
			interpreter.pointerAt(continuationToExplodeIndex);
		assert continuation.numArgsAndLocalsAndStack() == slotsCount;
		for (int i = 1; i <= slotsCount; i++)
		{
			final AvailObject slotValue =
				continuation.argOrLocalOrStackAt(i);
			interpreter.pointerAtPut(slots.tupleIntAt(i), slotValue);
		}
		interpreter.pointerAtPut(explodedCallerIndex, continuation.caller());
		interpreter.pointerAtPut(
			explodedFunctionIndex,
			continuation.function());
	}

	@Override
	public void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet)
	{
		// continuation to explode is instruction.operands[0].
		final L2WriteVectorOperand explodedContinuationSlotsOperand =
			(L2WriteVectorOperand) instruction.operands[1];
		final L2WritePointerOperand explodedCallerOperand =
			(L2WritePointerOperand) instruction.operands[2];
		final L2WritePointerOperand explodedFunctionOperand =
			(L2WritePointerOperand) instruction.operands[3];
		final L2ConstantOperand slotTypesTupleOperand =
			(L2ConstantOperand) instruction.operands[4];
		final L2ConstantOperand slotConstantsMapOperand =
			(L2ConstantOperand) instruction.operands[5];
		final L2ConstantOperand nullSlotsSetOperand =
			(L2ConstantOperand) instruction.operands[6];
		final L2ConstantOperand functionTypeOperand =
			(L2ConstantOperand) instruction.operands[7];

		// Update the type and value information to agree with the types and
		// values known to be in the slots of the continuation being exploded
		// into registers.
		registerSet.typeAtPut(
			explodedCallerOperand.register,
			ContinuationTypeDescriptor.mostGeneralType(),
			instruction);
		registerSet.typeAtPut(
			explodedFunctionOperand.register,
			functionTypeOperand.object,
			instruction);
		final A_Tuple slotTypes = slotTypesTupleOperand.object;
		final List<L2ObjectRegister> explodedSlots =
			explodedContinuationSlotsOperand.vector.registers();
		assert explodedSlots.size() == slotTypes.tupleSize();
		for (int i = 1, end = slotTypes.tupleSize(); i <= end; i++)
		{
			registerSet.typeAtPut(
				explodedSlots.get(i - 1),
				slotTypes.tupleAt(i),
				instruction);
		}
		final A_Map slotValues = slotConstantsMapOperand.object;
		for (final MapDescriptor.Entry entry : slotValues.mapIterable())
		{
			final int slotIndex = entry.key().extractInt();
			final AvailObject slotValue = entry.value();
			registerSet.constantAtPut(
				explodedSlots.get(slotIndex - 1),
				slotValue,
				instruction);
		}
		for (final A_Number indexObject : nullSlotsSetOperand.object)
		{
			registerSet.constantAtPut(
				explodedSlots.get(indexObject.extractInt() - 1),
				NilDescriptor.nil(),
				instruction);
		}
	}
}
