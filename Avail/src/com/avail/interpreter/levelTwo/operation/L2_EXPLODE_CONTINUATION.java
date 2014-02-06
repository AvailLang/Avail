/**
 * L2_EXPLODE_CONTINUATION.java
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

import static com.avail.interpreter.levelTwo.L2OperandType.*;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.L2IntegerRegister;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.interpreter.levelTwo.register.L2RegisterVector;
import com.avail.optimizer.L2Translator;
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
			WRITE_INT.is("skip return check"),
			CONSTANT.is("slot types map"),
			CONSTANT.is("slot constants map"),
			CONSTANT.is("null slots set"),
			CONSTANT.is("function type"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		// Expand the current continuation's slots into the specified vector
		// of destination registers.  Also explode the current function and the
		// caller.  Ignore the level one program counter and stack pointer since
		// they're implicit in level two code.
		final L2ObjectRegister continuationToExplodeReg =
			instruction.readObjectRegisterAt(0);
		final L2RegisterVector explodedSlotsVector =
			instruction.writeVectorRegisterAt(1);
		final L2ObjectRegister targetCallerReg =
			instruction.writeObjectRegisterAt(2);
		final L2ObjectRegister targetFunctionReg =
			instruction.writeObjectRegisterAt(3);
		final L2IntegerRegister skipReturnCheckReg =
			instruction.writeIntRegisterAt(4);

		final List<L2ObjectRegister> slotRegs = explodedSlotsVector.registers();
		final int slotsCount = slotRegs.size();
		final A_Continuation continuation =
			continuationToExplodeReg.in(interpreter);
		assert continuation.numArgsAndLocalsAndStack() == slotsCount;
		for (int i = 1; i <= slotsCount; i++)
		{
			final AvailObject slotValue = continuation.argOrLocalOrStackAt(i);
			slotRegs.get(i - 1).set(slotValue, interpreter);
		}
		targetCallerReg.set(continuation.caller(), interpreter);
		targetFunctionReg.set(continuation.function(), interpreter);
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
//		final L2ObjectRegister continuationToExplodeReg =
//			instruction.readObjectRegisterAt(0);
		final L2RegisterVector explodedSlotsVector =
			instruction.writeVectorRegisterAt(1);
		final L2ObjectRegister targetCallerReg =
			instruction.writeObjectRegisterAt(2);
		final L2ObjectRegister targetFunctionReg =
			instruction.writeObjectRegisterAt(3);
//		final L2IntegerRegister skipReturnCheckReg =
//			instruction.writeIntRegisterAt(4);
		final A_Map slotTypes = instruction.constantAt(5);
		final A_Map slotConstants = instruction.constantAt(6);
		final A_Set nullSlots = instruction.constantAt(7);
		final A_Type functionType = instruction.constantAt(8);

		// Update the type and value information to agree with the types and
		// values known to be in the slots of the continuation being exploded
		// into registers.
		registerSet.typeAtPut(
			targetCallerReg,
			ContinuationTypeDescriptor.mostGeneralType(),
			instruction);
		registerSet.typeAtPut(targetFunctionReg, functionType, instruction);
		final List<L2ObjectRegister> slotRegs = explodedSlotsVector.registers();
		for (final MapDescriptor.Entry entry : slotTypes.mapIterable())
		{
			final int slotIndex = entry.key().extractInt();
			final A_Type slotType = entry.value();
			registerSet.typeAtPut(
				slotRegs.get(slotIndex - 1),
				slotType,
				instruction);
		}
		for (final MapDescriptor.Entry entry : slotConstants.mapIterable())
		{
			final int slotIndex = entry.key().extractInt();
			final AvailObject slotValue = entry.value();
			registerSet.constantAtPut(
				slotRegs.get(slotIndex - 1),
				slotValue,
				instruction);
		}
		for (final A_Number indexObject : nullSlots)
		{
			registerSet.constantAtPut(
				slotRegs.get(indexObject.extractInt() - 1),
				NilDescriptor.nil(),
				instruction);
		}
	}
}
