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

import com.avail.descriptor.A_Type;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.register.L2ObjectRegister;
import com.avail.optimizer.L1NaiveTranslator;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;

import java.util.List;

import static com.avail.interpreter.levelTwo.L2OperandType.PC;
import static com.avail.interpreter.levelTwo.L2OperandType.READ_POINTER;

/**
 * Jump to the target if the first value equals the second value.
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
			PC.is("target"),
			READ_POINTER.is("first value"),
			READ_POINTER.is("second value"));

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	{
		final int target = instruction.pcOffsetAt(0);
		final L2ObjectRegister firstReg = instruction.readObjectRegisterAt(1);
		final L2ObjectRegister secondReg = instruction.readObjectRegisterAt(2);

		if (firstReg.in(interpreter).equals(secondReg.in(interpreter)))
		{
			interpreter.offset(target);
		}
	}

	@Override
	public boolean regenerate (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L1NaiveTranslator naiveTranslator)
	{
//		final L2PcOperand target = instruction.pcAt(0);
		final L2ObjectRegister firstReg = instruction.readObjectRegisterAt(1);
		final L2ObjectRegister secondReg = instruction.readObjectRegisterAt(2);

		final boolean canJump;
		final boolean mustJump;
		if (registerSet.hasConstantAt(firstReg)
			&& registerSet.hasConstantAt(secondReg))
		{
			// Both constants are known.  This is its own special case to
			// deal with instance metas that are known by value, not just by
			// their type (which would be too weak to draw conclusions).
			mustJump = registerSet.constantAt(firstReg).equals(
				registerSet.constantAt(secondReg));
			canJump = mustJump;
		}
		else
		{
			final A_Type firstType = registerSet.typeAt(firstReg);
			final A_Type secondType = registerSet.typeAt(secondReg);
			final A_Type intersection =
				firstType.typeIntersection(secondType);
			if (intersection.isBottom())
			{
				canJump = false;
				mustJump = false;
			}
			else if (intersection.instanceCount().equalsInt(1)
				&& !intersection.isInstanceMeta())
			{
				canJump = true;
				mustJump = true;
			}
			else
			{
				canJump = true;
				mustJump = false;
			}
		}

		if (mustJump)
		{
			// Jump unconditionally.  The instructions that follow the
			// replacement jump will die and be eliminated next pass.
			naiveTranslator.addInstruction(
				L2_JUMP.instance,
				instruction.operands[0]);
			return true;
		}
		if (!canJump)
		{
			// Never jump.  Leave off the jump, and let the code at the target
			// label die next pass if there's no way to reach it.
			return true;
		}
		// The test could not be eliminated or improved.
		return super.regenerate(instruction, registerSet, naiveTranslator);
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Translator translator)
	{
		final int target = instruction.pcOffsetAt(0);
		final L2ObjectRegister firstReg = instruction.readObjectRegisterAt(1);
		final L2ObjectRegister secondReg = instruction.readObjectRegisterAt(2);

		assert registerSets.size() == 2;
//		final RegisterSet fallThroughSet = registerSets.get(0);
		final RegisterSet postJumpSet = registerSets.get(1);

		// In the path where the registers compared equal, we can deduce that
		// both registers' origin registers must be constrained to the type
		// intersection of the two registers.
		final A_Type intersection =
			postJumpSet.typeAt(firstReg).typeIntersection(
				postJumpSet.typeAt(secondReg));
		postJumpSet.strengthenTestedTypeAtPut(
			firstReg, intersection);
		postJumpSet.strengthenTestedTypeAtPut(
			secondReg, intersection);

		// Furthermore, if one register is a constant, then in the path where
		// the registers compared equal we can deduce that both registers'
		// origin registers hold that same constant.
		if (postJumpSet.hasConstantAt(firstReg))
		{
			postJumpSet.strengthenTestedValueAtPut(
				secondReg, postJumpSet.constantAt(firstReg));
		}
		else if (postJumpSet.hasConstantAt(secondReg))
		{
			postJumpSet.strengthenTestedValueAtPut(
				firstReg, postJumpSet.constantAt(secondReg));
		}
	}

	@Override
	public boolean hasSideEffect ()
	{
		// It jumps, which counts as a side effect.
		return true;
	}
}
