/**
 * L2_JUMP_IF_OBJECTS_EQUAL.java
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

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Type;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.optimizer.L1NaiveTranslator;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;

import javax.annotation.Nullable;
import java.util.List;

import static com.avail.interpreter.levelTwo.L2OperandType.PC;
import static com.avail.interpreter.levelTwo.L2OperandType.READ_POINTER;

/**
 * Branch based on whether the two values are equal to each other.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2_JUMP_IF_OBJECTS_EQUAL extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_JUMP_IF_OBJECTS_EQUAL().init(
			READ_POINTER.is("first value"),
			READ_POINTER.is("second value"),
			PC.is("is equal"),
			PC.is("is not equal"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final L2ReadPointerOperand firstReg =
			instruction.readObjectRegisterAt(0);
		final L2ReadPointerOperand secondReg =
			instruction.readObjectRegisterAt(1);
		final int ifEqualIndex = instruction.pcOffsetAt(2);
		final int notEqualIndex = instruction.pcOffsetAt(3);

		interpreter.offset(
			firstReg.in(interpreter).equals(secondReg.in(interpreter))
				? ifEqualIndex
				: notEqualIndex);
	}

	@Override
	public boolean regenerate (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L1NaiveTranslator naiveTranslator)
	{
		final L2ReadPointerOperand firstReg =
			instruction.readObjectRegisterAt(0);
		final L2ReadPointerOperand secondReg =
			instruction.readObjectRegisterAt(1);
		final L2PcOperand ifEqual = instruction.pcAt(2);
		final L2PcOperand notEqual = instruction.pcAt(3);

		final @Nullable A_BasicObject constant1 = firstReg.constantOrNull();
		final @Nullable A_BasicObject constant2 = secondReg.constantOrNull();
		if (constant1 != null && constant2 != null)
		{
			// Compare them right now.
			naiveTranslator.addInstruction(
				L2_JUMP.instance,
				constant1.equals(constant1) ? ifEqual : notEqual);
			return true;
		}
		if (firstReg.type().typeIntersection(secondReg.type()).isBottom())
		{
			// They can't be equal.
			naiveTranslator.addInstruction(L2_JUMP.instance, notEqual);
			return true;
		}
		// Otherwise it's still contingent.
		return super.regenerate(instruction, registerSet, naiveTranslator);
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Translator translator)
	{
		final L2ReadPointerOperand firstReg =
			instruction.readObjectRegisterAt(0);
		final L2ReadPointerOperand secondReg =
			instruction.readObjectRegisterAt(1);
		final L2PcOperand ifEqual = instruction.pcAt(2);
		final L2PcOperand notEqual = instruction.pcAt(3);

		assert registerSets.size() == 2;
		final RegisterSet fallThroughSet = registerSets.get(0);
		final RegisterSet postJumpSet = registerSets.get(1);

		// In the path where the registers compared equal, we can deduce that
		// both registers' origin registers must be constrained to the type
		// intersection of the two registers.
		final A_Type intersection =
			postJumpSet.typeAt(firstReg.register()).typeIntersection(
				postJumpSet.typeAt(secondReg.register()));
		postJumpSet.strengthenTestedTypeAtPut(
			firstReg.register(), intersection);
		postJumpSet.strengthenTestedTypeAtPut(
			secondReg.register(), intersection);

		// Furthermore, if one register is a constant, then in the path where
		// the registers compared equal we can deduce that both registers'
		// origin registers hold that same constant.
		if (postJumpSet.hasConstantAt(firstReg.register()))
		{
			postJumpSet.strengthenTestedValueAtPut(
				secondReg.register(),
				postJumpSet.constantAt(firstReg.register()));
		}
		else if (postJumpSet.hasConstantAt(secondReg.register()))
		{
			postJumpSet.strengthenTestedValueAtPut(
				firstReg.register(),
				postJumpSet.constantAt(secondReg.register()));
		}
	}

	@Override
	public boolean hasSideEffect ()
	{
		// It jumps, which counts as a side effect.
		return true;
	}
}
